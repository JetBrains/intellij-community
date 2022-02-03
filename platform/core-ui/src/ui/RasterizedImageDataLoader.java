// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ImageDataByUrlLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.IconTransform;
import com.intellij.ui.icons.ImageDataLoader;
import com.intellij.ui.icons.ImageDescriptor;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.StartupUiUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.ImageFilter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

final class RasterizedImageDataLoader implements ImageDataLoader {
  private final int cacheKey;

  private final String path;
  private final WeakReference<ClassLoader> classLoaderRef;
  private final String originalPath;
  private final WeakReference<ClassLoader> originalClassLoaderRef;

  private final int imageFlags;
  private boolean myPatched = false;

  RasterizedImageDataLoader(@NotNull String path,
                            @NotNull WeakReference<ClassLoader> classLoaderRef,
                            @NotNull String originalPath,
                            @NotNull WeakReference<ClassLoader> originalClassLoaderRef,
                            int cacheKey,
                            int imageFlags) {
    this.cacheKey = cacheKey;

    this.path = path;
    this.classLoaderRef = classLoaderRef;
    this.originalPath = originalPath;
    this.originalClassLoaderRef = originalClassLoaderRef;

    this.imageFlags = imageFlags;
  }

  private static @NotNull String normalizePath(String patchedPath) {
    return patchedPath.charAt(0) == '/' ? patchedPath.substring(1) : patchedPath;
  }

  static @NotNull ImageDataLoader createPatched(@NotNull String originalPath,
                                                @NotNull WeakReference<ClassLoader> originalClassLoaderRef,
                                                @NotNull Pair<String, ClassLoader> patched,
                                                int cacheKey,
                                                int imageFlags) {
    String effectivePath = normalizePath(patched.first);
    WeakReference<ClassLoader> effectiveClassLoaderRef = patched.second == null ? originalClassLoaderRef : new WeakReference<>(patched.second);
    RasterizedImageDataLoader loader = new RasterizedImageDataLoader(effectivePath, effectiveClassLoaderRef, originalPath, originalClassLoaderRef, cacheKey, imageFlags);
    loader.setPatched(true);
    return loader;
  }

  private void setPatched(boolean patched) {
    myPatched = patched;
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
    boolean isSvg = cacheKey != 0;
    // use cache key only if path to image is not customized
    return loadRasterized(path, filters, classLoader, flags, scaleContext, isSvg, originalPath == path ? cacheKey : 0, imageFlags, myPatched);
  }

  @Override
  public @Nullable URL getURL() {
    ClassLoader classLoader = classLoaderRef.get();
    return classLoader == null ? null : classLoader.getResource(path);
  }

  @Override
  public @Nullable ImageDataLoader patch(@NotNull String originalPath, @NotNull IconTransform transform) {
    ClassLoader classLoader = classLoaderRef.get();
    Pair<String, ClassLoader> patched = transform.patchPath(originalPath, classLoader);
    if (patched == null) {
      if (path != this.originalPath && this.originalPath.equals(normalizePath(originalPath))) {
        return new RasterizedImageDataLoader(this.originalPath, this.originalClassLoaderRef,
                                             this.originalPath, this.originalClassLoaderRef,
                                             cacheKey, imageFlags);
      }
      return null;
    }

    if (patched.first.startsWith("file:/")) {
      ClassLoader effectiveClassLoader = patched.second == null ? classLoader : patched.second;
      try {
        return new ImageDataByUrlLoader(new URL(patched.first), patched.first, effectiveClassLoader, false);
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return createPatched(this.originalPath, this.originalClassLoaderRef, patched, cacheKey, imageFlags);
    }
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
                                                boolean isSvg,
                                                int rasterizedCacheKey,
                                                @MagicConstant(flagsFromClass = ImageDescriptor.class) int imageFlags,
                                                boolean patched) {
    long loadingStart = StartUpMeasurer.getCurrentTimeIfEnabled();

    // Prefer retina images for HiDPI scale, because downscaling
    // retina images provides a better result than up-scaling non-retina images.
    float pixScale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);

    int dotIndex = path.lastIndexOf('.');
    String name = dotIndex < 0 ? path : path.substring(0, dotIndex);
    float scale = ImageLoader.adjustScaleFactor((flags & ImageLoader.ALLOW_FLOAT_SCALING) == ImageLoader.ALLOW_FLOAT_SCALING, pixScale);

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

    List<Pair<String,Float>> effectivePaths;
    if (patched) {
      Pair<String, Float> retinaDark = Pair.create(name + "@2x_dark." + ext, isSvg ? scale : 2);
      Pair<String, Float> dark       = Pair.create(name + "_dark." + ext, isSvg ? scale : 1);
      Pair<String, Float> retina     = Pair.create(name + "@2x." + ext, isSvg ? scale : 2);
      Pair<String, Float> plain      = Pair.create(path, isSvg ? scale : 1);
      effectivePaths = isRetina && isDark ? Arrays.asList(retinaDark, dark, retina, plain)
                                          : isDark ? Arrays.asList(dark, plain)
                                                   : isRetina ? Arrays.asList(retina, plain)
                                                              : List.of(plain);
    } else {
      effectivePaths = List.of(Pair.create(effectivePath, imageScale));
    }

    ImageLoader.Dimension2DDouble originalUserSize = new ImageLoader.Dimension2DDouble(0, 0);
    try {
      long start = StartUpMeasurer.getCurrentTimeIfEnabled();
      Image image = null;
      for (Pair<String, Float> effPath: effectivePaths) {
        String pathToImage = effPath.first;
        float imgScale = effPath.second;
        if (isSvg) {
          image = SVGLoader.loadFromClassResource(null, classLoader, pathToImage, rasterizedCacheKey, imgScale, isEffectiveDark,
                                                  originalUserSize);
        }
        else {
          image = ImageLoader.loadPngFromClassResource(pathToImage, null, classLoader, imgScale, originalUserSize);
        }

        if (image != null) break;
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
      return ImageLoader.convertImage(image, filters, flags, scaleContext, !isSvg, StartupUiUtil.isJreHiDPI(scaleContext),
                                      imageScale, isSvg);
    }
    catch (IOException e) {
      Logger.getInstance(RasterizedImageDataLoader.class).debug(e);
      return null;
    }
  }
}
