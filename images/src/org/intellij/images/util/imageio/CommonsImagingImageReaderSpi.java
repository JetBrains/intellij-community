// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.util.imageio;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.apache.commons.imaging.*;
import org.apache.commons.imaging.common.bytesource.ByteSource;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class CommonsImagingImageReaderSpi extends ImageReaderSpi {

  private final ThreadLocal<ImageFormat> myFormat = new ThreadLocal<>();
  private final List<ImageFormat> myFormats;

  public CommonsImagingImageReaderSpi() {
    super();
    vendorName = "JetBrains, s.r.o.";
    version = "1.0";

    // todo standard GIF/BMP formats can be optionally skipped as well
    // JPEG is skipped due to Exception: cannot read or write JPEG images. (JpegImageParser.java:92)
    // tiff reader seems to be broken
    // PNG reader has bugs with well-compressed PNG images, use standard one instead
    myFormats = new ArrayList<>(Arrays.asList(ImageFormats.values()));
    myFormats.removeAll(Arrays.asList(ImageFormats.UNKNOWN,
                                         ImageFormats.JPEG,
                                         ImageFormats.TIFF,
                                         ImageFormats.PNG));

    names = new String[myFormats.size() * 2];
    suffixes = new String[myFormats.size()];
    MIMETypes = new String[myFormats.size()];
    pluginClassName = MyImageReader.class.getName();
    inputTypes = new Class[] {ImageInputStream.class};
    for (int i = 0, allFormatsLength = myFormats.size(); i < allFormatsLength; i++) {
      final ImageFormat format = myFormats.get(i);
      names[2 * i] = StringUtil.toLowerCase(format.getExtension());
      names[2 * i + 1] = StringUtil.toLowerCase(format.getExtension());
      suffixes[i] = names[2 * i];
      MIMETypes[i] = "image/" + names[2 * i];
    }
  }

  @Override
  public String getDescription(Locale locale) {
    return "Apache Commons Imaging adapter reader";
  }

  @Override
  public boolean canDecodeInput(Object input) throws IOException {
    if (!(input instanceof ImageInputStream stream)) {
      return false;
    }
    try {
      final ImageFormat imageFormat = Imaging.guessFormat(new MyByteSource(stream));
      if (myFormats.contains(imageFormat)) {
        myFormat.set(imageFormat);
        return true;
      }
      return false;
    }
    catch (ImageReadException e) {
      throw new IOException(e);
    }
  }

  @Override
  public ImageReader createReaderInstance(Object extension) {
    return new MyImageReader(this, myFormat.get());
  }

  private static class MyByteSource extends ByteSource {
    private final ImageInputStream myStream;

    MyByteSource(final ImageInputStream stream) {
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
    public byte[] getBlock(final long start, final int length) throws IOException {
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

  private static final class MyImageReader extends ImageReader {
    private byte[] myBytes;
    private ImageInfo myInfo;
    private BufferedImage[] myImages;
    private final ImageFormat myDefaultFormat;

    private MyImageReader(final CommonsImagingImageReaderSpi provider, final ImageFormat imageFormat) {
      super(provider);
      myDefaultFormat = imageFormat == null? ImageFormats.UNKNOWN : imageFormat;
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
          myInfo = Imaging.getImageInfo(getBytes());
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
          List<BufferedImage> images = Imaging.getAllBufferedImages(getBytes());
          myImages = images.toArray(new BufferedImage[0]);
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
      return input == null? myDefaultFormat.getName() : getInfo().getFormat().getName();
    }
  }
}
