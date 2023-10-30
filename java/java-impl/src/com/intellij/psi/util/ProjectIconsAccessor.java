// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UPolyadicExpression;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolve small icons located in a project for use in UI (e.g., gutter preview icon, lookups).
 */
@Service(Service.Level.PROJECT)
public final class ProjectIconsAccessor {
  @NonNls
  private static final String JAVAX_SWING_ICON = "javax.swing.Icon";

  private static final int ICON_MAX_WEIGHT = 16;
  private static final int ICON_MAX_HEIGHT = 16;
  private static final int ICON_MAX_SIZE = 2 * 1024 * 1024; // 2Kb

  private static final List<String> ICON_EXTENSIONS = List.of("png", "ico", "bmp", "gif", "jpg", "svg");

  private final Project project;

  private final Cache<String, Pair<Long, Icon>> iconCache = Caffeine.newBuilder().maximumSize(500).build();

  ProjectIconsAccessor(@NotNull Project project) {
    this.project = project;
  }

  public static ProjectIconsAccessor getInstance(Project project) {
    return project.getService(ProjectIconsAccessor.class);
  }

  @Nullable
  public VirtualFile resolveIconFile(UElement initializerElement) {
    if (initializerElement == null) return null;
    final List<FileReference> refs = new ArrayList<>();
    initializerElement.accept(new AbstractUastVisitor() {
      @Override
      public boolean visitPolyadicExpression(@NotNull UPolyadicExpression node) {
        if (!(node instanceof UInjectionHost uInjectionHost)) return true;
        processInjectionHost(uInjectionHost);
        super.visitPolyadicExpression(node);
        return true;
      }

      @Override
      public boolean visitLiteralExpression(@NotNull ULiteralExpression node) {
        if (!(node instanceof UInjectionHost uInjectionHost)) return true;
        processInjectionHost(uInjectionHost);
        super.visitLiteralExpression(node);
        return true;
      }

      private void processInjectionHost(@NotNull UInjectionHost node) {
        PsiElement psi = node.getSourcePsi();
        if (psi != null) {
          for (PsiReference ref : psi.getReferences()) {
            if (ref instanceof FileReference) {
              refs.add((FileReference)ref);
            }
          }
        }
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

  public @Nullable Icon getIcon(@NotNull VirtualFile file) {
    String path = file.getPath();
    long stamp = file.getModificationStamp();

    Pair<Long, Icon> iconInfo = iconCache.getIfPresent(path);
    if (iconInfo != null && iconInfo.getFirst() >= stamp) {
      return iconInfo.second;
    }

    try {
      Icon icon = createOrFindBetterIcon(file, isIdeaProject(project));
      iconInfo = new Pair<>(stamp, hasProperSize(icon) ? icon : null);
      iconCache.put(file.getPath(), iconInfo);
    }
    catch (Exception e) {
      iconInfo = null;
      iconCache.invalidate(path);
    }
    return Pair.getSecond(iconInfo);
  }

  public static boolean isIconClassType(PsiType type) {
    return InheritanceUtil.isInheritor(type, JAVAX_SWING_ICON);
  }

  private static boolean isIconFileExtension(String extension) {
    return extension != null && ICON_EXTENSIONS.contains(StringUtil.toLowerCase(extension));
  }

  public static boolean hasProperSize(Icon icon) {
    return icon.getIconHeight() <= JBUIScale.scale(ICON_MAX_HEIGHT) &&
           icon.getIconWidth() <= JBUIScale.scale(ICON_MAX_WEIGHT);
  }

  public static boolean isIdeaProject(@Nullable Project project) {
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
