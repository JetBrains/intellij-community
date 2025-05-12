/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.format;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.FormatUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Utilities related to printf-like format string
 */
public final class FormatDecode {

  private static final Pattern fsPattern = Pattern.compile(
    "%(?<posSpec>\\d+\\$)?(?<flags>[-#+ 0,(<]*)(?<width>\\d+)?(?<precision>\\.\\d+)?(?<dateSpec>[tT])?(?<conversion>[a-zA-Z%])");

  private FormatDecode() { }

  private static final Validator ALL_VALIDATOR = new AllValidator();

  private static final int LEFT_JUSTIFY = 1; // '-'
  private static final int ALTERNATE = 2; // '#'
  private static final int PLUS = 4; // '+'
  private static final int LEADING_SPACE = 8; // ' '
  private static final int ZERO_PAD = 16; // '0'
  private static final int GROUP = 32; // ','
  private static final int PARENTHESES = 64; // '('
  private static final int PREVIOUS = 128; // '<'

  private static int flag(char c) {
    return switch (c) {
      case '-' -> LEFT_JUSTIFY;
      case '#' -> ALTERNATE;
      case '+' -> PLUS;
      case ' ' -> LEADING_SPACE;
      case '0' -> ZERO_PAD;
      case ',' -> GROUP;
      case '(' -> PARENTHESES;
      case '<' -> PREVIOUS;
      default -> -1;
    };
  }

  private static String flagString(int flags) {
    final StringBuilder result = new StringBuilder(8);
    if ((flags & LEFT_JUSTIFY) != 0) {
      result.append('-');
    }
    if ((flags & ALTERNATE) != 0) {
      result.append('#');
    }
    if ((flags & PLUS) != 0) {
      result.append('+');
    }
    if ((flags & LEADING_SPACE) != 0) {
      result.append(' ');
    }
    if ((flags & ZERO_PAD) != 0) {
      result.append('0');
    }
    if ((flags & GROUP) != 0) {
      result.append(',');
    }
    if ((flags & PARENTHESES) != 0) {
      result.append('(');
    }
    if ((flags & PREVIOUS) != 0) {
      result.append('<');
    }
    return result.toString();
  }

  private static void checkFlags(int value, int allowedFlags, String specifier) {
    final int result = value & ~allowedFlags;
    if (result != 0) {
      final String flags = flagString(result);
      throw new IllegalFormatException(
        InspectionGadgetsBundle.message("format.string.error.flags.not.allowed", flags, specifier, flags.length()));
    }
  }

  public static Validator[] decodePrefix(String prefix, int argumentCount) {
    return decode(prefix, argumentCount, true, false);
  }

  public static Validator @NotNull [] decode(String formatString, int argumentCount) {
    return decode(formatString, argumentCount, false, false);
  }

  public static Validator @NotNull [] decodeNoVerify(String formatString, int argumentCount) {
    return decode(formatString, argumentCount, false, true);
  }

