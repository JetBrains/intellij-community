package com.intellij.localvcs;

import com.intellij.localVcs.common.CouldNotLoadLvcsException;
import com.intellij.openapi.localVcs.LocalVcsBundle;
import com.intellij.util.containers.IntObjectCache;
import com.intellij.util.io.CachedFile;
import com.intellij.util.io.CachedRandomAccessFile;
import com.intellij.util.io.CachingStrategy;
import com.intellij.util.io.SharedCachingStrategy;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NonNls;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class ContentStorage {
  private CachedFile myStore;

  private TIntIntHashMap myIds2Offsets;
  private IntObjectCache<byte[]> myContentCache;

  public ContentStorage(File f) throws IOException, CouldNotLoadLvcsException {
    // todo why SharedCachingStrategy?
    CachingStrategy s = new SharedCachingStrategy(16 * 1024, 2 * 1024 * 1024);

    myStore = new CachedRandomAccessFile(f, s);
    myIds2Offsets = new TIntIntHashMap(1024);
    myContentCache = new IntObjectCache<byte[]>(200);
    load();
  }

  private void load() throws IOException, CouldNotLoadLvcsException {
    myStore.seek(0);
    final int length = (int)myStore.length();
    int offset;
    while ((offset = (int)myStore.getFilePointer()) < length) {
      final ContentDescriptor cd = new ContentDescriptor(myStore, true);
      if (!cd.isOk()) {
        throw new CouldNotLoadLvcsException(LocalVcsBundle.message("exception.text.local.history.content.store.is.corrupted"));
      }
      if (!cd.isRemoved()) {
        myIds2Offsets.put(cd.getId(), offset);
      }
    }
  }

  public void close() throws IOException {
    myStore.close();
  }

  public void save() throws IOException {
    myStore.flush();
  }

  public void storeContent(int id, byte[] content) throws IOException {
    int offset = (int)myStore.length();

    myStore.seek(offset);
    ContentDescriptor d = new ContentDescriptor(id, content);

    d.saveTo(myStore);

    myIds2Offsets.put(id, offset);
    myContentCache.cacheObject(id, content);
  }

  public byte[] loadContent(int id) throws IOException {
    byte[] content = findContentInCache(id);
    if (content != null) return content;

    int offset = getOffsetFor(id);

    myStore.seek(offset);
    ContentDescriptor cd = new ContentDescriptor(myStore, false);

    content = cd.getContent();
    myContentCache.cacheObject(id, content);

    return content;
  }

  public void removeContent(int id) throws IOException {
    int offset = getOffsetFor(id);
    myStore.seek(offset);

    ContentDescriptor d = new ContentDescriptor(myStore, true);
    d.markRemoved();

    myStore.seek(offset);
    d.saveTo(myStore); // todo what do we save removed content for?

    myContentCache.remove(id);
    myIds2Offsets.remove(id);
  }

  private byte[] findContentInCache(int id) {
    return myContentCache.tryKey(id);
  }

  private int getOffsetFor(int id) {
    assert hasContent(id);
    return myIds2Offsets.get(id);
  }

  public boolean hasContent(int id) {
    return myIds2Offsets.containsKey(id);
  }

  private static class ContentDescriptor {
    private static final byte REMOVED_MASK = 1;
    private static final byte COMPRESSED_MASK = 2;
    private int myId;
    private int myChecksum;
    private byte myFlags;
    private byte[] myContent;
    private boolean myIsOk;

    ContentDescriptor(CachedFile file, final boolean loadOnly) throws IOException {
      myId = file.readInt();
      myChecksum = file.readInt();
      myFlags = file.readByte();
      int length = file.readInt();
      myContent = new byte[length];
      file.read(myContent);
      myIsOk = calcChecksum() == myChecksum;
      if (!loadOnly && myIsOk && isCompressed()) {
        myContent = SourceCodeCompressor.decompress(myContent);
      }
    }

    ContentDescriptor(int id, byte[] content) {
      myId = id;
      myFlags = 0;
      final byte[] compressedContent = SourceCodeCompressor.compress(content);
      if (compressedContent.length >= content.length) {
        myContent = content;
      }
      else {
        myContent = compressedContent;
        markCompressed();
      }
      myChecksum = calcChecksum();
    }

    public void saveTo(CachedFile file) throws IOException {
      file.writeInt(myId);
      file.writeInt(myChecksum);
      file.writeByte(myFlags);
      if (!isRemoved()) {
        file.writeInt(myContent.length);
        file.write(myContent);
      }
    }

    boolean isOk() {
      return myIsOk;
    }

    int getId() {
      return myId;
    }

    void setId(int id) {
      myId = id;
    }

    int getChecksum() {
      return myChecksum;
    }

    boolean isRemoved() {
      return (myFlags & ContentDescriptor.REMOVED_MASK) != 0;
    }

    void markRemoved() {
      myFlags |= ContentDescriptor.REMOVED_MASK;
    }

    boolean isCompressed() {
      return (myFlags & ContentDescriptor.COMPRESSED_MASK) != 0;
    }

    void markCompressed() {
      myFlags |= ContentDescriptor.COMPRESSED_MASK;
    }

    int getLength() {
      return myContent.length;
    }

    byte[] getContent() {
      return myContent;
    }

    int calcChecksum() {
      int checksum = 31415926 + myContent.length;
      for (byte b : myContent) {
        checksum = ((checksum << 5) + checksum) ^ b;
      }
      return checksum;
    }
  }

  private static class SourceCodeCompressor {
    private final static VaultOutputStream OUTPUT;
    private final static byte[] PRESET_BUF;
    private final static Deflater DEFLATER;
    private final static Inflater INFLATER;
    private final static byte[] INFLATE_BUFFER;

    static {
      @NonNls final String preset_buf_string =
        "                   ;\r\n\r\n\r\n\r\n\n\n\n { {\r\n }\r\n = == != < > >= <= ? : ++ += -- -= [] [i] () ()) ())) (); ()); ())); () {" +
        "// /* /** */ * opyright (c)package com.import java.utilimport javax.swingimport java.awt" +
        "import com.intellijimport org.import gnu.*;new super(this(public interface extends implements " +
        "public abstract class public class private final static final protected synchronized my our " +
        "instanceof throws return return;if (else {for (while (do {break;continue;throw try {catch (finally {" +
        "null;true;false;void byte short int long boolean float double Object String Class System.Exception Throwable" +
        "getsetputcontainsrunashCodeequalslengthsizeremoveaddclearwritereadopenclosename=\"getNamerray" +
        "istollectionHashMapSetnpututputtreamhildrenarentrootitemctionefaultrojectomponentpplicationerializ" +
        "Created by IntelliJ IDEA.@author Logger ettingsFontialog JPanel JLabel JCheckBox JComboBox JList JSpinner " +
        "<html>/>\r\n<head</head><body bgcolor=</body>table<?xml version=\"<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML" +
        "titleframecaret<a href=\"http://</a><div </div><td </td><tr </tr><p </p><hscripttext/css<img src=" +
        "<!--><link rel=width=height=align=span=centerrightleftstyle=celljsp:rootxmlns:avascript";
      PRESET_BUF = preset_buf_string.getBytes();
      OUTPUT = new SourceCodeCompressor.VaultOutputStream();
      DEFLATER = new Deflater(Deflater.BEST_COMPRESSION);
      INFLATER = new Inflater();
      INFLATE_BUFFER = new byte[4096];
    }

    public static synchronized byte[] compress(byte[] source) {
      try {
        SourceCodeCompressor.DEFLATER.reset();
        SourceCodeCompressor.DEFLATER.setDictionary(SourceCodeCompressor.PRESET_BUF);
        try {
          DeflaterOutputStream output = null;
          try {
            output = new DeflaterOutputStream(SourceCodeCompressor.OUTPUT, SourceCodeCompressor.DEFLATER);
            output.write(source);
          }
          finally {
            if (output != null) {
              output.close();
            }
          }
        }
        catch (IOException e) {
          return source;
        }
        return SourceCodeCompressor.OUTPUT.toByteArray();
      }
      finally {
        SourceCodeCompressor.OUTPUT.reset();
      }
    }

    public static synchronized byte[] decompress(byte[] compressed) throws IOException {
      SourceCodeCompressor.INFLATER.reset();
      InflaterInputStream input = null;
      try {
        input = new InflaterInputStream(new ByteArrayInputStream(compressed), SourceCodeCompressor.INFLATER);
        final int b = input.read();
        if (b == -1) {
          SourceCodeCompressor.INFLATER.setDictionary(SourceCodeCompressor.PRESET_BUF);
        }
        else {
          SourceCodeCompressor.OUTPUT.write(b);
        }
        int readBytes;
        while ((readBytes = input.read(SourceCodeCompressor.INFLATE_BUFFER)) > 0) {
          SourceCodeCompressor.OUTPUT.write(SourceCodeCompressor.INFLATE_BUFFER, 0, readBytes);
        }
        return SourceCodeCompressor.OUTPUT.toByteArray();
      }
      finally {
        if (input != null) {
          input.close();
        }
        SourceCodeCompressor.OUTPUT.reset();
      }
    }

    private static class VaultOutputStream extends ByteArrayOutputStream {

      private static final int MIN_BUF_SIZE = 0x10000;
      private byte[] MIN_BUFFER;

      public VaultOutputStream() {
        super(SourceCodeCompressor.VaultOutputStream.MIN_BUF_SIZE);
        MIN_BUFFER = buf;
      }

      @SuppressWarnings({"NonSynchronizedMethodOverridesSynchronizedMethod"})
      public void reset() {
        count = 0;
        buf = MIN_BUFFER;
      }
    }
  }
}
