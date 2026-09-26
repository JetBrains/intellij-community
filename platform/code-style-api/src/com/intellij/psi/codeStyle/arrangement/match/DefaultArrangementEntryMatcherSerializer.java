// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementSettingsSerializer;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokenType;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DefaultArrangementEntryMatcherSerializer {

  private static final Comparator<ArrangementMatchCondition> CONDITION_COMPARATOR = (c1, c2) -> {
    boolean isAtom1 = c1 instanceof ArrangementAtomMatchCondition;
    boolean isAtom2 = c2 instanceof ArrangementAtomMatchCondition;
    if (isAtom1 ^ isAtom2) {
      return isAtom1 ? 1 : -1; // Composite conditions before atom conditions.
    }
    else if (!isAtom1) {
      return 0;
    }

    ArrangementAtomMatchCondition atom1 = (ArrangementAtomMatchCondition)c1;
    ArrangementAtomMatchCondition atom2 = (ArrangementAtomMatchCondition)c2;
    int cmp = atom1.getType().compareTo(atom2.getType());
    if (cmp == 0) {
      cmp = atom1.getValue().toString().compareTo(atom2.getValue().toString());
    }
    return cmp;
  };

  private static final @NotNull Logger LOG = Logger.getInstance(DefaultArrangementEntryMatcherSerializer.class);

  private static final @NotNull String COMPOSITE_CONDITION_NAME = "AND";

  private final DefaultArrangementSettingsSerializer.Mixin myMixin;

  public DefaultArrangementEntryMatcherSerializer(DefaultArrangementSettingsSerializer.Mixin mixin) {
    myMixin = mixin;
  }

  public @Nullable <T extends ArrangementEntryMatcher> Element serialize(@NotNull T matcher) {
    if (matcher instanceof StdArrangementEntryMatcher) {
      return serialize(((StdArrangementEntryMatcher)matcher).getCondition());
    }
    LOG.warn(String.format(
      "Can't serialize arrangement entry matcher of class '%s'. Reason: expected to find '%s' instance instead",
      matcher.getClass(), StdArrangementEntryMatcher.class
    ));
    return null;
  }

  private static @NotNull Element serialize(@NotNull ArrangementMatchCondition condition) {
    MySerializationVisitor visitor = new MySerializationVisitor();
    condition.invite(visitor);
    return visitor.result;
  }

  public @Nullable StdArrangementEntryMatcher deserialize(@NotNull Element matcherElement) {
    ArrangementMatchCondition condition = deserializeCondition(matcherElement);
    return condition == null ? null : new StdArrangementEntryMatcher(condition);
  }

  private @Nullable ArrangementMatchCondition deserializeCondition(@NotNull Element matcherElement) {
    String name = matcherElement.getName();
    if (COMPOSITE_CONDITION_NAME.equals(name)) {
      ArrangementCompositeMatchCondition composite = new ArrangementCompositeMatchCondition();
      for (Element child : matcherElement.getChildren()) {
        ArrangementMatchCondition deserialised = deserializeCondition(child);
        if (deserialised != null) {
          composite.addOperand(deserialised);
        }
      }
      return composite;
    }
    else {
      return deserializeAtomCondition(matcherElement);
    }
  }

  private @Nullable ArrangementMatchCondition deserializeAtomCondition(@NotNull Element matcherElement) {
    String id = matcherElement.getName();
    ArrangementSettingsToken token = StdArrangementTokens.byId(id);
    boolean processInnerText = true;

    if (token != null
        && (StdArrangementTokens.General.TYPE.equals(token) || StdArrangementTokens.General.MODIFIER.equals(token)))
    {
      // Backward compatibility with old arrangement settings format.
      id = matcherElement.getText();
      if (StringUtil.isEmpty(id)) {
        LOG.warn("Can't deserialize match condition at legacy format");
        return null;
      }
      token = StdArrangementTokens.byId(id);
      processInnerText = false;
    }
    
    if (token == null) {
      token = myMixin.deserializeToken(id);
    }
    if (token == null) {
      LOG.warn(String.format("Can't deserialize match condition with id '%s'", id));
      return null;
    }

    Object value = token;
    String text = matcherElement.getText();
    if (text != null && processInnerText) {
      text = StringUtil.unescapeStringCharacters(matcherElement.getText());
      if (!StringUtil.isEmpty(text)) {
        final Boolean booleanValue = parseBooleanValue(text);
        if (booleanValue != null) {
          value = booleanValue;
        }
        else {
          value = text;
        }
      }
    }
    return new ArrangementAtomMatchCondition(token, value);
  }

  private static @Nullable Boolean parseBooleanValue(@NotNull String text) {
    if (StringUtil.equalsIgnoreCase(text, Boolean.TRUE.toString())) {
      return true;
    }

    if (StringUtil.equalsIgnoreCase(text, Boolean.FALSE.toString())) {
      return false;
    }
    return null;
  }

  private static class MySerializationVisitor implements ArrangementMatchConditionVisitor {
    
    Element result;
    Element parent;
    
    @Override
    public void visit(@NotNull ArrangementAtomMatchCondition condition) {
      ArrangementSettingsToken type = condition.getType();
      final Element element = new Element(type.getId());
      if (StdArrangementTokenType.REG_EXP.is(type)) {
        element.setText(StringUtil.escapeStringCharacters(condition.getValue().toString()));
      }
      else if (condition.getValue() instanceof Boolean) {
        element.setText(condition.getValue().toString());
      }
      register(element);
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
      Element composite = new Element(COMPOSITE_CONDITION_NAME);
      register(composite);
      parent = composite;
      List<ArrangementMatchCondition> operands =
        new ArrayList<>(condition.getOperands());
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
