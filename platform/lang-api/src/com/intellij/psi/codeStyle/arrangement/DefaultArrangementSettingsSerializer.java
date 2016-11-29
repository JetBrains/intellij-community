/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.*;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link ArrangementSettingsSerializer} which knows how to handle {@link StdArrangementSettings built-in arrangement tokens}
 * and {@link Mixin can be used as a base for custom serializer implementation}.
 * 
 * @author Denis Zhdanov
 * @since 7/18/12 10:37 AM
 */
public class DefaultArrangementSettingsSerializer implements ArrangementSettingsSerializer {
  private static final Logger LOG = Logger.getInstance("#" + DefaultArrangementSettingsSerializer.class.getName());

  @NotNull @NonNls private static final String GROUPS_ELEMENT_NAME     = "groups";
  @NotNull @NonNls private static final String GROUP_ELEMENT_NAME      = "group";
  @NotNull @NonNls private static final String RULES_ELEMENT_NAME      = "rules";
  @NotNull @NonNls private static final String TOKENS_ELEMENT_NAME     = "tokens";
  @NotNull @NonNls private static final String TOKEN_ELEMENT_NAME      = "token";
  @NotNull @NonNls private static final String TOKEN_ID                = "id";
  @NotNull @NonNls private static final String TOKEN_NAME              = "name";
  @NotNull @NonNls private static final String SECTION_ELEMENT_NAME    = "section";
  @NotNull @NonNls private static final String SECTION_START_ATTRIBUTE = "start_comment";
  @NotNull @NonNls private static final String SECTION_END_ATTRIBUTE   = "end_comment";
  @NotNull @NonNls private static final String RULE_ELEMENT_NAME       = "rule";
  @NotNull @NonNls private static final String TYPE_ELEMENT_NAME       = "type";
  @NotNull @NonNls private static final String MATCHER_ELEMENT_NAME    = "match";
  @NotNull @NonNls private static final String ORDER_TYPE_ELEMENT_NAME = "order";

  @NotNull private final DefaultArrangementEntryMatcherSerializer myMatcherSerializer;
  @NotNull private final Mixin                                    myMixin;
  @NotNull private final ArrangementSettings                      myDefaultSettings;

  public DefaultArrangementSettingsSerializer(@NotNull StdArrangementSettings defaultSettings) {
    this(Mixin.NULL, defaultSettings);
  }

  public DefaultArrangementSettingsSerializer(@NotNull Mixin mixin, @NotNull StdArrangementSettings defaultSettings) {
    myMixin = new MutableMixin(mixin);
    myMatcherSerializer = new DefaultArrangementEntryMatcherSerializer(myMixin);
    myDefaultSettings = defaultSettings;
  }

  @Override
  public void serialize(@NotNull ArrangementSettings s, @NotNull Element holder) {
    if (!(s instanceof StdArrangementSettings)) {
      return;
    }

    StdArrangementSettings settings = (StdArrangementSettings)s;
    if (settings instanceof ArrangementExtendableSettings && myDefaultSettings instanceof ArrangementExtendableSettings) {
      final Set<StdArrangementRuleAliasToken> tokensDefinition = ((ArrangementExtendableSettings)settings).getRuleAliases();
      final boolean isDefault = tokensDefinition.equals(((ArrangementExtendableSettings)myDefaultSettings).getRuleAliases());
      if (!isDefault) {
        final Element tokensElement = new Element(TOKENS_ELEMENT_NAME);
        for (StdArrangementRuleAliasToken definition : tokensDefinition) {
          final Element tokenElement = new Element(TOKEN_ELEMENT_NAME);
          tokenElement.setAttribute(TOKEN_ID, definition.getId());
          tokenElement.setAttribute(TOKEN_NAME, definition.getName());

          final Element rulesElement = new Element(RULES_ELEMENT_NAME);
          for (StdArrangementMatchRule rule : definition.getDefinitionRules()) {
            rulesElement.addContent(serialize(rule));
          }
          tokenElement.addContent(rulesElement);
          tokensElement.addContent(tokenElement);
        }
        holder.addContent(tokensElement);
      }
    }

    List<ArrangementGroupingRule> groupings = settings.getGroupings();
    final boolean isDefaultGroupings = groupings.equals(myDefaultSettings.getGroupings());
    if (!isDefaultGroupings) {
      Element groupingsElement = new Element(GROUPS_ELEMENT_NAME);
      holder.addContent(groupingsElement);
      for (ArrangementGroupingRule group : groupings) {
        Element groupElement = new Element(GROUP_ELEMENT_NAME);
        groupingsElement.addContent(groupElement);
        groupElement.addContent(new Element(TYPE_ELEMENT_NAME).setText(group.getGroupingType().getId()));
        groupElement.addContent(new Element(ORDER_TYPE_ELEMENT_NAME).setText(group.getOrderType().getId()));
      }
    }

    final List<ArrangementSectionRule> sections = settings.getSections();
    final boolean isDefaultRules = sections.equals((myDefaultSettings).getSections());
    if (!isDefaultRules) {
      Element rulesElement = new Element(RULES_ELEMENT_NAME);
      holder.addContent(rulesElement);
      for (ArrangementSectionRule section : sections) {
        rulesElement.addContent(serialize(section));
      }
    }
  }