  private static Validator @NotNull [] decode(String formatString, int argumentCount, boolean isPrefix, boolean noVerify) {
    final ArrayList<Validator> parameters = new ArrayList<>();

    final Matcher matcher = fsPattern.matcher(formatString);
    boolean previousAllowed = false;
    int implicit = 0;
    int pos = 0;
    int i = 0;
    while (matcher.find(i)) {
      final int start = matcher.start();
      if (start != i) {
        checkText(formatString.substring(i, start));
      }
      i = matcher.end();
      boolean isAllVerifier = false;
      //theoretically, it could be a correct specifier, divided into several parts, for example: "%1$t" + "Y"
      //it is better to add ALL_VERIFIER
      if (noVerify || isPrefix && i == formatString.length()) {
        isAllVerifier = true;
      }
      final String specifier = matcher.group();
      final String posSpec = matcher.group("posSpec");
      final String flags = matcher.group("flags");
      final String width = matcher.group("width");
      final String precision = matcher.group("precision");
      final String dateSpec = matcher.group("dateSpec");
      final @NonNls String conversion = matcher.group("conversion");

      int flagBits = 0;
      for (int j = 0; j < flags.length(); j++) {
        final char flag = flags.charAt(j);
        final int bit = flag(flag);
        if (bit == -1) {
          throw new IllegalFormatException(InspectionGadgetsBundle.message("format.string.error.unexpected.flag", flag, specifier));
        }
        if ((flagBits | bit) == flagBits) {
          throw new IllegalFormatException(InspectionGadgetsBundle.message("format.string.error.duplicate.flag", flag, specifier));
        }
        flagBits |= bit;
      }

      // check this first because it should not affect "implicit"
      if ("n".equals(conversion)) {
        // no flags allowed
        checkFlags(flagBits, 0, specifier);
        if (!StringUtil.isEmpty(width)) {
          throw new IllegalFormatException(InspectionGadgetsBundle.message("format.string.error.width.not.allowed", width, specifier));
        }
        checkNoPrecision(precision, specifier);
        continue;
      }
      else if ("%".equals(conversion)) { // literal '%'
        checkFlags(flagBits, LEFT_JUSTIFY, specifier);
        checkNoPrecision(precision, specifier);
        continue;
      }

      if (posSpec != null) {
        if (isAllBitsSet(flagBits, PREVIOUS)) {
          throw new IllegalFormatException(
            InspectionGadgetsBundle.message("format.string.error.unnecessary.position.specifier", posSpec, specifier));
        }
        final String num = posSpec.substring(0, posSpec.length() - 1);
        pos = Integer.parseInt(num) - 1;
        if (pos < 0) {
          throw new IllegalFormatException(
            InspectionGadgetsBundle.message("format.string.error.illegal.position.specifier", posSpec, specifier));
        }
        previousAllowed = true;
      }
      else if (isAllBitsSet(flagBits, PREVIOUS)) {
        // reuse last pos
        if (!previousAllowed) {
          throw new IllegalFormatException(InspectionGadgetsBundle.message("format.string.error.previous.element.not.found", specifier));
        }
      }
      else {
        previousAllowed = true;
        pos = implicit++;
      }

      final Validator allowed;
      if (isAllVerifier) {
        allowed = new AllValidatorWithRange(TextRange.create(matcher.start(), matcher.end()),
                                            new Spec(posSpec, flags, width, precision, dateSpec, conversion));
      }
      else if (dateSpec != null) {  // a t or T
        checkFlags(flagBits, LEFT_JUSTIFY | PREVIOUS, specifier);
        DateTimeConversionType dateTimeConversionType = getDateTimeConversionType(conversion.charAt(0));
        if (dateTimeConversionType == DateTimeConversionType.UNKNOWN) {
          throw new IllegalFormatException(
            InspectionGadgetsBundle.message("format.string.error.unknown.conversion", dateSpec + conversion));
        }
        checkNoPrecision(precision, specifier);
        allowed = new DateValidator(specifier, dateTimeConversionType);
      }
      else {
        switch (conversion.charAt(0)) {
          case 'b', 'B', 'h', 'H' -> { // boolean (general); Integer hex string (general)
            checkFlags(flagBits, LEFT_JUSTIFY | PREVIOUS, specifier);
            allowed = ALL_VALIDATOR;
          }
          case 's', 'S' -> { // formatted string (general)
            checkFlags(flagBits, LEFT_JUSTIFY | ALTERNATE | PREVIOUS, specifier);
            allowed = (flagBits & ALTERNATE) != 0 ? new FormattableValidator(specifier) : ALL_VALIDATOR;
          }
          case 'c', 'C' -> { // unicode character
            checkFlags(flagBits, LEFT_JUSTIFY | PREVIOUS, specifier);
            checkNoPrecision(precision, specifier);
            allowed = new CharValidator(specifier);
          }
          case 'd' -> { // decimal integer
            checkFlags(flagBits, ~ALTERNATE, specifier);
            checkNoPrecision(precision, specifier);
            allowed = new IntValidator(specifier);
          }
          case 'o', 'x', 'X' -> { // octal integer, hexadecimal integer
            checkFlags(flagBits, ~(PLUS | LEADING_SPACE | GROUP), specifier);
            checkNoPrecision(precision, specifier);
            allowed = new IntValidator(specifier);
          }
          case 'a', 'A' -> { // hexadecimal floating-point number
            checkFlags(flagBits, ~(PARENTHESES | GROUP), specifier);
            allowed = new FloatValidator(specifier);
          }
          case 'e', 'E' -> { // floating point -> decimal number in computerized scientific notation
            checkFlags(flagBits, ~GROUP, specifier);
            allowed = new FloatValidator(specifier);
          }
          case 'g', 'G' -> { // scientific notation
            checkFlags(flagBits, ~ALTERNATE, specifier);
            allowed = new FloatValidator(specifier);
          }
          case 'f' -> // floating point -> decimal number
            allowed = new FloatValidator(specifier);
          default -> throw new IllegalFormatException(InspectionGadgetsBundle.message("format.string.error.unknown.conversion", specifier));
        }
      }
      if (precision != null && precision.length() < 2) {
        throw new IllegalFormatException(InspectionGadgetsBundle.message("format.string.error.invalid.precision", specifier));
      }
      if (isAllBitsSet(flagBits, LEADING_SPACE | PLUS)) {
        throw new IllegalFormatException(
          InspectionGadgetsBundle.message("format.string.error.illegal.flag.combination", ' ', '+', specifier));
      }
      if (isAllBitsSet(flagBits, LEFT_JUSTIFY | ZERO_PAD)) {
        throw new IllegalFormatException(
          InspectionGadgetsBundle.message("format.string.error.illegal.flag.combination", '-', '0', specifier));
      }
      if (StringUtil.isEmpty(width)) {
        if (isAllBitsSet(flagBits, LEFT_JUSTIFY)) {
          throw new IllegalFormatException(InspectionGadgetsBundle.message("format.string.error.left.justify.no.width", specifier));
        }
        if (isAllBitsSet(flagBits, ZERO_PAD)) {
          throw new IllegalFormatException(InspectionGadgetsBundle.message("format.string.error.zero.padding.no.width", specifier));
        }
      }
      storeValidator(allowed, pos, parameters, argumentCount);
    }
    if (i < formatString.length()) {
      String endString = formatString.substring(i);
      if (!isPrefix) {
        checkText(endString);
      }
      else {
        //only case, when fsPattern doesn't find a specifier, is when there is no any conversion.
        //add "s" as a random reasonable conversion
        String suggestedString = endString + "s";
        Matcher endMatcher = fsPattern.matcher(suggestedString);
        if (!endMatcher.find() || endMatcher.end() != suggestedString.length()) {
          checkText(endString);
        }
      }
    }

    return parameters.toArray(new Validator[0]);
  }

