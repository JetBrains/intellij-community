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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementRuleAlias;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementExtendableSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Grouping.OVERRIDDEN_METHODS;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME;
import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 9/18/12 9:24 AM
 */
public class ArrangementSettingsSerializationTest {

  private static Element doSerializationTest(@NotNull StdArrangementSettings settings, @NotNull StdArrangementSettings defaultSettings) {
    Element holder = new Element("holder");
    ArrangementSettingsSerializer instance = new TestArrangementSettingsSerializer(defaultSettings);
    instance.serialize(settings, holder);
    ArrangementSettings restored = instance.deserialize(holder);
    assertEquals(settings, restored);
    return holder;
  }

  private static StdArrangementMatchRule rule(boolean byName, @NotNull ArrangementSettingsToken... tokens) {
    final List<ArrangementAtomMatchCondition> conditions = new ArrayList<ArrangementAtomMatchCondition>();
    for (ArrangementSettingsToken token : tokens) {
      conditions.add(new ArrangementAtomMatchCondition(token));
    }
    final StdArrangementEntryMatcher matcher = new StdArrangementEntryMatcher(new ArrangementCompositeMatchCondition(conditions));
    return byName ? new StdArrangementMatchRule(matcher, BY_NAME) : new StdArrangementMatchRule(matcher);
  }

  private static ArrangementGroupingRule group(@NotNull ArrangementSettingsToken token) {
    return new ArrangementGroupingRule(token);
  }

  private static ArrangementSectionRule section(@Nullable String start, @Nullable String end, StdArrangementMatchRule... rules) {
    return ArrangementSectionRule.create(start == null ? null : start.trim(), end == null ? null : end.trim(), rules);
  }

  private static ArrangementSectionRule section(boolean byName, @NotNull ArrangementSettingsToken... tokens) {
    return section(null, null, rule(byName, tokens));
  }

  private static StdArrangementSettings settings(@NotNull List<ArrangementGroupingRule> groupings,
                                                 @NotNull List<ArrangementSectionRule> sections) {
    return new StdArrangementSettings(groupings, sections);
  }

  private static StdArrangementExtendableSettings extendableSettings(@NotNull List<ArrangementGroupingRule> groupings,
                                                                     @NotNull List<ArrangementSectionRule> sections,
                                                                     @NotNull Collection<ArrangementRuleAlias> tokens) {
    return new StdArrangementExtendableSettings(groupings, sections, tokens);
  }

  private static StdArrangementSettings emptySettings() {
    return new StdArrangementSettings(ContainerUtil.<ArrangementGroupingRule>emptyList(), ContainerUtil.<ArrangementSectionRule>emptyList());
  }

  private static ArrangementSettingsToken customToken(@NotNull String name) {
    return ArrangementUtil.createRuleAliasToken(name, name);
  }

  private static ArrangementRuleAlias visibilityToken() {
    final ArrayList<StdArrangementMatchRule> rules = new ArrayList<StdArrangementMatchRule>();
    rules.add(rule(false, PUBLIC));
    rules.add(rule(false, PROTECTED));
    rules.add(rule(false, PRIVATE));
    return new ArrangementRuleAlias(customToken("visibility"), rules);
  }

  private static ArrangementRuleAlias modifiersToken() {
    final ArrayList<StdArrangementMatchRule> rules = new ArrayList<StdArrangementMatchRule>();
    rules.add(rule(false, PUBLIC, STATIC));
    rules.add(rule(false, PROTECTED, STATIC));
    rules.add(rule(false, PRIVATE, STATIC));
    rules.add(rule(false, PUBLIC));
    rules.add(rule(false, PROTECTED));
    rules.add(rule(false, PRIVATE));
    return new ArrangementRuleAlias(customToken("modifiers"), rules);
  }

