/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 09.04.2005
 * Time: 22:37:50
 */
package com.intellij.lang.properties.psi;

import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

public interface Property extends PsiNamedElement {
  @Nullable String getKey();
  @Nullable String getValue();
  @Nullable String getKeyValueSeparator();

  void setValue(@NonNls @NotNull String value) throws IncorrectOperationException;
}