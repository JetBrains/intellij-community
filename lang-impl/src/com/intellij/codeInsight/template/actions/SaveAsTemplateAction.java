/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 5:04:04 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.template.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.OtherContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.EditTemplateDialog;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

public class SaveAsTemplateAction extends AnAction {
  public static final @NonNls String JAVA_LANG_PACKAGE_PREFIX = "java.lang";

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);

    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final TextRange selection = new TextRange(editor.getSelectionModel().getSelectionStart(),
                                              editor.getSelectionModel().getSelectionEnd());
    PsiElement current = file.findElementAt(selection.getStartOffset());
    int startOffset = selection.getStartOffset();
    while (current instanceof PsiWhiteSpace) {
      current = current.getNextSibling();
      if (current == null) break;
      startOffset = current.getTextRange().getStartOffset();
    }

    if (startOffset >= selection.getEndOffset()) startOffset = selection.getStartOffset();

    final PsiElement[] psiElements = PsiTreeUtil.collectElements(file, new PsiElementFilter() {
      public boolean isAccepted(PsiElement element) {
        return selection.contains(element.getTextRange()) && element.getReferences().length > 0;
      }
    });

    final Document document = EditorFactory.getInstance().createDocument(editor.getDocument().getText().
                                                                         substring(startOffset,
                                                                                   selection.getEndOffset()));
    final int offsetDelta = startOffset;
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Map<RangeMarker, String> rangeToText = new HashMap<RangeMarker, String>();

            for (PsiElement element : psiElements) {
              for (PsiReference reference : element.getReferences()) {
                if (!(reference instanceof PsiQualifiedReference) || ((PsiQualifiedReference) reference).getQualifier() == null) {
                  String canonicalText = reference.getCanonicalText();
                  TextRange referenceRange = reference.getRangeInElement();
                  TextRange range = element.getTextRange().cutOut(referenceRange).shiftRight(-offsetDelta);
                  final String oldText = document.getText().substring(range.getStartOffset(), range.getEndOffset());
                  if (!canonicalText.equals(oldText)) {
                    rangeToText.put(document.createRangeMarker(range), canonicalText);
                  }
                }
              }
            }

            for (Map.Entry<RangeMarker, String> entry : rangeToText.entrySet()) {
              document.replaceString(entry.getKey().getStartOffset(), entry.getKey().getEndOffset(), entry.getValue());
            }
          }
        });
      }
    }, null, null);

    TemplateSettings templateSettings = TemplateSettings.getInstance();

    TemplateImpl template = new TemplateImpl("", document.getText(), TemplateSettings.USER_GROUP_NAME);

    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file.getVirtualFile());
    boolean anyApplicable = false;
    for(TemplateContextType contextType: Extensions.getExtensions(TemplateContextType.EP_NAME)) {
      if (contextType.isInContext(fileType)) {
        contextType.setEnabled(template.getTemplateContext(), true);
        anyApplicable = true;
      }
    }
    if (!anyApplicable) {
      new OtherContextType().setEnabled(template.getTemplateContext(), true);
    }

    String defaultShortcut = "";
    if (templateSettings.getDefaultShortcutChar() == TemplateSettings.ENTER_CHAR) {
      defaultShortcut = CodeInsightBundle.message("template.shortcut.enter");
    }
    if (templateSettings.getDefaultShortcutChar() == TemplateSettings.TAB_CHAR) {
      defaultShortcut = CodeInsightBundle.message("template.shortcut.tab");
    }
    if (templateSettings.getDefaultShortcutChar() == TemplateSettings.SPACE_CHAR) {
      defaultShortcut = CodeInsightBundle.message("template.shortcut.space");
    }

    EditTemplateDialog dialog = new EditTemplateDialog(
      editor.getComponent(),
      CodeInsightBundle.message("dialog.edit.live.template.title"),
      template,
      templateSettings.getTemplateGroups(),
      defaultShortcut);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    dialog.apply();
    templateSettings.addTemplate(template);
    templateSettings.setLastSelectedTemplateKey(template.getKey());
  }

  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);

    if (file == null || editor == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(editor.getSelectionModel().hasSelection());
    }
  }
}
