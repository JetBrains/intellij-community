/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsAware;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * Common code style settings can be used by several programming languages. Each language may have its own
 * instance of {@code CommonCodeStyleSettings}.
 *
 * @author Rustam Vishnyakov
 */
public class CommonCodeStyleSettings {
  // Dev. notes:
  // - Do not add language-specific options here, use CustomCodeStyleSettings instead.
  // - A new options should be added to CodeStyleSettingsCustomizable as well.
  // - Covered by CodeStyleConfigurationsTest.

  @NonNls private static final String ARRANGEMENT_ELEMENT_NAME = "arrangement";

  private final Language myLanguage;

  private ArrangementSettings myArrangementSettings;
  private CodeStyleSettings   myRootSettings;
  private IndentOptions       myIndentOptions;
  private final FileType myFileType;
  private boolean             myForceArrangeMenuAvailable;

  protected SoftMargins mySoftMargins = new SoftMargins();

  @NonNls private static final String INDENT_OPTIONS_TAG = "indentOptions";

  public CommonCodeStyleSettings(Language language, FileType fileType) {
    myLanguage = language;
    myFileType = fileType;
  }

  public CommonCodeStyleSettings(Language language) {
    this(language, language == null ? null : language.getAssociatedFileType());
  }

  void setRootSettings(@NotNull CodeStyleSettings rootSettings) {
    myRootSettings = rootSettings;
  }

  public Language getLanguage() {
    return myLanguage;
  }

  @NotNull
  public IndentOptions initIndentOptions() {
    myIndentOptions = new IndentOptions();
    return myIndentOptions;
  }

  @Nullable
  public FileType getFileType() {
    return myFileType;
  }

  @NotNull
  public CodeStyleSettings getRootSettings() {
    return myRootSettings;
  }

  @Nullable
  public IndentOptions getIndentOptions() {
    return myIndentOptions;
  }

  @Nullable
  public ArrangementSettings getArrangementSettings() {
    return myArrangementSettings;
  }

  public void setArrangementSettings(@NotNull ArrangementSettings settings) {
    myArrangementSettings = settings;
  }

  public void setForceArrangeMenuAvailable(boolean value) {
    myForceArrangeMenuAvailable = value;
  }

  public boolean isForceArrangeMenuAvailable() {
    return myForceArrangeMenuAvailable;
  }

  @SuppressWarnings("unchecked")
  public CommonCodeStyleSettings clone(@NotNull CodeStyleSettings rootSettings) {
    CommonCodeStyleSettings commonSettings = new CommonCodeStyleSettings(myLanguage, getFileType());
    copyPublicFields(this, commonSettings);
    commonSettings.setRootSettings(rootSettings);
    commonSettings.myForceArrangeMenuAvailable = myForceArrangeMenuAvailable;
    if (myIndentOptions != null) {
      IndentOptions targetIndentOptions = commonSettings.initIndentOptions();
      targetIndentOptions.copyFrom(myIndentOptions);
    }
    if (myArrangementSettings != null) {
      commonSettings.setArrangementSettings(myArrangementSettings.clone());
    }
    commonSettings.setSoftMargins(getSoftMargins());
    return commonSettings;
  }

  protected static void copyPublicFields(Object from, Object to) {
    assert from != to;
    ReflectionUtil.copyFields(to.getClass().getFields(), from, to);
  }

  void copyNonDefaultValuesFrom(CommonCodeStyleSettings from) {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(null);
    PARENT_SETTINGS_INSTALLED =
      ReflectionUtil
        .copyFields(getClass().getFields(), from, this, new SupportedFieldsDiffFilter(from, getSupportedFields(), defaultSettings) {
          @Override
          public boolean isAccept(@NotNull Field field) {
            if ("RIGHT_MARGIN".equals(field.getName())) return false; // Never copy RIGHT_MARGIN, it is inherited automatically if -1
            return super.isAccept(field);
          }
        });
  }

