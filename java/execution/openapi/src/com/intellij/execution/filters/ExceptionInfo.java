// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public class ExceptionInfo {
  private static final @NonNls String EXCEPTION_IN_THREAD = "Exception in thread \"";
  private static final @NonNls String CAUSED_BY = "Caused by: ";
  private final int myClassNameOffset;
  private final @NotNull String myExceptionClassName;
  private final @NotNull String myExceptionMessage;

  ExceptionInfo(int offset, @NotNull String exceptionClassName, @NotNull String exceptionMessage) {
    myClassNameOffset = offset;
    myExceptionClassName = exceptionClassName;
    myExceptionMessage = exceptionMessage;
  }

  @Nullable ExceptionLineRefiner.RefinerMatchResult matchSpecificExceptionElement(@NotNull PsiElement element) {
    return null;
  }

  /**
   * @return a predicate that matches an element within the source line that is likely an exception source
   */
  public @NotNull ExceptionLineRefiner getPositionRefiner() {
    return new AfterExceptionRefiner(this);
  }

  /**
   * @return offset of the class name within the parsed line
   */
  public int getClassNameOffset() {
    return myClassNameOffset;
  }

  /**
   * @return exception class name
   */
  public @NotNull String getExceptionClassName() {
    return myExceptionClassName;
  }

  /**
   * @return exception message, empty string if absent
   */
  public @NotNull String getExceptionMessage() {
    return myExceptionMessage;
  }

  public static @Nullable ExceptionInfo parseMessage(String line, int textEndOffset) {
    int firstSpace = line.indexOf(' ');
    int colonPos = -1;
    TextRange classRange = null;
    if (firstSpace == -1) {
      classRange = getExceptionClassFromMessage(line, 0, getLength(line));
    }
    else if (firstSpace == "Caused".length() && line.startsWith(CAUSED_BY)) {
      colonPos = line.indexOf(':', CAUSED_BY.length());
      classRange = getExceptionClassFromMessage(line, CAUSED_BY.length(), colonPos == -1 ? getLength(line) : colonPos);
    }
    else if (firstSpace == "Exception".length() && line.startsWith(EXCEPTION_IN_THREAD)) {
      int nextQuotePos = line.indexOf("\" ", EXCEPTION_IN_THREAD.length());
      if (nextQuotePos == -1) return null;
      int start = nextQuotePos + "\" ".length();
      colonPos = line.indexOf(':', start);
      classRange = getExceptionClassFromMessage(line, start, colonPos == -1 ? getLength(line) : colonPos);
    }
    else if (firstSpace > 2 && line.charAt(firstSpace - 1) == ':') {
      colonPos = firstSpace - 1;
      classRange = getExceptionClassFromMessage(line, 0, firstSpace - 1);
    }
    else if (firstSpace > 3 && line.charAt(0) == '[' && line.charAt(firstSpace - 1) == ':' && line.charAt(firstSpace - 2) == ']') {
      colonPos = line.indexOf(':', firstSpace);
      classRange = getExceptionClassFromMessage(line, firstSpace + 1, colonPos == -1 ? getLength(line) : colonPos);
    }
    if (classRange == null) return null;
    String message = colonPos == -1 ? null : line.substring(colonPos + 1).trim();
    message = StringUtil.notNullize(message);
    int absoluteOffset = textEndOffset - line.length();
    String exceptionName = classRange.substring(line);
    int startOffset = classRange.getStartOffset() + absoluteOffset;
    return createExceptionInfo(message, exceptionName, startOffset);
  }

  private static @NotNull ExceptionInfo createExceptionInfo(String message, String exceptionName, int startOffset) {
    return switch (exceptionName) {
      case "java.lang.ArrayIndexOutOfBoundsException" -> new ArrayIndexOutOfBoundsExceptionInfo(startOffset, message);
      case "java.lang.ArrayStoreException" -> new ArrayStoreExceptionInfo(startOffset, message);
      case "java.lang.ClassCastException" -> new ClassCastExceptionInfo(startOffset, message);
      case "java.lang.NullPointerException" -> new NullPointerExceptionInfo(startOffset, message);
      case "java.lang.AssertionError" -> new AssertionErrorInfo(startOffset, message);
      case "java.lang.ArithmeticException" -> new ArithmeticExceptionInfo(startOffset, message);
      case "java.lang.NegativeArraySizeException" -> new NegativeArraySizeExceptionInfo(startOffset, message);
      default -> {
        ExceptionInfo info =
          JetBrainsNotNullInstrumentationExceptionInfo.tryCreate(startOffset, exceptionName, message);
        yield info != null ? info : new ExceptionInfo(startOffset, exceptionName, message);
      }
    };
  }

  private static int getLength(String line) {
    int length = line.length();
    while (length > 2 && Character.isWhitespace(line.charAt(length - 1))) {
      length--;
    }
    return length;
  }

  /**
   * Returns a substring of {@code line} from {@code from} to {@code to} position after heuristically checking that
   * given substring could be an exception class name. Currently, all names which are not very long, consist of
   * Java identifier symbols and have at least one dot are considered to be possible exception names by this method.
   *
   * @param line line to extract exception name from
   * @param from start index
   * @param to   end index (exclusive)
   * @return a substring between from and to or null if it doesn't look like an exception name.
   */
  private static TextRange getExceptionClassFromMessage(String line, int from, int to) {
    if (to - from > 200) return null;
    boolean hasDot = false;
    for (int i = from; i < to; i++) {
      char c = line.charAt(i);
      if (c == '.' && i == from) return null;
      if (c == '$' && !hasDot) return null;
      if (c != '.' && c != '$' && !Character.isJavaIdentifierPart(c)) return null;
      hasDot |= c == '.';
    }
    if (!hasDot) return null;
    return new TextRange(from, to);
  }

  private static class AfterExceptionRefiner implements ExceptionLineRefiner {
    private final ExceptionInfo myInfo;

    private AfterExceptionRefiner(ExceptionInfo info) { this.myInfo = info;}

    @Override
    public RefinerMatchResult matchElement(@NotNull PsiElement current) {
      PsiElement newException = findNewException(current);
      if (newException != null) return RefinerMatchResult.of(newException);
      if (current instanceof PsiKeyword && current.textMatches(JavaKeywords.THROW)) {
        PsiElement nextLeaf = PsiTreeUtil.nextVisibleLeaf(current);
        newException = findNewException(nextLeaf);
        if (newException != null) return onTheSameLineFor(current, newException, true);
      }
      return myInfo.matchSpecificExceptionElement(current);
      }

    private @Nullable PsiElement findNewException(PsiElement element) {
      // We look for new Exception() expression rather than throw statement, because stack-trace is filled in exception constructor
      if (element instanceof PsiKeyword && element.textMatches(JavaKeywords.NEW)) {
        PsiNewExpression newExpression = ObjectUtils.tryCast(element.getParent(), PsiNewExpression.class);
        if (newExpression != null) {
          PsiType type = newExpression.getType();
          if (type != null && type.equalsToText(myInfo.getExceptionClassName())) return element;
        }
      }
      return null;
    }

    @Override
    public ExceptionLineRefiner consumeNextLine(String line) {
      ExceptionInfo info = myInfo.consumeStackLine(line);
      if (info != null) {
        return new AfterExceptionRefiner(info);
      }
      return null;
    }

    @Override
    public ExceptionInfo getExceptionInfo() {
      return myInfo;
    }
  }

  public ExceptionInfo consumeStackLine(@NonNls String line) {
    return null;
  }

  private static boolean onTheSameLine(@NotNull Document document, @NotNull PsiElement current, @NotNull PsiElement operand) {
    return document.getLineNumber(current.getTextRange().getStartOffset()) == document.getLineNumber(operand.getTextRange().getStartOffset());
  }

  private static boolean isEmpty(@NotNull PsiElement element) {
    return StringUtil.isEmptyOrSpaces(element.getText());
  }
  static @Nullable ExceptionLineRefiner.RefinerMatchResult onTheSameLineFor(@Nullable PsiElement from,
                                                                            @Nullable PsiElement reason,
                                                                            boolean forward) {
    if (from == null || reason == null) {
      return null;
    }
    if (isEmpty(from)) {
      PsiElement probablyFrom;
      if (forward) {
        probablyFrom = PsiTreeUtil.prevVisibleLeaf(from);
      }
      else {
        probablyFrom = PsiTreeUtil.nextVisibleLeaf(from);
      }
      if (probablyFrom != null) {
        from = probablyFrom;
      }
    }
    PsiFile file = from.getContainingFile();
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) return null;

    PsiElement current = reason;
    while (current != null && current != from) {
      if (onTheSameLine(document, current, from)) {
        return new ExceptionLineRefiner.RefinerMatchResult(current, reason);
      }
      if (forward) {
        current = PsiTreeUtil.prevLeaf(current);
      }
      else {
        current = PsiTreeUtil.nextLeaf(current);
      }
    }
    return new ExceptionLineRefiner.RefinerMatchResult(from, reason);
  }
}
