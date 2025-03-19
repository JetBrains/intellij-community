// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import com.intellij.psi.formatter.java.JavaFormatterAnnotationUtil;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Functions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static com.intellij.util.ObjectUtils.tryCast;

public class JavaArrangementVisitor extends JavaRecursiveElementVisitor {

  private static final String NULL_CONTENT = "no content";

  private static final Map<String, ArrangementSettingsToken> MODIFIERS = new HashMap<>();

  static {
    MODIFIERS.put(PsiModifier.PUBLIC, PUBLIC);
    MODIFIERS.put(PsiModifier.PROTECTED, PROTECTED);
    MODIFIERS.put(PsiModifier.PRIVATE, PRIVATE);
    MODIFIERS.put(PsiModifier.PACKAGE_LOCAL, PACKAGE_PRIVATE);
    MODIFIERS.put(PsiModifier.STATIC, STATIC);
    MODIFIERS.put(PsiModifier.FINAL, FINAL);
    MODIFIERS.put(PsiModifier.TRANSIENT, TRANSIENT);
    MODIFIERS.put(PsiModifier.VOLATILE, VOLATILE);
    MODIFIERS.put(PsiModifier.SYNCHRONIZED, SYNCHRONIZED);
    MODIFIERS.put(PsiModifier.ABSTRACT, ABSTRACT);
  }

  @SuppressWarnings({"DialogTitleCapitalization", "HardCodedStringLiteral"}) // not visible in UI
  private static final ArrangementSettingsToken ANON_CLASS_PARAMETER_LIST =
    new ArrangementSettingsToken("Dummy", "not matchable anon class argument list");
  @SuppressWarnings({"DialogTitleCapitalization", "HardCodedStringLiteral"})
  private static final ArrangementSettingsToken ANONYMOUS_CLASS_BODY =
    new ArrangementSettingsToken("Dummy", "not matchable anonymous class body");

  private final @NotNull Stack<JavaElementArrangementEntry> myStack = new Stack<>();
  private final @NotNull JavaArrangementParseInfo myInfo;
  private final @NotNull Collection<? extends TextRange> myRanges;
  private final @NotNull Set<ArrangementSettingsToken> myGroupingRules;
  private final @NotNull MethodBodyProcessor myMethodBodyProcessor;
  private final boolean myCheckDeep;
  private final @NotNull ArrangementSectionDetector mySectionDetector;
  private final @Nullable Document myDocument;

  private final @NotNull HashMap<PsiClass, Set<PsiField>> myCachedClassFields = new HashMap<>();

  private final @NotNull Set<PsiComment> myProcessedSectionsComments = new HashSet<>();

  JavaArrangementVisitor(@NotNull JavaArrangementParseInfo infoHolder,
                         @Nullable Document document,
                         @NotNull Collection<? extends TextRange> ranges,
                         @NotNull ArrangementSettings settings,
                         boolean checkDeep) {
    myInfo = infoHolder;
    myDocument = document;
    myRanges = ranges;
    myGroupingRules = getGroupingRules(settings);

    myMethodBodyProcessor = new MethodBodyProcessor(infoHolder);
    myCheckDeep = checkDeep;
    mySectionDetector = new ArrangementSectionDetector(document, settings, data -> {
      TextRange range = data.getTextRange();
      JavaSectionArrangementEntry entry = new JavaSectionArrangementEntry(getCurrent(), data.getToken(), range, data.getText(), true);
      registerEntry(entry);
    });
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    if (myProcessedSectionsComments.contains(comment)) {
      return;
    }
    mySectionDetector.processComment(comment);
  }

  private static @NotNull Set<ArrangementSettingsToken> getGroupingRules(@NotNull ArrangementSettings settings) {
    Set<ArrangementSettingsToken> groupingRules = new HashSet<>();
    for (ArrangementGroupingRule rule : settings.getGroupings()) {
      groupingRules.add(rule.getGroupingType());
    }
    return groupingRules;
  }

