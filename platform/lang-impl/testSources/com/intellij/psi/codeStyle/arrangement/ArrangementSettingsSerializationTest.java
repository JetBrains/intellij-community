// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementExtendableSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementRuleAliasToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.IOException;
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

public class ArrangementSettingsSerializationTest {
  private static final String VISIBILITY = "visibility";
  private static final String MODIFIERS = "modifiers";

  private static Element doSerializationTest(@NotNull StdArrangementSettings settings, @NotNull StdArrangementSettings defaultSettings) {
    Element holder = new Element("holder");
    ArrangementSettingsSerializer instance = new TestArrangementSettingsSerializer(defaultSettings);
    instance.serialize(settings, holder);
    ArrangementSettings restored = instance.deserialize(holder);
    assertEquals(settings, restored);
    return holder;
  }

  private static StdArrangementMatchRule rule(boolean byName, ArrangementSettingsToken @NotNull ... tokens) {
    final List<ArrangementAtomMatchCondition> conditions = new ArrayList<>();
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

  private static ArrangementSectionRule section(boolean byName, ArrangementSettingsToken @NotNull ... tokens) {
    return section(null, null, rule(byName, tokens));
  }

  private static StdArrangementSettings settings(@NotNull List<? extends ArrangementGroupingRule> groupings,
                                                 @NotNull List<? extends ArrangementSectionRule> sections) {
    return new StdArrangementSettings(groupings, sections);
  }

  private static StdArrangementExtendableSettings extendableSettings(@NotNull List<? extends ArrangementGroupingRule> groupings,
                                                                     @NotNull List<? extends ArrangementSectionRule> sections,
                                                                     @NotNull Collection<? extends StdArrangementRuleAliasToken> tokens) {
    return new StdArrangementExtendableSettings(groupings, sections, tokens);
  }

  private static StdArrangementSettings emptySettings() {
    return new StdArrangementSettings(ContainerUtil.emptyList(), ContainerUtil.emptyList());
  }

  private static StdArrangementRuleAliasToken visibilityToken() {
    final ArrayList<StdArrangementMatchRule> rules = new ArrayList<>();
    rules.add(rule(false, PUBLIC));
    rules.add(rule(false, PROTECTED));
    rules.add(rule(false, PRIVATE));
    return new StdArrangementRuleAliasToken(VISIBILITY, VISIBILITY, rules);
  }

  private static StdArrangementRuleAliasToken modifiersToken() {
    final ArrayList<StdArrangementMatchRule> rules = new ArrayList<>();
    rules.add(rule(false, PUBLIC, STATIC));
    rules.add(rule(false, PROTECTED, STATIC));
    rules.add(rule(false, PRIVATE, STATIC));
    rules.add(rule(false, PUBLIC));
    rules.add(rule(false, PROTECTED));
    rules.add(rule(false, PRIVATE));
    return new StdArrangementRuleAliasToken(MODIFIERS, MODIFIERS, rules);
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
    assertEquals(1, holder.getChildren().size());
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
    assertEquals(1, holder.getChildren().size());
    assertNotNull(holder.getChild("groups"));
    assertNull(holder.getChild("rules"));
  }

  @Test
  public void testEmptyGroupings() {
    final StdArrangementSettings settings = new StdArrangementSettings();
    final ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(FIELD);
    settings.addRule(new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), BY_NAME));

