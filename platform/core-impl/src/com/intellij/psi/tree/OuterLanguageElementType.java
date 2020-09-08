// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.OuterLanguageElementImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Element type that may be used for representing outer fragments in templating language.
 *
 * @see com.intellij.psi.templateLanguages.ITemplateDataElementType
 * @see OuterLanguageElementImpl
 * @see IReparseableLeafElementType
 */
public class OuterLanguageElementType extends IElementType implements ILeafElementType {
  public OuterLanguageElementType(@NonNls @NotNull String debugName,
                                  @Nullable Language language) {
    super(debugName, language);
  }

  protected OuterLanguageElementType(@NonNls @NotNull String debugName,
                                     @Nullable Language language, boolean register) {
    super(debugName, language, register);
  }

  @Override
  public @NotNull ASTNode createLeafNode(@NotNull CharSequence leafText) {
    return new OuterLanguageElementImpl(this, leafText);
  }
}
