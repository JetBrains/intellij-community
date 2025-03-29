finkerdomia@gmail.com
 Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.application.options.codeStyle.properties.CommaSeparatedValues;
import com.intellij.configurationStore.Property;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsAware;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.codeStyle.CodeStyleDefaults.*;

/**
 * Common code style settings can be used by several programming languages. Each language may have its own
 * instance of {@code CommonCodeStyleSettings}.
 */
public class CommonCodeStyleSettings implements CommentStyleSettings {
  // Dev. notes:
  // - Do not add language-specific options here, use CustomCodeStyleSettings instead.
  // - New options should be added to CodeStyleSettingsCustomizable as well.
  // - Covered by CodeStyleConfigurationsTest.

  private static final @NonNls String ARRANGEMENT_ELEMENT_NAME = "arrangement";

  private final @NotNull String myLangId;

  private ArrangementSettings myArrangementSettings;
  private CodeStyleSettings   myRootSettings;
  private @Nullable IndentOptions       myIndentOptions;
  private boolean             myForceArrangeMenuAvailable;

  private final SoftMargins mySoftMargins = new SoftMargins();

  private static final @NonNls String INDENT_OPTIONS_TAG = "indentOptions";


  private static final Logger LOG = Logger.getInstance(CommonCodeStyleSettings.class);

  public CommonCodeStyleSettings(@Nullable Language language) {
    this(ObjectUtils.notNull(language, Language.ANY).getID());
  }

  private CommonCodeStyleSettings(@NotNull String langId) {
    myLangId = langId;
  }

  void setRootSettings(@NotNull CodeStyleSettings rootSettings) {
    myRootSettings = rootSettings;
  }

  public @NotNull Language getLanguage() {
    Language language = Language.findLanguageByID(myLangId);
    if (language == null) {
      LOG.error("Can't find the language with ID " + myLangId);
      return Language.ANY;
    }
    return language;
  }

  public @NotNull IndentOptions initIndentOptions() {
    myIndentOptions = new IndentOptions();
    return myIndentOptions;
  }

  public @NotNull CodeStyleSettings getRootSettings() {
    return myRootSettings;
  }

  public @Nullable IndentOptions getIndentOptions() {
    return myIndentOptions;
  }

