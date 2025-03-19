package com.intellij.database.csv;

import com.intellij.database.settings.CsvSettings;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.database.csv.CsvFormatsSettings.formatsSimilar;

public class CsvFormatter {
  private final CsvFormat myFormat;

  public CsvFormatter(@NotNull CsvFormat format) {
    myFormat = format;
  }

  public @NotNull CsvFormat getFormat() {
    return myFormat;
  }

  public @NotNull String formatValue(@Nullable Object value) {
    return formatValue(myFormat.dataRecord, value);
  }

  public @NotNull String formatHeaderValue(@Nullable Object value) {
    return formatValue(ObjectUtils.notNull(myFormat.headerRecord, myFormat.dataRecord), value);
  }

  public @NotNull String valueSeparator() {
    return valueSeparator(myFormat.dataRecord);
  }

  public @NotNull String headerValueSeparator() {
    return valueSeparator(ObjectUtils.notNull(myFormat.headerRecord, myFormat.dataRecord));
  }

  public @NotNull String recordSeparator() {
    return myFormat.dataRecord.recordSeparator;
  }

  public @NotNull String formatRecord(@NotNull List<?> values) {
    return formatRecord(myFormat.dataRecord, values);
  }

  public String formatHeader(@NotNull List<?> values) {
    return formatRecord(ObjectUtils.notNull(myFormat.headerRecord, myFormat.dataRecord), values);
  }

  public boolean requiresRowNumbers() {
    return myFormat.rowNumbers;
  }

  protected @NotNull String valueToRawText(@Nullable Object value) {
    return String.valueOf(value);
  }

  private @NotNull String formatRecord(@NotNull CsvRecordFormat recordTemplate, @NotNull List<?> values) {
    StringBuilder sb = new StringBuilder();
    sb.append(StringUtil.notNullize(recordTemplate.prefix));
    for (Object value : values) {
      sb.append(formatValue(recordTemplate, value)).append(valueSeparator(recordTemplate));
    }
    sb.setLength(values.isEmpty() ? sb.length() : sb.length() - valueSeparator(recordTemplate).length());
    sb.append(StringUtil.notNullize(recordTemplate.suffix));
    return sb.toString();
  }

  private @NotNull String formatValue(@NotNull CsvRecordFormat recordTemplate, @Nullable Object value) {
    String valueText = value == null ? StringUtil.notNullize(recordTemplate.nullText) : valueToRawText(value);
    valueText = recordTemplate.trimWhitespace ? StringUtil.trimLeading(StringUtil.trimTrailing(valueText)) : valueText;
    CsvRecordFormat.QuotationPolicy quotationPolicy = recordTemplate.quotationPolicy;
    CsvRecordFormat.Quotes quotes = value == null ? null : getQuotes(recordTemplate, valueText, quotationPolicy);
    return quotes != null ? quotes.leftQuote + escapeQuotes(valueText, quotes) + quotes.rightQuote : valueText;
  }

  private static @Nullable CsvRecordFormat.Quotes getQuotes(@NotNull CsvRecordFormat recordTemplate,
                                                            @NotNull String valueText,
                                                            @NotNull CsvRecordFormat.QuotationPolicy quotationPolicy) {
    CsvRecordFormat.Quotes quote = ContainerUtil.getFirstItem(recordTemplate.quotes);
    if (quotationPolicy == CsvRecordFormat.QuotationPolicy.ALWAYS) {
      return quote;
    }
    else if (quotationPolicy == CsvRecordFormat.QuotationPolicy.AS_NEEDED) {
      return shouldQuote(recordTemplate, valueText, quote) ? quote : null;
    }
    else if (quotationPolicy == CsvRecordFormat.QuotationPolicy.NEVER) {
      return null;
    }
    throw new AssertionError("Unhandled quotation policy: " + quotationPolicy);
  }

  private static @NotNull String escapeQuotes(@NotNull CharSequence s, @NotNull CsvRecordFormat.Quotes quotes) {
    boolean leftIsEmpty = quotes.leftQuote.isEmpty();
    boolean rightIsEmpty = quotes.rightQuote.isEmpty();
    List<String> escapedQuotes = ContainerUtil.filter(
      Arrays.asList(leftIsEmpty ? null : quotes.leftQuoteEscaped, rightIsEmpty ? null : quotes.rightQuoteEscaped),
      Conditions.notNull()
    );
    List<String> unescapedQuotes = ContainerUtil.filter(
      Arrays.asList(leftIsEmpty ? null : quotes.leftQuote, rightIsEmpty ? null : quotes.rightQuote),
      Conditions.notNull()
    );
    return StringUtil.replace(s.toString(), unescapedQuotes, escapedQuotes);
  }

  private static @NotNull String valueSeparator(@NotNull CsvRecordFormat template) {
    return template.valueSeparator;
  }

  private static boolean shouldQuote(@NotNull CsvRecordFormat recordTemplate,
                                     @NotNull String valueText,
                                     @Nullable CsvRecordFormat.Quotes quote) {
    return StringUtil.contains(valueText, recordTemplate.valueSeparator) ||
           StringUtil.contains(valueText, recordTemplate.recordSeparator) ||
           quote != null && StringUtil.contains(valueText, quote.rightQuote) ||
           quote != null && StringUtil.contains(valueText, quote.leftQuote) ||
           StringUtil.isNotEmpty(recordTemplate.prefix) && StringUtil.contains(valueText, recordTemplate.prefix) ||
           StringUtil.isNotEmpty(recordTemplate.suffix) && StringUtil.contains(valueText, recordTemplate.suffix) ||
           StringUtil.equals(valueText, recordTemplate.nullText);
  }

  public static @NotNull CsvFormat setFirstRowIsHeader(@NotNull CsvFormat currentFormat, boolean value) {
    String withHeader = " " + GridCsvBundle.message("csv.format.with.header");
    String withoutHeader = " " + GridCsvBundle.message("csv.format.without.header");
    List<CsvFormat> formats = CsvSettings.getSettings().getCsvFormats();
    boolean isTemp = ContainerUtil.find(formats, f -> f.id.equals(currentFormat.id)) == null;
    String newName = isTemp ? StringUtil.trimEnd(StringUtil.trimEnd(currentFormat.name, withHeader), withoutHeader) : currentFormat.name;
    newName += value ? withHeader : withoutHeader;
    CsvFormat newFormat = new CsvFormat(newName, currentFormat.dataRecord, value ? currentFormat.dataRecord : null, currentFormat.rowNumbers);
    CsvFormat existingFormat = ContainerUtil.find(formats, f -> formatsSimilar(f, newFormat));
    return ObjectUtils.notNull(existingFormat, newFormat);
  }
}
