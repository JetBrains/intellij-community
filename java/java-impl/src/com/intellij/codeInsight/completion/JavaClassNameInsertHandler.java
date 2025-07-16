// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.*;

class JavaClassNameInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  static final InsertHandler<JavaPsiClassReferenceElement> JAVA_CLASS_INSERT_HANDLER = new JavaClassNameInsertHandler();

  @Override
  public void handleInsert(final @NotNull InsertionContext context, final @NotNull JavaPsiClassReferenceElement item) {
    int offset = context.getTailOffset() - 1;
    final PsiFile file = context.getFile();
    PsiImportStatementBase importStatement = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiImportStatementBase.class, false);
    if (importStatement != null) {
      PsiJavaCodeReferenceElement ref = findJavaReference(file, offset);
      String qname = item.getQualifiedName();
      if (qname != null && (ref == null || !qname.equals(ref.getCanonicalText()))) {
        AllClassesGetter.INSERT_FQN.handleInsert(context, item);
      }
      if (importStatement instanceof PsiImportStaticStatement) {
        context.setAddCompletionChar(false);
        EditorModificationUtilEx.insertStringAtCaret(context.getEditor(), ".");
      }
      return;
    }

    PsiElement position = file.findElementAt(offset);
    PsiJavaCodeReferenceElement ref = position != null && position.getParent() instanceof PsiJavaCodeReferenceElement ?
                                      (PsiJavaCodeReferenceElement) position.getParent() : null;
    PsiClass psiClass = item.getObject();
    //psiClass after completion can be broken and not parseable, but it is expected
    if (!psiClass.isValid()) {
      return;
    }
    SmartPsiElementPointer<PsiClass> classPointer = SmartPointerManager.createPointer(psiClass);
    final Project project = context.getProject();

    final Editor editor = context.getEditor();
    final char c = context.getCompletionChar();
    if (c == '#') {
      context.setLaterRunnable(() -> new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor));
    } else if (c == '.' && PsiTreeUtil.getParentOfType(position, PsiParameterList.class) == null) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }

    String qname = psiClass.getQualifiedName();
    if (qname != null && PsiTreeUtil.getParentOfType(position, PsiDocComment.class, false) != null &&
        (ref == null || !ref.isQualified()) &&
        shouldInsertFqnInJavadoc(item, file)) {
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), qname);
      return;
    }

    if (ref != null && PsiTreeUtil.getParentOfType(position, PsiDocTag.class) != null && ref.isReferenceTo(psiClass)) {
      return;
    }

    OffsetKey refEnd = context.trackOffset(context.getTailOffset(), true);

    boolean fillTypeArgs = context.getCompletionChar() == '<';
    if (fillTypeArgs) {
      context.setAddCompletionChar(false);
    }

    PsiClass finalPsiClass = psiClass;
    DumbService.getInstance(project).runWithAlternativeResolveEnabled(() -> PsiTypeLookupItem.addImportForItem(context, finalPsiClass));
    if (!context.getOffsetMap().containsOffset(refEnd)) {
      return;
    }

    context.setTailOffset(context.getOffset(refEnd));
    refEnd = context.trackOffset(context.getTailOffset(), false);

    context.commitDocument();

    // Restore elements after commit
    position = file.findElementAt(context.getTailOffset() - 1);
    ref = position != null && position.getParent() instanceof PsiJavaCodeReferenceElement ?
          (PsiJavaCodeReferenceElement)position.getParent() : null;

    if (c == '!' || c == '?') {
      context.setAddCompletionChar(false);
      if (ref != null && !(ref instanceof PsiReferenceExpression) &&
          !ref.textContains('@') && !(ref.getParent() instanceof PsiAnnotation)) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
        String annoName = manager.getDefaultAnnotation(c == '!' ? Nullability.NOT_NULL : Nullability.NULLABLE, ref);
        PsiClass cls = JavaPsiFacade.getInstance(project).findClass(annoName, file.getResolveScope());
        if (cls != null) {
          PsiJavaCodeReferenceElement newRef =
            JavaPsiFacade.getElementFactory(project).createReferenceFromText('@' + annoName + ' ' + ref.getText(), ref);
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref.replace(newRef));
        }
      }
    }

    if (ref != null && PsiUtil.isAvailable(JavaFeature.PATTERNS, ref) && psiClass.getTypeParameters().length > 0) {
      PsiExpression instanceOfOperand = JavaCompletionUtil.getInstanceOfOperand(ref);
      if (instanceOfOperand != null) {
        PsiClassType origType = JavaPsiFacade.getElementFactory(project).createType(psiClass);
        PsiType generified = DfaPsiUtil.tryGenerify(instanceOfOperand, origType);
        if (generified != null && generified != origType) {
          String completeTypeText = generified.getCanonicalText();
          PsiJavaCodeReferenceElement newRef =
            JavaPsiFacade.getElementFactory(project).createReferenceFromText(completeTypeText, ref);
          PsiElement resultingRef = JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref.replace(newRef));
          context.getEditor().getCaretModel().moveToOffset(resultingRef.getTextRange().getEndOffset());
          return;
        }
      }
    }

    psiClass = classPointer.dereference();

    if (item.getUserData(JavaChainLookupElement.CHAIN_QUALIFIER) == null &&
        shouldInsertParentheses(file.findElementAt(context.getTailOffset() - 1))) {
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
        overwriteTopmostReference(context);
        context.commitDocument();
      }
      if (psiClass != null && ConstructorInsertHandler.insertParentheses(context, item, psiClass, false)) {
        fillTypeArgs |= psiClass.hasTypeParameters() && PsiUtil.isAvailable(JavaFeature.GENERICS, file);
      }
    }
    else if (insertingAnnotation(context, item)) {
      if (psiClass != null && shouldHaveAnnotationParameters(psiClass)) {
        JavaCompletionUtil.insertParentheses(context, item, false, true);
      }
      if (context.getCompletionChar() == Lookup.NORMAL_SELECT_CHAR || context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
        CharSequence text = context.getDocument().getCharsSequence();
        int tail = context.getTailOffset();
        if (text.length() > tail && Character.isLetter(text.charAt(tail))) {
          context.getDocument().insertString(tail, " ");
        }
      }
    }

    if (fillTypeArgs && context.getCompletionChar() != '(') {
      JavaCompletionUtil.promptTypeArgs(context, context.getOffset(refEnd));
    }
    else if (context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR &&
             psiClass != null && psiClass.getTypeParameters().length == 1 &&
             PsiUtil.isAvailable(JavaFeature.GENERICS, file)) {
      wrapFollowingTypeInGenerics(context, context.getOffset(refEnd));
    }
  }

  private static void wrapFollowingTypeInGenerics(InsertionContext context, int refEnd) {
    PsiTypeElement typeElem = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), refEnd, PsiTypeElement.class, false);
    if (typeElem != null) {
      int typeEnd = typeElem.getTextRange().getEndOffset();
      context.getDocument().insertString(typeEnd, ">");
      context.getEditor().getCaretModel().moveToOffset(typeEnd + 1);
      context.getDocument().insertString(refEnd, "<");
      context.setAddCompletionChar(false);
    }
  }

  static @Nullable PsiJavaCodeReferenceElement findJavaReference(@NotNull PsiFile file, int offset) {
    return PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiJavaCodeReferenceElement.class, false);
  }

  static void overwriteTopmostReference(InsertionContext context) {
    context.commitDocument();
    PsiJavaCodeReferenceElement ref = findJavaReference(context.getFile(), context.getTailOffset() - 1);
    if (ref != null) {
      while (ref.getParent() instanceof PsiJavaCodeReferenceElement) ref = (PsiJavaCodeReferenceElement)ref.getParent();
      context.getDocument().deleteString(context.getTailOffset(), ref.getTextRange().getEndOffset());
    }
  }

  private static boolean shouldInsertFqnInJavadoc(@NotNull JavaPsiClassReferenceElement item, @NotNull PsiFile file) {
    JavaCodeStyleSettings javaSettings = getInstance(file);

    return switch (javaSettings.CLASS_NAMES_IN_JAVADOC) {
      case FULLY_QUALIFY_NAMES_ALWAYS -> true;
      case FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED -> file instanceof PsiJavaFile javaFile &&
                                                  item.getQualifiedName() != null &&
                                                  !ImportHelper.isAlreadyImported(javaFile, item.getQualifiedName());
      default -> false;
    };
  }

  private static boolean shouldInsertParentheses(PsiElement position) {
    final PsiJavaCodeReferenceElement ref = PsiTreeUtil.getParentOfType(position, PsiJavaCodeReferenceElement.class);
    if (ref == null) {
      return false;
    }

    final PsiReferenceParameterList parameterList = ref.getParameterList();
    if (parameterList != null && parameterList.getTextLength() > 0) {
      return false;
    }

    final PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(ref);
    if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
      return !DumbService.getInstance(position.getProject())
        .computeWithAlternativeResolveEnabled(() -> isArrayTypeExpected((PsiExpression)prevElement.getParent()));
    }

    return false;
  }

  static boolean isArrayTypeExpected(PsiExpression expr) {
    return ContainerUtil.exists(ExpectedTypesProvider.getExpectedTypes(expr, true),
                                info -> {
                                  if (info.getType() instanceof PsiArrayType) {
                                    PsiMethod method = info.getCalledMethod();
                                    return method == null || !method.isVarArgs() || !(expr.getParent() instanceof PsiExpressionList) ||
                                           MethodCallUtils.getParameterForArgument(expr) != null;
                                  }
                                  return false;
                                });
  }

  private static boolean insertingAnnotation(InsertionContext context, LookupElement item) {
    final Object obj = item.getObject();
    if (!(obj instanceof PsiClass) || !((PsiClass)obj).isAnnotationType()) return false;

    PsiElement leaf = context.getFile().findElementAt(context.getStartOffset());
    PsiAnnotation anno = PsiTreeUtil.getParentOfType(leaf, PsiAnnotation.class);
    return anno != null && PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), leaf, false);
  }

  static boolean shouldHaveAnnotationParameters(PsiClass annoClass) {
    for (PsiMethod m : annoClass.getMethods()) {
      if (!PsiUtil.isAnnotationMethod(m)) continue;
      if (((PsiAnnotationMethod)m).getDefaultValue() == null) return true;
    }
    return false;
  }
}
