/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NonNls;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface StubSerializer<T extends StubElement> {
  @NonNls
  String getExternalId();

  void serialize(T stub, DataOutputStream dataStream, final PersistentStringEnumerator nameStorage) throws IOException;
  T deserialize(DataInputStream dataStream, final StubElement parentStub, final PersistentStringEnumerator nameStorage) throws IOException;

  void indexStub(T stub, IndexSink sink);
}