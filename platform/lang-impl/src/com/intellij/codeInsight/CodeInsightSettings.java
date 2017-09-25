/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.codeInsight.editorActions.SmartBackspaceMode;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

@State(
  name = "CodeInsightSettings",
  storages = @Storage("editor.codeinsight.xml")
)
public class CodeInsightSettings implements PersistentStateComponent<Element>, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.CodeInsightSettings");

  public static CodeInsightSettings getInstance() {
    return ServiceManager.getService(CodeInsightSettings.class);
  }

  public CodeInsightSettings() {
    Application application = ApplicationManager.getApplication();
    if (Registry.is("java.completion.argument.hints") ||
        (application != null && application.isInternal() && !application.isUnitTestMode()) && 
        Registry.is("java.completion.argument.hints.internal")) {
      SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = true;
      Registry.get("java.completion.argument.hints").setValue(false);
      Registry.get("java.completion.argument.hints.internal").setValue(false);
    }
  }

  @Override
  @Nullable
  public CodeInsightSettings clone() {
    try {
      return (CodeInsightSettings)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public boolean SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
  public boolean AUTO_POPUP_PARAMETER_INFO = true;
  public int PARAMETER_INFO_DELAY = 1000;
  public boolean AUTO_POPUP_JAVADOC_INFO;
  public int JAVADOC_INFO_DELAY = 1000;
  public boolean AUTO_POPUP_COMPLETION_LOOKUP = true;

  @MagicConstant(intValues = {ALL, NONE, FIRST_LETTER})
  public int COMPLETION_CASE_SENSITIVE = FIRST_LETTER;
  public static final int ALL = 1;
  public static final int NONE = 2;
  public static final int FIRST_LETTER = 3;

  public boolean SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS;
  public boolean AUTOCOMPLETE_ON_CODE_COMPLETION = true;
  public boolean AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = true;

  public boolean AUTOCOMPLETE_COMMON_PREFIX = true;

  public boolean SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO;

  public boolean SHOW_SOURCE_INFERRED_ANNOTATIONS = true;

  @OptionTag("SMART_BACKSPACE") // explicit name makes it work also for obfuscated private field's name
  private int SMART_BACKSPACE = SmartBackspaceMode.AUTOINDENT.ordinal();
  
  @Transient
  @NotNull
  public SmartBackspaceMode getBackspaceMode() {
    SmartBackspaceMode[] values = SmartBackspaceMode.values();
    return SMART_BACKSPACE >= 0 && SMART_BACKSPACE < values.length ? values[SMART_BACKSPACE] : SmartBackspaceMode.OFF; 
  }

  @Transient
  public void setBackspaceMode(@NotNull SmartBackspaceMode mode) {
    SMART_BACKSPACE = mode.ordinal();
  }

  public boolean SMART_INDENT_ON_ENTER = true;
  public boolean INSERT_BRACE_ON_ENTER = true;
  public boolean INSERT_SCRIPTLET_END_ON_ENTER = true;
  public boolean JAVADOC_STUB_ON_ENTER = true;
  public boolean SMART_END_ACTION = true;
  public boolean JAVADOC_GENERATE_CLOSING_TAG = true;

  public boolean SURROUND_SELECTION_ON_QUOTE_TYPED;

  public boolean AUTOINSERT_PAIR_BRACKET = true;
  public boolean AUTOINSERT_PAIR_QUOTE = true;
  public boolean REFORMAT_BLOCK_ON_RBRACE = true;

  @MagicConstant(intValues = {NO_REFORMAT, INDENT_BLOCK, INDENT_EACH_LINE, REFORMAT_BLOCK})
  public int REFORMAT_ON_PASTE = INDENT_EACH_LINE;
  public static final int NO_REFORMAT = 1;
  public static final int INDENT_BLOCK = 2;
  public static final int INDENT_EACH_LINE = 3;
  public static final int REFORMAT_BLOCK = 4;

  public boolean INDENT_TO_CARET_ON_PASTE;

  @MagicConstant(intValues = {YES, NO, ASK})
  public int ADD_IMPORTS_ON_PASTE = ASK;
  public static final int YES = 1;
  public static final int NO = 2;
  public static final int ASK = 3;

  public boolean HIGHLIGHT_BRACES = true;
  public boolean HIGHLIGHT_SCOPE;

  public boolean USE_INSTANCEOF_ON_EQUALS_PARAMETER;
  public boolean USE_ACCESSORS_IN_EQUALS_HASHCODE;

  public boolean HIGHLIGHT_IDENTIFIER_UNDER_CARET = true;

  /**
   * @deprecated use {@link CodeInsightWorkspaceSettings#optimizeImportsOnTheFly}
   */
  public boolean OPTIMIZE_IMPORTS_ON_THE_FLY;

  public boolean ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
  public boolean ADD_MEMBER_IMPORTS_ON_THE_FLY = true;
  public boolean JSP_ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;

  /**
   * Names of classes and packages excluded from (Java) auto-import and completion. These are only IDE-specific settings
   * and don't take project-specific settings into account.
   * So please don't reference this field directly, use JavaProjectCodeInsightSettings instead.
   */
  @Property(surroundWithTag = false)
  @AbstractCollection(
    surroundWithTag = false,
    elementTag = "EXCLUDED_PACKAGE",
    elementValueAttribute = "NAME")
  public String[] EXCLUDED_PACKAGES = ArrayUtil.EMPTY_STRING_ARRAY;

  @Override
  public void loadState(final Element state) {
    // 'Write' save only diff from default. Before load do reset to default values.
    setDefaults();

    try {
      XmlSerializer.deserializeInto(this, state);
    }
    catch (XmlSerializationException e) {
      LOG.info(e);
    }
  }

  private void setDefaults() {
    try {
      ReflectionUtil.copyFields(CodeInsightSettings.class.getDeclaredFields(), new CodeInsightSettings(), this,
                                new DifferenceFilter<Object>(null, null) {
                                  @Override
                                  public boolean isAccept(@NotNull Field field) {
                                    return !field.getName().equals("EXCLUDED_PACKAGES");
                                  }
                                });
    }
    catch (Throwable e) {
      LOG.info(e);
    }

    EXCLUDED_PACKAGES = ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    writeExternal(element);
    return element;
  }

  public void writeExternal(final Element element) {
    try {
      XmlSerializer.serializeInto(this, element, new SkipDefaultValuesSerializationFilters());
    }
    catch (XmlSerializationException e) {
      LOG.info(e);
    }
  }
}
