package com.intellij.json;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JsonTokenType extends IElementType {
  public JsonTokenType(@NotNull @NonNls String debugName) {
    super(debugName, JsonLanguage.INSTANCE);
  }
}
