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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaArrangementVisitor extends JavaElementVisitor {

  private static final Map<String, ArrangementModifier> MODIFIERS = new HashMap<String, ArrangementModifier>();
  static {
    MODIFIERS.put(PsiModifier.PUBLIC, ArrangementModifier.PUBLIC);
    MODIFIERS.put(PsiModifier.PROTECTED, ArrangementModifier.PROTECTED);
    MODIFIERS.put(PsiModifier.PRIVATE, ArrangementModifier.PRIVATE);
    MODIFIERS.put(PsiModifier.PACKAGE_LOCAL, ArrangementModifier.PACKAGE_PRIVATE);
    MODIFIERS.put(PsiModifier.STATIC, ArrangementModifier.STATIC);
    MODIFIERS.put(PsiModifier.FINAL, ArrangementModifier.FINAL);
    MODIFIERS.put(PsiModifier.TRANSIENT, ArrangementModifier.TRANSIENT);
    MODIFIERS.put(PsiModifier.VOLATILE, ArrangementModifier.VOLATILE);
    MODIFIERS.put(PsiModifier.SYNCHRONIZED, ArrangementModifier.SYNCHRONIZED);
    MODIFIERS.put(PsiModifier.ABSTRACT, ArrangementModifier.ABSTRACT);
  }

  private final Stack<JavaElementArrangementEntry> myStack = new Stack<JavaElementArrangementEntry>();

  @NotNull private final List<JavaElementArrangementEntry> myRootEntries;
  @NotNull private       Document                          myDocument;
  @NotNull private       Collection<TextRange>             myRanges;

  public JavaArrangementVisitor(@NotNull List<JavaElementArrangementEntry> entries,
                                @NotNull Document document,
                                @NotNull Collection<TextRange> ranges)
  {
    myRootEntries = entries;
    myDocument = document;
    myRanges = ranges;
  }

  @Override
  public void visitClass(PsiClass aClass) {
    ArrangementEntryType type = ArrangementEntryType.CLASS;
    if (aClass.isEnum()) {
      type = ArrangementEntryType.ENUM;
    }
    else if (aClass.isInterface()) {
      type = ArrangementEntryType.INTERFACE;
    }
    JavaElementArrangementEntry entry = createNewEntry(aClass.getTextRange(), type, aClass.getName(), true);
    processEntry(entry, aClass, aClass);
  }

  @Override
  public void visitAnonymousClass(PsiAnonymousClass aClass) {
    JavaElementArrangementEntry entry = createNewEntry(aClass.getTextRange(), ArrangementEntryType.CLASS, aClass.getName(), false);
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
    JavaElementArrangementEntry entry = createNewEntry(field.getTextRange(), ArrangementEntryType.FIELD, field.getName(), true);
    processEntry(entry, field, field.getInitializer());
  }

  @Override
  public void visitMethod(PsiMethod method) {
    JavaElementArrangementEntry entry = createNewEntry(method.getTextRange(), ArrangementEntryType.METHOD, method.getName(), true);
    processEntry(entry, method, method.getBody());
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
      createNewEntry(anonymousClass.getTextRange(), ArrangementEntryType.CLASS, anonymousClass.getName(), false);
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
  private JavaElementArrangementEntry createNewEntry(@NotNull TextRange range,
                                                     @NotNull ArrangementEntryType type,
                                                     @Nullable String name,
                                                     boolean canArrange)
  {
    if (!isWithinBounds(range)) {
      return null;
    }
    DefaultArrangementEntry current = getCurrent();
    JavaElementArrangementEntry entry;
    if (canArrange) {
      TextRange expandedRange = ArrangementUtil.expandToLine(range, myDocument.getCharsSequence());
      TextRange rangeToUse = expandedRange == null ? range : expandedRange;
      entry = new JavaElementArrangementEntry(current, rangeToUse, type, name, expandedRange != null);
    }
    else {
      entry = new JavaElementArrangementEntry(current, range, type, name, false);
    }
    if (current == null) {
      myRootEntries.add(entry);
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
        entry.addModifier(MODIFIERS.get(modifier));
      }
    }
    if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      entry.addModifier(ArrangementModifier.PACKAGE_PRIVATE);
    }
  }
}