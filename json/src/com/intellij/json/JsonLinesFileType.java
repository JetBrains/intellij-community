package com.intellij.json;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.openapi.util.NlsContexts.Label;

public class JsonLinesFileType extends LanguageFileType {
  public static final JsonLinesFileType INSTANCE = new JsonLinesFileType();

  private JsonLinesFileType() {
    super(JsonLanguage.INSTANCE, true);
  }

  @Override
  public @NonNls @NotNull String getName() {
    return "JSON-lines";
  }

  @Override
  public @Label @NotNull String getDescription() {
    return JsonBundle.message("filetype.json_lines.description");
  }

  @Override
  public @NlsSafe @NotNull String getDefaultExtension() {
    return "jsonl";
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.FileTypes.Json;
  }
}
