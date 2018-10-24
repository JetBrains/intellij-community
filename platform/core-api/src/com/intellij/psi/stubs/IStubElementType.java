// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public abstract class IStubElementType<StubT extends StubElement, PsiT extends PsiElement> extends IElementType implements StubSerializer<StubT> {
  private static volatile boolean ourInitializedStubs;
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.IStubElementType");

  public IStubElementType(@NotNull @NonNls final String debugName, @Nullable final Language language) {
    super(debugName, language);
    if (ourInitializedStubs) {
      LOG.error("All stub element types should be created before index initialization is complete.\n" +
                "Please add the class containing stub element type constants to \"stubElementTypeHolder\" extension.\n" +
                "Registered extensions: " + Arrays.toString(StubElementTypeHolderEP.EP_NAME.getExtensions()));
    }
  }

  static void loadRegisteredStubElementTypes() {
    for (StubElementTypeHolderEP holderEP : StubElementTypeHolderEP.EP_NAME.getExtensionList()) {
      holderEP.initialize();
    }
    ourInitializedStubs = true;
  }

  public abstract PsiT createPsi(@NotNull StubT stub);

  @NotNull
  public abstract StubT createStub(@NotNull PsiT psi, final StubElement parentStub);

  public boolean shouldCreateStub(ASTNode node) {
    return true;
  }

}