// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Consider using {@link com.intellij.codeInsight.completion.InsertHandler} instead
 */
public abstract class TailTypeDecorator<T extends LookupElement> extends LookupElementDecorator<T> {
  public TailTypeDecorator(T delegate) {
    super(delegate);
  }

  public static <T extends LookupElement> TailTypeDecorator<T> withTail(T element, final TailType type) {
    return new TailTypeDecorator<>(element) {
      @Override
      protected TailType computeTailType(InsertionContext context) {
        return type;
      }
    };
  }

  @Nullable
  protected abstract TailType computeTailType(InsertionContext context);

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    TailType tailType = computeTailType(context);

    getDelegate().handleInsert(context);
    if (tailType != null && tailType.isApplicable(context)) {
      PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());
      int tailOffset = context.getTailOffset();
      if (tailOffset < 0) {
        throw new AssertionError("tailOffset < 0: delegate=" + getDelegate() + "; this=" + this + "; tail=" + tailType);
      }
      tailType.processTail(context.getEditor(), tailOffset);
    }
  }

}
