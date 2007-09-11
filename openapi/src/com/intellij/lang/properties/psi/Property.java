/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 09.04.2005
 * Time: 22:37:50
 */
package com.intellij.lang.properties.psi;

import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.lang.properties.psi.PropertiesElementFactory
 */
public interface Property extends PsiNamedElement {
  @Nullable String getKey();
  @Nullable String getValue();

  /**
   * Returns the value with \n, \r, \t, \f and Unicode escape characters converted to their
   * character equivalents.
   *
   * @return unescaped value, or null if no value is specified for this property.
   */
  @Nullable String getUnescapedValue();

  /**
   * Returns the key with \n, \r, \t, \f and Unicode escape characters converted to their
   * character equivalents.
   *
   * @return unescaped key, or null if no key is specified for this property.
   */
  @Nullable String getUnescapedKey();

  @Nullable String getKeyValueSeparator();

  void setValue(@NonNls @NotNull String value) throws IncorrectOperationException;

  PropertiesFile getContainingFile() throws PsiInvalidElementAccessException;

  /**
   * @return text of comment preceding this property. Comment-start characters ('#' and '!') are stripped from the text.
   */
  @Nullable String getDocCommentText();
}
