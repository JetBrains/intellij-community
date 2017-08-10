/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.JavaIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author rvishnyakov
 */
public class JavaLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) return SPACING_SAMPLE;
    if (settingsType == SettingsType.BLANK_LINES_SETTINGS) return BLANK_LINE_SAMPLE;
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) return WRAPPING_CODE_SAMPLE;

    return GENERAL_CODE_SAMPLE;
  }

  @Override
  public int getRightMargin(@NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) return 37;
    return super.getRightMargin(settingsType);
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.showAllStandardOptions();
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACES_WITHIN_ANGLE_BRACKETS", "Angle brackets",CodeStyleSettingsCustomizable.SPACES_WITHIN);

      String groupName = CodeStyleSettingsCustomizable.SPACES_IN_TYPE_ARGUMENTS;
      consumer.moveStandardOption("SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS", groupName);
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT", "After closing angle bracket", groupName);

      groupName = CodeStyleSettingsCustomizable.SPACES_IN_TYPE_PARAMETERS;
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER", ApplicationBundle.message("checkbox.spaces.before.opening.angle.bracket"), groupName);
      consumer.showCustomOption(JavaCodeStyleSettings.class, "SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS", "Around type bounds", groupName);
    }
    else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions("RIGHT_MARGIN",
                                   "WRAP_ON_TYPING",
                                   "KEEP_CONTROL_STATEMENT_IN_ONE_LINE",
                                   "LINE_COMMENT_AT_FIRST_COLUMN",
                                   "BLOCK_COMMENT_AT_FIRST_COLUMN",
                                   "KEEP_LINE_BREAKS",
                                   "KEEP_FIRST_COLUMN_COMMENT",
                                   "CALL_PARAMETERS_WRAP",
                                   "PREFER_PARAMETERS_WRAP",
                                   "CALL_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                   "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                   "METHOD_PARAMETERS_WRAP",
                                   "METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                   "METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                   "RESOURCE_LIST_WRAP",
                                   "RESOURCE_LIST_LPAREN_ON_NEXT_LINE",
                                   "RESOURCE_LIST_RPAREN_ON_NEXT_LINE",
                                   "EXTENDS_LIST_WRAP",
                                   "THROWS_LIST_WRAP",
                                   "EXTENDS_KEYWORD_WRAP",
                                   "THROWS_KEYWORD_WRAP",
                                   "METHOD_CALL_CHAIN_WRAP",
                                   "PARENTHESES_EXPRESSION_LPAREN_WRAP",
                                   "PARENTHESES_EXPRESSION_RPAREN_WRAP",
                                   "BINARY_OPERATION_WRAP",
                                   "BINARY_OPERATION_SIGN_ON_NEXT_LINE",
                                   "TERNARY_OPERATION_WRAP",
                                   "TERNARY_OPERATION_SIGNS_ON_NEXT_LINE",
                                   "MODIFIER_LIST_WRAP",
                                   "KEEP_SIMPLE_BLOCKS_IN_ONE_LINE",
                                   "KEEP_SIMPLE_METHODS_IN_ONE_LINE",
                                   "KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE",
                                   "KEEP_SIMPLE_CLASSES_IN_ONE_LINE",
                                   "KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE",
                                   "FOR_STATEMENT_WRAP",
                                   "FOR_STATEMENT_LPAREN_ON_NEXT_LINE",
                                   "FOR_STATEMENT_RPAREN_ON_NEXT_LINE",
                                   "ARRAY_INITIALIZER_WRAP",
                                   "ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE",
                                   "ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE",
                                   "ASSIGNMENT_WRAP",
                                   "PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE",
                                   "LABELED_STATEMENT_WRAP",
                                   "ASSERT_STATEMENT_WRAP",
                                   "ASSERT_STATEMENT_COLON_ON_NEXT_LINE",
                                   "IF_BRACE_FORCE",
                                   "DOWHILE_BRACE_FORCE",
                                   "WHILE_BRACE_FORCE",
                                   "FOR_BRACE_FORCE",
                                   "WRAP_LONG_LINES",
                                   "METHOD_ANNOTATION_WRAP",
                                   "CLASS_ANNOTATION_WRAP",
                                   "FIELD_ANNOTATION_WRAP",
                                   "PARAMETER_ANNOTATION_WRAP",
                                   "VARIABLE_ANNOTATION_WRAP",
                                   "ALIGN_MULTILINE_CHAINED_METHODS",
                                   "ALIGN_MULTILINE_PARAMETERS",
                                   "ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
                                   "ALIGN_MULTILINE_RESOURCES",
                                   "ALIGN_MULTILINE_FOR",
                                   "INDENT_WHEN_CASES",
                                   "ALIGN_MULTILINE_BINARY_OPERATION",
                                   "ALIGN_MULTILINE_ASSIGNMENT",
                                   "ALIGN_MULTILINE_TERNARY_OPERATION",
                                   "ALIGN_MULTILINE_THROWS_LIST",
                                   "ALIGN_THROWS_KEYWORD",
                                   "ALIGN_MULTILINE_EXTENDS_LIST",
                                   "ALIGN_MULTILINE_METHOD_BRACKETS",
                                   "ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION",
                                   "ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION",
                                   "ALIGN_GROUP_FIELD_DECLARATIONS",
                                   "BRACE_STYLE",
                                   "CLASS_BRACE_STYLE",
                                   "METHOD_BRACE_STYLE",
                                   "LAMBDA_BRACE_STYLE",
                                   "USE_FLYING_GEESE_BRACES",
                                   "FLYING_GEESE_BRACES_GAP",
                                   "DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS",
                                   "ELSE_ON_NEW_LINE",
                                   "WHILE_ON_NEW_LINE",
                                   "CATCH_ON_NEW_LINE",
                                   "FINALLY_ON_NEW_LINE",
                                   "INDENT_CASE_FROM_SWITCH",
                                   "CASE_STATEMENT_ON_NEW_LINE",
                                   "SPECIAL_ELSE_IF_TREATMENT",
                                   "ENUM_CONSTANTS_WRAP",
                                   "ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS",
                                   "ALIGN_SUBSEQUENT_SIMPLE_METHODS",
                                   "WRAP_FIRST_METHOD_IN_CALL_CHAIN");

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "ANNOTATION_PARAMETER_WRAP",
                                ApplicationBundle.message("wrapping.annotation.parameters"),
                                null,
                                CodeStyleSettingsCustomizable.WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);

      consumer.showCustomOption(JavaCodeStyleSettings.class,
                                "ALIGN_MULTILINE_ANNOTATION_PARAMETERS",
                                ApplicationBundle.message("wrapping.align.when.multiline"),
                                ApplicationBundle.message("wrapping.annotation.parameters"));

      String groupName = ApplicationBundle.message("wrapping.fields.annotation");
      consumer.showCustomOption(JavaCodeStyleSettings.class, "DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION", "Do not wrap after single annotation", groupName);
    }
    else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      consumer.showAllStandardOptions();
      consumer.showCustomOption(JavaCodeStyleSettings.class, "BLANK_LINES_AROUND_INITIALIZER", ApplicationBundle.message("editbox.blanklines.around.initializer"), CodeStyleSettingsCustomizable.BLANK_LINES);
    }
    else if (settingsType == SettingsType.COMMENTER_SETTINGS) {
      consumer.showStandardOptions("LINE_COMMENT_ADD_SPACE");
    }
    else {
      consumer.showAllStandardOptions();
    }
  }

  @Override
  public PsiFile createFileFromText(final Project project, final String text) {
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(
      "sample.java", StdFileTypes.JAVA, text, LocalTimeCounter.currentTime(), false, false
    );
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
    return file;
  }

  @Override
  public DisplayPriority getDisplayPriority() {
    if (PlatformUtils.isIntelliJ()) return DisplayPriority.KEY_LANGUAGE_SETTINGS;
    return DisplayPriority.LANGUAGE_SETTINGS;
  }

  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings settings = new CommonCodeStyleSettings(JavaLanguage.INSTANCE);
    settings.initIndentOptions();
    return settings;
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new JavaIndentOptionsEditor();
  }


  @Override
  @NotNull
  public DocCommentSettings getDocCommentSettings(@NotNull PsiFile file) {
    if (file.isValid()) {
      return new DocCommentSettings() {
        private final JavaCodeStyleSettings mySettings =
          CodeStyleSettingsManager.getSettings(file.getProject()).getCustomSettings(JavaCodeStyleSettings.class);

        @Override
        public boolean isDocFormattingEnabled() {
          return mySettings.ENABLE_JAVADOC_FORMATTING;
        }

        @Override
        public void setDocFormattingEnabled(boolean formattingEnabled) {
          mySettings.ENABLE_JAVADOC_FORMATTING = formattingEnabled;
        }

        @Override
        public boolean isLeadingAsteriskEnabled() {
          return mySettings.JD_LEADING_ASTERISKS_ARE_ENABLED;
        }
      };
    }
    return super.getDocCommentSettings(file);
  }

  private static final String GENERAL_CODE_SAMPLE =
    "public class Foo {\n" +
    "  public int[] X = new int[]{1, 3, 5, 7, 9, 11};\n" +
    "\n" +
    "  public void foo(boolean a, int x, int y, int z) {\n" +
    "    label1:\n" +
    "    do {\n" +
    "      try {\n" +
    "        if (x > 0) {\n" +
    "          int someVariable = a ? x : y;\n" +
    "          int anotherVariable = a ? x : y;\n" +
    "        }\n" +
    "        else if (x < 0) {\n" +
    "          int someVariable = (y + z);\n" +
    "          someVariable = x = x + y;\n" +
    "        }\n" +
    "        else {\n" +
    "          label2:\n" +
    "          for (int i = 0; i < 5; i++) doSomething(i);\n" +
    "        }\n" +
    "        switch (a) {\n" +
    "          case 0:\n" +
    "            doCase0();\n" +
    "            break;\n" +
    "          default:\n" +
    "            doDefault();\n" +
    "        }\n" +
    "      }\n" +
    "      catch (Exception e) {\n" +
    "        processException(e.getMessage(), x + y, z, a);\n" +
    "      }\n" +
    "      finally {\n" +
    "        processFinally();\n" +
    "      }\n" +
    "    }\n" +
    "    while (true);\n" +
    "\n" +
    "    if (2 < 3) return;\n" +
    "    if (3 < 4) return;\n" +
    "    do {\n" +
    "      x++;\n" +
    "    }\n" +
    "    while (x < 10000);\n" +
    "    while (x < 50000) x++;\n" +
    "    for (int i = 0; i < 5; i++) System.out.println(i);\n" +
    "  }\n" +
    "\n" +
    "  private class InnerClass implements I1, I2 {\n" +
    "    public void bar() throws E1, E2 {\n" +
    "    }\n" +
    "  }\n" +
    "}";

  private static final String BLANK_LINE_SAMPLE =
    "/*\n" +
    " * This is a sample file.\n" +
    " */\n" +
    "package com.intellij.samples;\n" +
    "\n" +
    "import com.intellij.idea.Main;\n" +
    "\n" +
    "import javax.swing.*;\n" +
    "import java.util.Vector;\n" +
    "\n" +
    "public class Foo {\n" +
    "  private int field1;\n" +
    "  private int field2;\n" +
    "\n" +
    "  {\n" +
    "      field1 = 2;\n" +
    "  }\n" +
    "\n" +
    "  public void foo1() {\n" +
    "      new Runnable() {\n" +
    "          public void run() {\n" +
    "          }\n" +
    "      };\n" +
    "  }\n" +
    "\n" +
    "  public class InnerClass {\n" +
    "  }\n" +
    "}\n" +
    "class AnotherClass {\n" +
    "}\n" +
    "interface TestInterface {\n" +
    "    int MAX = 10;\n" +
    "    int MIN = 1;\n" +
    "    void method1();\n" +
    "    void method2();\n" +
    "}";

  private static final String SPACING_SAMPLE =
    "@Annotation(param1 = \"value1\", param2 = \"value2\")\n" +
    "@SuppressWarnings({\"ALL\"})\n" +
    "public class Foo<T extends Bar & Abba, U> {\n" +
    "  int[] X = new int[]{1, 3, 5, 6, 7, 87, 1213, 2};\n" +
    "  int[] empty = new int[]{};" +
    "\n" +
    "  public void foo(int x, int y) {\n" +
    "    Runnable r = () -> {};\n" +
    "    Runnable r1 = this :: bar;\n" +
    "    for (int i = 0; i < x; i++) {\n" +
    "      y += (y ^ 0x123) << 2;\n" +
    "    }\n" +
    "    do {\n" +
    "      try(MyResource r1 = getResource(); MyResource r2 = null) {\n" +
    "        if (0 < x && x < 10) {\n" +
    "          while (x != y) {\n" +
    "            x = f(x * 3 + 5);\n" +
    "          }\n" +
    "        }\n" +
    "        else {\n" +
    "          synchronized (this) {\n" +
    "            switch (e.getCode()) {\n" +
    "              //...\n" +
    "            }\n" +
    "          }\n" +
    "        }\n" +
    "      }\n" +
    "      catch (MyException e) {\n" +
    "      }\n" +
    "      finally {\n" +
    "        int[] arr = (int[])g(y);\n" +
    "        x = y >= 0 ? arr[y] : -1;\n" +
    "        Map<String, String> sMap = new HashMap<String, String>();\n" +
    "        Bar.<String, Integer>mess(null);\n" +
    "      }\n" +
    "    }\n" +
    "    while (true);\n" +
    "  }\n" +
    "  void bar(){{return;}}\n" +
    "}\n" +
    "class Bar {\n" +
    "    static <U, T> U mess(T t) {\n" +
    "        return null;\n" +
    "    }\n" +
    "}\n" +
    "interface Abba {}";

  private static final String WRAPPING_CODE_SAMPLE =
    "/*\n" +
    " * This is a sample file.\n" +
    " */\n" +
    "\n" +
    "public class ThisIsASampleClass extends C1 implements I1, I2, I3, I4, I5 {\n" +
    "  private int f1 = 1;\n" +
    "  private String field2 = \"\";\n" +
    "  public void foo1(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {}\n" +
    "  public static void longerMethod() throws Exception1, Exception2, Exception3 {\n" +
    "// todo something\n" +
    "    int\n" +
    "i = 0;\n" +
    "    int[] a = new int[] {1, 2, 0x0052, 0x0053, 0x0054};\n" +
    "    int[] empty = new int[] {};\n" +
    "    int var1 = 1; int var2 = 2;\n" +
    "    foo1(0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057);\n" +
    "    int x = (3 + 4 + 5 + 6) * (7 + 8 + 9 + 10) * (11 + 12 + 13 + 14 + 0xFFFFFFFF);\n" +
    "    String s1, s2, s3;\n" +
    "    s1 = s2 = s3 = \"012345678901456\";\n" +
    "    assert i + j + k + l + n+ m <= 2 : \"assert description\";" +
    "    int y = 2 > 3 ? 7 + 8 + 9 : 11 + 12 + 13;\n" +
    "    super.getFoo().foo().getBar().bar();\n" +
    "\n" +
    "    label: " +
    "    if (2 < 3) return; else if (2 > 3) return; else return;\n" +
    "    for (int i = 0; i < 0xFFFFFF; i += 2) System.out.println(i);\n" +
    "    while (x < 50000) x++;\n" +
    "    do x++; while (x < 10000);\n" +
    "    switch (a) {\n" +
    "    case 0:\n" +
    "      doCase0();\n" +
    "      break;\n" +
    "    default:\n" +
    "      doDefault();\n" +
    "    }\n" +
    "    try (MyResource r1 = getResource(); MyResource r2 = null) {\n" +
    "      doSomething();\n" +
    "    } catch (Exception e) {\n" +
    "      processException(e);\n" +
    "    } finally {\n" +
    "      processFinally();\n" +
    "    }\n" +
    "    do {\n" +
    "        x--;\n" +
    "    } while (x > 10); \n" +
    "    try (MyResource r1 = getResource();\n" +
    "      MyResource r2 = null) {\n" +
    "      doSomething();\n" +
    "    }\n" +
    "    Runnable r = () -> {};\n" +
    "  }\n" +
    "    public static void test() \n" +
    "        throws Exception { \n" +
    "        foo.foo().bar(\"arg1\", \n" +
    "                      \"arg2\"); \n" +
    "        new Object() {};" +
    "    } \n" +
    "    class TestInnerClass {}\n" +
    "    interface TestInnerInterface {}\n" +
    "}\n" +
    "\n" +
    "enum Breed {\n" +
    "    Dalmatian(), Labrador(), Dachshund()\n" +
    "}\n" +
    "\n" +
    "@Annotation1 @Annotation2 @Annotation3(param1=\"value1\", param2=\"value2\") @Annotation4 class Foo {\n" +
    "    @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") public static void foo(){\n" +
    "    }\n" +
    "    @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") public static int myFoo;\n" +
    "    public void method(@Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") final int param){\n" +
    "        @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") final int localVariable;" +
    "    }\n" +
    "}";
}
