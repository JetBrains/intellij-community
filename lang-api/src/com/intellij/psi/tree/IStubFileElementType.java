/*
 * @author max
 */
package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NonNls;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class IStubFileElementType<T extends PsiFileStub> extends IFileElementType implements StubSerializer<T>{
  public IStubFileElementType(final Language language) {
    super(language);
  }

  public IStubFileElementType(@NonNls final String debugName, final Language language) {
    super(debugName, language);
  }

  public StubBuilder getBuilder() {
    return new DefaultStubBuilder();
  }

  public String getExternalId() {
    return "psi.file";
  }

  public void serialize(final T stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
  }

  public T deserialize(final DataInputStream dataStream,
                       final StubElement parentStub, final PersistentStringEnumerator nameStorage) throws IOException {
    return (T)new PsiFileStubImpl(null);
  }

  public void indexStub(final PsiFileStub stub, final IndexSink sink) {
  }
}