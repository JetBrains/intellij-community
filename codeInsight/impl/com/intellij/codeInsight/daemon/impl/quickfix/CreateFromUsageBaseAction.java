package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public abstract class CreateFromUsageBaseAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseAction");

  protected CreateFromUsageBaseAction() {
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = getElement();
    if (element == null) {
      return false;
    }

    PsiClass[] targetClasses = getTargetClasses(element);
    return targetClasses != null && !isValidElement(element) && isAvailableImpl(offset);

  }

  protected abstract boolean isAvailableImpl(int offset);

  protected abstract void invokeImpl(PsiClass targetClass);

  protected abstract boolean isValidElement(PsiElement result);

  protected static boolean shouldShowTag(int offset, PsiElement namedElement, PsiElement element) {
    if (namedElement == null) return false;
    TextRange range = namedElement.getTextRange();
    if (range.getLength() == 0) return false;
    boolean isInNamedElement = range.contains(offset);
    return isInNamedElement || element.getTextRange().contains(offset-1);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element = getElement();

    if (LOG.isDebugEnabled()) {
      LOG.debug("CreateFromUsage: element =" + element);
    }

    if (element == null) {
      return;
    }

    PsiClass[] targetClasses = getTargetClasses(element);
    if (targetClasses == null) return;

    if (targetClasses.length == 1) {
      doInvoke(project, targetClasses[0]);
    } else {
      chooseTargetClass(targetClasses, editor, file);
    }
  }

  private void doInvoke(Project project, PsiClass targetClass) {
    if (!CodeInsightUtil.prepareFileForWrite(targetClass.getContainingFile())) {
      return;
    }

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    invokeImpl(targetClass);
  }

  abstract protected PsiElement getElement();

  protected void chooseTargetClass(PsiClass[] classes, final Editor editor, final PsiFile file) {
    final Project project = classes[0].getProject();

    final JList list = new JList(classes);
    PsiElementListCellRenderer renderer = new PsiClassListCellRenderer();
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(renderer);
    renderer.installSpeedSearch(list);

    Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        final PsiClass aClass = (PsiClass) list.getSelectedValue();
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                doInvoke(project, aClass);
              }
            });

          }
        }, getText(), null);
      }
    };

    new PopupChooserBuilder(list).
      setTitle(QuickFixBundle.message("target.class.chooser.title")).
      setItemChoosenCallback(runnable).
      createPopup().
      showInBestPositionFor(editor);
  }

  protected static Editor positionCursor(Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile(), textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  protected static void setupVisibility(PsiClass parentClass, PsiClass targetClass, PsiModifierList list) throws IncorrectOperationException {
    if (targetClass.isInterface()) {
      list.deleteChildRange(list.getFirstChild(), list.getLastChild());
      return;
    }

    if (parentClass != null && (parentClass.equals(targetClass) || PsiTreeUtil.isAncestor(targetClass, parentClass, true))) {
      list.setModifierProperty(PsiModifier.PRIVATE, true);
    } else {
      list.setModifierProperty(PsiModifier.PUBLIC, true);
    }
  }

  protected static boolean shouldCreateStaticMember(PsiReferenceExpression ref, PsiElement enclosingContext, PsiClass targetClass) {
    if (targetClass.isInterface()) {
      return false;
    }

    PsiExpression qualifierExpression = ref.getQualifierExpression();
    while (qualifierExpression instanceof PsiParenthesizedExpression) {
      qualifierExpression = ((PsiParenthesizedExpression) qualifierExpression).getExpression();
    }

    if (qualifierExpression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifierExpression;

      PsiElement resolvedElement = referenceExpression.resolve();

      return resolvedElement instanceof PsiClass;
    } else if (qualifierExpression instanceof PsiTypeCastExpression) {
      return false;
    } else if (qualifierExpression instanceof PsiCallExpression) {
      return false;
    } else {
      if (enclosingContext instanceof PsiMethod) {
        PsiMethodCallExpression callExpression;

        if (ref.getParent() instanceof PsiMethodCallExpression) {
          callExpression = PsiTreeUtil.getParentOfType(ref.getParent(), PsiMethodCallExpression.class);
        } else {
          callExpression = PsiTreeUtil.getParentOfType(ref, PsiMethodCallExpression.class);
        }

        if (callExpression != null && callExpression.getMethodExpression().getText().equals(PsiKeyword.SUPER)) {
          return true;
        }

        PsiMethod method = (PsiMethod) enclosingContext;
        return method.hasModifierProperty(PsiModifier.STATIC);
      } else if (enclosingContext instanceof PsiField) {
        PsiField field = (PsiField) enclosingContext;
        return field.hasModifierProperty(PsiModifier.STATIC);
      } else if (enclosingContext instanceof PsiClassInitializer) {
        PsiClassInitializer initializer = (PsiClassInitializer) enclosingContext;
        return initializer.hasModifierProperty(PsiModifier.STATIC);
      }
    }

    return false;
  }

  private static PsiExpression getQualifier (PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement ref = ((PsiNewExpression) element).getClassReference();
      if (ref instanceof PsiReferenceExpression) {
        return ((PsiReferenceExpression) ref).getQualifierExpression();
      }
    } else if (element instanceof PsiReferenceExpression) {
      return ((PsiReferenceExpression) element).getQualifierExpression();
    } else if (element instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression) element).getMethodExpression().getQualifierExpression();
    }

    return null;
  }

  public static PsiSubstitutor getTargetSubstitutor (PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiSubstitutor substitutor = ((PsiNewExpression)element).getClassReference().advancedResolve(false).getSubstitutor();
      return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
    }

    PsiExpression qualifier = getQualifier(element);
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolveGenerics().getSubstitutor();
      }
    }

    return PsiSubstitutor.EMPTY;
  }

  protected boolean isAllowOuterTargetClass() {
    return true;
  }

  //Should return only valid inproject classes
  protected PsiClass[] getTargetClasses(PsiElement element) {
    PsiClass psiClass = null;
    PsiExpression qualifier = null;

    boolean allowOuterClasses = false;
    if (element instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)element;
      qualifier = newExpression.getQualifier();

      if (qualifier == null) {
        PsiJavaCodeReferenceElement ref = newExpression.getClassReference();
        if (ref != null) {
          PsiElement refElement = ref.resolve();
          if (refElement instanceof PsiClass) psiClass = (PsiClass) refElement;
        }
      }
    } else if (element instanceof PsiReferenceExpression) {
      qualifier = ((PsiReferenceExpression) element).getQualifierExpression();
    } else if (element instanceof PsiMethodCallExpression) {
      final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
      qualifier = methodExpression.getQualifierExpression();
      final @NonNls String referenceName = methodExpression.getReferenceName();
      if (referenceName == null) return null;
    }
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type instanceof PsiClassType) {
        psiClass = ((PsiClassType)type).resolve();
      }

      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiElement resolved = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        if (resolved instanceof PsiClass) {
          if (psiClass == null) psiClass = (PsiClass)resolved;
        }
      }
    } else if (psiClass == null) {
      psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      allowOuterClasses = true;
    }

    if (psiClass instanceof PsiTypeParameter) {
      PsiClass[] supers = psiClass.getSupers();
      List<PsiClass> filtered = new ArrayList<PsiClass>();
      for (PsiClass aSuper : supers) {
        if (!aSuper.getManager().isInProject(aSuper)) continue;
        if (!(aSuper instanceof PsiTypeParameter)) filtered.add(aSuper);
      }
      return filtered.size() > 0 ? filtered.toArray(new PsiClass[filtered.size()]) : null;
    }
    else {
      if (psiClass == null || !psiClass.getManager().isInProject(psiClass)) {
        return null;
      }

      if (!allowOuterClasses ||
          !isAllowOuterTargetClass() ||
          ApplicationManager.getApplication().isUnitTestMode())
        return new PsiClass[]{psiClass};

      List<PsiClass> result = new ArrayList<PsiClass>();

      while (psiClass != null) {
        result.add(psiClass);
        if (psiClass.hasModifierProperty(PsiModifier.STATIC)) break;
        psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
      }
      return result.toArray(new PsiClass[result.size()]);
    }
  }

  protected static void startTemplate (final Editor editor, final Template template, final Project project) {
    Runnable runnable = new Runnable() {
      public void run() {
        TemplateManager.getInstance(project).startTemplate(editor, template);
      }
    };
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
    else {
      runnable.run();
    }
  }

  protected static void startTemplate (final Editor editor, final Template template, final Project project, final TemplateEditingListener listener) {
    Runnable runnable = new Runnable() {
      public void run() {
        TemplateManager.getInstance(project).startTemplate(editor, template, listener);
      }
    };
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
    else {
      runnable.run();
    }
  }
}
