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

import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.CompositeArrangementEntryMatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 7/17/12 11:24 AM
 */
public class ArrangementRuleUtil {
  
  private ArrangementRuleUtil() {
  }

  //region Serialization

  @NotNull
  public static List<ArrangementRule> readExternal(@NotNull Element element, @NotNull Language language) {
    final List<ArrangementRule> result = new ArrayList<ArrangementRule>();
    ArrangementRuleSerializer serializer = getSerializer(language);
    for (Object child : element.getChildren()) {
      ArrangementRule rule = serializer.deserialize((Element)child);
      if (rule != null) {
        result.add(rule);
      }
    }
    return result;
  }

  public static void writeExternal(@NotNull Element element, @NotNull List<ArrangementRule> rules, @NotNull Language language) {
    if (rules.isEmpty()) {
      return;
    }

    ArrangementRuleSerializer serializer = getSerializer(language);
    for (ArrangementRule rule : rules) {
      Element e = serializer.serialize(rule);
      if (e != null) {
        element.addContent(e);
      }
    }
  }

  private static ArrangementRuleSerializer getSerializer(@NotNull Language language) {
    Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(language);
    if (rearranger instanceof ArrangementRuleSerializer) {
      return new CompositeArrangementRuleSerializer((ArrangementRuleSerializer)rearranger, DefaultArrangementRuleSerializer.INSTANCE);
    }
    return DefaultArrangementRuleSerializer.INSTANCE;
  }
  
  private static class CompositeArrangementRuleSerializer implements ArrangementRuleSerializer {

    @NotNull private final List<ArrangementRuleSerializer> mySerializers = new ArrayList<ArrangementRuleSerializer>();

    CompositeArrangementRuleSerializer(@NotNull ArrangementRuleSerializer ... serializers) {
      mySerializers.addAll(Arrays.asList(serializers));
    }

    @Nullable
    @Override
    public ArrangementRule deserialize(@NotNull Element element) {
      for (ArrangementRuleSerializer serializer : mySerializers) {
        ArrangementRule rule = serializer.deserialize(element);
        if (rule != null) {
          return rule;
        }
      }
      return null;
    }

    @Nullable
    @Override
    public Element serialize(ArrangementRule rule) {
      for (ArrangementRuleSerializer serializer : mySerializers) {
        Element element = serializer.serialize(rule);
        if (element != null) {
          return element;
        }
      }
      return null;
    }
  }
  //endregion

  //region Matchers composition
  @NotNull
  public static ArrangementEntryMatcher or(@NotNull ArrangementEntryMatcher... matchers) {
    return combine(CompositeArrangementEntryMatcher.Operator.OR, matchers);
  }

  @NotNull
  public static ArrangementEntryMatcher and(@NotNull ArrangementEntryMatcher... matchers) {
    return combine(CompositeArrangementEntryMatcher.Operator.AND, matchers);
  }

  @NotNull
  private static ArrangementEntryMatcher combine(@NotNull CompositeArrangementEntryMatcher.Operator operator,
                                                 @NotNull ArrangementEntryMatcher... matchers)
  {
    CompositeArrangementEntryMatcher composite = null;
    for (ArrangementEntryMatcher matcher : matchers) {
      if (matcher instanceof CompositeArrangementEntryMatcher && ((CompositeArrangementEntryMatcher)(matcher)).getOperator() == operator) {
        composite = (CompositeArrangementEntryMatcher)matcher;
        break;
      }
    }

    if (composite == null) {
      return new CompositeArrangementEntryMatcher(operator, matchers);
    }

    for (ArrangementEntryMatcher matcher : matchers) {
      if (matcher != composite) {
        composite.addMatcher(matcher);
      }
    }
    return composite;
  }
  //endregion
}
