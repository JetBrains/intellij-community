/*
 * @author max
 */
package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.DefaultStubBuilder;
import org.jetbrains.annotations.NonNls;

public class IStubFileElementType extends IFileElementType {
  public IStubFileElementType(final Language language) {
    super(language);
  }

  public IStubFileElementType(@NonNls final String debugName, final Language language) {
    super(debugName, language);
  }

  public StubBuilder getBuilder() {
    return new DefaultStubBuilder();
  }
}