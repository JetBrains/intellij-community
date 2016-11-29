/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.General.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Grouping.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.*;

/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:31 PM
 */
public class JavaRearranger implements Rearranger<JavaElementArrangementEntry>,
                                       ArrangementSectionRuleAwareSettings,
                                       ArrangementStandardSettingsAware,
                                       ArrangementColorsAware {

  // Type
  @NotNull private static final Set<ArrangementSettingsToken>                                SUPPORTED_TYPES     =
    ContainerUtilRt.newLinkedHashSet(
      FIELD, INIT_BLOCK, CONSTRUCTOR, METHOD, CLASS, INTERFACE, ENUM, GETTER, SETTER, OVERRIDDEN
    );
  // Modifier
  @NotNull private static final Set<ArrangementSettingsToken>                                SUPPORTED_MODIFIERS =
    ContainerUtilRt.newLinkedHashSet(
      PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE, STATIC, FINAL, ABSTRACT, SYNCHRONIZED, TRANSIENT, VOLATILE
    );
  @NotNull private static final List<ArrangementSettingsToken>                               SUPPORTED_ORDERS    =
    ContainerUtilRt.newArrayList(KEEP, BY_NAME);
  @NotNull private static final ArrangementSettingsToken                                     NO_TYPE             =
    new ArrangementSettingsToken("NO_TYPE", "NO_TYPE");
  @NotNull
  private static final          Map<ArrangementSettingsToken, Set<ArrangementSettingsToken>> MODIFIERS_BY_TYPE   =
    ContainerUtilRt.newHashMap();
  @NotNull private static final Collection<Set<ArrangementSettingsToken>>                    MUTEXES             =
    ContainerUtilRt.newArrayList();

  private static final Set<ArrangementSettingsToken> TYPES_WITH_DISABLED_ORDER = ContainerUtil.newHashSet();
  private static final Set<ArrangementSettingsToken> TYPES_WITH_DISABLED_NAME_MATCH = ContainerUtil.newHashSet();

  static {
    Set<ArrangementSettingsToken> visibilityModifiers = ContainerUtilRt.newHashSet(PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE);
    MUTEXES.add(visibilityModifiers);
    MUTEXES.add(SUPPORTED_TYPES);

    Set<ArrangementSettingsToken> commonModifiers = concat(visibilityModifiers, STATIC, FINAL);

    MODIFIERS_BY_TYPE.put(NO_TYPE, commonModifiers);
    MODIFIERS_BY_TYPE.put(ENUM, visibilityModifiers);
    MODIFIERS_BY_TYPE.put(INTERFACE, visibilityModifiers);
    MODIFIERS_BY_TYPE.put(CLASS, concat(commonModifiers, ABSTRACT));
    MODIFIERS_BY_TYPE.put(METHOD, concat(commonModifiers, SYNCHRONIZED, ABSTRACT));
    MODIFIERS_BY_TYPE.put(CONSTRUCTOR, concat(commonModifiers, SYNCHRONIZED));
    MODIFIERS_BY_TYPE.put(FIELD, concat(commonModifiers, TRANSIENT, VOLATILE));
    MODIFIERS_BY_TYPE.put(GETTER, ContainerUtilRt.<ArrangementSettingsToken>newHashSet());
    MODIFIERS_BY_TYPE.put(SETTER, ContainerUtilRt.<ArrangementSettingsToken>newHashSet());
    MODIFIERS_BY_TYPE.put(OVERRIDDEN, ContainerUtilRt.<ArrangementSettingsToken>newHashSet());
    MODIFIERS_BY_TYPE.put(INIT_BLOCK, ContainerUtilRt.newHashSet(STATIC));

    TYPES_WITH_DISABLED_ORDER.add(INIT_BLOCK);

    TYPES_WITH_DISABLED_NAME_MATCH.add(INIT_BLOCK);
  }

  private static final Map<ArrangementSettingsToken, List<ArrangementSettingsToken>> GROUPING_RULES = ContainerUtilRt.newLinkedHashMap();

  static {
    GROUPING_RULES.put(GETTERS_AND_SETTERS, Collections.<ArrangementSettingsToken>emptyList());
    GROUPING_RULES.put(OVERRIDDEN_METHODS, ContainerUtilRt.newArrayList(BY_NAME, KEEP));
    GROUPING_RULES.put(DEPENDENT_METHODS, ContainerUtilRt.newArrayList(BREADTH_FIRST, DEPTH_FIRST));
  }

  private static final StdArrangementRuleAliasToken VISIBILITY = new StdArrangementRuleAliasToken("visibility");

  static {
    final ArrayList<StdArrangementMatchRule> visibility = new ArrayList<>();
    and(visibility, PUBLIC);
    and(visibility, PACKAGE_PRIVATE);
    and(visibility, PROTECTED);
    and(visibility, PRIVATE);
    VISIBILITY.setDefinitionRules(visibility);
  }

  private static final StdArrangementExtendableSettings DEFAULT_SETTINGS;

  static {
    List<ArrangementGroupingRule> groupingRules = ContainerUtilRt.newArrayList(new ArrangementGroupingRule(GETTERS_AND_SETTERS));
    List<StdArrangementMatchRule> matchRules = ContainerUtilRt.newArrayList();
    ArrangementSettingsToken[] visibility = {PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE};
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, STATIC, FINAL, modifier);
    }
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, STATIC, modifier);
    }
    and(matchRules, INIT_BLOCK, STATIC);

    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, FINAL, modifier);
    }
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, modifier);
    }
    and(matchRules, FIELD);
    and(matchRules, INIT_BLOCK);
    and(matchRules, CONSTRUCTOR);
    and(matchRules, METHOD, STATIC);
    and(matchRules, METHOD);
    and(matchRules, ENUM);
    and(matchRules, INTERFACE);
    and(matchRules, CLASS, STATIC);
    and(matchRules, CLASS);

    List<StdArrangementRuleAliasToken> aliasTokens = ContainerUtilRt.newArrayList();
    aliasTokens.add(VISIBILITY);
    DEFAULT_SETTINGS = StdArrangementExtendableSettings.createByMatchRules(groupingRules, matchRules, aliasTokens);
  }

  private static final DefaultArrangementSettingsSerializer SETTINGS_SERIALIZER = new DefaultArrangementSettingsSerializer(DEFAULT_SETTINGS);

  @NotNull
  private static Set<ArrangementSettingsToken> concat(@NotNull Set<ArrangementSettingsToken> base, ArrangementSettingsToken... modifiers) {
    Set<ArrangementSettingsToken> result = ContainerUtilRt.newHashSet(base);
    Collections.addAll(result, modifiers);
    return result;
  }

  private static void setupGettersAndSetters(@NotNull JavaArrangementParseInfo info) {
    Collection<JavaArrangementPropertyInfo> properties = info.getProperties();
    for (JavaArrangementPropertyInfo propertyInfo : properties) {
      JavaElementArrangementEntry getter = propertyInfo.getGetter();
      JavaElementArrangementEntry setter = propertyInfo.getSetter();
      if (getter != null && setter != null && setter.getDependencies() == null) {
        setter.addDependency(getter);
      }
    }
  }

  private static void setupUtilityMethods(@NotNull JavaArrangementParseInfo info, @NotNull ArrangementSettingsToken orderType) {
    if (DEPTH_FIRST.equals(orderType)) {
      for (ArrangementEntryDependencyInfo rootInfo : info.getMethodDependencyRoots()) {
        setupDepthFirstDependency(rootInfo);
      }
    }
    else if (BREADTH_FIRST.equals(orderType)) {
      for (ArrangementEntryDependencyInfo rootInfo : info.getMethodDependencyRoots()) {
        setupBreadthFirstDependency(rootInfo);
      }
    }
    else {
      assert false : orderType;
    }
  }

  private static void setupDepthFirstDependency(@NotNull ArrangementEntryDependencyInfo info) {
    for (ArrangementEntryDependencyInfo dependencyInfo : info.getDependentEntriesInfos()) {
      setupDepthFirstDependency(dependencyInfo);
      JavaElementArrangementEntry dependentEntry = dependencyInfo.getAnchorEntry();
      if (dependentEntry.getDependencies() == null) {
        dependentEntry.addDependency(info.getAnchorEntry());
      }
    }
  }

  private static void setupBreadthFirstDependency(@NotNull ArrangementEntryDependencyInfo info) {
    Deque<ArrangementEntryDependencyInfo> toProcess = new ArrayDeque<>();
    toProcess.add(info);
    JavaElementArrangementEntry prev = info.getAnchorEntry();
    while (!toProcess.isEmpty()) {
      ArrangementEntryDependencyInfo current = toProcess.removeFirst();
      for (ArrangementEntryDependencyInfo dependencyInfo : current.getDependentEntriesInfos()) {
        JavaElementArrangementEntry dependencyMethod = dependencyInfo.getAnchorEntry();
        if (dependencyMethod.getDependencies() == null) {
          dependencyMethod.addDependency(prev);
          prev = dependencyMethod;
        }
        toProcess.addLast(dependencyInfo);
      }
    }
  }

  private static void setupOverriddenMethods(JavaArrangementParseInfo info) {
    for (JavaArrangementOverriddenMethodsInfo methodsInfo : info.getOverriddenMethods()) {
      JavaElementArrangementEntry previous = null;
      for (JavaElementArrangementEntry entry : methodsInfo.getMethodEntries()) {
        if (previous != null && entry.getDependencies() == null) {
          entry.addDependency(previous);
        }
        previous = entry;
      }
    }
  }

  @Nullable
  @Override
  public Pair<JavaElementArrangementEntry, List<JavaElementArrangementEntry>> parseWithNew(
    @NotNull PsiElement root,
    @Nullable Document document,
    @NotNull Collection<TextRange> ranges,
    @NotNull PsiElement element,
    @NotNull ArrangementSettings settings)
  {
    JavaArrangementParseInfo existingEntriesInfo = new JavaArrangementParseInfo();
    root.accept(new JavaArrangementVisitor(existingEntriesInfo, document, ranges, settings));

    JavaArrangementParseInfo newEntryInfo = new JavaArrangementParseInfo();
    element.accept(new JavaArrangementVisitor(newEntryInfo, document, Collections.singleton(element.getTextRange()), settings));
    if (newEntryInfo.getEntries().size() != 1) {
      return null;
    }
    return Pair.create(newEntryInfo.getEntries().get(0), existingEntriesInfo.getEntries());
  }

  @NotNull
  @Override
  public List<JavaElementArrangementEntry> parse(@NotNull PsiElement root,
                                                 @Nullable Document document,
                                                 @NotNull Collection<TextRange> ranges,
                                                 @NotNull ArrangementSettings settings)
  {
    // Following entries are subject to arrangement: class, interface, field, method.
    JavaArrangementParseInfo parseInfo = new JavaArrangementParseInfo();
    root.accept(new JavaArrangementVisitor(parseInfo, document, ranges, settings));
    for (ArrangementGroupingRule rule : settings.getGroupings()) {
      if (GETTERS_AND_SETTERS.equals(rule.getGroupingType())) {
        setupGettersAndSetters(parseInfo);
      }
      else if (DEPENDENT_METHODS.equals(rule.getGroupingType())) {
        setupUtilityMethods(parseInfo, rule.getOrderType());
      }
      else if (OVERRIDDEN_METHODS.equals(rule.getGroupingType())) {
        setupOverriddenMethods(parseInfo);
      }
    }
    List<ArrangementEntryDependencyInfo> fieldDependencyRoots = parseInfo.getFieldDependencyRoots();
    if (!fieldDependencyRoots.isEmpty()) {
      setupFieldInitializationDependencies(fieldDependencyRoots, settings, parseInfo);
    }
    return parseInfo.getEntries();
  }

  public void setupFieldInitializationDependencies(@NotNull List<ArrangementEntryDependencyInfo> fieldDependencyRoots,
                                                   @NotNull ArrangementSettings settings,
                                                   @NotNull JavaArrangementParseInfo parseInfo)
  {
    Collection<JavaElementArrangementEntry> fields = parseInfo.getFields();
    List<JavaElementArrangementEntry> arrangedFields = ArrangementEngine.arrange(fields, settings.getSections(), settings.getRulesSortedByPriority(), null);

    for (ArrangementEntryDependencyInfo root : fieldDependencyRoots) {
      JavaElementArrangementEntry anchorField = root.getAnchorEntry();
      final int anchorEntryIndex = arrangedFields.indexOf(anchorField);

      for (ArrangementEntryDependencyInfo fieldInInitializerInfo : root.getDependentEntriesInfos()) {
        JavaElementArrangementEntry fieldInInitializer = fieldInInitializerInfo.getAnchorEntry();
        if (arrangedFields.indexOf(fieldInInitializer) > anchorEntryIndex) {
          anchorField.addDependency(fieldInInitializer);
        }
      }
    }
  }


  @Override
  public int getBlankLines(@NotNull CodeStyleSettings settings,
                           @Nullable JavaElementArrangementEntry parent,
                           @Nullable JavaElementArrangementEntry previous,
                           @NotNull JavaElementArrangementEntry target)
  {
    if (previous == null) {
      return -1;
    }

    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    if (FIELD.equals(target.getType())) {
      if (parent != null && parent.getType() == INTERFACE) {
        return commonSettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE;
      }
      else if (INIT_BLOCK.equals(previous.getType())) {
        return javaSettings.BLANK_LINES_AROUND_INITIALIZER;
      }
      else {
        return commonSettings.BLANK_LINES_AROUND_FIELD;
      }
    }
    else if (METHOD.equals(target.getType())) {
      if (parent != null && parent.getType() == INTERFACE) {
        return commonSettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE;
      }
      else {
        return commonSettings.BLANK_LINES_AROUND_METHOD;
      }
    }
    else if (CLASS.equals(target.getType())) {
      return commonSettings.BLANK_LINES_AROUND_CLASS;
    }
    else if (INIT_BLOCK.equals(target.getType())) {
      return javaSettings.BLANK_LINES_AROUND_INITIALIZER;
    }
    else {
      return -1;
    }
  }

  @NotNull
  @Override
  public ArrangementSettingsSerializer getSerializer() {
    return SETTINGS_SERIALIZER;
  }

  @NotNull
  @Override
  public StdArrangementSettings getDefaultSettings() {
    return DEFAULT_SETTINGS;
  }

  @Nullable
  @Override
  public List<CompositeArrangementSettingsToken> getSupportedGroupingTokens() {
    return ContainerUtilRt.newArrayList(
      new CompositeArrangementSettingsToken(GETTERS_AND_SETTERS),
      new CompositeArrangementSettingsToken(OVERRIDDEN_METHODS, BY_NAME, KEEP),
      new CompositeArrangementSettingsToken(DEPENDENT_METHODS, BREADTH_FIRST, DEPTH_FIRST)
    );
  }

  @Nullable
  @Override
  public List<CompositeArrangementSettingsToken> getSupportedMatchingTokens() {
    return ContainerUtilRt.newArrayList(
      new CompositeArrangementSettingsToken(TYPE, SUPPORTED_TYPES),
      new CompositeArrangementSettingsToken(MODIFIER, SUPPORTED_MODIFIERS),
      new CompositeArrangementSettingsToken(StdArrangementTokens.Regexp.NAME),
      new CompositeArrangementSettingsToken(ORDER, KEEP, BY_NAME)
    );
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementSettingsToken token, @Nullable ArrangementMatchCondition current) {
    if (SUPPORTED_TYPES.contains(token)) {
      return true;
    }

    ArrangementSettingsToken type = null;
    if (current != null) {
      type = ArrangementUtil.parseType(current);
    }
    if (type == null) {
      type = NO_TYPE;
    }

    if (SUPPORTED_ORDERS.contains(token)) {
      return !TYPES_WITH_DISABLED_ORDER.contains(type);
    }

    if (StdArrangementTokens.Regexp.NAME.equals(token)) {
      return !TYPES_WITH_DISABLED_NAME_MATCH.contains(type);
    }

    Set<ArrangementSettingsToken> modifiers = MODIFIERS_BY_TYPE.get(type);
    return modifiers != null && modifiers.contains(token);
  }

  @NotNull
  @Override
  public ArrangementEntryMatcher buildMatcher(@NotNull ArrangementMatchCondition condition) throws IllegalArgumentException {
    throw new IllegalArgumentException("Can't build a matcher for condition " + condition);
  }

  @NotNull
  @Override
  public Collection<Set<ArrangementSettingsToken>> getMutexes() {
    return MUTEXES;
  }

  private static void and(@NotNull List<StdArrangementMatchRule> matchRules, @NotNull ArrangementSettingsToken... conditions) {
      if (conditions.length == 1) {
        matchRules.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(new ArrangementAtomMatchCondition(
          conditions[0]
        ))));
        return;
      }

      ArrangementCompositeMatchCondition composite = new ArrangementCompositeMatchCondition();
      for (ArrangementSettingsToken condition : conditions) {
        composite.addOperand(new ArrangementAtomMatchCondition(condition));
      }
      matchRules.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(composite)));
    }

  @Nullable
  @Override
  public TextAttributes getTextAttributes(@NotNull EditorColorsScheme scheme, @NotNull ArrangementSettingsToken token, boolean selected) {
    if (selected) {
      TextAttributes attributes = new TextAttributes();
      attributes.setForegroundColor(scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
      attributes.setBackgroundColor(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
      return attributes;
    }
    else if (SUPPORTED_TYPES.contains(token)) {
      return getAttributes(scheme, JavaHighlightingColors.KEYWORD);
    }
    else if (SUPPORTED_MODIFIERS.contains(token)) {
      getAttributes(scheme, JavaHighlightingColors.KEYWORD);
    }
    return null;
  }

  @Nullable
  private static TextAttributes getAttributes(@NotNull EditorColorsScheme scheme, @NotNull TextAttributesKey ... keys) {
    TextAttributes result = null;
    for (TextAttributesKey key : keys) {
      TextAttributes attributes = scheme.getAttributes(key).clone();
      if (attributes == null) {
        continue;
      }

      if (result == null) {
        result = attributes;
      }

      Color currentForegroundColor = result.getForegroundColor();
      if (currentForegroundColor == null) {
        result.setForegroundColor(attributes.getForegroundColor());
      }

      Color currentBackgroundColor = result.getBackgroundColor();
      if (currentBackgroundColor == null) {
        result.setBackgroundColor(attributes.getBackgroundColor());
      }

      if (result.getForegroundColor() != null && result.getBackgroundColor() != null) {
        return result;
      }
    }

    if (result != null && result.getForegroundColor() == null) {
      return null;
    }

    if (result != null && result.getBackgroundColor() == null) {
      result.setBackgroundColor(scheme.getDefaultBackground());
    }
    return result;
  }

  @Nullable
  @Override
  public Color getBorderColor(@NotNull EditorColorsScheme scheme, boolean selected) {
    return null;
  }
}
