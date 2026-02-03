// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.formatter;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author Mikhail Golubev
 */
public final class JsonCodeStyleSettings extends CustomCodeStyleSettings {

  public static final int DO_NOT_ALIGN_PROPERTY = PropertyAlignment.DO_NOT_ALIGN.getId();
  public static final int ALIGN_PROPERTY_ON_VALUE = PropertyAlignment.ALIGN_ON_VALUE.getId();
  public static final int ALIGN_PROPERTY_ON_COLON = PropertyAlignment.ALIGN_ON_COLON.getId();

  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_COLON = false;
  public boolean KEEP_TRAILING_COMMA = false;

  // TODO: check whether it's possible to migrate CustomCodeStyleSettings to newer com.intellij.util.xmlb.XmlSerializer
  /**
   * Contains value of {@link JsonCodeStyleSettings.PropertyAlignment#getId()}
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
  @CommonCodeStyleSettings.WrapConstant
  public int OBJECT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
  
  // This was default policy for array elements wrapping in JavaScript's JSON.
  // CHOP_DOWN_IF_LONG seems more appropriate however for short arrays.
  @MagicConstant(flags = {
    CommonCodeStyleSettings.DO_NOT_WRAP,
    CommonCodeStyleSettings.WRAP_ALWAYS,
    CommonCodeStyleSettings.WRAP_AS_NEEDED, 
    CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
  })
  @CommonCodeStyleSettings.WrapConstant
  public int ARRAY_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;

  public JsonCodeStyleSettings(CodeStyleSettings container) {
    super(JsonLanguage.INSTANCE.getID(), container);
  }

  public enum PropertyAlignment {
    DO_NOT_ALIGN(0, "formatter.align.properties.none"),
    ALIGN_ON_VALUE(1, "formatter.align.properties.on.value"),
    ALIGN_ON_COLON(2, "formatter.align.properties.on.colon");
    private final @PropertyKey(resourceBundle = JsonBundle.BUNDLE) String myKey;
    private final int myId;

    PropertyAlignment(int id, @NotNull @PropertyKey(resourceBundle = JsonBundle.BUNDLE) String key) {
      myKey = key;
      myId = id;
    }

    public @NotNull String getDescription() {
      return JsonBundle.message(myKey);
    }

    public int getId() {
      return myId;
    }
  }
}
