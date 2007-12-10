package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.SuppressUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class AddNoInspectionDocTagFix implements IntentionAction {
  private String myID;
  protected SmartPsiElementPointer myContext;

  public AddNoInspectionDocTagFix(LocalInspectionTool tool, PsiElement context) {
    this(tool.getID(), context);
  }

  public AddNoInspectionDocTagFix(HighlightDisplayKey key, PsiElement context) {
    this(key.getID(), context);
  }

  public AddNoInspectionDocTagFix(String ID, PsiElement context) {
    myID = ID;
    myContext = SmartPointerManager.getInstance(context.getProject()).createLazyPointer(context);
  }

  @NotNull
  public String getText() {
    PsiDocCommentOwner container = getContainer();

    @NonNls String key = container instanceof PsiClass
                         ? "suppress.inspection.class"
                         : container instanceof PsiMethod
                           ? "suppress.inspection.method"
                           : "suppress.inspection.field";
    return InspectionsBundle.message(key);
  }

  @Nullable protected PsiDocCommentOwner getContainer() {
    PsiElement context = myContext.getElement();
    if (context == null || !(context.getContainingFile().getLanguage() instanceof JavaLanguage) || context instanceof PsiFile){
      return null;
    }
    PsiElement container = context;
    while (!(container instanceof PsiDocCommentOwner) || container instanceof PsiTypeParameter) {
      container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
      if (container == null) return null;
    }
    return (PsiDocCommentOwner)container;
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiDocCommentOwner container = getContainer();
    final boolean isValid = container != null && !(container instanceof JspHolderMethod);
    if (!isValid) return false;
    if (SuppressUtil.canHave15Suppressions(file) && !SuppressUtil.alreadyHas14Suppressions(container)) return false;
    PsiElement context = myContext.getElement();
    return context != null && context.getManager().isInProject(context);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiDocCommentOwner container = getContainer();
    assert container != null;
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(container.getContainingFile().getVirtualFile());
    if (status.hasReadonlyFiles()) return;
    PsiDocComment docComment = container.getDocComment();
    PsiManager manager = PsiManager.getInstance(project);
    if (docComment == null) {
      String commentText = "/** @" + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME + " "+ myID + "*/";
      docComment = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocCommentFromText(commentText, null);
      PsiElement firstChild = container.getFirstChild();
      container.addBefore(docComment, firstChild);
    } else {
      PsiDocTag noInspectionTag = docComment.findTagByName(GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME);
      if (noInspectionTag != null) {
        final PsiDocTagValue valueElement = noInspectionTag.getValueElement();
        String tagText = "@" + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME + " "
                         + (valueElement != null ? valueElement.getText() + "," : "")+ myID;
        noInspectionTag.replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocTagFromText(tagText, null));
      } else {
        String tagText = "@" + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID;
        docComment.add(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocTagFromText(tagText, null));
      }
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
