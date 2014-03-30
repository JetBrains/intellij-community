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

package com.intellij.codeInsight.preview;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.reference.SoftReference;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author spleaner
 */
public class ImagePreviewComponent extends JPanel implements PreviewHintComponent {
  private static final Key<Long> TIMESTAMP_KEY = Key.create("Image.timeStamp");
  private static final Key<SoftReference<BufferedImage>> BUFFERED_IMAGE_REF_KEY = Key.create("Image.bufferedImage");
  private static final Key<String> FORMAT_KEY = Key.create("Image.format");

  private static final List<String> supportedExtensions = Arrays.asList(ImageIO.getReaderFormatNames());
  @NotNull
  private final BufferedImage myImage;

  /**
   * @param image buffered image
   * @param imageFileSize File length in bytes.
   */
  private ImagePreviewComponent(@NotNull final BufferedImage image, final long imageFileSize) {
    setLayout(new BorderLayout());

    myImage = image;
    add(new ImageComp(), BorderLayout.CENTER);
    add(createLabel(image, imageFileSize), BorderLayout.SOUTH);

    setBackground(UIUtil.getToolTipBackground());
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
  }

  @Override
  @TestOnly
  public boolean isEqualTo(@Nullable PreviewHintComponent other) {
    if (!(other instanceof ImagePreviewComponent)) {
      return false;
    }
    ImagePreviewComponent otherPreview = (ImagePreviewComponent)other;
    if (myImage.getWidth() == otherPreview.myImage.getWidth() && myImage.getHeight() == otherPreview.myImage.getHeight()) {
      for (int x = 0; x < myImage.getWidth(); x++) {
        for (int y = 0; y < myImage.getHeight(); y++) {
          if (myImage.getRGB(x, y) != otherPreview.myImage.getRGB(x, y)) {
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  @NotNull
  private static JLabel createLabel(@NotNull final BufferedImage image, long imageFileSize) {
    final int width = image.getWidth();
    final int height = image.getHeight();
    final ColorModel colorModel = image.getColorModel();
    final int i = colorModel.getPixelSize();
    return new JLabel(String.format("%dx%d, %dbpp, %s", width, height, i, StringUtil.formatFileSize(imageFileSize)));
  }

  @SuppressWarnings({"AutoUnboxing"})
  private static boolean refresh(@NotNull VirtualFile file) throws IOException {
    Long loadedTimeStamp = file.getUserData(TIMESTAMP_KEY);
    SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
    if (loadedTimeStamp == null || loadedTimeStamp < file.getTimeStamp() || SoftReference.dereference(imageRef) == null) {
      try {
        final byte[] content = file.contentsToByteArray();
        InputStream inputStream = new ByteArrayInputStream(content, 0, content.length);
        ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream);
        try {
          Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
          if (imageReaders.hasNext()) {
            ImageReader imageReader = imageReaders.next();
            try {
              file.putUserData(FORMAT_KEY, imageReader.getFormatName());
              ImageReadParam param = imageReader.getDefaultReadParam();
              imageReader.setInput(imageInputStream, true, true);
              int minIndex = imageReader.getMinIndex();
              BufferedImage image = imageReader.read(minIndex, param);
              file.putUserData(BUFFERED_IMAGE_REF_KEY, new SoftReference<BufferedImage>(image));
              return true;
            }
            finally {
              imageReader.dispose();
            }
          }
        }
        finally {
          imageInputStream.close();
        }
      }
      finally {
        // We perform loading no more needed
        file.putUserData(TIMESTAMP_KEY, System.currentTimeMillis());
      }
    }
    return false;
  }

  public static JComponent getPreviewComponent(@Nullable final PsiElement parent) {
    if (parent == null) {
      return null;
    }
    final PsiReference[] references = parent.getReferences();
    for (final PsiReference reference : references) {
      final PsiElement fileItem = reference.resolve();
      if (fileItem instanceof PsiFileSystemItem) {
        final PsiFileSystemItem item = (PsiFileSystemItem)fileItem;
        if (!item.isDirectory()) {
          final VirtualFile file = item.getVirtualFile();
          if (file != null && supportedExtensions.contains(file.getExtension())) {
            try {
              refresh(file);
              SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
              final BufferedImage image = SoftReference.dereference(imageRef);
              if (image != null) {
                return new ImagePreviewComponent(image, file.getLength());
              }
            }
            catch (IOException ignored) {
              // nothing
            }
          }
        }
      }
    }

    return null;
  }

  /**
   * This method doesn't use caching, so if you want to use it then you should consider implementing external cache.
   */
  public static ImagePreviewComponent getPreviewComponent(@NotNull final BufferedImage image, final long imageFileSize) {
    return new ImagePreviewComponent(image, imageFileSize);
  }

  private class ImageComp extends JComponent {
    private final Dimension myPreferredSize;

    private ImageComp() {
      if (myImage.getWidth() > 300 || myImage.getHeight() > 300) {
        // will make image smaller
        final float factor = 300.0f / Math.max(myImage.getWidth(), myImage.getHeight());
        myPreferredSize = new Dimension((int)(myImage.getWidth() * factor), (int)(myImage.getHeight() * factor));
      }
      else {
        myPreferredSize = new Dimension(myImage.getWidth(), myImage.getHeight());
      }
    }

    @Override
    public void paint(final Graphics g) {
      super.paint(g);
      Rectangle r = getBounds();
      final int width = myImage.getWidth();
      final int height = myImage.getHeight();

      g.drawImage(myImage, 0, 0, r.width > width ? width : r.width, r.height > height ? height : r.height, this);
    }

    @Override
    public Dimension getPreferredSize() {
      return myPreferredSize;
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }
  }
}
