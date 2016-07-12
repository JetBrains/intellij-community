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

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SchemeElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TemplateImpl extends Template implements SchemeElement {
  private String myKey;
  @NotNull private String myString;
  private String myDescription;
  private String myGroupName;
  private char myShortcutChar = TemplateSettings.DEFAULT_CHAR;
  private final List<Variable> myVariables = new SmartList<>();
  private List<Segment> mySegments = null;
  private String myTemplateText = null;
  private String myId;

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TemplateImpl)) return false;

    final TemplateImpl template = (TemplateImpl) o;
    if (myId != null && template.myId != null && myId.equals(template.myId)) return true;

    if (isToReformat != template.isToReformat) return false;
    if (isToShortenLongNames != template.isToShortenLongNames) return false;
    if (myShortcutChar != template.myShortcutChar) return false;
    if (myDescription != null ? !myDescription.equals(template.myDescription) : template.myDescription != null) return false;
    if (myGroupName != null ? !myGroupName.equals(template.myGroupName) : template.myGroupName != null) return false;
    if (myKey != null ? !myKey.equals(template.myKey) : template.myKey != null) return false;
    if (!myString.equals(template.myString)) return false;
    if (myTemplateText != null ? !myTemplateText.equals(template.myTemplateText) : template.myTemplateText != null) return false;

    if (!new HashSet<Variable>(myVariables).equals(new HashSet<Variable>(template.myVariables))) return false;
    if (isDeactivated != template.isDeactivated) return false;

    return true;
  }

  public int hashCode() {
    if (myId != null) {
      return myId.hashCode();
    }
    int result;
    result = myKey.hashCode();
    result = 29 * result + myString.hashCode();
    result = 29 * result + myGroupName.hashCode();
    return result;
  }

  private boolean isToReformat = false;
  private boolean isToShortenLongNames = true;
  private boolean toParseSegments = true;
  private TemplateContext myTemplateContext = new TemplateContext();

  @NonNls public static final String END = "END";
  @NonNls public static final String SELECTION = "SELECTION";
  @NonNls public static final String SELECTION_START = "SELECTION_START";
  @NonNls public static final String SELECTION_END = "SELECTION_END";
  @NonNls public static final String ARG = "ARG";

  public static final Set<String> INTERNAL_VARS_SET = new HashSet<String>(Arrays.asList(
      END, SELECTION, SELECTION_START, SELECTION_END));

  private boolean isDeactivated = false;

  public boolean isInline() {
    return myIsInline;
  }

  private boolean isToIndent = true;


  @Override
  public void setInline(boolean isInline) {
    myIsInline = isInline;
  }

  private boolean myIsInline = false;



  public TemplateImpl(@NotNull String key, @NotNull String group) {
    this(key, null, group);
    toParseSegments = false;
    myTemplateText = "";
    mySegments = new SmartList<>();
  }

  public TemplateImpl(@NotNull String key, String string, @NotNull String group) {
    myKey = key;
    myString = StringUtil.convertLineSeparators(StringUtil.notNullize(string));
    myGroupName = group;
  }


  @Override
  public void addTextSegment(@NotNull String text) {
    text = StringUtil.convertLineSeparators(text);
    myTemplateText += text;
  }

  @Override
  public void addVariableSegment(@NotNull String name) {
    mySegments.add(new Segment(name, myTemplateText.length()));
  }

  @Override
  public Variable addVariable(Expression expression, boolean isAlwaysStopAt) {
    return addVariable("__Variable" + myVariables.size(), expression, isAlwaysStopAt);
  }

  @Override
  public Variable addVariable(@NotNull String name,
                              Expression expression,
                              Expression defaultValueExpression,
                              boolean isAlwaysStopAt,
                              boolean skipOnStart) {
    if (mySegments != null) {
      Segment segment = new Segment(name, myTemplateText.length());
      mySegments.add(segment);
    }
    Variable variable = new Variable(name, expression, defaultValueExpression, isAlwaysStopAt, skipOnStart);
    myVariables.add(variable);
    return variable;
  }

  @Override
  public void addEndVariable() {
    Segment segment = new Segment(END, myTemplateText.length());
    mySegments.add(segment);
  }

  @Override
  public void addSelectionStartVariable() {
    Segment segment = new Segment(SELECTION_START, myTemplateText.length());
    mySegments.add(segment);
  }

  @Override
  public void addSelectionEndVariable() {
    Segment segment = new Segment(SELECTION_END, myTemplateText.length());
    mySegments.add(segment);
  }

  @Override
  public String getId() {
    return myId;
  }

  @NotNull
  @Override
  public TemplateImpl copy() {
    TemplateImpl template = new TemplateImpl(myKey, myString, myGroupName);
    template.resetFrom(this);
    return template;
  }

  public void resetFrom(TemplateImpl another) {
    removeAllParsed();
    toParseSegments = another.toParseSegments;

    myKey = another.getKey();
    myString = another.myString;
    myTemplateText = another.myTemplateText;
    myGroupName = another.myGroupName;
    myId = another.myId;
    myDescription = another.myDescription;
    myShortcutChar = another.myShortcutChar;
    isToReformat = another.isToReformat;
    isToShortenLongNames = another.isToShortenLongNames;
    myIsInline = another.myIsInline;
    myTemplateContext = another.myTemplateContext.createCopy();
    isDeactivated = another.isDeactivated;
    for (Property property : Property.values()) {
      boolean value = another.getValue(property);
      if (value != Template.getDefaultValue(property)) {
        setValue(property, value);
      }
    }
    for (Variable variable : another.myVariables) {
      addVariable(variable.getName(), variable.getExpressionString(), variable.getDefaultValueString(), variable.isAlwaysStopAt());
    }
  }

  @Override
  public boolean isToReformat() {
    return isToReformat;
  }

  @Override
  public void setToReformat(boolean toReformat) {
    isToReformat = toReformat;
  }

  @Override
  public void setToIndent(boolean toIndent) {
    isToIndent = toIndent;
  }

  public boolean isToIndent() {
    return isToIndent;
  }

  @Override
  public boolean isToShortenLongNames() {
    return isToShortenLongNames;
  }

  @Override
  public void setToShortenLongNames(boolean toShortenLongNames) {
    isToShortenLongNames = toShortenLongNames;
  }

  public void setDeactivated(boolean isDeactivated) {
    this.isDeactivated = isDeactivated;
  }

  public boolean isDeactivated() {
    return isDeactivated;
  }

  @NotNull public TemplateContext getTemplateContext() {
    return myTemplateContext;
  }

  public int getEndSegmentNumber() {
    return getVariableSegmentNumber(END);
  }

  public int getSelectionStartSegmentNumber() {
    return getVariableSegmentNumber(SELECTION_START);
  }

  public int getSelectionEndSegmentNumber() {
    return getVariableSegmentNumber(SELECTION_END);
  }

  public int getVariableSegmentNumber(String variableName) {
    parseSegments();
    for (int i = 0; i < mySegments.size(); i++) {
      Segment segment = mySegments.get(i);
      if (segment.name.equals(variableName)) {
        return i;
      }
    }
    return -1;
  }

  public IntArrayList getVariableSegmentNumbers(String variableName) {
    IntArrayList result = new IntArrayList();
    parseSegments();
    for (int i = 0; i < mySegments.size(); i++) {
      Segment segment = mySegments.get(i);
      if (segment.name.equals(variableName)) {
        result.add(i);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public String getTemplateText() {
    parseSegments();
    return myTemplateText;
  }

  @Override
  public String getSegmentName(int i) {
    parseSegments();
    return mySegments.get(i).name;
  }

  @Override
  public int getSegmentOffset(int i) {
    parseSegments();
    return mySegments.get(i).offset;
  }

  @Override
  public int getSegmentsCount() {
    parseSegments();
    return mySegments.size();
  }

  public void parseSegments() {
    if(!toParseSegments) {
      return;
    }
    if(mySegments != null) {
      return;
    }

    mySegments = new SmartList<>();
    StringBuilder buffer = new StringBuilder("");
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(myString);

    while(true){
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();
      String token = myString.substring(start, end);
      if (tokenType == TemplateTokenType.VARIABLE){
        String name = token.substring(1, token.length() - 1);
        Segment segment = new Segment(name, buffer.length());
        mySegments.add(segment);
      }
      else if (tokenType == TemplateTokenType.ESCAPE_DOLLAR){
        buffer.append("$");
      }
      else{
        buffer.append(token);
      }
      lexer.advance();
    }
    myTemplateText = buffer.toString();
  }

  public void removeAllParsed() {
    myVariables.clear();
    mySegments = null;
  }

  @Override
  public Variable addVariable(@NotNull String name, String expression, String defaultValue, boolean isAlwaysStopAt) {
    Variable variable = new Variable(name, expression, defaultValue, isAlwaysStopAt);
    myVariables.add(variable);
    return variable;
  }

  public void removeVariable(int i) {
    myVariables.remove(i);
  }

  public int getVariableCount() {
    return myVariables.size();
  }

  public String getVariableNameAt(int i) {
    return myVariables.get(i).getName();
  }

  public String getExpressionStringAt(int i) {
    return myVariables.get(i).getExpressionString();
  }

  public Expression getExpressionAt(int i) {
    return myVariables.get(i).getExpression();
  }

  public String getDefaultValueStringAt(int i) {
    return myVariables.get(i).getDefaultValueString();
  }

  public Expression getDefaultValueAt(int i) {
    return myVariables.get(i).getDefaultValueExpression();
  }

  public boolean isAlwaysStopAt(int i) {
    return myVariables.get(i).isAlwaysStopAt();
  }

  @Override
  public String getKey() {
    return myKey;
  }

  public void setKey(String key) {
    myKey = key;
  }

  @NotNull
  @Override
  public String getString() {
    parseSegments();
    return myString;
  }

  /**
   * Set template text as it appears in Live Template settings, including variables surrounded with '$'.
   * The text will be reparsed when needed.
   * @param string template string text
   */
  public void setString(@NotNull String string) {
    myString = StringUtil.convertLineSeparators(string);
    toParseSegments = true;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public char getShortcutChar() {
    return myShortcutChar;
  }

  public void setShortcutChar(char shortcutChar) {
    myShortcutChar = shortcutChar;
  }

  public String getGroupName() {
    return myGroupName;
  }

  @Override
  public void setGroupName(@NotNull String groupName) {
    myGroupName = groupName;
  }

  public boolean isSelectionTemplate() {
    parseSegments();
    for (Segment v : mySegments) {
      if (SELECTION.equals(v.name)) return true;
    }

    return false;
  }

  public boolean hasArgument() {
    for (Variable v : myVariables) {
      if (v.getName().equals(ARG)) return true;
    }
    return false;
  }

  public void setId(@Nullable final String id) {
    myId = id;
  }

  public Map<TemplateOptionalProcessor, Boolean> createOptions() {
    Map<TemplateOptionalProcessor, Boolean> context = new LinkedHashMap<TemplateOptionalProcessor, Boolean>();
    for (TemplateOptionalProcessor processor : Extensions.getExtensions(TemplateOptionalProcessor.EP_NAME)) {
      context.put(processor, processor.isEnabled(this));
    }
    return context;
  }

  public TemplateContext createContext() {
    return getTemplateContext().createCopy();
  }

  public boolean contextsEqual(TemplateImpl defaultTemplate) {
    return getTemplateContext().getDifference(defaultTemplate.getTemplateContext()) == null;
  }

  public void applyOptions(final Map<TemplateOptionalProcessor, Boolean> context) {
    for (Map.Entry<TemplateOptionalProcessor, Boolean> entry : context.entrySet()) {
      entry.getKey().setEnabled(this, entry.getValue().booleanValue());
    }
  }

  public void applyContext(final TemplateContext context) {
    myTemplateContext = context.createCopy();
  }

  public boolean skipOnStart(int i) {
    return myVariables.get(i).skipOnStart();
  }

  public ArrayList<Variable> getVariables() {
    return new ArrayList<>(myVariables);
  }

  private static class Segment {
    public final String name;
    public final int offset;

    private Segment(@NotNull String name, int offset) {
      this.name = name;
      this.offset = offset;
    }
  }

  @Override
  public String toString() {
    return myGroupName +"/" + myKey;
  }
}
