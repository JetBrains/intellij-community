package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

public class RemoveSuppressWarningAction implements IntentionAction, LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction");

  private final String myID;
  protected final PsiElement myContext;

  public RemoveSuppressWarningAction(String ID, PsiElement context) {
    myID = ID;
    myContext = context;
  }

  public String getText() {
    return QuickFixBundle.message("remove.suppression.action.name", myID);
  }

  public String getFamilyName() {
    return QuickFixBundle.message("remove.suppression.action.family");
  }

  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    try {
      invoke(project, null, element.getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public String getName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    final Module module = ModuleUtil.findModuleForPsiElement(myContext);
    if (module == null) return false;
    final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
    if (jdk == null) return false;
    final boolean is_1_5 = jdk.getVersionString().indexOf("1.5") > 0;
    return  DaemonCodeAnalyzerSettings.getInstance().SUPPRESS_WARNINGS && is_1_5 && LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(myContext)) <= 0 &&
            myContext.isValid() && myContext.getManager().isInProject(myContext);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    if (myContext instanceof PsiAnnotation) {
      removeFromAnnotation((PsiAnnotation)myContext);
    }
    else if (myContext instanceof PsiDocComment) {
      removeFromJavaDoc((PsiDocComment)myContext);
    }
    else if (myContext instanceof PsiComment) {
      removeFromComment((PsiComment)myContext);
    }
    else {
      LOG.error("invalid element type: " + myContext);
    }
  }

  private void removeFromComment(final PsiComment comment) throws IncorrectOperationException {
    String newText = removeFromElementText(comment);
    if (newText == null) {
      comment.delete();
    }
    else {
      PsiComment newComment = comment.getManager().getElementFactory().createCommentFromText("// " + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME+" "+newText, comment);
      comment.replace(newComment);
    }
  }

  private void removeFromJavaDoc(PsiDocComment docComment) throws IncorrectOperationException {
    PsiDocTag tag = docComment.findTagByName(GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME);
    if (tag == null) return;
    String newText = removeFromElementText(tag.getValueElement());
    if (newText == null) {
      tag.delete();
    }
    else {
      newText = "@" + GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME + newText;
      PsiDocTag newTag = tag.getManager().getElementFactory().createDocTagFromText(newText, tag);
      tag.replace(newTag);
    }
  }

  private String removeFromElementText(final PsiElement element) {
    String text = StringUtil.trimStart(element.getText(), "//").trim();
    text = StringUtil.trimStart(text, "@").trim();
    text = StringUtil.trimStart(text, GlobalInspectionContextImpl.SUPPRESS_INSPECTIONS_TAG_NAME).trim();
    List<String> ids = StringUtil.split(text, ",");
    int i = ArrayUtil.find(ids.toArray(), myID);
    if (i==-1 || ids.size()==1) return null;
    ids.remove(i);
    return StringUtil.join(ids, ",");
  }

  private void removeFromAnnotation(final PsiAnnotation annotation) throws IncorrectOperationException {
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      PsiAnnotationMemberValue value = attribute.getValue();
      if (value instanceof PsiArrayInitializerMemberValue) {
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
        for (PsiAnnotationMemberValue initializer : initializers) {
          if (removeFromValue(initializer, initializers.length==1)) return;
        }
      }
      if (removeFromValue(value, attributes.length==1)) return;
    }
  }

  private boolean removeFromValue(final PsiAnnotationMemberValue value, final boolean removeParent) throws IncorrectOperationException {
    String text = value.getText();
    text = StringUtil.trimStart(text, "\"");
    text = StringUtil.trimEnd(text, "\"");
    if (myID.equals(text)) {
      if (removeParent) {
        myContext.delete();
      }
      else {
        value.delete();
      }
      return true;
    }
    return false;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
