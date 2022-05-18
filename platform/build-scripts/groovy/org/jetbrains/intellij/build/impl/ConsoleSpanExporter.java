// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.text.Formats;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public final class ConsoleSpanExporter implements SpanExporter {
  public static void setPathRoot(@NotNull Path dir) {
    String s1 = dir.toString() + File.separatorChar;
    String s2 = dir.toRealPath().toString() + File.separatorChar;
    if (s1.equals(s2)) {
      rootPathsWithEndSlash = List.of(s1);
    }
    else {
      rootPathsWithEndSlash = List.of(s1, s2);
    }
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    StringBuilder sb = new StringBuilder();
    for (SpanData span : spans) {
      Attributes attributes = span.getAttributes();
      boolean reportSpanToConsole = !attributes.get(AttributeKey.booleanKey("_CES_")).equals(Boolean.TRUE);
      if (reportSpanToConsole) {
        writeSpan(sb, span, span.getEndEpochNanos() - span.getStartEpochNanos(), span.getEndEpochNanos());
      }
    }


    if (sb.length() > 0) {
      // System.out.print is synchronized - buffer content to reduce calls
      System.out.print(sb.toString());
    }


    return CompletableResultCode.ofSuccess();
  }

  private static void writeSpan(StringBuilder sb, SpanData span, long duration, long endEpochNanos) {
    sb.append(span.getName());
    sb.append(" (duration=");
    sb.append(Formats.formatDuration(TimeUnit.NANOSECONDS.toMillis(duration)));
    sb.append(", end=");
    writeTime(endEpochNanos, sb);

    if (span.getStatus().getStatusCode().equals(StatusCode.ERROR) && !span.getStatus().getDescription().isEmpty()) {
      sb.append(", error=");
      sb.append(span.getStatus().getDescription());
    }


    writeAttributesAsHumanReadable(span.getAttributes(), sb);

    sb.append(")");
    sb.append(StringGroovyMethods.asType("\n", (Class<Object>)Character.class));

    List<EventData> events = span.getEvents();
    if (!events.isEmpty()) {
      boolean isHeaderWritten = false;
      int offset = 0;
      String prefix = " ";
      for (EventData event : events) {
        if (EXCLUDED_EVENTS_FROM_CONSOLE.contains(event.getName())) {
          offset = offset--;
          continue;
        }


        if (!isHeaderWritten) {
          sb.append("  events:");
          if ((events.size() - offset) > 1) {
            sb.append(StringGroovyMethods.asType("\n", (Class<Object>)Character.class));
            prefix = "    ";
          }

          isHeaderWritten = true;
        }


        sb.append(prefix);
        sb.append(event.getName());
        sb.append(" (time=");
        writeTime(event.getEpochNanos(), sb);
        writeAttributesAsHumanReadable(event.getAttributes(), sb);
        sb.append(")");
        sb.append(StringGroovyMethods.asType("\n", (Class<Object>)Character.class));
      }
    }
  }

  private static void writeTime(long epochNanos, StringBuilder sb) {
    long epochSeconds = TimeUnit.NANOSECONDS.toSeconds(epochNanos);
    long adjustNanos = epochNanos - TimeUnit.SECONDS.toNanos(epochSeconds);
    ISO_LOCAL_TIME.formatTo(LocalTime.ofInstant(Instant.ofEpochSecond(epochSeconds, adjustNanos), ZoneId.systemDefault()), sb);
  }

  private static void writeValueAsHumanReadable(String s, StringBuilder sb) {
    for (String prefix : rootPathsWithEndSlash) {
      String newS = s.replace(prefix, buildRootMacro);
      if (!DefaultGroovyMethods.is(newS, s)) {
        sb.append(newS);
        return;
      }
    }

    sb.append(s);
  }

  private static void writeAttributesAsHumanReadable(Attributes attributes, final StringBuilder sb) {
    attributes.forEach(new BiConsumer<AttributeKey<?>, Object>() {
      @Override
      public void accept(AttributeKey<?> k, Object v) {
        if (k.equals(SemanticAttributes.THREAD_NAME) || k.equals(SemanticAttributes.THREAD_ID)) {
          return;
        }


        sb.append(", ");
        sb.append(k.getKey());
        sb.append(StringGroovyMethods.asType("=", (Class<Object>)Character.class));

        if (k.equals(SemanticAttributes.EXCEPTION_STACKTRACE)) {
          String delimiter = "─".repeat(79);
          sb.append("\n  ┌");
          sb.append(delimiter);
          sb.append("┐\n   ");
          sb.append(v.toString().replaceAll("(\r\n|\n)", "\n   ").trim());
          sb.append("\n  └");
          sb.append(delimiter);
          sb.append("┘");
        }
        else {
          if (k.getKey().equals("modulesWithSearchableOptions") || ((v instanceof List && ((List)v).size() > 16))) {
            sb.append("…");
          }
          else if (v instanceof Iterable) {
            for (String s : (Iterable<String>)v) {
              writeValueAsHumanReadable(s, sb);
              sb.append(", ");
            }

            sb.setLength(sb.length() - 2);
          }
          else {
            writeValueAsHumanReadable(v.toString(), sb);
          }
        }
      }
    });
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public void close() {
  }

  private static final Set<String> EXCLUDED_EVENTS_FROM_CONSOLE = Set.of("include module outputs");
  private static final DateTimeFormatter ISO_LOCAL_TIME =
    new DateTimeFormatterBuilder().appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(":").appendValue(ChronoField.MINUTE_OF_HOUR, 2)
      .optionalStart().appendLiteral(":").appendValue(ChronoField.SECOND_OF_MINUTE, 2).optionalStart()
      .appendFraction(ChronoField.NANO_OF_SECOND, 0, 4, true).toFormatter();
  private static List<String> rootPathsWithEndSlash = Collections.emptyList();
  private static final String buildRootMacro = '\${buildRoot}' + File.separatorChar;
}
