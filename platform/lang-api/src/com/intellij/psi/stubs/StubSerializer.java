/*
 * @author max
 */
package com.intellij.psi.stubs;

import org.jetbrains.annotations.NonNls;

import java.io.IOException;

public interface StubSerializer<T extends StubElement> {
  @NonNls
  String getExternalId();

  void serialize(T stub, StubOutputStream dataStream) throws IOException;
  T deserialize(StubInputStream dataStream, final StubElement parentStub) throws IOException;

  void indexStub(T stub, IndexSink sink);
}