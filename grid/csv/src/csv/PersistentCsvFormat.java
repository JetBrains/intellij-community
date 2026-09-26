package com.intellij.database.csv;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Tag("csv-format")
public class PersistentCsvFormat {
  @Attribute("name")
  public String name;
  @Attribute("id")
  public String id = UUID.randomUUID().toString();
  @Tag("data")
  public Record data;
  @Tag("header")
  public Record header;
  @Attribute("row-numbers")
  public boolean rowNumbers;


  @SuppressWarnings("unused")
  public PersistentCsvFormat() {
    this("");
  }

  public PersistentCsvFormat(@NotNull CsvFormat format) {
    this(format.id, format.name, format);
  }

  public PersistentCsvFormat(@NotNull String id, @NotNull String name, @NotNull CsvFormat format) {
    this(name);
    this.id = id;
    this.data = new Record(format.dataRecord);
    this.header = format.headerRecord != null ? new Record(format.headerRecord) : null;
    this.rowNumbers = format.rowNumbers;
  }

  private PersistentCsvFormat(@NotNull String name) {
    this.name = name;
  }

  public @Nullable CsvFormat immutable() {
    if (!isValid()) return null;

    String name = Objects.requireNonNull(this.name);
    CsvRecordFormat dataFormat = Objects.requireNonNull(data.immutable());
    CsvRecordFormat header = this.header != null ? Objects.requireNonNull(this.header.immutable()) : null;
    return new CsvFormat(name, dataFormat, header, id, rowNumbers);
  }

  private boolean isValid() {
    return name != null &&
           data != null && data.isValid() &&
           (header == null || header.isValid());
  }

  @Tag("record-format")
  public static class Record {
    private static final String QUOTATION_POLICY_NEVER = "never";
    private static final String QUOTATION_POLICY_ALWAYS = "always";
    private static final String QUOTATION_POLICY_AS_NEEDED = "as needed";

    @Attribute("prefix")
    public String prefix;
    @Attribute("suffix")
    public String suffix;
    @Attribute("nullText")
    public String nullText;
    @Attribute("quotationPolicy")
    public String quotationPolicy;
    @Attribute("valueSeparator")
    public String valueSeparator;
    @Attribute("recordSeparator")
    public String recordSeparator;
    @Attribute("trimWhitespace")
    public boolean trimWhitespace;

    @XCollection(propertyElementName = "quotation")
    public List<Quotes> quotes = new ArrayList<>();

    @SuppressWarnings("unused")
    public Record() {
    }

    public Record(@NotNull CsvRecordFormat record) {
      prefix = record.prefix;
      suffix = record.suffix;
      nullText = record.nullText;
      quotationPolicy = valueOfQuotationPolicy(record.quotationPolicy);
      valueSeparator = record.valueSeparator;
      recordSeparator = record.recordSeparator;
      trimWhitespace = record.trimWhitespace;
      quotes = ContainerUtil.map(record.quotes, quotes1 -> new Quotes(quotes1));
    }

    private @NotNull CsvRecordFormat immutable() {
      CsvRecordFormat.QuotationPolicy qp = Objects.requireNonNull(quotationPolicy(quotationPolicy));
      List<CsvRecordFormat.Quotes> immutableQuotes =
        ContainerUtil.map(quotes, quotes1 -> new CsvRecordFormat.Quotes(quotes1.left, quotes1.right, quotes1.leftEscaped, quotes1.rightEscaped));

      return new CsvRecordFormat(prefix, suffix, nullText, immutableQuotes, qp, valueSeparator, recordSeparator, trimWhitespace);
    }

    private boolean isValid() {
      return prefix != null &&
             suffix != null &&
             quotationPolicy(quotationPolicy) != null &&
             valueSeparator != null &&
             recordSeparator != null &&
             quotes != null &&
             ContainerUtil.find(quotes, quotes1 -> quotes1 == null || !quotes1.isValid()) == null;
    }

    private static @Nullable CsvRecordFormat.QuotationPolicy quotationPolicy(@NotNull String qp) {
      return QUOTATION_POLICY_AS_NEEDED.equals(qp) ? CsvRecordFormat.QuotationPolicy.AS_NEEDED :
             QUOTATION_POLICY_ALWAYS.equals(qp) ? CsvRecordFormat.QuotationPolicy.ALWAYS :
             QUOTATION_POLICY_NEVER.equals(qp) ? CsvRecordFormat.QuotationPolicy.NEVER : null;
    }

    private static @NotNull String valueOfQuotationPolicy(@NotNull CsvRecordFormat.QuotationPolicy policy) {
      return switch (policy) {
        case ALWAYS -> QUOTATION_POLICY_ALWAYS;
        case AS_NEEDED -> QUOTATION_POLICY_AS_NEEDED;
        case NEVER -> QUOTATION_POLICY_NEVER;
      };
    }
  }

  @Tag("quotes")
  public static class Quotes {
    @Attribute("left")
    public String left;
    @Attribute("right")
    public String right;
    @Attribute("leftEscaped")
    public String leftEscaped;
    @Attribute("rightEscaped")
    public String rightEscaped;

    @SuppressWarnings("unused")
    public Quotes() {
    }

    public Quotes(@NotNull CsvRecordFormat.Quotes quotes) {
      left = quotes.leftQuote;
      right = quotes.rightQuote;
      leftEscaped = quotes.leftQuoteEscaped;
      rightEscaped = quotes.rightQuoteEscaped;
    }

    private boolean isValid() {
      return left != null && right != null && leftEscaped != null && rightEscaped != null;
    }
  }
}
