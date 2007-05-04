package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


/**
 * @author ven
 */
public class CreateConstructorMatchingSuperFix extends BaseIntentionAction {
  private Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorMatchingSuperFix");

  private PsiClass myClass;

  public CreateConstructorMatchingSuperFix(PsiClass aClass) {
    myClass = aClass;
  }

  public String getFamilyName() {
    return QuickFixBundle.message("create.constructor.matching.super");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!myClass.isValid() || !myClass.getManager().isInProject(myClass)) return false;
    setText(QuickFixBundle.message("create.constructor.matching.super"));
    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;
    PsiClass baseClass = myClass.getSuperClass();
    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, myClass, PsiSubstitutor.EMPTY);
    LOG.assertTrue(baseClass != null);
    List<PsiMethodMember> baseConstructors = new ArrayList<PsiMethodMember>();
    PsiMethod[] baseConstrs = baseClass.getConstructors();
    for (PsiMethod baseConstr : baseConstrs) {
      if (PsiUtil.isAccessible(baseConstr, myClass, myClass)) baseConstructors.add(new PsiMethodMember(baseConstr, substitutor));
    }

    PsiMethodMember[] constructors = baseConstructors.toArray(new PsiMethodMember[baseConstructors.size()]);
    if (constructors.length == 0) {
      constructors = new PsiMethodMember[baseConstrs.length];
      for (int i = 0; i < baseConstrs.length; i++) {
        constructors[i] = new PsiMethodMember(baseConstrs[i], substitutor);
      }
    }

    LOG.assertTrue(constructors.length >=1); // Otherwise we won't have been messing with all this stuff
    boolean isCopyJavadoc = true;
    if (constructors.length > 1) {
      MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(constructors, false, true, project);
      chooser.setTitle(QuickFixBundle.message("super.class.constructors.chooser.title"));
      chooser.show();
      if (chooser.getExitCode() != MemberChooser.OK_EXIT_CODE) return;
      constructors = chooser.getSelectedElements(new PsiMethodMember[0]);
      isCopyJavadoc = chooser.isCopyJavadoc();
    }

    final PsiMethodMember[] constructors1 = constructors;
    final boolean isCopyJavadoc1 = isCopyJavadoc;
    ApplicationManager.getApplication().runWriteAction (
      new Runnable() {
        public void run() {
          try {
            PsiElementFactory factory = myClass.getManager().getElementFactory();
            CodeStyleManager reformatter = CodeStyleManager.getInstance(project);
            PsiMethod derived = null;
            for (PsiMethodMember candidate : constructors1) {
              PsiMethod base = (PsiMethod)candidate.getElement();
              derived = GenerateMembersUtil.substituteGenericMethod(base, candidate.getSubstitutor());

              if (!isCopyJavadoc1) {
                final PsiDocComment docComment = derived.getDocComment();
                if (docComment != null) {
                  docComment.delete();
                }
              }

              derived.getNameIdentifier().replace(myClass.getNameIdentifier());
              @NonNls StringBuffer buffer = new StringBuffer();
              buffer.append("void foo () {\nsuper(");

              PsiParameter[] params = derived.getParameterList().getParameters();
              for (int j = 0; j < params.length; j++) {
                PsiParameter param = params[j];
                buffer.append(param.getName());
                if (j < params.length - 1) buffer.append(",");
              }
              buffer.append(");\n}");
              PsiMethod stub = factory.createMethodFromText(buffer.toString(), myClass);

              derived.getBody().replace(stub.getBody());
              derived = (PsiMethod)reformatter.reformat(derived);
              derived = (PsiMethod)myClass.add(derived);
            }
            if (derived != null) {
              editor.getCaretModel().moveToOffset(derived.getTextRange().getStartOffset());
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }

          UndoManager.getInstance(myClass.getContainingFile().getProject()).markDocumentForUndo(myClass.getContainingFile());
        }
      }
    );
  }

  public boolean startInWriteAction() {
    return false;
  }
}