  private static DateTimeConversionType getDateTimeConversionType(char conversion) {
    return switch (conversion) {
      case 'H', 'I', 'k', 'l', 'M', 'S', 'L', 'N', 'p', 'R', 'T', 'r' -> DateTimeConversionType.TIME;
      case 'z', 'Z' -> DateTimeConversionType.ZONE;
      case 's', 'Q', 'c' -> DateTimeConversionType.ZONED_DATE_TIME;
      case 'B', 'b', 'h', 'A', 'a', 'C', 'Y', 'y', 'j', 'm', 'd', 'e', 'D', 'F' -> DateTimeConversionType.DATE;
      default -> DateTimeConversionType.UNKNOWN;
    };
  }

  private static void checkNoPrecision(String precision, String specifier) {
    if (!StringUtil.isEmpty(precision)) {
      throw new IllegalFormatException(InspectionGadgetsBundle.message("format.string.error.precision.not.allowed", precision, specifier));
    }
  }

  private static boolean isAllBitsSet(int value, int mask) {
    return (value & mask) == mask;
  }

  private static void checkText(String s) {
    if (s.indexOf('%') != -1) {
      throw new IllegalFormatException();
    }
  }

  private static void storeValidator(Validator validator, int pos, ArrayList<Validator> parameters, int argumentCount) {
    if (pos < parameters.size()) {
      final Validator existing = parameters.get(pos);
      if (existing == null) {
        parameters.set(pos, validator);
      }
      else if (existing instanceof MultiValidator) {
        ((MultiValidator)existing).addValidator(validator);
      }
      else if (existing != validator) {
        final MultiValidator multiValidator = new MultiValidator(existing.getSpecifier());
        multiValidator.addValidator(existing);
        multiValidator.addValidator(validator);
        parameters.set(pos, multiValidator);
      }
    }
    else {
      while (pos > parameters.size() && argumentCount > parameters.size()) {
        parameters.add(null);
      }
      parameters.add(validator);
    }
  }

