// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.google.common.collect.Comparators;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class SortContentAction extends PsiElementBaseIntentionAction {
  public static final int MIN_ELEMENTS_COUNT = 3;

  private static final class Holder {
    public static final SortingStrategy[] EXPRESSION_SORTING_STRATEGIES = {
      new StringLiteralSortingStrategy(),
      new IntLiteralSortingStrategy(),
      new EnumConstantSortingStrategy()
    };

    private static final Sortable<?>[] OUR_SORTABLES = new Sortable[]{
      new ArrayInitializerSortable(),
      new VarargSortable(),
      new EnumConstantDeclarationSortable(),
      new AnnotationArraySortable()
    };
    static final CallMatcher COLLECTION_LITERALS = CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_LIST, "of"),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_SET, "of")
    );
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.sort.content");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    for (Sortable<?> sortable : Holder.OUR_SORTABLES) {
      if (sortable.isAvailable(element)) {
        sortable.replaceWithSorted(element);
      }
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    for (Sortable<?> sortable : Holder.OUR_SORTABLES) {
      if (sortable.isAvailable(element)) return true;
    }
    return false;
  }

  private interface SortingStrategy {
    boolean isSuitableEntryElement(@NotNull PsiElement element);

    @NotNull
    Comparator<PsiElement> getComparator();

    /**
     * Additional check to make sure that relationships between elements is suitable for current strategy
     */
    default boolean isSuitableElements(List<? extends PsiElement> elements) {
      return true;
    }
  }


  private static final class StringLiteralSortingStrategy implements SortingStrategy {
    @Override
    public boolean isSuitableEntryElement(@NotNull PsiElement element) {
      return element instanceof PsiExpression && ExpressionUtils.computeConstantExpression((PsiExpression)element) instanceof String;
    }

    @NotNull
    @Override
    public Comparator<PsiElement> getComparator() {
      return Comparator.comparing(element -> (String)ExpressionUtils.computeConstantExpression((PsiExpression)element));
    }
  }

  private static class IntLiteralSortingStrategy implements SortingStrategy {
    @Override
    public boolean isSuitableEntryElement(@NotNull PsiElement element) {
      PsiExpression expression = ObjectUtils.tryCast(element, PsiExpression.class);
      if (expression == null) return false;
      return ExpressionUtils.computeConstantExpression(expression) instanceof Integer;
    }

    @NotNull
    @Override
    public Comparator<PsiElement> getComparator() {
      return Comparator.comparing(element -> (Integer)ExpressionUtils.computeConstantExpression((PsiExpression)element));
    }
  }

  private static class EnumConstantSortingStrategy implements SortingStrategy {
    private static PsiType extractType(@NotNull PsiElement element) {
      PsiExpression expression = ObjectUtils.tryCast(element, PsiExpression.class);
      PsiReferenceExpression referenceExpression = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiReferenceExpression.class);
      if (referenceExpression == null) return null;
      PsiEnumConstant enumConstant = ObjectUtils.tryCast(referenceExpression.resolve(), PsiEnumConstant.class);
      if (enumConstant == null) return null;
      return referenceExpression.getType();
    }

    @Override
    public boolean isSuitableEntryElement(@NotNull PsiElement element) {
      return extractType(element) != null;
    }

    @NotNull
    @Override
    public Comparator<PsiElement> getComparator() {
      return Comparator.comparing(el -> {
        PsiExpression expr = (PsiExpression)el;
        return ((PsiReferenceExpression)Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(expr))).getReferenceName();
      });
    }

    @Override
    public boolean isSuitableElements(@NotNull List<? extends PsiElement> elements) {
      PsiElement first = elements.get(0);
      PsiType firstType = extractType(first);
      if (firstType == null) return false;
      return elements.stream()
                     .map(element -> (PsiExpression)element)
                     .allMatch(expr -> firstType.equals(expr.getType()));
    }
  }

  private static class EnumConstantDeclarationSortingStrategy implements SortingStrategy {
    @Override
    public boolean isSuitableEntryElement(@NotNull PsiElement element) {
      return element instanceof PsiEnumConstant;
    }

    @NotNull
    @Override
    public Comparator<PsiElement> getComparator() {
      return Comparator.comparing(el -> ((PsiEnumConstant)el).getName());
    }

    @Override
    public boolean isSuitableElements(List<? extends PsiElement> elements) {
      Set<PsiEnumConstant> constants = ContainerUtil.map2Set(elements, el -> (PsiEnumConstant)el);
      for (PsiElement element: elements) {
        PsiEnumConstant enumConstant = (PsiEnumConstant)element;
        boolean entriesHaveDependencies = StreamEx.ofTree((PsiElement)enumConstant.getArgumentList(), el -> StreamEx.of(el.getChildren()))
          .select(PsiReferenceExpression.class)
          .anyMatch(ref -> constants.contains(ref.resolve()));
        if(entriesHaveDependencies) return false;
      }
      return true;
    }
  }

  private static final class SortableEntry {
    private final @NotNull PsiElement myElement;
    private final @NotNull List<PsiComment> myBeforeSeparator;
    private final @NotNull List<? extends PsiComment> myAfterSeparator;

    private SortableEntry(@NotNull PsiElement element,
                          @NotNull List<PsiComment> beforeSeparator,
                          @NotNull List<? extends PsiComment> afterSeparator) {
      myElement = element;
      myBeforeSeparator = beforeSeparator;
      myAfterSeparator = afterSeparator;
    }

    /**
     * @return true iff eol required
     */
    boolean generate(StringBuilder sb, boolean isLastInList) {
      sb.append(myElement.getText());

      boolean newLineNeed = generateComments(sb, myBeforeSeparator);
      if (newLineNeed) {
        if (isLastInList && myAfterSeparator.isEmpty()) {
          return true;
        } else {
          sb.append("\n");
        }
      }

      if (!isLastInList) {
        sb.append(",");
      }
      return generateComments(sb, myAfterSeparator);
    }

    private static boolean generateComments(StringBuilder sb, List<? extends PsiComment> comments) {
      boolean newLineNeed = false;
      for (PsiComment element : comments) {
        if (newLineNeed) {
          sb.append('\n');
          newLineNeed = false;
        }
        sb.append(" ");
        sb.append(element.getText());
        if (element.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          newLineNeed = true;
        }
      }
      return newLineNeed;
    }
  }

  private static final class SortableList {
    private final List<SortableEntry> myEntries;
    private final SortingStrategy mySortingStrategy;
    private final LineLayout myLineLayout;
    private final List<? extends PsiElement> myBeforeFirstElements;

    private SortableList(List<SortableEntry> entries,
                         SortingStrategy strategy,
                         LineLayout layout,
                         List<? extends PsiElement> beforeFirstElements) {
      myEntries = entries;
      mySortingStrategy = strategy;
      myLineLayout = layout;
      myBeforeFirstElements = beforeFirstElements;
    }

    /**
     * @return true iff eol required
     */
    boolean generate(StringBuilder sb) {
      for (PsiElement beforeFirstElement : myBeforeFirstElements) {
        sb.append(beforeFirstElement.getText());
      }
      return myLineLayout.generate(sb, myEntries);
    }

    void sort() {
      Comparator<PsiElement> comparator = mySortingStrategy.getComparator();
      myEntries.sort(Comparator.comparing(sortableEntry -> sortableEntry.myElement, comparator));
    }

    PsiElement getLastElement() {
      SortableEntry last = myEntries.get(myEntries.size() - 1);
      List<? extends PsiComment> beforeSeparator = last.myBeforeSeparator;
      if (beforeSeparator.isEmpty()) {
        return last.myElement;
      }
      return beforeSeparator.get(beforeSeparator.size() - 1);
    }
  }

  /**
   * Base class for something that contains sortable list
   *
   * @param <C> context type
   */
  private static abstract class Sortable<C> {
    abstract boolean isEnd(@NotNull PsiElement element);

    abstract SortingStrategy @NotNull [] sortStrategies();

    /**
     * Extract context to use in consequent calls
     * @param origin element at which intention was invoked
     */
    @Nullable
    abstract C getContext(@NotNull PsiElement origin);

    /**
     * @return list of elements that should be used in comparisons
     */
    @NotNull
    abstract List<PsiElement> getElements(@NotNull C context);

    abstract PsiElement getFirst(C context);

    abstract void replaceWithSorted(PsiElement origin);

    @Nullable
    SortableList readEntries(@NotNull C context) {
      SortingStrategy strategy = null;
      PsiElement current = getFirst(context);
      List<PsiElement> beforeFirst = new SmartList<>();
      outer:
      while (current != null && !isEnd(current)) {
        for (SortingStrategy currentStrategy : sortStrategies()) {
          if (currentStrategy.isSuitableEntryElement(current)) {
            strategy = currentStrategy;
            break outer;
          }
        }
        beforeFirst.add(current);
        current = current.getNextSibling();
      }
      if (strategy == null) return null;

      ReadStateMachine sm = new ReadStateMachine(current, strategy, this);
      if (sm.run()) return null;

      List<SortableEntry> entries = sm.mySortableEntries;
      List<PsiElement> entryElements = ContainerUtil.map(entries, e -> e.myElement);
      if (entryElements.size() < MIN_ELEMENTS_COUNT) return null;
      if (!strategy.isSuitableElements(entryElements)) return null;
      // in case when element after last sortable entry is an error element
      // all comments until the end were glued to this entry,
      // so now we need to remove them in order to avoid duplication
      SortableEntry last = entries.get(entries.size() - 1);
      if (last.myElement.getNextSibling() instanceof PsiErrorElement) {
        last.myAfterSeparator.clear();
      }
      return new SortableList(entries, strategy, sm.myLineLayout, beforeFirst);
    }

    boolean isAvailable(@NotNull PsiElement origin) {
      C context = getContext(origin);
      if (context == null) return false;
      List<PsiElement> elements = getElements(context);
      if (elements.size() < MIN_ELEMENTS_COUNT) {
        return false;
      }
      SortingStrategy sortingStrategy = findSortingStrategy(elements);
      if (sortingStrategy == null) return false;
      Comparator<PsiElement> comparator = sortingStrategy.getComparator();
      return sortingStrategy.isSuitableElements(elements) && !Comparators.isInOrder(elements, comparator);
    }

    @Nullable
    private SortingStrategy findSortingStrategy(List<? extends PsiElement> elements) {
      return ContainerUtil.find(sortStrategies(), strategy -> ContainerUtil.and(elements, strategy::isSuitableEntryElement));
    }

    boolean isSeparator(@NotNull PsiElement element) {
      return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.COMMA;
    }

    boolean isError(@NotNull PsiElement element) {
      return element instanceof PsiErrorElement;
    }


    private enum State {
      Element,
      BetweenElementAndSeparator,
      Separator,
      AfterSeparator
    }

    private static class ReadStateMachine {
      private @NotNull State myState = State.Element;
      private @NotNull PsiElement myCurrent;
      private final @NotNull SortingStrategy myStrategy;
      private final @NotNull List<SortableEntry> mySortableEntries = new ArrayList<>();
      private final @NotNull LineLayout myLineLayout = new LineLayout();
      private final @NotNull Sortable<?> mySortable;
      // Entry building
      private List<PsiComment> myBeforeSeparator = new SmartList<>();
      private List<PsiComment> myAfterSeparator = new SmartList<>();
      private PsiElement myEntryElement = null;
      private boolean myHasErrors = false;

      ReadStateMachine(@NotNull PsiElement current,
                              @NotNull SortingStrategy strategy,
                              @NotNull Sortable<?> block) {
        // Expect that current element is
        myCurrent = current;
        myStrategy = strategy;
        mySortable = block;
      }

      /**
       * @return true if error occurred
       */
      boolean run() {
        while (true) {
          if (!nextState()) {
            // Handle last
            if (myEntryElement != null) {
              finishEntry();
            }
            return myHasErrors;
          }
          else if (myHasErrors) return true;
        }
      }

      /**
       * advances state machine to next state
       *
       * @return false if end is reached
       */
      boolean nextState() {
        PsiElement next = myCurrent.getNextSibling();
        boolean isEnd = next == null || mySortable.isEnd(next);
        if (isEnd) {
          // foo("bar", "baz")
          //                 ^ here state == Element, to add last element we should handle it separately
          if (myState == State.Element) {
            myLineLayout.addElementOnLine();
            myEntryElement = myCurrent;
          }
          return false;
        }
        boolean isSeparator = mySortable.isSeparator(next) ||
                              // we assume that user forgot to add separator, so we consider error element as separator
                              ((myState == State.Element || myState == State.BetweenElementAndSeparator) && mySortable.isError(next));
        switch (myState) {
          case Element -> {
            myEntryElement = myCurrent;
            myLineLayout.addElementOnLine();
            if (isSeparator) {
              advance(next, State.Separator);
            }
            else {
              addIntermediateEntryElement(next, myBeforeSeparator);
              advance(next, State.BetweenElementAndSeparator);
            }
          }
          case BetweenElementAndSeparator -> {
            if (isSeparator) {
              advance(next, State.Separator);
            }
            else {
              addIntermediateEntryElement(next, myBeforeSeparator);
              advance(next, State.BetweenElementAndSeparator);
            }
          }
          case Separator, AfterSeparator -> {
            if (myStrategy.isSuitableEntryElement(next)) {
              finishEntry();
              advance(next, State.Element);
            }
            else {
              addIntermediateEntryElement(next, myAfterSeparator);
              advance(next, State.AfterSeparator);
            }
          }
        }
        return true;
      }

      private void finishEntry() {
        if (myEntryElement == null) {
          myHasErrors = true;
          return;
        }
        mySortableEntries.add(new SortableEntry(myEntryElement, myBeforeSeparator, myAfterSeparator));
        myBeforeSeparator = new SmartList<>();
        myAfterSeparator = new SmartList<>();
        myEntryElement = null;
        }

      private void addIntermediateEntryElement(@NotNull PsiElement element, List<? super PsiComment> target) {
        if (element instanceof PsiWhiteSpace) {
          int newLineCount = (int)element.getText().chars().filter(value -> value == '\n').count();
          if (newLineCount != 0) {
            myLineLayout.addBreaks(newLineCount);
          }
          return;
        }
        if (element instanceof PsiComment) {
          target.add((PsiComment)element);
        }
      }

      private void advance(@NotNull PsiElement next, State state) {
        myState = state;
        myCurrent = next;
      }
    }
  }

  /**
   * Class to manage \n placement.
   * It tries to preserve entry count on line as it was before the sort.
   */
  private static final class LineLayout {
    private final IntList myEntryCountOnLines = new IntArrayList();
    private int myCurrent = 0;

    LineLayout() {
      myEntryCountOnLines.add(0);
    }

    void addBreaks(int count) {
      for (int i = 0; i < count; i++) {
        myEntryCountOnLines.add(0);
      }
      myCurrent += count;
    }

    void addElementOnLine() {
      myEntryCountOnLines.set(myCurrent, myEntryCountOnLines.getInt(myCurrent) + 1);
    }

    /**
     * @return true iff eol required
     */
    private boolean generate(StringBuilder sb, List<SortableEntry> entries) {
      int entryIndex = 0;
      int lines = myEntryCountOnLines.size();
      int currentEntryIndex = 0;
      int entryCount = entries.size();
      boolean eolRequired = false;
      for (int rowIndex = 0; rowIndex < lines; rowIndex++) {
        int entryCountOnRow = myEntryCountOnLines.getInt(rowIndex);
        if (entryCountOnRow == 0) {
          sb.append("\n");
          eolRequired = false;
          continue;
        }
        for (int rowPosition = 0; rowPosition < entryCountOnRow; rowPosition++) {
          currentEntryIndex++;
          boolean isLastInRow = rowPosition + 1 == entryCountOnRow && rowIndex + 1 != lines;
          boolean isLastInList = currentEntryIndex == entryCount;
          eolRequired = entries.get(entryIndex).generate(sb, isLastInList);
          if (!isLastInList && (isLastInRow || eolRequired)) {
            sb.append("\n");
            eolRequired = false;
          }
          entryIndex++;
        }
      }
      return eolRequired;
    }
  }

  static abstract class ElementBasedSortable<T extends PsiElement> extends Sortable<ElementBasedSortable.ElementContext<T>> {
    static class ElementContext<T extends PsiElement> {
      private final @NotNull T myElement;

      ElementContext(@NotNull T element) {
        myElement = element;
      }
    }

    /**
     * Generates replacement for elementToSort
     */
    abstract String generateReplacementText(@NotNull SortableList list, T elementToSort);

    @Override
    void replaceWithSorted(PsiElement origin) {
      ElementContext<T> context = getContext(origin);
      if (context == null) return;
      SortableList sortableList = readEntries(context);
      if (sortableList == null) return;
      sortableList.sort();
      T contextElement = context.myElement;
      String replacement = generateReplacementText(sortableList, contextElement);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(origin.getProject());
      contextElement.replace(factory.createExpressionFromText(replacement, contextElement));
    }

    @NotNull
    @Override
    List<PsiElement> getElements(@NotNull ElementContext<T> context) {
      return getElements(context.myElement);
    }

    /**
     * Returns only elements to sort. It may be simpler than iterating over all and creating {@link SortableList}.
     */
    abstract List<PsiElement> getElements(@NotNull T elementToSort);

    /**
     * Return element, which children will be sorted. This element will be replaced with new one.
     */
    @Nullable
    abstract T getElementToSort(@NotNull PsiElement origin);

    @Nullable
    @Override
    ElementContext<T> getContext(@NotNull PsiElement origin) {
      T elementToSort = getElementToSort(origin);
      if (elementToSort == null) return null;
      return new ElementContext<>(elementToSort);
    }

    @Override
    PsiElement getFirst(ElementContext<T> context) {
      return context.myElement.getFirstChild();
    }
  }

  private static class ArrayInitializerSortable extends ElementBasedSortable<PsiArrayInitializerExpression> {
    @Override
    boolean isEnd(@NotNull PsiElement element) {
      return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE;
    }

    @Override
    SortingStrategy @NotNull [] sortStrategies() {
      return Holder.EXPRESSION_SORTING_STRATEGIES;
    }


    @Override
    String generateReplacementText(@NotNull SortableList list, @NotNull PsiArrayInitializerExpression elementToSort) {
      StringBuilder sb = new StringBuilder();
      boolean eolRequired = list.generate(sb);
      if (eolRequired) {
        sb.append("\n");
      }
      sb.append("}");
      return sb.toString();
    }

    @Nullable
    @Override
    PsiArrayInitializerExpression getElementToSort(@NotNull PsiElement origin) {
      return PsiTreeUtil.getParentOfType(origin, PsiArrayInitializerExpression.class);
    }

    @Override
    List<PsiElement> getElements(@NotNull PsiArrayInitializerExpression elementToSort) {
      return Arrays.asList(elementToSort.getInitializers());
    }
  }

  private static class AnnotationArraySortable extends Sortable<PsiArrayInitializerMemberValue> {

    @Override
    boolean isEnd(@NotNull PsiElement element) {
      return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE;
    }

    @Override
    SortingStrategy @NotNull [] sortStrategies() {
      return Holder.EXPRESSION_SORTING_STRATEGIES;
    }

    @Nullable
    @Override
    PsiArrayInitializerMemberValue getContext(@NotNull PsiElement origin) {
      return PsiTreeUtil.getParentOfType(origin, PsiArrayInitializerMemberValue.class);
    }

    @NotNull
    @Override
    List<PsiElement> getElements(@NotNull PsiArrayInitializerMemberValue context) {
      return Arrays.asList(context.getInitializers());
    }

    @Override
    PsiElement getFirst(PsiArrayInitializerMemberValue context) {
      return context.getFirstChild();
    }

    @Override
    void replaceWithSorted(PsiElement origin) {
      PsiArrayInitializerMemberValue context = getContext(origin);
      if (context == null) return;
      SortableList sortableList = readEntries(context);
      if (sortableList == null) return;
      sortableList.sort();
      String replacement = generateReplacementText(sortableList);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(origin.getProject());
      PsiAnnotation annotation = factory.createAnnotationFromText("@Ann(" + replacement + ")", null);
      PsiAnnotationMemberValue replacementElement = annotation.getParameterList().getAttributes()[0].getValue();
      assert replacementElement != null;
      context.replace(replacementElement);
    }

    String generateReplacementText(@NotNull SortableList list) {
      StringBuilder sb = new StringBuilder();
      boolean newLineRequired = list.generate(sb);
      if (newLineRequired) {
        sb.append("\n");
      }
      sb.append("}");
      return sb.toString();
    }
  }

  private static class VarargSortable extends Sortable<VarargSortable.VarargContext> {
    static class VarargContext {
      private final @NotNull PsiExpressionList myExpressionList;
      private final @NotNull List<PsiExpression> myVarargArguments;

      VarargContext(@NotNull PsiExpressionList expressionList, @NotNull List<PsiExpression> varargArguments) {
        myExpressionList = expressionList;
        myVarargArguments = varargArguments;
      }
    }

    @Override
    boolean isEnd(@NotNull PsiElement element) {
      return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RPARENTH;
    }

    @Override
    SortingStrategy @NotNull [] sortStrategies() {
      return Holder.EXPRESSION_SORTING_STRATEGIES;
    }

    @Nullable
    @Override
    VarargContext getContext(@NotNull PsiElement origin) {
      PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(origin, PsiExpressionList.class);
      if (expressionList == null) return null;
      PsiMethodCallExpression call = ObjectUtils.tryCast(expressionList.getParent(), PsiMethodCallExpression.class);
      if (call == null) return null;
      PsiExpression[] arguments = expressionList.getExpressions();
      if (arguments.length < MIN_ELEMENTS_COUNT) return null;
      PsiMethod method = ObjectUtils.tryCast(call.getMethodExpression().resolve(), PsiMethod.class);
      if (method == null) return null;
      PsiParameterList parameterList = method.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
      if (isSuitableVarargLikeCall(call)) {
        return new VarargContext(expressionList, Arrays.asList(call.getArgumentList().getExpressions()));
      }
      if (arguments.length - parameters.length + 1 < MIN_ELEMENTS_COUNT) return null;
      PsiExpression[] varargArguments = getVarargArguments(arguments, origin, parameters);
      if (varargArguments == null) return null;
      return new VarargContext(expressionList, Arrays.asList(varargArguments));
    }

    @NotNull
    @Override
    List<PsiElement> getElements(@NotNull VarargContext context) {
      return new ArrayList<>(context.myVarargArguments);
    }

    @Override
    PsiElement getFirst(VarargContext context) {
      return context.myVarargArguments.get(0);
    }

    private static PsiExpression @Nullable [] getVarargArguments(PsiExpression @NotNull [] arguments,
                                                                 @NotNull PsiElement originElement,
                                                                 PsiParameter @NotNull [] parameters) {
      PsiParameter last = ArrayUtil.getLastElement(parameters);
      if (last == null) return null;
      if (!last.isVarArgs()) return null;
      PsiExpression closestExpression = getTopmostExpression(getClosestExpression(originElement));
      if (closestExpression == null) return null;
      int indexOfCurrent = Arrays.asList(arguments).indexOf(closestExpression);
      if (-1 == indexOfCurrent) return null;
      if (indexOfCurrent < parameters.length - 1) return null;
      if (arguments.length < parameters.length + MIN_ELEMENTS_COUNT - 1) return null;
      return Arrays.copyOfRange(arguments, parameters.length - 1, arguments.length);
    }

    private static boolean isSuitableVarargLikeCall(@NotNull PsiCallExpression call) {
      if (!Holder.COLLECTION_LITERALS.matches(call)) return false;
      PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList == null) return false;
      return argumentList.getExpressionCount() >= MIN_ELEMENTS_COUNT;
    }

    @Nullable
    private static PsiExpression getTopmostExpression(@Nullable final PsiExpression expression) {
      if (expression == null) return null;
      @NotNull PsiExpression current = expression;
      while (true) {
        PsiExpression parentExpr = ObjectUtils.tryCast(current.getParent(), PsiExpression.class);
        if (parentExpr == null) break;
        current = parentExpr;
      }
      return current;
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
    void replaceWithSorted(PsiElement origin) {
      VarargContext context = getContext(origin);
      if (context == null) return;
      SortableList sortableList = readEntries(context);
      if (sortableList == null) return;
      sortableList.sort();
      PsiExpressionList expressionList = context.myExpressionList;
      PsiMethodCallExpression call = ObjectUtils.tryCast(expressionList.getParent(), PsiMethodCallExpression.class);
      if (call == null) return;
      String methodName = call.getMethodExpression().getText();
      if (methodName == null) return;
      StringBuilder sb = new StringBuilder();
      for (PsiElement child : call.getChildren()) {
        if (child == expressionList) break;
        sb.append(child.getText());
      }
      PsiExpression firstVararg = context.myVarargArguments.get(0);
      PsiElement child = expressionList.getFirstChild();
      while(child != firstVararg) {
        sb.append(child.getText());
        child = child.getNextSibling();
      }
      boolean newLineRequired = sortableList.generate(sb);
      if (newLineRequired) {
        sb.append("\n");
      }
      sb.append(")");
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(origin.getProject());
      call.replace(factory.createExpressionFromText(sb.toString(), call));
    }
  }

  private static class EnumConstantDeclarationSortable extends Sortable<EnumConstantDeclarationSortable.EnumContext> {
    static class EnumContext {
      private final @NotNull List<PsiEnumConstant> myEnumConstants;
      private final @NotNull PsiElement myFirst;

      EnumContext(@NotNull List<PsiEnumConstant> enumConstants, @NotNull PsiElement first) {myEnumConstants = enumConstants;
        myFirst = first;
      }
    }

    @Override
    boolean isEnd(@NotNull PsiElement element) {
      if (element instanceof PsiJavaToken) {
        IElementType tokenType = ((PsiJavaToken)element).getTokenType();
        if (tokenType == JavaTokenType.SEMICOLON || tokenType == JavaTokenType.RBRACE) {
          return true;
        }
      }
      return false;
    }

    @Override
    SortingStrategy @NotNull [] sortStrategies() {
      return new SortingStrategy[] {
        new EnumConstantDeclarationSortingStrategy()
      };
    }

    @Nullable
    @Override
    EnumContext getContext(@NotNull PsiElement origin) {
      PsiClass aClass = PsiTreeUtil.getParentOfType(origin, PsiClass.class);
      if (aClass == null) return null;
      if (!aClass.isEnum()) return null;
      PsiEnumConstant[] constants = PsiTreeUtil.getChildrenOfType(aClass, PsiEnumConstant.class);
      if (constants == null || constants.length < MIN_ELEMENTS_COUNT) return null;
      PsiEnumConstant last = constants[constants.length - 1];
      PsiElement lastEnumRelatedElement = getLastEnumDeclarationRelatedElement(last);
      if (lastEnumRelatedElement.getTextRange().getEndOffset() <= origin.getTextOffset()) return null;
      PsiElement lBrace = aClass.getLBrace();
      if (lBrace == null) return null;
      PsiElement nextAfterLbrace = lBrace.getNextSibling();
      if (nextAfterLbrace == null) return null;
      return new EnumContext(Arrays.asList(constants), nextAfterLbrace);
    }

    private static @NotNull PsiElement getLastEnumDeclarationRelatedElement(@NotNull PsiEnumConstant last) {
      PsiElement current = last.getNextSibling();
      while (current instanceof PsiWhiteSpace
             || current instanceof PsiComment
             || (current instanceof PsiJavaToken && (((PsiJavaToken)current).getTokenType() == JavaTokenType.COMMA))
      ) {
        current = current.getNextSibling();
      }
      return current;
    }

    @NotNull
    @Override
    List<PsiElement> getElements(@NotNull EnumContext context) {
      return new ArrayList<>(context.myEnumConstants);
    }

    @Override
    PsiElement getFirst(EnumContext context) {
      return context.myFirst;
    }

    @Override
    void replaceWithSorted(PsiElement origin) {
      EnumContext context = getContext(origin);
      if (context == null) return;
      SortableList sortableList = readEntries(context);
      if (sortableList == null) return;
      PsiElement lastElement = sortableList.getLastElement();
      sortableList.sort();
      PsiClass aClass = PsiTreeUtil.getParentOfType(origin, PsiClass.class);
      if (aClass == null) return;
      String name = aClass.getName();
      if (name == null) return;
      PsiElement lBrace = aClass.getLBrace();
      PsiElement rBrace = aClass.getRBrace();
      if (lBrace == null || rBrace == null) return;

       //PsiEnumConstant holds comments inside, we need codegen to know about this comments to place \n correctly
      for (SortableEntry entry : sortableList.myEntries) {
        List<PsiComment> comments = StreamEx.ofTree(entry.myElement, el -> StreamEx.of(el.getChildren()))
          .select(PsiComment.class)
          .filter(comment -> !(comment instanceof PsiDocComment)).toList();
        for (PsiComment comment : comments) {
          entry.myBeforeSeparator.add((PsiComment)comment.copy());
          comment.delete();
        }
      }
      StringBuilder sb = new StringBuilder();
      if (sortableList.generate(sb)) {
        sb.append("\n");
      }
      PsiElement elementToPreserve = lastElement.getNextSibling();
      while (elementToPreserve != null && elementToPreserve != rBrace) {
        sb.append(elementToPreserve.getText());
        elementToPreserve = elementToPreserve.getNextSibling();
      }
      Project project = aClass.getProject();
      PsiClass newEnum = createEnum(project, sb.toString());
      if (newEnum == null) return;
      PsiElement newClassLBrace = newEnum.getLBrace();
      PsiElement newClassRBrace = newEnum.getRBrace();
      if (newClassLBrace == null || newClassRBrace == null) return;
      aClass.deleteChildRange(lBrace, rBrace);

      // Can't use addRangeAfter: when there are whitespaces with comments semicolon inserted
      StringBuilder finalText = new StringBuilder();
      for(PsiElement current = newClassLBrace.getNextSibling(); current != newClassRBrace; current = current.getNextSibling()) {
        finalText.append(current.getText());
      }
      String prefix = aClass.getText();

      // Workaround of deleting \n between comment after enum and lBrace when after class there are at least one \n
      PsiElement lastChild = aClass.getLastChild();
      if (lastChild instanceof PsiComment && ((PsiComment)lastChild).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT)  {
        prefix += "\n";
      }
      //if (aClass.getLastChild() instanceof PsiComment)
      PsiClass anEnum = createEnum(project, finalText.toString(), prefix);
      if (anEnum != null) {
        aClass.replace(anEnum);
      }
    }

    private static PsiClass createEnum(Project project, String text, String prefix) {
      String enumText = prefix + " {" + text + "}";
      PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(project)
                                                    .createFileFromText("_DUMMY_", JavaFileType.INSTANCE, enumText);
      PsiClass[] classes = file.getClasses();
      if (classes.length != 1) return null;
      return classes[0];
    }

    private static PsiClass createEnum(Project project, String text) {
      return createEnum(project, text, "enum __DUMMY__");
    }
  }
}
