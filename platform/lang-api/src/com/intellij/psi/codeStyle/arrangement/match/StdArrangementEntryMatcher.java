// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokenType;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link ArrangementEntryMatcher} which is based on standard match conditions in form of {@link ArrangementSettingsToken}.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 *
 * @author Denis Zhdanov
 */
public class StdArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final ArrangementMatchCondition myCondition;
  @NotNull private final ArrangementEntryMatcher   myDelegate;

  public StdArrangementEntryMatcher(@NotNull ArrangementMatchCondition condition) {
    this(condition, new StdMatcherBuilderImpl());
  }

  public StdArrangementEntryMatcher(@NotNull ArrangementMatchCondition condition, @NotNull StdMatcherBuilder builder) {
    myCondition = condition;
    myDelegate = doBuildMatcher(condition, builder);
  }

  @NotNull
  public ArrangementMatchCondition getCondition() {
    return myCondition;
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    return myDelegate.isMatched(entry);
  }

  @Override
  public int hashCode() {
    return myCondition.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StdArrangementEntryMatcher matcher = (StdArrangementEntryMatcher)o;
    return myCondition.equals(matcher.myCondition);
  }

  @Override
  public String toString() {
    return myCondition.toString();
  }

  @NotNull
  private static ArrangementEntryMatcher doBuildMatcher(@NotNull ArrangementMatchCondition condition, @NotNull StdMatcherBuilder builder) {
    MyVisitor visitor = new MyVisitor(builder);
    condition.invite(visitor);
    return visitor.getMatcher();
  }

  /**
   * Used by inner visitor to build matchers from atom conditions.
   */
  public interface StdMatcherBuilder {

    /**
     * Parses given condition storing all data required to later produce a matcher based on the condition. It is called each time an
     * {@link ArrangementAtomMatchCondition} is encountered when traversing {@link ArrangementMatchCondition} on the
     * {@link StdArrangementEntryMatcher} creation.
     * @param condition condition to parse
     */
    void onCondition(@NotNull ArrangementAtomMatchCondition condition);

    /**
     * Returns a collection of matchers obtained through {@link #addMatcher(ArrangementEntryMatcher) addMatcher} calls or
     * built from info gained by {@link #onCondition(ArrangementAtomMatchCondition) onCondition} calls.
     * @return a collection of matchers
     */
    @Nullable Collection<ArrangementEntryMatcher> buildMatchers();

    /**
     * Adds given matcher to collection provided by {@link #buildMatchers() buildMatchers} calls.
     * @param matcher matcher to be added
     */
    void addMatcher(@NotNull ArrangementEntryMatcher matcher);
  }

  /**
   * Standard implementation of {@link StdMatcherBuilder}. Constructs entry matchers of types {@link ByTypeArrangementEntryMatcher},
   * {@link ByModifierArrangementEntryMatcher}, {@link ByNameArrangementEntryMatcher}, {@link ByNamespaceArrangementEntryMatcher}.
   */
  public static class StdMatcherBuilderImpl implements StdMatcherBuilder {

    @NotNull private final List<ArrangementEntryMatcher> myMatchers  = new ArrayList<>();
    /**
     * Maps token type to all arrangement tokens that were encountered so far by parsing conditions with
     * {@link #onCondition(ArrangementAtomMatchCondition) onCondition} calls.
     */
    @NotNull protected final MultiValuesMap<StdArrangementTokenType, ArrangementAtomMatchCondition> context =
      new MultiValuesMap<>();
    @Nullable private String myNamePattern;
    @Nullable private String myNamespacePattern;
    @Nullable private String myText;

    /**
     * Adds given entry to context by given entry type.
     * @param token token added to context
     */
    protected void addToContext(@NotNull StdArrangementSettingsToken token, @NotNull ArrangementAtomMatchCondition condition) {
      StdArrangementTokenType tokenType = token.getTokenType();
      context.put(tokenType, condition);
    }

    @Override
    public void onCondition(@NotNull ArrangementAtomMatchCondition condition) {
      if (StdArrangementTokens.Regexp.NAME.equals(condition.getType())) {
        myNamePattern = condition.getValue().toString();
        return;
      }
      else if (StdArrangementTokens.Regexp.XML_NAMESPACE.equals(condition.getType())) {
        myNamespacePattern = condition.getValue().toString();
      }
      else if (StdArrangementTokens.Regexp.TEXT.equals(condition.getType())) {
        myText = condition.getValue().toString();
      }
      Object v = condition.getValue();
      final ArrangementSettingsToken type = condition.getType();
      if (type instanceof StdArrangementSettingsToken) {
        //Process any StdArrangementSettingsToken. No need to change it when new types of tokens will be processed.
        addToContext((StdArrangementSettingsToken)type, condition);
      }
    }

    @Nullable
    @Override
    public Collection<ArrangementEntryMatcher> buildMatchers() {
      List<ArrangementEntryMatcher> result =
        new ArrayList<>(myMatchers);
      Collection<ArrangementAtomMatchCondition> entryTokens = context.get(StdArrangementTokenType.ENTRY_TYPE);
      if (entryTokens!= null) {
        result.add(new ByTypeArrangementEntryMatcher(entryTokens));
      }
      Collection<ArrangementAtomMatchCondition> modifierTokens = context.get(StdArrangementTokenType.MODIFIER);
      if (modifierTokens != null) {
        result.add(new ByModifierArrangementEntryMatcher(modifierTokens));
      }
      if (myNamePattern != null) {
        result.add(new ByNameArrangementEntryMatcher(myNamePattern));
      }
      if (myNamespacePattern != null) {
        result.add(new ByNamespaceArrangementEntryMatcher(myNamespacePattern));
      }
      if (myText != null) {
        result.add(new ByTextArrangementEntryMatcher(myText));
      }
      return result;
    }

    @Override
    public void addMatcher(@NotNull ArrangementEntryMatcher matcher) {
      myMatchers.add(matcher);
    }
  }

  private static final class MyVisitor implements ArrangementMatchConditionVisitor {

    @NotNull private final StdMatcherBuilder myMatcherBuilder;
    private boolean nestedComposite;

    private MyVisitor(@NotNull StdMatcherBuilder matcherBuilder) {
      myMatcherBuilder = matcherBuilder;
    }

    @Override
    public void visit(@NotNull ArrangementAtomMatchCondition condition) {
      myMatcherBuilder.onCondition(condition);
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
      if (!nestedComposite) {
        nestedComposite = true;
        for (ArrangementMatchCondition c : condition.getOperands()) {
          c.invite(this);
        }
      }
      else {
        myMatcherBuilder.addMatcher(doBuildMatcher(condition, myMatcherBuilder));
      }
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    public ArrangementEntryMatcher getMatcher() {
      Collection<ArrangementEntryMatcher> matchers = myMatcherBuilder.buildMatchers();

      if (matchers.size() == 1) {
        return matchers.iterator().next();
      } else {
        CompositeArrangementEntryMatcher result = new CompositeArrangementEntryMatcher();
        for (ArrangementEntryMatcher matcher: matchers) {
          result.addMatcher(matcher);
        }
        return result;
      }
    }
  }
}
