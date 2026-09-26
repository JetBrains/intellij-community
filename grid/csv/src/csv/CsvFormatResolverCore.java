package com.intellij.database.csv;

import com.intellij.database.datagrid.CsvReader;
import com.intellij.database.datagrid.StreamCsvFormatParser;
import com.intellij.database.dbimport.TypeMerger;
import com.intellij.database.remote.dbimport.ErrorRecord;
import com.intellij.database.settings.CsvSettings;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.database.csv.CsvFormatsSettings.formatsSimilar;
import static com.intellij.database.dbimport.CsvImportUtil.getPreferredTypeMergerBasedOnContent;

public class CsvFormatResolverCore {
  protected static final int LIMIT = 3000;

  public static @Nullable CsvFormat getMoreSuitableCsvFormat(@NotNull VirtualFile file, boolean tryDetectHeader,
                                                             @Nullable Supplier<List<CsvFormat>> existingFormatsSupplier) {
    CharSequence sequence = file instanceof LightVirtualFile ? ((LightVirtualFile)file).getContent() :
                            file.getFileType().isBinary() ? null :
                            LoadTextUtil.loadText(file, LIMIT);
    return sequence == null ? null : getMoreSuitableCsvFormat(sequence, tryDetectHeader, existingFormatsSupplier);
  }

  public static @Nullable CsvFormat getMoreSuitableCsvFormat(@NotNull CharSequence sequence) {
    return getMoreSuitableCsvFormat(sequence, true, null);
  }

  public static @Nullable CsvFormat getMoreSuitableCsvFormat(@NotNull CharSequence sequence, boolean tryDetectHeader, @Nullable Supplier<List<CsvFormat>> existingFormatsSupplier) {
    List<CsvFormat> formats = new ArrayList<>();
    List<CsvFormat> existingFormats =
      existingFormatsSupplier != null ? existingFormatsSupplier.get() : CsvSettings.getSettings().getCsvFormats();
    for (CsvFormat format : existingFormats) {
      if (tryDetectHeader || isSimple(format)) {
        formats.add(format);
      }
    }
    for (CsvFormat format : existingFormats) {
      if (isSimple(format)) continue;
      CsvFormat f = simplifyFormat(format);
      if (!ContainerUtil.exists(formats, ff -> formatsSimilar(f, ff))) {
        formats.add(f);
      }
    }
    List<MyParserResult> results = new ArrayList<>();
    for (CsvFormat format : formats) {
      StreamCsvFormatParser parser = new StreamCsvFormatParser(format, LIMIT, new CsvReader(new StringReader(sequence.toString())));
      try {
        results.add(new MyParserResult(parser.parse(), format));
      }
      catch (IOException e) {
        results.add(new MyParserResult(null, format));
      }
    }
    results.sort(null);
    MyParserResult result = ContainerUtil.getFirstItem(results);
    if (result == null) return null;
    CsvFormat format = result.myFormat;
    boolean addHeader = tryDetectHeader && format.headerRecord == null && !result.myHeaderAsExpected;
    if (!addHeader && existingFormats.contains(format)) return format;
    return withUniqueName(
      existingFormats,
      addHeader
      ? addHeader(format)
      : format
    );
  }

  private static @Nullable CsvFormat withUniqueName(List<CsvFormat> existingFormats, CsvFormat resultFormat) {
    if (resultFormat != null && !existingFormats.contains(resultFormat)) {
      String name = getNewFormatName(resultFormat, existingFormats);
      if (!Objects.equals(resultFormat.name, name)) {
        resultFormat = new CsvFormat(name, resultFormat.dataRecord, resultFormat.headerRecord, resultFormat.id, resultFormat.rowNumbers);
      }
    }
    return resultFormat;
  }

  private static boolean firstRowSeemsToBeHeader(@NotNull StreamCsvFormatParser.CsvParserResult result) {
    JBIterable<StreamCsvFormatParser.Token[]> records = asIterable(result.getHeader()).append(result.getRecords());
    StreamCsvFormatParser.Token[] first = records.first();
    if (first == null) return false;
    int columnsCount = Math.min(20, first.length);
    for (int i = 0; i < columnsCount; i++) {
      int idx = i;
      JBIterable<@Nullable String> values = records.map(record -> record[idx].getValue()).take(200);
      if (firstValueSeemsToBeHeader(values)) return true;
    }
    return false;
  }