  @Nullable
  private CommonCodeStyleSettings getDefaultSettings() {
    return LanguageCodeStyleSettingsProvider.getDefaultCommonSettings(myLanguage);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    if (myIndentOptions != null) {
      Element indentOptionsElement = element.getChild(INDENT_OPTIONS_TAG);
      if (indentOptionsElement != null) {
        myIndentOptions.deserialize(indentOptionsElement);
      }
    }
    Element arrangementRulesContainer = element.getChild(ARRANGEMENT_ELEMENT_NAME);
    if (arrangementRulesContainer != null) {
      myArrangementSettings = ArrangementUtil.readExternal(arrangementRulesContainer, myLanguage);
    }
    mySoftMargins.deserializeFrom(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    CommonCodeStyleSettings defaultSettings = getDefaultSettings();
    Set<String> supportedFields = getSupportedFields();
    if (supportedFields != null) {
      supportedFields.add("PARENT_SETTINGS_INSTALLED");
      supportedFields.add("FORCE_REARRANGE_MODE");
    }
    DefaultJDOMExternalizer.writeExternal(this, element, new SupportedFieldsDiffFilter(this, supportedFields, defaultSettings));
    mySoftMargins.serializeInto(element);
    if (myIndentOptions != null) {
      IndentOptions defaultIndentOptions = defaultSettings != null ? defaultSettings.getIndentOptions() : null;
      Element indentOptionsElement = new Element(INDENT_OPTIONS_TAG);
      myIndentOptions.serialize(indentOptionsElement, defaultIndentOptions);
      if (!indentOptionsElement.getChildren().isEmpty()) {
        element.addContent(indentOptionsElement);
      }
    }

    if (myArrangementSettings != null) {
      Element container = new Element(ARRANGEMENT_ELEMENT_NAME);
      ArrangementUtil.writeExternal(container, myArrangementSettings, myLanguage);
      if (!container.getChildren().isEmpty()) {
        element.addContent(container);
      }
    }
  }

  @Nullable
  private Set<String> getSupportedFields() {
    final LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(myLanguage);
    return provider == null ? null : provider.getSupportedFields();
  }

  private static class SupportedFieldsDiffFilter extends DifferenceFilter<CommonCodeStyleSettings> {
    private final Set<String> mySupportedFieldNames;

    public SupportedFieldsDiffFilter(final CommonCodeStyleSettings object,
                                     Set<String> supportedFiledNames,
                                     final CommonCodeStyleSettings parentObject) {
      super(object, parentObject);
      mySupportedFieldNames = supportedFiledNames;
    }

    @Override
    public boolean isAccept(@NotNull Field field) {
      if (mySupportedFieldNames == null ||
          mySupportedFieldNames.contains(field.getName())) {
        return super.isAccept(field);
      }
      return false;
    }
  }

//----------------- GENERAL --------------------
  public int RIGHT_MARGIN = -1;

  public boolean LINE_COMMENT_AT_FIRST_COLUMN = true;
  public boolean BLOCK_COMMENT_AT_FIRST_COLUMN = true;

  /**
   * Tells if a space is added when commenting/uncommenting lines with a line comment.
   */
  public boolean LINE_COMMENT_ADD_SPACE = false;

  public boolean KEEP_LINE_BREAKS = true;

  /**
   * Controls END_OF_LINE_COMMENT's and C_STYLE_COMMENT's
   */
  public boolean KEEP_FIRST_COLUMN_COMMENT = true;
  public boolean INSERT_FIRST_SPACE_IN_LINE = true;

  /**
   * Keep "if (..) ...;" (also while, for)
   * Does not control "if (..) { .. }"
   */
  public boolean KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;

//----------------- BLANK LINES --------------------

  /**
   * Keep up to this amount of blank lines between declarations
   */
  public int KEEP_BLANK_LINES_IN_DECLARATIONS = 2;

  /**
   * Keep up to this amount of blank lines in code
   */
  public int KEEP_BLANK_LINES_IN_CODE = 2;

  public int KEEP_BLANK_LINES_BEFORE_RBRACE = 2;

  public int BLANK_LINES_BEFORE_PACKAGE = 0;
  public int BLANK_LINES_AFTER_PACKAGE = 1;
  public int BLANK_LINES_BEFORE_IMPORTS = 1;
  public int BLANK_LINES_AFTER_IMPORTS = 1;

  public int BLANK_LINES_AROUND_CLASS = 1;
  public int BLANK_LINES_AROUND_FIELD = 0;
  public int BLANK_LINES_AROUND_METHOD = 1;
  public int BLANK_LINES_BEFORE_METHOD_BODY = 0;

  public int BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 0;
  public int BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 1;


  public int BLANK_LINES_AFTER_CLASS_HEADER = 0;
  public int BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 0;
  //public int BLANK_LINES_BETWEEN_CASE_BLOCKS;


//----------------- BRACES & INDENTS --------------------

  /**
   * <PRE>
   * 1.
   * if (..) {
   * body;
   * }
   * 2.
   * if (..)
   * {
   * body;
   * }
   * 3.
   * if (..)
   * {
   * body;
   * }
   * 4.
   * if (..)
   * {
   * body;
   * }
   * 5.
   * if (long-condition-1 &&
   * long-condition-2)
   * {
   * body;
   * }
   * if (short-condition) {
   * body;
   * }
   * </PRE>
   */

  public static final int END_OF_LINE = 1;
  public static final int NEXT_LINE = 2;
  public static final int NEXT_LINE_SHIFTED = 3;
  public static final int NEXT_LINE_SHIFTED2 = 4;
  public static final int NEXT_LINE_IF_WRAPPED = 5;

  @MagicConstant(intValues = {END_OF_LINE, NEXT_LINE, NEXT_LINE_SHIFTED, NEXT_LINE_SHIFTED2, NEXT_LINE_IF_WRAPPED})
  public @interface BraceStyleConstant {}

  @BraceStyleConstant public int BRACE_STYLE = END_OF_LINE;
  @BraceStyleConstant public int CLASS_BRACE_STYLE = END_OF_LINE;
  @BraceStyleConstant public int METHOD_BRACE_STYLE = END_OF_LINE;
  @BraceStyleConstant public int LAMBDA_BRACE_STYLE = END_OF_LINE;

  /**
   * Defines if 'flying geese' style should be used for curly braces formatting, e.g. if we want to format code like
   * <p/>
   * <pre>
   *     class Test {
   *         {
   *             System.out.println();
   *         }
   *     }
   * </pre>
   * to
   * <pre>
   *     class Test { {
   *         System.out.println();
   *     } }
   * </pre>
   */
  public boolean USE_FLYING_GEESE_BRACES = false;

  public boolean DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = false;

  /**
   * <PRE>
   * "}
   * else"
   * or
   * "} else"
   * </PRE>
   */
  public boolean ELSE_ON_NEW_LINE = false;

  /**
   * <PRE>
   * "}
   * while"
   * or
   * "} while"
   * </PRE>
   */
  public boolean WHILE_ON_NEW_LINE = false;

  /**
   * <PRE>
   * "}
   * catch"
   * or
   * "} catch"
   * </PRE>
   */
  public boolean CATCH_ON_NEW_LINE = false;

  /**
   * <PRE>
   * "}
   * finally"
   * or
   * "} finally"
   * </PRE>
   */
  public boolean FINALLY_ON_NEW_LINE = false;

  public boolean INDENT_CASE_FROM_SWITCH = true;
  
  public boolean CASE_STATEMENT_ON_NEW_LINE = true;

  /**
   * Controls "break" position relative to "case".
   * <pre>
   * case 0:
   * <--->break;
   * </pre>
   */
  public boolean INDENT_BREAK_FROM_CASE = true;

  public boolean SPECIAL_ELSE_IF_TREATMENT = true;

  /**
   * Indicates if long sequence of chained method calls should be aligned.
   * <p/>
   * E.g. if statement like {@code 'foo.bar().bar().bar();'} should be reformatted to the one below if,
   * say, last {@code 'bar()'} call exceeds right margin. The code looks as follows after reformatting
   * if this property is {@code true}:
   * <p/>
   * <pre>
   *     foo.bar().bar()
   *        .bar();
   * </pre>
   */
  public boolean ALIGN_MULTILINE_CHAINED_METHODS = false;
  public boolean ALIGN_MULTILINE_PARAMETERS = true;
  public boolean ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
  public boolean ALIGN_MULTILINE_RESOURCES = true;
  public boolean ALIGN_MULTILINE_FOR = true;
  /** @deprecated Use RubyCodeStyleSettings.INDENT_WITH_CASES */
  @Deprecated
  public boolean INDENT_WHEN_CASES = true;

  public boolean ALIGN_MULTILINE_BINARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_ASSIGNMENT = false;
  public boolean ALIGN_MULTILINE_TERNARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_THROWS_LIST = false;
  public boolean ALIGN_THROWS_KEYWORD = false;

  public boolean ALIGN_MULTILINE_EXTENDS_LIST = false;
  public boolean ALIGN_MULTILINE_METHOD_BRACKETS = false;
  public boolean ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
  public boolean ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = false;

//----------------- Group alignments ---------------

  /**
   * Specifies if subsequent fields/variables declarations and initialisations should be aligned in columns like below:
   * int start = 1;
   * int end   = 10;
   */
  public boolean ALIGN_GROUP_FIELD_DECLARATIONS = false;
  public boolean ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = false;
  public boolean ALIGN_SUBSEQUENT_SIMPLE_METHODS = false;

//----------------- SPACES --------------------

  /**
   * Controls =, +=, -=, etc
   */
  public boolean SPACE_AROUND_ASSIGNMENT_OPERATORS = true;

  /**
   * Controls &&, ||
   */
  public boolean SPACE_AROUND_LOGICAL_OPERATORS = true;

  /**
   * Controls ==, !=
   */
  public boolean SPACE_AROUND_EQUALITY_OPERATORS = true;

  /**
   * Controls <, >, <=, >=
   */
  public boolean SPACE_AROUND_RELATIONAL_OPERATORS = true;

  /**
   * Controls &, |, ^
   */
  public boolean SPACE_AROUND_BITWISE_OPERATORS = true;

  /**
   * Controls +, -
   */
  public boolean SPACE_AROUND_ADDITIVE_OPERATORS = true;

  /**
   * Controls *, /, %
   */
  public boolean SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;

  /**
   * Controls <<. >>, >>>
   */
  public boolean SPACE_AROUND_SHIFT_OPERATORS = true;

  public boolean SPACE_AROUND_UNARY_OPERATOR = false;

  public boolean SPACE_AROUND_LAMBDA_ARROW = true;
  public boolean SPACE_AROUND_METHOD_REF_DBL_COLON = false;

  public boolean SPACE_AFTER_COMMA = true;
  public boolean SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = true;
  public boolean SPACE_BEFORE_COMMA = false;
  public boolean SPACE_AFTER_SEMICOLON = true; // in for-statement
  public boolean SPACE_BEFORE_SEMICOLON = false; // in for-statement

  /**
   * "( expr )"
   * or
   * "(expr)"
   */
  public boolean SPACE_WITHIN_PARENTHESES = false;

  /**
   * "f( expr )"
   * or
   * "f(expr)"
   */
  public boolean SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;

  /**
   * "f( )"
   * or
   * "f()"
   */
  public boolean SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = false;

  /**
   * "void f( int param )"
   * or
   * "void f(int param)"
   */
  public boolean SPACE_WITHIN_METHOD_PARENTHESES = false;

  /**
   * "void f( )"
   * or
   * "void f()"
   */
  public boolean SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = false;

  /**
   * "if( expr )"
   * or
   * "if(expr)"
   */
  public boolean SPACE_WITHIN_IF_PARENTHESES = false;

  /**
   * "while( expr )"
   * or
   * "while(expr)"
   */
  public boolean SPACE_WITHIN_WHILE_PARENTHESES = false;

  /**
   * "for( int i = 0; i < 10; i++ )"
   * or
   * "for(int i = 0; i < 10; i++)"
   */
  public boolean SPACE_WITHIN_FOR_PARENTHESES = false;

  /**
   * "try( Resource r = r() )"
   * or
   * "catch(Resource r = r())"
   */
  public boolean SPACE_WITHIN_TRY_PARENTHESES = false;

  /**
   * "catch( Exception e )"
   * or
   * "catch(Exception e)"
   */
  public boolean SPACE_WITHIN_CATCH_PARENTHESES = false;

  /**
   * "switch( expr )"
   * or
   * "switch(expr)"
   */
  public boolean SPACE_WITHIN_SWITCH_PARENTHESES = false;

  /**
   * "synchronized( expr )"
   * or
   * "synchronized(expr)"
   */
  public boolean SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = false;

  /**
   * "( Type )expr"
   * or
   * "(Type)expr"
   */
  public boolean SPACE_WITHIN_CAST_PARENTHESES = false;

  /**
   * "[ expr ]"
   * or
   * "[expr]"
   */
  public boolean SPACE_WITHIN_BRACKETS = false;

  /**
   * void foo(){ { return; } }
   * or
   * void foo(){{return;}}
   */
  public boolean SPACE_WITHIN_BRACES = false;

  /**
   * "int X[] { 1, 3, 5 }"
   * or
   * "int X[] {1, 3, 5}"
   */
  public boolean SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = false;

  /**
   * "int X[] { }"
   * or
   * "int X[] {}"
   */
  public boolean SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = false;

  public boolean SPACE_AFTER_TYPE_CAST = true;

  /**
   * "f (x)"
   * or
   * "f(x)"
   */
  public boolean SPACE_BEFORE_METHOD_CALL_PARENTHESES = false;

  /**
   * "void f (int param)"
   * or
   * "void f(int param)"
   */
  public boolean SPACE_BEFORE_METHOD_PARENTHESES = false;

  /**
   * "if (...)"
   * or
   * "if(...)"
   */
  public boolean SPACE_BEFORE_IF_PARENTHESES = true;

  /**
   * "while (...)"
   * or
   * "while(...)"
   */
  public boolean SPACE_BEFORE_WHILE_PARENTHESES = true;

  /**
   * "for (...)"
   * or
   * "for(...)"
   */
  public boolean SPACE_BEFORE_FOR_PARENTHESES = true;

  /**
   * "try (...)"
   * or
   * "try(...)"
   */
  public boolean SPACE_BEFORE_TRY_PARENTHESES = true;

  /**
   * "catch (...)"
   * or
   * "catch(...)"
   */
  public boolean SPACE_BEFORE_CATCH_PARENTHESES = true;

  /**
   * "switch (...)"
   * or
   * "switch(...)"
   */
  public boolean SPACE_BEFORE_SWITCH_PARENTHESES = true;

  /**
   * "synchronized (...)"
   * or
   * "synchronized(...)"
   */
  public boolean SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = true;

  /**
   * "class A {"
   * or
   * "class A{"
   */
  public boolean SPACE_BEFORE_CLASS_LBRACE = true;

  /**
   * "void f() {"
   * or
   * "void f(){"
   */
  public boolean SPACE_BEFORE_METHOD_LBRACE = true;

  /**
   * "if (...) {"
   * or
   * "if (...){"
   */
  public boolean SPACE_BEFORE_IF_LBRACE = true;

  /**
   * "else {"
   * or
   * "else{"
   */
  public boolean SPACE_BEFORE_ELSE_LBRACE = true;

  /**
   * "while (...) {"
   * or
   * "while (...){"
   */
  public boolean SPACE_BEFORE_WHILE_LBRACE = true;

  /**
   * "for (...) {"
   * or
   * "for (...){"
   */
  public boolean SPACE_BEFORE_FOR_LBRACE = true;

  /**
   * "do {"
   * or
   * "do{"
   */
  public boolean SPACE_BEFORE_DO_LBRACE = true;

  /**
   * "switch (...) {"
   * or
   * "switch (...){"
   */
  public boolean SPACE_BEFORE_SWITCH_LBRACE = true;

  /**
   * "try {"
   * or
   * "try{"
   */
  public boolean SPACE_BEFORE_TRY_LBRACE = true;

  /**
   * "catch (...) {"
   * or
   * "catch (...){"
   */
  public boolean SPACE_BEFORE_CATCH_LBRACE = true;

  /**
   * "finally {"
   * or
   * "finally{"
   */
  public boolean SPACE_BEFORE_FINALLY_LBRACE = true;

  /**
   * "synchronized (...) {"
   * or
   * "synchronized (...){"
   */
  public boolean SPACE_BEFORE_SYNCHRONIZED_LBRACE = true;

  /**
   * "new int[] {"
   * or
   * "new int[]{"
   */
  public boolean SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;

  /**
   * '@SuppressWarnings({"unchecked"})
   * or
   * '@SuppressWarnings( {"unchecked"})
   */
  public boolean SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = false;

  public boolean SPACE_BEFORE_ELSE_KEYWORD = true;
  public boolean SPACE_BEFORE_WHILE_KEYWORD = true;
  public boolean SPACE_BEFORE_CATCH_KEYWORD = true;
  public boolean SPACE_BEFORE_FINALLY_KEYWORD = true;

  public boolean SPACE_BEFORE_QUEST = true;
  public boolean SPACE_AFTER_QUEST = true;
  public boolean SPACE_BEFORE_COLON = true;
  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_TYPE_PARAMETER_LIST = false;

  //----------------- WRAPPING ---------------------------

  public static final int DO_NOT_WRAP = 0x00;
  public static final int WRAP_AS_NEEDED = 0x01;
  public static final int WRAP_ALWAYS = 0x02;
  public static final int WRAP_ON_EVERY_ITEM = 0x04;

  public int CALL_PARAMETERS_WRAP = DO_NOT_WRAP;
  public boolean PREFER_PARAMETERS_WRAP = false;
  public boolean CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false; // misnamed, actually means: wrap AFTER lparen
  public boolean CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  public int METHOD_PARAMETERS_WRAP = DO_NOT_WRAP;
  public boolean METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
  public boolean METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  public int RESOURCE_LIST_WRAP = DO_NOT_WRAP;
  public boolean RESOURCE_LIST_LPAREN_ON_NEXT_LINE = false;
  public boolean RESOURCE_LIST_RPAREN_ON_NEXT_LINE = false;

  public int EXTENDS_LIST_WRAP = DO_NOT_WRAP;
  public int THROWS_LIST_WRAP = DO_NOT_WRAP;

  public int EXTENDS_KEYWORD_WRAP = DO_NOT_WRAP;
  public int THROWS_KEYWORD_WRAP = DO_NOT_WRAP;

  public int METHOD_CALL_CHAIN_WRAP = DO_NOT_WRAP;
  public boolean WRAP_FIRST_METHOD_IN_CALL_CHAIN = false;

  public boolean PARENTHESES_EXPRESSION_LPAREN_WRAP = false;
  public boolean PARENTHESES_EXPRESSION_RPAREN_WRAP = false;

  public int BINARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;

  public int TERNARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = false;

  public boolean MODIFIER_LIST_WRAP = false;

  public boolean KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_CLASSES_IN_ONE_LINE = false;
  public boolean KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = false;

  public int FOR_STATEMENT_WRAP = DO_NOT_WRAP;
  public boolean FOR_STATEMENT_LPAREN_ON_NEXT_LINE = false;
  public boolean FOR_STATEMENT_RPAREN_ON_NEXT_LINE = false;

  public int ARRAY_INITIALIZER_WRAP = DO_NOT_WRAP;
  public boolean ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = false;
  public boolean ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = false;

  public int ASSIGNMENT_WRAP = DO_NOT_WRAP;
  public boolean PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false;

  public int LABELED_STATEMENT_WRAP = WRAP_ALWAYS;

  public boolean WRAP_COMMENTS = false;

  public int ASSERT_STATEMENT_WRAP = DO_NOT_WRAP;
  public boolean ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false;

  // BRACE FORCING
  public static final int DO_NOT_FORCE = 0x00;
  public static final int FORCE_BRACES_IF_MULTILINE = 0x01;
  public static final int FORCE_BRACES_ALWAYS = 0x03;

  public int IF_BRACE_FORCE = DO_NOT_FORCE;
  public int DOWHILE_BRACE_FORCE = DO_NOT_FORCE;
  public int WHILE_BRACE_FORCE = DO_NOT_FORCE;
  public int FOR_BRACE_FORCE = DO_NOT_FORCE;

  public boolean WRAP_LONG_LINES = false;

  //-------------- Annotation formatting settings-------------------------------------------

  public int METHOD_ANNOTATION_WRAP = WRAP_ALWAYS;
  public int CLASS_ANNOTATION_WRAP = WRAP_ALWAYS;
  public int FIELD_ANNOTATION_WRAP = WRAP_ALWAYS;
  public int PARAMETER_ANNOTATION_WRAP = DO_NOT_WRAP;
  public int VARIABLE_ANNOTATION_WRAP = DO_NOT_WRAP;

  public boolean SPACE_BEFORE_ANOTATION_PARAMETER_LIST = false;
  public boolean SPACE_WITHIN_ANNOTATION_PARENTHESES = false;

  //----------------------------------------------------------------------------------------


  //-------------------------Enums----------------------------------------------------------
  public int ENUM_CONSTANTS_WRAP = DO_NOT_WRAP;

  //
  // The flag telling that original default settings were overwritten with non-default
  // values from shared code style settings (happens upon the very first initialization).
  //
  public boolean PARENT_SETTINGS_INSTALLED = false;

  //-------------------------Force rearrange settings---------------------------------------
  public static int REARRANGE_ACCORDIND_TO_DIALOG = 0;
  public static int REARRANGE_ALWAYS = 1;
  public static int REARRANGE_NEVER = 2;

  public int FORCE_REARRANGE_MODE = REARRANGE_ACCORDIND_TO_DIALOG;

  public enum WrapOnTyping {
    DEFAULT(-1),
    NO_WRAP (0),
    WRAP (1);

    public int intValue;

    WrapOnTyping(int i) {
      intValue = i;
    }
  }

  /**
   * Defines if wrapping should occur when typing reaches right margin. <b>Do not use a value of this field directly, call
   * {@link CodeStyleSettings#isWrapOnTyping(Language)} method instead</b>.
   */
  public int WRAP_ON_TYPING = WrapOnTyping.DEFAULT.intValue;

  //-------------------------Indent options-------------------------------------------------
  public static class IndentOptions implements Cloneable, JDOMExternalizable {
    public int INDENT_SIZE = 4;
    public int CONTINUATION_INDENT_SIZE = 8;
    public int TAB_SIZE = 4;
    public boolean USE_TAB_CHARACTER = false;
    public boolean SMART_TABS = false;
    public int LABEL_INDENT_SIZE = 0;
    public boolean LABEL_INDENT_ABSOLUTE = false;
    public boolean USE_RELATIVE_INDENTS = false;
    public boolean KEEP_INDENTS_ON_EMPTY_LINES = false;

    private FileIndentOptionsProvider myFileIndentOptionsProvider;
    private static final Key<CommonCodeStyleSettings.IndentOptions> INDENT_OPTIONS_KEY = Key.create("INDENT_OPTIONS_KEY");
    private boolean myOverrideLanguageOptions;

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element, field -> {
        if ("KEEP_INDENTS_ON_EMPTY_LINES".equals(field.getName())) {
          return KEEP_INDENTS_ON_EMPTY_LINES;
        }
        return true;
      });
    }