  @Nullable
  @Override
  public ArrangementSettings deserialize(@NotNull Element element) {
    final Set<StdArrangementRuleAliasToken> tokensDefinition = deserializeTokensDefinition(element, myDefaultSettings);
    final List<ArrangementGroupingRule> groupingRules = deserializeGropings(element, myDefaultSettings);
    final Element rulesElement = element.getChild(RULES_ELEMENT_NAME);
    final List<ArrangementSectionRule> sectionRules = ContainerUtil.newArrayList();
    if(rulesElement == null) {
      sectionRules.addAll(myDefaultSettings.getSections());
    }
    else {
      sectionRules.addAll(deserializeSectionRules(rulesElement, tokensDefinition));
      if (sectionRules.isEmpty()) {
        // for backward compatibility
        final List<StdArrangementMatchRule> rules = deserializeRules(rulesElement, tokensDefinition);
        return StdArrangementSettings.createByMatchRules(groupingRules, rules);
      }
    }

    if (tokensDefinition == null) {
      return new StdArrangementSettings(groupingRules, sectionRules);
    }
    return new StdArrangementExtendableSettings(groupingRules, sectionRules, tokensDefinition);
  }

  @Nullable
  private Set<StdArrangementRuleAliasToken> deserializeTokensDefinition(@NotNull Element element, @NotNull ArrangementSettings defaultSettings) {
    if (!(defaultSettings instanceof ArrangementExtendableSettings)) {
      return null;
    }

    final Element tokensRoot = element.getChild(TOKENS_ELEMENT_NAME);
    if (tokensRoot == null) {
      return ((ArrangementExtendableSettings)myDefaultSettings).getRuleAliases();
    }

    final Set<StdArrangementRuleAliasToken> tokenDefinitions = new THashSet<>();
    final List<Element> tokens = tokensRoot.getChildren(TOKEN_ELEMENT_NAME);
    for (Element token : tokens) {
      final Attribute id = token.getAttribute(TOKEN_ID);
      final Attribute name = token.getAttribute(TOKEN_NAME);
      assert id != null && name != null : "Can not find id for token: " + token;
      final Element rules = token.getChild(RULES_ELEMENT_NAME);
      final List<StdArrangementMatchRule> tokenRules =
        rules == null ? ContainerUtil.<StdArrangementMatchRule>emptyList() : deserializeRules(rules, null);
      tokenDefinitions.add(new StdArrangementRuleAliasToken(id.getValue(), name.getValue(), tokenRules));
    }
    return tokenDefinitions;
  }

  @NotNull
  private List<ArrangementGroupingRule> deserializeGropings(@NotNull Element element, @Nullable ArrangementSettings defaultSettings) {
    Element groups = element.getChild(GROUPS_ELEMENT_NAME);
    if (groups == null) {
      return defaultSettings == null ? ContainerUtil.<ArrangementGroupingRule>newSmartList() : defaultSettings.getGroupings();
    }

    final List<ArrangementGroupingRule> groupings = new ArrayList<>();
    for (Object group : groups.getChildren(GROUP_ELEMENT_NAME)) {
      Element groupElement = (Element)group;

      // Grouping type.
      String groupingTypeId = groupElement.getChildText(TYPE_ELEMENT_NAME);
      ArrangementSettingsToken groupingType = StdArrangementTokens.byId(groupingTypeId);
      if (groupingType == null) {
        groupingType = myMixin.deserializeToken(groupingTypeId);
      }
      if (groupingType == null) {
        LOG.warn(String.format("Can't deserialize grouping type token by id '%s'", groupingTypeId));
        continue;
      }

      // Order type.
      String orderTypeId = groupElement.getChildText(ORDER_TYPE_ELEMENT_NAME);
      ArrangementSettingsToken orderType = StdArrangementTokens.byId(orderTypeId);
      if (orderType == null) {
        orderType = myMixin.deserializeToken(orderTypeId);
      }
      if (orderType == null) {
        LOG.warn(String.format("Can't deserialize grouping order type token by id '%s'", orderTypeId));
        continue;
      }
      groupings.add(new ArrangementGroupingRule(groupingType, orderType));
    }
    return groupings;
  }

  @NotNull
  private List<ArrangementSectionRule> deserializeSectionRules(@NotNull Element rulesElement,
                                                               @Nullable Set<StdArrangementRuleAliasToken> tokens) {
    final List<ArrangementSectionRule> sectionRules = new ArrayList<>();
    for (Object o : rulesElement.getChildren(SECTION_ELEMENT_NAME)) {
      final Element sectionElement = (Element)o;
      final List<StdArrangementMatchRule> rules = deserializeRules(sectionElement, tokens);
      final Attribute start = sectionElement.getAttribute(SECTION_START_ATTRIBUTE);
      final String startComment = start != null ? start.getValue().trim() : null;
      final Attribute end = sectionElement.getAttribute(SECTION_END_ATTRIBUTE);
      final String endComment = end != null ? end.getValue().trim() : null;
      sectionRules.add(ArrangementSectionRule.create(startComment, endComment, rules));
    }
    return sectionRules;
  }

