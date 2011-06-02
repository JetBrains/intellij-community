/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.intellij.images.util.imageio;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.apache.sanselan.ImageFormat;
import org.apache.sanselan.ImageInfo;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.byteSources.ByteSource;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;

public class SanselanImageReaderSpi extends ImageReaderSpi {

  private ThreadLocal<ImageFormat> myFormat = new ThreadLocal<ImageFormat>();

  public SanselanImageReaderSpi() {
    super();
    vendorName = "JetBrains, s.r.o.";
    version = "1.0";

    // todo standard PNG/TIFF/JEPG/GIF/BMP formats can be optionally skipped as well
    final ImageFormat[] allFormats = ArrayUtil.remove(ImageFormat.getAllFormats(), ImageFormat.IMAGE_FORMAT_UNKNOWN);
    names = new String[allFormats.length * 2];
    suffixes = new String[allFormats.length];
    MIMETypes = new String[allFormats.length];
    pluginClassName = MyImageReader.class.getName();
    inputTypes = new Class[] {ImageInputStream.class};
    for (int i = 0, allFormatsLength = allFormats.length; i < allFormatsLength; i++) {
      final ImageFormat format = allFormats[i];
      names[2 * i] = format.extension.toLowerCase();
      names[2 * i + 1] = format.extension.toUpperCase();
      suffixes[i] = names[2 * i];
      MIMETypes[i] = "image/" + names[2 * i];
    }
  }

  public String getDescription(Locale locale) {
    return "Apache Sanselan project based image reader";
  }

  public boolean canDecodeInput(Object input) throws IOException {
    if (!(input instanceof ImageInputStream)) {
      return false;
    }
    final ImageInputStream stream = (ImageInputStream)input;
    try {
      final ImageFormat imageFormat = Sanselan.guessFormat(new MyByteSource(stream));
      if (imageFormat != null) {
        myFormat.set(imageFormat);
        return true;
      }
      return false;
    }
    catch (ImageReadException e) {
      throw new IOException(e);
    }
  }

  public ImageReader createReaderInstance(Object extension) {
    return new MyImageReader(this, myFormat.get());
  }

  private static class MyByteSource extends ByteSource {
    private final ImageInputStream myStream;

    public MyByteSource(final ImageInputStream stream) {
      super(stream.toString());
      myStream = stream;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      myStream.seek(0);
      return new InputStream() {
        @Override
        public int read() throws IOException {
          return myStream.read();
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
          return myStream.read(b, off, len);
        }
      };
    }

    @Override
    public byte[] getBlock(final int start, final int length) throws IOException {
      myStream.seek(start);
      final byte[] bytes = new byte[length];
      final int read = myStream.read(bytes);
      return ArrayUtil.realloc(bytes, read);
    }

    @Override
    public byte[] getAll() throws IOException {
      return FileUtil.loadBytes(getInputStream());
    }

    @Override
    public long getLength() throws IOException {
      return myStream.length();
    }

    @Override
    public String getDescription() {
      return myStream.toString();
    }
  }

  private static class MyImageReader extends ImageReader {
    private byte[] myBytes;
    private ImageInfo myInfo;
    private BufferedImage[] myImages;
    private final ImageFormat myDefaultFormat;

    public MyImageReader(final SanselanImageReaderSpi provider, final ImageFormat imageFormat) {
      super(provider);
      myDefaultFormat = imageFormat == null? ImageFormat.IMAGE_FORMAT_UNKNOWN : imageFormat;
    }

    @Override
    public void dispose() {
      myBytes = null;
      myInfo = null;
      myImages = null;
    }

    @Override
    public void setInput(final Object input, final boolean seekForwardOnly, final boolean ignoreMetadata) {
      super.setInput(input, seekForwardOnly, ignoreMetadata);
      myBytes = null;
      myInfo = null;
      myImages = null;
    }

    private ImageInfo getInfo() throws IOException {
      if (myInfo == null) {
        try {
          myInfo = Sanselan.getImageInfo(getBytes());
        }
        catch (ImageReadException e) {
          throw new IOException(e);
        }
      }
      return myInfo;
    }

    private byte[] getBytes() throws IOException {
      if (myBytes == null) {
        final ImageInputStream stream = (ImageInputStream)input;
        myBytes = new MyByteSource(stream).getAll();
      }
      return myBytes;
    }

    private BufferedImage[] getImages() throws IOException {
      if (myImages == null) {
        try {
          final ArrayList<BufferedImage> images = Sanselan.getAllBufferedImages(getBytes());
          myImages = images.toArray(new BufferedImage[images.size()]);
        }
        catch (ImageReadException e) {
          throw new IOException(e);
        }
      }
      return myImages;
    }

    @Override
    public int getNumImages(final boolean allowSearch) throws IOException {
      return getInfo().getNumberOfImages();
    }

    @Override
    public int getWidth(final int imageIndex) throws IOException {
      return getInfo().getWidth();
    }

    @Override
    public int getHeight(final int imageIndex) throws IOException {
      return getInfo().getHeight();
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(final int imageIndex) throws IOException {
      return Collections.singletonList(ImageTypeSpecifier.createFromRenderedImage(getImages()[imageIndex])).iterator();
    }

    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
      return null;
    }

    @Override
    public IIOMetadata getImageMetadata(final int imageIndex) throws IOException {
      return null;
    }

    @Override
    public BufferedImage read(final int imageIndex, final ImageReadParam param) throws IOException {
      return getImages()[imageIndex];
    }

    @Override
    public String getFormatName() throws IOException {
      // return default if called before setInput
      return input == null? myDefaultFormat.name : getInfo().getFormat().name;
    }
  }
}
