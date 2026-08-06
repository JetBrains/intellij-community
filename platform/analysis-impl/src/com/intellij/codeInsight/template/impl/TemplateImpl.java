// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
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
import com.intellij.util.DocumentUtil;
import com.intellij.util.SmartList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

  /**
   * Default placeholder for empty template segments. Inflated into zero-length segments during
   * macro evaluation and reformat, removed before any user-visible state.
   */
  private @NotNull String mySegmentPlaceholder = "_IjT_";

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
      if (value != getDefaultValue(property)) {
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
   * Returns the placeholder string substituted variables segments during
   * {@link #update(ModPsiUpdater, TemplateStateProcessor)} for macro evaluation and reformat.
   */
  @ApiStatus.Experimental
  public @NotNull String getSegmentPlaceholder() {
    return mySegmentPlaceholder;
  }

  /**
   * Overrides the placeholder used for segments in
   * {@link #update(ModPsiUpdater, TemplateStateProcessor)}. Use to substitute variables segments during
   * {@link #update(ModPsiUpdater, TemplateStateProcessor)} for macro evaluation and reformat.
   */
  @ApiStatus.Experimental
  public void setSegmentPlaceholder(@NotNull String placeholder) {
    mySegmentPlaceholder = placeholder;
  }

  /**
   * Performs a template execution within ModCommand context. The template is not actually executed, but
   * contributes to {@link ModPsiUpdater} to form the final {@link ModCommand}.
   * <p>
   * Note that not all the template behavior is implemented yet, and not everything is supported in ModCommands at all,
   * so expect that complex templates that use rare features may not work correctly.
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
    wholeTemplate.setGreedyToLeft(true);
    wholeTemplate.setGreedyToRight(true);
    Map<String, Variable> variableMap = StreamEx.of(getVariables()).toMap(Variable::getName, Function.identity());
    Project project = updater.getProject();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    List<Segment> segments = getSegments();
    // Build markers and a name→indices index in one pass; the index gives the O(1) lookups used
    // below by `computeInitialVariableValues` and the editable-field loop.
    List<MarkerInfo> markers = new ArrayList<>(segments.size());
    Map<String, List<Integer>> indicesByName = new HashMap<>();
    for (int i = 0; i < segments.size(); i++) {
      Segment segment = segments.get(i);
      RangeMarker marker = document.createRangeMarker(start + segment.offset, start + segment.offset);
      if (!END.equals(segment.name)) {
        // can be a conflict if EXPR and END are together
        marker.setGreedyToRight(true);
      }
      markers.add(new MarkerInfo(segment, marker));
      indicesByName.computeIfAbsent(segment.name, _ -> new ArrayList<>()).add(i);
    }

    Map<String, String> calculatedValues = computeInitialVariableValues(markers, indicesByName, manager, updater);

    RangeMarker endMarker = resolveEndMarker(markers, variableMap, calculatedValues, document, processor, updater.getPsiFile());
    for (TemplateOptionalProcessor proc : DumbService.getDumbAwareExtensions(project, TemplateOptionalProcessor.EP_NAME)) {
      if (proc instanceof ModCommandAwareTemplateOptionalProcessor mcProcessor) {
        mcProcessor.processText(this, updater, wholeTemplate);
      }
    }

    if (isToIndent()) {
      smartIndent(document, wholeTemplate.getStartOffset(), wholeTemplate.getEndOffset(), this, markers);
    }
    if (isToReformat()) {
      reformatTemplate(document, manager, project, updater, wholeTemplate, markers);
    }
    if (endMarker == null) {
      endMarker = document.createRangeMarker(wholeTemplate.getEndOffset(), wholeTemplate.getEndOffset());
    }
    document.deleteString(endMarker.getStartOffset(), endMarker.getEndOffset());
    manager.commitDocument(document);
    int endOffset = endMarker.getStartOffset();
    if (isToReformat()) {
      // Indent adjustment may replace the leading whitespace; its return value preserves the logical caret position.
      endOffset = adjustEndLineIndent(document, endOffset, project, updater);
    }
    // Anchor at the post-adjustment offset. Registering fields below can make additional document changes,
    // so the marker must track the final position until the template is built.
    endMarker.dispose();
    endMarker = document.createRangeMarker(endOffset, endOffset);

    // Done after formatting so that field ranges match the final document state.
    ModTemplateBuilder builder = updater.templateBuilder();
    boolean fieldsCreated = registerEditableFields(builder, markers, indicesByName, manager, updater);
    fieldsCreated |= registerMirrorFields(builder, markers, indicesByName, calculatedValues, manager, updater);

    if (fieldsCreated) {
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

  /**
   * Wraps an expression so that when its {@link Expression#calculateResult} returns {@code null},
   * the default-value expression is tried as a fallback.
   * <p>
   * This mirrors the fallback logic that {@code TemplateState.recalcSegment()} already applies
   * for the interactive template session — the ModCommand/preview path needs the same behaviour.
   */
  private static @NotNull Expression withDefaultFallback(@NotNull Expression expression,
                                                         @NotNull Expression defaultValue) {
    return new Expression() {
      @Override
      public @Nullable Result calculateResult(ExpressionContext context) {
        Result result = expression.calculateResult(context);
        return result != null ? result : defaultValue.calculateResult(context);
      }

      @Override
      public LookupElement @Nullable [] calculateLookupItems(ExpressionContext context) {
        return expression.calculateLookupItems(context);
      }

      @Override
      public @NotNull LookupFocusDegree getLookupFocusDegree() {
        return expression.getLookupFocusDegree();
      }
    };
  }

  private static @Nullable RangeMarker resolveEndMarker(
    @NotNull List<MarkerInfo> markers,
    @NotNull Map<String, Variable> variableMap,
    @NotNull Map<String, String> calculatedValues,
    @NotNull Document document,
    @NotNull TemplateStateProcessor processor,
    @NotNull PsiFile psiFile
  ) {
    for (MarkerInfo info : markers) {
      if (!info.segment.name.equals(END)) continue;
      int endOffset = info.marker.getStartOffset();
      // If the END marker landed at the start of a non-editable variable's range
      // (happens when END and a non-editable variable share the same original template offset),
      // move END to after that variable's substituted value.
      for (MarkerInfo other : markers) {
        if (other == info) continue;
        Variable v = variableMap.get(other.segment.name);
        if (v != null && !v.isAlwaysStopAt() && calculatedValues.containsKey(other.segment.name)) {
          if (other.marker.getStartOffset() <= endOffset && endOffset < other.marker.getEndOffset()) {
            endOffset = other.marker.getEndOffset();
          }
        }
      }
      TextRange range = processor.insertNewLineIndentMarker(psiFile, document, endOffset);
      return range != null
             ? document.createRangeMarker(range)
             : document.createRangeMarker(endOffset, endOffset);
    }
    return null;
  }

  private record DependencySpec(@NotNull String dependantName, @Nullable String defaultValue) {}

  /**
   * Decides whether {@code variable} (a non-editable) should be linked as a dependent field.
   *
   * <ol>
   *   <li><b>Identity mirror</b>: if some editable has the same computed value AND a
   *       {@link #getSegmentPlaceholder()} probe confirms the expression is pass-through on that
   *       editable's value (e.g. {@code escapeString(EXPR)} on an identifier), returns the
   *       editable's <b>simple name</b> so LSP snippet mirroring works with it.</li>
   *   <li><b>Non-identity dependency via AST</b>: if the expression's AST contains a
   *       {@link VariableNode} referencing some editable's name (e.g.
   *       {@code iterableComponentType(ITERABLE_TYPE)}), returns the full <b>expression string</b>
   *       plus the XML default so the classic template engine re-parses and re-evaluates the
   *       macro on every edit and falls back to the default when it returns {@code null}.</li>
   *   <li><b>PSI-reading macro</b>: any other {@link MacroCallNode} expression (e.g.
   *       {@code rightSideType()} has no parameters but reads the surrounding PSI) is also
   *       registered with expression string + XML default, so recompute-on-edit works even when
   *       there's no {@code VariableNode} to walk.</li>
   * </ol>
   *
   * <p>Returns {@code null} if the expression isn't a macro call or has no textual form
   * ({@code getExpressionString()} is empty — typical for programmatic {@link Expression}
   * subclasses).
   */
  private @Nullable DependencySpec detectDependantSpec(@NotNull Variable variable,
                                                       @NotNull Collection<Variable> allVariables,
                                                       @NotNull Map<String, String> calculatedValues,
                                                       @NotNull TextRange markerRange,
                                                       @NotNull PsiElement element,
                                                       @NotNull PsiFile file) {
    String name = variable.getName();
    String value = calculatedValues.get(name);
    if (value == null) return null;
    Expression expression = variable.getExpression();
    String exprString = variable.getExpressionString();
    // Detection strategies, in order: (1) identity mirror (sentinel-probe), (2) AST reference
    // to an editable VariableNode, (3) parameterless macro with a non-empty XML default.
    // Parameterless macros without a default stay unregistered so the initial reformat pass
    // isn't lost to `TemplateBuilderImpl.initInlineTemplate`'s wipe + no-reformat re-insert.
    String placeholder = getSegmentPlaceholder();
    for (Variable other : allVariables) {
      if (!other.isAlwaysStopAt()) continue;
      String otherName = other.getName();
      if (otherName.equals(name)) continue;
      String otherValue = calculatedValues.get(otherName);
      if (otherValue == null) continue;
      if (value.equals(otherValue)) {
        Map<String, String> probe = new LinkedHashMap<>(calculatedValues);
        probe.put(otherName, placeholder);
        Result probeResult = expression.calculateResult(new DummyContext(markerRange, element, file, probe));
        if (probeResult != null && placeholder.equals(probeResult.toString())) {
          return new DependencySpec(otherName, null);
        }
      }
      if (!exprString.isEmpty() && expressionReferences(expression, otherName)) {
        String defaultValue = variable.getDefaultValueString();
        return new DependencySpec(exprString, defaultValue.isEmpty() ? null : defaultValue);
      }
    }
    if (expression instanceof MacroCallNode && !exprString.isEmpty()) {
      String defaultValue = variable.getDefaultValueString();
      if (!defaultValue.isEmpty()) {
        return new DependencySpec(exprString, defaultValue);
      }
    }
    return null;
  }

  /**
   * Walks an {@link Expression} tree looking for a {@link VariableNode} whose name matches
   * {@code variableName}. Only recognises nodes produced by the template-expression parser —
   * programmatic custom {@code Expression} subclasses can't be introspected and return false.
   */
  private static boolean expressionReferences(@NotNull Expression expression, @NotNull String variableName) {
    if (expression instanceof VariableNode vn) {
      if (variableName.equals(vn.getName())) return true;
      Expression init = vn.getInitialValue();
      return init != null && expressionReferences(init, variableName);
    }
    if (expression instanceof MacroCallNode mn) {
      for (Expression param : mn.getParameters()) {
        if (expressionReferences(param, variableName)) return true;
      }
    }
    return false;
  }

  /**
   * Registers editable variables as interactive template fields in declaration order, so tab-stop
   * order matches template semantics rather than segment text order.
   *
   * @return {@code true} if at least one field was registered on {@code builder}.
   */
  private boolean registerEditableFields(@NotNull ModTemplateBuilder builder,
                                          @NotNull List<MarkerInfo> markers,
                                          @NotNull Map<String, List<Integer>> indicesByName,
                                          @NotNull PsiDocumentManager manager,
                                          @NotNull ModPsiUpdater updater) {
    Document document = updater.getDocument();
    boolean any = false;
    for (Variable variable : getVariables()) {
      if (!variable.isAlwaysStopAt()) continue;
      String name = variable.getName();
      for (int idx : indicesByName.getOrDefault(name, List.of())) {
        MarkerInfo info = markers.get(idx);
        manager.commitDocument(document);
        PsiElement element = updater.getPsiFile().findElementAt(info.marker.getStartOffset());
        if (element == null) continue;
        //see `result = defaultValue.calculateResult(context)` in TemplateState.recalcSegment
        Expression expression = withDefaultFallback(variable.getExpression(), variable.getDefaultValueExpression());
        builder.field(element, info.marker.getTextRange().shiftLeft(element.getTextRange().getStartOffset()),
                      name, expression);
        any = true;
      }
    }
    return any;
  }

  /**
   * Registers non-editable variables that mirror an editable one as dependent fields, so a later
   * edit of the editable field keeps both positions in sync
   *
   * @return {@code true} if at least one mirror field was registered on {@code builder}.
   */
  private boolean registerMirrorFields(@NotNull ModTemplateBuilder builder,
                                        @NotNull List<MarkerInfo> markers,
                                        @NotNull Map<String, List<Integer>> indicesByName,
                                        @NotNull Map<String, String> calculatedValues,
                                        @NotNull PsiDocumentManager manager,
                                        @NotNull ModPsiUpdater updater) {
    Document document = updater.getDocument();
    boolean any = false;
    ArrayList<Variable> variables = getVariables();
    for (Variable variable : variables) {
      if (variable.isAlwaysStopAt()) continue;
      String name = variable.getName();
      List<Integer> occurrences = indicesByName.getOrDefault(name, List.of());
      if (occurrences.isEmpty()) continue;
      MarkerInfo firstInfo = markers.get(occurrences.getFirst());
      manager.commitDocument(document);
      PsiElement firstElement = updater.getPsiFile().findElementAt(firstInfo.marker.getStartOffset());
      if (firstElement == null) continue;
      DependencySpec spec = detectDependantSpec(variable, variables, calculatedValues,
                                                firstInfo.marker.getTextRange(), firstElement, updater.getPsiFile());
      if (spec == null) continue;
      for (int idx : occurrences) {
        MarkerInfo info = markers.get(idx);
        manager.commitDocument(document);
        PsiElement element = updater.getPsiFile().findElementAt(info.marker.getStartOffset());
        if (element == null) continue;
        builder.field(element, info.marker.getTextRange().shiftLeft(element.getTextRange().getStartOffset()),
                      name, spec.dependantName(), spec.defaultValue());
        any = true;
      }
    }
    return any;
  }

  private record InflatedSegment(MarkerInfo info, boolean preSpaceBefore, boolean preSpaceAfter) {}

  private void reformatTemplate(@NotNull Document document, @NotNull PsiDocumentManager manager,
                                       @NotNull Project project, @NotNull ModPsiUpdater updater,
                                       @NotNull RangeMarker wholeTemplate, @NotNull List<MarkerInfo> markers) {
    List<InflatedSegment> emptyValues = new ArrayList<>();
    for (MarkerInfo info : markers) {
      if (END.equals(info.segment.name)) continue;
      int pos = info.marker.getStartOffset();
      if (pos == info.marker.getEndOffset()) {
        CharSequence chars = document.getCharsSequence();
        boolean preSpaceBefore = pos > 0 && chars.charAt(pos - 1) == ' ';
        boolean preSpaceAfter = pos < chars.length() && chars.charAt(pos) == ' ';
        document.insertString(pos, getSegmentPlaceholder());
        emptyValues.add(new InflatedSegment(info, preSpaceBefore, preSpaceAfter));
      }
    }
    manager.commitDocument(document);
    int reformatStart = wholeTemplate.getStartOffset();
    int reformatEnd = wholeTemplate.getEndOffset();
    // Extend reformat range to include leading whitespace on the line so the formatter can adjust indentation
    int lineStart = document.getLineStartOffset(document.getLineNumber(reformatStart));
    if (document.getCharsSequence().subSequence(lineStart, reformatStart).toString().isBlank()) {
      reformatStart = lineStart;
    }
    CodeStyleManager.getInstance(project).reformatText(updater.getPsiFile(), reformatStart, reformatEnd);
    for (InflatedSegment seg : emptyValues) {
      int start = seg.info.marker.getStartOffset();
      int end = seg.info.marker.getEndOffset();
      // If the reformat added a space that wasn't there before inflation (e.g. formatter put
      // a space on both sides of the placeholder in `=_IJT_ iterator`), consume one space
      // so empty values collapse cleanly. If both sides already had spaces before inflation
      // (e.g. `instanceof  ?`), leave both in place so the template's literal spacing is kept.
      CharSequence chars = document.getCharsSequence();
      boolean postSpaceBefore = start > 0 && chars.charAt(start - 1) == ' ';
      boolean postSpaceAfter = end < chars.length() && chars.charAt(end) == ' ';
      if (postSpaceBefore && postSpaceAfter && !(seg.preSpaceBefore() && seg.preSpaceAfter())) {
        end++;
      }
      document.deleteString(start, end);
    }
  }

  private static int adjustEndLineIndent(@NotNull Document document, int offset,
                                         @NotNull Project project, @NotNull ModPsiUpdater updater) {
    int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
    if (document.getCharsSequence().subSequence(lineStart, offset).toString().trim().isEmpty()) {
      return CodeStyleManager.getInstance(project).adjustLineIndent(updater.getPsiFile(), offset);
    }
    return offset;
  }

  @Override
  public String toString() {
    return myGroupName +"/" + myKey;
  }

  private record MarkerInfo(Segment segment, RangeMarker marker) {}

  /**
   * Evaluates every template variable in declaration order, substituting the computed value
   * into the document so later variables can see it (via PSI scan or
   * {@link ExpressionContext#getVariableValue}).
   *
   * <p>Mutates {@code markers}: entries covering substituted values are replaced via
   * {@link #substituteAtMarker} (old {@link RangeMarker}s are disposed and re-created with
   * exact ranges). Indices in {@code markers} stay stable.
   *
   * @return map of variable name → computed value.
   */
  private @NotNull Map<String, String> computeInitialVariableValues(
    @NotNull List<MarkerInfo> markers,
    @NotNull Map<String, List<Integer>> indicesByName,
    @NotNull PsiDocumentManager manager,
    @NotNull ModPsiUpdater updater
  ) {
    Map<String, String> calculatedValues = new LinkedHashMap<>();
    Document document = updater.getDocument();
    ArrayList<Variable> variables = getVariables();
    Set<String> processed = new HashSet<>();
    for (Variable variable : variables) {
      String name = variable.getName();
      if (calculatedValues.containsKey(name)) continue;
      // indices are in segment (= text) order by construction.
      List<Integer> occurrences = indicesByName.getOrDefault(name, List.of());
      // Don't inflate variables that have already been evaluated — their empty state is
      // intentional (result was null/empty).
      if (occurrences.isEmpty()) {
        processed.add(name);
        continue;
      }
      List<Integer> inflated = inflateEmptySegments(markers, variables, indicesByName, document, processed);
      manager.commitDocument(document);
      MarkerInfo primary = markers.get(occurrences.getFirst());
      PsiElement element = updater.getPsiFile().findElementAt(primary.marker.getStartOffset());
      //see `result = defaultValue.calculateResult(context)` in TemplateState.recalcSegment
      Expression expression = withDefaultFallback(variable.getExpression(), variable.getDefaultValueExpression());
      Result result = element == null ? null : expression.calculateResult(
        new DummyContext(primary.marker.getTextRange(), element, updater.getPsiFile(), calculatedValues));
      restoreEmptySegments(markers, inflated, document);
      manager.commitDocument(document);
      processed.add(name);
      if (result == null) continue;
      String value = result.toString();
      if (value == null || value.isEmpty()) continue;
      calculatedValues.put(name, value);
      // Substitute at every marker carrying this name, right-to-left so earlier markers don't shift.
      for (int i = occurrences.size() - 1; i >= 0; i--) {
        substituteAtMarker(markers, occurrences.get(i), value, document);
      }
      manager.commitDocument(document);
    }
    return calculatedValues;
  }

  /**
   * Fills every zero-length segment whose name matches a variable with a placeholder (the
   * supplied {@code placeholder}, defaulting to {@link #getSegmentPlaceholder()}).
   * Returns the marker indices that were inflated, to be passed back to
   * {@link #restoreEmptySegments}.
   */
  private @NotNull List<Integer> inflateEmptySegments(@NotNull List<MarkerInfo> markers,
                                                             @NotNull List<Variable> variables,
                                                             @NotNull Map<String, List<Integer>> indicesByName,
                                                             @NotNull Document document,
                                                             @NotNull Set<String> skipAlreadyProcessed) {
    List<Integer> inflated = new ArrayList<>();
    for (Variable variable : variables) {
      String name = variable.getName();
      if (skipAlreadyProcessed.contains(name)) continue;
      for (int idx : indicesByName.getOrDefault(name, List.of())) {
        MarkerInfo info = markers.get(idx);
        if (info.marker.getStartOffset() != info.marker.getEndOffset()) continue;
        substituteAtMarker(markers, idx, getSegmentPlaceholder(), document);
        inflated.add(idx);
      }
    }
    return inflated;
  }

  private static void restoreEmptySegments(@NotNull List<MarkerInfo> markers,
                                            @NotNull List<Integer> inflated,
                                            @NotNull Document document) {
    for (int i = inflated.size() - 1; i >= 0; i--) {
      MarkerInfo info = markers.get(inflated.get(i));
      document.deleteString(info.marker.getStartOffset(), info.marker.getEndOffset());
    }
  }

  /**
   * Substitutes {@code value} at {@code markers.get(idx)}, repositioning any zero-length
   * siblings at the same offset (e.g. {@code $FINAL$$TYPE$}) so they don't greedy-extend
   * over the insertion.
   */
  private static void substituteAtMarker(@NotNull List<MarkerInfo> markers, int idx,
                                          @NotNull String value, @NotNull Document document) {
    MarkerInfo target = markers.get(idx);
    int start = target.marker.getStartOffset();
    int end = target.marker.getEndOffset();
    if (start != end) {
      document.replaceString(start, end, value);
      return;
    }
    List<Integer> atOffset = zeroLengthMarkersAt(markers, start);
    for (int i : atOffset) {
      markers.get(i).marker.dispose();
    }
    document.insertString(start, value);
    int end2 = start + value.length();
    // Target covers the inserted text; siblings stay zero-length, placed before or after
    // the insertion according to their segment-list order relative to the target.
    markers.set(idx, recreateMarker(document, target.segment, start, end2));
    for (int i : atOffset) {
      if (i == idx) continue;
      int pos = i < idx ? start : end2;
      markers.set(i, recreateMarker(document, markers.get(i).segment, pos, pos));
    }
  }

  private static @NotNull List<Integer> zeroLengthMarkersAt(@NotNull List<MarkerInfo> markers, int offset) {
    List<Integer> result = new ArrayList<>();
    for (int i = 0; i < markers.size(); i++) {
      RangeMarker m = markers.get(i).marker;
      if (m.getStartOffset() == offset && m.getEndOffset() == offset) result.add(i);
    }
    return result;
  }

  private static @NotNull MarkerInfo recreateMarker(@NotNull Document document, @NotNull Segment segment, int start, int end) {
    RangeMarker marker = document.createRangeMarker(start, end);
    if (!END.equals(segment.name)) {
      marker.setGreedyToRight(true);
    }
    return new MarkerInfo(segment, marker);
  }

  /**
   * similar to {@link TemplateState#smartIndent(int, int)}
   */
  private static void smartIndent(@NotNull Document document, int startOffset, int endOffset,
                                   @NotNull TemplateBase template, @NotNull List<MarkerInfo> markers) {
    int startLineNum = document.getLineNumber(startOffset);
    int endLineNum = document.getLineNumber(endOffset);
    if (endLineNum == startLineNum) {
      return;
    }

    int selectionIndent = -1;
    int selectionStartLine = -1;
    int selectionEndLine = -1;
    int selectionSegment = template.getVariableSegmentNumber(SELECTION);
    if (selectionSegment >= 0) {
      int selectionStart = template.getSegmentOffset(selectionSegment);
      selectionIndent = 0;
      String templateText = template.getTemplateText();
      while (selectionStart > 0 && templateText.charAt(selectionStart - 1) == ' ') {
        selectionIndent++;
        selectionStart--;
      }
      for (MarkerInfo info : markers) {
        if (SELECTION.equals(info.segment.name)) {
          selectionStartLine = document.getLineNumber(info.marker.getStartOffset());
          selectionEndLine = document.getLineNumber(info.marker.getEndOffset());
          break;
        }
      }
    }

    int indentLineNum = startLineNum;
    int lineLength = 0;
    for (; indentLineNum >= 0; indentLineNum--) {
      lineLength = document.getLineEndOffset(indentLineNum) - document.getLineStartOffset(indentLineNum);
      if (lineLength > 0) {
        break;
      }
    }
    if (indentLineNum < 0) {
      return;
    }
    StringBuilder buffer = new StringBuilder();
    CharSequence text = document.getCharsSequence();
    for (int i = 0; i < lineLength; i++) {
      char ch = text.charAt(document.getLineStartOffset(indentLineNum) + i);
      if (ch != ' ' && ch != '\t') {
        break;
      }
      buffer.append(ch);
    }
    if (buffer.isEmpty() && selectionIndent <= 0 || startLineNum >= endLineNum) {
      return;
    }
    String stringToInsert = buffer.toString();
    int finalSelectionStartLine = selectionStartLine;
    int finalSelectionEndLine = selectionEndLine;
    int finalSelectionIndent = selectionIndent;
    DocumentUtil.executeInBulk(document, () -> {
      for (int i = startLineNum + 1; i <= endLineNum; i++) {
        if (i > finalSelectionStartLine && i <= finalSelectionEndLine) {
          document.insertString(document.getLineStartOffset(i), StringUtil.repeatSymbol(' ', finalSelectionIndent));
        }
        else {
          document.insertString(document.getLineStartOffset(i), stringToInsert);
        }
      }
    });
  }

  @ApiStatus.Internal
  public static class DummyContext implements ExpressionContext {
    private final @NotNull TextRange myRange;
    private final @NotNull PsiElement myElement;
    private final @NotNull PsiFile myFile;
    private final @Nullable Map<String, String> myVariableValues;

    public DummyContext(@NotNull TextRange range, @NotNull PsiElement element, @NotNull PsiFile file) {
      this(range, element, file, null);
    }

    public DummyContext(@NotNull TextRange range, @NotNull PsiElement element, @NotNull PsiFile file,
                        @Nullable Map<String, String> variableValues) {
      myRange = range;
      myElement = element;
      myFile = file;
      myVariableValues = variableValues;
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
    public @Nullable TextResult getVariableValue(String variableName) {
      if (myVariableValues != null) {
        String value = myVariableValues.get(variableName);
        if (value != null) {
          return new TextResult(value);
        }
      }
      return null;
    }
  }
}
