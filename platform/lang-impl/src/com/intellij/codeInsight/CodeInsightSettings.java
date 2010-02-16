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

package com.intellij.codeInsight;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;


@State(
  name = "CodeInsightSettings",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/editor.codeinsight.xml"
    )}
)
public class CodeInsightSettings implements PersistentStateComponent<Element>, Cloneable, ExportableComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.CodeInsightSettings");

  @NonNls private static final String EXCLUDED_PACKAGE = "EXCLUDED_PACKAGE";
  @NonNls private static final String ATTRIBUTE_NAME = "NAME";
  @NonNls public static final String EXTERNAL_FILE_NAME = "editor.codeinsight";

  public static CodeInsightSettings getInstance() {
    return ServiceManager.getService(CodeInsightSettings.class);
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(EXTERNAL_FILE_NAME)};
  }

  @NotNull
  public String getPresentableName() {
    return CodeInsightBundle.message("codeinsight.settings");
  }

  @Nullable
  public CodeInsightSettings clone() {
    try {
      return (CodeInsightSettings)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public boolean AUTO_POPUP_MEMBER_LOOKUP = true;
  public int MEMBER_LOOKUP_DELAY = 1000;
  public boolean AUTO_POPUP_XML_LOOKUP = true;
  public int XML_LOOKUP_DELAY = 0;
  public boolean AUTO_POPUP_PARAMETER_INFO = true;
  public int PARAMETER_INFO_DELAY = 1000;
  public boolean AUTO_POPUP_JAVADOC_INFO = false;
  public int JAVADOC_INFO_DELAY = 1000;
  public boolean AUTO_POPUP_JAVADOC_LOOKUP = true;
  public int JAVADOC_LOOKUP_DELAY = 1000;

  public int COMPLETION_CASE_SENSITIVE = FIRST_LETTER; // ALL, NONE or FIRST_LETTER
  public static final int ALL = 1;
  public static final int NONE = 2;
  public static final int FIRST_LETTER = 3;

  public boolean AUTOCOMPLETE_ON_CODE_COMPLETION = true;
  public boolean AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = true;
  public boolean AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = false;
  public boolean AUTOCOMPLETE_COMMON_PREFIX = true;
  public boolean SHOW_STATIC_AFTER_INSTANCE = false;

  public boolean SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO = false;

  public boolean SMART_INDENT_ON_ENTER = true;
  public boolean INSERT_BRACE_ON_ENTER = true;
  public boolean INSERT_SCRIPTLET_END_ON_ENTER = true;
  public boolean JAVADOC_STUB_ON_ENTER = true;

  public boolean SMART_END_ACTION = true;

  public boolean AUTOINSERT_PAIR_BRACKET = true;
  public boolean AUTOINSERT_PAIR_QUOTE = true;

  public int REFORMAT_ON_PASTE = INDENT_BLOCK;
  public static final int NO_REFORMAT = 1;
  public static final int INDENT_BLOCK = 2;
  public static final int INDENT_EACH_LINE = 3;
  public static final int REFORMAT_BLOCK = 4;

  public int ADD_IMPORTS_ON_PASTE = ASK; // YES, NO or ASK
  public static final int YES = 1;
  public static final int NO = 2;
  public static final int ASK = 3;

  public boolean HIGHLIGHT_BRACES = true;
  public boolean HIGHLIGHT_SCOPE = false;

  public boolean USE_INSTANCEOF_ON_EQUALS_PARAMETER = false;

  public boolean HIGHLIGHT_IDENTIFIER_UNDER_CARET = false;

  public boolean OPTIMIZE_IMPORTS_ON_THE_FLY = false;
  public boolean ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;

  @Property(surroundWithTag = false) @AbstractCollection(
    surroundWithTag = false,
    elementTag = "EXCLUDED_PACKAGE",
    elementValueAttribute = "NAME")
  public String[] EXCLUDED_PACKAGES = ArrayUtil.EMPTY_STRING_ARRAY;

  public void loadState(final Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
    }
    catch (InvalidDataException e) {
      LOG.info(e);
    }
    final List list = state.getChildren(EXCLUDED_PACKAGE);
    EXCLUDED_PACKAGES = new String[list.size()];
    for(int i=0; i<list.size(); i++) {
      EXCLUDED_PACKAGES [i] = ((Element) list.get(i)).getAttributeValue(ATTRIBUTE_NAME);
    }
  }

  public Element getState() {
    Element element = new Element("state");
    writeExternal(element);
    return element;
  }

  public void writeExternal(final Element element) {
    try {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
    catch (WriteExternalException e) {
      LOG.info(e);
    }
    for(String s: EXCLUDED_PACKAGES) {
      final Element child = new Element(EXCLUDED_PACKAGE);
      child.setAttribute(ATTRIBUTE_NAME, s);
      element.addContent(child);
    }
  }
}
