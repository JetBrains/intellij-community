package com.intellij.database.datagrid;

import com.intellij.database.csv.CsvRecordFormat;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import dk.brics.automaton.SpecialOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class CsvLexer {
  public static final int MAX_CHARACTERS = 10 * 1024 * 1024 / 2;
  private CsvReader myReader;

  private CsvRecordFormat myCsvFormat;

  private TokenType myType;
  private long myStart;
  private long myEnd;
  private long myOffset;
  private long myTokenLine;
  private String myText;

  private long myEndOffset;
  private long myTokenEndLine;

  private State myState;

  private RunAutomaton myQuotedAutomaton;
  private RunAutomaton myValueAutomaton;
  private RunAutomaton mySeparatorRewindAutomaton;
  private int myMaxSeparatorRewind;

  public CsvLexer(@NotNull CsvReader reader) {
    reset(reader);
  }

  public void reset(@NotNull CsvReader reader) {
    myState = State.RECORD_START;
    myReader = reader;
  }

  public void setCsvFormat(@NotNull CsvRecordFormat csvFormat) {
    myCsvFormat = csvFormat;
    buildAutomatons();
  }

  private void buildAutomatons() {
    myQuotedAutomaton = buildQuotationAutomaton();
    myValueAutomaton = buildValueAutomaton();
    mySeparatorRewindAutomaton = buildSeparatorRewindAutomaton();
    myMaxSeparatorRewind = Math.max(myCsvFormat.recordSeparator.length() + myCsvFormat.suffix.length(), myCsvFormat.valueSeparator.length());
  }

  private @Nullable RunAutomaton buildQuotationAutomaton() {
    StringBuilder pattern = new StringBuilder();
    appendQuotesValuePattern(pattern, myCsvFormat.quotes);
    if (!pattern.isEmpty()) {
      appendValueEndLookahead(pattern, myCsvFormat);
      return buildRunAutomaton(pattern);
    }
    else {
      return null;
    }
  }

  private @Nullable RunAutomaton buildSeparatorRewindAutomaton() {
    StringBuilder pattern = new StringBuilder();
    if (!appendSeparatorPattern(pattern, myCsvFormat).isEmpty()) {
      Automaton automaton = buildAutomaton(pattern);
      SpecialOperations.reverse(automaton);
      return new RunAutomaton(automaton);
    }
    else {
      return null;
    }
  }

  private @NotNull RunAutomaton buildValueAutomaton() {
    StringBuilder pattern = new StringBuilder(".*");
    appendValueEndLookahead(pattern, myCsvFormat);
    return buildRunAutomaton(pattern);
  }

  public @NotNull CsvRecordFormat getCsvFormat() {
    return myCsvFormat;
  }

  private static @NotNull RunAutomaton buildRunAutomaton(StringBuilder builder) {
    return new RunAutomaton(buildAutomaton(builder));
  }

  private static Automaton buildAutomaton(StringBuilder builder) {
    return new RegExp(builder.toString()).toAutomaton();
  }

  public static StringBuilder appendQuotesValuePattern(StringBuilder res, List<CsvRecordFormat.Quotes> quotes) {
    res.append("(");
    boolean first = true;
    for (CsvRecordFormat.Quotes quote: quotes) {
      if (quote.leftQuote.isEmpty() || quote.rightQuote.isEmpty()) continue;
      if (!first) res.append("|");
      else first = false;
      appendQuoteValuePattern(res, quote);
    }
    res.append(")");
    return res;
  }

  private static void appendValueEndLookahead(StringBuilder res, CsvRecordFormat format) {
    if (format.recordSeparator.isEmpty() && format.valueSeparator.isEmpty() && format.suffix.isEmpty()) {
      res.append("\0");
      return;
    }
    res.append("(");
    appendSeparatorPattern(res, format);
    res.append(")");
  }

  private static StringBuilder appendSeparatorPattern(StringBuilder res, CsvRecordFormat format) {
    if (format.recordSeparator.isEmpty() && format.valueSeparator.isEmpty() && format.suffix.isEmpty()) return res;
    res.append("\0");
    CharSequence rec = bricsEscape(format.recordSeparator);
    if (!rec.isEmpty()) {
      res.append("|").append(rec).append("\0?");
    }
    if (!format.valueSeparator.isEmpty()) {
      res.append("|").append(bricsEscape(format.valueSeparator)).append("\0?");
    }
    if (!format.suffix.isEmpty()) {
      res.append("|").append(bricsEscape(format.suffix));
      if (!rec.isEmpty()) {
        res.append("(\0|").append(rec).append("\0?").append(")");
      }
    }
    return res;
  }

  private static void appendQuoteValuePattern(StringBuilder res, CsvRecordFormat.Quotes quote) {
    CharSequence el = bricsEscape(quote.leftQuote);
    CharSequence er = bricsEscape(quote.rightQuote);
    CharSequence eer = bricsEscape(quote.rightQuoteEscaped);
    res.append(el);
    res.append('(').append(eer).append("|.&~(").append(er).append("))*");
    res.append(er);
  }

  private static @NotNull CharSequence bricsEscape(String text) {
    if (text.isEmpty()) return text;
    StringBuilder r = new StringBuilder();
    boolean opened = false;
    for (int i = 0; i < text.length(); ++i) {
      char c = text.charAt(i);
      if (c == '"') {
        if (opened) {
          r.append('"');
          opened = false;
        }
        r.append('\\');
      }
      else if (!opened) {
        opened = true;
        r.append('"');
      }
      r.append(c);
    }
    if (opened) {
      r.append('"');
    }
    return r;
  }

  public void advance() throws IOException {
    if (!myReader.isReady()) {
      myState.end(this);
      nextState();
      return;
    }

    myState.nextToken(this);
    while (myReader.isReady() && myType == null) {
      nextState();
      myState.nextToken(this);
    }
    nextState();
  }

  public boolean isReady() throws IOException {
    return myReader.isReady();
  }

  public @Nullable String readAhead(int count) throws IOException {
    return myReader.readAhead(count);
  }

  public long getLine() {
    return myReader.getLine();
  }

  public long getCharacters() {
    return myReader.getCharacters();
  }

  public long getLineCharacters() {
    return myReader.getLineCharacters();
  }

  public long getTokenLine() {
    return myTokenLine;
  }

  public @Nullable TokenType getType() {
    return myType;
  }

  public long getStart() {
    return myStart;
  }

  public long getEnd() {
    return myEnd;
  }

  public long getOffset() {
    return myOffset;
  }

  public @Nullable String getText() {
    return myText;
  }

  public boolean hasToken() {
    return myType != null;
  }

  public void valueOrQuoted() throws IOException {
    int quotedCount = matchAndRewindSeparators(myQuotedAutomaton);
    if (quotedCount != -1) {
      String quoted = StringUtil.notNullize(myReader.readString(quotedCount));
      setToken(TokenType.QUOTED_VALUE, quoted);
    }
    else {
      int valueCount = matchAndRewindSeparators(myValueAutomaton);
      String value = StringUtil.notNullize(myReader.readString(valueCount));
      setToken(TokenType.VALUE, value);
    }
  }

  private int matchAndRewindSeparators(RunAutomaton automaton) throws IOException {
    int matched = automaton == null ? -1 : myReader.matchAhead(automaton, 0);
    return matched == -1 ? -1 : rewindSeparators(matched);
  }

  private int rewindSeparators(int count) throws IOException {
    int rewCount = mySeparatorRewindAutomaton == null ? -1 : myReader.matchBackward(count, mySeparatorRewindAutomaton, myMaxSeparatorRewind);
    return rewCount == -1 ? count : count - rewCount;
  }

  protected void nextState() throws IOException {
    setState(myState.nextState(myType));
  }

  private void setState(@Nullable State state) {
    myState = state;
  }

  private void setToken(@Nullable TokenType type, @Nullable String text) {
    setToken(type, text, myEnd, getCharacters(), myTokenEndLine, myEndOffset);
  }
  private void setToken(@Nullable TokenType type, @Nullable String text, long start, long end, long line, long offset) {
    myType = type;
    myText = text;
    myStart = start;
    myEnd = end;
    myTokenLine = line;
    myOffset = offset;
    myTokenEndLine = getLine();
    myEndOffset = getLineCharacters();
  }

  private void setNullToken() {
    setToken(null, null);
  }

  private void getSymbolToken(@NotNull String symbol,
                              @NotNull TokenType type) throws IOException {
    if (!myReader.isReady()) {
      setNullToken();
    }
    else if (myReader.read(symbol)) {
      setToken(type, symbol);
    }
    else {
      setNullToken();
    }
  }

  private void separator() throws IOException {
    if (!myReader.isReady()) {
      setNullToken();
    }
    else if (isSuffix() && myReader.read(myCsvFormat.suffix, true)) {
      setToken(TokenType.SUFFIX, myCsvFormat.suffix);
    }
    else if (myReader.read(myCsvFormat.recordSeparator)) {
      setToken(TokenType.RECORD_SEPARATOR, myCsvFormat.recordSeparator);
    }
    else if (myReader.read(myCsvFormat.valueSeparator)) {
      setToken(TokenType.VALUE_SEPARATOR, myCsvFormat.valueSeparator);
    }
    else {
      setNullToken();
    }
  }

  private boolean isSuffix() throws IOException {
    if (myCsvFormat.suffix.isEmpty()) return false;
    String suffix = myCsvFormat.suffix + myCsvFormat.recordSeparator;
    return myReader.matchAhead(suffix, true) != -1;
  }

  private static @NotNull String getElementText(@NotNull IElementType type) {
    String s = StringUtil.toLowerCase(type.toString());
    return StringUtil.join(s.split("_"), " ");
  }

  public static class TokenType {
    private static final IElementType RECORD_SEPARATOR_TYPE = new IElementType("RECORD_SEPARATOR", null);
    private static final IElementType VALUE_SEPARATOR_TYPE = new IElementType("VALUE_SEPARATOR", null);
    private static final IElementType VALUE_TYPE = new IElementType("VALUE", null);
    private static final IElementType QUOTED_VALUE_TYPE = new IElementType("QUOTED_VALUE", null);
    private static final IElementType SUFFIX_TYPE = new IElementType("SUFFIX", null);
    private static final IElementType PREFIX_TYPE = new IElementType("PREFIX", null);

    public static final TokenType RECORD_SEPARATOR = new TokenType(RECORD_SEPARATOR_TYPE) {
      @Override
      public @NotNull String getDebugName(@NotNull CsvRecordFormat format) {
        return super.getDebugName(format) + " (" + format.recordSeparator + ")";
      }

      @Override
      @NotNull String getTokenRepresentation(@NotNull String text) {
        return myName + " " + super.getTokenRepresentation(text);
      }
    };
    public static final TokenType VALUE_SEPARATOR = new TokenType(VALUE_SEPARATOR_TYPE) {
      @Override
      public @NotNull String getDebugName(@NotNull CsvRecordFormat format) {
        return super.getDebugName(format) + " (" + format.valueSeparator + ")";
      }

      @Override
      @NotNull String getTokenRepresentation(@NotNull String text) {
        return myName + " " + super.getTokenRepresentation(text);
      }
    };
    public static final TokenType VALUE = new TokenType(VALUE_TYPE) {
      @Override
      protected @NotNull String wrap(@NotNull String s) {
        return "\"" + s + "\"";
      }
    };
    public static final TokenType QUOTED_VALUE = new TokenType(QUOTED_VALUE_TYPE) {
      @Override
      public @NotNull String getDebugName(@NotNull CsvRecordFormat format) {
        return "value";
      }

      @Override
      protected @NotNull String wrap(@NotNull String s) {
        return s;
      }
    };
    public static final TokenType PREFIX = new TokenType(PREFIX_TYPE) {
      @Override
      public @NotNull String getDebugName(@NotNull CsvRecordFormat format) {
        return super.getDebugName(format) + " (" + format.prefix + ")";
      }

      @Override
      @NotNull String getTokenRepresentation(@NotNull String text) {
        return myName + " " + super.getTokenRepresentation(text);
      }
    };
    public static final TokenType SUFFIX = new TokenType(SUFFIX_TYPE) {
      @Override
      public @NotNull String getDebugName(@NotNull CsvRecordFormat format) {
        return super.getDebugName(format) + " (" + format.suffix + ")";
      }

      @Override
      @NotNull String getTokenRepresentation(@NotNull String text) {
        return myName + " " + super.getTokenRepresentation(text);
      }
    };

    protected final String myName;

    private final IElementType myElementType;

    private TokenType(@NotNull IElementType type) {
      myElementType = type;
      myName = getElementText(type);
    }

    public @NotNull String getDebugName(@NotNull CsvRecordFormat format) {
      return myName;
    }

    @NotNull
    String getTokenRepresentation(@NotNull String text) {
      return wrap(StringUtil.escapeStringCharacters(text));
    }

    protected @NotNull String wrap(@NotNull String s) {
      return "(" + s + ")";
    }

    public @NotNull IElementType getElementType() {
      return myElementType;
    }

    @Override
    public String toString() {
      return myElementType.toString();
    }
  }

  private enum State {
    RECORD_START {
      @Override
      void nextToken(@NotNull CsvLexer lexer) throws IOException {
        lexer.getSymbolToken(lexer.myCsvFormat.prefix, TokenType.PREFIX);
      }

      @Override
      @NotNull
      CsvLexer.State nextState(@Nullable TokenType type) {
        return VALUE_START;
      }
    },
    RECORD_END {
      @Override
      void nextToken(@NotNull CsvLexer lexer) throws IOException {
        lexer.getSymbolToken(lexer.myCsvFormat.suffix, TokenType.SUFFIX);
      }

      @Override
      @NotNull
      CsvLexer.State nextState(@Nullable TokenType type) {
        return type == null || type == TokenType.RECORD_SEPARATOR ? VALUE_START : VALUE_END;
      }
    },
    VALUE_START {
      @Override
      void nextToken(@NotNull CsvLexer lexer) throws IOException {
        lexer.valueOrQuoted();
      }

      @Override
      void end(@NotNull CsvLexer lexer) throws IOException {
        lexer.valueOrQuoted();
      }

      @Override
      @NotNull CsvLexer.State nextState(@Nullable TokenType type) {
        return VALUE_END;
      }
    },
    VALUE_END {
      @Override
      void nextToken(@NotNull CsvLexer lexer) throws IOException {
        lexer.separator();
      }

      @Override
      @NotNull
      State nextState(@Nullable TokenType type) {
        return type == null ? RECORD_END :
               type == TokenType.SUFFIX ? VALUE_END :
               type == TokenType.RECORD_SEPARATOR ? RECORD_START :
               VALUE_START;
      }
    };

    abstract void nextToken(@NotNull CsvLexer lexer) throws IOException;
    void end(@NotNull CsvLexer lexer) throws IOException {
      lexer.setNullToken();
    }

    abstract @NotNull State nextState(@Nullable TokenType type) throws IOException;
  }
}
