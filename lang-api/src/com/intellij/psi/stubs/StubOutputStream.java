package com.intellij.psi.stubs;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author yole
 */
public class StubOutputStream extends DataOutputStream {
  private final PersistentStringEnumerator myNameStorage;
  private final byte[] myStringIOBuffer = IOUtil.allocReadWriteUTFBuffer();

  public StubOutputStream(OutputStream out, PersistentStringEnumerator nameStorage) {
    super(out);
    myNameStorage = nameStorage;
  }

  public void writeUTFFast(final String arg) throws IOException {
    IOUtil.writeUTFFast(myStringIOBuffer, this, arg);
  }

  public void writeName(final String arg) throws IOException {
    DataInputOutputUtil.writeNAME(this, arg, myNameStorage);
  }

  public void writeVarInt(final int value) throws IOException {
    DataInputOutputUtil.writeINT(this, value);
  }

  public int getStringId(final String value) throws IOException {
    return myNameStorage.enumerate(value);
  }
}
