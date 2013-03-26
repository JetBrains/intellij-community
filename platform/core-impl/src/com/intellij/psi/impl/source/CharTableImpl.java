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

package com.intellij.psi.impl.source;

import com.intellij.psi.CommonClassNames;
import com.intellij.util.CharTable;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceHashingStrategy;
import com.intellij.util.text.CharSequenceSubSequence;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author max
 */
public class CharTableImpl implements CharTable {
  private static final int INTERN_THRESHOLD = 40; // 40 or more characters long tokens won't be interned.
  private static final CharSequenceHashingStrategy HASHER = new CharSequenceHashingStrategy();
  private static final OpenTHashSet<CharSequence> STATIC_ENTRIES = newStaticSet();

  private final OpenTHashSet<CharSequence> entries = new OpenTHashSet<CharSequence>(10, 0.9f, HASHER);

  @NotNull
  @Override
  public CharSequence intern(@NotNull final CharSequence text) {
    if (text.length() > INTERN_THRESHOLD) return createSequence(text);

    return doIntern(text);
  }

  @NotNull
  public CharSequence doIntern(@NotNull CharSequence text) {
    CharSequence interned = getStaticInterned(text.toString());
    if (interned != null) {
      return interned;
    }

    synchronized(entries) {
      interned = entries.get(text);
      if (interned != null) {
        return interned;
      }

      // We need to create separate string just to prevent referencing all character data when original is string or char sequence over string
      final CharSequence entry = createSequence(text);
      boolean added = entries.add(entry);
      assert added;

      return entry;
    }
  }

  @NotNull
  @Override
  public CharSequence intern(@NotNull final CharSequence baseText, final int startOffset, final int endOffset) {
    if (endOffset - startOffset == baseText.length()) return baseText;
    return intern(new CharSequenceSubSequence(baseText, startOffset, endOffset));
  }

  @NotNull
  private static String createSequence(@NotNull CharSequence text) {
    char[] buf = new char[text.length()];
    CharArrayUtil.getChars(text, buf, 0);

    return StringFactory.createShared(buf); // this way the .toString() doesn't create another instance (as opposed to new CharArrayCharSequence())
  }

  @Nullable
  public static CharSequence getStaticInterned(@NotNull String text) {
    return STATIC_ENTRIES.get(text);
  }

  public static void staticIntern(@NotNull String text) {
    synchronized(STATIC_ENTRIES) {
      STATIC_ENTRIES.add(text);
    }
  }
  
  private static OpenTHashSet<CharSequence> newStaticSet() {
    final OpenTHashSet<CharSequence> r = new OpenTHashSet<CharSequence>(10, 0.9f, HASHER);
    r.add("==" );
    r.add("!=" );
    r.add("||" );
    r.add("++" );
    r.add("--" );

    r.add("<" );
    r.add("<=" );
    r.add("<<=" );
    r.add("<<" );
    r.add(">" );
    r.add("&" );
    r.add("&&" );

    r.add("+=" );
    r.add("-=" );
    r.add("*=" );
    r.add("/=" );
    r.add("&=" );
    r.add("|=" );
    r.add("^=" );
    r.add("%=" );

    r.add("("   );
    r.add(")"   );
    r.add("{"   );
    r.add("}"   );
    r.add("["   );
    r.add("]"   );
    r.add(";"   );
    r.add(","   );
    r.add("..." );
    r.add("."   );

    r.add("=" );
    r.add("!" );
    r.add("~" );
    r.add("?" );
    r.add(":" );
    r.add("+" );
    r.add("-" );
    r.add("*" );
    r.add("/" );
    r.add("|" );
    r.add("^" );
    r.add("%" );
    r.add("@" );

    r.add(" " );
    r.add("  " );
    r.add("   " );
    r.add("    " );
    r.add("     " );
    r.add("      " );
    r.add("       " );
    r.add("        " );
    r.add("         " );
    r.add("          " );
    r.add("           " );
    r.add("            " );
    r.add("             " );
    r.add("              " );
    r.add("               " );
    r.add("\n" );
    r.add("\n  " );
    r.add("\n    " );
    r.add("\n      " );
    r.add("\n        " );
    r.add("\n          " );
    r.add("\n            " );
    r.add("\n              " );
    r.add("\n                " );

    r.add("<");
    r.add(">");
    r.add("</");
    r.add("/>");
    r.add("\"");
    r.add("\'");
    r.add("<![CDATA[");
    r.add("]]>");
    r.add("<!--");
    r.add("-->");
    r.add("<!DOCTYPE");
    r.add("SYSTEM");
    r.add("PUBLIC");
    r.add("<?");
    r.add("?>");

    r.add("<%");
    r.add("%>");
    r.add("<%=");
    r.add("<%@");
    r.add("${");
    r.add("");
    return r;
  }

  static {
    addStringsFromClassToStatics(CommonClassNames.class);
  }
  public static void addStringsFromClassToStatics(@NotNull Class aClass) {
    for (Field field : aClass.getDeclaredFields()) {
      if ((field.getModifiers() & Modifier.STATIC) == 0) continue;
      if ((field.getModifiers() & Modifier.PUBLIC) == 0) continue;
      String typeName;
      try {
        typeName = (String)field.get(null);
      }
      catch (Exception e) {
        continue;
      }
      staticIntern(typeName);
    }
  }
}
