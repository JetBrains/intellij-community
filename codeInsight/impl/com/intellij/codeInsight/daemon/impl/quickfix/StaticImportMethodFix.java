package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiProximityComparator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class StaticImportMethodFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix");
  private final PsiMethodCallExpression myMethodCall;
  private List<PsiMethod> candidates;

  public StaticImportMethodFix(PsiMethodCallExpression methodCallExpression) {
    myMethodCall = methodCallExpression;
  }

  @NotNull
  public String getText() {
    String text = QuickFixBundle.message("static.import.method.text");
    if (candidates.size() == 1) {
      final int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS  | PsiFormatUtil.SHOW_FQ_NAME;
      text += " '" + PsiFormatUtil.formatMethod(candidates.get(0), PsiSubstitutor.EMPTY, options, 0)+"'";
    }
    else {
      text += "...";
    }
    return text;
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(file)) <= 0
           && myMethodCall != null 
           && myMethodCall.isValid()
           && myMethodCall.getMethodExpression().getQualifierExpression() == null
           && file.getManager().isInProject(file)
           && !(candidates == null ? candidates = getMethodsToImport() : candidates).isEmpty()
      ;
  }

  @NotNull
  private List<PsiMethod> getMethodsToImport() {
    PsiShortNamesCache cache = myMethodCall.getManager().getShortNamesCache();
    PsiReferenceExpression reference = myMethodCall.getMethodExpression();
    PsiExpressionList argumentList = myMethodCall.getArgumentList();
    String name = reference.getReferenceName();
    ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    if (name == null) return list;
    GlobalSearchScope scope = myMethodCall.getResolveScope();
    PsiMethod[] methods = cache.getMethodsByNameIfNotMoreThan(name, scope, 20);
    List<PsiMethod> applicableList = new ArrayList<PsiMethod>();
    for (PsiMethod method : methods) {
      ProgressManager.getInstance().checkCanceled();
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && CompletionUtil.isInExcludedPackage(aClass)) continue;
      if (!method.hasModifierProperty(PsiModifier.STATIC)) continue;
      PsiFile file = method.getContainingFile();
      if (file instanceof PsiJavaFile
          //do not show methods from default package
          && ((PsiJavaFile)file).getPackageName().length() != 0
          && PsiUtil.isAccessible(method, myMethodCall, aClass)) {
        list.add(method);
        if (PsiUtil.isApplicable(method, PsiSubstitutor.EMPTY, argumentList)) {
          applicableList.add(method);
        }
      }
    }
    List<PsiMethod> result = applicableList.isEmpty() ? list : applicableList;
    Collections.sort(result, new PsiProximityComparator(argumentList, argumentList.getProject()));
    return result;
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    if (candidates.size() == 1) {
      final PsiMethod toImport = candidates.get(0);
      doImport(toImport);
    }
    else {
      chooseAndImport(editor);
    }
  }

  private void doImport(final PsiMethod toImport) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(toImport.getProject(), new Runnable(){
          public void run() {
            try {
              myMethodCall.getMethodExpression().bindToElementViaStaticImport(toImport.getContainingClass());
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }, getText(), this);
      }
    });
  }

  private void chooseAndImport(Editor editor) {
    final JList list = new JList(new Vector<PsiMethod>(candidates));
    list.setCellRenderer(new MethodCellRenderer(true));
    new PopupChooserBuilder(list).
      setTitle(QuickFixBundle.message("static.import.method.choose.method.to.import")).
      setMovable(true).
      setItemChoosenCallback(new Runnable() {
        public void run() {
          PsiMethod selectedValue = (PsiMethod)list.getSelectedValue();
          if (selectedValue == null) return;
          LOG.assertTrue(selectedValue.isValid());
          doImport(selectedValue);
        }
      }).createPopup().
      showInBestPositionFor(editor);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
