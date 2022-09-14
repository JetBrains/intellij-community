// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CharTableImpl implements CharTable {
  private static final int INTERN_THRESHOLD = 40; // 40 or more characters long tokens won't be interned.

  private static final StringHashToCharSequencesMap STATIC_ENTRIES = newStaticSet();
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final StringHashToCharSequencesMap entries = new StringHashToCharSequencesMap(10, 0.9f);

  @NotNull
  @Override
  public CharSequence intern(@NotNull CharSequence text) {
    return text.length() > INTERN_THRESHOLD ? text : doIntern(text);
  }

  @NotNull
  private CharSequence doIntern(@NotNull CharSequence text, int startOffset, int endOffset) {
    int hashCode;
    if (startOffset == 0 && endOffset == text.length()) {
      hashCode = StringUtil.stringHashCode(text);
    }
    else {
      hashCode = StringUtil.stringHashCode(text, startOffset, endOffset);
    }
    CharSequence interned = STATIC_ENTRIES.getSubSequenceWithHashCode(hashCode, text, startOffset, endOffset);
    if (interned != null) {
      return interned;
    }

    synchronized(entries) {
      return entries.getOrAddSubSequenceWithHashCode(hashCode, text, startOffset, endOffset);
    }
  }

  @NotNull
  public CharSequence doIntern(@NotNull CharSequence text) {
    return doIntern(text, 0, text.length());
  }

  @NotNull
  @Override
  public CharSequence intern(@NotNull CharSequence baseText, int startOffset, int endOffset) {
    return endOffset - startOffset > INTERN_THRESHOLD ? substring(baseText, startOffset, endOffset)
                                                      : doIntern(baseText, startOffset, endOffset);
  }

  @NotNull
  private static String substring(@NotNull CharSequence text, int startOffset, int endOffset) {
    if (text instanceof String) {
      return ((String)text).substring(startOffset, endOffset);
    }
    return text.subSequence(startOffset, endOffset).toString();
  }

  @Nullable
  public static CharSequence getStaticInterned(@NotNull CharSequence text) {
    return STATIC_ENTRIES.get(text);
  }

  @NotNull
  private static StringHashToCharSequencesMap newStaticSet() {
    StringHashToCharSequencesMap r = new StringHashToCharSequencesMap(10, 0.9f);
    r.add("==");
    r.add("!=");
    r.add("||");
    r.add("++");
    r.add("--");

    r.add("<");
    r.add("<=");
    r.add("<<=");
    r.add("<<");
    r.add(">");
    r.add("&");
    r.add("&&");

    r.add("+=");
    r.add("-=");
    r.add("*=");
    r.add("/=");
    r.add("&=");
    r.add("|=");
    r.add("^=");
    r.add("%=");

    r.add("(");
    r.add(")");
    r.add("{");
    r.add("}");
    r.add("[");
    r.add("]");
    r.add(";");
    r.add(",");
    r.add("...");
    r.add(".");

    r.add("=");
    r.add("!");
    r.add("~");
    r.add("?");
    r.add(":");
    r.add("+");
    r.add("-");
    r.add("*");
    r.add("/");
    r.add("|");
    r.add("^");
    r.add("%");
    r.add("@");

    r.add(" ");
    r.add("  ");
    r.add("   ");
    r.add("    ");
    r.add("     ");
    r.add("      ");
    r.add("       ");
    r.add("        ");
    r.add("         ");
    r.add("          ");
    r.add("           ");
    r.add("            ");
    r.add("             ");
    r.add("              ");
    r.add("               ");
    r.add("\n");
    r.add("\n  ");
    r.add("\n    ");
    r.add("\n      ");
    r.add("\n        ");
    r.add("\n          ");
    r.add("\n            ");
    r.add("\n              ");
    r.add("\n                ");

    r.add("<");
    r.add(">");
    r.add("</");
    r.add("/>");
    r.add("\"");
    r.add("'");

    r.add("");
    return r;
  }

  // hashCode -> CharSequence|CharSequence[]
  private static final class StringHashToCharSequencesMap extends Int2ObjectOpenHashMap<Object> {
    private StringHashToCharSequencesMap(int capacity, float loadFactor) {
      super(capacity, loadFactor);
    }

    private CharSequence getSubSequenceWithHashCode(int hashCode, @NotNull CharSequence sequence, int startOffset, int endOffset) {
      Object o = get(hashCode);
      if (o == null) return null;
      if (o instanceof CharSequence) {
        if (charSequenceSubSequenceEquals((CharSequence)o, sequence, startOffset, endOffset)) {
          return (CharSequence)o;
        }
        return null;
      }
      if (o instanceof CharSequence[]) {
        for(CharSequence cs:(CharSequence[])o) {
          if (charSequenceSubSequenceEquals(cs, sequence, startOffset, endOffset)) {
            return cs;
          }
        }
        return null;
      }
      assert false:o.getClass();
      return null;
    }

    private static boolean charSequenceSubSequenceEquals(@NotNull CharSequence cs, @NotNull CharSequence baseSequence, int startOffset, int endOffset) {
      if (cs.length() != endOffset - startOffset) return false;
      if (cs == baseSequence && startOffset == 0) return true;
      for(int i = 0, len = cs.length(); i < len; ++i) {
        if (cs.charAt(i) != baseSequence.charAt(startOffset + i)) return false;
      }
      return true;
    }

    private CharSequence get(@NotNull CharSequence sequence) {
      int endOffset = sequence.length();
      int hashCode = StringUtil.stringHashCode(sequence);
      return getSubSequenceWithHashCode(hashCode, sequence, 0, endOffset);
    }

    private void add(@NotNull CharSequence sequence) {
      int endOffset = sequence.length();
      int hashCode = StringUtil.stringHashCode(sequence);
      getOrAddSubSequenceWithHashCode(hashCode, sequence, 0, endOffset);
    }

    @NotNull
    private CharSequence getOrAddSubSequenceWithHashCode(int hashCode, @NotNull CharSequence sequence, int startOffset, int endOffset) {
      Object value = get(hashCode);
      String addedSequence;

      if (value == null) {
        addedSequence = substring(sequence, startOffset, endOffset);
        put(hashCode, addedSequence);
      }
      else if (value instanceof CharSequence) {
        CharSequence existingSequence = (CharSequence)value;
        if (charSequenceSubSequenceEquals(existingSequence, sequence, startOffset, endOffset)) {
          return existingSequence;
        }
        addedSequence = substring(sequence, startOffset, endOffset);
        put(hashCode, new CharSequence[]{existingSequence, addedSequence});
      }
      else if (value instanceof CharSequence[]) {
        CharSequence[] existingSequenceArray = (CharSequence[])value;
        for (CharSequence cs : existingSequenceArray) {
          if (charSequenceSubSequenceEquals(cs, sequence, startOffset, endOffset)) {
            return cs;
          }
        }
        addedSequence = substring(sequence, startOffset, endOffset);
        CharSequence[] newSequenceArray = ArrayUtil.append(existingSequenceArray, addedSequence, CharSequence[]::new);
        put(hashCode, newSequenceArray);
      }
      else {
        assert false : value.getClass();
        return null;
      }
      return addedSequence;
    }
  }
}
