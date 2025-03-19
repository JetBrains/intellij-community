// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.navigation.ItemPresentation;

public interface JsonObject extends JsonContainer {

  @NotNull
  List<JsonProperty> getPropertyList();

  @Nullable JsonProperty findProperty(@NotNull String name);

  @Nullable ItemPresentation getPresentation();

}
