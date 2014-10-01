package com.intellij.json.formatter;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonCodeStyleSettings extends CustomCodeStyleSettings {

  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_COLON = false;

  public PropertyAlignment PROPERTY_ALIGNMENT = PropertyAlignment.DO_NOT_ALIGN;

  public JsonCodeStyleSettings(CodeStyleSettings container) {
    super(JsonLanguage.INSTANCE.getID(), container);
  }

  public enum PropertyAlignment {
    DO_NOT_ALIGN(JsonBundle.message("msg.align.properties.none")),
    ALIGN_ON_VALUE(JsonBundle.message("msg.align.properties.on.value")),
    ALIGN_ON_COLON(JsonBundle.message("msg.align.properties.on.colon"));

    private final String myDescription;

    PropertyAlignment(@NotNull String description) {
      myDescription = description;
    }

    @NotNull
    public String getDescription() {
      return myDescription;
    }
  }
}
