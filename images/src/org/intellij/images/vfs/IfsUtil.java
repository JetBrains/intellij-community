// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.vfs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleContextCache;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.EDT;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.common.bytesource.ByteSourceArray;
import org.apache.commons.imaging.formats.ico.IcoImageParser;
import org.intellij.images.editor.ImageDocument;
import org.intellij.images.editor.ImageDocument.ScaledImageProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import static com.intellij.reference.SoftReference.dereference;
import static com.intellij.ui.scale.ScaleType.OBJ_SCALE;

/**
 * Image loader utility.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class IfsUtil {
  private static final Logger LOG = Logger.getInstance(IfsUtil.class);

  public static final String ICO_FORMAT = "ico";
  public static final String SVG_FORMAT = "svg";

  private static final Key<Pair<Long, Long>> TIME_MODIFICATION_STAMP_KEY = Key.create("Image.timeModificationStamp");
  private static final Key<String> FORMAT_KEY = Key.create("Image.format");
  private static final Key<SoftReference<ScaledImageProvider>> IMAGE_PROVIDER_REF_KEY = Key.create("Image.bufferedImageProvider");
  private static final IcoImageParser ICO_IMAGE_PARSER = new IcoImageParser();

  /**
   * Load image data for file and put user data attributes into file.
   *
   * @param file File
   * @return true if file image is loaded.
   * @throws IOException if image can not be loaded
   */
  private static boolean refresh(@NotNull VirtualFile file) throws IOException {
    Pair<Long, Long> loadedTimeModificationStamp = file.getUserData(TIME_MODIFICATION_STAMP_KEY);
    Pair<Long, Long> actualTimeModificationStamp = Pair.create(file.getTimeStamp(), file.getModificationStamp());
    SoftReference<ScaledImageProvider> imageProviderRef = file.getUserData(IMAGE_PROVIDER_REF_KEY);
    if (!actualTimeModificationStamp.equals(loadedTimeModificationStamp) || dereference(imageProviderRef) == null) {
      try {
        final byte[] content = file.contentsToByteArray();
        file.putUserData(IMAGE_PROVIDER_REF_KEY, null);

        if (ICO_FORMAT.equalsIgnoreCase(file.getExtension())) {
          try {
            final BufferedImage image = ICO_IMAGE_PARSER.getBufferedImage(new ByteSourceArray(content), null);
            file.putUserData(FORMAT_KEY, ICO_FORMAT);
            file.putUserData(IMAGE_PROVIDER_REF_KEY, new SoftReference<>((scale, ancestor) -> image));
            return true;
          }
          catch (ImageReadException ignore) { }
        }

        if (isSVG(file)) {
          final Ref<URL> url = Ref.create();
          try {
            url.set(new File(file.getPath()).toURI().toURL());
          }
          catch (MalformedURLException ex) {
            LOG.warn(ex.getMessage());
          }

          try {
            // ensure svg can be displayed
            SVGLoader.INSTANCE.load(url.get(), new ByteArrayInputStream(content), 1.0f);
          }
          catch (Throwable t) {
            LOG.warn(url.get() + " " + t.getMessage());
            return false;
          }

          file.putUserData(FORMAT_KEY, SVG_FORMAT);
          file.putUserData(IMAGE_PROVIDER_REF_KEY, new SoftReference<>(new ImageDocument.CachedScaledImageProvider() {
            final ScaleContextCache<Image> cache = new ScaleContextCache<>((ctx) -> {
              try {
                return SVGLoader.loadHiDPI(url.get(), new ByteArrayInputStream(content), ctx);
              }
              catch (Throwable t) {
                LOG.warn(url.get() + " " + t.getMessage());
                return null;
              }
            });
            @Override
            public void clearCache() {
              cache.clear();
            }

            @Override
            public BufferedImage apply(Double zoom, Component ancestor) {
              ScaleContext ctx = ScaleContext.create(ancestor);
              ctx.setScale(OBJ_SCALE.of(zoom));
              return (BufferedImage)cache.getOrProvide(ctx);
            }
          }));
          return true;
        }

        InputStream inputStream = new ByteArrayInputStream(content, 0, content.length);
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
          Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
          if (imageReaders.hasNext()) {
            ImageReader imageReader = imageReaders.next();
            try {
              file.putUserData(FORMAT_KEY, imageReader.getFormatName());
              ImageReadParam param = imageReader.getDefaultReadParam();
              imageReader.setInput(imageInputStream, true, true);
              int minIndex = imageReader.getMinIndex();
              BufferedImage image = imageReader.read(minIndex, param);
              file.putUserData(IMAGE_PROVIDER_REF_KEY, new SoftReference<>((zoom, ancestor) -> image));
              return true;
            }
            finally {
              imageReader.dispose();
            }
          }
        }
      } finally {
        // We perform loading no more needed
        file.putUserData(TIME_MODIFICATION_STAMP_KEY, actualTimeModificationStamp);
      }
    }
    return false;
  }

  @Nullable
  public static BufferedImage getImage(@NotNull VirtualFile file) throws IOException {
    return getImage(file, null);
  }

  @Nullable
  public static BufferedImage getImage(@NotNull VirtualFile file, @Nullable Component ancestor) throws IOException {
    ScaledImageProvider imageProvider = getImageProvider(file);
    if (imageProvider == null) return null;
    return imageProvider.apply(1d, ancestor);
  }

  @Nullable
  public static ScaledImageProvider getImageProvider(@NotNull VirtualFile file) throws IOException {
    refresh(file);
    SoftReference<ScaledImageProvider> imageProviderRef = file.getUserData(IMAGE_PROVIDER_REF_KEY);
    return dereference(imageProviderRef);
  }

  public static boolean isSVG(@Nullable VirtualFile file) {
    return file != null && SVG_FORMAT.equalsIgnoreCase(file.getExtension());
  }

  @Nullable
  public static String getFormat(@NotNull VirtualFile file) throws IOException {
    refresh(file);
    return file.getUserData(FORMAT_KEY);
  }

  public static @NlsSafe String getReferencePath(Project project, VirtualFile file) {
    if (EDT.isCurrentThreadEdt()) {
      LOG.warn("FIXME IDEA-341171 org.intellij.images.vfs.IfsUtil#getReferencePath on EDT");
      return file.getName();
    }
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile sourceRoot = fileIndex.getSourceRootForFile(file);
    if (sourceRoot != null) {
      return getRelativePath(file, sourceRoot);
    }

    VirtualFile root = fileIndex.getContentRootForFile(file);
    if (root != null) {
      return getRelativePath(file, root);
    }

    return file.getPath();
  }

  private static String getRelativePath(final VirtualFile file, final VirtualFile root) {
    if (root.equals(file)) {
      return file.getPath();
    }
    return "/" + VfsUtilCore.getRelativePath(file, root, '/');
  }
}
