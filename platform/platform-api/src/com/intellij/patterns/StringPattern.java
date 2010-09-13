/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.patterns;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ProcessingContext;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.DatatypesAutomatonProvider;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class StringPattern extends ObjectPattern<String, StringPattern> {
  private static final InitialPatternCondition<String> CONDITION = new InitialPatternCondition<String>(String.class) {
    public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
      return o instanceof String;
    }


    public void append(@NonNls final StringBuilder builder, final String indent) {
      builder.append("string()");
    }
  };

  protected StringPattern() {
    super(CONDITION);
  }

  @NotNull
  public StringPattern startsWith(@NonNls @NotNull final String s) {
    return with(new PatternCondition<String>("startsWith") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        return str.startsWith(s);
      }
    });
  }

  @NotNull
  public StringPattern endsWith(@NonNls @NotNull final String s) {
    return with(new PatternCondition<String>("endsWith") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        return str.endsWith(s);
      }
    });
  }

  @NotNull
  public StringPattern contains(@NonNls @NotNull final String s) {
    return with(new PatternCondition<String>("contains") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        return str.contains(s);
      }

    });
  }

  @NotNull
  public StringPattern containsChars(@NonNls @NotNull final String s) {
    return with(new PatternCondition<String>("containsChars") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        for (int i=0, len=s.length(); i<len; i++) {
          if (str.indexOf(s.charAt(i))>-1) return true;
        }
        return false;
      }
    });
  }

  @NotNull
  public StringPattern matches(@NonNls @NotNull final String s) {
    final String escaped = StringUtil.escapeToRegexp(s);
    if (escaped.equals(s)) {
      return equalTo(s);
    }
    final Pattern pattern = Pattern.compile(s);

    return with(new ValuePatternCondition<String>("matches") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        return pattern.matcher(str).matches();
      }

      @Override
      public Collection<String> getValues() {
        return Collections.singleton(s);
      }
    });
  }

  @NotNull
  public StringPattern matchesBrics(@NonNls @NotNull final String s) {
    final String escaped = StringUtil.escapeToRegexp(s);
    if (escaped.equals(s)) {
      return equalTo(s);
    }

    StringBuilder sb = new StringBuilder(s.length()*5);
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if(c == ' ') {
        sb.append("<whitespace>");
      }
      else
      //This is really stupid and inconvinient builder - it breaks any normal pattern with uppercase
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

    return with(new ValuePatternCondition<String>("matchesBrics") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        return runAutomaton.run(str);
      }

      @Override
      public Collection<String> getValues() {
        return Collections.singleton(s);
      }
    });
  }

  @NotNull
  public StringPattern contains(@NonNls @NotNull final ElementPattern<Character> pattern) {
    return with(new PatternCondition<String>("contains") {
      public boolean accepts(@NotNull final String str, final ProcessingContext context) {
        for (int i = 0; i < str.length(); i++) {
          if (pattern.accepts(str.charAt(i))) return true;
        }
        return false;
      }
    });
  }

  public StringPattern longerThan(final int minLength) {
    return with(new PatternCondition<String>("longerThan") {
      public boolean accepts(@NotNull final String s, final ProcessingContext context) {
        return s.length() > minLength;
      }
    });
  }

  @NotNull
  public StringPattern oneOf(@NonNls final String... values) {
    return super.oneOf(values);
  }

  @NotNull
  public StringPattern oneOfIgnoreCase(@NonNls final String... values) {
    return with(new CaseInsensitiveValuePatternCondition("oneOfIgnoreCase", values));
  }

  @NotNull
  public StringPattern oneOf(@NonNls final Collection<String> set) {
    return super.oneOf(set);
  }

}
