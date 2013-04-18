package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * Holds keys to the colors used at external system-specific processing.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/18/12 4:19 PM
 */
public class ExternalSystemTextAttributes {

  /**
   * References color to use for indication of particular change that exists only at the external system side.
   * <p/>
   * Example: particular dependency is added at the external system side but not at the ide.
   */
  public static final TextAttributesKey EXTERNAL_SYSTEM_LOCAL_CHANGE = TextAttributesKey.createTextAttributesKey(
    "EXTERNAL_SYSTEM_LOCAL_CHANGE",
    DefaultLanguageHighlighterColors.STRING
  );

  /**
   * References color to use for indication of particular change that exists only at the ide side.
   * <p/>
   * Example: particular dependency is added at the ide side but not at the external system.
   */
  public static final TextAttributesKey IDE_LOCAL_CHANGE = TextAttributesKey.createTextAttributesKey(
    "IDE_LOCAL_CHANGE",
    DefaultLanguageHighlighterColors.KEYWORD
  );

  /**
   * References color to use for indication that particular setting has different values at the gradle and intellij.
   * <p/>
   * Example: particular module is renamed at the intellij, i.e. <code>'module.name'</code> property has different (conflicting)
   * values at the gradle and the intellij.
   */
  public static final TextAttributesKey CHANGE_CONFLICT = TextAttributesKey.createTextAttributesKey(
    "EXTERNAL_SYSTEM_CHANGE_CONFLICT",
    CodeInsightColors.ERRORS_ATTRIBUTES
  );

  public static final TextAttributesKey OUTDATED_ENTITY = TextAttributesKey.createTextAttributesKey(
    "GRADLE_OUTDATED_ENTITY",
    DefaultLanguageHighlighterColors.STATIC_FIELD
  );

  /**
   * References color to use for indication that particular setting has the same values at the gradle and intellij.
   */
  public static final TextAttributesKey NO_CHANGE = TextAttributesKey.createTextAttributesKey(
    "EXTERNAL_SYSTEM_NO_CHANGE",
    HighlighterColors.TEXT
  );

  private ExternalSystemTextAttributes() {
  }
}
