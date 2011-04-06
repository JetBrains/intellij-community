/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.annotations.Nullable;

/**
 * Consider using {@link InsertHandler} instead
 * @author peter
 */
public abstract class TailTypeDecorator<T extends LookupElement> extends LookupElementDecorator<T> {
  public TailTypeDecorator(T delegate) {
    super(delegate);
  }

  public static <T extends LookupElement> TailTypeDecorator<T> withTail(T element, final TailType type) {
    return new TailTypeDecorator<T>(element) {
      @Override
      protected TailType computeTailType(InsertionContext context) {
        return type;
      }
    };
  }

  @Nullable
  protected abstract TailType computeTailType(InsertionContext context);

  @Override
  public void handleInsert(InsertionContext context) {
    final LookupElement delegate = getDelegate();
    final TailType tailType = computeTailType(context);

    final LookupItem lookupItem = delegate.as(LookupItem.CLASS_CONDITION_KEY);
    if (lookupItem != null && tailType != null) {
      lookupItem.setTailType(TailType.UNKNOWN);
    }
    delegate.handleInsert(context);
    if (tailType != null && tailType.isApplicable(context)) {
      PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();
      int tailOffset = context.getTailOffset();
      if (tailOffset < 0) {
        throw new AssertionError("tailOffset < 0: delegate=" + getDelegate() + "; this=" + this + "; tail=" + tailType);
      }
      tailType.processTail(context.getEditor(), tailOffset);
    }
  }

}
