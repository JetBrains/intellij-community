package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ModifierFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix");

  private final PsiModifierList myModifierList;
  private final String myModifier;
  private final boolean myShouldHave;
  private boolean myShowContainingClass;

  public ModifierFix(PsiModifierList modifierList, String modifier, boolean shouldHave, boolean showContainingClass) {
    myModifierList = modifierList;
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
  }

  @NotNull
  public String getText() {
    String name = null;
    PsiElement parent = myModifierList.getParent();
    if (parent instanceof PsiClass) {
      name = ((PsiClass)parent).getName();
    }
    else if (parent instanceof PsiMethod) {
      name = PsiFormatUtil.formatMethod((PsiMethod)parent,
                                        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME |
                                                              (myShowContainingClass
                                                               ? PsiFormatUtil.SHOW_CONTAINING_CLASS
                                                               : 0),
                                        0);
    }
    else if (parent instanceof PsiVariable) {
      name =
      PsiFormatUtil.formatVariable((PsiVariable)parent,
                                   PsiFormatUtil.SHOW_NAME |
                                   (myShowContainingClass ? PsiFormatUtil.SHOW_CONTAINING_CLASS : 0),
                                   PsiSubstitutor.EMPTY);
    }
    else if (parent instanceof PsiClassInitializer) {
      PsiClass containingClass = ((PsiClassInitializer)parent).getContainingClass();
      String className = containingClass instanceof PsiAnonymousClass ?
                         QuickFixBundle.message("anonymous.class.presentation",
                                                ((PsiAnonymousClass)containingClass).getBaseClassType().getPresentableText())
                         : containingClass.getName();
      name = QuickFixBundle.message("class.initializer.presentation", className);
    }

    final String modifierText = myModifier.equals(PsiModifier.PACKAGE_LOCAL)
                                ? QuickFixBundle.message("package.local.visibility.presentation")
                                : myModifier;

    return QuickFixBundle.message(myShouldHave ? "add.modifier.fix" : "remove.modifier.fix",
                                  name,
                                  modifierText);
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.modifiers.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myModifierList != null
           && myModifierList.isValid()
           && myModifierList.getManager().isInProject(myModifierList)
           && myModifier != null;
  }

  private void changeModifierList (PsiModifierList modifierList) {
    try {
      modifierList.setModifierProperty(myModifier, myShouldHave);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void invoke(@NotNull Project project, Editor editor, final PsiFile file) {

    final List<PsiModifierList> modifierLists = new ArrayList<PsiModifierList>();
    PsiElement owner = myModifierList.getParent();
    if (owner instanceof PsiMethod) {
      PsiSearchHelper helper = PsiManager.getInstance(project).getSearchHelper();
      PsiModifierList copy = (PsiModifierList)myModifierList.copy();
      changeModifierList(copy);
      final int accessLevel = PsiUtil.getAccessLevel(copy);

      helper.processOverridingMethods(new PsiElementProcessor<PsiMethod>() {
        public boolean execute(PsiMethod inheritor) {
          PsiModifierList list = inheritor.getModifierList();
          if (inheritor.getManager().isInProject(inheritor) && PsiUtil.getAccessLevel(list) < accessLevel) {
            modifierLists.add(list);
          }
          return true;
        }
      }, (PsiMethod)owner, owner.getResolveScope(), true);
    }

    if (!CodeInsightUtil.prepareFileForWrite(myModifierList.getContainingFile())) return;

    if (!modifierLists.isEmpty()) {
      if (Messages.showYesNoDialog(project,
                                   QuickFixBundle.message("change.inheritors.visibility.warning.text"),
                                   QuickFixBundle.message("change.inheritors.visibility.warning.title"),
                                   Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            for (final PsiModifierList modifierList : modifierLists) {
              changeModifierList(modifierList);
            }
          }
        });
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        changeModifierList(myModifierList);
        UndoManager.getInstance(file.getProject()).markDocumentForUndo(file);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

}
