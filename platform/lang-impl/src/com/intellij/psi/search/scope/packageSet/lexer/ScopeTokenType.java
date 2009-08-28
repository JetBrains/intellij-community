/*
 * @author max
 */
package com.intellij.psi.search.scope.packageSet.lexer;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ScopeTokenType extends IElementType {
  public ScopeTokenType(@NotNull @NonNls final String debugName) {
    super(debugName, Language.ANY);
  }
}