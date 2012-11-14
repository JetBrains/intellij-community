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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementColorsAware;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementConditionsGrouper;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.*;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.*;

/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:31 PM
 */
public class JavaRearranger implements Rearranger<JavaElementArrangementEntry>, ArrangementStandardSettingsAware,
                                       ArrangementConditionsGrouper, ArrangementColorsAware
{

  // Type
  @NotNull private static final Set<ArrangementEntryType> SUPPORTED_TYPES = EnumSet.of(INTERFACE, CLASS, ENUM, FIELD, METHOD, CONSTRUCTOR);

  // Modifier
  @NotNull private static final Set<ArrangementModifier> SUPPORTED_MODIFIERS = EnumSet.of(
    PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE, STATIC, FINAL, VOLATILE, TRANSIENT, SYNCHRONIZED, ABSTRACT
  );

  @NotNull private static final Object                                NO_TYPE           = new Object();
  @NotNull private static final Map<Object, Set<ArrangementModifier>> MODIFIERS_BY_TYPE = new HashMap<Object, Set<ArrangementModifier>>();
  @NotNull private static final Collection<Set<?>>                    MUTEXES           = new ArrayList<Set<?>>();

  static {
    EnumSet<ArrangementModifier> visibilityModifiers = EnumSet.of(PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE);
    MUTEXES.add(visibilityModifiers);
    MUTEXES.add(SUPPORTED_TYPES);

    Set<ArrangementModifier> commonModifiers = concat(visibilityModifiers, STATIC, FINAL);
    
    MODIFIERS_BY_TYPE.put(NO_TYPE, commonModifiers);
    MODIFIERS_BY_TYPE.put(ENUM, visibilityModifiers);
    MODIFIERS_BY_TYPE.put(INTERFACE, visibilityModifiers);
    MODIFIERS_BY_TYPE.put(CLASS, concat(commonModifiers, ABSTRACT));
    MODIFIERS_BY_TYPE.put(METHOD, concat(commonModifiers, SYNCHRONIZED, ABSTRACT));
    MODIFIERS_BY_TYPE.put(CONSTRUCTOR, concat(commonModifiers, SYNCHRONIZED));
    MODIFIERS_BY_TYPE.put(FIELD, concat(commonModifiers, TRANSIENT, VOLATILE));
  }

  @NotNull private static final List<Set<ArrangementMatchCondition>> UI_GROUPING_RULES = ContainerUtilRt.newArrayList();
  static {
    UI_GROUPING_RULES.add(new HashSet<ArrangementMatchCondition>(
      ContainerUtil.map(
        SUPPORTED_TYPES,
        new Function<ArrangementEntryType, ArrangementMatchCondition>() {
          @Override
          public ArrangementMatchCondition fun(ArrangementEntryType type) {
            return new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, type);
          }
        }
      )
    ));
  }

  private static final Map<ArrangementGroupingType, Set<ArrangementEntryOrderType>> GROUPING_RULES = ContainerUtilRt.newHashMap();
  static {
    GROUPING_RULES.put(ArrangementGroupingType.GETTERS_AND_SETTERS, EnumSet.noneOf(ArrangementEntryOrderType.class));
    GROUPING_RULES.put(ArrangementGroupingType.OVERRIDDEN_METHODS,
                       EnumSet.of(ArrangementEntryOrderType.BY_NAME, ArrangementEntryOrderType.KEEP));
    GROUPING_RULES.put(ArrangementGroupingType.DEPENDENT_METHODS,
                       EnumSet.of(ArrangementEntryOrderType.BREADTH_FIRST, ArrangementEntryOrderType.DEPTH_FIRST));
  }

  private static final List<ArrangementGroupingRule> DEFAULT_GROUPING_RULES = new ArrayList<ArrangementGroupingRule>();
  static {
    DEFAULT_GROUPING_RULES.add(new ArrangementGroupingRule(ArrangementGroupingType.GETTERS_AND_SETTERS));
  }

  private static final List<StdArrangementMatchRule> DEFAULT_MATCH_RULES = new ArrayList<StdArrangementMatchRule>();

  static {
    ArrangementModifier[] visibility = {PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE};
    for (ArrangementModifier modifier : visibility) {
      and(FIELD, STATIC, FINAL, modifier);
    }
    for (ArrangementModifier modifier : visibility) {
      and(FIELD, STATIC, modifier);
    }
    for (ArrangementModifier modifier : visibility) {
      and(FIELD, FINAL, modifier);
    }
    for (ArrangementModifier modifier : visibility) {
      and(FIELD, modifier);
    }
    and(FIELD);
    and(CONSTRUCTOR);
    and(METHOD, STATIC);
    and(METHOD);
    and(ENUM);
    and(INTERFACE);
    and(CLASS, STATIC);
    and(CLASS);
  }

  private static final StdArrangementSettings DEFAULT_SETTINGS = new StdArrangementSettings(DEFAULT_GROUPING_RULES, DEFAULT_MATCH_RULES);

  private static void and(@NotNull Object... conditions) {
    if (conditions.length == 1) {
      DEFAULT_MATCH_RULES.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(new ArrangementAtomMatchCondition(
        ArrangementUtil.parseType(conditions[0]), conditions[0]
      ))));
      return;
    }

    ArrangementCompositeMatchCondition composite = new ArrangementCompositeMatchCondition();
    for (Object condition : conditions) {
      composite.addOperand(new ArrangementAtomMatchCondition(ArrangementUtil.parseType(condition), condition));
    }
    DEFAULT_MATCH_RULES.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(composite)));
  }

  @NotNull
  private static Set<ArrangementModifier> concat(@NotNull Set<ArrangementModifier> base, ArrangementModifier... modifiers) {
    EnumSet<ArrangementModifier> result = EnumSet.copyOf(base);
    Collections.addAll(result, modifiers);
    return result;
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
    Set<ArrangementGroupingType> groupingRules = getGroupingRules(settings);
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
        switch (rule.getGroupingType()) {
          case GETTERS_AND_SETTERS:
            setupGettersAndSetters(parseInfo);
            break;
          case DEPENDENT_METHODS:
            setupUtilityMethods(parseInfo, rule.getOrderType());
            break;
          case OVERRIDDEN_METHODS:
            setupOverriddenMethods(parseInfo);
          default: // Do nothing
        }
      }
    }
    return parseInfo.getEntries();
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

  private static void setupUtilityMethods(@NotNull JavaArrangementParseInfo info, @NotNull ArrangementEntryOrderType orderType) {
    switch (orderType) {
      case DEPTH_FIRST:
        for (JavaArrangementMethodDependencyInfo rootInfo : info.getMethodDependencyRoots()) {
          setupDepthFirstDependency(rootInfo);
        }
        break;
      case BREADTH_FIRST:
        for (JavaArrangementMethodDependencyInfo rootInfo : info.getMethodDependencyRoots()) {
          setupBreadthFirstDependency(rootInfo);
        }
      default: // Unexpected type, do nothing
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
  
  @NotNull
  private static Set<ArrangementGroupingType> getGroupingRules(@Nullable ArrangementSettings settings) {
    Set<ArrangementGroupingType> groupingRules = EnumSet.noneOf(ArrangementGroupingType.class);
    if (settings != null) {
      for (ArrangementGroupingRule rule : settings.getGroupings()) {
        groupingRules.add(rule.getGroupingType());
      }
    }
    return groupingRules;
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
    switch (target.getType()) {
      case FIELD:
        if (parent != null && parent.getType() == INTERFACE) {
          return commonSettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE;
        }
        else {
          return commonSettings.BLANK_LINES_AROUND_FIELD;
        }
      case METHOD:
        if (parent != null && parent.getType() == INTERFACE) {
          return commonSettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE;
        }
        else {
          return commonSettings.BLANK_LINES_AROUND_METHOD;
        }
      default:
        return commonSettings.BLANK_LINES_AROUND_CLASS;
    }
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementEntryType type, @Nullable ArrangementMatchCondition current) {
    return SUPPORTED_TYPES.contains(type);
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementModifier modifier, @Nullable ArrangementMatchCondition current) {
    if (current == null) {
      return SUPPORTED_MODIFIERS.contains(modifier);
    }

    final Ref<Object> typeRef = new Ref<Object>();
    current.invite(new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition setting) {
        if (setting.getType() == ArrangementSettingType.TYPE) {
          typeRef.set(setting.getValue());
        }
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition setting) {
        for (ArrangementMatchCondition n : setting.getOperands()) {
          if (typeRef.get() != null) {
            return;
          }
          n.invite(this);
        }
      }

      @Override
      public void visit(@NotNull ArrangementNameMatchCondition condition) {
      }
    });
    Object key = typeRef.get() == null ? NO_TYPE : typeRef.get();
    Set<ArrangementModifier> modifiers = MODIFIERS_BY_TYPE.get(key);
    return modifiers != null && modifiers.contains(modifier);
  }

  @NotNull
  @Override
  public Collection<Set<?>> getMutexes() {
    return MUTEXES;
  }

  @NotNull
  @Override
  public List<Set<ArrangementMatchCondition>> getGroupingConditions() {
    return Collections.emptyList();
    //return UI_GROUPING_RULES;
  }
  
  @Nullable
  @Override
  public StdArrangementSettings getDefaultSettings() {
    return DEFAULT_SETTINGS;
  }

  @Override
  public boolean isNameFilterSupported() {
    return true;
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementGroupingType groupingType, @Nullable ArrangementEntryOrderType orderType) {
    Set<ArrangementEntryOrderType> orderTypes = GROUPING_RULES.get(groupingType);
    if (orderTypes == null) {
      return false;
    }
    return orderType == null || orderTypes.contains(orderType);
  }

  @Nullable
  @Override
  public TextAttributes getTextAttributes(@NotNull EditorColorsScheme scheme, @NotNull ArrangementSettingType type, boolean selected) {
    if (selected) {
      TextAttributes attributes = new TextAttributes();
      attributes.setForegroundColor(scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
      attributes.setBackgroundColor(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
      return attributes;
    }
    if (type == ArrangementSettingType.MODIFIER) {
      return getAttributes(scheme, SyntaxHighlighterColors.KEYWORD);
    }
    else if (type == ArrangementSettingType.TYPE) {
      return getAttributes(scheme, CodeInsightColors.CLASS_NAME_ATTRIBUTES, CodeInsightColors.INTERFACE_NAME_ATTRIBUTES);
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
