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

import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Grouping.OVERRIDDEN_METHODS;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PRIVATE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC;
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

  private static StdArrangementSettings sectionSettings(@NotNull List<ArrangementGroupingRule> groupings,
                                                        @NotNull List<ArrangementSectionRule> sections) {
    return new StdArrangementSettings(groupings, sections);
  }

  private static StdArrangementSettings emptySettings() {
    return new StdArrangementSettings(ContainerUtil.<ArrangementGroupingRule>emptyList(), ContainerUtil.<ArrangementSectionRule>emptyList());
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
      sectionSettings(ContainerUtil.newArrayList(group(OVERRIDDEN_METHODS)), ContainerUtil.newArrayList(section(true, FIELD)));
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
      sectionSettings(ContainerUtil.newArrayList(group(OVERRIDDEN_METHODS)), sections);

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

  private static class TestArrangementSettingsSerializer extends DefaultArrangementSettingsSerializer {

    public TestArrangementSettingsSerializer(@NotNull StdArrangementSettings defaultSettings) {
      super(defaultSettings);
    }
  }
}
