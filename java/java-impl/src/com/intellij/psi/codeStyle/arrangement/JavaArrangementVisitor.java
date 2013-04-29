/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;

public class JavaArrangementVisitor extends JavaElementVisitor {

  private static final String NULL_CONTENT = "no content";

  private static final Map<String, ArrangementSettingsToken> MODIFIERS = ContainerUtilRt.newHashMap();

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

  @NotNull private final Stack<JavaElementArrangementEntry>           myStack   = new Stack<JavaElementArrangementEntry>();
  @NotNull private final Map<PsiElement, JavaElementArrangementEntry> myEntries = new HashMap<PsiElement, JavaElementArrangementEntry>();

  @NotNull private final  JavaArrangementParseInfo      myInfo;
  @NotNull private final  Collection<TextRange>         myRanges;
  @NotNull private final  Set<ArrangementSettingsToken> myGroupingRules;
  @NotNull private final  MethodBodyProcessor           myMethodBodyProcessor;
  @Nullable private final Document                      myDocument;

  public JavaArrangementVisitor(@NotNull JavaArrangementParseInfo infoHolder,
                                @Nullable Document document,
                                @NotNull Collection<TextRange> ranges,
                                @NotNull Set<ArrangementSettingsToken> groupingRules)
  {
    myInfo = infoHolder;
    myDocument = document;
    myRanges = ranges;
    myGroupingRules = groupingRules;
    myMethodBodyProcessor = new MethodBodyProcessor(infoHolder);
  }

  @Override
  public void visitClass(PsiClass aClass) {
    ArrangementSettingsToken type = CLASS;
    if (aClass.isEnum()) {
      type = ENUM;
    }
    else if (aClass.isInterface()) {
      type = INTERFACE;
    }
    JavaElementArrangementEntry entry = createNewEntry(aClass, aClass.getTextRange(), type, aClass.getName(), true);
    processEntry(entry, aClass, aClass);
  }

  @Override
  public void visitAnonymousClass(PsiAnonymousClass aClass) {
    JavaElementArrangementEntry entry = createNewEntry(
      aClass, aClass.getTextRange(), ANONYMOUS_CLASS, aClass.getName(), false
    );
    processEntry(entry, null, aClass);
  }

  @Override
  public void visitJavaFile(PsiJavaFile file) {
    for (PsiClass psiClass : file.getClasses()) {
      visitClass(psiClass);
    }
  }

