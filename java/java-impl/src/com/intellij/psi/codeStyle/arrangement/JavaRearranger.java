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
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementColorsAware;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsAware;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Grouping.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.General.*;

/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:31 PM
 */
public class JavaRearranger implements Rearranger<JavaElementArrangementEntry>, ArrangementStandardSettingsAware, ArrangementColorsAware {

  // Type
  @NotNull private static final Set<ArrangementSettingsToken>                                SUPPORTED_TYPES     =
    ContainerUtilRt.newLinkedHashSet(
      FIELD, CONSTRUCTOR, METHOD, CLASS, INTERFACE, ENUM
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
  }

  private static final Map<ArrangementSettingsToken, List<ArrangementSettingsToken>> GROUPING_RULES = ContainerUtilRt.newLinkedHashMap();

  static {
    GROUPING_RULES.put(GETTERS_AND_SETTERS, Collections.<ArrangementSettingsToken>emptyList());
    GROUPING_RULES.put(OVERRIDDEN_METHODS, ContainerUtilRt.newArrayList(BY_NAME, KEEP));
    GROUPING_RULES.put(DEPENDENT_METHODS, ContainerUtilRt.newArrayList(BREADTH_FIRST, DEPTH_FIRST));
  }

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
      for (JavaArrangementMethodDependencyInfo rootInfo : info.getMethodDependencyRoots()) {
        setupDepthFirstDependency(rootInfo);
      }
    }
    else if (BREADTH_FIRST.equals(orderType)) {
      for (JavaArrangementMethodDependencyInfo rootInfo : info.getMethodDependencyRoots()) {
        setupBreadthFirstDependency(rootInfo);
      }
    }
    else {
      assert false : orderType;
    }
  }

  private static void setupDepthFirstDependency(@NotNull JavaArrangementMethodDependencyInfo info) {
    for (JavaArrangementMethodDependencyInfo dependencyInfo : info.getDependentMethodInfos()) {
      setupDepthFirstDependency(dependencyInfo);
      JavaElementArrangementEntry dependentEntry = dependencyInfo.getAnchorMethod();
      if (dependentEntry.getDependencies() == null) {
        dependentEntry.addDependency(info.getAnchorMethod());
      }
    }
  }

  private static void setupBreadthFirstDependency(@NotNull JavaArrangementMethodDependencyInfo info) {
    Deque<JavaArrangementMethodDependencyInfo> toProcess = new ArrayDeque<JavaArrangementMethodDependencyInfo>();
    toProcess.add(info);
    while (!toProcess.isEmpty()) {
      JavaArrangementMethodDependencyInfo current = toProcess.removeFirst();
      for (JavaArrangementMethodDependencyInfo dependencyInfo : current.getDependentMethodInfos()) {
        JavaElementArrangementEntry dependencyMethod = dependencyInfo.getAnchorMethod();
        if (dependencyMethod.getDependencies() == null) {
          dependencyMethod.addDependency(current.getAnchorMethod());
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
    @Nullable ArrangementSettings settings)
  {
    Set<ArrangementSettingsToken> groupingRules = getGroupingRules(settings);
    JavaArrangementParseInfo existingEntriesInfo = new JavaArrangementParseInfo();
    root.accept(new JavaArrangementVisitor(existingEntriesInfo, document, ranges, groupingRules));

    JavaArrangementParseInfo newEntryInfo = new JavaArrangementParseInfo();
    element.accept(new JavaArrangementVisitor(newEntryInfo, document, Collections.singleton(element.getTextRange()), groupingRules));
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
                                                 @Nullable ArrangementSettings settings)
  {
    // Following entries are subject to arrangement: class, interface, field, method.
    JavaArrangementParseInfo parseInfo = new JavaArrangementParseInfo();
    root.accept(new JavaArrangementVisitor(parseInfo, document, ranges, getGroupingRules(settings)));
    if (settings != null) {
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
    }
    return parseInfo.getEntries();
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
    if (FIELD.equals(target.getType())) {
      if (parent != null && parent.getType() == INTERFACE) {
        return commonSettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE;
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
    else {
      return -1;
    }
  }

  @NotNull
  private static Set<ArrangementSettingsToken> getGroupingRules(@Nullable ArrangementSettings settings) {
    Set<ArrangementSettingsToken> groupingRules = ContainerUtilRt.newHashSet();
    if (settings != null) {
      for (ArrangementGroupingRule rule : settings.getGroupings()) {
        groupingRules.add(rule.getGroupingType());
      }
    }
    return groupingRules;
  }

  @NotNull
  @Override
  public StdArrangementSettings getDefaultSettings() {
    List<ArrangementGroupingRule> groupingRules = ContainerUtilRt.newArrayList(new ArrangementGroupingRule(GETTERS_AND_SETTERS));
    List<StdArrangementMatchRule> matchRules = ContainerUtilRt.newArrayList();
    ArrangementSettingsToken[] visibility = {PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE};
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, STATIC, FINAL, modifier);
    }
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, STATIC, modifier);
    }
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, FINAL, modifier);
    }
    for (ArrangementSettingsToken modifier : visibility) {
      and(matchRules, FIELD, modifier);
    }
    and(matchRules, FIELD);
    and(matchRules, CONSTRUCTOR);
    and(matchRules, METHOD, STATIC);
    and(matchRules, METHOD);
    and(matchRules, ENUM);
    and(matchRules, INTERFACE);
    and(matchRules, CLASS, STATIC);
    and(matchRules, CLASS);

    return new StdRulePriorityAwareSettings(groupingRules, matchRules);
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
    if (SUPPORTED_TYPES.contains(token) || SUPPORTED_ORDERS.contains(token) || StdArrangementTokens.Regexp.NAME.equals(token)) {
      return true;
    }
    ArrangementSettingsToken type = null;
    if (current != null) {
      type = ArrangementUtil.parseType(current);
    }
    if (type == null) {
      type = NO_TYPE;
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
          conditions[0], conditions[0]
        ))));
        return;
      }

      ArrangementCompositeMatchCondition composite = new ArrangementCompositeMatchCondition();
      for (ArrangementSettingsToken condition : conditions) {
        composite.addOperand(new ArrangementAtomMatchCondition(condition, condition));
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
      TextAttributes attributes = scheme.getAttributes(key);
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
