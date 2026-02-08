// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaConstructorCallElement;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

@NotNullByDefault
final class ConstructorCallCompletionItem extends PsiUpdateCompletionItem<Object> {
  private final @Nullable PsiMethod myConstructor;
  private final @Nullable Arguments myArguments;
  private final ClassReferenceCompletionItem myClassItem;

  static @Unmodifiable List<ModCompletionItem> tryWrap(ClassReferenceCompletionItem item, PsiElement position) {
    if (JavaConstructorCallElement.isConstructorCallPlace(position)) {
      PsiClass psiClass = item.contextObject();

      List<PsiMethod> constructors = ContainerUtil.filter(
        psiClass.getConstructors(),
        c -> JavaConstructorCallElement.shouldSuggestConstructor(psiClass, position, c));
      if (CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) {
        if (!constructors.isEmpty()) {
          return ContainerUtil.map(constructors,
                                   c -> new ConstructorCallCompletionItem(item, c));
        }
      }
      else {
        return List.of(new ConstructorCallCompletionItem(item, ContainerUtil.getOnlyItem(constructors)));
      }
    }
    return List.of(item);
  }

  private record Arguments(String canonical, @NlsSafe String presentation) {
  }

  private ConstructorCallCompletionItem(ClassReferenceCompletionItem classItem, @Nullable PsiMethod constructor) {
    super(classItem.mainLookupString(), constructor == null ? classItem.contextObject() : constructor);
    myClassItem = classItem;
    myConstructor = constructor;
    myArguments = computeArguments();
  }

  private @Nullable Arguments computeArguments() {
    if (myConstructor == null) return null;
    PsiParameter[] parameters = myConstructor.getParameterList().getParameters();
    if (parameters.length == 1) {
      PsiType type = myClassItem.getSubstitutor().substitute(parameters[0].getType());
      if (type instanceof PsiClassType clsType && TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_CLASS, clsType.rawType())) {
        PsiType[] typeArgs = clsType.getParameters();
        if (typeArgs.length == 1 && typeArgs[0] instanceof PsiClassType typeArg && typeArg.getParameterCount() == 0) {
          return new Arguments(typeArg.getCanonicalText() + ".class", typeArg.getPresentableText() + ".class");
        }
      }
      if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_VOID, type)) {
        return new Arguments(JavaKeywords.NULL, JavaKeywords.NULL);
      }
    }
    return null;
  }

  @Override
  public boolean isValid() {
    return (myConstructor == null || myConstructor.isValid()) && myClassItem.isValid();
  }

  @Override
  public void update(ActionContext actionContext, InsertionContext insertionContext, ModPsiUpdater updater) {
    myClassItem.update(actionContext, insertionContext, updater);
    Document document = updater.getDocument();
    Project project = updater.getProject();
    PsiClass psiClass = myClassItem.contextObject();
    PsiFile file = updater.getPsiFile();
    insertTail(updater, psiClass);
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiCallExpression callExpression = PsiTreeUtil.findElementOfClassAtOffset(file, updater.getCaretOffset() - 1,
                                                                              PsiCallExpression.class, false);
    // make sure this is the constructor call we've just added, not the enclosing method/constructor call
    if (callExpression != null) {
      PsiExpressionList argumentList = callExpression.getArgumentList();
      if (argumentList != null && myArguments != null) {
        PsiExpressionList newList = ((PsiMethodCallExpression)JavaPsiFacade.getElementFactory(project)
          .createExpressionFromText("a(" + myArguments.canonical() + ")", argumentList)).getArgumentList();
        argumentList = (PsiExpressionList)argumentList.replace(newList);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(argumentList);
        int offset = argumentList.getTextRange().getEndOffset();
        if (updater.getCaretOffset() < offset) {
          updater.moveCaretTo(offset);
        }
      }
    }
  }

  /**
   * Inserts {@code <>()}.
   */
  private static void insertTail(ModPsiUpdater updater, PsiClass psiClass) {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings != null && !settings.isInsertParenthesesAutomatically()) return;
    int inparens = 1;
    int caret = updater.getCaretOffset();
    Document document = updater.getDocument();
    PsiFile file = updater.getPsiFile();
    boolean needRightParenth = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CommonCodeStyleSettings styleSettings = CodeStyle.getLanguageSettings(file, JavaLanguage.INSTANCE);
    PsiMethod[] constructors = psiClass.getConstructors();
    boolean alwaysEmpty = constructors.length == 0 || constructors.length == 1 && !constructors[0].hasParameters();
    String typeParams = "";
    if (psiClass.hasTypeParameters()) {
      typeParams = "<>";
      document.insertString(caret, typeParams);
    }
    String parens = "";
    if (styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES) {
      parens = " ";
      inparens++;
    }
    if (styleSettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES) {
      parens += "(  ";
      inparens++;
    } else {
      parens += "(";
    }
    if (needRightParenth) {
      parens += ")";
    }
    document.insertString(caret + typeParams.length(), parens);
    int inParensOffset = caret + inparens + typeParams.length();
    int endOffset = caret + typeParams.length() + parens.length();
    if (!typeParams.isEmpty()) {
      updater.registerTabOut(TextRange.create(caret + 1, caret + 1), alwaysEmpty ? endOffset : inParensOffset);
      updater.moveCaretTo(updater.getCaretOffset() + 1);
    } else {
      if (alwaysEmpty) {
        updater.moveCaretTo(endOffset);
      } else {
        updater.moveCaretTo(inParensOffset);
      }
    }
    if (needRightParenth && !alwaysEmpty) {
      updater.registerTabOut(TextRange.create(inParensOffset, inParensOffset), endOffset);
    }
  }

  @Override
  public ModCompletionItemPresentation presentation() {
    ModCompletionItemPresentation presentation = myClassItem.presentation();
    MarkupText text = presentation.mainText();
    String args = myArguments != null ? "(" + myArguments.presentation() + ")" :
                  myConstructor == null ? "" :
                  PsiFormatUtil.formatMethod(myConstructor,
                                             myClassItem.getSubstitutor(),
                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                             PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);
    return presentation.withMainText(text.concat(args, MarkupText.Kind.GRAYED));
  }
}
