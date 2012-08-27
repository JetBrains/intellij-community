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
import com.intellij.psi.codeStyle.arrangement.ArrangementOperator;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 7/19/12 1:00 PM
 */
public class DefaultArrangementEntryMatcherSerializer {

  private static final Logger      LOG                 = Logger.getInstance("#" + DefaultArrangementEntryMatcherSerializer.class.getName());
  private static final Set<String> COMPOSITE_OPERATORS = new HashSet<String>();
  private static final Set<String> ATOM_SETTINGS_TYPES = new HashSet<String>();
  static {
    for (ArrangementOperator operator : ArrangementOperator.values()) {
      COMPOSITE_OPERATORS.add(operator.toString());
    }
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
  public ArrangementEntryMatcher deserialize(@NotNull Element matcherElement) {
    ArrangementMatchCondition condition = deserializeCondition(matcherElement);
    return condition == null ? null : new StdArrangementEntryMatcher(condition);
  }

  @Nullable
  private static ArrangementMatchCondition deserializeCondition(@NotNull Element matcherElement) {
    String name = matcherElement.getName();
    if (!COMPOSITE_OPERATORS.contains(name)) {
      if (ATOM_SETTINGS_TYPES.contains(name)) {
        return deserializeAtomCondition(matcherElement);
      }
      else {
        LOG.warn(String.format(
          "Can't deserialize an arrangement entry matcher from matchElement with name '%s'. Reason: only the following elements"
          + "are supported: %s and %s",
          name, COMPOSITE_OPERATORS, ATOM_SETTINGS_TYPES
        ));
        return null;
      }
    }
    else {
      ArrangementCompositeMatchCondition composite = new ArrangementCompositeMatchCondition(ArrangementOperator.valueOf(name));
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
      Element element = new Element(condition.getType().toString()).setText(condition.getValue().toString());
      if (result == null) {
        result = element;
      }
      if (parent != null) {
        parent.addContent(element);
      }
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
      Element composite = new Element(condition.getOperator().toString());
      if (result == null) {
        result = composite;
      }
      if (parent != null) {
        parent.addContent(composite);
      }
      parent = composite;
      for (ArrangementMatchCondition c : condition.getOperands()) {
        c.invite(this);
      }
    }
  }
}
