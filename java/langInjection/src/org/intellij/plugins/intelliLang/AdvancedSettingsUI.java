// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.plugins.intelliLang;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Gregory.Shrago
 */
public class AdvancedSettingsUI implements SearchableConfigurable {
  private final Configuration.AdvancedConfiguration myConfiguration;
  private AdvancedSettingsPanel myPanel;
  private final Project myProject;

  public AdvancedSettingsUI(final @NotNull Project project, Configuration configuration) {
    myProject = project;
    myConfiguration = configuration.getAdvancedConfiguration();
  }

  @Override
  public JComponent createComponent() {
    myPanel = new AdvancedSettingsPanel(myProject, myConfiguration);
    return myPanel.content;
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.content.apply();
  }

  @Override
  public boolean isModified() {
    return myPanel.content.isModified();
  }

  @Override
  public void reset() {
    myPanel.content.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  @Override
  public String getDisplayName() {
    return IntelliLangBundle.message("configurable.AdvancedSettingsUI.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.injection.advanced";
  }

  @Override
  public @NotNull String getId() {
    return "IntelliLang.Advanced";
  }

  static final class BrowseClassListener implements ActionListener {
    private final Project myProject;
    private final ReferenceEditorWithBrowseButton myField;

    BrowseClassListener(Project project, ReferenceEditorWithBrowseButton annotationField) {
      myProject = project;
      myField = annotationField;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);

      final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(myField.getText(), scope);
      final TreeClassChooser chooser =
        factory.createNoInnerClassesScopeChooser(IntelliLangBundle.message("dialog.title.select.annotation.class"), scope, new ClassFilter() {
          @Override
          public boolean isAccepted(PsiClass aClass) {
            return aClass.isAnnotationType();
          }
        }, aClass);

      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelected();
      if (psiClass != null) {
        myField.setText(psiClass.getQualifiedName());
      }
    }
  }
}
