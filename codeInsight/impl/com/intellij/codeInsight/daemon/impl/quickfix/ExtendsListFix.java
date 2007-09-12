package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExtendsListFix implements IntentionAction, LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.ExtendsListFix");

  final PsiClass myClass;
  final PsiClass myClassToExtendFrom;
  private final boolean myToAdd;
  private final PsiClassType myTypeToExtendFrom;

  public ExtendsListFix(PsiClass aClass, PsiClassType typeToExtendFrom, boolean toAdd) {
    myClass = aClass;
    myClassToExtendFrom = typeToExtendFrom.resolve();
    myTypeToExtendFrom = typeToExtendFrom;
    myToAdd = toAdd;
  }

  public ExtendsListFix(PsiClass aClass, PsiClass classToExtendFrom, boolean toAdd) {
    myClass = aClass;
    myClassToExtendFrom = classToExtendFrom;
    myTypeToExtendFrom = aClass.getManager().getElementFactory().createType(classToExtendFrom);
    myToAdd = toAdd;
  }

  @NotNull
  public String getText() {
    @NonNls final String messageKey;
    if (myClass.isInterface() == myClassToExtendFrom.isInterface()) {
      messageKey = myToAdd ? "add.class.to.extends.list" : "remove.class.from.extends.list";
    }
    else {
      messageKey = myToAdd ? "add.interface.to.implements.list" : "remove.interface.from.implements.list";
    }

    return QuickFixBundle.message(messageKey, myClass.getName(), myClassToExtendFrom.getQualifiedName());
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.extends.list.family");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    invoke(project, null, descriptor.getPsiElement().getContainingFile());
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
        myClass != null
        && myClass.isValid()
        && myClass.getManager().isInProject(myClass)
        && myClassToExtendFrom != null
        && myClassToExtendFrom.isValid()
        && !myClassToExtendFrom.hasModifierProperty(PsiModifier.FINAL)
        && (myClassToExtendFrom.isInterface()
            || (!myClass.isInterface()
                && myClass.getExtendsList() != null
                && myClass.getExtendsList().getReferencedTypes().length == 0 == myToAdd))
        ;

  }

  protected void invokeImpl () {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;
    PsiReferenceList extendsList = !(myClass instanceof PsiTypeParameter) &&
                                   myClass.isInterface() != myClassToExtendFrom.isInterface() ?
                                   myClass.getImplementsList() : myClass.getExtendsList();
    PsiReferenceList otherList = extendsList == myClass.getImplementsList() ?
                                 myClass.getExtendsList() : myClass.getImplementsList();
    try {
      if (extendsList != null) {
        modifyList(extendsList, myToAdd, -1);
      }
      if (otherList != null) {
        modifyList(otherList, false, -1);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    invokeImpl();
    UndoManager.getInstance(file.getProject()).markDocumentForUndo(file);
  }

  /**
   * @param position to add new class to or -1 if add to the end
   */
  PsiReferenceList modifyList(@NotNull PsiReferenceList extendsList, boolean add, int position) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
    boolean alreadyExtends = false;
    for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
      if (referenceElement.resolve() == myClassToExtendFrom) {
        alreadyExtends = true;
        if (!add) {
          referenceElement.delete();
        }
      }
    }
    PsiReferenceList list = extendsList;
    if (add && !alreadyExtends) {
      PsiElement anchor;
      if (position == -1) {
        anchor = referenceElements.length ==0 ? null : referenceElements[referenceElements.length-1];
      }
      else if (position == 0) {
        anchor = null;
      }
      else {
        anchor = referenceElements[position - 1];
      }
      PsiJavaCodeReferenceElement classReferenceElement = myClass.getManager().getElementFactory().createReferenceElementByType(myTypeToExtendFrom);
      PsiElement element;
      if (anchor == null) {
        if (referenceElements.length == 0) {
          element = extendsList.add(classReferenceElement);
        }
        else {
          element = extendsList.addBefore(classReferenceElement, referenceElements[0]);
        }
      }
      else {
        element = extendsList.addAfter(classReferenceElement, anchor);
      }
      list = (PsiReferenceList) element.getParent();
    }
    return list;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
