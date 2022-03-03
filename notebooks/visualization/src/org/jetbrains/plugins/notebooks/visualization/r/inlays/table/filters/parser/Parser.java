/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.parser;

import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IParser;

import javax.swing.*;
import java.text.Format;
import java.text.ParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Basic implementation of a {@link IParser}, supporting only simple operators
 * referring to the content of a single column.<br>
 * The supporter operators include:
 *
 * <ul>
 *   <li>Comparison operators. The comparison is done on the parsed object, not
 *     on the string representation, unless no {@link Format} or {@link
 *     Comparator} is defined for the given type. For example, specifying the
 *     text &quot;&gt;= 4&quot; implies, for a column with integer types, that a
 *     direct comparison between integers will be performed. These operators
 *     are:
 *
 *     <ul>
 *       <li>&gt;=</li>
 *       <li>&gt;</li>
 *       <li>&lt;</li>
 *       <li>&lt;=</li>
 *       <li>&lt;&gt;, !: all behave the same</li>
 *     </ul>
 *   </li>
 *   <li>Basic wildcard operators. These operators work using the string
 *     representation of the types (using, when possible, the defined {@link
 *     Format} instance). Only two wildcard characters are defined: * and ?
 *
 *     <ul>
 *       <li>~: for example ~ *vadis* will filter in all expressions including
 *         the substring vadis</li>
 *       <li>!~: negates the previous operator</li>
 *     </ul>
 *   </li>
 *   <li>Regular expression operator. There is only one such operator: ~~,
 *     accepting a java regular expression.</li>
 * </ul>
 *
 * <p>In addition, providing no operator will behave as the operator ~</p>
 */
public class Parser implements IParser {

  FormatWrapper format;
  Comparator comparator;
  boolean ignoreCase;
  Comparator<String> stringComparator;
  int modelIndex;
  static HtmlHandler htmlHandler = new HtmlHandler();
  private static final Map<String, IOperand> operands;
  private static final IOperand wildcardOperand;
  private static final WildcardOperand instantOperand;
  private static final Pattern expressionMatcher;
  private static StringBuilder escapeBuffer = new StringBuilder();

  public Parser(Format format,
                Comparator classComparator,
                Comparator<String> stringComparator,
                boolean ignoreCase,
                int modelIndex) {
    this.format = new FormatWrapper(format);
    this.comparator = classComparator;
    this.stringComparator = stringComparator;
    this.ignoreCase = ignoreCase;
    this.modelIndex = modelIndex;
  }

  /**
   * {@link IParser} interface.
   */
  @Override
  public RowFilter parseText(String expression)
    throws ParseException {
    Matcher matcher = expressionMatcher.matcher(expression);
    if (matcher.matches()) {
      // all expressions match!
      IOperand op = operands.get(matcher.group(1));
      if (op == null) {
        // note that instant does not apply if there is an operator!
        op = getDefaultOperator();
      }

      return op.create(this, matcher.group(3).trim());
    }

    throw new ParseException("", 0);
  }

  /**
   * {@link IParser} interface.
   */
  @Override
  public InstantFilter parseInstantText(String expression)
    throws ParseException {
    expression = expression.trim();
    Matcher matcher = expressionMatcher.matcher(expression);
    if (matcher.matches()) {
      // all expressions match!
      IOperand op = operands.get(matcher.group(1));
      if (op == null) {
        // note that instant does not apply if there is an operator!
        op = getDefaultOperator();
      }

      InstantFilter ret = new InstantFilter();
      ret.filter = op.create(this, matcher.group(3));
      ret.expression = (op == instantOperand)
                       ? getInstantAppliedExpression(expression) : expression;

      return ret;
    }

    throw new ParseException("", 0);
  }

  protected String getInstantAppliedExpression(String expression) {
    return instantOperand.getAppliedExpression(expression);
  }

  /**
   * Returns the default operator if none is specified by the user
   *
   * @return the default operator if none is specified by the user
   */
  public IOperand getDefaultOperator() {
    return instantOperand;
  }

  /**
   * {@link IParser} interface.
   */
  @Override
  public String stripHtml(String expression) {
    return htmlHandler.stripHtml(expression);
  }

  /**
   * {@link IParser} interface.
   */
  @Override
  public String escape(String expression) {
    expression = expression.trim();
    Matcher matcher = expressionMatcher.matcher(expression);
    if (matcher.matches()) {
      String operator = matcher.group(1);
      int lastAdded = 0;
      if (operator != null) {
        escapeBuffer.append('\\').append(operator);
        expression = matcher.group(2);
      }

      int total = expression.length();
      for (int i = 0; i < total; i++) {
        char ch = expression.charAt(i);
        if ((ch == '*') || (ch == '?')) {
          escapeBuffer.append(expression, lastAdded, i);
          escapeBuffer.append('\\').append(ch);
          lastAdded = i + 1;
        }
      }

      if (escapeBuffer.length() > 0) {
        escapeBuffer.append(expression, lastAdded, total);
        expression = escapeBuffer.toString();
        escapeBuffer.delete(0, escapeBuffer.length());
      }
    }

    return expression;
  }