  /**
   * @return true if cast is required to please argument validator
   */
  public static boolean isSuspiciousFormatCall(PsiMethodCallExpression expression, PsiTypeCastExpression cast) {
    FormatArgument formatArgument =
      FormatArgument.extract(expression, Collections.emptyList(), Collections.emptyList());
    if (formatArgument == null) {
      return false;
    }

    String value = formatArgument.calculateValue();
    if (value == null) {
      return false;
    }

    int formatArgumentIndex = formatArgument.getIndex();

    PsiExpression[] arguments = expression.getArgumentList().getExpressions();

    final Validator[] validators;
    try {
      validators = decode(value, arguments.length - formatArgumentIndex);
    }
    catch (IllegalFormatException e) {
      return false;
    }

    if (validators.length == 0) {
      return false;
    }

    int idx = IntStream.range(0, arguments.length)
      .filter(i -> PsiTreeUtil.isAncestor(arguments[i], cast, false)).findFirst()
      .orElse(-1);

    if (idx < formatArgumentIndex) {
      return false;
    }

    Validator validator = validators[idx - formatArgumentIndex];
    PsiTypeElement castType = cast.getCastType();
    return validator.valid(Objects.requireNonNull(castType).getType()) &&
           !validator.valid(Objects.requireNonNull(cast.getOperand()).getType());
  }

  public static class IllegalFormatException extends RuntimeException {

    public IllegalFormatException(@Nls String message) {
      super(message);
    }

    public IllegalFormatException() { }
  }

  private static class AllValidator extends Validator {
    AllValidator() {
      super("");
    }

    @Override
    public boolean valid(PsiType type) {
      return true;
    }
  }

  private static class AllValidatorWithRange extends AllValidator {
    private final @NotNull TextRange myRange;
    private final @NotNull Spec mySpec;

    AllValidatorWithRange(@NotNull TextRange range, @NotNull Spec spec) {
      myRange = range;
      mySpec = spec;
    }

    @Override
    public @NotNull Spec getSpec() {
      return mySpec;
    }

    @Override
    public @NotNull TextRange getRange() {
      return myRange;
    }
  }

  private static class DateValidator extends Validator {

    private final DateTimeConversionType dateTimeConversionType;

    DateValidator(String specifier, DateTimeConversionType dateTimeConversionType) {
      super(specifier);
      this.dateTimeConversionType = dateTimeConversionType;
    }

