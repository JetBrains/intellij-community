/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;

/**
 * Shows small (16x16 or less) icons as gutters
 * Works in places where it's possible to resolve from literal expression
 * to an icon image
 *
 * @author Konstantin Bulenkov
 */
public class IconLineMarkerProvider implements LineMarkerProvider {
  @NonNls private static final String JAVAX_SWING_ICON = "javax.swing.Icon";
  private static final int ICON_MAX_WEIGHT = 16;
  private static final int ICON_MAX_HEIGHT = 16;
  private static final int ICON_MAX_SIZE = 2 * 1024 * 1024; //2Kb
  private static final List<String> ICON_EXTS = Arrays.asList("png", "ico", "bmp", "gif", "jpg");

  //TODO: remove old unused icons from the cache
  private final HashMap<String, Pair<Long, Icon>> iconsCache = new HashMap<String, Pair<Long, Icon>>();

  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    if (! DaemonCodeAnalyzerSettings.getInstance().SHOW_SMALL_ICONS_IN_GUTTER) return null;

    if (element instanceof PsiAssignmentExpression) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)element).getLExpression();
      final PsiExpression expr = ((PsiAssignmentExpression)element).getRExpression();
      if (lExpression instanceof PsiReferenceExpression) {
        PsiElement var = ((PsiReferenceExpression)lExpression).resolve();
        if (var instanceof PsiVariable) {
          return resolveIconInfo(((PsiVariable)var).getType(), expr);
        }
      }
    }
    else if (element instanceof PsiReturnStatement) {
      PsiReturnStatement psiReturnStatement = (PsiReturnStatement)element;
      final PsiExpression value = psiReturnStatement.getReturnValue();
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method != null) {
        final PsiType returnType = method.getReturnType();
        final LineMarkerInfo<PsiElement> result = resolveIconInfo(returnType, value);

        if (result != null || !isIconClassType(returnType) || value == null) return result;

        if (methodContainsReturnStatementOnly(method)) {
          for (PsiReference ref : value.getReferences()) {
            final PsiElement field = ref.resolve();
            if (field instanceof PsiField) {
              return resolveIconInfo(returnType, ((PsiField)field).getInitializer(), psiReturnStatement);
            }
          }
        }
      }
    }
    else if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)element;
      return resolveIconInfo(var.getType(), var.getInitializer());
    }
    return null;
  }

  private static boolean methodContainsReturnStatementOnly(@NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null || body.getStatements().length != 1) return false;

    return body.getStatements()[0] instanceof PsiReturnStatement;
  }

  @Nullable
  private LineMarkerInfo<PsiElement> resolveIconInfo(PsiType type, PsiExpression initializer) {
    return resolveIconInfo(type, initializer, initializer);
  }

  @Nullable
  private LineMarkerInfo<PsiElement> resolveIconInfo(PsiType type, PsiExpression initializer, PsiElement bindingElement) {
    if (initializer != null && initializer.isValid() && isIconClassType(type)) {
      final Project project = initializer.getProject();
      final List<FileReference> refs = new ArrayList<FileReference>();
      initializer.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (element instanceof PsiLiteralExpression) {
            for (PsiReference ref : element.getReferences()) {
              if (ref instanceof FileReference) {
                refs.add((FileReference)ref);
              }
            }
          }
          super.visitElement(element);
        }
      });

      for (FileReference ref : refs) {
        final PsiFileSystemItem psiFileSystemItem = ref.resolve();
        VirtualFile file = null;
        if (psiFileSystemItem == null) {
          final ResolveResult[] results = ref.multiResolve(false);
          for (ResolveResult result : results) {
            final PsiElement element = result.getElement();
            if (element instanceof PsiBinaryFile) {
              file = ((PsiFile)element).getVirtualFile();
              break;
            }
          }
        } else {
          file = psiFileSystemItem.getVirtualFile();
        }

        if (file == null || file.isDirectory()
            || !isIconFileExtension(file.getExtension())
            || file.getLength() > ICON_MAX_SIZE) continue;

        final Icon icon = getIcon(file, project);

        if (icon != null) {
          final Ref<VirtualFile> f = Ref.create(file);
          final GutterIconNavigationHandler<PsiElement> navHandler = new GutterIconNavigationHandler<PsiElement>() {
            @Override
            public void navigate(MouseEvent e, PsiElement elt) {
              FileEditorManager.getInstance(project).openFile(f.get(), true);
            }
          };
          return new LineMarkerInfo<PsiElement>(bindingElement, bindingElement.getTextRange(), icon,
                                                Pass.UPDATE_ALL, null, navHandler,
                                                GutterIconRenderer.Alignment.LEFT);
        }
      }
    }
    return null;
  }

  private static boolean isIconFileExtension(String extension) {
    return extension != null && ICON_EXTS.contains(extension.toLowerCase());
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
  }

  private static boolean hasProperSize(Icon icon) {
    return icon.getIconHeight() <= ICON_MAX_HEIGHT && icon.getIconWidth() <= ICON_MAX_WEIGHT;
  }

  @Nullable
  private Icon getIcon(VirtualFile file, Project project) {
    final String path = file.getPath();
    final long stamp = file.getModificationStamp();
    Pair<Long, Icon> iconInfo = iconsCache.get(path);
    if (iconInfo == null || iconInfo.getFirst() < stamp) {
      try {
        final Icon icon = createOrFindBetterIcon(file, PlatformUtils.isIdeaProject(project));
        iconInfo = new Pair<Long, Icon>(stamp, hasProperSize(icon) ? icon : null);
        iconsCache.put(file.getPath(), iconInfo);
      }
      catch (Exception e) {//
        iconInfo = null;
        iconsCache.remove(path);
      }
    }
    return iconInfo == null ? null : iconInfo.getSecond();
  }

  private Icon createOrFindBetterIcon(VirtualFile file, boolean tryToFindBetter) throws IOException {
    if (tryToFindBetter) {
      VirtualFile parent = file.getParent();
      String name = file.getNameWithoutExtension();
      String ext = file.getExtension();
      VirtualFile newFile;
      boolean retina = UIUtil.isRetina();
      boolean dark = UIUtil.isUnderDarcula();
      if (retina && dark) {
        newFile = parent.findChild(name + "@2x_dark." + ext);
        if (newFile != null) {
          return loadIcon(newFile, 2);
        }
      }

      if (dark) {
        newFile = parent.findChild(name + "_dark." + ext);
        if (newFile != null) {
          return loadIcon(file, 1);
        }
      }

      if (retina) {
        newFile = parent.findChild(name + "@2x." + ext);
        if (newFile != null) {
          return loadIcon(newFile, 2);
        }
      }
    }
    return new ImageIcon(file.contentsToByteArray());
  }

  private ImageIcon loadIcon(VirtualFile file, int scale) throws IOException {
    return new ImageIcon(ImageLoader.loadFromStream(file.getInputStream(), scale));
  }

  private static boolean isIconClassType(PsiType type) {
    return InheritanceUtil.isInheritor(type, JAVAX_SWING_ICON);
  }
}