package com.intellij.database.datagrid;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvRecordFormat;
import com.intellij.database.datagrid.CsvLexer.TokenType;
import com.intellij.database.remote.dbimport.ErrorRecord;
import com.intellij.database.remote.dbimport.OffsetRecord;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StreamCsvFormatParser {
  private static final int ERROR_TEXT_SIZE = 15;
  private static final int NOT_IMPORTANT = -1;

  private final int myMaxCharsReadPerBatch;

  private final CsvFormat myDataFormat;

  private List<ErrorRecord> myErrors;
  private Token[] myHeader;
  private final CsvLexer myLexer;

  public StreamCsvFormatParser(@NotNull CsvFormat format, int maxCharsReadPerBatch, @NotNull CsvReader reader) {
    myDataFormat = format;
    myMaxCharsReadPerBatch = maxCharsReadPerBatch;
    myLexer = new CsvLexer(reader);
    myLexer.setCsvFormat(ObjectUtils.chooseNotNull(myDataFormat.headerRecord, myDataFormat.dataRecord));
  }

  public @Nullable CsvParserResult parse() throws IOException {
    long startCharacters = myLexer.getCharacters();
    if (startCharacters != 0 && myHeader == null) return null;

    myErrors = new ArrayList<>();
    List<Token[]> result = new ArrayList<>();
    if (!parseHeader(startCharacters)) {
      if (myErrors.isEmpty()) myErrors.add(new MyRecord());
      return new CsvParserResult(null, ContainerUtil.emptyList(), myErrors, myLexer.getCharacters() - startCharacters);
    }
    if (startCharacters == 0 && myDataFormat.headerRecord == null) result.add(myHeader);
    parseRecords(result, startCharacters);

    return !result.isEmpty() || !myErrors.isEmpty() ?
           new CsvParserResult(myDataFormat.headerRecord == null ? null : myHeader, result, myErrors, myLexer.getCharacters() - startCharacters) :
           null;
  }

  private void parseRecords(@NotNull List<Token[]> result, long startCharacters) throws IOException {
    int valuesPerRecordCount = myDataFormat.rowNumbers ? myHeader.length + 1 : myHeader.length;
    while (myLexer.isReady() && (myLexer.getCharacters() - startCharacters < myMaxCharsReadPerBatch || result.isEmpty())) {
      Token[] e = getRecord(myDataFormat.dataRecord, true, valuesPerRecordCount);
      if (e == null) break;
      result.add(e);
    }
  }

  private boolean parseHeader(long startCharacters) throws IOException {
    if (myHeader != null) return true;

    CsvRecordFormat headerFormat = myDataFormat.headerRecord == null ? myDataFormat.dataRecord : myDataFormat.headerRecord;
    while (myLexer.getCharacters() - startCharacters < myMaxCharsReadPerBatch && myLexer.isReady() && myHeader == null) {
      myHeader = getRecord(headerFormat, myDataFormat.headerRecord == null, NOT_IMPORTANT);
    }
    myLexer.setCsvFormat(myDataFormat.dataRecord);
    return myHeader != null;
  }

  private Token @Nullable [] getRecord(@NotNull CsvRecordFormat format, boolean allowNulls, int valuesPerRecordCount) throws IOException {
    List<Token> record = null;
    while (myLexer.isReady() && record == null) {
      try {
        record = parseRecord(format, allowNulls, valuesPerRecordCount);
      }
      catch (CsvParserException e) {
        myErrors.add(new OffsetRecord(e, e.getLine(), e.getOffset()));
      }
    }
    return record == null ? null : ContainerUtil.toArray(record, Token[]::new);
  }

  private @NotNull List<Token> parseRecord(@NotNull CsvRecordFormat format,
                                           boolean allowNulls,
                                           int valuesPerRecordCount) throws IOException, CsvParserException {
    List<Token> result = new ArrayList<>();
    parseRecordChain(format, allowNulls, valuesPerRecordCount, result);
    return result.isEmpty() || !myDataFormat.rowNumbers ? result : ContainerUtil.subList(result, 1);
  }

  private void parseRecordChain(@NotNull CsvRecordFormat format,
                         boolean allowNulls,
                         int valuesPerRecordCount,
                         List<Token> result) throws IOException, CsvParserException {
    myLexer.advance();
    maybe(TokenType.PREFIX);
    if (myLexer.getType() == null) return;
    if (!valuesChain(format, result, allowNulls, valuesPerRecordCount)) {
      return;
    }
    checkUnexpectedCount(format, result, valuesPerRecordCount);
    maybe(TokenType.SUFFIX);
    assertTypesOrNull(format, TokenType.RECORD_SEPARATOR);
  }

  private boolean valuesChain(@NotNull CsvRecordFormat format,
                                  @NotNull List<Token> result,
                                  boolean allowNulls,
                                  int valuesPerRecordCount) throws CsvParserException, IOException {
    while (shouldReadValue()) {
      value(format, result, allowNulls, valuesPerRecordCount);
    }
    return true;
  }

  private void value(@NotNull CsvRecordFormat format,
                     @NotNull List<Token> result,
                     boolean allowNulls,
                     int valuesPerRecordCount) throws CsvParserException, IOException {
    assertTokenType(format, TokenType.VALUE, TokenType.QUOTED_VALUE);
    result.add(createValueToken(allowNulls ? format : null));
    myLexer.advance();
    if (valuesPerRecordCount != NOT_IMPORTANT && valuesPerRecordCount == result.size() && myLexer.getType() != null) {
      countChain(format);
    }
    if (checkIfTypesOrNull(TokenType.RECORD_SEPARATOR, TokenType.SUFFIX)) {
      return;
    }
    assertTokenType(format, TokenType.VALUE_SEPARATOR);
    myLexer.advance();
  }

  private void countChain(@NotNull CsvRecordFormat format) throws CsvParserException, IOException {
    maybe(TokenType.SUFFIX);
    assertTypesOrNull(format, TokenType.RECORD_SEPARATOR);
  }

  private @Nullable Token createValueToken(@Nullable CsvRecordFormat format) {
    String text = myLexer.getText();
    if (text == null) return null;
    if (text.length() >= CsvLexer.MAX_CHARACTERS) myErrors.add(
      new OffsetRecord(new MaxCharactersReachedException(cut(text)), myLexer.getTokenLine(), myLexer.getOffset())
    );
    return createToken(format);
  }

  private void checkUnexpectedCount(@NotNull CsvRecordFormat format,
                                           @NotNull List<Token> result,
                                           int count) throws CsvParserException, IOException {
    if (count != NOT_IMPORTANT && result.size() < count) throw newParserException(format, TokenType.VALUE);
  }

  private boolean shouldReadValue() {
    TokenType type = myLexer.getType();
    return type != null && type != TokenType.SUFFIX && type != TokenType.RECORD_SEPARATOR;
  }

  private @NotNull StreamCsvFormatParser.CsvParserException newParserException(@NotNull CsvRecordFormat format,
                                                                               TokenType @NotNull ... types) throws IOException {
    TokenType type = myLexer.getType();
    boolean hasToken = type == null;
    String text = hasToken ? cut(myLexer.readAhead(ERROR_TEXT_SIZE)) : myLexer.getText();
    long character = hasToken ? myLexer.getLineCharacters() : myLexer.getOffset();
    long line = hasToken ? myLexer.getLine() : myLexer.getTokenLine();
    return new CsvParserException(
      text == null ? null : StringUtil.wrapWithDoubleQuote(text),
      createToken(null),
      format,
      character,
      line,
      types
    );
  }

  @Contract("null -> null; !null -> !null")
  private static String cut(@Nullable String text) {
    return text == null ? null : text.substring(0, Math.min(text.length(), ERROR_TEXT_SIZE)) + "...";
  }

  private @Nullable Token createToken(@Nullable CsvRecordFormat format) {
    if (!myLexer.hasToken()) return null;
    String text = Objects.requireNonNull(myLexer.getText());
    TokenType type = Objects.requireNonNull(myLexer.getType());
    boolean isNull = type != TokenType.QUOTED_VALUE && format != null && StringUtil.equals(text, format.nullText);
    if (type == TokenType.QUOTED_VALUE) {
      CsvRecordFormat fmt = myLexer.getCsvFormat();
      for (CsvRecordFormat.Quotes quote : fmt.quotes) {
        if (quote.isQuoted(text)) {
          text = unquote(text, quote, fmt.trimWhitespace);
          break;
        }
      }
    }
    if (type == TokenType.VALUE && myLexer.getCsvFormat().trimWhitespace) {
      text = text.strip();
    }
    return new Token(text, isNull, type, myLexer.getTokenLine(), myLexer.getOffset());
  }

  private static String unquote(String text, CsvRecordFormat.Quotes quote, boolean trim) {
    int from = quote.leftQuote.length();
    int to = text.length() - quote.rightQuote.length();
    if (trim) {
      while (from < to && Character.isWhitespace(text.charAt(from))) {
        ++from;
      }
      while (from < to && Character.isWhitespace(text.charAt(to - 1))) {
        --to;
      }
    }
    if (to <= from) return "";
    int idx = StringUtil.indexOf(text, quote.rightQuoteEscaped, from, to);
    if (idx == -1) return text.substring(from, to);
    StringBuilder res = new StringBuilder(text.length() * 2);
    while (idx != -1) {
      res.append(text, from, idx);
      res.append(quote.rightQuote);
      from = idx + quote.rightQuoteEscaped.length();
      idx = StringUtil.indexOf(text, quote.rightQuoteEscaped, from, to);
    }
    res.append(text, from, to);
    return res.toString();
  }

  private static void restore(@NotNull CsvLexer lexer) throws IOException {
    while (lexer.isReady() && lexer.getType() != TokenType.RECORD_SEPARATOR) {
      lexer.advance();
    }
  }

  public static class CsvParserException extends Exception {
    public static final String END_OF_FILE = "end of file";

    private static final String MASK = "actual: %s, expected: %s";

    private final long myCharacter;
    private final long myLine;

    CsvParserException(@Nullable String text,
                       @Nullable Token actual,
                       @NotNull CsvRecordFormat format,
                       long character,
                       long line,
                       TokenType @NotNull ... expected) {
      super(getMessageText(format, actual, text, expected));
      myCharacter = character;
      myLine = line;
    }

    public long getOffset() {
      return myCharacter;
    }

    public long getLine() {
      return myLine;
    }

    private static @NotNull String getMessageText(@NotNull CsvRecordFormat format,
                                                  @Nullable Token token,
                                                  @Nullable String text,
                                                  TokenType @NotNull ... types) {
      return String.format(MASK, getActualText(token, text), getExpected(format, types));
    }

    private static @NotNull String getExpected(@NotNull CsvRecordFormat format, TokenType @NotNull ... types) {
      return StringUtil.escapeStringCharacters(StringUtil.join(types, type -> type.getDebugName(format), ", "));
    }

    private static @NotNull String getActualText(@Nullable Token token, @Nullable String text) {
      return token != null ? token.getType().getTokenRepresentation(token.getText()) :
             text == null ? END_OF_FILE :
             StringUtil.wrapWithDoubleQuote(StringUtil.escapeStringCharacters(text));
    }
  }

  public static class CsvParserResult {
    private final Token[] myHeader;
    private final List<Token[]> myRecords;
    private final List<ErrorRecord> myErrors;
    private final long myCharacters;

    public CsvParserResult(Token @Nullable [] header,
                           @NotNull List<Token[]> records,
                           @NotNull List<ErrorRecord> errors,
                           long characters) {
      myHeader = header;
      myRecords = records;
      myErrors = errors;
      myCharacters = characters;
    }

    public Token @Nullable [] getHeader() {
      return myHeader;
    }

    public @NotNull List<Token[]> getRecords() {
      return myRecords;
    }

    public long getCharacters() {
      return myCharacters;
    }

    public @NotNull List<ErrorRecord> getErrors() {
      return myErrors;
    }

    public @NotNull String getErrorText() {
      return StringUtil.join(myErrors, ErrorRecord::getMessage, "\n");
    }
  }

  public static class Token {
    private final String myText;
    private final boolean myIsNull;
    private final TokenType myType;
    private final long myLine;
    private final long myOffset;

    public Token(@NotNull String text, boolean isNull, @NotNull TokenType type, long line, long offset) {
      myText = text;
      myIsNull = isNull;
      myType = type;
      myLine = line;
      myOffset = offset;
    }

    public @NotNull String getText() {
      return myText;
    }

    public @Nullable String getValue() {
      return myIsNull ? null : myText;
    }

    public @NotNull TokenType getType() {
      return myType;
    }

    public long getLine() {
      return myLine;
    }

    public long getOffset() {
      return myOffset;
    }
  }

  private void assertTokenType(@NotNull CsvRecordFormat format,
                                      TokenType @NotNull ... types) throws IOException, CsvParserException {
    CsvLexer.TokenType type = myLexer.getType();
    if (type != null && ArrayUtil.contains(type, types)) return;
    CsvParserException e = newParserException(format, types);
    if (type == TokenType.RECORD_SEPARATOR) throw e;
    restore(myLexer);
    throw e;
  }

  private boolean checkIfTypesOrNull(TokenType @NotNull ... types) {
    return myLexer.getType() == null || ArrayUtil.contains(myLexer.getType(), types);
  }


  private void assertTypesOrNull(CsvRecordFormat format, TokenType @NotNull ... types) throws IOException, CsvParserException {
    if (myLexer.getType() == null) return;
    assertTokenType(format, types);
  }

  private void maybe(@NotNull TokenType type) throws IOException {
    TokenType tokenType = myLexer.getType();
    if (tokenType == type) myLexer.advance();
  }

  public static class MaxCharactersReachedException extends Exception {
    public MaxCharactersReachedException(@NotNull String text) {
      super(String.format("too long value: \"%s\"", StringUtil.escapeStringCharacters(text)));
    }
  }

  private static class MyRecord extends ErrorRecord {

    protected MyRecord() {
      super(0);
    }

    @Override
    public @NotNull String getMessage() {
      return "couldn't parse header";
    }
  }
}
