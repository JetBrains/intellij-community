package com.intellij.psi.stubs;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.StringRef;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author yole
 */
public class StubInputStream extends DataInputStream {
  private final PersistentStringEnumerator myNameStorage;
  private final byte[] myStringIOBuffer = IOUtil.allocReadWriteUTFBuffer();

  public StubInputStream(InputStream in, PersistentStringEnumerator nameStorage) {
    super(in);
    myNameStorage = nameStorage;
  }

  public String readUTFFast() throws IOException {
    return IOUtil.readUTFFast(myStringIOBuffer, this);
  }

  public StringRef readName() throws IOException {
    return DataInputOutputUtil.readNAME(this, myNameStorage);
  }

  public int readVarInt() throws IOException {
    return DataInputOutputUtil.readINT(this);
  }

  public String stringFromId(int id) throws IOException {
    return myNameStorage.valueOf(id);
  }
}
