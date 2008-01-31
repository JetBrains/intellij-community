/*
 * User: anna
 * Date: 31-Jan-2008
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.DialogUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public class ImportPopupHectorComponentProvider implements HectorComponentPanelsProvider {

  public HectorComponentPanel createConfigurable(@NotNull final PsiFile file) {
    final Project project = file.getProject();
    final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
    final boolean notInLibrary =
      !fileIndex.isInLibrarySource(virtualFile) && !fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInContent(virtualFile);
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<Language> languages = viewProvider.getPrimaryLanguages();

    return new HectorComponentPanel() {
      private JCheckBox myImportPopupCheckBox = new JCheckBox(EditorBundle.message("hector.import.popup.checkbox"));
      public JComponent createComponent() {
        DialogUtil.registerMnemonic(myImportPopupCheckBox);
        return myImportPopupCheckBox;
      }

      public boolean isModified() {
        return myImportPopupCheckBox.isSelected() != analyzer.isImportHintsEnabled(file);
      }

      public void apply() throws ConfigurationException {
        analyzer.setImportHintsEnabled(file, myImportPopupCheckBox.isSelected());
      }

      public void reset() {
        myImportPopupCheckBox.setSelected(analyzer.isImportHintsEnabled(file));
        myImportPopupCheckBox.setEnabled(analyzer.isAutohintsAvailable(file));
        myImportPopupCheckBox.setVisible(notInLibrary && languages.contains(StdLanguages.JAVA));
      }

      public void disposeUIResources() {
        myImportPopupCheckBox = null;
      }
    };
  }

}