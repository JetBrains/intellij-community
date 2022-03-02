package com.intellij.json;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class JsonFileType extends LanguageFileType{
  public static final JsonFileType INSTANCE = new JsonFileType();
  public static final String DEFAULT_EXTENSION = "json";

  protected JsonFileType(Language language) {
    super(language);
  }

  protected JsonFileType(Language language, boolean secondary) {
    super(language, secondary);
  }

  protected JsonFileType() {
    super(JsonLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "JSON";
  }

  @NotNull
  @Override
  public String getDescription() {
    return JsonBundle.message("filetype.json.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    // TODO: add JSON icon instead
    return AllIcons.FileTypes.Json;
  }
}
