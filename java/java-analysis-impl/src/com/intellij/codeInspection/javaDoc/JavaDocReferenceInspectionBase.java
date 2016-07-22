/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaDocReferenceInspectionBase  extends BaseJavaBatchLocalInspectionTool {
  @NonNls private static final String SHORT_NAME = "JavadocReference";

  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, InspectionManager manager,
                                                    boolean onTheFly) {
    return manager.createProblemDescriptor(element, template, onTheFly, null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
  }

  public void visitRefInDocTag(final PsiDocTag tag,
                               final JavadocManager manager,
                               final PsiElement context,
                               final List<ProblemDescriptor> problems,
                               final InspectionManager inspectionManager,
                               final boolean onTheFly) {
    final String tagName = tag.getName();
    final PsiDocTagValue value = tag.getValueElement();
    if (value == null) return;
    final JavadocTagInfo info = manager.getTagInfo(tagName);
    if (info != null && !info.isValidInContext(context)) return;
    final String message = info == null || !info.isInline() ? null : info.checkTagValue(value);
    if (message != null){
      problems.add(createDescriptor(value, message, inspectionManager, onTheFly));
    }

    final PsiReference reference = value.getReference();
    if (reference == null) return;
    final PsiElement element = reference.resolve();
    if (element != null) return;
    final int textOffset = value.getTextOffset();
    if (textOffset == value.getTextRange().getEndOffset()) return;
    final PsiDocTagValue valueElement = tag.getValueElement();
    if (valueElement == null) return;

    final CharSequence paramName = value.getContainingFile().getViewProvider().getContents().subSequence(textOffset, value.getTextRange().getEndOffset());
    final String params = "<code>" + paramName + "</code>";
    final List<LocalQuickFix> fixes = new ArrayList<>();
    if (onTheFly && "param".equals(tagName)) {
      final PsiDocCommentOwner commentOwner = PsiTreeUtil.getParentOfType(tag, PsiDocCommentOwner.class);
      if (commentOwner instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)commentOwner;
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final PsiDocTag[] tags = tag.getContainingComment().getTags();
        final Set<String> unboundParams = new HashSet<>();
        for (PsiParameter parameter : parameters) {
          if (!JavadocHighlightUtil.hasTagForParameter(tags, parameter)) {
            unboundParams.add(parameter.getName());
          }
        }
        if (!unboundParams.isEmpty()) {
          fixes.add(createRenameReferenceQuickFix(unboundParams));
        }
      }
    }
    fixes.add(new RemoveTagFix(tagName, paramName));

    problems.add(inspectionManager.createProblemDescriptor(valueElement, reference.getRangeInElement(), cannotResolveSymbolMessage(params),
                                                           ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly,
                                                           fixes.toArray(new LocalQuickFix[fixes.size()])));
  }

  protected LocalQuickFix createRenameReferenceQuickFix(Set<String> unboundParams) {
    return null;
  }

  private static String cannotResolveSymbolMessage(String params) {
    return InspectionsBundle.message("inspection.javadoc.problem.cannot.resolve", params);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!PsiPackage.PACKAGE_INFO_FILE.equals(file.getName()) || !(file instanceof PsiJavaFile)) {
      return null;
    }
    final PsiDocComment docComment = PsiTreeUtil.getChildOfType(file, PsiDocComment.class);
    final PsiJavaFile javaFile = (PsiJavaFile)file;
    final String packageName = javaFile.getPackageName();
    final PsiPackage aPackage = JavaPsiFacade.getInstance(file.getProject()).findPackage(packageName);
    return checkComment(docComment, aPackage, manager, isOnTheFly);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod psiMethod, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkMember(psiMethod, manager, isOnTheFly);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkMember(field, manager, isOnTheFly);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkMember(aClass, manager, isOnTheFly);
  }

  @Nullable
  private ProblemDescriptor[] checkMember(final PsiDocCommentOwner docCommentOwner, final InspectionManager manager, final boolean isOnTheFly) {
    return checkComment(docCommentOwner.getDocComment(), docCommentOwner, manager, isOnTheFly);
  }

  private ProblemDescriptor[] checkComment(PsiDocComment docComment, PsiElement context, InspectionManager manager, boolean isOnTheFly) {
    if (docComment == null) return null;

    final List<ProblemDescriptor> problems = new ArrayList<>();
    final Set<PsiJavaCodeReferenceElement> references = new HashSet<>();
    docComment.accept(getVisitor(references, context, problems, manager, isOnTheFly));
    for (PsiJavaCodeReferenceElement reference : references) {
      final PsiElement referenceNameElement = reference.getReferenceNameElement();
      problems.add(manager.createProblemDescriptor(referenceNameElement != null ? referenceNameElement : reference,
                                                   cannotResolveSymbolMessage("<code>" + reference.getText() + "</code>"),
                                                   !isOnTheFly ? null : createAddQualifierFix(reference),
                                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, isOnTheFly));
    }

    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  protected LocalQuickFix createAddQualifierFix(PsiJavaCodeReferenceElement reference) {
    return null;
  }

  private PsiElementVisitor getVisitor(final Set<PsiJavaCodeReferenceElement> references,
                                       final PsiElement context,
                                       final List<ProblemDescriptor> problems,
                                       final InspectionManager manager,
                                       final boolean onTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        JavaResolveResult result = reference.advancedResolve(false);
        if (result.getElement() == null && !result.isPackagePrefixPackageReference()) {
          references.add(reference);
        }
      }

      @Override public void visitDocTag(PsiDocTag tag) {
        super.visitDocTag(tag);
        final JavadocManager javadocManager = JavadocManager.SERVICE.getInstance(tag.getProject());
        final JavadocTagInfo info = javadocManager.getTagInfo(tag.getName());
        if (info == null || !info.isInline()) {
          visitRefInDocTag(tag, javadocManager, context, problems, manager, onTheFly);
        }
      }

      @Override public void visitInlineDocTag(PsiInlineDocTag tag) {
        super.visitInlineDocTag(tag);
        final JavadocManager javadocManager = JavadocManager.SERVICE.getInstance(tag.getProject());
        visitRefInDocTag(tag, javadocManager, context, problems, manager, onTheFly);
      }

      @Override public void visitElement(PsiElement element) {
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
          //do not visit method javadoc twice
          if (!(child instanceof PsiDocCommentOwner)) {
            child.accept(this);
          }
        }
      }
    };
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.javadoc.ref.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.javadoc.issues");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  private static class RemoveTagFix implements LocalQuickFix {
    private final String myTagName;
    private final CharSequence myParamName;

    public RemoveTagFix(String tagName, CharSequence paramName) {
      myTagName = tagName;
      myParamName = paramName;
    }

    @Override
    @NotNull
    public String getName() {
      return "Remove @" + myTagName + " " + myParamName;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return "Remove tag";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiDocTag myTag = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiDocTag.class);
      if (myTag == null) return;
      if (!FileModificationService.getInstance().preparePsiElementForWrite(myTag)) return;
      myTag.delete();
    }
  }
}
