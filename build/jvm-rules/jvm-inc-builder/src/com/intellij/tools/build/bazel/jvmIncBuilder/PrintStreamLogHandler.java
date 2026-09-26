package com.intellij.tools.build.bazel.jvmIncBuilder;

import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class PrintStreamLogHandler extends Handler {
  private final Span mySpan;
  private final Map<String, Attributes> myAttributesCache = new ConcurrentHashMap<>();
  private final PrintStream myPrintSteam;

  public PrintStreamLogHandler(Level logLevel, PrintStream stream, Span span) {
    myPrintSteam = stream;
    mySpan = span;
    setFormatter(new RecordFormatter());
    setLevel(logLevel);
  }

  @Override
  public void publish(LogRecord logRecord) {
    if (!isLoggable(logRecord)) {
      return;
    }
    String message = getFormatter().format(logRecord);
    if (message != null) {
      mySpan.addEvent(message, getOTAttributes(logRecord));
      myPrintSteam.append(message);
    }
    Throwable ex = logRecord.getThrown();
    if (ex != null) {
      mySpan.recordException(ex, getOTAttributes(logRecord));
    }
  }

  private Attributes getOTAttributes(LogRecord logRecord) {
    return myAttributesCache.computeIfAbsent(logRecord.getLoggerName(), category -> Attributes.of(AttributeKey.stringKey("category"), category));
  }

  @Override
  public void flush() {
    myPrintSteam.flush();
  }

  @Override
  public void close() throws SecurityException {
    myPrintSteam.flush();
    myAttributesCache.clear();
  }

  private static class RecordFormatter extends Formatter {
    private static final String LINE_SEPARATOR = System.lineSeparator();

    private final long logCreation;

    RecordFormatter() {
      logCreation = System.currentTimeMillis();
    }


    protected long getStartedMillis() {
      return logCreation;
    }

    @Override
    public String format(LogRecord record) {
      long recordMillis = record.getMillis(), startedMillis = getStartedMillis();
      StringBuilder sb = new StringBuilder();

      LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(recordMillis), ZoneId.systemDefault());
      sb.append(date.getYear());
      sb.append('-');
      appendWithPadding(sb, Integer.toString(date.getMonthValue()), 2, '0');
      sb.append('-');
      appendWithPadding(sb, Integer.toString(date.getDayOfMonth()), 2, '0');
      sb.append(' ');
      appendWithPadding(sb, Integer.toString(date.getHour()), 2, '0');
      sb.append(':');
      appendWithPadding(sb, Integer.toString(date.getMinute()), 2, '0');
      sb.append(':');
      appendWithPadding(sb, Integer.toString(date.getSecond()), 2, '0');
      sb.append(',');
      appendWithPadding(sb, Long.toString(recordMillis % 1000), 3, '0');
      sb.append(' ');

      sb.append('[');
      appendWithPadding(sb, startedMillis == 0 ? "-------" : String.valueOf(recordMillis - startedMillis), 7, ' ');
      sb.append("] ");

      Level level = record.getLevel();
      appendWithPadding(sb, level == Level.WARNING ? "WARN" : level.getName(), 6, ' ');

      sb.append(" - ")
        .append(record.getLoggerName())
        .append(" - ")
        .append(formatMessage(record))
        .append(LINE_SEPARATOR);

      if (record.getThrown() != null) {
        appendThrowable(record.getThrown(), sb);
      }

      return sb.toString();
    }

    private static void appendWithPadding(StringBuilder sb, String s, int width, char padChar) {
      for (int i = 0, padding = width - s.length(); i < padding; i++) sb.append(padChar);
      sb.append(s);
    }

    private static void appendThrowable(Throwable thrown, StringBuilder sb) {
      StringWriter stringWriter = new StringWriter();
      thrown.printStackTrace(new PrintWriter(stringWriter));
      String[] lines = StringUtil.splitByLines(stringWriter.toString());
      int maxStackSize = 1024, maxExtraSize = 256;
      if (lines.length > maxStackSize + maxExtraSize) {
        String[] res = new String[maxStackSize + maxExtraSize + 1];
        System.arraycopy(lines, 0, res, 0, maxStackSize);
        res[maxStackSize] = "\t...";
        System.arraycopy(lines, lines.length - maxExtraSize, res, maxStackSize + 1, maxExtraSize);
        for (int i = 0; i < res.length; i++) {
          if (i > 0) {
            sb.append(LINE_SEPARATOR);
          }
          sb.append(res[i]);
        }
      }
      else {
        sb.append(stringWriter.getBuffer());
      }
    }
  }
}
