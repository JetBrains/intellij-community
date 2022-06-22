// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.FQNameCellRenderer;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class JavaDocReferenceInspection extends LocalInspectionTool {
  private static final String SHORT_NAME = "JavadocReference";

  @SuppressWarnings("WeakerAccess")
  public boolean REPORT_INACCESSIBLE = true;

  @Override
  public @Nullable JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      JavaBundle.message("checkbox.html.report.inaccessible.symbols"), this, "REPORT_INACCESSIBLE");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.javadoc.issues");
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(PsiJavaFile file) {
        if (PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
          checkComment(PsiTreeUtil.getChildOfType(file, PsiDocComment.class), file, holder, isOnTheFly);
        }
      }

      @Override
      public void visitModule(PsiJavaModule module) {
        checkComment(module.getDocComment(), module, holder, isOnTheFly);
      }

      @Override
      public void visitClass(PsiClass aClass) {
        checkComment(aClass.getDocComment(), aClass, holder, isOnTheFly);
      }

      @Override
      public void visitField(PsiField field) {
        checkComment(field.getDocComment(), field, holder, isOnTheFly);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        checkComment(method.getDocComment(), method, holder, isOnTheFly);
      }
    };
  }

  private void checkComment(@Nullable PsiDocComment comment, PsiElement context, ProblemsHolder holder, boolean isOnTheFly) {
    if (comment == null) return;

    JavadocManager javadocManager = JavadocManager.SERVICE.getInstance(holder.getProject());
    comment.accept(new JavaElementVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        visitRefElement(reference, context, isOnTheFly, holder);
      }

      @Override
      public void visitDocTag(PsiDocTag tag) {
        super.visitDocTag(tag);
        visitRefInDocTag(tag, javadocManager, context, holder, isOnTheFly);
      }

      @Override
      public void visitElement(@NotNull PsiElement element) {
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
          child.accept(this);
        }
      }
    });
  }

  private void visitRefElement(PsiJavaCodeReferenceElement reference, PsiElement context, boolean isOnTheFly, ProblemsHolder holder) {
    JavaResolveResult result = reference.advancedResolve(false);
    String refText = reference.getText();
    String message = getResolveErrorMessage(result.getElement(), context, refText);
    if (message != null && !result.isPackagePrefixPackageReference()) {
      PsiElement element = Objects.requireNonNullElse(reference.getReferenceNameElement(), reference);

      LocalQuickFix fix = null;
      if (isOnTheFly) {
        List<? extends PsiClass> classesToImport = new ImportClassFix(reference).getClassesToImport();
        if (!classesToImport.isEmpty()) {
          fix = new AddQualifierFix(classesToImport);
        }
        else if (URLUtil.HTTP_PROTOCOL.equals(refText) || URLUtil.HTTPS_PROTOCOL.equals(refText)) {
          PsiElement refHolder = reference.getParent();
          if (refHolder != null && refHolder.getNode().getElementType() == JavaDocElementType.DOC_REFERENCE_HOLDER) {
            PsiElement adjacent = refHolder.getNextSibling();
            if (adjacent instanceof PsiDocToken &&
                ((PsiDocToken)adjacent).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA &&
                adjacent.getText().startsWith(URLUtil.SCHEME_SEPARATOR)) {
              PsiDocComment docComment = PsiTreeUtil.getParentOfType(reference, PsiDocComment.class);
              if (docComment != null) {
                int startOffsetInDocComment = refHolder.getTextOffset() - docComment.getTextOffset();
                int endOffsetInDocComment =
                  refHolder.getTextOffset() + refText.length() + adjacent.getTextLength() - docComment.getTextOffset();
                fix = new UrlToHtmlFix(docComment, startOffsetInDocComment, endOffsetInDocComment);
              }
            }
          }
        }
      }

      holder.registerProblem(holder.getManager().createProblemDescriptor(
        element, message, fix, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, isOnTheFly));
    }
  }

  private void visitRefInDocTag(PsiDocTag tag, JavadocManager manager, PsiElement context, ProblemsHolder holder, boolean isOnTheFly) {
    PsiDocTagValue value = tag.getValueElement();
    if (value == null) return;

    String tagName = tag.getName();
    JavadocTagInfo info = manager.getTagInfo(tagName);
    if (info != null && !info.isValidInContext(context)) return;

    if (info != null && info.isInline()) {
      String message = info.checkTagValue(value);
      if (message != null) {
        holder.registerProblem(holder.getManager().createProblemDescriptor(
          value, message, isOnTheFly, null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL));
      }
    }

    PsiReference reference = value.getReference();
    if (reference == null) return;
    int textOffset = value.getTextOffset();
    if (textOffset == value.getTextRange().getEndOffset()) return;
    PsiDocTagValue valueElement = tag.getValueElement();
    if (valueElement == null) return;

    PsiElement element = reference.resolve();
    String paramName =
      value.getContainingFile().getViewProvider().getContents().subSequence(textOffset, value.getTextRange().getEndOffset()).toString();
    String message = element == null && reference instanceof PsiPolyVariantReference ?
                     getResolveErrorMessage(((PsiPolyVariantReference)reference).multiResolve(false), context, paramName) :
                     getResolveErrorMessage(element, context, paramName);
    if (message == null) {
      return;
    }

    List<LocalQuickFix> fixes = new ArrayList<>();

    if (isOnTheFly && "param".equals(tagName)) {
      PsiDocCommentOwner commentOwner = PsiTreeUtil.getParentOfType(tag, PsiDocCommentOwner.class);
      if (commentOwner instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)commentOwner;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        PsiDocTag[] tags = tag.getContainingComment().getTags();
        Set<String> unboundParams = new HashSet<>();
        for (PsiParameter parameter : parameters) {
          if (!JavadocHighlightUtil.hasTagForParameter(tags, parameter)) {
            unboundParams.add(parameter.getName());
          }
        }
        if (!unboundParams.isEmpty()) {
          fixes.add(new RenameReferenceQuickFix(unboundParams));
        }
      }
    }

    fixes.add(new RemoveTagFix(tagName, paramName));

    if (isOnTheFly && element != null && REPORT_INACCESSIBLE) {
      fixes.add(new SetInspectionOptionFix(this, "REPORT_INACCESSIBLE", JavaBundle.message("disable.report.inaccessible.symbols.fix"), false));
    }

    holder.registerProblem(holder.getManager().createProblemDescriptor(
      valueElement, reference.getRangeInElement(), message, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, isOnTheFly,
      fixes.toArray(LocalQuickFix.EMPTY_ARRAY)));
  }

  private @InspectionMessage String getResolveErrorMessage(ResolveResult[] resolveResults, PsiElement context, CharSequence referenceText) {
    if (resolveResults.length == 0) {
      return JavaBundle.message("inspection.javadoc.problem.cannot.resolve", "<code>" + referenceText + "</code>");
    }

    if (REPORT_INACCESSIBLE &&
        !Arrays.stream(resolveResults).map(ResolveResult::getElement).filter(Objects::nonNull).allMatch(e -> isAccessible(e, context))) {
      return JavaBundle.message("inspection.javadoc.problem.inaccessible", "<code>" + referenceText + "</code>");
    }

    return null;
  }

  private @InspectionMessage String getResolveErrorMessage(@Nullable PsiElement resolved, PsiElement context, CharSequence referenceText) {
    if (resolved == null) {
      return JavaBundle.message("inspection.javadoc.problem.cannot.resolve", "<code>" + referenceText + "</code>");
    }

    if (REPORT_INACCESSIBLE && !isAccessible(resolved, context)) {
      return JavaBundle.message("inspection.javadoc.problem.inaccessible", "<code>" + referenceText + "</code>");
    }

    return null;
  }

  private static boolean isAccessible(PsiElement resolved, PsiElement context) {
    if (!(resolved instanceof PsiMember)) {
      return true;
    }
    if (!PsiResolveHelper.SERVICE.getInstance(resolved.getProject()).isAccessible((PsiMember)resolved, context, null)) {
      return false;
    }
    VirtualFile file = PsiUtilCore.getVirtualFile(resolved);
    return file == null || context.getResolveScope().contains(file);
  }

  private static class RenameReferenceQuickFix implements LocalQuickFix {
    private final Set<String> myUnboundParams;

    RenameReferenceQuickFix(Set<String> unboundParams) {
      myUnboundParams = unboundParams;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("quickfix.family.change.javadoc.to");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(dataContext -> {
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        assert editor != null;

        TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRange();
        if (textRange != null) {
          editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());
        }

        String word = editor.getSelectionModel().getSelectedText();
        if (word != null && !word.isBlank()) {
          List<LookupElement> items = new ArrayList<>();
          for (String variant : myUnboundParams) {
            items.add(LookupElementBuilder.create(variant));
          }
          LookupManager.getInstance(project).showLookup(editor, items.toArray(LookupElement.EMPTY_ARRAY));
        }
      });
    }
  }

  private static class AddQualifierFix implements LocalQuickFix {
    private final List<? extends PsiClass> originalClasses;

    AddQualifierFix(List<? extends PsiClass> originalClasses) {
      this.originalClasses = originalClasses;
    }

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("add.qualifier");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiJavaCodeReferenceElement ref = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiJavaCodeReferenceElement.class);
      if (ref != null) {
        originalClasses.sort(new PsiProximityComparator(ref.getElement()));
        DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(dataContext ->
          JBPopupFactory.getInstance()
            .createPopupChooserBuilder(originalClasses)
            .setTitle(QuickFixBundle.message("add.qualifier.original.class.chooser.title"))
            .setItemChosenCallback(psiClass -> {
              if (!ref.isValid()) return;
              WriteCommandAction.writeCommandAction(project, ref.getContainingFile()).run(() -> {
                if (psiClass.isValid()) {
                  PsiDocumentManager.getInstance(project).commitAllDocuments();
                  ref.bindToElement(psiClass);
                }
              });
            })
            .setRenderer(new FQNameCellRenderer())
            .createPopup()
            .showInBestPositionFor(dataContext));
      }
    }
  }

  private static class RemoveTagFix implements LocalQuickFix {
    private final String myTagName;
    private final String myParamName;

    RemoveTagFix(String tagName, String paramName) {
      myTagName = tagName;
      myParamName = paramName;
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("quickfix.text.remove.javadoc.0.1", myTagName, myParamName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("quickfix.family.remove.javadoc.tag");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiDocTag myTag = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiDocTag.class);
      if (myTag != null) {
        myTag.delete();
      }
    }
  }
}
