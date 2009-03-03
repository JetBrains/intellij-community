package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.ide.DataManager;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;

import java.util.*;

public class PopupActionChooser {
  private final String myTitle;
  private boolean hasEnabledSurrounders;

  public PopupActionChooser(String title) {
    myTitle = title;
  }

  public void invoke(final Project project, final Editor editor, final PsiFile file, final Surrounder[] surrounders, final PsiElement[] elements){
    final DefaultActionGroup applicable = new DefaultActionGroup();
    hasEnabledSurrounders = false;

    Set<Character> usedMnemonicsSet = new HashSet<Character>();

    int index = 0;
    for (Surrounder surrounder : surrounders) {
      if (surrounder.isApplicable(elements)) {
        char mnemonic;
        if (index < 9) {
          mnemonic = (char)('0' + index + 1);
        }
        else if (index == 9) {
          mnemonic = '0';
        }
        else {
          mnemonic = (char)('A' + index - 10);
        }
        index++;
        usedMnemonicsSet.add(Character.toUpperCase(mnemonic));
        applicable.add(new InvokeSurrounderAction(surrounder, project, editor, elements, mnemonic));
        hasEnabledSurrounders = true;
      }
    }

    int offset = editor.getCaretModel().getOffset();
    TemplateContextType contextType = TemplateManager.getInstance(project).getContextType(file, offset);
    TemplateImpl[] templates = TemplateSettings.getInstance().getTemplates();
    ArrayList<TemplateImpl> array = new ArrayList<TemplateImpl>();
    for (TemplateImpl template : templates) {
      if (template.isDeactivated()) continue;
      if (template.getTemplateContext().isEnabled(contextType) && template.isSelectionTemplate()) {
        array.add(template);
      }
    }

    Collections.sort(array, new Comparator<TemplateImpl>() {
      public int compare(TemplateImpl o1, TemplateImpl o2) {
        return o1.getKey().compareTo(o2.getKey());
      }
    });

    if (!array.isEmpty()) {
      applicable.addSeparator("Live templates");
    }

    for (TemplateImpl template : array) {
      applicable.add(new InvokeTemplateAction(template, editor, project, usedMnemonicsSet));
      hasEnabledSurrounders = true;
    }

    if (hasEnabledSurrounders) {
      DataContext context = DataManager.getInstance().getDataContext(editor.getContentComponent());
      final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(myTitle,
                               applicable,
                               context,
                               JBPopupFactory.ActionSelectionAid.MNEMONICS, 
                               true);
      popup.showInBestPositionFor(editor);
    }
  }

  private static class InvokeSurrounderAction extends AnAction {
    private final Surrounder mySurrounder;
    private final Project myProject;
    private final Editor myEditor;
    private final PsiElement[] myElements;

    public InvokeSurrounderAction(Surrounder surrounder, Project project, Editor editor, PsiElement[] elements, char mnemonic) {
      super(UIUtil.MNEMONIC + String.valueOf(mnemonic) + ". " + surrounder.getTemplateDescription());
      mySurrounder = surrounder;
      myProject = project;
      myEditor = editor;
      myElements = elements;
    }


    public void actionPerformed(AnActionEvent e) {
      CommandProcessor.getInstance().executeCommand(
          myProject, new Runnable(){
          public void run(){
            final Runnable action = new Runnable(){
              public void run(){
                SurroundWithHandler.doSurround(myProject, myEditor, mySurrounder, myElements);
              }
            };
            ApplicationManager.getApplication().runWriteAction(action);
          }
        },
        null,
        null
      );
    }
  }

  public boolean isHasEnabledSurrounders() {
    return hasEnabledSurrounders;
  }
}
