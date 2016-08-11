/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private static final List<String> ICON_EXTENSIONS = ContainerUtil.immutableList("png", "ico", "bmp", "gif", "jpg");

  private final Project myProject;

  private final SLRUMap<String, Pair<Long, Icon>> iconsCache = new SLRUMap<>(500, 1000);

  ProjectIconsAccessor(Project project) {
    myProject = project;
  }

  public static ProjectIconsAccessor getInstance(Project project) {
    return ServiceManager.getService(project, ProjectIconsAccessor.class);
  }

  @Nullable
  public VirtualFile resolveIconFile(PsiType type, @Nullable PsiExpression initializer) {
    if (initializer == null || !initializer.isValid() || !isIconClassType(type)) {
      return null;
    }

    final List<FileReference> refs = new ArrayList<>();
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
    Pair<Long, Icon> iconInfo = iconsCache.get(path);
    if (iconInfo == null || iconInfo.getFirst() < stamp) {
      try {
        final Icon icon = createOrFindBetterIcon(file, isIdeaProject(myProject));
        iconInfo = new Pair<>(stamp, hasProperSize(icon) ? icon : null);
        iconsCache.put(file.getPath(), iconInfo);
      }
      catch (Exception e) {//
        iconInfo = null;
        iconsCache.remove(path);
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
    return icon.getIconHeight() <= ICON_MAX_HEIGHT &&
           icon.getIconWidth() <= ICON_MAX_WEIGHT;
  }

  private static boolean isIdeaProject(Project project) {
    if (project == null) return false;
    VirtualFile baseDir = project.getBaseDir();
    return baseDir != null && (baseDir.findChild("idea.iml") != null || baseDir.findChild("community-main.iml") != null);
  }

  private static Icon createOrFindBetterIcon(VirtualFile file, boolean useIconLoader) throws IOException {
    if (useIconLoader) {
      return IconLoader.findIcon(new File(file.getPath()).toURI().toURL());
    }
    return new ImageIcon(file.contentsToByteArray());
  }
}
