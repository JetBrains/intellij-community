// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ComparableImplementedButEqualsNotOverriddenInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.problem.descriptor");
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    if (infos[0] instanceof PsiAnonymousClass) {
      return new LocalQuickFix[] {new GenerateEqualsMethodFix()};
    }

    return new LocalQuickFix[] {
      new GenerateEqualsMethodFix(),
      new AddNoteFix()
    };
  }

  private static class GenerateEqualsMethodFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.generate.equals.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiClass aClass = (PsiClass)startElement.getParent();
      final @NonNls StringBuilder methodText = new StringBuilder();
      if (PsiUtil.isLanguageLevel5OrHigher(aClass)) {
        methodText.append("@java.lang.Override ");
      }
      methodText.append("public ");
      methodText.append("boolean equals(Object o) {\n");
      methodText.append("return o instanceof ").append(aClass.getName());
      methodText.append("&& compareTo((").append(aClass.getName()).append(")o) == 0;\n");
      methodText.append("}");
      final PsiMethod method =
        JavaPsiFacade.getElementFactory(project).createMethodFromText(methodText.toString(), aClass, PsiUtil.getLanguageLevel(aClass));
      final PsiElement newMethod = aClass.add(method);
      CodeStyleManager.getInstance(project).reformat(newMethod);
    }
  }

  private static class AddNoteFix extends PsiUpdateModCommandQuickFix {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\*[ \t]+@");
    // Let's keep a doc comment in English. Otherwise it will be hard to suppress the warning based on the JavaDoc substring
    // (see CompareToAndEqualsNotPairedVisitor#visitClass below).
    private static final @NonNls String NOTE = " * Note: this class has a natural ordering that is inconsistent with equals.\n";

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.add.note.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiClass aClass = (PsiClass)startElement.getParent();
      final PsiDocComment comment = aClass.getDocComment();
      if (comment == null) {
        final PsiDocComment newComment = JavaPsiFacade.getElementFactory(project).createDocCommentFromText("/**\n" + NOTE + "*/", aClass);
        aClass.addBefore(newComment, aClass.getFirstChild());
      }
      else {
        final String text = comment.getText();
        final Matcher matcher = PARAM_PATTERN.matcher(text);
        final String newCommentText = matcher.find()
                                      ? text.substring(0, matcher.start()) + NOTE + text.substring(matcher.start())
                                      : text.substring(0, text.length() - 2) + NOTE + "*/";
        final PsiDocComment newComment = JavaPsiFacade.getElementFactory(project).createDocCommentFromText(newCommentText);
        comment.replace(newComment);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CompareToAndEqualsNotPairedVisitor();
  }

  private static class CompareToAndEqualsNotPairedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isInterface() || aClass.isRecord() || aClass.isEnum()) {
        // the problem can't be fixed for an interface, so let's not report it
        // a record has an equals method implemented by default
        // an enum has a default final compareTo() method, no need to check
        return;
      }
      final PsiClass comparableClass =
        JavaPsiFacade.getInstance(aClass.getProject()).findClass(CommonClassNames.JAVA_LANG_COMPARABLE, aClass.getResolveScope());
      if (comparableClass == null || !aClass.isInheritor(comparableClass, true)) {
        return;
      }
      final PsiMethod[] comparableMethods = comparableClass.findMethodsByName(HardcodedMethodConstants.COMPARE_TO, false);
      if (comparableMethods.length == 0) { // incorrect/broken jdk
        return;
      }
      final PsiMethod comparableMethod = MethodSignatureUtil.findMethodBySuperMethod(aClass, comparableMethods[0], false);
      if (comparableMethod == null || comparableMethod.hasModifierProperty(PsiModifier.ABSTRACT) ||
        comparableMethod.getBody() == null) {
        return;
      }
      final PsiClass objectClass = ClassUtils.findObjectClass(aClass);
      if (objectClass == null) {
        return;
      }
      final PsiMethod[] equalsMethods = objectClass.findMethodsByName(HardcodedMethodConstants.EQUALS, false);
      if (equalsMethods.length != 1) { // incorrect/broken jdk
        return;
      }
      final PsiMethod equalsMethod = MethodSignatureUtil.findMethodBySuperMethod(aClass, equalsMethods[0], false);
      if (equalsMethod != null && !equalsMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final String docCommentText = StringUtil.collapseWhiteSpace(getActualCommentText(aClass.getDocComment()));
      if (StringUtil.containsIgnoreCase(docCommentText, "this class has a natural ordering that is inconsistent with equals")) {
        // see Comparable.compareTo() javadoc
        return;
      }
      registerClassError(aClass, aClass);
    }

    private static String getActualCommentText(PsiDocComment comment) {
      if (comment == null) return "";
      return Arrays.stream(comment.getChildren())
        .filter(e -> (e instanceof PsiDocToken) && ((PsiDocToken)e).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA)
        .map(PsiElement::getText)
        .collect(Collectors.joining());
    }
  }
}
