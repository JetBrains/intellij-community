// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface JsonProperty extends PsiElement {

  @NotNull
  JsonPropertyName getPropertyName();

  @Nullable
  JsonPropertyValue getPropertyValue();

  @NotNull
  String getName();

}
