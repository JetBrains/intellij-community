// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.IconTransform;
import com.intellij.ui.icons.ImageDataLoader;
import com.intellij.ui.icons.ImageDescriptor;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.ImageLoader.Dimension2DDouble;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.StartupUiUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.ImageFilter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.List;

final class RasterizedImageDataLoader implements ImageDataLoader {
  private final WeakReference<ClassLoader> classLoaderRef;
  private final int cacheKey;
  private final int imageFlags;
  private final String path;

  RasterizedImageDataLoader(@NotNull String path, @NotNull ClassLoader classLoader, int cacheKey, int imageFlags) {
    this.path = path;
    classLoaderRef = new WeakReference<>(classLoader);
    this.cacheKey = cacheKey;
    this.imageFlags = imageFlags;
  }

  @Override
  public @Nullable Image loadImage(@NotNull List<? extends ImageFilter> filters, @NotNull ScaleContext scaleContext, boolean isDark) {
    // do not use cache
    int flags = ImageLoader.ALLOW_FLOAT_SCALING;
    if (isDark) {
      flags |= ImageLoader.USE_DARK;
    }
    ClassLoader classLoader = classLoaderRef.get();
    if (classLoader == null) {
      return null;
    }
    return loadRasterized(path, filters, classLoader, flags, scaleContext, cacheKey == 0, cacheKey, imageFlags);
  }

  @Override
  public @Nullable URL getURL() {
    ClassLoader classLoader = classLoaderRef.get();
    return classLoader == null ? null : classLoader.getResource(path);
  }

  @Override
  public @Nullable ImageDataLoader patch(@NotNull String originalPath, @NotNull IconTransform transform) {
    ClassLoader classLoader = classLoaderRef.get();
    String pathWithLeadingSlash = originalPath.charAt(0) == '/' ? originalPath : ('/' + originalPath);
    return classLoader == null ? null : IconLoader.createNewResolverIfNeeded(classLoader, pathWithLeadingSlash, transform);
  }

  @Override
  public boolean isMyClassLoader(@NotNull ClassLoader classLoader) {
    return classLoaderRef.get() == classLoader;
  }

  @Override
  public String toString() {
    return "RasterizedImageDataLoader(" +
           ", classLoader=" + classLoaderRef.get() +
           ", path='" + path + '\'' +
           ')';
  }

  private static @Nullable Image loadRasterized(@NotNull String path,
                                               @NotNull List<? extends ImageFilter> filters,
                                               @NotNull ClassLoader classLoader,
                                               @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                               @NotNull ScaleContext scaleContext,
                                               boolean isUpScaleNeeded,
                                                int rasterizedCacheKey,
                                               @MagicConstant(flagsFromClass = ImageDescriptor.class) int imageFlags) {
    long loadingStart = StartUpMeasurer.getCurrentTimeIfEnabled();

    // Prefer retina images for HiDPI scale, because downscaling
    // retina images provides a better result than up-scaling non-retina images.
    float pixScale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);

    int dotIndex = path.lastIndexOf('.');
    String name = dotIndex < 0 ? path : path.substring(0, dotIndex);
    float scale = ImageLoader.adjustScaleFactor((flags & ImageLoader.ALLOW_FLOAT_SCALING) == ImageLoader.ALLOW_FLOAT_SCALING, pixScale);

    boolean isSvg = rasterizedCacheKey != 0;
    boolean isDark = (flags & ImageLoader.USE_DARK) == ImageLoader.USE_DARK;
    boolean isRetina = JBUIScale.isHiDPI(pixScale);

    float imageScale;

    String ext = isSvg ? "svg" : dotIndex < 0 || (dotIndex == path.length() - 1) ? "" : path.substring(dotIndex + 1);

    String effectivePath;
    boolean isEffectiveDark = isDark;
    if (isRetina && isDark && (imageFlags & ImageDescriptor.HAS_DARK_2x) == ImageDescriptor.HAS_DARK_2x) {
      effectivePath = name + "@2x_dark." + ext;
      imageScale = isSvg ? scale : 2;
    }
    else if (isDark && (imageFlags & ImageDescriptor.HAS_DARK) == ImageDescriptor.HAS_DARK) {
      effectivePath = name + "_dark." + ext;
      imageScale = isSvg ? scale : 1;
    }
    else {
      isEffectiveDark = false;
      if (isRetina && (imageFlags & ImageDescriptor.HAS_2x) == ImageDescriptor.HAS_2x) {
        effectivePath = name + "@2x." + ext;
        imageScale = isSvg ? scale : 2;
      }
      else {
        effectivePath = path;
        imageScale = isSvg ? scale : 1;
      }
    }

    Dimension2DDouble originalUserSize = new Dimension2DDouble(0, 0);
    try {
      long start = StartUpMeasurer.getCurrentTimeIfEnabled();
      Image image;
      if (isSvg) {
        image = SVGLoader.loadFromClassResource(null, classLoader, effectivePath, rasterizedCacheKey, imageScale, isEffectiveDark,
                                                originalUserSize);
      }
      else {
        image = ImageLoader.loadPngFromClassResource(effectivePath, null, classLoader, imageScale, originalUserSize);
      }

      if (start != -1) {
        IconLoadMeasurer.loadFromResources.end(start);
      }
      if (loadingStart != -1) {
        IconLoadMeasurer.addLoading(isSvg, loadingStart);
      }

      if (image == null) {
        return null;
      }
      return ImageLoader.convertImage(image, filters, flags, scaleContext, isUpScaleNeeded, StartupUiUtil.isJreHiDPI(scaleContext),
                                      imageScale, isSvg);
    }
    catch (IOException e) {
      Logger.getInstance(RasterizedImageDataLoader.class).debug(e);
      return null;
    }
  }
}
