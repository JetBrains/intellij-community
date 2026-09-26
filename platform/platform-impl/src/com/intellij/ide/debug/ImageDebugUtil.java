// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.debug;

import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Helper methods invoked by the IDE debugger inside the debuggee JVM to serialize IDE-specific images and icons.
 * <p>
 * The standard debugger path uses {@code com.intellij.rt.debugger.ImageSerializer} together with
 * {@code com.intellij.debugger.ui.tree.render.ImageObjectRenderer} and
 * {@code com.intellij.debugger.ui.tree.render.IconObjectRenderer}, which only transfer PNG bytes.
 * This helper is used for IDE-specific HiDPI wrappers whose logical size may differ from the raw raster size,
 * so the data also includes {@code logicalWidth} and {@code logicalHeight}.
 */
@SuppressWarnings("unused")
final class ImageDebugUtil {
  private static final byte IMAGE_FORMAT_VERSION = 1;

  private ImageDebugUtil() {
  }

  static void ensureLoaded() {
  }

  public static @NotNull String imageToBytes(@NotNull Image image) throws IOException {
    return serialize(
      ImageUtil.getUserWidth(image),
      ImageUtil.getUserHeight(image),
      toPngBytes(ImageUtil.toBufferedImage(image, false))
    );
  }

  public static @NotNull String iconToBytes(@NotNull Icon icon) throws IOException {
    return serialize(
      icon.getIconWidth(),
      icon.getIconHeight(),
      toPngBytes(IconUtil.toBufferedImage(icon, false))
    );
  }

  public static @Nullable String iconToBytesPreview(@NotNull Icon icon, int maxSize) throws IOException {
    if (icon.getIconHeight() > maxSize || icon.getIconWidth() > maxSize) {
      return null;
    }
    return serialize(
      icon.getIconWidth(),
      icon.getIconHeight(),
      toPngBytes(IconUtil.toBufferedImage(icon, false))
    );
  }

  private static byte @NotNull [] toPngBytes(@NotNull BufferedImage image) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "png", output)) {
      throw new IOException("PNG writer is not available");
    }
    return output.toByteArray();
  }

  private static @NotNull String serialize(int logicalWidth, int logicalHeight, byte @NotNull [] pngBytes) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (DataOutputStream stream = new DataOutputStream(output)) {
      stream.writeByte(IMAGE_FORMAT_VERSION);
      stream.writeInt(logicalWidth);
      stream.writeInt(logicalHeight);
      stream.writeInt(pngBytes.length);
      stream.write(pngBytes);
    }
    return output.toString(StandardCharsets.ISO_8859_1);
  }
}