  private void createAndProcessAnonymousClassBodyEntry(@NotNull PsiAnonymousClass aClass) {
    final PsiElement lBrace = aClass.getLBrace();
    final PsiElement rBrace = aClass.getRBrace();

    if (lBrace == null || rBrace == null) {
      return;
    }

    TextRange codeBlockRange = new TextRange(lBrace.getTextRange().getStartOffset(), rBrace.getTextRange().getEndOffset());
    JavaElementArrangementEntry entry = createNewEntry(codeBlockRange, ANONYMOUS_CLASS_BODY, aClass.getName());

    if (entry == null) {
      return;
    }

    processChildrenWithinEntryScope(entry, () -> {
      PsiElement current = lBrace;
      while (current != rBrace) {
        current = current.getNextSibling();
        if (current == null) {
          break;
        }
        current.accept(this);
      }
    });
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    boolean isSectionCommentsDetected = registerSectionComments(aClass);
    TextRange range = isSectionCommentsDetected ? getElementRangeWithoutComments(aClass) : aClass.getTextRange();

    ArrangementSettingsToken type = CLASS;
    if (aClass.isEnum()) {
      type = ENUM;
    }
    else if (aClass.isInterface()) {
      type = INTERFACE;
    }
    JavaElementArrangementEntry entry = createNewEntry(range, type, aClass.getName());
    processEntry(entry, aClass, aClass);
  }

  @Override
  public void visitTypeParameter(@NotNull PsiTypeParameter parameter) {
  }

  @Override
  public void visitAnonymousClass(final @NotNull PsiAnonymousClass aClass) {
    JavaElementArrangementEntry entry = createNewEntry(aClass.getTextRange(), ANONYMOUS_CLASS, aClass.getName());
    if (entry == null) {
      return;
    }
    processChildrenWithinEntryScope(entry, () -> {
      PsiExpressionList list = aClass.getArgumentList();
      if (list != null && list.getTextLength() > 0) {
        JavaElementArrangementEntry listEntry = createNewEntry(list.getTextRange(), ANON_CLASS_PARAMETER_LIST, aClass.getName());
        processEntry(listEntry, null, list);
      }
      createAndProcessAnonymousClassBodyEntry(aClass);
    });
  }

  @Override
  public void visitField(@NotNull PsiField field) {
    boolean isSectionCommentsDetected = registerSectionComments(field);

    // There is a possible case that more than one field is declared for the same type like 'int i, j;'.
    // the case is handled in getFieldWithSiblings().
    PsiElement fieldPrev = getPreviousNonWsComment(field.getPrevSibling(), 0);
    if (PsiUtil.isJavaToken(fieldPrev, JavaTokenType.COMMA)) {
      return;
    }

    // There is a possible case that fields which share the same type declaration are located on different document lines, e.g.:
    //    int i1,
    //        i2;
    // We want to consider only the first declaration then but need to expand its range to all affected lines (up to semicolon).
    TextRange range = isSectionCommentsDetected ? getElementRangeWithoutComments(field) : field.getTextRange();
    PsiElement child = field.getLastChild();
    boolean needSpecialProcessing = true;
    if (isSemicolon(child)) {
      needSpecialProcessing = false;
    }
    else if (child instanceof PsiComment) {
      // There is a possible field definition like below:
      //   int f; // my comment.
      // The comment goes into field PSI here, that's why we need to handle it properly.
      PsiElement prev = getPreviousNonWsComment(child, range.getStartOffset());
      needSpecialProcessing = prev != null && !isSemicolon(prev);
    }

    if (needSpecialProcessing) {
      for (PsiElement e = field.getNextSibling(); e != null; e = e.getNextSibling()) {
        if (e instanceof PsiWhiteSpace || e instanceof PsiComment) { // Skip white space and comment
          continue;
        }
        if (e instanceof PsiJavaToken) {
          if (((PsiJavaToken)e).getTokenType() == JavaTokenType.COMMA) { // Skip comma
            continue;
          }
          else {
            break;
          }
        }
        if (e instanceof PsiField) {
          PsiElement c = e.getLastChild();
          if (c != null) {
            c = getPreviousNonWsComment(c, range.getStartOffset());
          }
          // Stop if current field ends by a semicolon.
          if (c instanceof PsiErrorElement // Incomplete field without trailing semicolon
              || PsiUtil.isJavaToken(c, JavaTokenType.SEMICOLON)) {
            range = TextRange.create(range.getStartOffset(), expandToCommentIfPossible(c));
          }
          else {
            continue;
          }
        }
        break;
      }
    }

    JavaElementArrangementEntry entry = createNewEntry(range, FIELD, field.getName());
    if (entry == null) {
      return;
    }

    processEntry(entry, field, field.getInitializer());
    myInfo.onFieldEntryCreated(field, entry);

    List<PsiField> referencedFields = new ArrayList<>();
    for (PsiField adjacentField : getFieldWithSiblings(field)) {
      referencedFields.addAll(getReferencedFields(adjacentField));
    }
    for (PsiField referencedField : referencedFields) {
      myInfo.registerFieldInitializationDependency(field, referencedField);
    }
  }

