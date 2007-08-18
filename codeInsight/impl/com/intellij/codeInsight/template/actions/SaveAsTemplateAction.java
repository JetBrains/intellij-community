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
import com.intellij.codeInsight.template.impl.EditTemplateDialog;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SaveAsTemplateAction extends AnAction {
  public static final @NonNls String JAVA_LANG_PACKAGE_PREFIX = "java.lang";

  public SaveAsTemplateAction() {
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Editor editor = DataKeys.EDITOR.getData(dataContext);
    PsiFile file = DataKeys.PSI_FILE.getData(dataContext);

    Project project = file.getProject();
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
        if (!(element instanceof PsiJavaCodeReferenceElement)) return false;
        if (!selection.contains(element.getTextRange())) return false;
        PsiElement ref = ((PsiJavaCodeReferenceElement)element).resolve();
        if (!(ref instanceof PsiClass)) return false;
        PsiClass psiClass = (PsiClass)ref;
        if (!(psiClass.getParent() instanceof PsiJavaFile)) return false;
        PsiDirectory directory = PsiTreeUtil.getParentOfType(psiClass, PsiDirectory.class);
        if (directory.getPackage().getQualifiedName().equals(JAVA_LANG_PACKAGE_PREFIX)) return false;
        return true;
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
            Map rangeToClass = new HashMap();

            for (int i = 0; i < psiElements.length; i++) {
              PsiElement element = psiElements[i];
              TextRange textRange = element.getTextRange();
              rangeToClass.put(document.createRangeMarker(
                element.getTextRange().getStartOffset() - offsetDelta,
                textRange.getEndOffset() - offsetDelta),
                               ((PsiJavaCodeReferenceElement)element).resolve());
            }

            Set ranges = rangeToClass.keySet();
            for (Iterator i = ranges.iterator(); i.hasNext();) {
              RangeMarker textRange = (RangeMarker)i.next();
              PsiClass psiClass = (PsiClass)rangeToClass.get(textRange);
              document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), psiClass.getQualifiedName());
            }
          }
        });
      }
    }, null, null);

    TemplateSettings templateSettings = TemplateSettings.getInstance();

    TemplateImpl template = new TemplateImpl("", document.getText(), TemplateSettings.USER_GROUP_NAME);

    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file.getVirtualFile());
    if (fileType == StdFileTypes.HTML) {
      template.getTemplateContext().HTML = true;
      template.getTemplateContext().JAVA_CODE = false;
    }
    else if (fileType == StdFileTypes.XML) {
      template.getTemplateContext().XML = true;
      template.getTemplateContext().JAVA_CODE = false;
    }
    else if (fileType == StdFileTypes.JSP) {
      template.getTemplateContext().JSP = true;
      template.getTemplateContext().JAVA_CODE = false;
    }
    else if (fileType != StdFileTypes.JAVA) {
      template.getTemplateContext().OTHER = true;
      template.getTemplateContext().JAVA_CODE = false;
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
      templateSettings.getTemplates(),
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
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    PsiFile file = DataKeys.PSI_FILE.getData(dataContext);

    if (file == null || editor == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(editor.getSelectionModel().hasSelection());
    }
  }
}
