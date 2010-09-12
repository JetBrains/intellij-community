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

import com.intellij.util.CharTable;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceHashingStrategy;
import gnu.trove.THashSet;

/**
 * @author max
 */
public class CharTableImpl implements CharTable {
  private static final int INTERN_THRESHOLD = 40; // 40 or more characters long tokens won't be interned.
  private static final CharSequenceHashingStrategy HASHER = new CharSequenceHashingStrategy();
  private static final MyTHashSet STATIC_ENTRIES = new MyStaticTHashSet();

  private final MyTHashSet entries = new MyTHashSet();

  public CharSequence intern(final CharSequence text) {
    if (text.length() > INTERN_THRESHOLD) return createSequence(text);

    int idx = STATIC_ENTRIES.index(text);
    if (idx >= 0) {
      return STATIC_ENTRIES.get(idx);
    }

    synchronized(entries) {
      idx = entries.index(text);
      if (idx >= 0) {
        return entries.get(idx);
      }

      // We need to create separate string just to prevent referencing all character data when original is string or char sequence over string
      final CharSequence entry = createSequence(text);
      boolean added = entries.add(entry);
      assert added;

      return entry;
    }
  }

  public CharSequence intern(final CharSequence baseText, final int startOffset, final int endOffset) {
    if (endOffset - startOffset == baseText.length()) return baseText.toString();
    return intern(baseText.subSequence(startOffset, endOffset));
  }

  private static CharSequence createSequence(final CharSequence text) {
    final char[] buf = new char[text.length()];
    CharArrayUtil.getChars(text, buf, 0);
    return new CharArrayCharSequence(buf);
  }

  public static void staticIntern(final String text) {
    synchronized(STATIC_ENTRIES) {
      STATIC_ENTRIES.add(text);
    }
  }
  
  private static class MyTHashSet extends THashSet<CharSequence> {
    private MyTHashSet() {
      super(10, 0.9f, HASHER);
    }

    public int index(final CharSequence obj) {
      return super.index(obj);
    }

    public CharSequence get(int index) {
      return (CharSequence)_set[index];
    }
  }

  private static class MyStaticTHashSet extends MyTHashSet {{
    add("==" );
    add("!=" );
    add("||" );
    add("++" );
    add("--" );

    add("<" );
    add("<=" );
    add("<<=" );
    add("<<" );
    add(">" );
    add("&" );
    add("&&" );

    add("+=" );
    add("-=" );
    add("*=" );
    add("/=" );
    add("&=" );
    add("|=" );
    add("^=" );
    add("%=" );

    add("("   );
    add(")"   );
    add("{"   );
    add("}"   );
    add("["   );
    add("]"   );
    add(";"   );
    add(","   );
    add("..." );
    add("."   );

    add("=" );
    add("!" );
    add("~" );
    add("?" );
    add(":" );
    add("+" );
    add("-" );
    add("*" );
    add("/" );
    add("|" );
    add("^" );
    add("%" );
    add("@" );

    add(" " );
    add("  " );
    add("   " );
    add("    " );
    add("     " );
    add("      " );
    add("       " );
    add("        " );
    add("         " );
    add("          " );
    add("           " );
    add("            " );
    add("             " );
    add("              " );
    add("               " );
    add("\n" );
    add("\n  " );
    add("\n    " );
    add("\n      " );
    add("\n        " );
    add("\n          " );
    add("\n            " );
    add("\n              " );
    add("\n                " );

    add("<");
    add(">");
    add("</");
    add("/>");
    add("\"");
    add("\'");
    add("<![CDATA[");
    add("]]>");
    add("<!--");
    add("-->");
    add("<!DOCTYPE");
    add("SYSTEM");
    add("PUBLIC");
    add("<?");
    add("?>");

    add("<%");
    add("%>");
    add("<%=");
    add("<%@");
    add("${");
    add("");
  }}
}
