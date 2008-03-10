/*
 * @author max
 */
package com.intellij.psi.stubs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface StubSerializer<T extends StubElement> {
  void serialize(T stub, DataOutputStream dataStream) throws IOException;
  T deserialize(DataInputStream dataStream, final StubElement parentStub) throws IOException;
  void indexStub(T stub, IndexSink sink);
}