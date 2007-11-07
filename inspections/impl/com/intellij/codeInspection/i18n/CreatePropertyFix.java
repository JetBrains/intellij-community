package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CreatePropertyFix implements IntentionAction, LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeQuickFix");
  private PsiElement myElement;
  private String myKey;
  private List<PropertiesFile> myPropertiesFiles;

  public static final String NAME = QuickFixBundle.message("create.property.quickfix.text");

  public CreatePropertyFix() {
  }

  public CreatePropertyFix(PsiElement element, String key, final List<PropertiesFile> propertiesFiles) {
    myElement = element;
    myKey = key;
    myPropertiesFiles = propertiesFiles;
  }

  @NotNull
  public String getName() {
    return NAME;
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (isAvailable(project, null, null)) {
      invoke(project, null, psiElement.getContainingFile());
    }
  }

  @NotNull
  public String getText() {
    return NAME;
  }

  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @Nullable PsiFile file) {
    return myElement.isValid();
  }

  public void invoke(@NotNull final Project project, @Nullable Editor editor, @NotNull PsiFile file) {
    invokeAction(project, file, myElement, myKey, null, myPropertiesFiles);
  }

  @Nullable
  protected static Pair<String, String> invokeAction(@NotNull final Project project,
                                                     @NotNull PsiFile file,
                                                     @NotNull PsiElement psiElement,
                                                     @Nullable final String suggestedKey,
                                                     @Nullable String suggestedValue,
                                                     @Nullable final List<PropertiesFile> propertiesFiles) {
    final PsiLiteralExpression literalExpression = psiElement instanceof PsiLiteralExpression ? (PsiLiteralExpression)psiElement : null;
    final String propertyValue = suggestedValue == null ? "" : suggestedValue;

    final I18nizeQuickFixDialog dialog = new I18nizeQuickFixDialog(project, file, literalExpression, propertyValue, false, false) {
      protected void init() {
        super.init();
        setTitle(NAME);
      }

      public JComponent getPreferredFocusedComponent() {
        return getValueComponent();
      }

      protected List<String> suggestPropertiesFiles() {
        if (propertiesFiles == null || propertiesFiles.isEmpty()) {
          return super.suggestPropertiesFiles();
        }
        ArrayList<String> list = new ArrayList<String>();
        for (PropertiesFile propertiesFile : propertiesFiles) {
          final VirtualFile virtualFile = propertiesFile.getVirtualFile();
          if (virtualFile != null) {
            list.add(virtualFile.getPath());
          }
        }
        return list;
      }

      // do not suggest existing keys
      @NotNull
      protected List<String> getExistingValueKeys(String value) {
        return Collections.emptyList();
      }

      @NotNull
      protected String suggestPropertyKey(String value) {
        return suggestedKey == null ? "" : suggestedKey;
      }
    };
    dialog.show();
    if (!dialog.isOK()) return null;
    final String key = dialog.getKey();
    final String value = dialog.getValue();

    final Collection<PropertiesFile> selectedPropertiesFiles = dialog.getAllPropertiesFiles();
    createProperty(project, psiElement, selectedPropertiesFiles, key, value);

    return new Pair<String, String>(key, value);
  }

  public static void createProperty(@NotNull final Project project,
                                    @NotNull final PsiElement psiElement,
                                    @NotNull final Collection<PropertiesFile> selectedPropertiesFiles,
                                    @NotNull final String key,
                                    @NotNull final String value) {
    for (PropertiesFile selectedFile : selectedPropertiesFiles) {
      if (!CodeInsightUtil.prepareFileForWrite(selectedFile)) return;
    }
    UndoUtil.markPsiFileForUndo(psiElement.getContainingFile());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            try {
              I18nUtil.createProperty(project, selectedPropertiesFiles, key, value);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }, CodeInsightBundle.message("quickfix.i18n.command.name"), project);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }
}
