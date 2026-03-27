// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_EXCEPTION;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_THROWABLE;

@NotNullByDefault
final class CatchCompletionItem extends PsiUpdateCompletionItem<PsiCatchSection> {
  private static final int MAX_LIMIT_SIZE = 200;
  private static final List<String> DEFAULT_EXCEPTIONS = List.of(JAVA_LANG_THROWABLE, JAVA_LANG_EXCEPTION, JAVA_LANG_RUNTIME_EXCEPTION);
  private static final int MAX_LOOKUP_SIZE = 2;
  private final @NlsSafe String fullItemText;

  CatchCompletionItem(PsiCatchSection section, String lookupString) {
    super(lookupString, section);
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
  public void update(ActionContext actionContext, InsertionContext insertionContext, ModPsiUpdater updater) {
    Project project = updater.getProject();
    int startOffset = updater.getCaretOffset() - 1;

    PsiDocumentManager.getInstance(project).commitDocument(updater.getDocument());
    PsiFile psiFile = updater.getPsiFile();
    PsiElement element = psiFile.findElementAt(startOffset);
    element = PsiTreeUtil.getParentOfType(element, PsiCatchSection.class, false);
    if (element != null) {
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      PsiElement finalElement = element;
      element = DumbService.getInstance(project)
        .computeWithAlternativeResolveEnabled(() -> codeStyleManager.shortenClassReferences(finalElement));
      element = CodeStyleManager.getInstance(project).reformat(element);
    }

    if (element instanceof PsiCatchSection catchSection) {
      catchSection = (PsiCatchSection)CodeStyleManager.getInstance(project).reformat(catchSection);
      PsiCodeBlock catchBlock = catchSection.getCatchBlock();
      if (catchBlock != null) {
        TextRange rangeToSelect = SurroundWithUtil.getRangeToSelect(catchBlock);
        updater.select(rangeToSelect);
        updater.moveCaretTo(rangeToSelect.getEndOffset());
      }
    }
  }

  @Override
  public ModCompletionItemPresentation presentation() {
    int catchLength = JavaKeywords.CATCH.length();
    MarkupText text = MarkupText.builder().append(fullItemText.substring(0, catchLength), MarkupText.Kind.STRONG)
      .append(fullItemText.substring(catchLength), MarkupText.Kind.NORMAL).build();
    return new ModCompletionItemPresentation(text);
  }

  static List<ModCompletionItem> create(PsiElement position) {
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
    Set<PsiType> existedExceptionTypes = new HashSet<>();
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
          List<PsiClass> existedExceptionClasses =
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

    List<ModCompletionItem> lookupElements = new ArrayList<>();

    for (String stringException : stringExceptions) {
      try {
        PsiClassType exceptionType = factory.createTypeByFQClassName(stringException, tryStatement.getResolveScope());
        String name =
          new VariableNameGenerator(tryStatement, VariableKind.PARAMETER).byName("e", "ex", "exc").generate(false);
        PsiCatchSection catchSection = factory.createCatchSection(exceptionType, name, tryStatement);
        String catchSectionText = catchSection.getText();
        lookupElements.add(new CatchCompletionItem(catchSection, catchSectionText));
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

  private static List<String> getHardcodedExceptions(Set<PsiType> existedExceptions) {
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

  private static List<PsiClassType> getUnhandledExceptionTypes(PsiElement[] statements) {
    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(statements);
    if (exceptions.isEmpty()) {
      exceptions = ExceptionUtil.getThrownExceptions(statements);
    }
    return exceptions;
  }
}