  @Test
  public void all() {
    final StdArrangementSettings settings = new StdArrangementSettings();
    settings.addGrouping(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(FIELD);
    settings.addRule(new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), BY_NAME));
    doSerializationTest(settings, new StdArrangementSettings());
  }

  @Test
  public void testDefaultFilter() {
    final StdArrangementSettings settings = new StdArrangementSettings();
    settings.addGrouping(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(FIELD);
    settings.addRule(new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), BY_NAME));

    final Element holder = doSerializationTest(settings, settings);
    assertTrue(holder.getChildren().isEmpty());
  }

  @Test
  public void testDefaultGroupingFilter() {
    final ArrangementGroupingRule groupingRule = new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME);
    final ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(FIELD);
    final StdArrangementMatchRule rule = new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), BY_NAME);

    final StdArrangementSettings settings = new StdArrangementSettings();
    settings.addGrouping(groupingRule);
    settings.addRule(rule);
    final StdArrangementSettings defaultSettings = new StdArrangementSettings();
    defaultSettings.addGrouping(groupingRule);

    final Element holder = doSerializationTest(settings, defaultSettings);
    assertTrue(holder.getChildren().size() == 1);
    assertNull(holder.getChild("groups"));
    assertNotNull(holder.getChild("rules"));
  }

  @Test
  public void testDefaultRulesFilter() {
    final ArrangementGroupingRule groupingRule = new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME);
    final ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(FIELD);
    final StdArrangementMatchRule rule = new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), BY_NAME);

    final StdArrangementSettings settings = new StdArrangementSettings();
    settings.addGrouping(groupingRule);
    settings.addRule(rule);
    final StdArrangementSettings defaultSettings = new StdArrangementSettings();
    defaultSettings.addRule(rule);

    final Element holder = doSerializationTest(settings, defaultSettings);
    assertTrue(holder.getChildren().size() == 1);
    assertNotNull(holder.getChild("groups"));
    assertNull(holder.getChild("rules"));
  }

  @Test
  public void testEmptyGroupings() throws Exception {
    final StdArrangementSettings settings = new StdArrangementSettings();
    final ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(FIELD);
    settings.addRule(new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), BY_NAME));

    final StdArrangementSettings defaultSettings = new StdArrangementSettings();
    defaultSettings.addGrouping(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));

    final Element holder = doSerializationTest(settings, defaultSettings);
    assertTrue(holder.getChildren().size() == 2);
    final Element groups = holder.getChild("groups");
    assertNotNull(groups);
    assertTrue(groups.getChildren().isEmpty());
  }

  @Test
  public void testEmptyRules() throws Exception {
    final StdArrangementSettings settings = new StdArrangementSettings();
    settings.addGrouping(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));

    final StdArrangementSettings defaultSettings = new StdArrangementSettings();
    final ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(FIELD);
    final StdArrangementMatchRule rule = new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), BY_NAME);
    defaultSettings.addRule(rule);

    final Element holder = doSerializationTest(settings, defaultSettings);
    assertTrue(holder.getChildren().size() == 2);
    final Element rules = holder.getChild("rules");
    assertNotNull(rules);
    assertTrue(rules.getChildren().isEmpty());
  }

  @Test
  public void testSimpleSectionSerialize() {
    final StdArrangementSettings settings =
      settings(ContainerUtil.newArrayList(group(OVERRIDDEN_METHODS)), ContainerUtil.newArrayList(section(true, FIELD)));
    final Element holder = doSerializationTest(settings, emptySettings());
    assertTrue(holder.getChildren().size() == 2);
    assertNotNull(holder.getChild("groups"));
    final Element rules = holder.getChild("rules");
    assertNotNull(rules);
    assertTrue(rules.getChildren().size() == 1);
    final Element section = rules.getChild("section");
    assertNotNull(section);
    assertTrue(section.getChildren().size() == 1);
    assertNotNull(section.getChild("rule"));
  }

  @Test
  public void testSectionSerialize() {
    final ArrayList<ArrangementSectionRule> sections =
      ContainerUtil.newArrayList(section("start section", "end section", rule(true, METHOD, PRIVATE), rule(false, METHOD, PUBLIC)),
                                 section("start section", "end section", rule(true, FIELD)));
    final StdArrangementSettings settings =
      settings(ContainerUtil.newArrayList(group(OVERRIDDEN_METHODS)), sections);

    final Element holder = doSerializationTest(settings, emptySettings());
    assertTrue(holder.getChildren().size() == 2);
    assertNotNull(holder.getChild("groups"));
    final Element rules = holder.getChild("rules");
    assertNotNull(rules);
    assertTrue(rules.getChildren().size() == 2);
    final Element section = rules.getChild("section");
    assertEquals(section.getAttribute("start_comment").getValue(), "start section");
    assertEquals(section.getAttribute("end_comment").getValue(), "end section");
  }

  @Test
  public void testDefaultCustomTokenSerialize() {
    final Set<ArrangementRuleAlias> tokens = ContainerUtil.newHashSet(visibilityToken());
    final ArrayList<ArrangementGroupingRule> groupings =
      ContainerUtil.newArrayList(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final ArrayList<ArrangementSectionRule> rules = ContainerUtil.newArrayList(section(true, FIELD));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);

    final Element holder = doSerializationTest(settings, settings.clone());
    assertTrue(holder.getChildren().isEmpty());
  }

  @Test
  public void testCustomTokenSerializeLessThanDefault() {
    final Set<ArrangementRuleAlias> tokens = ContainerUtil.newHashSet(visibilityToken());
    final ArrayList<ArrangementGroupingRule> groupings = ContainerUtil.newArrayList(
      new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final ArrayList<ArrangementSectionRule> rules = ContainerUtil.newArrayList(section(true, FIELD));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);

    final Set<ArrangementRuleAlias> defaultTokens = ContainerUtil.newHashSet(visibilityToken(), modifiersToken());
    final StdArrangementExtendableSettings defaultSettings = extendableSettings(groupings, rules, defaultTokens);

    final Element holder = doSerializationTest(settings, defaultSettings);
    assertTrue(holder.getChildren().size() == 1);
    final Element tokenElement = holder.getChildren().get(0);
    assertEquals(tokenElement.getName(), "tokens");
    assertTrue(tokenElement.getChildren().size() == 1);
    final List<Element> tokenElements = tokenElement.getChildren();
    final Element element = tokenElements.get(0);
    assertEquals(element.getName(), "token");
    assertTrue(StringUtil.equals(element.getAttributeValue("id"), "visibility"));
  }

  @Test
  public void testCustomTokenSerializeMoreThanDefault() {
    final Set<ArrangementRuleAlias> tokens = ContainerUtil.newHashSet(visibilityToken(), modifiersToken());
    final ArrayList<ArrangementGroupingRule> groupings = ContainerUtil.newArrayList(
      new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final ArrayList<ArrangementSectionRule> rules = ContainerUtil.newArrayList(section(true, FIELD));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);

    final Set<ArrangementRuleAlias> defaultTokens = ContainerUtil.newHashSet(visibilityToken());
    final StdArrangementExtendableSettings defaultSettings = extendableSettings(groupings, rules, defaultTokens);

    final Element holder = doSerializationTest(settings, defaultSettings);
    assertTrue(holder.getChildren().size() == 1);
    final Element tokenElement = holder.getChildren().get(0);
    assertEquals(tokenElement.getName(), "tokens");
    assertTrue(tokenElement.getChildren().size() == 2);
    final List<Element> tokenElements = tokenElement.getChildren();
    final Element first = tokenElements.get(0);
    final Element second = tokenElements.get(1);
    assertEquals(first.getName(), "token");
    assertEquals(second.getName(), "token");
    assertTrue(StringUtil.equals(first.getAttributeValue("id"), "visibility") ||
               StringUtil.equals(second.getAttributeValue("id"), "visibility"));
    assertTrue(StringUtil.equals(first.getAttributeValue("id"), "modifiers") ||
               StringUtil.equals(second.getAttributeValue("id"), "modifiers"));
  }

  @Test
  public void testUseCustomTokenSerialize() {
    final Set<ArrangementRuleAlias> tokens = ContainerUtil.newHashSet(visibilityToken(), modifiersToken());
    final ArrayList<ArrangementGroupingRule> groupings = ContainerUtil.newArrayList(
      new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final ArrayList<ArrangementSectionRule> rules = ContainerUtil.newArrayList(section(true, FIELD, customToken("visibility")));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);

    final ArrayList<ArrangementSectionRule> defaultRules = ContainerUtil.newArrayList(section(true, FIELD));
    final StdArrangementExtendableSettings defaultSettings = extendableSettings(groupings, defaultRules, tokens);

    final Element holder = doSerializationTest(settings, defaultSettings);
    assertTrue(holder.getChildren().size() == 1);
    final Element tokenElement = holder.getChildren().get(0);
    assertEquals(tokenElement.getName(), "rules");
    assertTrue(tokenElement.getChildren().size() == 1);
    assertEquals(tokenElement.getChildren().get(0).getName(), "section");
    final Element rule = tokenElement.getChildren().get(0).getChildren().get(0);
    assertEquals(rule.getName(), "rule");
    assertEquals(rule.getChildren().get(0).getName(), "match");
    final Element and = rule.getChildren().get(0).getChildren().get(0);
    assertEquals(and.getName(), "AND");
    assertTrue(StringUtil.equals(and.getChildren().get(0).getName(), "visibility") ||
               StringUtil.equals(and.getChildren().get(1).getName(), "visibility"));
  }

  @Test
  public void testCustomTokenSerializeAndDeserialize() {
    final Set<ArrangementRuleAlias> tokens = ContainerUtil.newHashSet(visibilityToken(), modifiersToken());
    final ArrayList<ArrangementGroupingRule> groupings = ContainerUtil.newArrayList(
      new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final ArrayList<ArrangementSectionRule> rules = ContainerUtil.newArrayList(section(true, FIELD, customToken("visibility")));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);
    final StdArrangementExtendableSettings defaultSettings = new StdArrangementExtendableSettings();
    doSerializationTest(settings, defaultSettings);
  }

  private static class TestArrangementSettingsSerializer extends DefaultArrangementSettingsSerializer {

    public TestArrangementSettingsSerializer(@NotNull StdArrangementSettings defaultSettings) {
      super(defaultSettings);
    }
  }
}
