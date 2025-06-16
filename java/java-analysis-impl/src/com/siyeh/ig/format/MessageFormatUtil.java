// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.text.ChoiceFormat;
import java.util.*;

import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

/**
 * Utilities related to MessageFormat-like format string
 */
public final class MessageFormatUtil {
  /**
   * Matcher to match known JDK library methods that accept MessageFormat-like format string, along with an array/vararg of arguments
   */
  public static final CallMatcher PATTERN_METHODS = anyOf(
    staticCall("java.text.MessageFormat", "format").parameterCount(2)
  );

  private static final Map<String, List<String>> knownContractions = Map.ofEntries(
    Map.entry("aren", List.of("t")),
    Map.entry("can", List.of("t")),
    Map.entry("couldn", List.of("t")),
    Map.entry("didn", List.of("t")),
    Map.entry("doesn", List.of("t")),
    Map.entry("don", List.of("t")),
    Map.entry("hadn", List.of("t")),
    Map.entry("hasn", List.of("t")),
    Map.entry("haven", List.of("t")),
    Map.entry("he", List.of("d", "ll", "s")),
    Map.entry("i", List.of("d", "ll", "m", "ve")),
    Map.entry("isn", List.of("t")),
    Map.entry("it", List.of("s")),
    Map.entry("mustn", List.of("t")),
    Map.entry("shan", List.of("t")),
    Map.entry("she", List.of("d", "ll", "s")),
    Map.entry("shouldn", List.of("t")),
    Map.entry("that", List.of("s")),
    Map.entry("there", List.of("s")),
    Map.entry("they", List.of("d", "ll", "re", "ve")),
    Map.entry("we", List.of("d", "re", "ve")),
    Map.entry("weren", List.of("t")),
    Map.entry("what", List.of("ll", "re", "s", "ve")),
    Map.entry("where", List.of("s")),
    Map.entry("who", List.of("d", "ll", "re", "s", "ve")),
    Map.entry("won", List.of("t")),
    Map.entry("wouldn", List.of("t")),
    Map.entry("you", List.of("d", "ll", "re", "ve"))
  );

  /**
   * @param pattern MessageFormat-like formatting string
   * @return MessageFormatResult object that contains information about placeholders and possible syntax errors inside the pattern
   */
  public static @NotNull MessageFormatResult checkFormat(@NotNull String pattern) {
    if (pattern.isEmpty()) {
      return new MessageFormatResult(true, List.of(), List.of());
    }
    MessageHolder holder = parseMessageHolder(pattern);
    List<MessageFormatError> errors = holder.getErrors();
    List<MessageFormatPart> parts = holder.getParts();
    for (MessageFormatPart part : parts) {
      if (part.getParsedType() == MessageFormatParsedType.STRING) {
        String string = part.getText();
        List<MessageFormatError> doubleQuoteErrors = checkQuote(string);
        //rearrange quote errors, because quotes can be merged
        errors.addAll(rearrangeErrors(doubleQuoteErrors, pattern, string, part.start));
      }
    }
    List<MessageFormatPlaceholder> placeholderIndexes = new ArrayList<>();
    for (MessageFormatPart part : parts) {
      if (part.getParsedType() == MessageFormatParsedType.FORMAT_ELEMENT &&
          part.getMessageFormatElement() != null &&
          part.getMessageFormatElement().getIndex() != null) {
        placeholderIndexes.add(new MessageFormatPlaceholder(part.getMessageFormatElement().getIndex(),
                                                            part.getMessageFormatElement().formatType != null ?
                              new TextRange(part.start, part.getMessageFormatElement().formatTypeSegmentStart) :
                              new TextRange(part.start, part.start + part.text.length()),
                                                            part.getMessageFormatElement().formatType == null &&
                                                            part.getMessageFormatElement().currentPart == MessageFormatElementPart.ARGUMENT_INDEX));
      }
    }
    return new MessageFormatResult(errors.isEmpty(), errors, placeholderIndexes);
  }