    final StdArrangementSettings defaultSettings = new StdArrangementSettings();
    defaultSettings.addGrouping(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));

    final Element holder = doSerializationTest(settings, defaultSettings);
    assertEquals(2, holder.getChildren().size());
    final Element groups = holder.getChild("groups");
    assertNotNull(groups);
    assertTrue(groups.getChildren().isEmpty());
  }

  @Test
  public void testEmptyRules() {
    final StdArrangementSettings settings = new StdArrangementSettings();
    settings.addGrouping(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));

    final StdArrangementSettings defaultSettings = new StdArrangementSettings();
    final ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(FIELD);
    final StdArrangementMatchRule rule = new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), BY_NAME);
    defaultSettings.addRule(rule);

    final Element holder = doSerializationTest(settings, defaultSettings);
    assertEquals(2, holder.getChildren().size());
    final Element rules = holder.getChild("rules");
    assertNotNull(rules);
    assertTrue(rules.getChildren().isEmpty());
  }

  @Test
  public void testSimpleSectionSerialize() {
    final StdArrangementSettings settings =
      settings(List.of(group(OVERRIDDEN_METHODS)), List.of(section(true, FIELD)));
    final Element holder = doSerializationTest(settings, emptySettings());
    assertEquals(2, holder.getChildren().size());
    assertNotNull(holder.getChild("groups"));
    final Element rules = holder.getChild("rules");
    assertNotNull(rules);
    assertEquals(1, rules.getChildren().size());
    final Element section = rules.getChild("section");
    assertNotNull(section);
    assertEquals(1, section.getChildren().size());
    assertNotNull(section.getChild("rule"));
  }

  @Test
  public void testSectionSerialize() {
    final List<ArrangementSectionRule> sections =
      List.of(section("start section", "end section", rule(true, METHOD, PRIVATE), rule(false, METHOD, PUBLIC)),
              section("start section", "end section", rule(true, FIELD)));
    final StdArrangementSettings settings =
      settings(List.of(group(OVERRIDDEN_METHODS)), sections);

    final Element holder = doSerializationTest(settings, emptySettings());
    assertEquals(2, holder.getChildren().size());
    assertNotNull(holder.getChild("groups"));
    final Element rules = holder.getChild("rules");
    assertNotNull(rules);
    assertEquals(2, rules.getChildren().size());
    final Element section = rules.getChild("section");
    assertEquals(section.getAttribute("start_comment").getValue(), "start section");
    assertEquals(section.getAttribute("end_comment").getValue(), "end section");
  }

  @Test
  public void testDefaultCustomTokenSerialize() {
    final Set<StdArrangementRuleAliasToken> tokens = ContainerUtil.newHashSet(visibilityToken());
    final List<ArrangementGroupingRule> groupings =
      List.of(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final List<ArrangementSectionRule> rules = List.of(section(true, FIELD));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);

    final Element holder = doSerializationTest(settings, settings.clone());
    assertTrue(holder.getChildren().isEmpty());
  }

  @Test
  public void testCustomTokenSerializeLessThanDefault() throws IOException {
    final Set<StdArrangementRuleAliasToken> tokens = ContainerUtil.newHashSet(visibilityToken());
    final List<ArrangementGroupingRule> groupings = List.of(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final List<ArrangementSectionRule> rules = List.of(section(true, FIELD));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);

    final Set<StdArrangementRuleAliasToken> defaultTokens = ContainerUtil.newHashSet(visibilityToken(), modifiersToken());
    final StdArrangementExtendableSettings defaultSettings = extendableSettings(groupings, rules, defaultTokens);

    final Element holder = doSerializationTest(settings, defaultSettings);
    final String expected = """
      <holder>
        <tokens>
          <token id="visibility" name="visibility">
            <rules>
              <rule>
                <match>
                  <AND>
                    <PUBLIC>true</PUBLIC>
                  </AND>
                </match>
              </rule>
              <rule>
                <match>
                  <AND>
                    <PROTECTED>true</PROTECTED>
                  </AND>
                </match>
              </rule>
              <rule>
                <match>
                  <AND>
                    <PRIVATE>true</PRIVATE>
                  </AND>
                </match>
              </rule>
            </rules>
          </token>
        </tokens>
      </holder>""";
    assertXmlOutputEquals(expected, holder);
  }

  @Test
  public void testCustomTokenSerializeMoreThanDefault() throws IOException {
    final Set<StdArrangementRuleAliasToken> tokens = ContainerUtil.newHashSet(visibilityToken(), modifiersToken());
    final List<ArrangementGroupingRule> groupings = List.of(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final List<ArrangementSectionRule> rules = List.of(section(true, FIELD));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);

    final Set<StdArrangementRuleAliasToken> defaultTokens = ContainerUtil.newHashSet(visibilityToken());
    final StdArrangementExtendableSettings defaultSettings = extendableSettings(groupings, rules, defaultTokens);

    final Element holder = doSerializationTest(settings, defaultSettings);
    final String expected = """
      <holder>
        <tokens>
          <token id="modifiers" name="modifiers">
            <rules>
              <rule>
                <match>
                  <AND>
                    <PUBLIC>true</PUBLIC>
                    <STATIC>true</STATIC>
                  </AND>
                </match>
              </rule>
              <rule>
                <match>
                  <AND>
                    <PROTECTED>true</PROTECTED>
                    <STATIC>true</STATIC>
                  </AND>
                </match>
              </rule>
              <rule>
                <match>
                  <AND>
                    <PRIVATE>true</PRIVATE>
                    <STATIC>true</STATIC>
                  </AND>
                </match>
              </rule>
              <rule>
                <match>
                  <AND>
                    <PUBLIC>true</PUBLIC>
                  </AND>
                </match>
              </rule>
              <rule>
                <match>
                  <AND>
                    <PROTECTED>true</PROTECTED>
                  </AND>
                </match>
              </rule>
              <rule>
                <match>
                  <AND>
                    <PRIVATE>true</PRIVATE>
                  </AND>
                </match>
              </rule>
            </rules>
          </token>
          <token id="visibility" name="visibility">
            <rules>
              <rule>
                <match>
                  <AND>
                    <PUBLIC>true</PUBLIC>
                  </AND>
                </match>
              </rule>
              <rule>
                <match>
                  <AND>
                    <PROTECTED>true</PROTECTED>
                  </AND>
                </match>
              </rule>
              <rule>
                <match>
                  <AND>
                    <PRIVATE>true</PRIVATE>
                  </AND>
                </match>
              </rule>
            </rules>
          </token>
        </tokens>
      </holder>""";
    assertXmlOutputEquals(expected, holder);
  }

  @Test
  public void testUseCustomTokenSerialize() throws IOException {
    final StdArrangementRuleAliasToken visibility = visibilityToken();
    final StdArrangementRuleAliasToken modifiers = modifiersToken();
    final Set<StdArrangementRuleAliasToken> tokens = ContainerUtil.newHashSet(visibility, modifiers);
    final List<ArrangementGroupingRule> groupings = List.of(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    final List<ArrangementSectionRule> rules = List.of(section(true, FIELD, visibility));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);

    List<ArrangementSectionRule> defaultRules = List.of(section(true, FIELD));
    final StdArrangementExtendableSettings defaultSettings = extendableSettings(groupings, defaultRules, tokens);

    final Element holder = doSerializationTest(settings, defaultSettings);
    final String expected = """
      <holder>
        <rules>
          <section>
            <rule>
              <match>
                <AND>
                  <FIELD>true</FIELD>
                  <visibility />
                </AND>
              </match>
              <order>BY_NAME</order>
            </rule>
          </section>
        </rules>
      </holder>""";
    assertXmlOutputEquals(expected, holder);
  }

  @Test
  public void testCustomTokenSerializeAndDeserialize() {
    final StdArrangementRuleAliasToken visibility = visibilityToken();
    final StdArrangementRuleAliasToken modifiers = modifiersToken();
    final Set<StdArrangementRuleAliasToken> tokens = ContainerUtil.newHashSet(visibility, modifiers);
    List<ArrangementGroupingRule> groupings = List.of(new ArrangementGroupingRule(OVERRIDDEN_METHODS, BY_NAME));
    List<ArrangementSectionRule> rules = List.of(section(true, FIELD, visibility));
    final StdArrangementExtendableSettings settings = extendableSettings(groupings, rules, tokens);
    final StdArrangementExtendableSettings defaultSettings = new StdArrangementExtendableSettings();
    doSerializationTest(settings, defaultSettings);
  }

  private static final class TestArrangementSettingsSerializer extends DefaultArrangementSettingsSerializer {
    private TestArrangementSettingsSerializer(@NotNull StdArrangementSettings defaultSettings) {
      super(defaultSettings);
    }
  }

  private static void assertXmlOutputEquals(String expected, Element root) {
    assertEquals(expected, JDOMUtil.write(root));
  }
}
