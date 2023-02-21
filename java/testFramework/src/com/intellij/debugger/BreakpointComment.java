// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.classFilter.ClassFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The properties of a breakpoint from a test source file, specified by a 'Breakpoint!' comment.
 * <p>
 * The general syntax of a breakpoint comment is:
 * <blockquote>
 * [<i>kind</i>[(<i>kindValue</i>)]] Breakpoint! [<i>name</i>(<i>value</i>)...]
 * </blockquote>
 * <p>
 * The <i>kind</i> and <i>name</i> may consist of several words.
 * <p>
 * Examples:
 * <ul>
 * <li>{@code Breakpoint!}
 *     &mdash; a line breakpoint
 * <li>{@code Method Breakpoint!}
 *     &mdash; a method breakpoint
 * <li>{@code Field(myName) Breakpoint! LogExpression(myName.substring(1)) Pass count(3)}
 *     &mdash; a breakpoint on the field <i>myName</i>
 *     that when reached logs the given expression
 *     and only hits every 3rd time it is reached
 * </ul>
 * <p>
 * This parser only parses the general syntax.
 * Which breakpoint kinds are allowed and what their properties are
 * is up to the code that uses this parser.
 * <p>
 * After parsing the breakpoint properties
 * using {@link #readKind()}, {@link #readKindValue()} and {@link #readValue(String)},
 * calling {@link #done()} checks that all properties of the breakpoint
 * have been read.
 */
public final class BreakpointComment {

  private String kind;
  private String kindValue;
  private final Map<String, String> values = new LinkedHashMap<>();
  private final String fileName;
  private final int lineNumber;

  public static @NotNull BreakpointComment parse(@NotNull String text, @NotNull String fileName, int lineNumber) {
    return new Parser(text, fileName, lineNumber).parse();
  }

  private BreakpointComment(String fileName, int lineNumber) {
    this.fileName = fileName;
    this.lineNumber = lineNumber;
  }

  /**
   * Reads the optional kind of the breakpoint.
   * <p>
   * Typical values are "Method" or "Exception".
   *
   * @see #readKindValue()
   */
  public @Nullable String readKind() {
    String kind = this.kind;
    this.kind = null;
    return kind;
  }

  /**
   * Reads the optional further parameter of the breakpoint kind,
   * for example {@code myName} in {@code Field(myName) Breakpoint!}.
   */
  public @Nullable String readKindValue() {
    String value = kindValue;
    kindValue = null;
    return value;
  }

  /**
   * Reads a named property of the breakpoint, which has the form <i>name(value)</i>.
   * Any parentheses in the value must be balanced.
   */
  public @Nullable String readValue(@NotNull String name) { return values.remove(name); }

  /**
   * Reads a named boolean property of the breakpoint.
   */
  public @Nullable Boolean readBooleanValue(@NotNull String name) {
    String value = readValue(name);
    if (value == null) return null;
    if (value.equals("true")) return true;
    if (value.equals("false")) return false;
    throw error("Invalid boolean value '" + value + "' for '" + name + "'");
  }

  /**
   * Parses a string like {@code "Included,-ExceptionTest,-com.intellij.rt.*"} into
   * two lists of included and excluded class filters.
   */
  public static @NotNull Pair<ClassFilter[], ClassFilter[]> parseClassFilters(@NotNull String value) {
    ArrayList<ClassFilter> include = new ArrayList<>();
    ArrayList<ClassFilter> exclude = new ArrayList<>();
    for (String pattern : value.split(",")) {
      if (pattern.startsWith("-")) {
        exclude.add(new ClassFilter(pattern.substring(1)));
      }
      else {
        include.add(new ClassFilter(pattern));
      }
    }
    return Pair.create(include.toArray(ClassFilter.EMPTY_ARRAY), exclude.toArray(ClassFilter.EMPTY_ARRAY));
  }

  /** Checks that all properties have been read. */
  public void done() {
    if (kind != null) throw error("Unprocessed kind '" + kind + "'");
    if (kindValue != null) throw error("Unprocessed kind value '" + kindValue + "'");
    if (values.size() > 1) throw error("Unprocessed values '" + String.join(", ", values.keySet()) + "'");
    if (values.size() == 1) throw error("Unprocessed value '" + values.keySet().iterator().next() + "'");
  }

  private RuntimeException error(String msg) {
    return new IllegalArgumentException(msg + " at " + fileName + ":" + lineNumber);
  }

  private static class Parser {
    private final int[] s;
    private final int len;

    private int i;
    private final BreakpointComment comment;

    private Parser(String lineText, String fileName, int lineNumber) {
      String text = StringUtil.substringAfter(lineText, "//");
      if (text == null) throw new IllegalArgumentException("Breakpoint comment must start with '//' at " + fileName + ":" + lineNumber);
      s = text.codePoints().toArray();
      len = s.length;
      comment = new BreakpointComment(fileName, lineNumber);
    }

    @NotNull BreakpointComment parse() {
      skipWhitespace();

      String kind = parseName();
      if (i < len && s[i] == '(') {
        comment.kind = kind;
        comment.kindValue = parseValue();
        skipWhitespace();
        kind = parseName();
        if (!kind.equals("Breakpoint")) {
          throw errorAt(i, "Expected 'Breakpoint' instead of '" + kind + "'");
        }
      }
      else {
        comment.kind = switch (kind) {
          case "Method Breakpoint" -> "Method";
          case "Breakpoint" -> "Line";
          default -> throw errorAt(i, "Invalid");
        };
      }
      skip('!');

      while (i < len) {
        skipWhitespace();
        if (i >= len) break; // Allow trailing space.
        String key = parseName();
        if (key.isEmpty()) throw errorAt(i, "Expected breakpoint property key");
        String value = parseValue();
        comment.values.put(key, value);
      }

      skipWhitespace();
      if (i < len) throw errorAt(i, "Extra '" + new String(s, i, len - i) + "'");

      return comment;
    }

    private void skipWhitespace() {
      while (i < len && Character.isWhitespace(s[i])) i++;
    }

    private void skip(int cp) {
      if (!(i < len && s[i] == cp)) throw errorAt(i, "Expected '" + new String(new int[]{cp}, 0, 1) + "'");
      i++;
    }

    private String parseName() {
      int start = i;
      while (i < len && (Character.isJavaIdentifierPart(s[i]) || Character.isWhitespace(s[i]))) i++;
      if (i == start) throw errorAt(start, "Expected a name");
      return new String(s, start, i - start);
    }

    private String parseValue() {
      skip('(');
      int start = i;
      int depth = 1;
      for (; i < len; i++) {
        if (s[i] == '(') {
          depth++;
        }
        else if (s[i] == ')' && --depth == 0) return new String(s, start, i++ - start);
      }
      throw errorAt(start, "Unfinished value '" + new String(s, start, len - start) + "'");
    }

    private RuntimeException errorAt(int index, String msg) {
      return new IllegalArgumentException(
        msg + " at index " + index + " of '" + new String(s, 0, len) + "' at " + comment.fileName + ":" + comment.lineNumber
      );
    }
  }
}