  private static @NotNull List<MessageFormatError> rearrangeErrors(@NotNull List<MessageFormatError> errors,
                                                                   @NotNull String fullPattern,
                                                                   @NotNull String string,
                                                                   int start) {
    if (errors.isEmpty()) {
      return errors;
    }
    TreeMap<Integer, Integer> stringTranslation = new TreeMap<>();
    stringTranslation.put(0, 0);
    TreeMap<Integer, Pair<Integer, Integer>> fullPatternTranslation = new TreeMap<>();
    int indexWithoutQuotes = 0;
    for (int i = 0; i < string.length(); i++) {
      if (string.charAt(i) != '\'') {
        if (i - 1 >= 0 && string.charAt(i - 1) == '\'') {
          stringTranslation.put(i, indexWithoutQuotes);
        }
        indexWithoutQuotes++;
      }
      else {
        if (i - 1 < 0 || string.charAt(i - 1) != '\'') {
          stringTranslation.put(i, indexWithoutQuotes);
        }
      }
    }
    stringTranslation.put(string.length(), indexWithoutQuotes + 1);

    indexWithoutQuotes = 0;
    fullPatternTranslation.put(0, Pair.pair(start, start));
    for (int i = start; i < fullPattern.length(); i++) {
      if (fullPattern.charAt(i) != '\'') {
        indexWithoutQuotes++;
      }
      else if (i - 1 < 0 || fullPattern.charAt(i - 1) != '\'') {
        int to = i;
        while (to < fullPattern.length() && fullPattern.charAt(to) == '\'') {
          to++;
        }
        fullPatternTranslation.put(indexWithoutQuotes, Pair.pair(i, to));
      }
    }
    if (!fullPatternTranslation.containsKey(indexWithoutQuotes)) {
      fullPatternTranslation.put(indexWithoutQuotes + 1, Pair.pair(fullPattern.length(), fullPattern.length()));
    }
    List<MessageFormatError> rearrangedErrors = new ArrayList<>();
    for (MessageFormatError error : errors) {
      Integer fromIndex = error.fromIndex();
      Integer toIndex = error.toIndex();
      Integer floorFromIndex = stringTranslation.floorKey(fromIndex);
      Integer ceilToIndex = stringTranslation.ceilingKey(toIndex);
      if (floorFromIndex == null || ceilToIndex == null) continue;
      Integer convertedShiftedIndex = stringTranslation.get(floorFromIndex);
      fromIndex = convertedShiftedIndex + (fromIndex - floorFromIndex);
      convertedShiftedIndex = stringTranslation.get(ceilToIndex);
      toIndex = convertedShiftedIndex + (toIndex - ceilToIndex);
      //quotes
      if (toIndex.equals(fromIndex)) {
        Pair<Integer, Integer> pair = fullPatternTranslation.get(fromIndex);
        if (pair == null) continue;
        rearrangedErrors.add(new MessageFormatError(error.errorType(), pair.first, pair.second));
      }
      else {
        floorFromIndex = fullPatternTranslation.floorKey(fromIndex);
        ceilToIndex = fullPatternTranslation.ceilingKey(toIndex);
        if (floorFromIndex == null || ceilToIndex == null) {
          continue;
        }
        Pair<Integer, Integer> shifterPair = fullPatternTranslation.get(floorFromIndex);
        fromIndex = shifterPair.second + (fromIndex - floorFromIndex);
        shifterPair = fullPatternTranslation.get(ceilToIndex);
        toIndex = shifterPair.first + (toIndex - ceilToIndex);
        rearrangedErrors.add(new MessageFormatError(error.errorType(), fromIndex, toIndex));
      }
    }
    return rearrangedErrors;
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull List<MessageFormatError> checkQuote(@NotNull String string) {
    List<MessageFormatError> errors = new ArrayList<>();
    int fromIndex = 0;
    while (true) {
      int currentQuoteIndex = string.indexOf('\'', fromIndex);
      if (currentQuoteIndex == -1) {
        break;
      }
      int afterContradiction = currentQuoteIndex;
      while (afterContradiction < string.length() && string.charAt(afterContradiction) == '\'') {
        afterContradiction++;
      }
      if (afterContradiction >= string.length()) {
        break;
      }
      fromIndex = afterContradiction;
      if (currentQuoteIndex + 1 == afterContradiction) {
        continue;
      }
      String firstPartContradiction = new StringBuilder(getStringWord(string, currentQuoteIndex - 1, i -> i - 1)).reverse().toString();
      String secondPartContradiction = getStringWord(string, afterContradiction, i -> i + 1);

      List<String> knownSecondParts = knownContractions.get(firstPartContradiction.toLowerCase(Locale.ROOT));
      if (knownSecondParts == null) {
        continue;
      }
      if (knownSecondParts.contains(secondPartContradiction.toLowerCase(Locale.ROOT))) {
        errors.add(new MessageFormatError(MessageFormatErrorType.MANY_QUOTES, currentQuoteIndex, fromIndex));
      }
    }
    return errors;
  }

  private static @NotNull String getStringWord(@NotNull String string, int from, @NotNull Int2IntFunction nextIntFun) {
    StringBuilder builder = new StringBuilder();
    int nextInt = from;
    while (nextInt >= 0 && nextInt < string.length() && Character.isLetter(string.charAt(nextInt))) {
      builder.append(string.charAt(nextInt));
      nextInt = nextIntFun.applyAsInt(nextInt);
    }
    return builder.toString();
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull MessageHolder parseMessageHolder(@NotNull String pattern) {
    MessageHolder holder = new MessageHolder(pattern);
    while (!holder.hasRuntimeError && holder.hasNext()) {
      char ch = holder.nextPool();
      if (holder.getLastPart().getParsedType() == MessageFormatParsedType.STRING) {
        if (ch == '\'') {
          if (holder.hasNext() && holder.nextPeek() == '\'') {
            holder.addChar(ch);
            holder.nextPool();
          }
          else {
            holder.inQuote = !holder.inQuote;
            holder.lastQuoteIndex = holder.current;
          }
        }
        else if (ch == '{') {
          if (holder.inQuote) {
            int end = findEndOfQuotedPlaceholder(pattern, holder.current);
            if (end != -1) {
              holder.addError(MessageFormatErrorType.QUOTED_PLACEHOLDER, holder.current, end);
            }
            holder.addChar(ch);
          }
          else {
            holder.startFormatElement(ch);
          }
        }
        else {
          holder.addChar(ch);
        }
      }
      else {
        if (holder.inQuote) {
          if (ch == '\'') {
            holder.inQuote = false;
          }
          holder.addChar(ch);
        }
        else {
          switch (ch) {
            case ',' -> {
              if (!holder.moveSegmentOfFormatElement()) {
                holder.addChar(ch);
              }
            }
            case '{' -> {
              holder.braceStack++;
              holder.addChar(ch);
            }
            case ' ' -> {
              MessageFormatPart part = holder.getLastPart();
              if (!(part.getMessageFormatElement() != null &&
                    part.getMessageFormatElement().currentPart == MessageFormatElementPart.FORMAT_TYPE &&
                    part.getMessageFormatElement().formatTypeSegment.isEmpty())) {
                holder.addChar(ch);
              }
            }
            case '}' -> {
              if (holder.braceStack != 0) {
                holder.braceStack--;
                holder.addChar(ch);
              }
              else {
                holder.finishFormatElement(ch);
              }
            }
            case '\'' -> {
              holder.inQuote = true;
              holder.lastQuoteIndex = holder.current;
              holder.addChar(ch);
            }
            default -> holder.addChar(ch);
          }
        }
      }
    }

    MessageFormatPart lastPart = holder.getLastPart();
    if (holder.inQuote) {
      holder.addError(MessageFormatErrorType.UNPAIRED_QUOTE, holder.lastQuoteIndex, holder.lastQuoteIndex + 1);
    }
    if (lastPart.getMessageFormatElement() != null &&
        holder.braceStack == 0 &&
        holder.getLastPart().parsedType != MessageFormatParsedType.STRING) {
      holder.addError(MessageFormatErrorType.UNMATCHED_BRACE,
                      lastPart.getMessageFormatElement().indexSegmentStart - 1,
                      lastPart.getMessageFormatElement().indexSegmentStart);
    }
    else if (lastPart.getMessageFormatElement() != null && !lastPart.getMessageFormatElement().finished) {
      holder.addError(MessageFormatErrorType.UNCLOSED_BRACE_PLACEHOLDER,
                      lastPart.getMessageFormatElement().indexSegmentStart - 1,
                      lastPart.getMessageFormatElement().indexSegmentStart);
    }

    return holder;
  }

  private static int findEndOfQuotedPlaceholder(@NotNull String pattern, int current) {
    if (!(current - 1 >= 0 && pattern.charAt(current - 1) == '\'')) {
      return -1;
    }
    String nextPattern = pattern.substring(current);
    int from = 0;
    int nextQuote;
    while (true) {
      nextQuote = nextPattern.indexOf('\'', from);
      if (nextQuote == -1) {
        return -1;
      }
      if (nextPattern.length() > nextQuote + 1 && nextPattern.charAt(nextQuote + 1) == '\'') {
        from = nextQuote + 2;
        continue;
      }
      break;
    }
    nextPattern = nextPattern.substring(0, nextQuote);
    if (nextPattern.startsWith("{") && nextPattern.endsWith("}") &&
        nextPattern.indexOf("}") == nextPattern.length() - 1) {
      MessageHolder holder = parseMessageHolder(nextPattern);
      if (holder.errors.isEmpty()) {
        List<MessageFormatPart> notStrings =
          ContainerUtil.filter(holder.parts, t -> !(t.getParsedType() == MessageFormatParsedType.STRING && t.getText().isEmpty()));
        if (notStrings.size() == 1 && notStrings.get(0).getParsedType() == MessageFormatParsedType.FORMAT_ELEMENT) {
          return nextQuote + current;
        }
      }
    }
    return -1;
  }

  private static @NotNull MessageHolder parseChoice(@NotNull String patten) {
    MessageHolder holder = new MessageHolder(patten);
    holder.parts.clear();
    holder.startNumberElement();
    List<Double> selectors = new ArrayList<>();
    List<Integer> startIndexes = new ArrayList<>();
    startIndexes.add(0);
    while (holder.hasNext() && !holder.hasRuntimeError) {
      char ch = holder.nextPool();
      if (ch == '\'') {
        if (holder.hasNext() && holder.nextPeek() == '\'') {
          holder.addChar(ch);
          holder.nextPool();
        }
        else {
          holder.inQuote = !holder.inQuote;
        }
      }
      else if (holder.inQuote) {
        holder.addChar(ch);
      }
      //finish selector
      else if (ch == '<' || ch == '#' || ch == '≤') {
        MessageFormatPart part = holder.getLastPart();
        String selector = part.text.toString();
        if (part.getParsedType() != MessageFormatParsedType.NUMBER || selector.isEmpty()) {
          holder.addError(MessageFormatErrorType.SELECTOR_NOT_FOUND, holder.current, holder.current + 1);
        }
        else if (!selector.equals("∞") && !selector.equals("-∞")) {
          try {
            double currentSelector = Double.parseDouble(selector);
            if (ch == '<') {
              currentSelector = ChoiceFormat.nextDouble(currentSelector);
            }
            if (!selectors.isEmpty()) {
              Double previousSelector = selectors.get(selectors.size() - 1);
              if (previousSelector >= currentSelector) {
                holder.addError(MessageFormatErrorType.INCORRECT_ORDER_CHOICE_SELECTOR, holder.current - selector.length(), holder.current);
              }
            }
            selectors.add(currentSelector);
          }
          catch (NumberFormatException e) {
            holder.addError(MessageFormatErrorType.INCORRECT_CHOICE_SELECTOR, holder.current - selector.length(), holder.current);
          }
        }
        startIndexes.add(holder.current + 1);
        holder.startStringElement();
      }
      else if (ch == '|') {
        startIndexes.add(holder.current + 1);
        holder.startNumberElement();
      }
      else {
        holder.addChar(ch);
      }
    }
    List<MessageFormatPart> parts = new ArrayList<>(holder.getParts());
    holder.parts.clear();
    for (int i = 0; !holder.hasRuntimeError && i < parts.size(); i++) {
      MessageFormatPart part = parts.get(i);
      String currentSubPattern = part.getText();
      if (part.getParsedType() == MessageFormatParsedType.STRING && currentSubPattern.indexOf('{') >= 0) {
        holder.merge(parseMessageHolder(currentSubPattern), startIndexes.get(i));
      }
      else {
        holder.parts.add(part);
      }
    }
    return holder;
  }

  enum MessageFormatType {
    NUMBER, DATE, TIME, CHOICE
  }

  @ApiStatus.Internal
  public enum MessageFormatParsedType {
    STRING, FORMAT_ELEMENT, NUMBER /*for choice format*/
  }

  enum MessageFormatElementPart {
    ARGUMENT_INDEX, FORMAT_TYPE, FORMAT_STYLE
  }

  public enum MessageFormatErrorType {
    QUOTED_PLACEHOLDER(ErrorSeverity.WEAK_WARNING),
    UNPARSED_INDEX(ErrorSeverity.RUNTIME_EXCEPTION),
    INDEX_NEGATIVE(ErrorSeverity.RUNTIME_EXCEPTION),
    UNKNOWN_FORMAT_TYPE(ErrorSeverity.RUNTIME_EXCEPTION),
    UNCLOSED_BRACE_PLACEHOLDER(ErrorSeverity.WARNING),
    UNPAIRED_QUOTE(ErrorSeverity.WARNING),
    UNMATCHED_BRACE(ErrorSeverity.RUNTIME_EXCEPTION),
    MANY_QUOTES(ErrorSeverity.WEAK_WARNING),
    INCORRECT_CHOICE_SELECTOR(ErrorSeverity.RUNTIME_EXCEPTION),
    SELECTOR_NOT_FOUND(ErrorSeverity.RUNTIME_EXCEPTION),
    INCORRECT_ORDER_CHOICE_SELECTOR(ErrorSeverity.RUNTIME_EXCEPTION);

    private final ErrorSeverity severity;

    MessageFormatErrorType(@NotNull ErrorSeverity severity) {
      this.severity = severity;
    }

    public ErrorSeverity getSeverity() {
      return severity;
    }
  }

  public enum ErrorSeverity {
    RUNTIME_EXCEPTION, WARNING, WEAK_WARNING
  }

  /**
   * Information about MessageFormat-like format string
   * 
   * @param valid if true, then the format string is valid
   * @param errors list of errors inside the format string
   * @param placeholders list of placeholders inside the format string
   */
  public record MessageFormatResult(boolean valid, @NotNull List<MessageFormatError> errors,
                                    @NotNull List<MessageFormatPlaceholder> placeholders) {
  }

  public record MessageFormatPlaceholder(int index, @NotNull TextRange range, boolean isString) implements FormatPlaceholder {
  }

  @ApiStatus.Internal
  public static final class MessageFormatPart {
    private final @NotNull StringBuilder text = new StringBuilder();
    private final @NotNull MessageFormatParsedType parsedType;
    private final @Nullable MessageFormatUtil.MessageFormatElement messageFormatElement;
    private int start;

    private MessageFormatPart(int start, @NotNull MessageFormatParsedType type, @Nullable MessageFormatElement element) {
      this.start = start;
      parsedType = type;
      messageFormatElement = element;
    }

    public @Nullable MessageFormatElement getMessageFormatElement() {
      return messageFormatElement;
    }

    @VisibleForTesting
    public String getText() {
      return text.toString();
    }

    public @NotNull MessageFormatParsedType getParsedType() {
      return parsedType;
    }

    private void add(char ch) {
      text.append(ch);
      if (messageFormatElement != null) {
        messageFormatElement.add(ch);
      }
    }
  }

  @ApiStatus.Internal
  public static final class MessageFormatElement {
    private final StringBuilder indexSegment = new StringBuilder();
    private final StringBuilder formatTypeSegment = new StringBuilder();
    private final StringBuilder formatStyleSegment = new StringBuilder();
    private boolean finished = false;
    private MessageFormatElementPart currentPart = MessageFormatElementPart.ARGUMENT_INDEX;
    private int indexSegmentStart = 0;
    private int formatTypeSegmentStart = 0;
    private int formatStyleSegmentStart = 0;

    private @Nullable Integer index;
    private @Nullable MessageFormatType formatType;

    public @Nullable Integer getIndex() {
      return index;
    }

    private boolean tryMoveSegment(@NotNull MessageHolder holder) {
      if (currentPart == MessageFormatElementPart.ARGUMENT_INDEX) {
        formatTypeSegmentStart = holder.current + 1;
        currentPart = MessageFormatElementPart.FORMAT_TYPE;
        return true;
      }
      if (currentPart == MessageFormatElementPart.FORMAT_TYPE) {
        formatStyleSegmentStart = holder.current + 1;
        currentPart = MessageFormatElementPart.FORMAT_STYLE;
        return true;
      }
      return false;
    }

    private void add(char ch) {
      switch (currentPart) {
        case ARGUMENT_INDEX -> indexSegment.append(ch);
        case FORMAT_TYPE -> formatTypeSegment.append(ch);
        case FORMAT_STYLE -> formatStyleSegment.append(ch);
      }
    }

    private void finish(@NotNull MessageHolder holder) {
      this.finished = true;
      try {
        index = Integer.parseInt(indexSegment.toString());
        if (index < 0) {
          holder.addError(MessageFormatErrorType.INDEX_NEGATIVE, indexSegmentStart, indexSegmentStart + indexSegment.length());
        }
      }
      catch (NumberFormatException e) {
        holder.addError(MessageFormatErrorType.UNPARSED_INDEX, indexSegmentStart, indexSegmentStart + indexSegment.length());
      }
      if (!formatTypeSegment.isEmpty()) {
        String typeSegment = formatTypeSegment.toString();
        for (MessageFormatType value : MessageFormatType.values()) {
          if (value.name().equals(typeSegment.trim().toUpperCase(Locale.ROOT))) {
            formatType = value;
          }
        }
        if (formatType == null) {
          if (formatStyleSegmentStart >= formatTypeSegmentStart) {
            holder.addError(MessageFormatErrorType.UNKNOWN_FORMAT_TYPE, formatTypeSegmentStart, formatStyleSegmentStart - 1);
          }
          else {
            holder.addError(MessageFormatErrorType.UNKNOWN_FORMAT_TYPE, formatTypeSegmentStart, holder.current);
          }
        }
      }

      tryParseStyle(holder);
    }

    private void tryParseStyle(@NotNull MessageHolder holder) {
      if (formatType != null) {
        switch (formatType) {
          case CHOICE -> {
            MessageHolder choiceHolder = parseChoice(formatStyleSegment.toString());
            holder.merge(choiceHolder, formatStyleSegmentStart);
          }
          case NUMBER, DATE, TIME -> {
            //skip, because it may depend on locals
          }
          default -> throw new IllegalArgumentException("formatType: " + formatType);
        }
      }
    }
  }

  public record MessageFormatError(@NotNull MessageFormatErrorType errorType, int fromIndex, int toIndex) {
  }

  @ApiStatus.Internal
  public static final class MessageHolder {
    private final String pattern;
    private final List<MessageFormatPart> parts = new ArrayList<>();
    private final List<MessageFormatError> errors = new ArrayList<>();
    private boolean hasRuntimeError = false;
    private int current = -1;
    private int braceStack = 0;
    private boolean inQuote = false;
    private int lastQuoteIndex = -1;

    private MessageHolder(@NotNull String pattern) {
      this.pattern = pattern;
      parts.add(new MessageFormatPart(0, MessageFormatParsedType.STRING, null));
    }

    public List<MessageFormatPart> getParts() {
      return parts;
    }

    public List<MessageFormatError> getErrors() {
      return errors;
    }

    boolean hasNext() {
      return current + 1 < pattern.length();
    }

    char nextPool() {
      return pattern.charAt(++current);
    }

    char nextPeek() {
      return pattern.charAt(current + 1);
    }

    private void startStringElement() {
      parts.add(new MessageFormatPart(current + 1, MessageFormatParsedType.STRING, null));
    }

    private void startNumberElement() {
      parts.add(new MessageFormatPart(current, MessageFormatParsedType.NUMBER, null));
    }

    private @NotNull MessageFormatPart getLastPart() {
      return parts.get(parts.size() - 1);
    }

    private void startFormatElement(char ch) {
      MessageFormatElement element = new MessageFormatElement();
      element.indexSegmentStart = current + 1; //skip {
      MessageFormatPart messageFormatPart = new MessageFormatPart(current, MessageFormatParsedType.FORMAT_ELEMENT, element);
      parts.add(messageFormatPart);
      messageFormatPart.text.append(ch);
    }

    private void addChar(char ch) {
      getLastPart().add(ch);
    }

    private boolean moveSegmentOfFormatElement() {
      MessageFormatPart part = getLastPart();
      if (part.parsedType != MessageFormatParsedType.FORMAT_ELEMENT ||
          part.messageFormatElement == null ||
          !part.messageFormatElement.tryMoveSegment(this)) {
        return false;
      }
      part.text.append(',');
      return true;
    }

    private void finishFormatElement(char ch) {
      MessageFormatPart part = getLastPart();
      part.text.append(ch);
      if (part.messageFormatElement != null) {
        part.messageFormatElement.finish(this);
      }
      startStringElement();
    }

    private void addError(@NotNull MessageFormatErrorType errorType, int from, int until) {
      if (!hasRuntimeError) {
        errors.add(new MessageFormatError(errorType, from, until));
      }
      if (errorType.severity == ErrorSeverity.RUNTIME_EXCEPTION) {
        hasRuntimeError = true;
      }
    }

    private void merge(@NotNull MessageHolder subHolder, int start) {
      List<MessageFormatError> errors = subHolder.getErrors();
      for (MessageFormatError error : rearrangeErrors(errors, this.pattern, subHolder.pattern, start)) {
        addError(error.errorType, error.fromIndex, error.toIndex);
      }
      for (MessageFormatPart part : subHolder.parts) {
        part.start += start;
        MessageFormatElement nestedMessageFormatElement = part.messageFormatElement;
        if (nestedMessageFormatElement != null) {
          nestedMessageFormatElement.indexSegmentStart += start;
          nestedMessageFormatElement.formatTypeSegmentStart += start;
          nestedMessageFormatElement.formatStyleSegmentStart += start;
        }
        this.parts.add(part);
      }
    }
  }
}
