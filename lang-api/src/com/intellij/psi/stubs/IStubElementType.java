/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class IStubElementType<StubT extends StubElement, PsiT extends PsiElement> extends IElementType implements StubSerializer<StubT> {
  public IStubElementType(@NotNull @NonNls final String debugName, @Nullable final Language language) {
    super(debugName, language);
  }

  public abstract PsiT createPsi(StubT stub);

  public abstract StubT createStub(PsiT psi, final StubElement parentStub);

  public boolean shouldCreateStub(ASTNode node) {
    return true;
  }
}