  /*
   * Process cases like int a,b,c;
   */
  private static List<PsiField> getFieldWithSiblings(@NotNull PsiField field) {
    List<PsiField> fields = new ArrayList<>();
    fields.add(field);
    PsiElement next = getNextAfterWsOrComment(field);
    while (next != null && PsiUtil.isJavaToken(next, JavaTokenType.COMMA)) {
      next = getNextAfterWsOrComment(next);
      if (next instanceof PsiField) {
        fields.add((PsiField)next);
      }
      else {
        break;
      }
      next = getNextAfterWsOrComment(next);
    }
    return fields;
  }

  private static @Nullable PsiElement getNextAfterWsOrComment(@NotNull PsiElement element) {
    PsiElement next = element.getNextSibling();
    while (next instanceof PsiWhiteSpace || next instanceof PsiComment) {
      next = next.getNextSibling();
    }
    return next;
  }

  private @NotNull List<PsiField> getReferencedFields(final @NotNull PsiField field) {
    final List<PsiField> referencedElements = new ArrayList<>();

    PsiExpression fieldInitializer = field.getInitializer();
    PsiClass containingClass = field.getContainingClass();

    if (fieldInitializer == null || containingClass == null) {
      return referencedElements;
    }

    Set<PsiField> classFields = myCachedClassFields.get(containingClass);
    if (classFields == null) {
      classFields = ContainerUtil.map2Set(containingClass.getFields(), Functions.id());
      myCachedClassFields.put(containingClass, classFields);
    }

    final Set<PsiField> containingClassFields = classFields;
    fieldInitializer.accept(new JavaRecursiveElementVisitor() {
      int myCurrentMethodLookupDepth;
      private static final int MAX_METHOD_LOOKUP_DEPTH = 3;

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        PsiElement ref = expression.resolve();
        if (ref instanceof PsiField && containingClassFields.contains(ref) && hasSameStaticModifier(field, (PsiField)ref)) {
          referencedElements.add((PsiField)ref);
        }
        else if (ref instanceof PsiMethod && myCurrentMethodLookupDepth < MAX_METHOD_LOOKUP_DEPTH) {
          myCurrentMethodLookupDepth++;
          visitMethod((PsiMethod)ref);
          myCurrentMethodLookupDepth--;
        }

        super.visitReferenceExpression(expression);
      }
    });

