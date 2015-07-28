package com.intellij.json.formatter;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonCodeStyleSettings extends CustomCodeStyleSettings {

  public static int DO_NOT_ALIGN_PROPERTY = PropertyAlignment.DO_NOT_ALIGN.getId();
  public static int ALIGN_PROPERTY_ON_VALUE = PropertyAlignment.ALIGN_ON_VALUE.getId();
  public static int ALIGN_PROPERTY_ON_COLON = PropertyAlignment.ALIGN_ON_COLON.getId();

  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_COLON = false;

  // TODO: check whether it's possible to migrate CustomCodeStyleSettings to newer com.intellij.util.xmlb.XmlSerializer
  /**
   * Contains value of {@link com.intellij.json.formatter.JsonCodeStyleSettings.PropertyAlignment#getId()}
   *
   * @see #DO_NOT_ALIGN_PROPERTY
   * @see #ALIGN_PROPERTY_ON_VALUE
   * @see #ALIGN_PROPERTY_ON_COLON
   */
  public int PROPERTY_ALIGNMENT = PropertyAlignment.DO_NOT_ALIGN.getId();

  @MagicConstant(flags = {
    CommonCodeStyleSettings.DO_NOT_WRAP,
    CommonCodeStyleSettings.WRAP_ALWAYS,
    CommonCodeStyleSettings.WRAP_AS_NEEDED, 
    CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
  })
  public int OBJECT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
  
  // This was default policy for array elements wrapping in JavaScript's JSON.
  // CHOP_DOWN_IF_LONG seems more appropriate however for short arrays.
  @MagicConstant(flags = {
    CommonCodeStyleSettings.DO_NOT_WRAP,
    CommonCodeStyleSettings.WRAP_ALWAYS,
    CommonCodeStyleSettings.WRAP_AS_NEEDED, 
    CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
  })
  public int ARRAY_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;

  public JsonCodeStyleSettings(CodeStyleSettings container) {
    super(JsonLanguage.INSTANCE.getID(), container);
  }

  public enum PropertyAlignment {
    DO_NOT_ALIGN(JsonBundle.message("formatter.align.properties.none"), 0),
    ALIGN_ON_VALUE(JsonBundle.message("formatter.align.properties.on.value"), 1),
    ALIGN_ON_COLON(JsonBundle.message("formatter.align.properties.on.colon"), 2);

    private final String myDescription;
    private final int myId;

    PropertyAlignment(@NotNull String description, int id) {
      myDescription = description;
      myId = id;
    }

    @NotNull
    public String getDescription() {
      return myDescription;
    }

    public int getId() {
      return myId;
    }
  }
}
