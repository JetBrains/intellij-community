package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CreatePropertyFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeQuickFix");
  private final PsiElement myElement;
  private final String myKey;
  private final String myBundleName;
  public static final String NAME = QuickFixBundle.message("create.property.quickfix.text");

  public CreatePropertyFix(PsiElement element, String key, final String bundleName) {
    myElement = element;
    myKey = key;
    myBundleName = bundleName;
  }

  public String getFamilyName() {
    return getText();
  }

  public String getText() {
    return NAME;
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myElement.isValid();
  }

  public void invoke(final Project project, Editor editor, @NotNull PsiFile file) throws IncorrectOperationException {
    PsiLiteralExpression literalExpression = myElement instanceof PsiLiteralExpression ? (PsiLiteralExpression)myElement : null;
    final I18nizeQuickFixDialog dialog = new I18nizeQuickFixDialog(project, file, literalExpression, "", false, false) {
      protected void init() {
        super.init();
        setTitle(NAME);
      }

      public JComponent getPreferredFocusedComponent() {
        return getValueComponent();
      }

      protected List<String> suggestPropertiesFiles() {
        List<PropertiesFile> propertiesFiles = I18nUtil.propertiesFilesByBundleName(myBundleName, myElement);
        if (propertiesFiles.isEmpty()) {
          return super.suggestPropertiesFiles();
        }
        ArrayList<String> list = new ArrayList<String>();
        for (PropertiesFile propertiesFile : propertiesFiles) {
          list.add(propertiesFile.getVirtualFile().getPath());
        }
        return list;
      }

      // do not suggest existing keys
      @NotNull
      protected List<String> getExistingValueKeys(String value) {
        return Collections.emptyList();
      }

      protected String suggestPropertyKey(String value) {
        return myKey;
      }
    };
    dialog.show();
    if (!dialog.isOK()) return;
    final String key = dialog.getKey();
    final String value = dialog.getValue();

    final Collection<PropertiesFile> selectedPropertiesFiles = dialog.getAllPropertiesFiles();
    invokeAction(project, selectedPropertiesFiles, key, value);

  }

  public void invokeAction(final Project project,
                            final Collection<PropertiesFile> selectedPropertiesFiles,
                            final String key,
                            final String value) {
    for (PropertiesFile selectedFile : selectedPropertiesFiles) {
      if (!CodeInsightUtil.prepareFileForWrite(selectedFile)) return;
    }
    UndoManager.getInstance(project).markDocumentForUndo(myElement.getContainingFile());

    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable(){
          public void run() {
            try {
              I18nizeQuickFix.createProperty(project, selectedPropertiesFiles, key, value);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }, CodeInsightBundle.message("quickfix.i18n.command.name"),project);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

  //tests only
  public String getKey() {
    return myKey;
  }
}
