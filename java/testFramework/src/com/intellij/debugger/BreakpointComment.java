// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.classFilter.ClassFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

  private @Nullable String kind;
  private @Nullable String kindValue;
  private final Map<@NotNull String, @NotNull String> values = new LinkedHashMap<>();
  private final Map<@NotNull String, Integer> index = new LinkedHashMap<>();
  private final Set<@NotNull String> validValues = new TreeSet<>();
  private final @NotNull String fileName;
  private final int lineNumber;

  /**
   * @param text       the line containing the comment
   * @param fileName   the file name, for diagnostics
   * @param lineNumber the 0-based line containing the comment, for diagnostics
   */
  public static @NotNull BreakpointComment parse(@NotNull String text, @NotNull String fileName, int lineNumber) {
    BreakpointComment comment = new BreakpointComment(fileName, lineNumber);
    return new Parser(text, comment).parse();
  }

  private BreakpointComment(@NotNull String fileName, int lineNumber) {
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
  public @NotNull String readKind() {
    String kind = this.kind;
    assert kind != null;
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
  public @Nullable String readValue(@NotNull String name) {
    validValues.add(name);
    return values.remove(name);
  }

  /**
   * Reads a named boolean property of the breakpoint.
   */
  public @Nullable Boolean readBooleanValue(@NotNull String name) {
    String value = readValue(name);
    if (value == null) return null;
    if (value.equals("true")) return true;
    if (value.equals("false")) return false;
    throw error("Invalid boolean value '" + value + "' for '" + name + "'", index.get(name));
  }

  /**
   * Reads a named integer property of the breakpoint.
   */
  public @Nullable Integer readIntValue(@NotNull String name) {
    String value = readValue(name);
    if (value == null) return null;
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      throw error("Invalid integer value '" + value + "' for '" + name + "'", index.get(name));
    }
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
    if (!values.isEmpty()) {
      String valid = String.join(", ", validValues);
      String msg = "Unprocessed %s '%s', %s".formatted(
        values.size() > 1 ? "values" : "value",
        String.join(", ", values.keySet()),
        validValues.size() > 1 ? "valid values are '" + valid + "'" :
        validValues.size() == 1 ? "the only valid value is '" + valid + "'" :
        "the breakpoint takes no values");
      throw error(msg, index.get(values.keySet().iterator().next()));
    }
  }

  private @NotNull RuntimeException error(String msg) {
    return new IllegalArgumentException(msg + " at " + fileName + ":" + (lineNumber + 1));
  }

  private @NotNull RuntimeException error(String msg, int index) {
    return new IllegalArgumentException(msg + " at " + fileName + ":" + (lineNumber + 1) + ":" + (index + 1));
  }

  private static class Parser {
    private final int[] s;
    private final int len;

    private int i;
    private final BreakpointComment comment;

    private Parser(@NotNull String lineText, BreakpointComment comment) {
      s = lineText.codePoints().toArray();
      len = s.length;
      this.comment = comment;
    }

    @NotNull BreakpointComment parse() {
      while (i + 1 < len && !(s[i] == '/' && s[i + 1] == '/')) i++;
      skip('/');
      skip('/');
      skipWhitespace();

      final String head = parseName();
      if (i < len && s[i] == '(') {
        // We have kind with value.
        comment.kind = head;
        comment.kindValue = parseValue();
        skipWhitespace();
        int nameStart = i;
        String tail = parseName();
        if (!tail.equals("Breakpoint")) {
          throw errorAt(nameStart, "Expected 'Breakpoint' instead of '" + tail + "'");
        }
      }
      else {
        var kind = head.replaceFirst("\\s*Breakpoint$", "");
        comment.kind = kind.isEmpty() ? "Line" : kind;
      }
      skip('!');

      while (i < len) {
        skipWhitespace();
        if (i >= len) break; // Allow trailing space.
        int nameStart = i;
        String key = parseName();
        if (key.isEmpty()) throw errorAt(i, "Expected breakpoint property key");
        if (comment.values.containsKey(key)) throw errorAt(nameStart, "Duplicate property key '" + key + "'");
        String value = parseValue();
        comment.values.put(key, value);
        comment.index.put(key, nameStart);
      }

      skipWhitespace();
      if (i < len) throw errorAt(i, "Extra '" + new String(s, i, len - i) + "'");

      return comment;
    }

    private void skipWhitespace() {
      while (i < len && Character.isWhitespace(s[i])) i++;
    }

    private void skip(int cp) {
      if (!(i < len && s[i] == cp)) throw errorAt(i, "Expected '" + Character.toString(cp) + "'");
      i++;
    }

    private @NotNull String parseName() {
      int start = i;
      while (i < len && (Character.isJavaIdentifierPart(s[i]) || Character.isWhitespace(s[i]))) i++;
      if (i == start) throw errorAt(start, "Expected a name");
      return new String(s, start, i - start);
    }

    private @NotNull String parseValue() {
      skip('(');
      int start = i;
      int depth = 1;
      for (; i < len; i++) {
        if (s[i] == '(') depth++;
        if (s[i] == ')' && --depth == 0) return new String(s, start, i++ - start);
      }
      throw errorAt(start, "Unfinished value '" + new String(s, start, len - start) + "'");
    }

    private @NotNull RuntimeException errorAt(int index, String msg) {
      return new IllegalArgumentException(msg + " at " + comment.fileName + ":" + (comment.lineNumber + 1) + ":" + (index + 1));
    }
  }
}
