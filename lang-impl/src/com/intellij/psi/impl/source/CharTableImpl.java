package com.intellij.psi.impl.source;

import com.intellij.util.CharTable;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceHashingStrategy;
import gnu.trove.THashSet;

/**
 * @author max
 */
public class CharTableImpl implements CharTable {
  private final static int INTERN_THRESHOLD = 40; // 40 or more characters long tokens won't be interned.
  private final static CharSequenceHashingStrategy HASHER = new CharSequenceHashingStrategy();
  private final static MyTHashSet staticEntries = new MyTHashSet();
  private final MyTHashSet entries = new MyTHashSet();

  public CharSequence intern(final CharSequence text) {
    if (text.length() > INTERN_THRESHOLD) return createSequence(text);
    int idx;

    synchronized(staticEntries) {
      idx = staticEntries.index(text);
      if (idx >= 0) {
        //noinspection NonPrivateFieldAccessedInSynchronizedContext
        return staticEntries.get(idx);
      }
    }

    synchronized(this) {
      idx = entries.index(text);
      if (idx >= 0) {
        //noinspection NonPrivateFieldAccessedInSynchronizedContext
        return entries.get(idx);
      }

      // We need to create separate string just to prevent referencing all character data when original is string or char sequence over string
      final CharSequence entry = createSequence(text);
      boolean added = entries.add(entry);
      assert added;

      return entry;
    }
  }

  private static CharSequence createSequence(final CharSequence text) {
    final char[] buf = new char[text.length()];
    CharArrayUtil.getChars(text, buf, 0);
    return StringFactory.createStringFromConstantArray(buf);
  }

  public static void staticIntern(final String text) {
    synchronized(staticEntries) {
      staticEntries.add(text);
    }
  }
  
  private final static class MyTHashSet extends THashSet<CharSequence> {
    public MyTHashSet() {
      super(10, 0.9f, CharTableImpl.HASHER);
    }

    public int index(final CharSequence obj) {
      return super.index(obj);
    }

    public CharSequence get(int index) {
      return (CharSequence)_set[index];
    }
  }

  static {
    CharTableImpl.staticIntern("==" );
    CharTableImpl.staticIntern("!=" );
    CharTableImpl.staticIntern("||" );
    CharTableImpl.staticIntern("++" );
    CharTableImpl.staticIntern("--" );

    CharTableImpl.staticIntern("<" );
    CharTableImpl.staticIntern("<=" );
    CharTableImpl.staticIntern("<<=" );
    CharTableImpl.staticIntern("<<" );
    CharTableImpl.staticIntern(">" );
    CharTableImpl.staticIntern("&" );
    CharTableImpl.staticIntern("&&" );

    CharTableImpl.staticIntern("+=" );
    CharTableImpl.staticIntern("-=" );
    CharTableImpl.staticIntern("*=" );
    CharTableImpl.staticIntern("/=" );
    CharTableImpl.staticIntern("&=" );
    CharTableImpl.staticIntern("|=" );
    CharTableImpl.staticIntern("^=" );
    CharTableImpl.staticIntern("%=" );

    CharTableImpl.staticIntern("("   );
    CharTableImpl.staticIntern(")"   );
    CharTableImpl.staticIntern("{"   );
    CharTableImpl.staticIntern("}"   );
    CharTableImpl.staticIntern("["   );
    CharTableImpl.staticIntern("]"   );
    CharTableImpl.staticIntern(";"   );
    CharTableImpl.staticIntern(","   );
    CharTableImpl.staticIntern("..." );
    CharTableImpl.staticIntern("."   );

    CharTableImpl.staticIntern("=" );
    CharTableImpl.staticIntern("!" );
    CharTableImpl.staticIntern("~" );
    CharTableImpl.staticIntern("?" );
    CharTableImpl.staticIntern(":" );
    CharTableImpl.staticIntern("+" );
    CharTableImpl.staticIntern("-" );
    CharTableImpl.staticIntern("*" );
    CharTableImpl.staticIntern("/" );
    CharTableImpl.staticIntern("|" );
    CharTableImpl.staticIntern("^" );
    CharTableImpl.staticIntern("%" );
    CharTableImpl.staticIntern("@" );

    CharTableImpl.staticIntern(" " );
    CharTableImpl.staticIntern("\n" );
    CharTableImpl.staticIntern("\n  " );
    CharTableImpl.staticIntern("\n    " );
    CharTableImpl.staticIntern("\n      " );
    CharTableImpl.staticIntern("\n        " );
    CharTableImpl.staticIntern("\n          " );
    CharTableImpl.staticIntern("\n            " );
    CharTableImpl.staticIntern("\n              " );
    CharTableImpl.staticIntern("\n                " );

    CharTableImpl.staticIntern("<");
    CharTableImpl.staticIntern(">");
    CharTableImpl.staticIntern("</");
    CharTableImpl.staticIntern("/>");
    CharTableImpl.staticIntern("\"");
    CharTableImpl.staticIntern("\'");
    CharTableImpl.staticIntern("<![CDATA[");
    CharTableImpl.staticIntern("]]>");
    CharTableImpl.staticIntern("<!--");
    CharTableImpl.staticIntern("-->");
    CharTableImpl.staticIntern("<!DOCTYPE");
    CharTableImpl.staticIntern("SYSTEM");
    CharTableImpl.staticIntern("PUBLIC");
    CharTableImpl.staticIntern("<?");
    CharTableImpl.staticIntern("?>");

    CharTableImpl.staticIntern("<%");
    CharTableImpl.staticIntern("%>");
    CharTableImpl.staticIntern("<%=");
    CharTableImpl.staticIntern("<%@");
    CharTableImpl.staticIntern("${");
    CharTableImpl.staticIntern("");
  }
}
