// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.options.SchemeElement;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

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
    if (!(o instanceof TemplateImpl template)) return false;

    if (myId != null && myId.equals(template.myId)) return true;

    if (isToReformat != template.isToReformat) return false;
    if (isToShortenLongNames != template.isToShortenLongNames) return false;
    if (myShortcutChar != template.myShortcutChar) return false;
    return Objects.equals(myDescription, template.myDescription) &&
           Objects.equals(myGroupName, template.myGroupName) &&
           Objects.equals(myKey, template.myKey) &&
           string().equals(template.string()) &&
           getValue(Property.USE_STATIC_IMPORT_IF_POSSIBLE) == template.getValue(Property.USE_STATIC_IMPORT_IF_POSSIBLE) &&
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

  private static final @NonNls String SELECTION_START = "SELECTION_START";
  private static final @NonNls String SELECTION_END = "SELECTION_END";
  public static final @NonNls String ARG = "ARG";

  public static final Set<String> INTERNAL_VARS_SET = Set.of(
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

  @ApiStatus.Internal
  public TemplateImpl(@NotNull @NlsSafe String key, @NlsSafe String string, @NotNull @NonNls String group, boolean storeBuildingStacktrace) {
    super(StringUtil.convertLineSeparators(StringUtil.notNullize(string)));
    myKey = key;
    myGroupName = group;
    setBuildingTemplateTrace(storeBuildingStacktrace ? new Throwable() : null);
  }

  @Override
  public @NotNull Variable addVariable(@NotNull Expression expression, boolean isAlwaysStopAt) {
    return addVariable("__Variable" + myVariables.size(), expression, isAlwaysStopAt);
  }

  @Override
  public @NotNull Variable addVariable(@NotNull @NlsSafe String name,
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

  @Override
  public @NotNull Variable addVariable(@NotNull @NlsSafe String name, @NlsSafe String expression, @NlsSafe String defaultValue, boolean isAlwaysStopAt) {
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

  @Override
  public @NotNull TemplateImpl copy() {
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

  public @NotNull TemplateContext getTemplateContext() {
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

  public @NotNull @NlsSafe String getVariableNameAt(int i) {
    return myVariables.get(i).getName();
  }

  public @NotNull @NlsSafe String getExpressionStringAt(int i) {
    return myVariables.get(i).getExpressionString();
  }

  @NotNull
  Expression getExpressionAt(int i) {
    return myVariables.get(i).getExpression();
  }

  public @NotNull @NlsSafe String getDefaultValueStringAt(int i) {
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

  public void setId(final @Nullable String id) {
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
    TemplateContext templateContext = getTemplateContext();
    for (Map.Entry<TemplateOptionalProcessor, Boolean> entry : context.entrySet()) {
      TemplateOptionalProcessor key = entry.getKey();
      if (key.isVisible(this, templateContext)) {
        key.setEnabled(this, entry.getValue().booleanValue());
      }
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

  @ApiStatus.Internal
  public void dropParsedData() {
    for (Variable variable : myVariables) {
      variable.dropParsedData();
    }
  }

  @SuppressWarnings("unused")
  //used is cases when building templates without PSI and TemplateBuilder
  public void setPrimarySegment(int segmentNumber) {
    Collections.swap(getSegments(), 0, segmentNumber);
  }

  /**
   * Performs a template execution within ModCommand context. The template is not actually executed, but
   * contributes to {@link ModPsiUpdater} to form the final {@link ModCommand}.
   * <p>
   *   Note that not all the template behavior is implemented yet, and not everything is supported in ModCommands at all,
   *   so expect that complex templates that use rare features may not work correctly.
   * </p>
   * 
   * @param updater {@link ModPsiUpdater} to use.
   */
  @ApiStatus.Internal
  public void update(@NotNull ModPsiUpdater updater, @NotNull TemplateStateProcessor processor) {
    parseSegments();
    int start = updater.getCaretOffset();
    String text = getTemplateText();
    Document document = updater.getDocument();
    document.insertString(start, text);
    RangeMarker wholeTemplate = document.createRangeMarker(start, start + text.length());
    Map<String, Variable> variableMap = StreamEx.of(getVariables()).toMap(Variable::getName, Function.identity());
    Project project = updater.getProject();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    List<Segment> segments = getSegments();
    record MarkerInfo(Segment segment, RangeMarker marker) {}
    List<MarkerInfo> markers = ContainerUtil.map(segments, segment -> {
      RangeMarker marker = document.createRangeMarker(start + segment.offset, start + segment.offset);
      marker.setGreedyToRight(true);
      return new MarkerInfo(segment, marker);
    });
    ModTemplateBuilder builder = null;
    RangeMarker endMarker = null;
    for (MarkerInfo info : markers) {
      Segment segment = info.segment;
      if (segment.name.equals("END")) {
        TextRange range = processor.insertNewLineIndentMarker(updater.getPsiFile(), document, info.marker.getStartOffset());
        if (range != null) {
          endMarker = document.createRangeMarker(range);
        } else {
          endMarker = info.marker;
        }
        continue;
      }
      Variable variable = variableMap.get(segment.name);
      if (variable != null) {
        manager.commitDocument(document);
        PsiElement element = updater.getPsiFile().findElementAt(info.marker.getStartOffset());
        if (element != null) {
          if (!variable.isAlwaysStopAt()) {
            Result result = variable.getExpression().calculateResult(new DummyContext(info.marker.getTextRange(), element, updater.getPsiFile()));
            if (result != null) {
              document.replaceString(info.marker.getStartOffset(), info.marker.getEndOffset(), result.toString());
            }
          } else {
            if (builder == null) builder = updater.templateBuilder();
            builder.field(element, info.marker.getTextRange().shiftLeft(element.getTextRange().getStartOffset()), segment.name,
                          variable.getExpression());
          }
        }
      }
    }
    for (TemplateOptionalProcessor proc : DumbService.getDumbAwareExtensions(project, TemplateOptionalProcessor.EP_NAME)) {
      if (proc instanceof ModCommandAwareTemplateOptionalProcessor mcProcessor) {
        mcProcessor.processText(this, updater, wholeTemplate);
      }
    }

    if (isToReformat()) {
      List<MarkerInfo> emptyValues = new ArrayList<>();
      for (MarkerInfo info : markers) {
        if (info.marker.getStartOffset() == info.marker.getEndOffset()) {
          document.insertString(info.marker.getStartOffset(), "a");
          emptyValues.add(info);
        }
      }
      manager.commitDocument(document);
      CodeStyleManager.getInstance(project)
        .reformatText(updater.getPsiFile(), wholeTemplate.getStartOffset(), wholeTemplate.getEndOffset());
      for (MarkerInfo value : emptyValues) {
        document.deleteString(value.marker.getStartOffset(), value.marker.getEndOffset());
      }
    }
    if (endMarker == null) {
      endMarker = document.createRangeMarker(wholeTemplate.getEndOffset(), wholeTemplate.getEndOffset());
    }
    document.deleteString(endMarker.getStartOffset(), endMarker.getEndOffset());
    if (builder != null) {
      builder.finishAt(endMarker.getStartOffset());
    } else {
      updater.moveCaretTo(endMarker.getStartOffset());
    }
    endMarker.dispose();
    for (MarkerInfo info : markers) {
      info.marker.dispose();
    }
    wholeTemplate.dispose();
  }

  @Override
  public String toString() {
    return myGroupName +"/" + myKey;
  }

  @ApiStatus.Internal
  public static class DummyContext implements ExpressionContext {
    private final @NotNull TextRange myRange;
    private final @NotNull PsiElement myElement;
    private final @NotNull PsiFile myFile;

    public DummyContext(@NotNull TextRange range, @NotNull PsiElement element, @NotNull PsiFile file) {
      myRange = range;
      myElement = element;
      myFile = file;
    }

    @Override
    public Project getProject() { return myFile.getProject(); }

    @Override
    public @Nullable PsiFile getPsiFile() {
      return myFile;
    }

    @Override
    public @Nullable Editor getEditor() { return null; }

    @Override
    public int getStartOffset() { return myRange.getStartOffset(); }

    @Override
    public int getTemplateStartOffset() { return myRange.getStartOffset(); }

    @Override
    public int getTemplateEndOffset() { return myRange.getEndOffset(); }

    @Override
    public <T> T getProperty(Key<T> key) { return null; }

    @Override
    public @Nullable PsiElement getPsiElementAtStartOffset() { return myElement.isValid() ? myElement : null; }

    @Override
    public @Nullable TextResult getVariableValue(String variableName) { return null; }
  }
}