    return referencedElements;
  }

  private static boolean hasSameStaticModifier(@NotNull PsiField first, @NotNull PsiField second) {
    boolean isSecondFieldStatic = second.hasModifierProperty(PsiModifier.STATIC);
    return first.hasModifierProperty(PsiModifier.STATIC) == isSecondFieldStatic;
  }

  private static @Nullable PsiElement getPreviousNonWsComment(@Nullable PsiElement element, int minOffset) {
    if (element == null) {
      return null;
    }
    for (PsiElement e = element; e != null && e.getTextRange().getStartOffset() >= minOffset; e = e.getPrevSibling()) {
      if (e instanceof PsiWhiteSpace || e instanceof PsiComment) {
        continue;
      }
      return e;
    }
    return null;
  }

  private int expandToCommentIfPossible(@NotNull PsiElement element) {
    if (myDocument == null) {
      return element.getTextRange().getEndOffset();
    }

    CharSequence text = myDocument.getCharsSequence();
    for (PsiElement e = element.getNextSibling(); e != null; e = e.getNextSibling()) {
      if (e instanceof PsiWhiteSpace) {
        if (hasLineBreak(text, e.getTextRange())) {
          return element.getTextRange().getEndOffset();
        }
      }
      else if (e instanceof PsiComment) {
        if (!hasLineBreak(text, e.getTextRange())) {
          return e.getTextRange().getEndOffset();
        }
      }
      else {
        return element.getTextRange().getEndOffset();
      }
    }
    return element.getTextRange().getEndOffset();
  }

  private static boolean hasLineBreak(@NotNull CharSequence text, @NotNull TextRange range) {
    for (int i = range.getStartOffset(), end = range.getEndOffset(); i < end; i++) {
      if (text.charAt(i) == '\n') {
        return true;
      }
    }
    return false;
  }

  private static boolean isSemicolon(@Nullable PsiElement e) {
    return PsiUtil.isJavaToken(e, JavaTokenType.SEMICOLON);
  }

  @Override
  public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
    JavaElementArrangementEntry entry = createNewEntry(initializer.getTextRange(), INIT_BLOCK, null);
    if (entry == null) {
      return;
    }
    parseModifierListOwner(initializer, entry);
  }

  private static @NotNull TextRange getElementRangeWithoutComments(@NotNull PsiElement element) {
    PsiElement[] children = element.getChildren();
    assert children.length > 1 && children[0] instanceof PsiComment;

    int i = 0;
    PsiElement child = children[i];
    while (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
      child = children[++i];
    }

    return new TextRange(child.getTextRange().getStartOffset(), element.getTextRange().getEndOffset());
  }

  private static @NotNull List<PsiComment> getComments(@NotNull PsiElement element) {
    PsiElement[] children = element.getChildren();
    List<PsiComment> comments = new ArrayList<>();

    for (PsiElement e : children) {
      if (e instanceof PsiComment) {
        comments.add((PsiComment)e);
      }
      else if (!(e instanceof PsiWhiteSpace)) {
        return comments;
      }
    }

    return comments;
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    boolean isSectionCommentsDetected = registerSectionComments(method);
    final TextRange range = isSectionCommentsDetected ? getElementRangeWithoutComments(method)
                                                      : method.getTextRange();

    ArrangementSettingsToken type = method.isConstructor() ? CONSTRUCTOR : METHOD;
    JavaElementArrangementEntry entry = createNewEntry(range, type, method.getName());
    if (entry == null) {
      return;
    }

    processEntry(entry, method, myCheckDeep ? method.getBody() : null);
    parseProperties(method, entry);
    myInfo.onMethodEntryCreated(method, entry);
    MethodSignatureBackedByPsiMethod overridden = SuperMethodsSearch.search(method, null, true, false).findFirst();
    if (overridden != null) {
      entry.addModifier(OVERRIDDEN);
      myInfo.onOverriddenMethod(overridden.getMethod(), method);
    }
    boolean reset = myMethodBodyProcessor.setBaseMethod(method);
    try {
      method.accept(myMethodBodyProcessor);
    }
    finally {
      if (reset) {
        myMethodBodyProcessor.setBaseMethod(null);
      }
    }
  }

  private boolean registerSectionComments(@NotNull PsiElement element) {
    List<PsiComment> comments = getComments(element);
    boolean isSectionCommentsDetected = false;
    for (PsiComment comment : comments) {
      if (mySectionDetector.processComment(comment)) {
        isSectionCommentsDetected = true;
        myProcessedSectionsComments.add(comment);
      }
    }
    return isSectionCommentsDetected;
  }

  private void parseProperties(PsiMethod method, JavaElementArrangementEntry entry) {
    String propertyName = null;
    boolean getter = true;
    if (PropertyUtilBase.isSimplePropertyGetter(method)) {
      entry.addModifier(GETTER);
      propertyName = PropertyUtilBase.getPropertyNameByGetter(method);
    }
    else if (PropertyUtilBase.isSimplePropertySetter(method)) {
      entry.addModifier(SETTER);
      propertyName = PropertyUtilBase.getPropertyNameBySetter(method);
      getter = false;
    }

    if (!myGroupingRules.contains(StdArrangementTokens.Grouping.GETTERS_AND_SETTERS) || propertyName == null) {
      return;
    }

    PsiClass containingClass = method.getContainingClass();
    String className = null;
    if (containingClass != null) {
      className = containingClass.getQualifiedName();
    }
    if (className == null) {
      className = NULL_CONTENT;
    }

    if (getter) {
      myInfo.registerGetter(propertyName, className, entry);
    }
    else {
      myInfo.registerSetter(propertyName, className, entry);
    }
  }

  @Override
  public void visitEnumConstant(@NotNull PsiEnumConstant enumConstant) {
  }

  private void processEntry(@Nullable JavaElementArrangementEntry entry,
                            @Nullable PsiModifierListOwner modifier,
                            final @Nullable PsiElement nextPsiRoot) {
    if (entry == null) {
      return;
    }
    if (modifier != null) {
      parseModifierListOwner(modifier, entry);
    }
    if (nextPsiRoot == null) {
      return;
    }
    processChildrenWithinEntryScope(entry, () -> nextPsiRoot.acceptChildren(this));
  }

  private void processChildrenWithinEntryScope(@NotNull JavaElementArrangementEntry entry, @NotNull Runnable childrenProcessing) {
    myStack.push(entry);
    try {
      childrenProcessing.run();
    }
    finally {
      myStack.pop();
    }
  }

  private void registerEntry(@NotNull JavaElementArrangementEntry entry) {
    DefaultArrangementEntry current = getCurrent();
    if (current == null) {
      myInfo.addEntry(entry);
    }
    else {
      current.addChild(entry);
    }
  }

  private @Nullable JavaElementArrangementEntry createNewEntry(@NotNull TextRange range,
                                                               @NotNull ArrangementSettingsToken type,
                                                               @Nullable String name) {
    if (!isWithinBounds(range)) {
      return null;
    }
    DefaultArrangementEntry current = getCurrent();
    JavaElementArrangementEntry entry;
    TextRange expandedRange = myDocument == null ? null : ArrangementUtil.expandToLineIfPossible(range, myDocument);
    TextRange rangeToUse = expandedRange == null ? range : expandedRange;
    entry = new JavaElementArrangementEntry(current, rangeToUse, type, name, true);
    registerEntry(entry);
    return entry;
  }

  private boolean isWithinBounds(@NotNull TextRange range) {
    for (TextRange textRange : myRanges) {
      if (textRange.intersects(range)) {
        return true;
      }
    }
    return false;
  }

  private @Nullable DefaultArrangementEntry getCurrent() {
    return myStack.isEmpty() ? null : myStack.peek();
  }

  private static void parseModifierListOwner(@NotNull PsiModifierListOwner modifierListOwner, @NotNull JavaElementArrangementEntry entry) {
    if (JavaFormatterAnnotationUtil.isFieldWithAnnotations(modifierListOwner)) {
      entry.setHasAnnotation();
    }

    PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (modifierList == null) {
      return;
    }
    for (String modifier : PsiModifier.MODIFIERS) {
      if (modifierList.hasModifierProperty(modifier)) {
        ArrangementSettingsToken arrangementModifier = MODIFIERS.get(modifier);
        if (arrangementModifier != null) {
          entry.addModifier(arrangementModifier);
        }
      }
    }
    if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      entry.addModifier(PACKAGE_PRIVATE);
    }
  }

  // Visitor that search dependencies (calls of other methods, that declared in this class, or method reference usages) for given method
  private static class MethodBodyProcessor extends JavaRecursiveElementVisitor {

    private final @NotNull JavaArrangementParseInfo myInfo;
    private @Nullable PsiMethod myBaseMethod;

    MethodBodyProcessor(@NotNull JavaArrangementParseInfo info) {
      myInfo = info;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression psiMethodCallExpression) {
      PsiReference reference = psiMethodCallExpression.getMethodExpression().getReference();
      if (reference == null) {
        return;
      }
      PsiElement e = reference.resolve();
      if (e instanceof PsiMethod m) {
        assert myBaseMethod != null;
        if (m.getContainingClass() == myBaseMethod.getContainingClass()) {
          myInfo.registerMethodCallDependency(myBaseMethod, m);
        }
      }

      // We process all method call expression children because there is a possible case like below:
      //   new Runnable() {
      //     void test();
      //   }.run();
      // Here we want to process that 'Runnable.run()' implementation.
      super.visitMethodCallExpression(psiMethodCallExpression);
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      PsiMethod method = tryCast(expression.resolve(), PsiMethod.class);
      if (method == null) return;
      assert myBaseMethod != null;
      if (method.getContainingClass() == myBaseMethod.getContainingClass()) {
        myInfo.registerMethodCallDependency(myBaseMethod, method);
      }
    }

    boolean setBaseMethod(@Nullable PsiMethod baseMethod) {
      if (baseMethod == null || myBaseMethod == null /* don't override a base method in case of method-local anonymous classes */) {
        myBaseMethod = baseMethod;
        return true;
      }
      return false;
    }
  }
}

