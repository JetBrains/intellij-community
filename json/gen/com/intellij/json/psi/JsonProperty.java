// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface JsonProperty extends JsonElement {

  @Nullable
  JsonValue getValue();

  @NotNull
  String getName();

  @NotNull
  JsonPropertyName getNameElement();

}
