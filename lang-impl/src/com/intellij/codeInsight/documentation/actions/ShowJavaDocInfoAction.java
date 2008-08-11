package com.intellij.codeInsight.documentation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.Arrays;

public class ShowJavaDocInfoAction extends BaseCodeInsightAction implements HintManager.ActionToIgnore {
  @NonNls public static final String CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE = "codeassists.quickjavadoc.lookup";
  @NonNls public static final String CODEASSISTS_QUICKJAVADOC_FEATURE = "codeassists.quickjavadoc";

  public ShowJavaDocInfoAction() {
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      public void invoke(Project project, Editor editor, PsiFile file) {
        DocumentationManager.getInstance(project).showJavaDocInfo(editor, file, true);
      }

      public boolean startInWriteAction() {
        return false;
      }
    };
  }


  protected boolean isValidForLookup() {
    return true;
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (editor == null && element == null) {
      presentation.setEnabled(false);
      return;
    }

    if (LookupManager.getInstance(project).getActiveLookup() != null) {
      if (!isValidForLookup()) {
        presentation.setEnabled(false);
      }
      else {
        presentation.setEnabled(true);
      }
    }
    else {
      if (editor != null) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == null) {
          presentation.setEnabled(false);
        }

        if (element == null && file != null) {
          final PsiReference ref = file.findReferenceAt(editor.getCaretModel().getOffset());
          if (ref instanceof PsiPolyVariantReference) {
            element = ref.getElement();
          }
        }
      }

      if (element != null) {
        presentation.setEnabled(true);
      }
    }
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);

    if (project != null && editor != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKJAVADOC_FEATURE);
      final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(project).getActiveLookup();
      if (lookup != null) {
        dumpLookupElementWeights(lookup);
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE);
      }
      actionPerformedImpl(project, editor);
    }
    else if (project != null) {
      if (DocumentationManager.getProviderFromElement(element) != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.quickjavadoc.ctrln");
        CommandProcessor.getInstance().executeCommand(project,
                                                      new Runnable() {
                                                        public void run() {
                                                          DocumentationManager.getInstance(project).showJavaDocInfo(element);
                                                        }
                                                      },
                                                      getCommandName(),
                                                      null);
      }
    }
  }

  private static void dumpLookupElementWeights(final LookupImpl lookup) {
    if (((ApplicationEx)ApplicationManager.getApplication()).isInternal()) {
      final ListModel model = lookup.getList().getModel();
      final int count = lookup.getPreferredItemsCount();
      for (int i = 0; i < model.getSize(); i++) {
        final LookupElement item = (LookupElement)model.getElementAt(i);
        System.out.println(item.getLookupString() + Arrays.toString(item.getUserData(LookupItem.WEIGHT)));
        if (i == count - 1) {
          System.out.println("------------");
        }
      }
    }
  }
}
