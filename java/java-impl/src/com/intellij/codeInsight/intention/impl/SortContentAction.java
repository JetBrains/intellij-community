// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import gnu.trove.TIntArrayList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;

public class SortContentAction extends PsiElementBaseIntentionAction {

  private static final ExpressionSortableList<?>[] ourSortableLists = new ExpressionSortableList[]{
    new ArraySortableList(),
    new VarargSortableList()
  };
  public static final int MIN_EXPRESSION_COUNT = 3;

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Sort content";
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    for (ExpressionSortableList<?> list : ourSortableLists) {
      if (list.extract(element) != null) break;
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    for (ExpressionSortableList<?> sortableList : ourSortableLists) {
      if (sortableList.isAvailable(element)) return true;
    }
    return false;
  }

  private static <T> boolean isOrdered(@NotNull T[] array, @NotNull Comparator<T> comparator) {
    for (int i = 0; i < array.length - 1; i++) {
      if (comparator.compare(array[i], array[i + 1]) > 0) {
        return false;
      }
    }
    return true;
  }

  // If this method called, that's means that all elements have the same type as argument
  @Contract("null -> null")
  @Nullable
  private static Comparator<PsiExpression> getComparator(@Nullable PsiType type) {
    if (type == null) return null;
    if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return Comparator.comparing(o -> (String)ExpressionUtils.computeConstantExpression(o));
    }
    if (isNumericType(type)) {
      return Comparator.comparingLong(o -> ((Number)Objects.requireNonNull(ExpressionUtils.computeConstantExpression(o))).longValue());
    }
    if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ENUM)) {
      return Comparator.comparing(expr -> ((PsiReferenceExpression)expr).getReferenceName());
    }
    return null;
  }

  private static boolean isNumericType(@NotNull PsiType type) {
    return type.equals(PsiType.INT) ||
           type.equals(PsiType.LONG) ||
           type.equals(PsiType.SHORT) ||
           type.equals(PsiType.BYTE);
  }

  /**
   * Base class for something that can be sorted and contains as elements expressions
   * @param <C> context class that will be available after extraction
   */
  private abstract static class ExpressionSortableList<C> {

    /**
     * Extracts context that can be accessed later during extraction
     * If context extracted action will be available
     * @param originElement the element on which this action was called
     * @return context object
     */
    @Nullable
    protected abstract C extractContext(@NotNull PsiElement originElement);

    /**
     * @param context context object
     * @return first element to start extracting from
     */
    @Nullable
    protected abstract PsiElement getFirst(@NotNull C context);

    /**
     * @param current element to decide if it is last
     * @return true if and only if end is reached and no expressions expected any more
     */
    protected abstract boolean isLast(@NotNull PsiElement current);

    /**
     * Replace element with expressions with sorted list
     * @param listContext list of expressions to be sorted
     * @param context context object
     * @return element with sorted expressions
     */
    @Nullable
    protected abstract PsiElement replace(@NotNull EntryListContext listContext,
                                          @NotNull PsiElementFactory factory,
                                          @NotNull C context);

    public PsiElement extract(@NotNull PsiElement element) {
      C context = extractContext(element);
      if (context == null) return null;
      EntryListContext listContext = EntryListContext.from(getFirst(context),
                                                           SortContentAction::isSortableExpression,
                                                           SortContentAction::isSeparator,
                                                           this::isLast);
      if (listContext == null) return null;
      listContext.sortContent();
      return replace(listContext, JavaPsiFacade.getElementFactory(element.getProject()), context);
    }

    private boolean isAvailable(@NotNull PsiElement element) {
      return extractContext(element) != null;
    }
  }

  private static boolean isSortableExpression(@NotNull PsiExpression current) {
    return ExpressionUtils.isEvaluatedAtCompileTime(current) || isEnumConstant(current);
  }

  private static boolean isSeparator(@NotNull PsiElement current) {
    return current instanceof PsiJavaToken && ((PsiJavaToken)current).getTokenType() == JavaTokenType.COMMA;
  }

  @Contract("null -> false")
  private static boolean isEnumConstant(PsiExpression current) {
    return current instanceof PsiReferenceExpression && ((PsiReferenceExpression)current).resolve() instanceof PsiEnumConstant;
  }

  private static boolean isSortableEnums(@NotNull StreamEx<PsiExpression> expressions, @NotNull PsiType expectedType) {
    return expressions.allMatch(current -> expectedType.equals(current.getType()) && isEnumConstant(current));
  }

  private static boolean isSortableConstants(@NotNull StreamEx<PsiExpression> expressions, @NotNull PsiType expectedType) {
    return expressions
      .allMatch(current -> expectedType.equals(current.getType())
                           && ExpressionUtils.computeConstantExpression(current) != null
                           && current instanceof PsiLiteralExpression
      );
  }

  private static boolean isSortableExpressions(@NotNull PsiExpression[] expressions, @NotNull PsiType expectedType) {
    return isSortableConstants(StreamEx.of(expressions), expectedType) || isSortableEnums(StreamEx.of(expressions), expectedType);
  }

  private static class ArraySortableList extends ExpressionSortableList<PsiArrayInitializerExpression> {

    @Nullable
    @Override
    protected PsiArrayInitializerExpression extractContext(@NotNull PsiElement originElement) {
      PsiArrayInitializerExpression initializerExpression = PsiTreeUtil.getParentOfType(originElement, PsiArrayInitializerExpression.class);
      if (initializerExpression == null) return null;
      PsiExpression[] initializers = initializerExpression.getInitializers();
      if (initializers.length < MIN_EXPRESSION_COUNT) return null;
      PsiType type = initializerExpression.getInitializers()[0].getType();
      if (type == null) return null;
      if (!isSortableExpressions(initializers, type)) return null;
      Comparator<PsiExpression> comparator = getComparator(type);
      if (comparator == null) return null;
      if (isOrdered(initializers, comparator)) return null;
      return initializerExpression;
    }

    @Override
    public PsiElement getFirst(@NotNull PsiArrayInitializerExpression context) {
      return context.getChildren()[1];
    }

    @Override
    public PsiElement replace(@NotNull EntryListContext listContext,
                              @NotNull PsiElementFactory factory,
                              @NotNull PsiArrayInitializerExpression toReplace) {
      return toReplace.replace(factory.createExpressionFromText("{" + listContext.generate() + "}", toReplace));
    }

    @Override
    public boolean isLast(@NotNull PsiElement current) {
      return current instanceof PsiJavaToken &&
             ((PsiJavaToken)current).getTokenType() == JavaTokenType.RBRACE;
    }
  }

  private static class VarargSortableList extends ExpressionSortableList<VarargSortableList.VarargContext> {

    static class VarargContext {
      private final @NotNull PsiExpressionList myExpressionList;
      private final @NotNull PsiExpression[] myArguments;

      private VarargContext(@NotNull PsiExpressionList expressionList, @NotNull PsiExpression[] arguments) {
        myExpressionList = expressionList;
        myArguments = arguments;
      }
    }

    @Nullable
    @Override
    protected VarargContext extractContext(@NotNull PsiElement originElement) {
      PsiExpressionList list = PsiTreeUtil.getParentOfType(originElement, PsiExpressionList.class);
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(list, PsiMethodCallExpression.class);
      if (call == null) return null;
      PsiExpression[] arguments = call.getArgumentList().getExpressions();
      if (arguments.length < MIN_EXPRESSION_COUNT) return null;
      PsiMethod method = tryCast(call.getMethodExpression().resolve(), PsiMethod.class);
      if (method == null) return null;
      PsiParameterList parameterList = method.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
      PsiExpression[] varargArguments = getVarargArguments(arguments, originElement, parameters);
      if(varargArguments == null) return null;
      PsiExpression argument = varargArguments[0];
      PsiType type = argument.getType();
      if(type == null) return null;
      if (!isSortableExpressions(varargArguments, type)) return null;
      Comparator<PsiExpression> comparator = getComparator(type);
      if (comparator == null) return null;
      if(isOrdered(varargArguments, comparator)) return null;
      return new VarargContext(list, varargArguments);
    }

    @Nullable
    private static PsiExpression[] getVarargArguments(@NotNull PsiExpression[] arguments,
                                                      @NotNull PsiElement originElement,
                                                      @NotNull PsiParameter[] parameters) {
      PsiParameter last = ArrayUtil.getLastElement(parameters);
      if (last == null) return null;
      if (!last.isVarArgs()) return null;
      PsiExpression closestExpression = getClosestExpression(originElement);
      if (closestExpression == null) return null;
      int indexOfCurrent = Arrays.asList(arguments).indexOf(closestExpression);
      if (-1 == indexOfCurrent) return null;
      if (indexOfCurrent < parameters.length - 1) return null;
      if (arguments.length < parameters.length + MIN_EXPRESSION_COUNT - 1) return null;
      return Arrays.copyOfRange(arguments, parameters.length - 1, arguments.length);
    }

    @Nullable
    private static PsiExpression getClosestExpression(@NotNull PsiElement element) {
      while (element != null) {
        if (element instanceof PsiWhiteSpace) {
          element = element.getNextSibling();
          continue;
        }
        if (element instanceof PsiJavaToken) {
          IElementType tokenType = ((PsiJavaToken)element).getTokenType();
          if (!(tokenType.equals(JavaTokenType.COMMA) || tokenType.equals(TokenType.WHITE_SPACE))) {
            break;
          }
          element = element.getNextSibling();
          continue;
        }
        if (!(element instanceof PsiComment)) break;
        element = element.getNextSibling();
      }
      return PsiTreeUtil.getParentOfType(element, PsiExpression.class, false);
    }

    @Override
    protected PsiElement getFirst(@NotNull VarargContext context) {
      return context.myArguments[0];
    }

    @Override
    protected boolean isLast(@NotNull PsiElement current) {
      return current instanceof PsiJavaToken &&
                          ((PsiJavaToken)current).getTokenType() == JavaTokenType.RPARENTH;
    }

    @Nullable
    @Override
    protected PsiElement replace(@NotNull EntryListContext listContext,
                                 @NotNull PsiElementFactory factory,
                                 @NotNull VarargContext context) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(context.myExpressionList, PsiMethodCallExpression.class);
      if (call == null) return null;
      PsiExpression[] arguments = call.getArgumentList().getExpressions();
      PsiMethod method = tryCast(call.getMethodExpression().resolve(), PsiMethod.class);
      if (method == null) return null;
      int parametersCount = method.getParameters().length;
      PsiExpression last = arguments[parametersCount - 1];
      PsiElement current = context.myExpressionList.getChildren()[0];
      String methodText = call.getMethodExpression().getText();
      StringBuilder sb = new StringBuilder();
      sb.append(methodText);
      while (current != null && current != last) {
        sb.append(current.getText());
        current = current.getNextSibling();
      }
      sb.append(listContext.generate());
      sb.append(")");
      return call.replace(factory.createExpressionFromText(sb.toString(), call));
    }
  }


  /**
   * Class to manage \n placement
   * It tries to preserve entry count on line as it was before sort
   */
  private static class LineLayout {
    private TIntArrayList myEntryCountOnLines;

    public LineLayout(TIntArrayList entryCountOnLines) {
      myEntryCountOnLines = entryCountOnLines;
    }

    @NotNull
    static LineLayout from(final PsiElement startingElement, Predicate<PsiElement> endPredicate) {
      PsiElement current = startingElement;
      TIntArrayList entryCountOnLines = new TIntArrayList();
      int currentEntryCount = 0;
      while (!(current instanceof PsiExpression)) {
        current = current.getNextSibling();
      }
      while (current != null && !endPredicate.test(current)) {
        if (current instanceof PsiExpression) {
          currentEntryCount++;
        }
        if (current instanceof PsiWhiteSpace) {
          int newLineCount = (int)current.getText().chars().filter(value -> value == '\n').count();
          for (int i = 0; i < newLineCount; i++) {
            entryCountOnLines.add(currentEntryCount);
            currentEntryCount = 0;
          }
        }
        current = current.getNextSibling();
      }
      entryCountOnLines.add(currentEntryCount);
      return new LineLayout(entryCountOnLines);
    }

    private void generate(StringBuilder sb, List<SortableEntry> entries) {
      int entryIndex = 0;
      int lines = myEntryCountOnLines.size();
      int currentEntryIndex = 0;
      int entryCount = entries.size();
      for (int rowIndex = 0; rowIndex < lines; rowIndex++) {
        int entryCountOnRow = myEntryCountOnLines.get(rowIndex);
        if (entryCountOnRow == 0) {
          sb.append("\n");
          continue;
        }
        for (int rowPosition = 0; rowPosition < entryCountOnRow; rowPosition++) {
          currentEntryIndex++;
          boolean isLastInRow = rowPosition + 1 == entryCountOnRow && rowIndex + 1 != lines;
          entries.get(entryIndex).generate(sb, isLastInRow, currentEntryIndex == entryCount);
          entryIndex++;
        }
      }
    }
  }

  private static class EntryListContext {
    private final @NotNull List<PsiElement> myBeforeFirst;
    private final @NotNull List<SortableEntry> myEntries;
    private final @NotNull LineLayout myLineLayout;

    private EntryListContext(@NotNull List<PsiElement> first,
                             @NotNull List<SortableEntry> entries,
                             @NotNull LineLayout layout) {
      myBeforeFirst = first;
      myEntries = entries;
      myLineLayout = layout;
    }

    @Nullable("when failed to extract")
    static EntryListContext from(final PsiElement startingElement, // first element after {
                                 Predicate<PsiExpression> expressionPredicate,
                                 Predicate<PsiElement> separatorPredicate,
                                 Predicate<PsiElement> endPredicate) {
      List<PsiElement> beforeFirst = new ArrayList<>();
      PsiElement current = startingElement;
      while (current != null && !testWhenExpression(expressionPredicate, current)) {
        beforeFirst.add(current);
        current = current.getNextSibling();
      }
      List<SortableEntry> entries = extractEntries(current, expressionPredicate, separatorPredicate, endPredicate);
      if (entries == null || entries.size() < MIN_EXPRESSION_COUNT) return null;
      return new EntryListContext(beforeFirst, entries, LineLayout.from(startingElement, endPredicate));
    }

    @Contract("_, null -> false")
    private static boolean testWhenExpression(Predicate<PsiExpression> expressionPredicate, PsiElement current) {
      return current instanceof PsiExpression && expressionPredicate.test((PsiExpression)current);
    }

    private void sortContent() {
      PsiExpression exampleExpression = myEntries.get(0).myExpression;
      Comparator<SortableEntry> comparator = SortableEntry.getEntryComparator(exampleExpression);
      if (comparator == null) return;
      Collections.sort(myEntries, comparator);
    }


    @Nullable("when failed to extract")
    private static List<SortableEntry> extractEntries(PsiElement startingElement,
                                                      Predicate<PsiExpression> expressionPredicate,
                                                      Predicate<PsiElement> separatorPredicate,
                                                      Predicate<PsiElement> endPredicate) {
      PsiElement current = startingElement;
      List<SortableEntry> entries = new ArrayList<>();
      while (current != null) {
        if (!testWhenExpression(expressionPredicate, current)) return null;
        PsiExpression expression = (PsiExpression)current;
        current = current.getNextSibling();
        List<PsiComment> beforeSeparator = new ArrayList<>();
        while (current != null) {
          if (separatorPredicate.test(current)) {
            current = current.getNextSibling();
            break;
          }
          if (endPredicate.test(current)) {
            entries.add(new SortableEntry(expression, beforeSeparator, new ArrayList<>()));
            return entries;
          }
          if (current instanceof PsiComment) {
            beforeSeparator.add((PsiComment)current);
          }
          current = current.getNextSibling();
        }
        List<PsiComment> afterSeparator = new ArrayList<>();
        while (current != null && !testWhenExpression(expressionPredicate, current)) {
          if (endPredicate.test(current)) break;
          if (current instanceof PsiComment) {
            afterSeparator.add((PsiComment)current);
          }
          current = current.getNextSibling();
        }
        entries.add(new SortableEntry(expression, beforeSeparator, afterSeparator));
        if (endPredicate.test(current)) return entries;
      }
      return null;
    }

    @NotNull
    private String generate() {
      StringBuilder sb = new StringBuilder();
      for (PsiElement element : myBeforeFirst) {
        sb.append(element.getText());
      }
      myLineLayout.generate(sb, myEntries);
      return sb.toString();
    }
  }

  private static class SortableEntry {
    private final @NotNull PsiExpression myExpression;
    private final @NotNull List<PsiComment> myBeforeSeparator;
    private final @NotNull List<PsiComment> myAfterSeparator;

    private SortableEntry(@NotNull PsiExpression expression,
                          @NotNull List<PsiComment> beforeSeparator,
                          @NotNull List<PsiComment> afterSeparator) {
      myExpression = expression;
      myBeforeSeparator = beforeSeparator;
      myAfterSeparator = afterSeparator;
    }

    @Nullable
    private static Comparator<SortableEntry> getEntryComparator(@NotNull PsiExpression exampleExpression) {
      if (exampleExpression instanceof PsiReferenceExpression) {
        if (!(((PsiReferenceExpression)exampleExpression).resolve() instanceof PsiEnumConstant)) return null;
        return Comparator
          .comparing(entry -> ((PsiReferenceExpression)entry.myExpression).getReferenceName());
      }
      PsiType type = exampleExpression.getType();
      Comparator<PsiExpression> comparator = getComparator(type);
      if (comparator == null) return null;
      return Comparator.comparing(entry -> entry.myExpression, comparator);
    }

    void generate(StringBuilder sb, boolean isLastInRow, boolean isLast) {
      sb.append(myExpression.getText());
      for (PsiElement element : myBeforeSeparator) {
        sb.append(element.getText());
      }
      if (!isLast) {
        sb.append(",");
      }
      boolean newLineSet = false;
      for (PsiComment comment : myAfterSeparator) {
        sb.append(" ")
          .append(comment.getText());
        if (comment.getText().contains("//")) {
          sb.append("\n");
          newLineSet = true;
        }
        else {
          newLineSet = false;
        }
      }
      if (isLastInRow && !newLineSet && !isLast) {
        sb.append("\n");
      }
    }
  }
}
