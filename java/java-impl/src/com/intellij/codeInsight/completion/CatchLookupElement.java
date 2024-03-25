// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.psi.CommonClassNames.*;

final class CatchLookupElement extends LookupItem<PsiCatchSection> {

  private static final int MAX_LIMIT_SIZE = 300;
  private static final List<String> DEFAULT_EXCEPTIONS = List.of(JAVA_LANG_THROWABLE, JAVA_LANG_EXCEPTION, JAVA_LANG_RUNTIME_EXCEPTION);
  private static final int MAX_LOOKUP_SIZE = 2;

  private final String fullItemText;

  private CatchLookupElement(@NotNull PsiCatchSection section, @NotNull String lookupString) {
    super(section, lookupString);
    StringBuilder builder = new StringBuilder();
    PsiJavaToken rParenth = section.getRParenth();
    for (PsiElement child : section.getChildren()) {
      if (child instanceof PsiVariable psiVariable) {
        int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_MODIFIERS;
        builder.append(PsiFormatUtil.formatVariable(psiVariable, options, PsiSubstitutor.EMPTY));
      }
      else {
        builder.append(child.getText());
      }
      if (child.equals(rParenth)) {
        break;
      }
    }
    fullItemText = builder.toString();
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    int catchLength = PsiKeyword.CATCH.length();
    presentation.setItemText(fullItemText.substring(0, catchLength));
    presentation.setItemTextBold(true);
    presentation.setTailText(fullItemText.substring(catchLength));
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    Project project = context.getProject();
    int startOffset = context.getStartOffset();

    PsiDocumentManager.getInstance(project).commitDocument(context.getEditor().getDocument());
    PsiFile psiFile = context.getFile();
    PsiElement element = psiFile.findElementAt(startOffset);
    element = PsiTreeUtil.getParentOfType(element, PsiCatchSection.class, false);
    if (element != null) {
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      codeStyleManager.shortenClassReferences(element);
    }
  }

  @NotNull
  static List<LookupElement> create(@NotNull PsiElement position) {
    PsiElement parent = PsiTreeUtil.getParentOfType(position, false, PsiTryStatement.class);
    if (!(parent instanceof PsiTryStatement tryStatement)) {
      return List.of();
    }

    PsiCodeBlock block = tryStatement.getTryBlock();
    if (block == null) {
      return List.of();
    }
    Project project = tryStatement.getProject();
    if (DumbService.isDumb(project)) {
      return List.of();
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiStatement[] statements = block.getStatements();

    PsiCatchSection[] sections = tryStatement.getCatchSections();
    Set<PsiClass> existedExceptionClasses = new HashSet<>();
    List<String> stringExceptions = new ArrayList<>();

    for (PsiCatchSection section : sections) {
      PsiType catchType = section.getCatchType();
      PsiClass existedException = PsiUtil.resolveClassInClassTypeOnly(catchType);
      if (existedException != null) {
        existedExceptionClasses.add(existedException);
      }
    }

    //if block is big enough, analyze can take a lot of time, let's use hardcoded exception
    if (block.getTextLength() > MAX_LIMIT_SIZE || existedExceptionClasses.size() > MAX_LOOKUP_SIZE) {
      stringExceptions = getHardcodedExceptions(existedExceptionClasses);
    }
    else {
      List<PsiClassType> exceptionTypes = getUnhandledExceptionTypes(statements);
      if (exceptionTypes.size() <= MAX_LOOKUP_SIZE) {
        for (PsiClassType exceptionType : exceptionTypes) {
          PsiClass thrownException = PsiUtil.resolveClassInClassTypeOnly(exceptionType);
          if (thrownException != null && !ContainerUtil.or(existedExceptionClasses,
                                                           existed-> InheritanceUtil.isInheritorOrSelf(thrownException, existed, true))) {
            String qualifiedName = thrownException.getQualifiedName();
            if (qualifiedName != null) {
              stringExceptions.add(qualifiedName);
            }
          }
        }
      }
      if (stringExceptions.isEmpty() || stringExceptions.size() > MAX_LOOKUP_SIZE) {
        stringExceptions = getHardcodedExceptions(existedExceptionClasses);
      }
    }

    List<LookupElement> lookupElements = new ArrayList<>();

    for (String stringException : stringExceptions) {
      try {
        PsiClassType exceptionType = factory.createTypeByFQClassName(stringException, tryStatement.getResolveScope());
        String name =
          new VariableNameGenerator(tryStatement, VariableKind.PARAMETER).byName("e", "ex", "exc").generate(false);
        PsiCatchSection catchSection = factory.createCatchSection(exceptionType, name, tryStatement);
        catchSection = (PsiCatchSection)CodeStyleManager.getInstance(project).reformat(catchSection);
        PsiJavaToken rParenth = catchSection.getRParenth();
        if (rParenth == null) {
          return List.of();
        }
        int offset = rParenth.getTextRangeInParent().getEndOffset();
        String catchSectionText = catchSection.getText().substring(0, offset);
        lookupElements.add(new CatchLookupElement(catchSection, catchSectionText));
        if (lookupElements.size() >= MAX_LOOKUP_SIZE) {
          break;
        }
      }
      catch (IncorrectOperationException e) {
        return List.of();
      }
    }

    return lookupElements;
  }

  @NotNull
  private static List<String> getHardcodedExceptions(@NotNull Set<PsiClass> existedExceptions) {
    ArrayList<String> exceptions = new ArrayList<>();
    for (String defaultException : DEFAULT_EXCEPTIONS) {
      if (ContainerUtil.exists(existedExceptions, t -> defaultException.equals(t.getQualifiedName()))) {
        break;
      }
      exceptions.add(defaultException);
    }
    Collections.reverse(exceptions);
    return exceptions;
  }

  @NotNull
  private static List<PsiClassType> getUnhandledExceptionTypes(PsiElement @NotNull [] statements) {
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(statements);
    if (exceptions.isEmpty()) {
      exceptions = ExceptionUtil.getThrownExceptions(statements);
    }
    return exceptions;
  }
}
