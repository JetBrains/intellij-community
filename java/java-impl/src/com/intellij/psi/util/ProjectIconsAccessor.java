// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolve small icons located in project for use in UI (e.g. gutter preview icon, lookups).
 *
 * @since 15
 */
public class ProjectIconsAccessor {

  @NonNls
  private static final String JAVAX_SWING_ICON = "javax.swing.Icon";

  private static final int ICON_MAX_WEIGHT = 16;
  private static final int ICON_MAX_HEIGHT = 16;
  private static final int ICON_MAX_SIZE = 2 * 1024 * 1024; // 2Kb

  private static final List<String> ICON_EXTENSIONS = ContainerUtil.immutableList("png", "ico", "bmp", "gif", "jpg", "svg");

  private final Project myProject;

  private final SLRUMap<String, Pair<Long, Icon>> iconsCache = new SLRUMap<>(500, 1000);

  ProjectIconsAccessor(Project project) {
    myProject = project;
  }

  public static ProjectIconsAccessor getInstance(Project project) {
    return ServiceManager.getService(project, ProjectIconsAccessor.class);
  }

  @Nullable
  public VirtualFile resolveIconFile(PsiElement initializer) {
    final List<FileReference> refs = new ArrayList<>();
    UElement initializerElement = UastContextKt.toUElement(initializer);
    if (initializerElement == null) return null;
    initializerElement.accept(new AbstractUastVisitor() {
      @Override
      public boolean visitLiteralExpression(@NotNull ULiteralExpression node) {
        PsiElement psi = node.getPsi();
        if (psi != null) {
          for (PsiReference ref : psi.getReferences()) {
            if (ref instanceof FileReference) {
              refs.add((FileReference)ref);
            }
          }
        }
        super.visitLiteralExpression(node);
        return true;
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
      }
      else {
        file = psiFileSystemItem.getVirtualFile();
      }

      if (file == null || file.isDirectory() ||
          !isIconFileExtension(file.getExtension()) ||
          file.getLength() > ICON_MAX_SIZE) {
        continue;
      }

      return file;
    }
    return null;
  }

  @Nullable
  public Icon getIcon(@NotNull VirtualFile file) {
    final String path = file.getPath();
    final long stamp = file.getModificationStamp();

    Pair<Long, Icon> iconInfo;
    synchronized (iconsCache) {
      iconInfo = iconsCache.get(path);
      if (iconInfo == null || iconInfo.getFirst() < stamp) {
        try {
          final Icon icon = createOrFindBetterIcon(file, isIdeaProject(myProject));
          iconInfo = new Pair<>(stamp, hasProperSize(icon) ? icon : null);
          iconsCache.put(file.getPath(), iconInfo);
        }
        catch (Exception e) {
          iconInfo = null;
          iconsCache.remove(path);
        }
      }
    }
    return iconInfo == null ? null : iconInfo.getSecond();
  }

  public static boolean isIconClassType(PsiType type) {
    return InheritanceUtil.isInheritor(type, JAVAX_SWING_ICON);
  }

  private static boolean isIconFileExtension(String extension) {
    return extension != null && ICON_EXTENSIONS.contains(extension.toLowerCase(Locale.US));
  }

  private static boolean hasProperSize(Icon icon) {
    return icon.getIconHeight() <= JBUI.scale(ICON_MAX_HEIGHT) &&
           icon.getIconWidth() <= JBUI.scale(ICON_MAX_WEIGHT);
  }

  private static boolean isIdeaProject(@Nullable Project project) {
    if (project == null) return false;
    VirtualFile baseDir = project.getBaseDir();
    //has copy in devkit plugin: org.jetbrains.idea.devkit.util.PsiUtil.isIntelliJBasedDir
    return baseDir != null && (baseDir.findChild("idea.iml") != null || baseDir.findChild("community-main.iml") != null
           || baseDir.findChild("intellij.idea.community.main.iml") != null || baseDir.findChild("intellij.idea.ultimate.main.iml") != null);
  }

  private static Icon createOrFindBetterIcon(VirtualFile file, boolean useIconLoader) throws IOException {
    if (useIconLoader) {
      return IconLoader.findIcon(new File(file.getPath()).toURI().toURL());
    }
    return new ImageIcon(file.contentsToByteArray());
  }
}
