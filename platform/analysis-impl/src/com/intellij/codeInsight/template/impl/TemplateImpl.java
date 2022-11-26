// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.options.SchemeElement;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TemplateImpl extends TemplateBase implements SchemeElement {
  private @NlsSafe String myKey;
  private @NlsContexts.DetailedDescription String myDescription;
  private String myGroupName;
  private char myShortcutChar = TemplateConstants.DEFAULT_CHAR;
  private final List<Variable> myVariables = new SmartList<>();
  private @NonNls String myId;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TemplateImpl)) return false;

    final TemplateImpl template = (TemplateImpl) o;
    if (myId != null && myId.equals(template.myId)) return true;

    if (isToReformat != template.isToReformat) return false;
    if (isToShortenLongNames != template.isToShortenLongNames) return false;
    if (myShortcutChar != template.myShortcutChar) return false;
    return Objects.equals(myDescription, template.myDescription) &&
           Objects.equals(myGroupName, template.myGroupName) &&
           Objects.equals(myKey, template.myKey) &&
           string().equals(template.string()) &&
           (templateText() != null ? templateText().equals(template.templateText()) : template.templateText() == null) &&
           new HashSet<>(myVariables).equals(new HashSet<>(template.myVariables)) && isDeactivated == template.isDeactivated;
  }

  @Override
  public int hashCode() {
    if (myId != null) {
      return myId.hashCode();
    }
    int result;
    result = myKey.hashCode();
    result = 29 * result + string().hashCode();
    result = 29 * result + myGroupName.hashCode();
    return result;
  }

  private boolean isToReformat;
  private boolean isToShortenLongNames = true;
  private TemplateContext myTemplateContext = new TemplateContext();

  @NonNls private static final String SELECTION_START = "SELECTION_START";
  @NonNls private static final String SELECTION_END = "SELECTION_END";
  @NonNls public static final String ARG = "ARG";

  public static final Set<String> INTERNAL_VARS_SET = ContainerUtil.set(
    END, SELECTION, SELECTION_START, SELECTION_END);

  private boolean isDeactivated;

  public boolean isInline() {
    return myIsInline;
  }

  private boolean isToIndent = true;


  @Override
  public void setInline(boolean isInline) {
    myIsInline = isInline;
  }

  private boolean myIsInline;

  public TemplateImpl(@NotNull @NlsSafe String key, @NotNull @NonNls String group) {
    this(key, null, group);
    setToParseSegments(false);
    setTemplateText("");
  }

  public TemplateImpl(@NotNull @NlsSafe String key, @Nullable @NlsSafe String string, @NotNull @NonNls String group) {
    this(key, string, group, true);
  }

  TemplateImpl(@NotNull @NlsSafe String key, @NlsSafe String string, @NotNull @NonNls String group, boolean storeBuildingStacktrace) {
    super(StringUtil.convertLineSeparators(StringUtil.notNullize(string)));
    myKey = key;
    myGroupName = group;
    setBuildingTemplateTrace(storeBuildingStacktrace ? new Throwable() : null);
  }

  @NotNull
  @Override
  public Variable addVariable(@NotNull Expression expression, boolean isAlwaysStopAt) {
    return addVariable("__Variable" + myVariables.size(), expression, isAlwaysStopAt);
  }

  @NotNull
  @Override
  public Variable addVariable(@NotNull @NlsSafe String name,
                              Expression expression,
                              Expression defaultValueExpression,
                              boolean isAlwaysStopAt,
                              boolean skipOnStart) {
    if (isParsed() || !isToParseSegments()) {
      addVariableSegment(name);
    }
    Variable variable = new Variable(name, expression, defaultValueExpression, isAlwaysStopAt, skipOnStart);
    myVariables.add(variable);
    return variable;
  }

  @NotNull
  @Override
  public Variable addVariable(@NotNull @NlsSafe String name, @NlsSafe String expression, @NlsSafe String defaultValue, boolean isAlwaysStopAt) {
    Variable variable = new Variable(name, expression, defaultValue, isAlwaysStopAt);
    myVariables.add(variable);
    return variable;
  }

  @Override
  public void addVariable(@NotNull Variable variable) {
    myVariables.add(variable);
  }

  @Override
  public void addEndVariable() {
    addVariableSegment(END);
  }

  @Override
  public void addSelectionStartVariable() {
    addVariableSegment(SELECTION_START);
  }

  @Override
  public void addSelectionEndVariable() {
    addVariableSegment(SELECTION_END);
  }

  @Override
  public @NonNls String getId() {
    return myId;
  }

  @NotNull
  @Override
  public TemplateImpl copy() {
    TemplateImpl template = new TemplateImpl(myKey, string(), myGroupName);
    template.resetFrom(this);
    return template;
  }

  public void resetFrom(TemplateImpl another) {
    removeAllParsed();
    setToParseSegments(another.isToParseSegments());

    myKey = another.getKey();
    setString(another.string());
    setTemplateText(another.templateText());
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
        setValue(property, true);
      }
    }
    for (Variable variable : another.myVariables) {
      myVariables.add(new Variable(variable));
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

  @NotNull
  public TemplateContext getTemplateContext() {
    return myTemplateContext;
  }

  public int getEndSegmentNumber() {
    return getVariableSegmentNumber(END);
  }

  int getSelectionStartSegmentNumber() {
    return getVariableSegmentNumber(SELECTION_START);
  }

  int getSelectionEndSegmentNumber() {
    return getVariableSegmentNumber(SELECTION_END);
  }

  public void removeAllParsed() {
    myVariables.clear();
    clearSegments();
    setToParseSegments(true);
    setBuildingTemplateTrace(new Throwable());
  }

  public void removeVariable(int i) {
    myVariables.remove(i);
  }

  public int getVariableCount() {
    return myVariables.size();
  }

  @NotNull
  public @NlsSafe String getVariableNameAt(int i) {
    return myVariables.get(i).getName();
  }

  @NotNull
  public @NlsSafe String getExpressionStringAt(int i) {
    return myVariables.get(i).getExpressionString();
  }

  @NotNull
  Expression getExpressionAt(int i) {
    return myVariables.get(i).getExpression();
  }

  @NotNull
  public @NlsSafe String getDefaultValueStringAt(int i) {
    return myVariables.get(i).getDefaultValueString();
  }

  @NotNull
  Expression getDefaultValueAt(int i) {
    return myVariables.get(i).getDefaultValueExpression();
  }

  public boolean isAlwaysStopAt(int i) {
    return myVariables.get(i).isAlwaysStopAt();
  }

  @Override
  public @NlsSafe String getKey() {
    return myKey;
  }

  public void setKey(@NlsSafe String key) {
    myKey = key;
  }

  @Override
  public @NlsContexts.DetailedDescription String getDescription() {
    return myDescription;
  }

  public void setDescription(@NlsContexts.DetailedDescription @Nullable String value) {
    value = StringUtil.notNullize(value).trim();
    if (!StringUtil.equals(value, myDescription)) {
      myDescription = value;
    }
  }

  public char getShortcutChar() {
    return myShortcutChar;
  }

  public void setShortcutChar(char shortcutChar) {
    myShortcutChar = shortcutChar;
  }

  public @NonNls String getGroupName() {
    return myGroupName;
  }

  @Override
  public void setGroupName(@NotNull @NonNls String groupName) {
    myGroupName = groupName;
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
    Map<TemplateOptionalProcessor, Boolean> context = new LinkedHashMap<>();
    for (TemplateOptionalProcessor processor : TemplateOptionalProcessor.EP_NAME.getExtensionList()) {
      context.put(processor, processor.isEnabled(this));
    }
    return context;
  }

  public TemplateContext createContext() {
    return getTemplateContext().createCopy();
  }

  public void applyOptions(final Map<TemplateOptionalProcessor, Boolean> context) {
    for (Map.Entry<TemplateOptionalProcessor, Boolean> entry : context.entrySet()) {
      entry.getKey().setEnabled(this, entry.getValue().booleanValue());
    }
  }

  public void applyContext(final TemplateContext context) {
    myTemplateContext = context.createCopy();
  }

  boolean skipOnStart(int i) {
    return myVariables.get(i).skipOnStart();
  }

  @Override
  public ArrayList<Variable> getVariables() {
    return new ArrayList<>(myVariables);
  }

  void dropParsedData() {
    for (Variable variable : myVariables) {
      variable.dropParsedData();
    }
  }

  @SuppressWarnings("unused")
  //used is cases when building templates without PSI and TemplateBuilder
  public void setPrimarySegment(int segmentNumber) {
    Collections.swap(getSegments(), 0, segmentNumber);
  }

  @Override
  public String toString() {
    return myGroupName +"/" + myKey;
  }
}
