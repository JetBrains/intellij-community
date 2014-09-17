// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

public interface JsonProperty extends JsonElement, PsiNamedElement {

  @NotNull
  String getName();

  @NotNull
  JsonValue getNameElement();

  @Nullable
  JsonValue getValue();

}