  /**
   * Internal interface, to be implemented by all operands.
   */
  protected interface IOperand {
    RowFilter create(Parser self, String right) throws ParseException;
  }

  /**
   * IOperand for comparison operations.
   */
  abstract protected static class ComparisonOperand implements IOperand {
    abstract boolean matches(int comparison);

    private String getOperator() {
      boolean gt = matches(1);
      boolean eq = matches(0);
      boolean ls = matches(-1);
      if (gt) {
        if (eq) return ">=";
        if (ls) return "!=";
        return ">";
      }
      if (ls) {
        if (eq) return "<=";
        return "<";
      }
      return "==";
    }

    /**
     * {@link IOperand} interface.
     */
    @Override
    public RowFilter create(Parser self, String right)
      throws ParseException {

      if (right != null) {
        if (self.comparator == null) {
          return createStringOperator(right, self.modelIndex,
                                      self.format, self.stringComparator);
        }

        Object o = self.format.parseObject(right);
        if (o != null) {
          return createOperator(o, self.modelIndex, self.comparator);
        }
      }

      throw new ParseException("", 0);
    }

    /**
     * Operator fine for given type, apply it.
     */
    private RowFilter createOperator(final Object right,
                                     final int modelIndex,
                                     final Comparator comparator) {
      return new RowFilter() {
        @Override
        public boolean include(Entry entry) {
          Object left = entry.getValue(modelIndex);
          if (left instanceof String) {
            left = htmlHandler.stripHtml((String)left);
          }
          return (left != null)
                 && matches(comparator.compare(left, right));
        }
      };
    }

    /**
     * Operator invalid for given type, filter by string representation.
     */
    private RowFilter createStringOperator(
      final String right,
      final int modelIndex,
      final FormatWrapper format,
      final Comparator stringComparator) {
      return new RowFilter() {
        @Override
        public boolean include(Entry entry) {
          Object left = entry.getValue(modelIndex);
          if (left == null) {
            return false;
          }

          String s = format.format(left);

          return (s.length() > 0)
                 && matches(stringComparator.compare(s, right));
        }
      };
    }
  }

  /**
   * IOperand for equal/unequal operations.
   */
  static protected class EqualOperand implements IOperand {

    boolean expected;

    /**
     * Single constructor.
     *
     * @param expected true if the operand expects the equal operation to
     *                 succeed
     */
    public EqualOperand(boolean expected) {
      this.expected = expected;
    }

    /**
     * {@link IOperand} interface.
     */
    @Override
    public RowFilter create(Parser self, String right)
      throws ParseException {
      if (self.comparator == null) {
        return createStringOperator(right, self.modelIndex, self.format,
                                    self.stringComparator);
      }

      if (right.length() == 0) {
        return createNullOperator(self.modelIndex);
      }

      Object o = self.format.parseObject(right);
      if (o == null) {
        throw new ParseException("", 0);
      }

      return createOperator(o, self.modelIndex, self.comparator);
    }

    /**
     * Operator fine for given type, apply it.
     */
    private RowFilter createOperator(final Object right,
                                     final int modelIndex,
                                     final Comparator comparator) {
      return new RowFilter() {
        @Override
        public boolean include(Entry entry) {
          Object left = entry.getValue(modelIndex);
          if (left instanceof String) {
            left = htmlHandler.stripHtml((String)left);
          }
          boolean value = (left != null)
                          && (0 == comparator.compare(left, right));
          return value == expected;
        }
      };
    }

    /**
     * No right operand give, comparing against 'null'.
     */
    private RowFilter createNullOperator(final int modelIndex) {
      return new RowFilter() {
        @Override
        public boolean include(Entry entry) {
          Object left = entry.getValue(modelIndex);

          return expected == (left == null);
        }
      };
    }

    /**
     * Operator invalid for given type, filter by string representation.
     */
    private RowFilter createStringOperator(
      final String right,
      final int modelIndex,
      final FormatWrapper format,
      final Comparator stringComparator) {
      return new RowFilter() {
        @Override
        public boolean include(Entry entry) {
          Object left = entry.getValue(modelIndex);
          String value = format.format(left);

          return expected == (stringComparator.compare(value, right)
                              == 0);
        }
      };
    }
  }

  /**
   * Operand for regular expressions.
   */
  static protected class REOperand implements IOperand {
    boolean equals;

    /**
     * Single constructor.
     *
     * @param equals true if the operand expects the regular expression
     *               matching to succeed
     */
    public REOperand(boolean equals) {
      this.equals = equals;
    }

    /**
     * {@link IOperand} interface.
     */
    @Override
    public RowFilter create(Parser self, String right)
      throws ParseException {
      final Pattern pattern = getPattern(right, self.ignoreCase);
      final int modelIndex = self.modelIndex;
      final FormatWrapper format = self.format;

      return new RowFilter() {

        @Override
        public boolean include(Entry entry) {
          Object o = entry.getValue(modelIndex);
          String left = format.format(o);

          return equals == pattern.matcher(left).matches();
        }
      };
    }

