// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

public interface JsonProperty extends PsiNamedElement {

  @NotNull
  List<JsonValue> getValueList();

  @NotNull
  String getName();

  @NotNull
  JsonStringLiteral getNameElement();

  @Nullable
  JsonValue getValue();

}