    @Override
    public boolean valid(PsiType type) {
      final String text = type.getCanonicalText();
      return PsiTypes.longType().equals(type) ||
             CommonClassNames.JAVA_LANG_LONG.equals(text) ||
             InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_DATE) ||
             InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_CALENDAR) ||
             (InheritanceUtil.isInheritor(type, "java.time.temporal.TemporalAccessor") &&
              isValidTemporalAccessor(text));
    }

    private boolean isValidTemporalAccessor(String text) {
      return switch (text) {
        case CommonClassNames.JAVA_TIME_LOCAL_DATE_TIME -> dateTimeConversionType == DateTimeConversionType.TIME ||
                                                           dateTimeConversionType == DateTimeConversionType.DATE;
        case CommonClassNames.JAVA_TIME_LOCAL_DATE -> dateTimeConversionType == DateTimeConversionType.DATE;
        case CommonClassNames.JAVA_TIME_LOCAL_TIME -> dateTimeConversionType == DateTimeConversionType.TIME;
        case CommonClassNames.JAVA_TIME_OFFSET_TIME -> dateTimeConversionType == DateTimeConversionType.TIME ||
                                                       dateTimeConversionType == DateTimeConversionType.ZONE;
        default -> true;
      };
    }
  }

  private static class CharValidator extends Validator {

    CharValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      if (PsiTypes.charType().equals(type) || PsiTypes.byteType().equals(type) || PsiTypes.shortType().equals(type) || PsiTypes.intType()
        .equals(type)) {
        return true;
      }
      final String text = type.getCanonicalText();
      return CommonClassNames.JAVA_LANG_CHARACTER.equals(text) ||
             CommonClassNames.JAVA_LANG_BYTE.equals(text) ||
             CommonClassNames.JAVA_LANG_SHORT.equals(text) ||
             CommonClassNames.JAVA_LANG_INTEGER.equals(text);
    }
  }

  private static class IntValidator extends Validator {

    IntValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      final String text = type.getCanonicalText();
      return PsiTypes.intType().equals(type) ||
             CommonClassNames.JAVA_LANG_INTEGER.equals(text) ||
             PsiTypes.longType().equals(type) ||
             CommonClassNames.JAVA_LANG_LONG.equals(text) ||
             PsiTypes.shortType().equals(type) ||
             CommonClassNames.JAVA_LANG_SHORT.equals(text) ||
             PsiTypes.byteType().equals(type) ||
             CommonClassNames.JAVA_LANG_BYTE.equals(text) ||
             "java.math.BigInteger".equals(text);
    }
  }

  private static class FloatValidator extends Validator {

    FloatValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      final String text = type.getCanonicalText();
      return PsiTypes.doubleType().equals(type) ||
             CommonClassNames.JAVA_LANG_DOUBLE.equals(text) ||
             PsiTypes.floatType().equals(type) ||
             CommonClassNames.JAVA_LANG_FLOAT.equals(text) ||
             "java.math.BigDecimal".equals(text);
    }
  }

  private static class FormattableValidator extends Validator {

    FormattableValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      return InheritanceUtil.isInheritor(type, "java.util.Formattable");
    }
  }

  public static class MultiValidator extends Validator {
    private final Set<Validator> validators = new HashSet<>(3);

    @Override
    public @Nullable String getInvalidSpecifier(PsiType type) {
      for (Validator validator : validators) {
        if (!validator.valid(type)) {
          return validator.getInvalidSpecifier(type);
        }
      }
      return null;
    }

    MultiValidator(String specifier) {
      super(specifier);
    }

    @Override
    public boolean valid(PsiType type) {
      for (Validator validator : validators) {
        if (!validator.valid(type)) {
          return false;
        }
      }
      return true;
    }

    public Set<Validator> getValidators() {
      return validators;
    }

    public void addValidator(Validator validator) {
      validators.add(validator);
    }
  }

  public abstract static class Validator {

    public @Nullable String getInvalidSpecifier(PsiType type){
      if (valid(type)) {
        return null;
      }
      return getSpecifier();
    }
    private final String mySpecifier;

    Validator(String specifier) {
      mySpecifier = specifier;
    }

    public abstract boolean valid(PsiType type);

    public String getSpecifier() {
      return mySpecifier;
    }

    public @Nullable TextRange getRange() {
      return null;
    }

    public @Nullable Spec getSpec() {
      return null;
    }
  }

  /**
   * @param validators validators returned from {@link #decode(String, int)} or similar methods
   * @return list of {@link FormatPlaceholder} objects
   */
  public static @NotNull List<@NotNull FormatPlaceholder> asPlaceholders(Validator @NotNull [] validators) {
    List<FormatPlaceholder> result = new ArrayList<>();
    for (int i = 0; i < validators.length; i++) {
      FormatDecode.Validator metaValidator = validators[i];
      if (metaValidator == null) continue;
      Collection<FormatDecode.Validator> unpacked = metaValidator instanceof FormatDecode.MultiValidator multi ?
                                                    multi.getValidators() : List.of(metaValidator);
      for (FormatDecode.Validator validator : unpacked) {
        TextRange stringRange = validator.getRange();
        if (stringRange == null) continue;
        record MyPlaceholder(int index, @NotNull TextRange range) implements FormatPlaceholder {}
        result.add(new MyPlaceholder(i, stringRange));
      }
    }
    return result;
  } 

  public record Spec(@Nullable String posSpec ,
                     @Nullable String flags,
                     @Nullable String width,
                     @Nullable String precision,
                     @Nullable String dateSpec,
                     @Nullable String conversion){

  }

  public static final class FormatArgument {
    private final int myIndex;
    private final PsiExpression myExpression;

    private FormatArgument(int index, PsiExpression expression) {
      myIndex = index;
      myExpression = expression;
    }

    public int getIndex() {
      return myIndex;
    }

    public PsiExpression getExpression() {
      return myExpression;
    }

    public static @Nullable FormatArgument extract(@NotNull PsiCallExpression expression, @NotNull List<String> methodNames, @NotNull List<String> classNames) {
      return extract(expression, methodNames, classNames, false);
    }

    public static @Nullable FormatArgument extract(@NotNull PsiCallExpression expression,
                                                   @NotNull List<String> methodNames,
                                                   @NotNull List<String> classNames,
                                                   boolean allowNotConstant) {
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) return null;
      PsiExpression[] arguments = argumentList.getExpressions();

      final PsiExpression formatArgument;
      int formatArgumentIndex;
      if (expression instanceof PsiMethodCallExpression && FormatUtils.STRING_FORMATTED.matches(expression)) {
        formatArgument = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
        formatArgumentIndex = 0;
      }
      else {
        if (!(expression instanceof PsiMethodCallExpression) ||
            !FormatUtils.isFormatCall((PsiMethodCallExpression)expression, methodNames, classNames)) {
          return fromPrintFormatAnnotation(expression);
        }

        formatArgumentIndex =
          IntStream.range(0, arguments.length).filter(i -> ExpressionUtils.hasStringType(arguments[i])).findFirst().orElse(-1);
        if (formatArgumentIndex < 0) {
          return null;
        }

        formatArgument = arguments[formatArgumentIndex];
        formatArgumentIndex++;
      }
      if (!ExpressionUtils.hasStringType(formatArgument) || (!allowNotConstant && !PsiUtil.isConstantExpression(formatArgument))) {
        return null;
      }
      return new FormatArgument(formatArgumentIndex, formatArgument);
    }

    private static FormatArgument fromPrintFormatAnnotation(@NotNull PsiCallExpression call) {
      PsiExpressionList argList = call.getArgumentList();
      if (argList == null || argList.isEmpty()) return null;
      PsiMethod method = call.resolveMethod();
      if (method == null) return null;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length < 2) return null;
      PsiType lastParameterType = parameters[parameters.length - 1].getType();
      if (lastParameterType instanceof PsiArrayType && TypeUtils.isJavaLangObject(((PsiArrayType)lastParameterType).getComponentType())) {
        int formatIndex = parameters.length - 2;
        PsiParameter maybeFormat = parameters[formatIndex];
        if (TypeUtils.isJavaLangString(maybeFormat.getType()) &&
            AnnotationUtil.isAnnotated(maybeFormat, "org.intellij.lang.annotations.PrintFormat", AnnotationUtil.CHECK_EXTERNAL)) {
          PsiExpression[] args = argList.getExpressions();
          if (args.length <= formatIndex) return null;
          return new FormatArgument(formatIndex + 1, args[formatIndex]);
        }
      }
      return null;
    }

    public String calculateValue() {
      final PsiType formatType = myExpression.getType();
      if (formatType == null) {
        return null;
      }
      return (String)ConstantExpressionUtil.computeCastTo(myExpression, formatType);
    }

    public String calculatePrefixValue() {
      StringBuilder builder = new StringBuilder();
      boolean hasText = false;
      final PsiType formatType = myExpression.getType();
      if (formatType == null || !formatType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return null;
      }

      PsiExpression psiExpression = myExpression;
      if (myExpression instanceof PsiLocalVariable variable) {
        if (VariableAccessUtils.variableIsAssigned(variable)) {
          return null;
        }
        psiExpression = variable.getInitializer();
      }

      if (psiExpression instanceof PsiPolyadicExpression polyadicExpression) {
        PsiExpression[] operands = polyadicExpression.getOperands();
        for (int i = 0, length = operands.length; i < length; i++) {
          PsiExpression operand = operands[i];
          String stringPart = (String)ConstantExpressionUtil.computeCastTo(operand, formatType);
          if (stringPart != null) {
            if (i == 0) {
              hasText = true;
            }
            builder.append(stringPart);
          }
          else {
            return hasText ? builder.toString() : null;
          }
        }
      }
      return builder.toString();
    }
  }

  private enum DateTimeConversionType {
    UNKNOWN, TIME, ZONE, ZONED_DATE_TIME, DATE
  }
}