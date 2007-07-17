package com.intellij.codeInspection;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class RemoveAnnotationQuickFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.i18n.AnnotateNonNlsQuickfix");
  private final PsiAnnotation myAnnotation;
  private final PsiModifierListOwner myListOwner;

  public RemoveAnnotationQuickFix(PsiAnnotation annotation, final PsiModifierListOwner listOwner) {
    myAnnotation = annotation;
    myListOwner = listOwner;
  }

  @NotNull
  public String getName() {
    return CodeInsightBundle.message("remove.annotation");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (myAnnotation.isPhysical()) {
      try {
        if (!CodeInsightUtil.preparePsiElementForWrite(myAnnotation)) return;
        myAnnotation.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    } else {
      ExternalAnnotationsManager.getInstance(project).deannotate(myListOwner, myAnnotation.getQualifiedName());
    }
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }
}