/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ClassMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class CodeStyleSettings implements Cloneable, JDOMExternalizable {
  private final ClassMap<CustomCodeStyleSettings> myCustomSettings = new ClassMap<CustomCodeStyleSettings>();

  @NonNls private static final String ADDITIONAL_INDENT_OPTIONS = "ADDITIONAL_INDENT_OPTIONS";
  @NonNls private static final String FILETYPE = "fileType";

  public CodeStyleSettings() {
    this(true);
  }

  public CodeStyleSettings(boolean loadExtensions) {
    initTypeToName();
    initImportsByDefault();

    if (loadExtensions) {
      final CodeStyleSettingsProvider[] codeStyleSettingsProviders = Extensions.getExtensions(CodeStyleSettingsProvider.EXTENSION_POINT_NAME);
      for (final CodeStyleSettingsProvider provider : codeStyleSettingsProviders) {
        addCustomSettings(provider.createCustomSettings(this));
      }
    }
  }

  private void initImportsByDefault() {
    PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(false, "java.awt", false));
    PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(false,"javax.swing", false));
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "javax", true));
    IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "java", true));
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
  }

  private void initTypeToName() {
    initGeneralLocalVariable(PARAMETER_TYPE_TO_NAME);
    initGeneralLocalVariable(LOCAL_VARIABLE_TYPE_TO_NAME);
    PARAMETER_TYPE_TO_NAME.addPair("*Exception", "e");
  }

  private static void initGeneralLocalVariable(@NonNls TypeToNameMap map) {
    map.addPair("int", "i");
    map.addPair("byte", "b");
    map.addPair("char", "c");
    map.addPair("long", "l");
    map.addPair("short", "i");
    map.addPair("boolean", "b");
    map.addPair("double", "v");
    map.addPair("float", "v");
    map.addPair("java.lang.Object", "o");
    map.addPair("java.lang.String", "s");
  }

  public void setParentSettings(CodeStyleSettings parent) {
    myParentSettings = parent;
  }

  public CodeStyleSettings getParentSettings() {
    return myParentSettings;
  }

  private void addCustomSettings(CustomCodeStyleSettings settings) {
    if (settings != null) {
      myCustomSettings.put(settings.getClass(), settings);
    }
  }

  public <T extends CustomCodeStyleSettings> T getCustomSettings(Class<T> aClass) {
    return (T)myCustomSettings.get(aClass);
  }

  public CodeStyleSettings clone() {
    CodeStyleSettings clone = new CodeStyleSettings();
    clone.copyFrom(this);
    return clone;
  }

  private void copyCustomSettingsFrom(CodeStyleSettings from) {
    assert from != this;
    myCustomSettings.clear();
    for (final CustomCodeStyleSettings settings : from.myCustomSettings.values()) {
      addCustomSettings((CustomCodeStyleSettings) settings.clone());
    }

    FIELD_TYPE_TO_NAME.copyFrom(from.FIELD_TYPE_TO_NAME);
    STATIC_FIELD_TYPE_TO_NAME.copyFrom(from.STATIC_FIELD_TYPE_TO_NAME);
    PARAMETER_TYPE_TO_NAME.copyFrom(from.PARAMETER_TYPE_TO_NAME);
    LOCAL_VARIABLE_TYPE_TO_NAME.copyFrom(from.LOCAL_VARIABLE_TYPE_TO_NAME);

    PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(from.PACKAGES_TO_USE_IMPORT_ON_DEMAND);
    IMPORT_LAYOUT_TABLE.copyFrom(from.IMPORT_LAYOUT_TABLE);

    OTHER_INDENT_OPTIONS.copyFrom(from.OTHER_INDENT_OPTIONS);

    myAdditionalIndentOptions.clear();
    for(Map.Entry<FileType, IndentOptions> optionEntry: from.myAdditionalIndentOptions.entrySet()) {
      IndentOptions options = optionEntry.getValue();
      myAdditionalIndentOptions.put(optionEntry.getKey(),(IndentOptions)options.clone());
    }
  }

  public void copyFrom(CodeStyleSettings from) {
    copyPublicFields(from, this);

    copyCustomSettingsFrom(from);
  }

  private static void copyPublicFields(Object from, Object to) {
    assert from != to;
    Field[] fields = to.getClass().getDeclaredFields();
    for (Field field : fields) {
      if (isPublic(field) && !isFinal(field)) {
        try {
          copyFieldValue(from, to, field);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private static void copyFieldValue(final Object from, Object to, final Field field)
    throws IllegalAccessException {
    Class<?> fieldType = field.getType();
    if (fieldType.isPrimitive()) {
      field.set(to, field.get(from));
    }
    else if (fieldType.equals(String.class)) {
      field.set(to, field.get(from));
    }
    else {
      throw new RuntimeException("Field not copied " + field.getName());
    }
  }

  private static boolean isPublic(final Field field) {
    return (field.getModifiers() & Modifier.PUBLIC) != 0;
  }

  private static boolean isFinal(final Field field) {
    return (field.getModifiers() & Modifier.FINAL) != 0;
  }

//----------------- GENERAL --------------------

  public boolean LINE_COMMENT_AT_FIRST_COLUMN = true;
  public boolean BLOCK_COMMENT_AT_FIRST_COLUMN = true;

  public boolean KEEP_LINE_BREAKS = true;

  /**
   * Controls END_OF_LINE_COMMENT's and C_STYLE_COMMENT's
   */
  public boolean KEEP_FIRST_COLUMN_COMMENT = true;
  public boolean INSERT_FIRST_SPACE_IN_LINE = true;

  public boolean USE_SAME_INDENTS = false;

  public static class IndentOptions implements JDOMExternalizable, Cloneable {
    public int INDENT_SIZE = 4;
    public int CONTINUATION_INDENT_SIZE = 8;
    public int TAB_SIZE = 4;
    public boolean USE_TAB_CHARACTER = false;
    public boolean SMART_TABS = false;
    public int LABEL_INDENT_SIZE = 0;
    public boolean LABEL_INDENT_ABSOLUTE = false;
    public boolean USE_RELATIVE_INDENTS = false;

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }

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
  }

  @Deprecated
  public final IndentOptions JAVA_INDENT_OPTIONS = new IndentOptions();
  @Deprecated
  public final IndentOptions JSP_INDENT_OPTIONS = new IndentOptions();
  @Deprecated
  public final IndentOptions XML_INDENT_OPTIONS = new IndentOptions();

  public final IndentOptions OTHER_INDENT_OPTIONS = new IndentOptions();

  private final Map<FileType,IndentOptions> myAdditionalIndentOptions = new LinkedHashMap<FileType, IndentOptions>();

  private static final String ourSystemLineSeparator = SystemProperties.getLineSeparator();

  /**
   * Line separator. It can be null if choosen line separator is "System-dependent"!
   */
  public String LINE_SEPARATOR;

  /**
   * @return line separator. If choosen line separator is "System-dependent" method returns default separator for this OS.
   */
  public String getLineSeparator() {
    return LINE_SEPARATOR != null ? LINE_SEPARATOR : ourSystemLineSeparator;
  }

  /**
   * Keep "if (..) ...;" (also while, for)
   * Does not control "if (..) { .. }"
   */
  public boolean KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;

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

  public int BRACE_STYLE = 1;
  public int CLASS_BRACE_STYLE = 1;
  public int METHOD_BRACE_STYLE = 1;

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

  /**
   * Defines number of white spaces between curly braces in case of {@link #USE_FLYING_GEESE_BRACES 'flying geese'} style usage.
   */
  public int FLYING_GEESE_BRACES_GAP = 1;

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

  public boolean SPECIAL_ELSE_IF_TREATMENT = true;

  /**
   * Indicates if long sequence of chained method calls should be aligned.
   * <p/>
   * E.g. if statement like <code>'foo.bar().bar().bar();'</code> should be reformatted to the one below if,
   * say, last <code>'bar()'</code> call exceeds right margin. The code looks as follows after reformatting
   * if this property is <code>true</code>:
   * <p/>
   * <pre>
   *     foo.bar().bar()
   *        .bar();
   * </pre>
   */
  public boolean ALIGN_MULTILINE_CHAINED_METHODS = false;
  public boolean ALIGN_MULTILINE_PARAMETERS = true;
  public boolean ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
  public boolean ALIGN_MULTILINE_FOR = true;
  public boolean INDENT_WHEN_CASES = true;

  public boolean ALIGN_MULTILINE_BINARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_ASSIGNMENT = false;
  public boolean ALIGN_MULTILINE_TERNARY_OPERATION = false;
  public boolean ALIGN_MULTILINE_THROWS_LIST = false;

  public boolean ALIGN_MULTILINE_EXTENDS_LIST = false;
  public boolean ALIGN_MULTILINE_METHOD_BRACKETS = false;
  public boolean ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
  public boolean ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = false;

//----------------- Group alignments ---------------

  /**
   * Specifies if subsequent fields/variables declarations and initialisations should be aligned in columns like below:
   *     int start = 1;
   *     int end   = 10;
   */
  public boolean ALIGN_GROUP_FIELDS_VARIABLES = false;

  public boolean ALIGN_GROUP_FIELD_DECLARATIONS = false;

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
   * "void f( int param )"
   * or
   * "void f(int param)"
   */
  public boolean SPACE_WITHIN_METHOD_PARENTHESES = false;

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
   * "int X[] { 1, 3, 5 }"
   * or
   * "int X[] {1, 3, 5}"
   */
  public boolean SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = false;

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

  public boolean SPACE_BEFORE_QUEST = true;
  public boolean SPACE_AFTER_QUEST = true;
  public boolean SPACE_BEFORE_COLON = true;
  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_TYPE_PARAMETER_LIST = false;

//----------------- NAMING CONVENTIONS --------------------

  public String FIELD_NAME_PREFIX = "";
  public String STATIC_FIELD_NAME_PREFIX = "";
  public String PARAMETER_NAME_PREFIX = "";
  public String LOCAL_VARIABLE_NAME_PREFIX = "";

  public String FIELD_NAME_SUFFIX = "";
  public String STATIC_FIELD_NAME_SUFFIX = "";
  public String PARAMETER_NAME_SUFFIX = "";
  public String LOCAL_VARIABLE_NAME_SUFFIX = "";

  public boolean PREFER_LONGER_NAMES = true;

  public final TypeToNameMap FIELD_TYPE_TO_NAME = new TypeToNameMap();
  public final TypeToNameMap STATIC_FIELD_TYPE_TO_NAME = new TypeToNameMap();
  @NonNls public final TypeToNameMap PARAMETER_TYPE_TO_NAME = new TypeToNameMap();
  public final TypeToNameMap LOCAL_VARIABLE_TYPE_TO_NAME = new TypeToNameMap();

//----------------- 'final' modifier settings -------
  public boolean GENERATE_FINAL_LOCALS = false;
  public boolean GENERATE_FINAL_PARAMETERS = false;

//----------------- annotations ----------------
  public boolean USE_EXTERNAL_ANNOTATIONS = false;
  public boolean INSERT_OVERRIDE_ANNOTATION = true;

//----------------- IMPORTS --------------------

  public boolean LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
  public boolean USE_FQ_CLASS_NAMES = false;
  public boolean USE_FQ_CLASS_NAMES_IN_JAVADOC = true;
  public boolean USE_SINGLE_CLASS_IMPORTS = true;
  public boolean INSERT_INNER_CLASS_IMPORTS = false;
  public int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  public int NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
  public final PackageEntryTable PACKAGES_TO_USE_IMPORT_ON_DEMAND = new PackageEntryTable();
  public final PackageEntryTable IMPORT_LAYOUT_TABLE = new PackageEntryTable();

//----------------- ORDER OF MEMBERS ------------------

  public int STATIC_FIELDS_ORDER_WEIGHT = 1;
  public int FIELDS_ORDER_WEIGHT = 2;
  public int CONSTRUCTORS_ORDER_WEIGHT = 3;
  public int STATIC_METHODS_ORDER_WEIGHT = 4;
  public int METHODS_ORDER_WEIGHT = 5;
  public int STATIC_INNER_CLASSES_ORDER_WEIGHT = 6;
  public int INNER_CLASSES_ORDER_WEIGHT = 7;

//----------------- WRAPPING ---------------------------
  public int RIGHT_MARGIN = 120;
  public boolean WRAP_WHEN_TYPING_REACHES_RIGHT_MAGIN = false;

  public static final int DO_NOT_WRAP = 0x00;
  public static final int WRAP_AS_NEEDED = 0x01;
  public static final int WRAP_ALWAYS = 0x02;
  public static final int WRAP_ON_EVERY_ITEM = 0x04;

  public int CALL_PARAMETERS_WRAP = DO_NOT_WRAP;
  public boolean PREFER_PARAMETERS_WRAP = false;
  public boolean CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
  public boolean CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  public int METHOD_PARAMETERS_WRAP = DO_NOT_WRAP;
  public boolean METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
  public boolean METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;

  public int EXTENDS_LIST_WRAP = DO_NOT_WRAP;
  public int THROWS_LIST_WRAP = DO_NOT_WRAP;

  public int EXTENDS_KEYWORD_WRAP = DO_NOT_WRAP;
  public int THROWS_KEYWORD_WRAP = DO_NOT_WRAP;

  public int METHOD_CALL_CHAIN_WRAP = DO_NOT_WRAP;

  public boolean PARENTHESES_EXPRESSION_LPAREN_WRAP = false;
  public boolean PARENTHESES_EXPRESSION_RPAREN_WRAP = false;

  public int BINARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;

  public int TERNARY_OPERATION_WRAP = DO_NOT_WRAP;
  public boolean TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = false;

  public boolean MODIFIER_LIST_WRAP = false;

  public boolean KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
  public boolean KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;

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

//----------------- EJB NAMING CONVENTIONS -------------
/**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_EB_PREFIX = "";         //EntityBean EJB Class name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_EB_SUFFIX = "Bean";     //EntityBean EJB Class name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_HI_PREFIX = "";         //EntityBean Home interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_HI_SUFFIX = "Home";     //EntityBean Home interface name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_RI_PREFIX = "";         //EntityBean Remote interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_RI_SUFFIX = "";         //EntityBean Remote interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_LHI_PREFIX = "Local";   //EntityBean local Home interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_LHI_SUFFIX = "Home";    //EntityBean local Home interface name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_LI_PREFIX = "Local";    //EntityBean local interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_LI_SUFFIX = "";         //EntityBean local interface name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_DD_PREFIX = "";         //EntityBean deployment descriptor name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_DD_SUFFIX = "EJB";      //EntityBean deployment descriptor name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_VO_PREFIX = "";
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_VO_SUFFIX = "VO";
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String ENTITY_PK_CLASS = "java.lang.String";

  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_EB_PREFIX = "";        //SessionBean EJB Class name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_EB_SUFFIX = "Bean";    //SessionBean EJB Class name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_HI_PREFIX = "";        //SessionBean Home interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_HI_SUFFIX = "Home";    //SessionBean Home interface name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_RI_PREFIX = "";        //SessionBean Remote interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_RI_SUFFIX = "";        //SessionBean Remote interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_LHI_PREFIX = "Local";  //SessionBean local Home interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_LHI_SUFFIX = "Home";   //SessionBean local Home interface name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_LI_PREFIX = "Local";   //SessionBean local interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_LI_SUFFIX = "";        //SessionBean local interface name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_SI_PREFIX = "";   //SessionBean service endpoint interface name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_SI_SUFFIX = "Service";        //SessionBean service endpoint interface name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_DD_PREFIX = "";        //SessionBean deployment descriptor name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SESSION_DD_SUFFIX = "EJB";     //SessionBean deployment descriptor name suffix

  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String MESSAGE_EB_PREFIX = "";        //MessageBean EJB Class name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String MESSAGE_EB_SUFFIX = "Bean";    //MessageBean EJB Class name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String MESSAGE_DD_PREFIX = "";        //MessageBean deployment descriptor name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String MESSAGE_DD_SUFFIX = "EJB";     //MessageBean deployment descriptor name suffix
//----------------- Servlet NAMING CONVENTIONS -------------
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SERVLET_CLASS_PREFIX = "";           //SERVLET Class name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SERVLET_CLASS_SUFFIX = "";           //SERVLET Class name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SERVLET_DD_PREFIX = "";              //SERVLET deployment descriptor name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String SERVLET_DD_SUFFIX = "";              //SERVLET deployment descriptor name suffix
//----------------- Web Filter NAMING CONVENTIONS -------------
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String FILTER_CLASS_PREFIX = "";          //Filter Class name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String FILTER_CLASS_SUFFIX = "";          //Filter Class name suffix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String FILTER_DD_PREFIX = "";             //Filter deployment descriptor name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String FILTER_DD_SUFFIX = "";             //Filter deployment descriptor name suffix

  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String LISTENER_CLASS_PREFIX = "";          //Listener Class name prefix
  /**
 * @deprecated use com.intellij.javaee.JavaeeCodeStyleSettings class
 */
  @NonNls public String LISTENER_CLASS_SUFFIX = "";          //Listener Class name suffix

//------------------------------------------------------------------------

  // ---------------------------------- Javadoc formatting options -------------------------
  public boolean ENABLE_JAVADOC_FORMATTING = true;

  /**
   * Align parameter comments to longest parameter name
   */
  public boolean JD_ALIGN_PARAM_COMMENTS = true;
  public int JD_MIN_PARM_NAME_LENGTH = 0;
  public int JD_MAX_PARM_NAME_LENGTH = 30;

  /**
   * Align exception comments to longest exception name
   */
  public boolean JD_ALIGN_EXCEPTION_COMMENTS = true;
  public int JD_MIN_EXCEPTION_NAME_LENGTH = 0;
  public int JD_MAX_EXCEPTION_NAME_LENGTH = 30;

  public boolean JD_ADD_BLANK_AFTER_PARM_COMMENTS = false;
  public boolean JD_ADD_BLANK_AFTER_RETURN = false;
  public boolean JD_ADD_BLANK_AFTER_DESCRIPTION = true;
  public boolean JD_P_AT_EMPTY_LINES = true;

  public boolean JD_KEEP_INVALID_TAGS = true;
  public boolean JD_KEEP_EMPTY_LINES = true;
  public boolean JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = false;

  public boolean JD_USE_THROWS_NOT_EXCEPTION = true;
  public boolean JD_KEEP_EMPTY_PARAMETER = true;
  public boolean JD_KEEP_EMPTY_EXCEPTION = true;
  public boolean JD_KEEP_EMPTY_RETURN = true;


  public boolean JD_LEADING_ASTERISKS_ARE_ENABLED = true;

  // ---------------------------------------------------------------------------------------


  // ---------------------------------- XML formatting options -------------------------

  public final static int WS_AROUND_CDATA_PRESERVE = 0;
  public final static int WS_AROUND_CDATA_NONE = 1;
  public final static int WS_AROUND_CDATA_NEW_LINES = 2;

  public boolean XML_KEEP_WHITESPACES = false;
  public int XML_ATTRIBUTE_WRAP = WRAP_AS_NEEDED;
  public int XML_TEXT_WRAP = WRAP_AS_NEEDED;

  public boolean XML_KEEP_LINE_BREAKS = true;
  public boolean XML_KEEP_LINE_BREAKS_IN_TEXT = true;
  public int XML_KEEP_BLANK_LINES = 2;

  public boolean XML_ALIGN_ATTRIBUTES = true;
  public boolean XML_ALIGN_TEXT = false;

  public boolean XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = false;
  public boolean XML_SPACE_AFTER_TAG_NAME = false;
  public boolean XML_SPACE_INSIDE_EMPTY_TAG = false;

  public boolean XML_KEEP_WHITE_SPACES_INSIDE_CDATA = false;
  public int XML_WHITE_SPACE_AROUND_CDATA = WS_AROUND_CDATA_PRESERVE;

  // ---------------------------------------------------------------------------------------

  // ---------------------------------- HTML formatting options -------------------------
  public boolean HTML_KEEP_WHITESPACES = false;
  public int HTML_ATTRIBUTE_WRAP = WRAP_AS_NEEDED;
  public int HTML_TEXT_WRAP = WRAP_AS_NEEDED;

  public boolean HTML_KEEP_LINE_BREAKS = true;
  public boolean HTML_KEEP_LINE_BREAKS_IN_TEXT = true;
  public int HTML_KEEP_BLANK_LINES = 2;

  public boolean HTML_ALIGN_ATTRIBUTES = true;
  public boolean HTML_ALIGN_TEXT = false;

  public boolean HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE = false;
  public boolean HTML_SPACE_AFTER_TAG_NAME = false;
  public boolean HTML_SPACE_INSIDE_EMPTY_TAG = false;

  @NonNls public String HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = "body,div,p,form,h1,h2,h3";
  @NonNls public String HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = "br";
  @NonNls public String HTML_DO_NOT_INDENT_CHILDREN_OF = "html,body,thead,tbody,tfoot";
  public int HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES = 200;

  @NonNls public String HTML_KEEP_WHITESPACES_INSIDE = "span,pre";
  @NonNls public String HTML_INLINE_ELEMENTS =
    "a,abbr,acronym,b,basefont,bdo,big,br,cite,cite,code,dfn,em,font,i,img,input,kbd,label,q,s,samp,select,span,strike,strong,sub,sup,textarea,tt,u,var";
  @NonNls public String HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT = "title,h1,h2,h3,h4,h5,h6,p";

  // ---------------------------------------------------------------------------------------


  // true if <%page import="x.y.z, x.y.t"%>
  // false if <%page import="x.y.z"%>
  //          <%page import="x.y.t"%>
  public boolean JSP_PREFER_COMMA_SEPARATED_IMPORT_LIST = false;

  //----------------------------------------------------------------------------------------

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
  //----------------------------------------------------------------------------------------

  private CodeStyleSettings myParentSettings;
  private boolean myLoadedAdditionalIndentOptions;

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    if (LAYOUT_STATIC_IMPORTS_SEPARATELY) {
      // add <all other static imports> entry if there is none
      boolean found = false;
      for (PackageEntry entry : IMPORT_LAYOUT_TABLE.getEntries()) {
        if (entry == PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY) {
          found = true;
          break;
        }
      }
      if (!found) {
        PackageEntry last = IMPORT_LAYOUT_TABLE.getEntryCount() == 0 ? null : IMPORT_LAYOUT_TABLE.getEntryAt(IMPORT_LAYOUT_TABLE.getEntryCount() - 1);
        if (last != PackageEntry.BLANK_LINE_ENTRY) {
          IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
        }
        IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      }
    }
    importOldIndentOptions(element);
    for (final CustomCodeStyleSettings settings : myCustomSettings.values()) {
      settings.readExternal(element);
    }

    final List list = element.getChildren(ADDITIONAL_INDENT_OPTIONS);
    if (list != null) {
      for(Object o:list) {
        if (o instanceof Element) {
          final Element additionalIndentElement = (Element)o;
          final String fileTypeId = additionalIndentElement.getAttributeValue(FILETYPE);

          if (fileTypeId != null && fileTypeId.length() > 0) {
            FileType target = FileTypeManager.getInstance().getFileTypeByExtension(fileTypeId);
            if (FileTypes.UNKNOWN == target || FileTypes.PLAIN_TEXT == target || target.getDefaultExtension().length() == 0) {
              target = new TempFileType(fileTypeId);
            }

            final IndentOptions options = new IndentOptions();
            options.readExternal(additionalIndentElement);
            registerAdditionalIndentOptions(target, options);
          }
        }
      }
    }

    copyOldIndentOptions("java", JAVA_INDENT_OPTIONS);
    copyOldIndentOptions("jsp", JSP_INDENT_OPTIONS);
    copyOldIndentOptions("xml", XML_INDENT_OPTIONS);
  }

  private void copyOldIndentOptions(@NonNls final String extension, final IndentOptions options) {
    final FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension);
    if (fileType != FileTypes.UNKNOWN && fileType != FileTypes.PLAIN_TEXT && !myAdditionalIndentOptions.containsKey(fileType) && fileType.getDefaultExtension().length() != 0) {
      registerAdditionalIndentOptions(fileType, options);
    }
  }

  private void importOldIndentOptions(@NonNls Element element) {
    final List options = element.getChildren("option");
    for (Object option1 : options) {
      @NonNls Element option = (Element)option1;
      @NonNls final String name = option.getAttributeValue("name");
      if ("TAB_SIZE".equals(name)) {
        final int value = Integer.valueOf(option.getAttributeValue("value")).intValue();
        JAVA_INDENT_OPTIONS.TAB_SIZE = value;
        JSP_INDENT_OPTIONS.TAB_SIZE = value;
        XML_INDENT_OPTIONS.TAB_SIZE = value;
        OTHER_INDENT_OPTIONS.TAB_SIZE = value;
      }
      else if ("INDENT_SIZE".equals(name)) {
        final int value = Integer.valueOf(option.getAttributeValue("value")).intValue();
        JAVA_INDENT_OPTIONS.INDENT_SIZE = value;
        JSP_INDENT_OPTIONS.INDENT_SIZE = value;
        XML_INDENT_OPTIONS.INDENT_SIZE = value;
        OTHER_INDENT_OPTIONS.INDENT_SIZE = value;
      }
      else if ("CONTINUATION_INDENT_SIZE".equals(name)) {
        final int value = Integer.valueOf(option.getAttributeValue("value")).intValue();
        JAVA_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE = value;
        JSP_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE = value;
        XML_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE = value;
        OTHER_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE = value;
      }
      else if ("USE_TAB_CHARACTER".equals(name)) {
        final boolean value = Boolean.valueOf(option.getAttributeValue("value")).booleanValue();
        JAVA_INDENT_OPTIONS.USE_TAB_CHARACTER = value;
        JSP_INDENT_OPTIONS.USE_TAB_CHARACTER = value;
        XML_INDENT_OPTIONS.USE_TAB_CHARACTER = value;
        OTHER_INDENT_OPTIONS.USE_TAB_CHARACTER = value;
      }
      else if ("SMART_TABS".equals(name)) {
        final boolean value = Boolean.valueOf(option.getAttributeValue("value")).booleanValue();
        JAVA_INDENT_OPTIONS.SMART_TABS = value;
        JSP_INDENT_OPTIONS.SMART_TABS = value;
        XML_INDENT_OPTIONS.SMART_TABS = value;
        OTHER_INDENT_OPTIONS.SMART_TABS = value;
      } else if ("SPACE_AFTER_UNARY_OPERATOR".equals(name)) {
        final boolean value = Boolean.valueOf(option.getAttributeValue("value")).booleanValue();
        SPACE_AROUND_UNARY_OPERATOR = value;
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final CodeStyleSettings parentSettings = new CodeStyleSettings();
    DefaultJDOMExternalizer.writeExternal(this, element, new DifferenceFilter<CodeStyleSettings>(this, parentSettings));
    List<CustomCodeStyleSettings> customSettings = new ArrayList<CustomCodeStyleSettings>(myCustomSettings.values());
    
    Collections.sort(customSettings, new Comparator<CustomCodeStyleSettings>(){
      public int compare(final CustomCodeStyleSettings o1, final CustomCodeStyleSettings o2) {
        return o1.getTagName().compareTo(o2.getTagName());
      }
    });

    for (final CustomCodeStyleSettings settings : customSettings) {
      final CustomCodeStyleSettings parentCustomSettings = parentSettings.getCustomSettings(settings.getClass());
      assert parentCustomSettings != null;
      settings.writeExternal(element, parentCustomSettings);
    }

    final FileType[] fileTypes = myAdditionalIndentOptions.keySet().toArray(new FileType[myAdditionalIndentOptions.keySet().size()]);
    Arrays.sort(fileTypes, new Comparator<FileType>() {
      public int compare(final FileType o1, final FileType o2) {
        return o1.getDefaultExtension().compareTo(o2.getDefaultExtension());
      }
    });

    for (FileType fileType : fileTypes) {
      final IndentOptions indentOptions = myAdditionalIndentOptions.get(fileType);
      Element additionalIndentOptions = new Element(ADDITIONAL_INDENT_OPTIONS);
      indentOptions.writeExternal(additionalIndentOptions);
      additionalIndentOptions.setAttribute(FILETYPE,fileType.getDefaultExtension());
      element.addContent(additionalIndentOptions);
    }
  }

  public IndentOptions getIndentOptions(FileType fileType) {
    if (USE_SAME_INDENTS || fileType == null) return OTHER_INDENT_OPTIONS;

    if (!myLoadedAdditionalIndentOptions) {
      loadAdditionalIndentOptions();
    }
    final IndentOptions indentOptions = myAdditionalIndentOptions.get(fileType);
    if (indentOptions != null) return indentOptions;

    return OTHER_INDENT_OPTIONS;
  }

  public boolean isSmartTabs(FileType fileType) {
    return getIndentOptions(fileType).SMART_TABS;
  }

  public int getIndentSize(FileType fileType) {
    return getIndentOptions(fileType).INDENT_SIZE;
  }

  public int getContinuationIndentSize(FileType fileType) {
    return getIndentOptions(fileType).CONTINUATION_INDENT_SIZE;
  }

  public int getLabelIndentSize(FileType fileType) {
    return getIndentOptions(fileType).LABEL_INDENT_SIZE;
  }

  public boolean getLabelIndentAbsolute(FileType fileType) {
    return getIndentOptions(fileType).LABEL_INDENT_ABSOLUTE;
  }

  public int getTabSize(FileType fileType) {
    return getIndentOptions(fileType).TAB_SIZE;
  }

  public boolean useTabCharacter(FileType fileType) {
    return getIndentOptions(fileType).USE_TAB_CHARACTER;
  }

  public static class TypeToNameMap implements JDOMExternalizable {
    private final List<String> myPatterns = new ArrayList<String>();
    private final List<String> myNames = new ArrayList<String>();

    public void addPair(String pattern, String name) {
      myPatterns.add(pattern);
      myNames.add(name);
    }

    public String nameByType(String type) {
      for (int i = 0; i < myPatterns.size(); i++) {
        String pattern = myPatterns.get(i);
        if (StringUtil.startsWithChar(pattern, '*')) {
          if (type.endsWith(pattern.substring(1))) {
            return myNames.get(i);
          }
        }
        else {
          if (type.equals(pattern)) {
            return myNames.get(i);
          }
        }
      }
      return null;
    }

    public void readExternal(@NonNls Element element) throws InvalidDataException {
      myPatterns.clear();
      myNames.clear();
      for (final Object o : element.getChildren("pair")) {
        @NonNls Element e = (Element)o;

        String pattern = e.getAttributeValue("type");
        String name = e.getAttributeValue("name");
        if (pattern == null || name == null) {
          throw new InvalidDataException();
        }
        myPatterns.add(pattern);
        myNames.add(name);

      }
    }

    public void writeExternal(Element parentNode) throws WriteExternalException {
      for (int i = 0; i < myPatterns.size(); i++) {
        String pattern = myPatterns.get(i);
        String name = myNames.get(i);
        @NonNls Element element = new Element("pair");
        parentNode.addContent(element);
        element.setAttribute("type", pattern);
        element.setAttribute("name", name);
      }
    }

    public void copyFrom(TypeToNameMap from) {
      assert from != this;
      myPatterns.clear();
      myPatterns.addAll(from.myPatterns);
      myNames.clear();
      myNames.addAll(from.myNames);
    }

    public boolean equals(Object other) {
      if (other instanceof TypeToNameMap) {
        TypeToNameMap otherMap = (TypeToNameMap)other;
        if (myPatterns.size() != otherMap.myPatterns.size()) {
          return false;
        }
        if (myNames.size() != otherMap.myNames.size()) {
          return false;
        }
        for (int i = 0; i < myPatterns.size(); i++) {
          String s1 = myPatterns.get(i);
          String s2 = otherMap.myPatterns.get(i);
          if (!Comparing.equal(s1, s2)) {
            return false;
          }
        }
        for (int i = 0; i < myNames.size(); i++) {
          String s1 = myNames.get(i);
          String s2 = otherMap.myNames.get(i);
          if (!Comparing.equal(s1, s2)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }

    public int hashCode() {
      int code = 0;
      for (String myPattern : myPatterns) {
        code += myPattern.hashCode();
      }
      for (String myName : myNames) {
        code += myName.hashCode();
      }
      return code;
    }

  }

  private void registerAdditionalIndentOptions(FileType fileType, IndentOptions options) {
    boolean exist = false;
    for (final FileType existing : myAdditionalIndentOptions.keySet()) {
      if (existing.getDefaultExtension() == fileType.getDefaultExtension()) {
        exist = true;
        break;
      }
    }

    if (!exist) {
      myAdditionalIndentOptions.put(fileType, options);
    }
  }

  public IndentOptions getAdditionalIndentOptions(FileType fileType) {
    if (!myLoadedAdditionalIndentOptions) {
      loadAdditionalIndentOptions();
    }
    return myAdditionalIndentOptions.get(fileType);
  }

  private void loadAdditionalIndentOptions() {
    myLoadedAdditionalIndentOptions = true;
    final FileTypeIndentOptionsProvider[] providers = Extensions.getExtensions(FileTypeIndentOptionsProvider.EP_NAME);
    for (final FileTypeIndentOptionsProvider provider : providers) {
      if (!myAdditionalIndentOptions.containsKey(provider.getFileType())) {
        registerAdditionalIndentOptions(provider.getFileType(), provider.createIndentOptions());
      }
    }
  }

  @TestOnly
  public void clearCodeStyleSettings() throws Exception {
    CodeStyleSettings cleanSettings = new CodeStyleSettings();
    copyFrom(cleanSettings);
    myAdditionalIndentOptions.clear(); //hack
    myLoadedAdditionalIndentOptions = false;
  }

  private static class TempFileType implements FileType {
    private final String myExtension;

    private TempFileType(@NotNull final String extension) {
      myExtension = extension;
    }

    @NotNull
    public String getName() {
      return "TempFileType";
    }

    @NotNull
    public String getDescription() {
      return "TempFileType";
    }

    @NotNull
    public String getDefaultExtension() {
      return myExtension;
    }

    public Icon getIcon() {
      return null;
    }

    public boolean isBinary() {
      return false;
    }

    public boolean isReadOnly() {
      return false;
    }

    public String getCharset(@NotNull VirtualFile file, byte[] content) {
      return null;
    }
  }
}
