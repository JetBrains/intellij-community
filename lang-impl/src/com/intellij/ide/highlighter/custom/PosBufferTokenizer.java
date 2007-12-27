package com.intellij.ide.highlighter.custom;


public class PosBufferTokenizer {
  private CharSequence buffer;

  // Temp buffer.
  private char buf[] = new char[20];

  private int peekc = NEED_CHAR;

  private static final int NEED_CHAR = Integer.MAX_VALUE;

  private boolean pushedBack;
  //private boolean forceLower;

  private byte ctype[] = new byte[256];
  private static final byte CT_WHITESPACE = 1;
  private static final byte CT_DIGIT = 2;
  private static final byte CT_ALPHA = 4;
  private static final byte CT_QUOTE = 8;
  private static final byte CT_POSTFIX = 16;

  public int ttype = TT_NOTHING;

  public static final int TT_EOF = -1;
  public static final int TT_EOL = '\n';
  public static final int TT_NUMBER = -2;
  public static final int TT_WORD = -3;
  public static final int TT_QUOTE = -5;
  public static final int TT_WHITESPACE = -6;
  private static final int TT_NOTHING = -4;

  private String hexPrefix;

  private Object lock;

  private int bufferEnd;

  private int pos;

  public String sval;
  public int startOffset;
  public int endOffset;

  private boolean myIgnoreCase;

  public PosBufferTokenizer() {
    wordChars('a', 'z');
    wordChars('A', 'Z');
    wordChars(128 + 32, 255);
    whitespaceChars(0, ' ');
    parseNumbers();
  }

  public void start(CharSequence buffer, int from, int to) {
    if (buffer == null) {
      throw new NullPointerException();
    }

    this.lock = this;
    this.buffer = buffer;

    if (to < from) {
      throw new IllegalArgumentException();
    }

    pos = from;
    bufferEnd = to;
  }

  public void resetSyntax() {
    for (int i = ctype.length; --i >= 0;) {
      ctype[i] = 0;
    }
  }

  public void wordChars(int low, int hi) {
    if (low < 0) {
      low = 0;
    }
    if (hi >= ctype.length) {
      hi = ctype.length - 1;
    }
    while (low <= hi) {
      ctype[low++] |= CT_ALPHA;
    }
  }

  public void whitespaceChars(int low, int hi) {
    if (low < 0) {
      low = 0;
    }
    if (hi >= ctype.length) {
      hi = ctype.length - 1;
    }
    while (low <= hi) {
      ctype[low++] = CT_WHITESPACE;
    }
  }

  public void ordinaryChars(int low, int hi) {
    if (low < 0) {
      low = 0;
    }
    if (hi >= ctype.length) {
      hi = ctype.length - 1;
    }
    while (low <= hi) {
      ctype[low++] = 0;
    }
  }

  public void ordinaryChar(int ch) {
    if (ch >= 0 && ch < ctype.length) {
      ctype[ch] = 0;
    }
  }

  public void quoteChar(int ch) {
    if (ch >= 0 && ch < ctype.length) {
      ctype[ch] = CT_QUOTE;
    }
  }

  public void parseNumbers() {
    for (int i = '0'; i <= '9'; i++) {
      ctype[i] |= CT_DIGIT;
    }
    ctype['.'] |= CT_DIGIT;
  }

  /** Read the next character */
  private int read() {
    synchronized (lock) {
      if (buffer == null) {
        throw new NullPointerException();
      }
      if (pos < 0) {
        pos = 0;
      }
      if (pos >= bufferEnd) {
        // We need pos to be "bufferEnd + 1" in order to drop
        // unnecessary checks for determining endOffset.
        if (pos == bufferEnd) pos++;
        return -1;
      } else {
        return buffer.charAt(pos++);
      }
    }
  }

  public void skipToEol() {
    int c;
    do {
      c = read();
    } while (c != '\r' && c != '\n' && c != -1);
    pos--;
    peekc = NEED_CHAR;
  }

  public void skipToChar(char c) {
    int d;
    do {
      d = read();
    } while (d != c && d != -1);
    peekc = NEED_CHAR;
  }

  public void skipToStr(String s) {
    int c = 0;
    while (c != -1) {
      skipToChar(s.charAt(0));
      int i = 1;
      do {
        if (i == s.length()) return;
        c = read();
        if (c != s.charAt(i++)) {
          break;
        }
      } while (c != -1);
      peekc = NEED_CHAR;
    }
  }

