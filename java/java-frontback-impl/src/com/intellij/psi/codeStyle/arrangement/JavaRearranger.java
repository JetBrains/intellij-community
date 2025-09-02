// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.General.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Grouping.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.*;

public final class JavaRearranger implements Rearranger<JavaElementArrangementEntry>,
                                             ArrangementSectionRuleAwareSettings,
                                             ArrangementStandardSettingsAware,
                                             ArrangementColorsAware {

  // Type
  private static final @NotNull Set<ArrangementSettingsToken> SUPPORTED_TYPES =
    ContainerUtil.newLinkedHashSet(
      FIELD, INIT_BLOCK, CONSTRUCTOR, METHOD, CLASS, INTERFACE, ENUM, GETTER, SETTER, OVERRIDDEN
    );
  // Modifier
  private static final @NotNull Set<ArrangementSettingsToken> SUPPORTED_MODIFIERS =
    ContainerUtil.newLinkedHashSet(
      PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE, STATIC, FINAL, ABSTRACT, SYNCHRONIZED, TRANSIENT, VOLATILE
    );
  private static final @NotNull List<ArrangementSettingsToken> SUPPORTED_ORDERS = List.of(KEEP, BY_NAME);
  private static final @NotNull ArrangementSettingsToken NO_TYPE = new ArrangementSettingsToken("NO_TYPE", "NO_TYPE");
  //NON-NLS not visible in settings


  static class Holder {
    private static final @NotNull Map<ArrangementSettingsToken, Set<ArrangementSettingsToken>> MODIFIERS_BY_TYPE;
    private static final @NotNull Collection<Set<ArrangementSettingsToken>> MUTEXES;

    private static final Set<ArrangementSettingsToken> TYPES_WITH_DISABLED_ORDER;
    private static final Set<ArrangementSettingsToken> TYPES_WITH_DISABLED_NAME_MATCH;
    static {
      Set<ArrangementSettingsToken> visibilityModifiers = Set.of(PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE);
      MUTEXES = List.of(visibilityModifiers, SUPPORTED_TYPES);

      Set<ArrangementSettingsToken> commonModifiers = concat(visibilityModifiers, STATIC, FINAL);

      MODIFIERS_BY_TYPE = Map.ofEntries(
        Map.entry(NO_TYPE, commonModifiers),
        Map.entry(ENUM, visibilityModifiers),
        Map.entry(INTERFACE, visibilityModifiers),
        Map.entry(CLASS, concat(commonModifiers, ABSTRACT)),
        Map.entry(METHOD, concat(commonModifiers, SYNCHRONIZED, ABSTRACT)),
        Map.entry(CONSTRUCTOR, concat(commonModifiers, SYNCHRONIZED)),
        Map.entry(FIELD, concat(commonModifiers, TRANSIENT, VOLATILE)),
        Map.entry(GETTER, Collections.emptySet()),
        Map.entry(SETTER, Collections.emptySet()),
        Map.entry(OVERRIDDEN, Collections.emptySet()),
        Map.entry(INIT_BLOCK, Set.of(STATIC)));

      TYPES_WITH_DISABLED_ORDER = Set.of(INIT_BLOCK);
      TYPES_WITH_DISABLED_NAME_MATCH = Set.of(INIT_BLOCK);
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
      List<ArrangementGroupingRule> groupingRules = List.of(new ArrangementGroupingRule(GETTERS_AND_SETTERS));
      List<StdArrangementMatchRule> matchRules = new ArrayList<>();
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

      List<StdArrangementRuleAliasToken> aliasTokens = new ArrayList<>();
      aliasTokens.add(VISIBILITY);
      DEFAULT_SETTINGS = StdArrangementExtendableSettings.createByMatchRules(groupingRules, matchRules, aliasTokens);
    }
  }


  private static final DefaultArrangementSettingsSerializer SETTINGS_SERIALIZER =
    new DefaultArrangementSettingsSerializer(Holder.DEFAULT_SETTINGS);

  private static @NotNull @Unmodifiable Set<ArrangementSettingsToken> concat(@NotNull Set<? extends ArrangementSettingsToken> base,
                                                                             ArrangementSettingsToken... modifiers) {
    Set<ArrangementSettingsToken> result = new HashSet<>(base);
    Collections.addAll(result, modifiers);
    return Set.of(result.toArray(new ArrangementSettingsToken[0]));
  }

  private static void setupGettersAndSetters(@NotNull JavaArrangementParseInfo info) {
    Collection<JavaArrangementPropertyInfo> properties = info.getProperties();
    for (JavaArrangementPropertyInfo propertyInfo : properties) {
      JavaElementArrangementEntry getter = propertyInfo.getGetter();
      List<JavaElementArrangementEntry> setters = propertyInfo.getSetters();
      if (getter != null) {
        JavaElementArrangementEntry previous = getter;
        for (JavaElementArrangementEntry setter : setters) {
          setter.addDependency(previous);
          previous = setter;
        }
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

  @Override
  public @Nullable Pair<JavaElementArrangementEntry, List<JavaElementArrangementEntry>> parseWithNew(
    @NotNull PsiElement root,
    @Nullable Document document,
    @NotNull Collection<? extends TextRange> ranges,
    @NotNull PsiElement element,
    @NotNull ArrangementSettings settings) {
    JavaArrangementParseInfo existingEntriesInfo = new JavaArrangementParseInfo();
    root.accept(new JavaArrangementVisitor(existingEntriesInfo, document, ranges, settings, false));

    JavaArrangementParseInfo newEntryInfo = new JavaArrangementParseInfo();
    element.accept(new JavaArrangementVisitor(newEntryInfo, document, Collections.singleton(element.getTextRange()), settings, false));
    if (newEntryInfo.getEntries().size() != 1) {
      return null;
    }
    return Pair.create(newEntryInfo.getEntries().get(0), existingEntriesInfo.getEntries());
  }

  @Override
  public @NotNull List<JavaElementArrangementEntry> parse(@NotNull PsiElement root,
                                                          @Nullable Document document,
                                                          @NotNull Collection<? extends TextRange> ranges,
                                                          @NotNull ArrangementSettings settings) {
    // Following entries are subject to arrangement: class, interface, field, method.
    JavaArrangementParseInfo parseInfo = new JavaArrangementParseInfo();
    root.accept(new JavaArrangementVisitor(parseInfo, document, ranges, settings, true));
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

  private static void setupFieldInitializationDependencies(@NotNull List<? extends ArrangementEntryDependencyInfo> fieldDependencyRoots,
                                                           @NotNull ArrangementSettings settings,
                                                           @NotNull JavaArrangementParseInfo parseInfo) {
    Collection<JavaElementArrangementEntry> fields = parseInfo.getFields();
    List<JavaElementArrangementEntry> arrangedFields =
      ArrangementEngine.arrange(fields, settings.getSections(), settings.getRulesSortedByPriority(), null);

    for (ArrangementEntryDependencyInfo root : fieldDependencyRoots) {
      JavaElementArrangementEntry anchorField = root.getAnchorEntry();
      final int anchorEntryIndex = arrangedFields.indexOf(anchorField);

      for (ArrangementEntryDependencyInfo fieldInInitializerInfo : root.getDependentEntriesInfos()) {
        JavaElementArrangementEntry fieldInInitializer = fieldInInitializerInfo.getAnchorEntry();
        if (arrangedFields.indexOf(fieldInInitializer) > anchorEntryIndex ||
            !fieldInInitializerInfo.getDependentEntriesInfos().isEmpty()) {
          anchorField.addDependency(fieldInInitializer);
        }
      }
    }
  }


  @Override
  public int getBlankLines(@NotNull CodeStyleSettings settings,
                           @Nullable JavaElementArrangementEntry parent,
                           @Nullable JavaElementArrangementEntry previous,
                           @NotNull JavaElementArrangementEntry target) {
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
        return target.hasAnnotation() ? javaSettings.BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS : commonSettings.BLANK_LINES_AROUND_FIELD;
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

  @Override
  public @NotNull ArrangementSettingsSerializer getSerializer() {
    return SETTINGS_SERIALIZER;
  }

  @Override
  public @NotNull StdArrangementSettings getDefaultSettings() {
    return Holder.DEFAULT_SETTINGS;
  }

  @Override
  public @Nullable List<CompositeArrangementSettingsToken> getSupportedGroupingTokens() {
    return List.of(
      new CompositeArrangementSettingsToken(GETTERS_AND_SETTERS),
      new CompositeArrangementSettingsToken(OVERRIDDEN_METHODS, BY_NAME, KEEP),
      new CompositeArrangementSettingsToken(DEPENDENT_METHODS, BREADTH_FIRST, DEPTH_FIRST)
    );
  }

  @Override
  public @Nullable List<CompositeArrangementSettingsToken> getSupportedMatchingTokens() {
    return List.of(
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
      return !Holder.TYPES_WITH_DISABLED_ORDER.contains(type);
    }

    if (StdArrangementTokens.Regexp.NAME.equals(token)) {
      return !Holder.TYPES_WITH_DISABLED_NAME_MATCH.contains(type);
    }

    Set<ArrangementSettingsToken> modifiers = Holder.MODIFIERS_BY_TYPE.get(type);
    return modifiers != null && modifiers.contains(token);
  }

  @Override
  public @NotNull ArrangementEntryMatcher buildMatcher(@NotNull ArrangementMatchCondition condition) throws IllegalArgumentException {
    throw new IllegalArgumentException("Can't build a matcher for condition " + condition);
  }

  @Override
  public @NotNull Collection<Set<ArrangementSettingsToken>> getMutexes() {
    return Holder.MUTEXES;
  }

  private static void and(@NotNull List<? super StdArrangementMatchRule> matchRules, ArrangementSettingsToken @NotNull ... conditions) {
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

  @Override
  public @Nullable TextAttributes getTextAttributes(@NotNull EditorColorsScheme scheme, @NotNull ArrangementSettingsToken token, boolean selected) {
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

  private static @Nullable TextAttributes getAttributes(@NotNull EditorColorsScheme scheme, TextAttributesKey @NotNull ... keys) {
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

  @Override
  public @Nullable Color getBorderColor(@NotNull EditorColorsScheme scheme, boolean selected) {
    return null;
  }
}
