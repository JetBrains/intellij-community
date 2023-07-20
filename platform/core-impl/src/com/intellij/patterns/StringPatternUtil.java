// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ProcessingContext;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public final class StringPatternUtil {
  public static @NotNull StringPattern matchesBrics(StringPattern pattern, final @NonNls @NotNull String s) {
    final String escaped = StringUtil.escapeToRegexp(s);
    if (escaped.equals(s)) {
      return pattern.equalTo(s);
    }

    @NonNls StringBuilder sb = new StringBuilder(s.length() * 5);
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if(c == ' ') {
        sb.append("<whitespacechar>");
      }
      else
      //This is really stupid and inconvenient builder - it breaks any normal pattern with uppercase
      if(Character.isUpperCase(c)) {
        sb.append('[').append(Character.toUpperCase(c)).append(Character.toLowerCase(c)).append(']');
      }
      else
      {
        sb.append(c);
      }
    }
    final RegExp regExp = new RegExp(sb.toString());
    final Automaton automaton = regExp.toAutomaton(new DatatypesAutomatonProvider());
    final RunAutomaton runAutomaton = new RunAutomaton(automaton, true);

    return pattern.with(new ValuePatternCondition<String>("matchesBrics") {
      @Override
      public boolean accepts(@NotNull String str, final ProcessingContext context) {
        return runAutomaton.run(str);
      }

      @Override
      public Collection<String> getValues() {
        return Collections.singleton(s);
      }
    });
  }
}
