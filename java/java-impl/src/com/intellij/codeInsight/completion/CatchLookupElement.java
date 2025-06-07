// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
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

  private static final int MAX_LIMIT_SIZE = 200;
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
    int catchLength = JavaKeywords.CATCH.length();
    presentation.setItemText(fullItemText.substring(0, catchLength));
    presentation.setItemTextBold(true);
    presentation.setTailText(fullItemText.substring(catchLength));
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    Project project = context.getProject();
    int startOffset = context.getStartOffset();

    Editor editor = context.getEditor();
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    PsiFile psiFile = context.getFile();
    PsiElement element = psiFile.findElementAt(startOffset);
    element = PsiTreeUtil.getParentOfType(element, PsiCatchSection.class, false);
    if (element != null) {
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      PsiElement finalElement = element;
      element = DumbService.getInstance(project)
        .computeWithAlternativeResolveEnabled(() -> codeStyleManager.shortenClassReferences(finalElement));
    }
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    if (element instanceof PsiCatchSection catchSection) {
      catchSection = (PsiCatchSection)CodeStyleManager.getInstance(project).reformat(catchSection);
      PsiCodeBlock catchBlock = catchSection.getCatchBlock();
      if (catchBlock != null) {
        TextRange rangeToSelect = SurroundWithUtil.getRangeToSelect(catchBlock);
        context.getEditor().getSelectionModel().setSelection(rangeToSelect.getStartOffset(), rangeToSelect.getEndOffset());
        editor.getCaretModel().moveToOffset(rangeToSelect.getEndOffset());
      }
    }
  }

  static @NotNull List<LookupElement> create(@NotNull PsiElement position) {
    PsiElement parent = PsiTreeUtil.getParentOfType(position, false, PsiTryStatement.class);
    if (!(parent instanceof PsiTryStatement tryStatement)) {
      return List.of();
    }

    PsiCodeBlock block = tryStatement.getTryBlock();
    if (block == null) {
      return List.of();
    }
    Project project = tryStatement.getProject();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiStatement[] statements = block.getStatements();

    PsiCatchSection[] sections = tryStatement.getCatchSections();
    Set<@NotNull PsiType> existedExceptionTypes = new HashSet<>();
    List<String> stringExceptions = new ArrayList<>();

    for (PsiCatchSection section : sections) {
      PsiType catchType = section.getCatchType();
      if(catchType != null) {
        existedExceptionTypes.add(catchType);
      }
    }

    //if block is big enough, analyze can take a lot of time, let's use hardcoded exception
    if (block.getTextLength() > MAX_LIMIT_SIZE || existedExceptionTypes.size() > MAX_LOOKUP_SIZE) {
      stringExceptions.addAll(getHardcodedExceptions(existedExceptionTypes));
    }
    else {
      DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
        List<PsiClassType> exceptionTypes = getUnhandledExceptionTypes(statements);
        if (exceptionTypes.size() <= MAX_LOOKUP_SIZE) {
          List<@NotNull PsiClass> existedExceptionClasses =
            ContainerUtil.mapNotNull(existedExceptionTypes, exist -> PsiUtil.resolveClassInClassTypeOnly(exist));
          for (PsiClassType exceptionType : exceptionTypes) {
            PsiClass thrownException = PsiUtil.resolveClassInClassTypeOnly(exceptionType);
            if (thrownException != null &&
                !ContainerUtil.or(existedExceptionClasses,
                                  existed ->
                                    InheritanceUtil.isInheritorOrSelf(thrownException, existed, true))) {
              String qualifiedName = thrownException.getQualifiedName();
              if (qualifiedName != null) {
                stringExceptions.add(qualifiedName);
              }
            }
          }
        }
      });

      if (stringExceptions.isEmpty() || stringExceptions.size() > MAX_LOOKUP_SIZE) {
        stringExceptions.addAll(getHardcodedExceptions(existedExceptionTypes));
      }
    }

    List<LookupElement> lookupElements = new ArrayList<>();

    for (String stringException : stringExceptions) {
      try {
        PsiClassType exceptionType = factory.createTypeByFQClassName(stringException, tryStatement.getResolveScope());
        String name =
          new VariableNameGenerator(tryStatement, VariableKind.PARAMETER).byName("e", "ex", "exc").generate(false);
        PsiCatchSection catchSection = factory.createCatchSection(exceptionType, name, tryStatement);
        String catchSectionText = catchSection.getText();
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

  private static @NotNull List<String> getHardcodedExceptions(@NotNull Set<@NotNull PsiType> existedExceptions) {
    ArrayList<String> exceptions = new ArrayList<>();
    for (String defaultException : DEFAULT_EXCEPTIONS) {
      if (ContainerUtil.exists(existedExceptions, t -> defaultException.equals(t.getCanonicalText()))) {
        break;
      }
      exceptions.add(defaultException);
    }
    Collections.reverse(exceptions);
    return exceptions;
  }

  private static @NotNull List<PsiClassType> getUnhandledExceptionTypes(PsiElement @NotNull [] statements) {
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(statements);
    if (exceptions.isEmpty()) {
      exceptions = ExceptionUtil.getThrownExceptions(statements);
    }
    return exceptions;
  }
}