  @NotNull
  private List<StdArrangementMatchRule> deserializeRules(@NotNull Element element, @Nullable final Set<StdArrangementRuleAliasToken> aliases) {
    if (aliases != null && myMixin instanceof MutableMixin) {
      ((MutableMixin)myMixin).setMyRuleAliases(aliases);
    }
    final List<StdArrangementMatchRule> rules = new ArrayList<>();
    for (Object o : element.getChildren(RULE_ELEMENT_NAME)) {
      Element ruleElement = (Element)o;
      Element matcherElement = ruleElement.getChild(MATCHER_ELEMENT_NAME);
      if (matcherElement == null) {
        continue;
      }

      StdArrangementEntryMatcher matcher = null;
      for (Object c : matcherElement.getChildren()) {
        matcher = myMatcherSerializer.deserialize((Element)c);
        if (matcher != null) {
          break;
        }
      }

      if (matcher == null) {
        return ContainerUtil.newSmartList();
      }

      Element orderTypeElement = ruleElement.getChild(ORDER_TYPE_ELEMENT_NAME);
      ArrangementSettingsToken orderType = null;
      if (orderTypeElement != null) {
        String orderTypeId = orderTypeElement.getText();
        orderType = StdArrangementTokens.byId(orderTypeId);
        if (orderType == null) {
          orderType = myMixin.deserializeToken(orderTypeId);
        }
        if (orderType == null) {
          LOG.warn(String.format("Can't deserialize matching rule order type for id '%s'. Falling back to default (%s)",
                                 orderTypeId, ArrangementMatchRule.DEFAULT_ORDER_TYPE.getId()));
        }
      }
      if (orderType == null) {
        orderType = ArrangementMatchRule.DEFAULT_ORDER_TYPE;
      }
      rules.add(new StdArrangementMatchRule(matcher, orderType));
    }
    return rules;
  }

  @Nullable
  public Element serialize(@NotNull ArrangementMatchRule rule) {
    Element matcherElement = myMatcherSerializer.serialize(rule.getMatcher());
    if (matcherElement == null) {
      return null;
    }
    
    Element result = new Element(RULE_ELEMENT_NAME);
    result.addContent(new Element(MATCHER_ELEMENT_NAME).addContent(matcherElement));
    if (rule.getOrderType() != ArrangementMatchRule.DEFAULT_ORDER_TYPE) {
      result.addContent(new Element(ORDER_TYPE_ELEMENT_NAME).setText(rule.getOrderType().getId()));
    }
    return result;
  }

  @Nullable
  public Element serialize(@NotNull ArrangementSectionRule section) {
    final Element sectionElement = new Element(SECTION_ELEMENT_NAME);
    if (StringUtil.isNotEmpty(section.getStartComment())) {
      // or only != null ?
      sectionElement.setAttribute(SECTION_START_ATTRIBUTE, section.getStartComment());
    }
    if (StringUtil.isNotEmpty(section.getEndComment())) {
      sectionElement.setAttribute(SECTION_END_ATTRIBUTE, section.getEndComment());
    }

    //TODO: serialize start & end comment as rule?
    final List<StdArrangementMatchRule> rules = section.getMatchRules();
    for (int i = 0; i < rules.size(); i++) {
      StdArrangementMatchRule rule = rules.get(i);
      if ((i != 0 || StringUtil.isEmpty(section.getStartComment())) &&
          (i != rules.size() - 1 || StringUtil.isEmpty(section.getEndComment()))) {
        sectionElement.addContent(serialize(rule));
      }
    }
    return sectionElement;
   }

  public static class MutableMixin implements Mixin {
    private final Mixin myDelegate;
    private Set<StdArrangementRuleAliasToken> myRuleAliases;

    public MutableMixin(Mixin delegate) {
      myDelegate = delegate;
    }

    public void setMyRuleAliases(Set<StdArrangementRuleAliasToken> aliases) {
      myRuleAliases = aliases;
    }

    @Nullable
    @Override
    public ArrangementSettingsToken deserializeToken(@NotNull String id) {
      final ArrangementSettingsToken token = myDelegate.deserializeToken(id);
      if (token != null || myRuleAliases == null) {
        return token;
      }

      for (StdArrangementRuleAliasToken alias : myRuleAliases) {
        if (StringUtil.equals(alias.getId(), id)) {
          return alias;
        }
      }
      return null;
    }
  }
  
  public interface Mixin {

    Mixin NULL = new Mixin() {
      @Nullable
      @Override
      public ArrangementSettingsToken deserializeToken(@NotNull String id) { return null; }
    };

    @Nullable
    ArrangementSettingsToken deserializeToken(@NotNull String id);
  }
}
