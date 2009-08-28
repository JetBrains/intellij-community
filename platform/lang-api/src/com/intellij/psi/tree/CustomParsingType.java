/*
 * @author max
 */
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CustomParsingType extends IElementType {
  public CustomParsingType(@NotNull @NonNls String debugName, @Nullable Language language) {
    super(debugName, language);
  }

  public abstract ASTNode parse(CharSequence text, CharTable table);
}
