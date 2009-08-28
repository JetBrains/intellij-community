package com.intellij.application.options.editor;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class CodeFoldingConfigurable extends CompositeConfigurable<CodeFoldingOptionsProvider> implements EditorOptionsProvider {
  private JCheckBox myCbFolding;
  private JPanel myRootPanel;
  private JPanel myFoldingPanel;

  @Nls
  public String getDisplayName() {
    return ApplicationBundle.message("group.code.folding");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.code.folding";
  }

  public JComponent createComponent() {
    for (CodeFoldingOptionsProvider provider : getConfigurables()) {
      myFoldingPanel.add(provider.createComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST,
                                                                            GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0));
    }
    return myRootPanel;
  }

  public boolean isModified() {
    return myCbFolding.isSelected() != EditorSettingsExternalizable.getInstance().isFoldingOutlineShown() ||
           super.isModified();
  }

  public void apply() throws ConfigurationException {
    EditorSettingsExternalizable.getInstance().setFoldingOutlineShown(myCbFolding.isSelected());
    super.apply();

    for (final Editor editor : EditorFactory.getInstance().getAllEditors()) {
      final Project project = editor.getProject();
      if (project != null && !project.isDefault()) {
        final CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
        if (foldingManager != null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              foldingManager.forceDefaultState(editor);
            }
          }, ModalityState.NON_MODAL);
        }
      }
    }
  }

  public void reset() {
    myCbFolding.setSelected(EditorSettingsExternalizable.getInstance().isFoldingOutlineShown());
    super.reset();
  }

  protected List<CodeFoldingOptionsProvider> createConfigurables() {
    return Arrays.asList(Extensions.getExtensions(CodeFoldingOptionsProvider.EP_NAME));
  }

  public String getId() {
    return "editor.preferences.folding";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }
}
