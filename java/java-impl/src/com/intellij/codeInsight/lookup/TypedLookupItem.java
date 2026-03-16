// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * A lookup element representing a type. Must extend {@link LookupElement}
 */
public interface TypedLookupItem {
  ClassConditionKey<TypedLookupItem> CLASS_CONDITION_KEY = ClassConditionKey.create(TypedLookupItem.class);

  /**
   * @return the type represented by this item
   */
  @Nullable
  PsiType getType();

  /**
   * @return icon associated with item type
   */
  default @Nullable Icon getIcon() {
    return DefaultLookupItemRenderer.getRawIcon((LookupElement)this);
  }

  /**
   * @return whether the item should be rendered as stroke out
   */
  default boolean isToStrikeout() {
    return JavaElementLookupRenderer.isToStrikeout((LookupElement)this);
  }
}
