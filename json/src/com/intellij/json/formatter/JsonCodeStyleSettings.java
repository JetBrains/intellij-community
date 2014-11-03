package com.intellij.json.formatter;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonCodeStyleSettings extends CustomCodeStyleSettings {

  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_COLON = false;

  public PropertyAlignment PROPERTY_ALIGNMENT = PropertyAlignment.DO_NOT_ALIGN;

  public int OBJECT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
  // This was default policy for array elements wrapping in JavaScript's JSON.
  // CHOP_DOWN_IF_LONG seems more appropriate however for short arrays.
  public int ARRAY_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;

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
