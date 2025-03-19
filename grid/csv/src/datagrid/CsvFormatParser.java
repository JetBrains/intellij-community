package com.intellij.database.datagrid;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvRecord;
import com.intellij.database.csv.CsvRecordFormat;
import com.intellij.database.csv.ValueRange;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CsvFormatParser {
  private final CsvFormat myDataFormat;
  private final Map<Object, Pattern> myCompiledPatterns = new IdentityHashMap<>();

  private LookAhead myLookAhead;

  public CsvFormatParser(@NotNull CsvFormat format) {
    myDataFormat = format;
  }

  public static @NotNull List<String> values(@Nullable CsvRecordFormat format,
                                             @NotNull CharSequence sequence,
                                             @NotNull List<ValueRange> ranges) {
    return ContainerUtil.map(ranges, range -> {
      String value = range.value(sequence).toString();
      return !(range instanceof QuotedValueRange) && format != null && format.nullText != null && StringUtil.equals(value, format.nullText)
             ? null
             : value;
    });
  }

  public @Nullable CsvParserResult parse(@NotNull CharSequence sequence) {
    myLookAhead = new LookAhead();

    List<String> columnNames = null;

    CsvRecord headerRow = null;
    List<CsvRecord> rows = new ArrayList<>();
    int currentOffset = 0;
    int columnsCount = 0;

    CsvRecordFormat headerFormat = myDataFormat.headerRecord;
    if (headerFormat != null) {
      CsvRecord headerRecord = parseRecordNotSkippingLines(sequence, currentOffset, headerFormat, myDataFormat.rowNumbers);
      if (headerRecord == null) {
        return null;
      }
      headerRow = headerRecord;
      columnNames = values(null, sequence, headerRecord.values);
      columnsCount = columnNames.size();
      currentOffset = headerRecord.range.getEndOffset();
    }

    while (true) {
      // parse a record
      CsvRecord record = parseRecordNotSkippingLines(sequence, currentOffset, myDataFormat.dataRecord, myDataFormat.rowNumbers);
      if (record == null) {
        break;
      }

      // initialize columns
      if (columnNames == null) {
        columnNames = values(null, sequence, record.values);
      }

      columnsCount = Math.max(columnsCount, record.values.size());

      // emit parsed record
      currentOffset = record.range.getEndOffset();
      rows.add(record);
    }

    return columnNames == null ? null : new CsvParserResult(myDataFormat, sequence, rows, headerRow, columnsCount);
  }

  private @Nullable CsvRecord parseRecordNotSkippingLines(@NotNull CharSequence sequence,
                                                          int startOffset,
                                                          @NotNull CsvRecordFormat template,
                                                          boolean firstColumnIsTitle) {
    if (startOffset >= sequence.length()) return null;
    CsvRecord record = parseRecord(sequence, startOffset, template, firstColumnIsTitle);
    if (record != null) return record;
    int endOffset = skipLine(sequence, startOffset);
    ValueRange valueRange = new ValueRange(startOffset, endOffset);
    return new CsvRecord(new TextRange(startOffset, sequence.length() == endOffset ? endOffset : endOffset + 1), Collections.singletonList(valueRange), sequence.length() != endOffset);
  }

  private CsvRecord parseRecord(@NotNull CharSequence sequence, int startOffset, @NotNull CsvRecordFormat template, boolean firstColumnIsTitle) {
    List<ValueRange> titleAndValues = null;

    int endOffset = startOffset;
    endOffset = StringUtil.isEmpty(template.prefix) ? endOffset :
                StringUtil.startsWith(sequence, endOffset, template.prefix) ? endOffset + template.prefix.length() : -1;
    if (endOffset != -1) {
      Pair<Integer, List<ValueRange>> res = parseValues(sequence, endOffset, template);
      titleAndValues = res.getSecond();
      endOffset = res.getFirst();
    }
    endOffset = endOffset == -1 ? -1 :
                StringUtil.isEmpty(template.suffix) ? endOffset :
                StringUtil.startsWith(sequence, endOffset, template.suffix) ? endOffset + template.suffix.length() : -1;

    if (endOffset == -1 || titleAndValues == null) return null;

    boolean hasRecordSeparator = false;
    if (endOffset != sequence.length() && endOffset == myLookAhead.nextRecordSeparatorOffset(template, sequence, endOffset)) {
      endOffset += template.recordSeparator.length();
      hasRecordSeparator = true;
    }

    TextRange recordRange = new TextRange(startOffset, endOffset);

    return firstColumnIsTitle && titleAndValues.size() < 2 ? null :
           new CsvRecord(recordRange, titleAndValues, hasRecordSeparator);
  }

  private Pair<Integer, List<ValueRange>> parseValues(@NotNull CharSequence sequence,
                                                      int startOffset,
                                                      @NotNull CsvRecordFormat template) {
    int currentValuesEndOffset = startOffset;
    List<ValueRange> values = new ArrayList<>();
    Ref<ValueRange> valueRef = Ref.create();

    boolean first = true;
    int expectedValuesEndOffset = myLookAhead.valuesEndOffset(template, sequence, currentValuesEndOffset);
    while (currentValuesEndOffset < expectedValuesEndOffset || first && currentValuesEndOffset == expectedValuesEndOffset) {
      if (!first) {
        if (currentValuesEndOffset != myLookAhead.nextValueSeparatorOffset(template, sequence, currentValuesEndOffset)) {
          return new Pair<>(-1, null);
        }
        currentValuesEndOffset += template.valueSeparator.length();
      }

      int valueEndOffset = parseValue(valueRef, sequence, currentValuesEndOffset, template);
      if (valueEndOffset == -1) {
        return new Pair<>(-1, null);
      }

      values.add(Objects.requireNonNull(valueRef.get()));
      currentValuesEndOffset = valueEndOffset;

      first = false;
      expectedValuesEndOffset = myLookAhead.valuesEndOffset(template, sequence, currentValuesEndOffset);
    }

    return values.isEmpty() ? new Pair<>(-1, null) : new Pair<>(currentValuesEndOffset, values);
  }

  private int parseValue(Ref<ValueRange> value,
                         CharSequence sequence,
                         int startOffset,
                         CsvRecordFormat template) {
    int endOffset = template.trimWhitespace ? skipWhitespaceUpToNextDelimiterOrRecordSeparator(template, sequence, startOffset) : startOffset;
    int valueStartOffset = endOffset;

    CsvRecordFormat.Quotes detectedQuotes = null;
    for (CsvRecordFormat.Quotes quotes : template.quotes) {
      if (quotes.leftQuote.isEmpty() || quotes.rightQuote.isEmpty()) continue;
      if (StringUtil.startsWith(sequence, endOffset, quotes.leftQuote)) {
        detectedQuotes = quotes;
        break;
      }
    }

    if (detectedQuotes == null) {
      int valueEndOffset = myLookAhead.nextDelimiterOrRecordSeparatorOffset(template, sequence, valueStartOffset);

      endOffset = valueEndOffset;

      while (template.trimWhitespace && valueEndOffset > valueStartOffset && Character.isWhitespace(sequence.charAt(valueEndOffset - 1))) {
        valueEndOffset--;
      }

      value.set(new ValueRange(valueStartOffset, valueEndOffset));
    }
    else {
      Matcher matcher = allInQuotesPattern(detectedQuotes).matcher(sequence);
      boolean matched = matcher.find(valueStartOffset + detectedQuotes.leftQuote.length());
      assert matched; // matching should never fail as the regex matches an empty sequence.

      int valueEndOffset = matcher.end(0);

      endOffset = valueEndOffset;
      if (StringUtil.startsWith(sequence, valueEndOffset, detectedQuotes.rightQuote)) {
        valueEndOffset += detectedQuotes.rightQuote.length();
        int delimiter = myLookAhead.nextDelimiterOrRecordSeparatorOffset(template, sequence, valueEndOffset);
        if (delimiter != valueEndOffset && !StringUtil.contains(sequence, valueStartOffset, valueEndOffset, '\n')) {
          endOffset = delimiter;
          value.set(new ValueRange(valueStartOffset, delimiter));
        }
        else {
          endOffset = valueEndOffset;
          value.set(new QuotedValueRange(valueStartOffset, valueEndOffset, detectedQuotes));
        }
      }
      else if (sequence.length() != endOffset) {
        // we neither have a right quote, nor reached eof, hence we failed to parse a value
        return -1;
      } else {
        value.set(new ImproperQuotedValueRange(valueStartOffset, valueEndOffset, detectedQuotes));
      }

      if (template.trimWhitespace) {
        endOffset = skipWhitespaceUpToNextDelimiterOrRecordSeparator(template, sequence, endOffset);
      }
    }

    assert !value.isNull();

    return endOffset;
  }

  private @NotNull Pattern allInQuotesPattern(@NotNull CsvRecordFormat.Quotes quotes) {
    Pattern pattern = myCompiledPatterns.get(quotes);
    if (pattern == null) {
      pattern = Pattern.compile("(?:" +
                                Pattern.quote(quotes.rightQuoteEscaped) +
                                "|" +
                                "(?!" + Pattern.quote(quotes.rightQuote) + ")." +
                                ")*+", Pattern.DOTALL);
      myCompiledPatterns.put(quotes, pattern);
    }
    return pattern;
  }

  private int skipWhitespaceUpToNextDelimiterOrRecordSeparator(@NotNull CsvRecordFormat template,
                                                               @NotNull CharSequence sequence,
                                                               int offset) {
    return skipWhitespace(sequence, offset, myLookAhead.nextDelimiterOrRecordSeparatorOffset(template, sequence, offset));
  }

  private static String unescapeQuotes(CharSequence s, CsvRecordFormat.Quotes quotes) {
    List<String> escapedQuotes = Arrays.asList(quotes.leftQuoteEscaped, quotes.rightQuoteEscaped);
    List<String> unescapedQuotes = Arrays.asList(quotes.leftQuote, quotes.rightQuote);
    return StringUtil.replace(String.valueOf(s), escapedQuotes, unescapedQuotes);
  }

  private static int skipWhitespace(CharSequence sequence, int offset, int maxOffset) {
    maxOffset = Math.min(maxOffset, sequence.length());
    while (offset < maxOffset && Character.isWhitespace(sequence.charAt(offset))) {
      offset++;
    }
    return offset;
  }

  private static int skipLine(CharSequence sequence, int offset) {
    while (offset < sequence.length() && sequence.charAt(offset) != '\n') {
      offset++;
    }
    return offset;
  }

private static class QuotedValueRange extends ValueRange {
  protected final CsvRecordFormat.Quotes myQuotes;

  QuotedValueRange(int startOffset, int endOffset, @NotNull CsvRecordFormat.Quotes quotes) {
    super(startOffset, endOffset);
    myQuotes = quotes;
  }

  @Override
  public CharSequence value(CharSequence s) {
    return unescapeQuotes(unquotedRange().subSequence(s), myQuotes);
  }

  protected TextRange unquotedRange() {
    return new TextRange(getStartOffset() + myQuotes.leftQuote.length(), getEndOffset() - myQuotes.rightQuote.length());
  }
}

private static class ImproperQuotedValueRange extends QuotedValueRange {
  ImproperQuotedValueRange(int startOffset, int endOffset, @NotNull CsvRecordFormat.Quotes quotes) {
    super(startOffset, endOffset, quotes);
  }

  @Override
  protected TextRange unquotedRange() {
    return new TextRange(getStartOffset() + myQuotes.leftQuote.length(), getEndOffset());
  }
}
  private static class LookAhead {
    private int myNextRecordSeparatorRequestOffset = -1;
    private int myNextRecordSeparatorOffset = -1;

    public int valuesEndOffset(@NotNull CsvRecordFormat template, @NotNull CharSequence sequence, int startOffset) {
      int recordSeparatorOffset = nextRecordSeparatorOffset(template, sequence, startOffset);
      String suffix = StringUtil.notNullize(template.suffix);
      return StringUtil.endsWith(sequence, startOffset, recordSeparatorOffset, suffix)
             ? recordSeparatorOffset - suffix.length()
             : recordSeparatorOffset;
    }

    public int nextRecordSeparatorOffset(@NotNull CsvRecordFormat template, @NotNull CharSequence sequence, int startOffset) {
      if (startOffset >= myNextRecordSeparatorRequestOffset && startOffset <= myNextRecordSeparatorOffset) {
        return myNextRecordSeparatorOffset;
      }
      myNextRecordSeparatorRequestOffset = startOffset;
      myNextRecordSeparatorOffset = indexOfOrEnd(sequence, startOffset, template.recordSeparator);
      return myNextRecordSeparatorOffset;
    }

    public int nextValueSeparatorOffset(@NotNull CsvRecordFormat template, @NotNull CharSequence sequence, int startOffset) {
      return indexOfOrEnd(sequence, startOffset, template.valueSeparator);
    }

    public int nextDelimiterOrRecordSeparatorOffset(@NotNull CsvRecordFormat template, @NotNull CharSequence sequence, int offset) {
      int nextPartOffset = valuesEndOffset(template, sequence, offset);
      int nextValueSeparatorOffset = nextValueSeparatorOffset(template, sequence, offset);
      return Math.min(nextPartOffset, nextValueSeparatorOffset);
    }

    private static int indexOfOrEnd(@NotNull CharSequence sequence, int start, @Nullable CharSequence infix) {
      int idx = StringUtil.isEmpty(infix) ? -1 : StringUtil.indexOf(sequence, infix, start);
      return idx == -1 ? sequence.length() : idx;
    }
  }
}

