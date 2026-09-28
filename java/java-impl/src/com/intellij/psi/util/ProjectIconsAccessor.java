// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UPolyadicExpression;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject;

/**
 * Resolve small icons located in a project for use in UI (e.g., gutter preview icon, lookups).
 */
@Service(Service.Level.PROJECT)
public final class ProjectIconsAccessor {
  private static final @NonNls String JAVAX_SWING_ICON = "javax.swing.Icon";

  private static final int ICON_MAX_WIDTH = 16;
  private static final int ICON_MAX_HEIGHT = 16;
  private static final int ICON_MAX_SIZE = 2 * 1024 * 1024; // 2Kb

  private static final List<String> ICON_EXTENSIONS = List.of("png", "ico", "bmp", "gif", "jpg", "svg");

  private final @NotNull Project project;

  private final Cache<String, Pair<Long, Icon>> iconCache = Caffeine.newBuilder().maximumSize(500).build();

  ProjectIconsAccessor(@NotNull Project project) {
    this.project = project;
  }

  public static ProjectIconsAccessor getInstance(Project project) {
    return project.getService(ProjectIconsAccessor.class);
  }

  public @Nullable VirtualFile resolveIconFile(UElement initializerElement) {
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

  /**
   * Returns the variant of the icon file for the current theme: {@code icon_dark.svg} next to {@code icon.svg} in a dark theme,
   * or {@code iconFile} itself if there is no such variant.
   * Use it when the icon is loaded without {@link IconLoader}, which handles light/dark internally.
   */
  public static @NotNull VirtualFile resolveIconFile(@NotNull VirtualFile iconFile) {
    if (!StringUtil.equalsIgnoreCase(iconFile.getExtension(), "svg") || JBColor.isBright()) return iconFile;

    VirtualFile directory = iconFile.getParent();
    if (directory == null) return iconFile;

    VirtualFile darkFile = directory.findChild(iconFile.getNameWithoutExtension() + "_dark.svg");
    return darkFile != null ? darkFile : iconFile;
  }

  public @Nullable Icon getIcon(@NotNull VirtualFile file) {
    String path = file.getPath();
    long stamp = file.getModificationStamp();

    Pair<Long, Icon> iconInfo = iconCache.getIfPresent(path);
    if (iconInfo != null && iconInfo.getFirst() >= stamp) {
      return iconInfo.second;
    }

    try {
      Icon icon = createOrFindBetterIcon(file);
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
           icon.getIconWidth() <= JBUIScale.scale(ICON_MAX_WIDTH);
  }

  private Icon createOrFindBetterIcon(@NotNull VirtualFile file) throws IOException {
    if (isIntelliJPlatformProject(project)) {
      return IconLoader.findIcon(file.toNioPath().toUri().toURL());
    }

    if (StringUtil.equalsIgnoreCase(file.getExtension(), "svg")) {
      Image svg;

      try (InputStream stream = resolveIconFile(file).getInputStream()) {
        ScaleContext context = ScaleContext.create();
        svg = SVGLoader.load(null, stream, context, ICON_MAX_WIDTH, ICON_MAX_HEIGHT);
        BufferedImage hiDPI = (BufferedImage)ImageUtil.ensureHiDPI(svg, context);
        return new JBImageIcon(hiDPI);
      }
    }

    return new ImageIcon(file.contentsToByteArray());
  }
}