    /**
     * Returns the {@link Pattern} instance associated to the provided
     * expression.
     */
    protected Pattern getPattern(String expression, boolean ignoreCase)
      throws ParseException {
      try {
        return Pattern.compile(expression,
                               Pattern.DOTALL | (ignoreCase
                                                 ? (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                                                 : 0));
      }
      catch (PatternSyntaxException pse) {
        throw new ParseException("", pse.getIndex());
      }
    }
  }

  /**
   * Operand for wildcard expressions.
   */
  static protected class WildcardOperand extends REOperand {

    private boolean instantMode;
    private int instantApplied;

    /**
     * Constructor for equal/unequal simple regular expression.
     */
    public WildcardOperand(boolean equals) {
      super(equals);
    }

    /**
     * Sets the instant mode (as is '*' was appended)
     */
    public void setInstantMode(boolean instantMode) {
      this.instantMode = instantMode;
    }

    /**
     * After the operand is used, this method returns the expression that
     * has been really applied to obtain the filter.
     */
    public String getAppliedExpression(String baseExpression) {
      switch (instantApplied) {
        case 0:
          return baseExpression;
        case 1:
          return "*" + baseExpression;
        case 2:
          return baseExpression + "*";
        case 3:
          return "*" + baseExpression + "*";
      }
      return baseExpression;
    }

    /**
     * {@link REOperand} interface.
     */
    @Override
    protected Pattern getPattern(String right,
                                 boolean ignoreCase)
      throws ParseException {
      return super.getPattern(convertToRE(right), ignoreCase);
    }

    /**
     * Converts a wildcard expression into a regular expression.
     */
    protected String convertToRE(String s) {
      StringBuilder sb = new StringBuilder();
      boolean escaped = false;
      instantApplied = 0;

      for (char c : s.toCharArray()) {

        if (c == '*') {
          if (escaped) {
            sb.append("\\*");
            escaped = false;
          }
          else {
            sb.append(".*");
          }
        }
        else if (c == '?') {
          if (escaped) {
            sb.append("\\?");
            escaped = false;
          }
          else {
            sb.append(".");
          }
        }
        else if (c == '\\') {
          if (escaped) {
            sb.append("\\\\\\\\");
          }
          escaped = !escaped;
        }
        else {
          if (escaped) {
            sb.append("\\\\");
            escaped = false;
          }
          switch (c) {
            case '[':
            case ']':
            case '^':
            case '$':
            case '+':
            case '{':
            case '}':
            case '|':
            case '(':
            case ')':
            case '.':
              sb.append('\\').append(c);
              break;

            default:
              sb.append(c);
              break;
          }
        }
      }

      if (escaped) {
        sb.append("\\\\");
      }

      if (instantMode) {
        int l = sb.length();
        boolean okStart, okEnd;
        if (l < 2) {
          okStart = okEnd = false;
        }
        else {
          okStart = sb.substring(0, 2).equals(".*");
          okEnd = sb.substring(l - 2).equals(".*");
        }
        if (!okStart) {
          instantApplied = 1;
          sb.insert(0, ".*");
        }
        if (!okEnd) {
          instantApplied += 2;
          sb.append(".*");
        }
      }

      return sb.toString();
    }
  }

  static {
    expressionMatcher = Pattern.compile(
      "^\\s*(>=|<=|<>|!~|~~|>|<|=|~|!)?(\\s*(.*))$", Pattern.DOTALL);

    operands = new HashMap<>();
    operands.put("~~", new REOperand(true));
    operands.put("!~", new WildcardOperand(false));
    operands.put("!", new EqualOperand(false));
    operands.put(">=", new ComparisonOperand() {
      @Override
      boolean matches(int comparison) {
        return comparison >= 0;
      }
    });
    operands.put(">", new ComparisonOperand() {
      @Override
      boolean matches(int comparison) {
        return comparison > 0;
      }
    });
    operands.put("<=", new ComparisonOperand() {
      @Override
      boolean matches(int comparison) {
        return comparison <= 0;
      }
    });
    operands.put("<", new ComparisonOperand() {
      @Override
      boolean matches(int comparison) {
        return comparison < 0;
      }
    });
    operands.put("<>", new ComparisonOperand() {
      @Override
      boolean matches(int comparison) {
        return comparison != 0;
      }
    });
    operands.put("~", wildcardOperand = new WildcardOperand(true));
    operands.put("=", new EqualOperand(true));
    instantOperand = new WildcardOperand(true);
    instantOperand.setInstantMode(true);
  }

  /**
   * Helper class to deal with null formats. It also trims the output.
   */
  static class FormatWrapper {
    Format format;

    FormatWrapper(Format format) {
      this.format = format;
    }

    public String format(Object o) {
      if (format == null) {
        return (o == null) ? "" : htmlHandler.stripHtml(o.toString());
      }
      return format.format(o).trim();
    }

    public Object parseObject(String content) throws ParseException {
      return (format == null) ? null : format.parseObject(content);
    }
  }
}
