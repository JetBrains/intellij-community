package com.intellij.json.psi;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class JsonPsiImplUtils {
  @NotNull
  public static String getName(@NotNull JsonProperty property) {
    return StringUtil.unquoteString(property.getPropertyName().getText());
  }
}
