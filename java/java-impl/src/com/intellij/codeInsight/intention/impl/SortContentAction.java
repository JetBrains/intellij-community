// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.google.common.collect.Comparators;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import gnu.trove.TIntArrayList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public class SortContentAction extends PsiElementBaseIntentionAction {
  public static final int MIN_ELEMENTS_COUNT = 3;

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
    for (Sortable<?> sortable : OUR_SORTABLES) {
      if (sortable.isAvailable(element)) {
        sortable.replaceWithSorted(element);
      }
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    for (Sortable<?> sortable : OUR_SORTABLES) {
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
    default boolean isSuitableElements(List<PsiElement> elements) {
      return true;
    }
  }


  private static class StringLiteralSortingStrategy implements SortingStrategy {

    @Override
    public boolean isSuitableEntryElement(@NotNull PsiElement element) {
      PsiLiteralExpression expression = tryCast(element, PsiLiteralExpression.class);
      if (expression == null) return false;
      return ExpressionUtils.computeConstantExpression(expression) instanceof String;
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
      PsiLiteralExpression expression = tryCast(element, PsiLiteralExpression.class);
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
      PsiReferenceExpression expression = tryCast(element, PsiReferenceExpression.class);
      if (expression == null) return null;
      PsiEnumConstant enumConstant = tryCast(expression.resolve(), PsiEnumConstant.class);
      if (enumConstant == null) return null;
      return expression.getType();
    }

    @Override
    public boolean isSuitableEntryElement(@NotNull PsiElement element) {
      return extractType(element) != null;
    }

    @NotNull
    @Override
    public Comparator<PsiElement> getComparator() {
      return Comparator.comparing(el -> ((PsiReferenceExpression)el).getReferenceName());
    }

    @Override
    public boolean isSuitableElements(@NotNull List<PsiElement> elements) {
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
    public boolean isSuitableElements(List<PsiElement> elements) {
      Set<String> names = elements.stream().map(element -> ((PsiEnumConstant)element).getName()).collect(Collectors.toSet());
      for (PsiElement element: elements) {
        PsiEnumConstant enumConstant = (PsiEnumConstant)element;
        if(StreamEx.ofTree((PsiElement)enumConstant.getArgumentList(), el -> StreamEx.of(el.getChildren()))
                .select(PsiReferenceExpression.class)
                .map(ref -> ref.getReferenceName())
                .anyMatch(refName -> names.contains(refName))) return false;
      }
      return true;
    }
  }

  private static class SortableEntry {
    private final @NotNull PsiElement myElement;
    private final @NotNull List<PsiComment> myBeforeSeparator;
    private final @NotNull List<PsiComment> myAfterSeparator;

    private SortableEntry(@NotNull PsiElement element,
                          @NotNull List<PsiComment> beforeSeparator,
                          @NotNull List<PsiComment> afterSeparator) {
      myElement = element;
      myBeforeSeparator = beforeSeparator;
      myAfterSeparator = afterSeparator;
    }

    void generate(StringBuilder sb, boolean isLastInRow, boolean isLastInList) {
      sb.append(myElement.getText());
      handleElementsBeforeSeparator(sb, isLastInList);
      if (!isLastInList) {
        sb.append(",");
      }
      boolean newLineSet = false;
      for (PsiComment comment : myAfterSeparator) {
        sb.append(" ")
          .append(comment.getText());
        if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          sb.append("\n");
          newLineSet = true;
        }
        else {
          newLineSet = false;
        }
      }
      if (isLastInRow && !newLineSet && !isLastInList) {
        sb.append("\n");
      }
    }


    private void handleElementsBeforeSeparator(StringBuilder sb, boolean isLast) {
      boolean newLineNeed = false;
      for (PsiElement element : myBeforeSeparator) {
        if (newLineNeed) {
          sb.append('\n');
          newLineNeed = false;
        }
        sb.append(element.getText());
        if (element instanceof PsiComment && ((PsiComment)element).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          newLineNeed = true;
        }
      }
      if (!isLast && newLineNeed) {
        sb.append('\n');
      }
    }

    SortableEntry copy() {
      List<PsiComment> afterSeparator = myAfterSeparator.stream().map(el -> (PsiComment)el.copy()).collect(Collectors.toList());
      List<PsiComment> beforeSeparator = myBeforeSeparator.stream().map(el -> (PsiComment)el.copy()).collect(Collectors.toList());
      return new SortableEntry(myElement.copy(), beforeSeparator, afterSeparator);
    }
  }

  private static class SortableList {
    private final List<SortableEntry> myEntries;
    private final SortingStrategy mySortingStrategy;
    private final LineLayout myLineLayout;
    private final List<PsiElement> myBeforeFirstElements;

    private SortableList(List<SortableEntry> entries,
                         SortingStrategy strategy,
                         LineLayout layout,
                         List<PsiElement> beforeFirstElements) {
      myEntries = entries;
      mySortingStrategy = strategy;
      myLineLayout = layout;
      myBeforeFirstElements = beforeFirstElements;
    }

    void generate(StringBuilder sb) {
      for (PsiElement beforeFirstElement : myBeforeFirstElements) {
        sb.append(beforeFirstElement.getText());
      }
      myLineLayout.generate(sb, myEntries);
    }

    void sort() {
      Comparator<PsiElement> comparator = mySortingStrategy.getComparator();
      myEntries.sort(Comparator.comparing(sortableEntry -> sortableEntry.myElement, comparator));
    }

    PsiElement getLastElement() {
      SortableEntry last = myEntries.get(myEntries.size() - 1);
      List<PsiComment> beforeSeparator = last.myBeforeSeparator;
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

    @NotNull
    abstract SortingStrategy[] sortStrategies();

    /**
     * Extract context to use in consequent calls
     * @param origin element at which intention was invoked
     */
    @Nullable
    abstract C getContext(@NotNull PsiElement origin);

    /**
     * @return list of elements, that should be used in comparisons
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
      List<PsiElement> entryElements = entries.stream().map(e -> e.myElement).collect(Collectors.toList());
      if (entryElements.size() < MIN_ELEMENTS_COUNT) return null;
      if (!strategy.isSuitableElements(entryElements)) return null;
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
    private SortingStrategy findSortingStrategy(List<PsiElement> elements) {

      return Arrays.stream(sortStrategies())
                   .filter(strategy -> elements.stream().allMatch(strategy::isSuitableEntryElement))
                   .findFirst()
                   .orElse(null);
    }

    boolean isSeparator(@NotNull PsiElement element) {
      return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.COMMA;
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

      public ReadStateMachine(@NotNull PsiElement current,
                              @NotNull SortingStrategy strategy,
                              @NotNull Sortable block) {
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
        boolean isSeparator = mySortable.isSeparator(next);
        switch (myState) {
          case Element:
            myEntryElement = myCurrent;
            myLineLayout.addElementOnLine();
            if (isSeparator) {
              advance(next, State.Separator);
            }
            else {
              addIntermediateEntryElement(next, myBeforeSeparator);
              advance(next, State.BetweenElementAndSeparator);
            }
            break;
          case BetweenElementAndSeparator:
            if (isSeparator) {
              advance(next, State.Separator);
            }
            else {
              addIntermediateEntryElement(next, myBeforeSeparator);
              advance(next, State.BetweenElementAndSeparator);
            }
            break;
          case Separator:
          case AfterSeparator:
            if (myStrategy.isSuitableEntryElement(next)) {
              finishEntry();
              advance(next, State.Element);
            }
            else {
              addIntermediateEntryElement(next, myAfterSeparator);
              advance(next, State.AfterSeparator);
            }
            break;
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

      private void addIntermediateEntryElement(@NotNull PsiElement element, List<PsiComment> target) {
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
   * Class to manage \n placement
   * It tries to preserve entry count on line as it was before sort
   */
  private static class LineLayout {
    private final TIntArrayList myEntryCountOnLines = new TIntArrayList();
    private int myCurrent = 0;

    public LineLayout() {
      myEntryCountOnLines.add(0);
    }

    void addBreaks(int count) {
      for (int i = 0; i < count; i++) {
        myEntryCountOnLines.add(0);
      }
      myCurrent += count;
    }

    void addElementOnLine() {
      myEntryCountOnLines.set(myCurrent, myEntryCountOnLines.get(myCurrent) + 1);
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

  static abstract class ElementBasedSortable<T extends PsiElement> extends Sortable<ElementBasedSortable.ElementContext<T>> {
    static class ElementContext<T extends PsiElement> {
      private final @NotNull T myElement;

      public ElementContext(@NotNull T element) {
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

    @NotNull
    @Override
    SortingStrategy[] sortStrategies() {
      return EXPRESSION_SORTING_STRATEGIES;
    }


    @Override
    String generateReplacementText(@NotNull SortableList list, @NotNull PsiArrayInitializerExpression elementToSort) {
      StringBuilder sb = new StringBuilder();
      list.generate(sb);
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

  private static class AnnotationArraySortable extends ElementBasedSortable<PsiArrayInitializerMemberValue> {
    @Override
    boolean isEnd(@NotNull PsiElement element) {
      return element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE;
    }

    @NotNull
    @Override
    SortingStrategy[] sortStrategies() {
      return EXPRESSION_SORTING_STRATEGIES;
    }


    @Override
    String generateReplacementText(@NotNull SortableList list, @NotNull PsiArrayInitializerMemberValue elementToSort) {
      StringBuilder sb = new StringBuilder();
      list.generate(sb);
      sb.append("}");
      return sb.toString();
    }

    @Nullable
    @Override
    PsiArrayInitializerMemberValue getElementToSort(@NotNull PsiElement origin) {
      return PsiTreeUtil.getParentOfType(origin, PsiArrayInitializerMemberValue.class);
    }

    @Override
    List<PsiElement> getElements(@NotNull PsiArrayInitializerMemberValue elementToSort) {
      return Arrays.asList(elementToSort.getInitializers());
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

    @NotNull
    @Override
    SortingStrategy[] sortStrategies() {
      return EXPRESSION_SORTING_STRATEGIES;
    }

    @Nullable
    @Override
    VarargContext getContext(@NotNull PsiElement origin) {
      PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(origin, PsiExpressionList.class);
      if (expressionList == null) return null;
      PsiMethodCallExpression call = tryCast(expressionList.getParent(), PsiMethodCallExpression.class);
      if (call == null) return null;
      PsiExpression[] arguments = expressionList.getExpressions();
      if (arguments.length < MIN_ELEMENTS_COUNT) return null;
      PsiMethod method = tryCast(call.getMethodExpression().resolve(), PsiMethod.class);
      if (method == null) return null;
      PsiParameterList parameterList = method.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
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
      if (arguments.length < parameters.length + MIN_ELEMENTS_COUNT - 1) return null;
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
    void replaceWithSorted(PsiElement origin) {
      VarargContext context = getContext(origin);
      if (context == null) return;
      SortableList sortableList = readEntries(context);
      if (sortableList == null) return;
      sortableList.sort();
      PsiExpressionList expressionList = context.myExpressionList;
      PsiMethodCallExpression call = tryCast(expressionList.getParent(), PsiMethodCallExpression.class);
      if (call == null) return;
      String methodName = call.getMethodExpression().getText();
      if (methodName == null) return;
      StringBuilder sb = new StringBuilder(methodName);
      PsiExpression firstVararg = context.myVarargArguments.get(0);
      PsiElement child = expressionList.getFirstChild();
      while(child != firstVararg) {
        sb.append(child.getText());
        child = child.getNextSibling();
      }
      sortableList.generate(sb);
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

    @NotNull
    @Override
    SortingStrategy[] sortStrategies() {
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
      PsiElement lBrace = aClass.getLBrace();
      if (lBrace == null) return null;
      PsiElement nextAfterLbrace = lBrace.getNextSibling();
      if (nextAfterLbrace == null) return null;
      return new EnumContext(Arrays.asList(constants), nextAfterLbrace);
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
        List<PsiComment> comments = StreamEx.ofTree(entry.myElement, el -> StreamEx.of(el.getChildren())).select(PsiComment.class).toList();
        for (PsiComment comment : comments) {
          entry.myBeforeSeparator.add((PsiComment)comment.copy());
          comment.delete();
        }
      }
      StringBuilder sb = new StringBuilder();
      sortableList.generate(sb);
      SortableEntry lastItem = ContainerUtil.getLastItem(sortableList.myEntries);
      assert lastItem != null;
      if (!lastItem.myBeforeSeparator.isEmpty()) {
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
      PsiClass anEnum = createEnum(project, finalText.toString(), aClass.getText());
      if (anEnum != null) {
        aClass.replace(anEnum);
      }
    }

    private static PsiClass createEnum(Project project, String text, String prefix) {
      PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(project)
                                                    .createFileFromText("_DUMMY_", JavaFileType.INSTANCE, prefix + " {" + text + "}");
      PsiClass[] classes = file.getClasses();
      if (classes.length != 1) return null;
      return classes[0];
    }

    private static PsiClass createEnum(Project project, String text) {
      return createEnum(project, text, "enum __DUMMY__");
    }
  }
}