  public int nextToken() {
    if (pushedBack) {
      pushedBack = false;
      return ttype;
    }
    byte ct[] = ctype;
    sval = null;

    startOffset = 0;
    endOffset = 0;

    int c = peekc;

    if (c < 0) {
      c = NEED_CHAR;
    }

    if (c == NEED_CHAR) {
      c = read();
      if (c < 0)
        return ttype = TT_EOF;
    }

    // Just to be safe
    ttype = c;

    // Set peekc so that the next invocation of nextToken will read
    // another character unless peekc is reset in this invocation
    peekc = NEED_CHAR;

    int ctype = c < 256 ? ct[c] : CT_ALPHA;

    if (isWhiteSpace(c)) {
      startOffset = pos - 1;
      do {
        c = read();
        if (c >= 0) {
          ctype = c < 256 ? ct[c] : CT_ALPHA;
        } else {
          break;
        }
      } while (isWhiteSpace(c));
      peekc = c;
      endOffset = pos - 1;
      return ttype = TT_WHITESPACE;
    }

    if ((ctype & CT_DIGIT) != 0) {
      startOffset = pos - 1;

      if (hexPrefix != null) {
        for (int i = 0; i < hexPrefix.length(); i++) {
          if (c == hexPrefix.charAt(i)) {
            c = read();
          } else {
            break;
          }
        }
      }
      do {
        c = read();
        if (c < 0) break;
        ctype = c < 256 ? ct[c] : CT_ALPHA;
        if (!('0' <= c && c <= '9') && c != '.' && (ctype & CT_POSTFIX) == 0) {
          break;
        }
      } while (c != -1);
      peekc = c;
      endOffset = pos - 1;
      return ttype = TT_NUMBER;
    }

    if ((ctype & CT_ALPHA) != 0) {
      int i = 0;
      do {
        if (i >= buf.length) {
          char nb[] = new char[buf.length * 2];
          System.arraycopy(buf, 0, nb, 0, buf.length);
          buf = nb;
        }
        buf[i++] = (char) c;
        c = read();
        ctype = c < 0 ? CT_WHITESPACE : c < 256 ? ct[c] : CT_ALPHA;
        // To support keywords like 'font-size'
        if (c == '-') ctype = CT_ALPHA;
      } while ((ctype & (CT_ALPHA | CT_DIGIT)) != 0);
      peekc = c;
      startOffset = pos - i - 1;
      endOffset = pos - 1;
      sval = String.copyValueOf(buf, 0, i);
      if (myIgnoreCase) {
        sval = sval.toLowerCase();
      }
      return ttype = TT_WORD;
    }

    if ((ctype & CT_QUOTE) != 0) {
      startOffset = pos - 1;
      ttype = c;
      int prev;
      int d = -1;
      do {
        prev = d;
        d = read();
        // Check for escape symbol.
        if (d == ttype && prev != '\\') break;
      } while (d != '\r' && d != '\n' && d != -1);
      endOffset = (d == ttype)?pos:pos - 1;
      if (d != ttype) {
        peekc = d;
      } else {
        peekc = NEED_CHAR;
      }
      return ttype = TT_QUOTE;
    }

    startOffset = pos - 1;
    endOffset = pos;

    return ttype = c;
  }

  private boolean isWhiteSpace(int c) {
    return c == ' ' || c == '\t' || c == '\n';
  }

  public void pushBack() {
    if (ttype != TT_NOTHING)   /* No-op if nextToken() not called */
      pushedBack = true;
  }

  public int getPos() {
    if (pos > bufferEnd) {
      return bufferEnd;
    }
    return pos;
  }

  public void setHexPrefix(String hexPrefix) {
    this.hexPrefix = hexPrefix;
    for (char c = 'a'; c <= 'f'; c++) ctype[c] |= CT_POSTFIX;
    for (char c = 'A'; c <= 'F'; c++) ctype[c] |= CT_POSTFIX;
  }

  public void setNumPostifxChars(String numPostfixChars) {
    if (numPostfixChars == null) return;
    for (int i = 0; i < numPostfixChars.length(); i++) {
      int ch = numPostfixChars.charAt(i);
      if (ch >= 0 && ch < ctype.length) {
        ctype[ch] |= CT_POSTFIX;
      }
    }
  }

  public void setIgnoreCase(boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
  }

  public boolean matchString(String s, int from) {
    for (int i = from; i < s.length(); i++) {
      int c = peekc;
      if (peekc == NEED_CHAR) {
        c = read();
      }
      if (c != s.charAt(i)) {
        peekc = c;
        return false;
      }
      peekc = NEED_CHAR;
    }
    return true;
  }
}