    public void serialize(Element indentOptionsElement, final IndentOptions defaultOptions) {
      XmlSerializer.serializeInto(this, indentOptionsElement, new SkipDefaultValuesSerializationFilters() {
        @Override
        protected void configure(@NotNull Object o) {
          if (o instanceof IndentOptions && defaultOptions != null) {
            ((IndentOptions)o).copyFrom(defaultOptions);
          }
        }
      });
    }

    public void deserialize(Element indentOptionsElement) {
      XmlSerializer.deserializeInto(this, indentOptionsElement);
    }

    @Override
    public Object clone() {
      try {
        return super.clone();
      }
      catch (CloneNotSupportedException e) {
        // Cannot happen
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      IndentOptions that = (IndentOptions)o;

      if (CONTINUATION_INDENT_SIZE != that.CONTINUATION_INDENT_SIZE) return false;
      if (INDENT_SIZE != that.INDENT_SIZE) return false;
      if (LABEL_INDENT_ABSOLUTE != that.LABEL_INDENT_ABSOLUTE) return false;
      if (USE_RELATIVE_INDENTS != that.USE_RELATIVE_INDENTS) return false;
      if (LABEL_INDENT_SIZE != that.LABEL_INDENT_SIZE) return false;
      if (SMART_TABS != that.SMART_TABS) return false;
      if (TAB_SIZE != that.TAB_SIZE) return false;
      if (USE_TAB_CHARACTER != that.USE_TAB_CHARACTER) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = INDENT_SIZE;
      result = 31 * result + CONTINUATION_INDENT_SIZE;
      result = 31 * result + TAB_SIZE;
      result = 31 * result + (USE_TAB_CHARACTER ? 1 : 0);
      result = 31 * result + (SMART_TABS ? 1 : 0);
      result = 31 * result + LABEL_INDENT_SIZE;
      result = 31 * result + (LABEL_INDENT_ABSOLUTE ? 1 : 0);
      result = 31 * result + (USE_RELATIVE_INDENTS ? 1 : 0);
      return result;
    }

    public void copyFrom(IndentOptions other) {
      copyPublicFields(other, this);
    }

    @Nullable
    FileIndentOptionsProvider getFileIndentOptionsProvider() {
      return myFileIndentOptionsProvider;
    }

    void setFileIndentOptionsProvider(@NotNull FileIndentOptionsProvider provider) {
      myFileIndentOptionsProvider = provider;
    }

    void associateWithDocument(@NotNull Document document) {
      document.putUserData(INDENT_OPTIONS_KEY, this);
    }

    @Nullable
    public static IndentOptions retrieveFromAssociatedDocument(@NotNull PsiFile file) {
      Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      return document != null ? document.getUserData(INDENT_OPTIONS_KEY) : null;
    }

    /**
     * @return True if the options can override the ones defined in language settings.
     * @see CommonCodeStyleSettings.IndentOptions#setOverrideLanguageOptions(boolean) 
     */
    public boolean isOverrideLanguageOptions() {
      return myOverrideLanguageOptions;
    }

    /**
     * Make the indent options override options defined for a language block if the block implements {@code BlockEx.getLanguage()}
     * Useful when indent options provider must take a priority over any language settings for a formatter block.
     * 
     * @param overrideLanguageOptions True if language block options should be ignored.
     * @see FileIndentOptionsProvider
     */
    public void setOverrideLanguageOptions(boolean overrideLanguageOptions) {
      myOverrideLanguageOptions = overrideLanguageOptions;
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CommonCodeStyleSettings) {
      if (
        ReflectionUtil.comparePublicNonFinalFields(this, obj) &&
        myIndentOptions.equals(((CommonCodeStyleSettings)obj).getIndentOptions()) &&
        arrangementSettingsEqual((CommonCodeStyleSettings)obj)
        ) {
        return true;
      }
    }
    return false;
  }

  protected boolean arrangementSettingsEqual(CommonCodeStyleSettings obj) {
    ArrangementSettings theseSettings = myArrangementSettings;
    ArrangementSettings otherSettings = obj.getArrangementSettings();
    if (theseSettings == null && otherSettings != null) {
      Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(myLanguage);
      if (rearranger instanceof ArrangementStandardSettingsAware) {
        theseSettings = ((ArrangementStandardSettingsAware)rearranger).getDefaultSettings();
      }
    }
    return Comparing.equal(theseSettings, obj.getArrangementSettings());
  }

  @NotNull
  public List<Integer> getSoftMargins() {
    return mySoftMargins.getValues();
  }

  void setSoftMargins(List<Integer> values) {
    mySoftMargins.setValues(values);
  }
}