  public @Nullable ArrangementSettings getArrangementSettings() {
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

  public CommonCodeStyleSettings clone(@NotNull CodeStyleSettings rootSettings) {
    CommonCodeStyleSettings commonSettings = new CommonCodeStyleSettings(myLangId);
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

  public void copyFrom(@NotNull CommonCodeStyleSettings source) {
    copyPublicFields(source, this);
    if (myIndentOptions != null) {
      CommonCodeStyleSettings.IndentOptions sourceIndentOptions = source.getIndentOptions();
      if (sourceIndentOptions != null) {
        myIndentOptions.copyFrom(sourceIndentOptions);
      }
    }
    setSoftMargins(source.getSoftMargins());
  }


  public void readExternal(Element element) {
    //noinspection deprecation
    DefaultJDOMExternalizer.readExternal(this, element);
    if (myIndentOptions != null) {
      Element indentOptionsElement = element.getChild(INDENT_OPTIONS_TAG);
      if (indentOptionsElement != null) {
        myIndentOptions.deserialize(indentOptionsElement);
      }
    }
    Element arrangementRulesContainer = element.getChild(ARRANGEMENT_ELEMENT_NAME);
    if (arrangementRulesContainer != null) {
      myArrangementSettings = ArrangementUtil.readExternal(arrangementRulesContainer, getLanguage());
    }
    mySoftMargins.deserializeFrom(element);
    LOG.info("Loaded " + getLanguage().getDisplayName() + " common code style settings");
  }

  @ApiStatus.Internal
  public void writeExternal(Element element) {
    LanguageCodeStyleProvider provider = LanguageCodeStyleProvider.forLanguage(getLanguage());
    if (provider != null) {
      writeExternal(element, provider);
    }
  }

  @ApiStatus.Internal
  public void writeExternal(@NotNull Element element, @NotNull LanguageCodeStyleProvider provider) {
    CommonCodeStyleSettings defaultSettings = provider.getDefaultCommonSettings();
    Set<String> supportedFields = provider.getSupportedFields();
    if (supportedFields != null) {
      supportedFields.add("FORCE_REARRANGE_MODE");
    }
    else {
      return;
    }
    //noinspection deprecation
    DefaultJDOMExternalizer.write(this, element, new SupportedFieldsDiffFilter(this, supportedFields, defaultSettings));
    mySoftMargins.serializeInto(element);
    if (myIndentOptions != null) {
      IndentOptions defaultIndentOptions = defaultSettings.getIndentOptions();
      Element indentOptionsElement = new Element(INDENT_OPTIONS_TAG);
      myIndentOptions.serialize(indentOptionsElement, defaultIndentOptions);
      if (!indentOptionsElement.getChildren().isEmpty()) {
        element.addContent(indentOptionsElement);
      }
    }

    if (myArrangementSettings != null) {
      Element container = new Element(ARRANGEMENT_ELEMENT_NAME);
      ArrangementUtil.writeExternal(container, myArrangementSettings, provider.getLanguage());
      if (!container.getChildren().isEmpty()) {
        element.addContent(container);
      }
    }
  }

  @Override
  public boolean isLineCommentInTheFirstColumn() {
    return LINE_COMMENT_AT_FIRST_COLUMN;
  }

  @Override
  public boolean isLineCommentFollowedWithSpace() {
    return LINE_COMMENT_ADD_SPACE;
  }

  @Override
  public boolean isBlockCommentIncludesSpace() {
    return BLOCK_COMMENT_ADD_SPACE;
  }


  public static final class SupportedFieldsDiffFilter extends DifferenceFilter<CommonCodeStyleSettings> {
    private final Set<String> mySupportedFieldNames;

    public SupportedFieldsDiffFilter(final CommonCodeStyleSettings object,
                                     Set<String> supportedFiledNames,
                                     final CommonCodeStyleSettings parentObject) {
      super(object, parentObject);
      mySupportedFieldNames = supportedFiledNames;
    }

    @Override
    public boolean test(@NotNull Field field) {
      if (mySupportedFieldNames != null &&
          mySupportedFieldNames.contains(field.getName())) {
        return super.test(field);
      }
      return false;
    }
  }

//----------------- GENERAL --------------------
  @Property(externalName = "max_line_length")
  public int RIGHT_MARGIN = -1;

  public boolean LINE_COMMENT_AT_FIRST_COLUMN = true;
  public boolean BLOCK_COMMENT_AT_FIRST_COLUMN = true;

  /**
   * Tells if a space is added when commenting/uncommenting lines with a line comment.
   */
  public boolean LINE_COMMENT_ADD_SPACE = false;
  public boolean BLOCK_COMMENT_ADD_SPACE = false;

  public boolean LINE_COMMENT_ADD_SPACE_ON_REFORMAT = false;
  public boolean LINE_COMMENT_ADD_SPACE_IN_SUPPRESSION = false;

  public boolean KEEP_LINE_BREAKS = true;

  /**
   * Controls END_OF_LINE_COMMENT's and C_STYLE_COMMENT's
   */
  public boolean KEEP_FIRST_COLUMN_COMMENT = true;

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

  /**
   * Keep up to this amount of blank lines between package declaration and header
   */
  public int KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER = 2;

  @Property(externalName = "keep_blank_lines_before_right_brace")
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

  /**
   * In Java-like languages specifies a number of blank lines before class closing brace '}'.
   */
  public int BLANK_LINES_BEFORE_CLASS_END = 0;

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
  @Retention(RetentionPolicy.RUNTIME)
  public @interface BraceStyleConstant {}

  @Property(externalName = "block_brace_style")
  @BraceStyleConstant public int BRACE_STYLE = END_OF_LINE;
  @BraceStyleConstant public int CLASS_BRACE_STYLE = END_OF_LINE;
  @BraceStyleConstant public int METHOD_BRACE_STYLE = END_OF_LINE;
  @BraceStyleConstant public int LAMBDA_BRACE_STYLE = END_OF_LINE;

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

  @Property(externalName = "case_statement_on_separate_line")
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

  public boolean ALIGN_MULTILINE_BINARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_ASSIGNMENT = false;
  public boolean ALIGN_MULTILINE_TERNARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_THROWS_LIST = false;
  public boolean ALIGN_THROWS_KEYWORD = false;

  public boolean ALIGN_MULTILINE_EXTENDS_LIST = false;
  @Property(externalName = "align_multiline_method_parentheses")
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
  public boolean ALIGN_CONSECUTIVE_ASSIGNMENTS = false;
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

  @Property(
    externalName = "space_after_for_semicolon"
  )
  public boolean SPACE_AFTER_SEMICOLON = true; // in for-statement
  @Property(
    externalName = "space_before_for_semicolon"
  )
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
  @Property(externalName = "space_within_empty_method_call_parentheses")
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
  @Property(externalName = "space_within_empty_method_parentheses")
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
   * "try(Resource r = r())"
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
  @Property(externalName = "space_within_empty_array_initializer_braces")
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
  @Property(externalName = "space_before_class_left_brace")
  public boolean SPACE_BEFORE_CLASS_LBRACE = true;

  /**
   * "void f() {"
   * or
   * "void f(){"
   */
  @Property(externalName = "space_before_method_left_brace")
  public boolean SPACE_BEFORE_METHOD_LBRACE = true;

  /**
   * "if (...) {"
   * or
   * "if (...){"
   */
  @Property(externalName = "space_before_if_left_brace")
  public boolean SPACE_BEFORE_IF_LBRACE = true;

  /**
   * "else {"
   * or
   * "else{"
   */
  @Property(externalName = "space_before_else_left_brace")
  public boolean SPACE_BEFORE_ELSE_LBRACE = true;

  /**
   * "while (...) {"
   * or
   * "while (...){"
   */
  @Property(externalName = "space_before_while_left_brace")
  public boolean SPACE_BEFORE_WHILE_LBRACE = true;

  /**
   * "for (...) {"
   * or
   * "for (...){"
   */
  @Property(externalName = "space_before_for_left_brace")
  public boolean SPACE_BEFORE_FOR_LBRACE = true;

  /**
   * "do {"
   * or
   * "do{"
   */
  @Property(externalName = "space_before_do_left_brace")
  public boolean SPACE_BEFORE_DO_LBRACE = true;

  /**
   * "switch (...) {"
   * or
   * "switch (...){"
   */
  @Property(externalName = "space_before_switch_left_brace")
  public boolean SPACE_BEFORE_SWITCH_LBRACE = true;

  /**
   * "try {"
   * or
   * "try{"
   */
  @Property(externalName = "space_before_try_left_brace")
  public boolean SPACE_BEFORE_TRY_LBRACE = true;

  /**
   * "catch (...) {"
   * or
   * "catch (...){"
   */
  @Property(externalName = "space_before_catch_left_brace")
  public boolean SPACE_BEFORE_CATCH_LBRACE = true;

  /**
   * "finally {"
   * or
   * "finally{"
   */
  @Property(externalName = "space_before_finally_left_brace")
  public boolean SPACE_BEFORE_FINALLY_LBRACE = true;

  /**
   * "synchronized (...) {"
   * or
   * "synchronized (...){"
   */
  @Property(externalName = "space_before_synchronized_left_brace")
  public boolean SPACE_BEFORE_SYNCHRONIZED_LBRACE = true;

  /**
   * "new int[] {"
   * or
   * "new int[]{"
   */
  @Property(externalName = "space_before_array_initializer_left_brace")
  public boolean SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;

  /**
   * '@SuppressWarnings({"unchecked"})
   * or
   * '@SuppressWarnings( {"unchecked"})
   */
  @Property(externalName = "space_before_annotation_array_initializer_left_brace")
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

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface WrapConstant {
  }

  public static final int DO_NOT_WRAP = 0x00;
  public static final int WRAP_AS_NEEDED = 0x01;
  public static final int WRAP_ALWAYS = 0x02;
  public static final int WRAP_ON_EVERY_ITEM = 0x04;

  @WrapConstant
  public int CALL_PARAMETERS_WRAP = DO_NOT_WRAP;
  public boolean PREFER_PARAMETERS_WRAP = false;
  @Property(externalName = "call_parameters_new_line_after_left_paren")
  public boolean CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false; // misnamed, actually means: wrap AFTER lparen
  @Property(externalName = "call_parameters_right_paren_on_new_line")
  public boolean CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  @WrapConstant
  public int METHOD_PARAMETERS_WRAP = DO_NOT_WRAP;
  @Property(externalName = "method_parameters_new_line_after_left_paren")
  public boolean METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
  @Property(externalName = "method_parameters_right_paren_on_new_line")
  public boolean METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  @WrapConstant
  public int RESOURCE_LIST_WRAP = DO_NOT_WRAP;
  @Property(externalName = "resource_list_new_line_after_left_paren")
  public boolean RESOURCE_LIST_LPAREN_ON_NEXT_LINE = false;
  @Property(externalName = "resource_list_right_paren_on_new_line")
  public boolean RESOURCE_LIST_RPAREN_ON_NEXT_LINE = false;

  @WrapConstant
  public int EXTENDS_LIST_WRAP = DO_NOT_WRAP;
  @WrapConstant
  public int THROWS_LIST_WRAP = DO_NOT_WRAP;

  @WrapConstant
  public int EXTENDS_KEYWORD_WRAP = DO_NOT_WRAP;
  @WrapConstant
  public int THROWS_KEYWORD_WRAP = DO_NOT_WRAP;

  @WrapConstant
  public int METHOD_CALL_CHAIN_WRAP = DO_NOT_WRAP;
  public boolean WRAP_FIRST_METHOD_IN_CALL_CHAIN = false;

  @Property(externalName = "parentheses_expression_new_line_after_left_paren")
  public boolean PARENTHESES_EXPRESSION_LPAREN_WRAP = false;
  @Property(externalName = "parentheses_expression_right_paren_on_new_line")
  public boolean PARENTHESES_EXPRESSION_RPAREN_WRAP = false;

  @WrapConstant
  public int BINARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;

  @WrapConstant
  public int TERNARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = false;

  @WrapConstant
  public boolean MODIFIER_LIST_WRAP = false;

  public boolean KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_CLASSES_IN_ONE_LINE = false;
  public boolean KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = false;

  @WrapConstant
  public int FOR_STATEMENT_WRAP = DO_NOT_WRAP;
  @Property(externalName = "for_statement_new_line_after_left_paren")
  public boolean FOR_STATEMENT_LPAREN_ON_NEXT_LINE = false;
  @Property(externalName = "for_statement_right_paren_on_new_line")
  public boolean FOR_STATEMENT_RPAREN_ON_NEXT_LINE = false;

  @WrapConstant
  public int ARRAY_INITIALIZER_WRAP = DO_NOT_WRAP;
  @Property(externalName = "array_initializer_new_line_after_left_brace")
  public boolean ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = false;
  @Property(externalName = "array_initializer_right_brace_on_new_line")
  public boolean ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = false;

  @WrapConstant
  public int ASSIGNMENT_WRAP = DO_NOT_WRAP;
  public boolean PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false;

  public boolean WRAP_COMMENTS = false;

  @WrapConstant
  public int ASSERT_STATEMENT_WRAP = DO_NOT_WRAP;

  @WrapConstant
  public int SWITCH_EXPRESSIONS_WRAP = WRAP_AS_NEEDED;
  public boolean ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false;

  // BRACE FORCING
  public static final int DO_NOT_FORCE = 0x00;
  public static final int FORCE_BRACES_IF_MULTILINE = 0x01;
  public static final int FORCE_BRACES_ALWAYS = 0x03;

  @MagicConstant(intValues = {DO_NOT_FORCE, FORCE_BRACES_IF_MULTILINE, FORCE_BRACES_ALWAYS})
  public @interface ForceBraceConstant {
  }

  @ForceBraceConstant public int IF_BRACE_FORCE = DO_NOT_FORCE;
  @Property(externalName = "do_while_brace_force")
  @ForceBraceConstant public int DOWHILE_BRACE_FORCE = DO_NOT_FORCE;
  @ForceBraceConstant public int WHILE_BRACE_FORCE = DO_NOT_FORCE;
  @ForceBraceConstant public int FOR_BRACE_FORCE = DO_NOT_FORCE;

  public boolean WRAP_LONG_LINES = false;

  //-------------- Annotation formatting settings-------------------------------------------

  @WrapConstant
  public int METHOD_ANNOTATION_WRAP = WRAP_ALWAYS;
  @WrapConstant
  public int CLASS_ANNOTATION_WRAP = WRAP_ALWAYS;
  @WrapConstant
  public int FIELD_ANNOTATION_WRAP = WRAP_ALWAYS;
  @WrapConstant
  public int PARAMETER_ANNOTATION_WRAP = DO_NOT_WRAP;
  @WrapConstant
  public int VARIABLE_ANNOTATION_WRAP = DO_NOT_WRAP;

  @Property(externalName = "space_before_annotation_parameter_list")
  public boolean SPACE_BEFORE_ANOTATION_PARAMETER_LIST = false;
  public boolean SPACE_WITHIN_ANNOTATION_PARENTHESES = false;

  //----------------------------------------------------------------------------------------


  //-------------------------Enums----------------------------------------------------------
  @WrapConstant
  public int ENUM_CONSTANTS_WRAP = DO_NOT_WRAP;

  // region Chained Builder Method Calls
  //----------------------------------------------------------------------------------------

  @CommaSeparatedValues public @NonNls String BUILDER_METHODS = "";
  public boolean KEEP_BUILDER_METHODS_INDENTS = false;

  private final @NotNull Set<String> myBuilderMethodsNameCache = new HashSet<>();
  private @NotNull String myCachedBuilderMethods = "";

  public boolean isBuilderMethod(@NotNull String methodName) {
    if (!StringUtil.equals(BUILDER_METHODS, myCachedBuilderMethods)) {
      myCachedBuilderMethods = BUILDER_METHODS;
      myBuilderMethodsNameCache.clear();
      Arrays.stream(BUILDER_METHODS.split(","))
        .filter(chunk -> !StringUtil.isEmptyOrSpaces(chunk))
        .forEach(chunk -> {
          myBuilderMethodsNameCache.add(chunk.trim());
        });
    }
    return myBuilderMethodsNameCache.contains(methodName);
  }


  // endregion

  //-------------------------Force rearrange settings---------------------------------------
  public static final int REARRANGE_ACCORDIND_TO_DIALOG = 0;
  public static final int REARRANGE_ALWAYS = 1;
  public static final int REARRANGE_NEVER = 2;

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
    public static final IndentOptions DEFAULT_INDENT_OPTIONS = new IndentOptions();

    public int INDENT_SIZE = DEFAULT_INDENT_SIZE;
    public int CONTINUATION_INDENT_SIZE = DEFAULT_CONTINUATION_INDENT_SIZE;
    @Property(externalName = "tab_width")
    public int TAB_SIZE = DEFAULT_TAB_SIZE;
    public boolean USE_TAB_CHARACTER = false;
    public boolean SMART_TABS = false;
    public int LABEL_INDENT_SIZE = 0;
    public boolean LABEL_INDENT_ABSOLUTE = false;
    public boolean USE_RELATIVE_INDENTS = false;
    public boolean KEEP_INDENTS_ON_EMPTY_LINES = false;

    // region More continuations (reserved for versions 2018.x)
    public int DECLARATION_PARAMETER_INDENT = - 1;
    public int GENERIC_TYPE_PARAMETER_INDENT = -1;
    public int CALL_PARAMETER_INDENT = -1;
    public int CHAINED_CALL_INDENT = -1;
    public int ARRAY_ELEMENT_INDENT = -1; // array declarations
    // endregion

    private FileIndentOptionsProvider myFileIndentOptionsProvider;
    private static final Key<CommonCodeStyleSettings.IndentOptions> INDENT_OPTIONS_KEY = Key.create("INDENT_OPTIONS_KEY");
    private boolean myOverrideLanguageOptions;

    @Override
    public void readExternal(Element element) {
      deserialize(element);
    }

    @Override
    public void writeExternal(Element element) {
      serialize(element, DEFAULT_INDENT_OPTIONS);
    }

    public void serialize(@NotNull Element indentOptionsElement, @Nullable IndentOptions defaultOptions) {
      SerializationFilter filter = null;
      if (defaultOptions != null) {
        //noinspection deprecation
        filter = new SkipDefaultValuesSerializationFilters() {
          @Override
          protected void configure(@NotNull Object o) {
            if (o instanceof IndentOptions) {
              ((IndentOptions)o).copyFrom(defaultOptions);
            }
          }
        };
      }
      XmlSerializer.serializeObjectInto(this, indentOptionsElement, filter);
    }

    public void deserialize(Element indentOptionsElement) {
      XmlSerializer.deserializeInto(indentOptionsElement, this);
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
      if (!(o instanceof IndentOptions that)) return false;

      if (CONTINUATION_INDENT_SIZE != that.CONTINUATION_INDENT_SIZE) return false;
      if (INDENT_SIZE != that.INDENT_SIZE) return false;
      if (LABEL_INDENT_ABSOLUTE != that.LABEL_INDENT_ABSOLUTE) return false;
      if (USE_RELATIVE_INDENTS != that.USE_RELATIVE_INDENTS) return false;
      if (LABEL_INDENT_SIZE != that.LABEL_INDENT_SIZE) return false;
      if (SMART_TABS != that.SMART_TABS) return false;
      if (TAB_SIZE != that.TAB_SIZE) return false;
      if (USE_TAB_CHARACTER != that.USE_TAB_CHARACTER) return false;

      if (DECLARATION_PARAMETER_INDENT != that.DECLARATION_PARAMETER_INDENT) return false;
      if (GENERIC_TYPE_PARAMETER_INDENT != that.GENERIC_TYPE_PARAMETER_INDENT) return false;
      if (CALL_PARAMETER_INDENT != that.CALL_PARAMETER_INDENT) return false;
      if (CHAINED_CALL_INDENT != that.CHAINED_CALL_INDENT) return false;
      if (ARRAY_ELEMENT_INDENT != that.ARRAY_ELEMENT_INDENT) return false;

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
      myOverrideLanguageOptions = other.myOverrideLanguageOptions;
    }

    public @Nullable FileIndentOptionsProvider getFileIndentOptionsProvider() {
      return myFileIndentOptionsProvider;
    }

    void setFileIndentOptionsProvider(@NotNull FileIndentOptionsProvider provider) {
      myFileIndentOptionsProvider = provider;
    }

    public void associateWithDocument(@NotNull Document document) {
      document.putUserData(INDENT_OPTIONS_KEY, this);
    }

    /**
     * @deprecated Use {@link #retrieveFromAssociatedDocument(Document)}
     */
    @Deprecated
    public static @Nullable IndentOptions retrieveFromAssociatedDocument(@NotNull PsiFile file) {
      Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      return document != null ? document.getUserData(INDENT_OPTIONS_KEY) : null;
    }

    public static @Nullable IndentOptions retrieveFromAssociatedDocument(@NotNull Document document) {
      return document.getUserData(INDENT_OPTIONS_KEY);
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
        mySoftMargins.equals(((CommonCodeStyleSettings)obj).mySoftMargins) &&
        Comparing.equal(myIndentOptions, ((CommonCodeStyleSettings)obj).getIndentOptions()) &&
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
      Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(getLanguage());
      if (rearranger instanceof ArrangementStandardSettingsAware) {
        theseSettings = ((ArrangementStandardSettingsAware)rearranger).getDefaultSettings();
      }
    }
    return Comparing.equal(theseSettings, obj.getArrangementSettings());
  }

  public @NotNull List<Integer> getSoftMargins() {
    return mySoftMargins.getValues();
  }

  public void setSoftMargins(List<Integer> values) {
    mySoftMargins.setValues(values);
  }
}
