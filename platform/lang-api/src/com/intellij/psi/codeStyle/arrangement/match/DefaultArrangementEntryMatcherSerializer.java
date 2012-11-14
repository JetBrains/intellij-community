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
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 7/19/12 1:00 PM
 */
public class DefaultArrangementEntryMatcherSerializer {

  private static final Comparator<ArrangementMatchCondition> CONDITION_COMPARATOR = new Comparator<ArrangementMatchCondition>() {

    private final TObjectIntHashMap<Object> WEIGHTS = new TObjectIntHashMap<Object>();

    {
      int weight = 0;
      for (ArrangementEntryType entryType : ArrangementEntryType.values()) {
        WEIGHTS.put(entryType, weight++);
      }
      for (ArrangementModifier modifier : ArrangementModifier.values()) {
        WEIGHTS.put(modifier, weight++);
      }
    }

    @Override
    public int compare(ArrangementMatchCondition c1, ArrangementMatchCondition c2) {
      boolean isAtom1 = c1 instanceof ArrangementAtomMatchCondition;
      boolean isAtom2 = c2 instanceof ArrangementAtomMatchCondition;
      if (isAtom1 ^ isAtom2) {
        return isAtom1 ? 1 : -1; // Composite conditions before atom conditions.
      }
      else if (!isAtom1 && !isAtom2) {
        return 0;
      }

      ArrangementAtomMatchCondition atom1 = (ArrangementAtomMatchCondition)c1;
      ArrangementAtomMatchCondition atom2 = (ArrangementAtomMatchCondition)c2;
      if (WEIGHTS.containsKey(atom1.getValue()) && WEIGHTS.containsKey(atom2.getValue())) {
        return WEIGHTS.get(atom1.getValue()) - WEIGHTS.get(atom2.getValue());
      }
      else {
        return 0;
      }
    }
  };

  private static final Logger LOG = Logger.getInstance("#" + DefaultArrangementEntryMatcherSerializer.class.getName());

  @NotNull private static final String COMPOSITE_CONDITION_NAME = "AND";
  private static final Set<String> ATOM_SETTINGS_TYPES = new HashSet<String>();

  static {
    for (ArrangementSettingType type : ArrangementSettingType.values()) {
      ATOM_SETTINGS_TYPES.add(type.toString());
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public <T extends ArrangementEntryMatcher> Element serialize(@NotNull T matcher) {
    if (matcher instanceof StdArrangementEntryMatcher) {
      return serialize(((StdArrangementEntryMatcher)matcher).getCondition());
    }
    LOG.warn(String.format(
      "Can't serialize arrangement entry matcher of class '%s'. Reason: expected to find '%s' instance instead",
      matcher.getClass(), StdArrangementEntryMatcher.class
    ));
    return null;
  }

  @NotNull
  private static Element serialize(@NotNull ArrangementMatchCondition condition) {
    MySerializationVisitor visitor = new MySerializationVisitor();
    condition.invite(visitor);
    return visitor.result;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public StdArrangementEntryMatcher deserialize(@NotNull Element matcherElement) {
    ArrangementMatchCondition condition = deserializeCondition(matcherElement);
    return condition == null ? null : new StdArrangementEntryMatcher(condition);
  }

  @Nullable
  private static ArrangementMatchCondition deserializeCondition(@NotNull Element matcherElement) {
    String name = matcherElement.getName();
    if (!COMPOSITE_CONDITION_NAME.equals(name)) {
      if (ATOM_SETTINGS_TYPES.contains(name)) {
        return deserializeAtomCondition(matcherElement);
      }
      else {
        LOG.warn(String.format(
          "Can't deserialize an arrangement entry matcher from matchElement with name '%s'. Reason: only the following elements"
          + "are supported: %s and %s",
          name, COMPOSITE_CONDITION_NAME, ATOM_SETTINGS_TYPES
        ));
        return null;
      }
    }
    else {
      ArrangementCompositeMatchCondition composite = new ArrangementCompositeMatchCondition();
      for (Object child : matcherElement.getChildren()) {
        ArrangementMatchCondition deserialised = deserializeCondition((Element)child);
        if (deserialised != null) {
          composite.addOperand(deserialised);
        }
      }
      return composite;
    }
  }

  @Nullable
  private static ArrangementMatchCondition deserializeAtomCondition(@NotNull Element matcherElement) {
    ArrangementSettingType settingType = ArrangementSettingType.valueOf(matcherElement.getName());
    Object value;
    switch (settingType) {
      case TYPE: value = ArrangementEntryType.valueOf(matcherElement.getText()); break;
      case MODIFIER: value = ArrangementModifier.valueOf(matcherElement.getText()); break;
      case NAME: value = StringUtil.unescapeStringCharacters(matcherElement.getText()); break;
      default:
        LOG.warn(String.format(
          "Can't deserialize an arrangement entry matcher from element of type '%s' with text '%s'",
          settingType, matcherElement.getText()
        ));
        return null;
    }
    return new ArrangementAtomMatchCondition(settingType, value);
  }

  private static class MySerializationVisitor implements ArrangementMatchConditionVisitor {
    
    Element result;
    Element parent;
    
    @Override
    public void visit(@NotNull ArrangementAtomMatchCondition condition) {
      String content = condition.getValue().toString();
      if (condition.getType() == ArrangementSettingType.NAME) {
        content = StringUtil.escapeStringCharacters(content);
      }
      Element element = new Element(condition.getType().toString()).setText(content);
      register(element);
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
      Element composite = new Element(COMPOSITE_CONDITION_NAME);
      register(composite);
      parent = composite;
      List<ArrangementMatchCondition> operands = new ArrayList<ArrangementMatchCondition>(condition.getOperands());
      ContainerUtil.sort(operands, CONDITION_COMPARATOR);
      for (ArrangementMatchCondition c : operands) {
        c.invite(this);
      }
    }

    private void register(@NotNull Element element) {
      if (result == null) {
        result = element;
      }
      if (parent != null) {
        parent.addContent(element);
      }
    }
  }
}