  @Override
  public void visitField(PsiField field) {
    // There is a possible case that more than one field is declared for the same type like 'int i, j;'. We want to process only
    // the first one then.
    PsiElement fieldPrev = getPreviousNonWsComment(field.getPrevSibling(), 0);
    if (fieldPrev instanceof PsiJavaToken && ((PsiJavaToken)fieldPrev).getTokenType() == JavaTokenType.COMMA) {
      return;
    }

    // There is a possible case that fields which share the same type declaration are located on different document lines, e.g.:
    //    int i1,
    //        i2;
    // We want to consider only the first declaration then but need to expand its range to all affected lines (up to semicolon).
    TextRange range = field.getTextRange();
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
        else if (e instanceof PsiJavaToken) {
          if (((PsiJavaToken)e).getTokenType() == JavaTokenType.COMMA) { // Skip comma
            continue;
          }
          else {
            break;
          }
        }
        else if (e instanceof PsiField) {
          PsiElement c = e.getLastChild();
          if (c != null) {
            c = getPreviousNonWsComment(c, range.getStartOffset());
          }
          // Stop if current field ends by a semicolon.
          if (c instanceof PsiErrorElement // Incomplete field without trailing semicolon
              || (c instanceof PsiJavaToken && ((PsiJavaToken)c).getTokenType() == JavaTokenType.SEMICOLON))
          {
            range = TextRange.create(range.getStartOffset(), expandToCommentIfPossible(c));
          }
          else {
            continue;
          }
        }
        break;
      }
    }
    JavaElementArrangementEntry entry = createNewEntry(field, range, FIELD, field.getName(), true);
    processEntry(entry, field, field.getInitializer());
  }

  @Nullable
  private static PsiElement getPreviousNonWsComment(@Nullable PsiElement element, int minOffset) {
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
    return e instanceof PsiJavaToken && ((PsiJavaToken)e).getTokenType() == JavaTokenType.SEMICOLON;
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    JavaElementArrangementEntry entry = createNewEntry(initializer, initializer.getTextRange(), FIELD, null, true);
    if (entry == null) {
      return;
    }

    PsiElement classLBrace = null;
    PsiClass clazz = initializer.getContainingClass();
    if (clazz != null) {
      classLBrace = clazz.getLBrace();
    }
    for (PsiElement e = initializer.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      JavaElementArrangementEntry prevEntry;
      if (e == classLBrace) {
        prevEntry = myEntries.get(clazz);
      }
      else {
        prevEntry = myEntries.get(e);
      }
      if (prevEntry != null) {
        entry.addDependency(prevEntry);
      }
      if (!(e instanceof PsiWhiteSpace)) {
        break;
      }
    }
  }

  @Override
  public void visitMethod(PsiMethod method) {
    ArrangementSettingsToken type = method.isConstructor() ? CONSTRUCTOR : METHOD;
    JavaElementArrangementEntry entry = createNewEntry(method, method.getTextRange(), type, method.getName(), true);
    if (entry == null) {
      return;
    }
    
    processEntry(entry, method, method.getBody());
    parseProperties(method, entry);
    myInfo.onMethodEntryCreated(method, entry);
    MethodSignatureBackedByPsiMethod overridden = SuperMethodsSearch.search(method, null, true, false).findFirst();
    if (overridden != null) {
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

  private void parseProperties(PsiMethod method, JavaElementArrangementEntry entry) {
    if (!myGroupingRules.contains(StdArrangementTokens.Grouping.GETTERS_AND_SETTERS)) {
      return;
    }

    String propertyName = null;
    boolean getter = true;
    if (PropertyUtil.isSimplePropertyGetter(method)) {
      propertyName = PropertyUtil.getPropertyNameByGetter(method);
    }
    else if (PropertyUtil.isSimplePropertySetter(method)) {
      propertyName = PropertyUtil.getPropertyNameBySetter(method);
      getter = false;
    }

    if (propertyName == null) {
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
  public void visitExpressionStatement(PsiExpressionStatement statement) {
    statement.getExpression().acceptChildren(this);
  }

  @Override
  public void visitNewExpression(PsiNewExpression expression) {
    PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
    if (anonymousClass == null) {
      return;
    }
    JavaElementArrangementEntry entry =
      createNewEntry(anonymousClass, anonymousClass.getTextRange(), CLASS, anonymousClass.getName(), false);
    processEntry(entry, null, anonymousClass);
  }

  @Override
  public void visitExpressionList(PsiExpressionList list) {
    for (PsiExpression expression : list.getExpressions()) {
      expression.acceptChildren(this);
    }
  }

  @Override
  public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    for (PsiElement element : statement.getDeclaredElements()) {
      element.acceptChildren(this);
    }
  }

  private void processEntry(@Nullable JavaElementArrangementEntry entry,
                            @Nullable PsiModifierListOwner modifier,
                            @Nullable PsiElement nextPsiRoot)
  {
    if (entry == null) {
      return;
    }
    if (modifier != null) {
      parseModifiers(modifier.getModifierList(), entry);
    }
    if (nextPsiRoot == null) {
      return;
    }
    myStack.push(entry);
    try {
      nextPsiRoot.acceptChildren(this);
    }
    finally {
      myStack.pop();
    }
  }
  
  @Nullable
  private JavaElementArrangementEntry createNewEntry(@NotNull PsiElement element,
                                                     @NotNull TextRange range,
                                                     @NotNull ArrangementSettingsToken type,
                                                     @Nullable String name,
                                                     boolean canArrange)
  {
    if (!isWithinBounds(range)) {
      return null;
    }
    DefaultArrangementEntry current = getCurrent();
    JavaElementArrangementEntry entry;
    if (canArrange) {
      TextRange expandedRange = myDocument == null ? null : ArrangementUtil.expandToLineIfPossible(range, myDocument);
      TextRange rangeToUse = expandedRange == null ? range : expandedRange;
      entry = new JavaElementArrangementEntry(current, rangeToUse, type, name, true);
    }
    else {
      entry = new JavaElementArrangementEntry(current, range, type, name, false);
    }
    myEntries.put(element, entry);
    if (current == null) {
      myInfo.addEntry(entry);
    }
    else {
      current.addChild(entry);
    }
    
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

  @Nullable
  private DefaultArrangementEntry getCurrent() {
    return myStack.isEmpty() ? null : myStack.peek();
  }

  @SuppressWarnings("MagicConstant")
  private static void parseModifiers(@Nullable PsiModifierList modifierList, @NotNull JavaElementArrangementEntry entry) {
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
  
  private static class MethodBodyProcessor extends JavaRecursiveElementVisitor {
    
    @NotNull private final JavaArrangementParseInfo myInfo;
    @Nullable private PsiMethod myBaseMethod;

    MethodBodyProcessor(@NotNull JavaArrangementParseInfo info) {
      myInfo = info;
    }

    public void visitMethodCallExpression(PsiMethodCallExpression psiMethodCallExpression) {
      PsiReference reference = psiMethodCallExpression.getMethodExpression().getReference();
      if (reference == null) {
        return;
      }
      PsiElement e = reference.resolve();
      if (e instanceof PsiMethod) {
        assert myBaseMethod != null;
        PsiMethod m = (PsiMethod)e;
        if (m.getContainingClass() == myBaseMethod.getContainingClass()) {
          myInfo.registerDependency(myBaseMethod, m);
        }
      }
      
      // We process all method call expression children because there is a possible case like below:
      //   new Runnable() {
      //     void test();
      //   }.run();
      // Here we want to process that 'Runnable.run()' implementation.
      super.visitMethodCallExpression(psiMethodCallExpression);
    }

    public boolean setBaseMethod(@Nullable PsiMethod baseMethod) {
      if (baseMethod == null || myBaseMethod == null /* don't override a base method in case of method-local anonymous classes */) {
        myBaseMethod = baseMethod;
        return true;
      }
      return false;
    }
  }
}

