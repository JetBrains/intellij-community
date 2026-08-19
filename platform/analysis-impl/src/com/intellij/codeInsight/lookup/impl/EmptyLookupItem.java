// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A placeholder item shown in the lookup list when there is nothing (yet) to suggest.
 * <p>
 * It is used in two situations:
 * <ul>
 *   <li>as a "no suggestions"/blank row when completion produced no matching elements
 *       (see {@link LookupImpl#addEmptyItem}), and</li>
 *   <li>as a {@linkplain #isLoading() loading} row rendered while suggestions are still being computed
 *       (see {@link LookupImpl#addDummyItems}).</li>
 * </ul>
 * Such an item is not a real completion suggestion: it must never be inserted into the document and is excluded
 * from anything that treats list entries as suggestions. In particular, it is filtered out of the visible item list,
 * ignored when computing the lookup prefix and its matcher, and never selected as the item to insert.
 * Code that inspects the currently selected element should check for this type before using it.
 */
@ApiStatus.Internal
public final class EmptyLookupItem extends LookupElement {
  private final String myText;
  private final boolean myLoading;

  public EmptyLookupItem(@NotNull String s, boolean loading) {
    myText = s;
    myLoading = loading;
  }

  @Override
  public @NotNull String getLookupString() {
    return "             ";
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    presentation.setItemText(myText);
  }

  public boolean isLoading() {
    return myLoading;
  }
}