  public static boolean firstValueSeemsToBeHeader(JBIterable<@Nullable String> values) {
    TypeMerger.StringMerger stringMerger = new TypeMerger.StringMerger("");
    TypeMerger.DoubleMerger numericMerger = new TypeMerger.DoubleMerger("");
    TypeMerger merger = getPreferredTypeMergerBasedOnContent(values, stringMerger, numericMerger);
    JBIterable<@Nullable String> valuesWithoutFirstLine = values.skip(1);
    TypeMerger mergerWithoutFirstLine = getPreferredTypeMergerBasedOnContent(valuesWithoutFirstLine, stringMerger, numericMerger);
    return merger != mergerWithoutFirstLine;
  }

  private static @NotNull JBIterable<StreamCsvFormatParser.Token[]> asIterable(StreamCsvFormatParser.Token[] header) {
    return header == null ? JBIterable.empty() : JBIterable.from(
      Collections.singletonList(header));
  }

  private static CsvFormat addHeader(CsvFormat format) {
    return new CsvFormat(format.name, format.dataRecord, format.dataRecord, format.id, false);
  }

  public static boolean isSimple(@NotNull CsvFormat format) {
    return !format.rowNumbers && format.headerRecord == null;
  }

  @Contract("null -> null; !null -> !null")
  public static CsvFormat simplifyFormat(@Nullable CsvFormat format) {
    if (format == null || format.headerRecord == null && !format.rowNumbers) {
      return format;
    }
    return new CsvFormat(format.name + " without header", format.dataRecord, null, false);
  }

  private static final class MyParserResult implements Comparable<MyParserResult> {
    private final StreamCsvFormatParser.CsvParserResult myResult;
    private final CsvFormat myFormat;
    private final boolean myHeaderAsExpected;

    private MyParserResult(@Nullable StreamCsvFormatParser.CsvParserResult result, @NotNull CsvFormat format) {
      myResult = result;
      myFormat = format;
      myHeaderAsExpected = result == null || (format.headerRecord != null) == firstRowSeemsToBeHeader(result);
    }

    @Override
    public int compareTo(@NotNull MyParserResult o) {
      if (myResult == null) return o.myResult == null ? 0 : 1;
      if (o.myResult == null) return -1;

      List<StreamCsvFormatParser.Token[]> records = myResult.getRecords();
      List<StreamCsvFormatParser.Token[]> oRecords = o.myResult.getRecords();
      if (records.isEmpty()) return oRecords.isEmpty() ? 0 : 1;
      if (oRecords.isEmpty()) return -1;

      int compare = Integer.compare(oRecords.get(0).length, records.get(0).length);
      if (compare != 0) return compare;
      compare = Integer.compare(errorsCount(myResult), errorsCount(o.myResult));
      if (compare != 0) return compare;
      return Boolean.compare(o.myHeaderAsExpected, myHeaderAsExpected);
    }

    private static int errorsCount(@NotNull StreamCsvFormatParser.CsvParserResult result) {
      List<ErrorRecord> errors = result.getErrors();
      ErrorRecord item = ContainerUtil.getLastItem(errors);
      if (item == null) return 0;

      String message = item.getMessage();
      return StringUtil.containsIgnoreCase(message, StreamCsvFormatParser.CsvParserException.END_OF_FILE) ?
             errors.size() - 1 :
             errors.size();
    }
  }

  public static @NotNull String getNewFormatName(@NotNull CsvFormat templateFormat, @NotNull List<CsvFormat> formats) {
    String base = templateFormat.name;

    if (isUnique(base, formats)) {
      return base;
    }

    for (int i = 1; ; i++) {
      String name = base + "_" + i;
      if (isUnique(name, formats)) {
        return name;
      }
    }
  }

  private static boolean isUnique(@NotNull String name, @NotNull List<CsvFormat> formats) {
    return CsvFormat.indexOfFormatNamed(formats, name) == -1;
  }
}
