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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.match.DefaultArrangementEntryMatcherSerializer;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    myMixin = mixin;
    myMatcherSerializer = new DefaultArrangementEntryMatcherSerializer(mixin);
    myDefaultSettings = defaultSettings;
  }

  @Override
  public void serialize(@NotNull ArrangementSettings s, @NotNull Element holder) {
    if (!(s instanceof StdArrangementSettings)) {
      return;
    }

    StdArrangementSettings settings = (StdArrangementSettings)s;
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

    List<StdArrangementMatchRule> rules = settings.getRules();
    final boolean isDefaultRules = rules.equals(myDefaultSettings.getRules());
    if (!isDefaultRules) {
      Element rulesElement = new Element(RULES_ELEMENT_NAME);
      holder.addContent(rulesElement);
      for (StdArrangementMatchRule rule : rules) {
        rulesElement.addContent(serialize(rule));
      }
    }
  }

  @Nullable
  @Override
  public ArrangementSettings deserialize(@NotNull Element element) {
    final List<ArrangementGroupingRule> groupingRules = deserializeGropings(element, myDefaultSettings);
    final List<StdArrangementMatchRule> rules = deserializeRules(element, myDefaultSettings);
    return new StdRulePriorityAwareSettings(groupingRules, rules);
  }

  @NotNull
  private List<ArrangementGroupingRule> deserializeGropings(@NotNull Element element, @Nullable ArrangementSettings defaultSettings) {
    Element groups = element.getChild(GROUPS_ELEMENT_NAME);
    if (groups == null) {
      return defaultSettings == null ? ContainerUtil.<ArrangementGroupingRule>newSmartList() : defaultSettings.getGroupings();
    }

    final List<ArrangementGroupingRule> groupings = new ArrayList<ArrangementGroupingRule>();
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
  private List<StdArrangementMatchRule> deserializeRules(@NotNull Element element, @Nullable ArrangementSettings defaultSettings) {
    Element rulesElement = element.getChild(RULES_ELEMENT_NAME);
    if (rulesElement == null) {
      return defaultSettings instanceof StdArrangementSettings ? ((StdArrangementSettings)defaultSettings).getRules() :
             ContainerUtil.<StdArrangementMatchRule>newSmartList();
    }

    final List<StdArrangementMatchRule> rules = new ArrayList<StdArrangementMatchRule>();
    for (Object o : rulesElement.getChildren(RULE_ELEMENT_NAME)) {
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
