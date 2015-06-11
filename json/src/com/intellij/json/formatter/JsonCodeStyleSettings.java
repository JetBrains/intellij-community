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

  public static int DO_NOT_ALIGN_PROPERTY = PropertyAlignment.DO_NOT_ALIGN.getId();
  public static int ALIGN_PROPERTY_ON_VALUE = PropertyAlignment.ALIGN_ON_VALUE.getId();
  public static int ALIGN_PROPERTY_ON_COLON = PropertyAlignment.ALIGN_ON_COLON.getId();

  /**
   * Keep brackets on their own lines if containing object spans more than one line.
   * Internally {@link com.intellij.formatting.DependentSpacingRule} is used to achieve that.
   * <h2>Example</h2>
   * <pre>
   *   ["foo",
   *    "bar",
   *    ["baz"]]
   * </pre>
   * will be transformed to
   * <pre>
   *   [
   *     "foo",
   *     "bar",
   *     ["baz"]
   *   ]
   * </pre>
   */
  public static int BRACES_STYLE_ON_SEPARATE_LINES = 0;
  /**
   * Lisp-style braces placement where braces "stick" to preceding/following elements.
   * <h2>Example</h2>
   * <pre>
   *   [
   *    "foo",
   *    "bar",
   *    [
   *      "baz"]]
   * </pre>
   * will be transformed to
   * <pre>
   *   ["foo",
   *    "bar",
   *    ["baz"]]
   * </pre>
   */
  public static int BRACES_STYLE_ON_SAME_LINE = 1;

  public static int[] BRACE_STYLE_VALUES = {
    BRACES_STYLE_ON_SEPARATE_LINES,
    BRACES_STYLE_ON_SAME_LINE
  };

  public static String[] BRACE_STYLE_NAMES = {
    "On separate lines",
    "On the same lines"
  };

  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_COLON = false;

  // TODO: check whether it's possible to migrate CustomCodeStyleSettings to newer com.intellij.util.xmlb.XmlSerializer
  /**
   * Contains value of {@link JsonCodeStyleSettings.PropertyAlignment#getId()}
   *
   * @see #DO_NOT_ALIGN_PROPERTY
   * @see #ALIGN_PROPERTY_ON_VALUE
   * @see #ALIGN_PROPERTY_ON_COLON
   */
  public int PROPERTY_ALIGNMENT = PropertyAlignment.DO_NOT_ALIGN.getId();

  public int OBJECT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
  // This was default policy for array elements wrapping in JavaScript's JSON.
  // CHOP_DOWN_IF_LONG seems more appropriate however for short arrays.
  public int ARRAY_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;

  public int OBJECT_BRACES_STYLE = BRACES_STYLE_ON_SEPARATE_LINES;
  public int ARRAY_BRACKETS_STYLE = BRACES_STYLE_ON_SEPARATE_LINES;

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
