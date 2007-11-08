package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author max
 */
public class ProjectConfigurableWrapper implements SearchableConfigurable {
  private Project myProject;
  private Configurable myDelegate;

  public ProjectConfigurableWrapper(Project project, Configurable delegate) {
    myProject = project;
    myDelegate = delegate;
  }

  public String getDisplayName() {
    return myDelegate.getDisplayName();
  }

  public void reset() {
    myDelegate.reset();
  }

  public void apply() throws ConfigurationException {
    checkProjectFileWritable();
    myDelegate.apply();
  }

  private void checkProjectFileWritable() {
    String path = myProject.getPresentableUrl();
    if (path != null) {
      File file = new File(path);
      if (file.exists() && !file.canWrite()) {
        Messages.showMessageDialog(
          myProject,
          OptionsBundle.message("project.file.read.only.error.message"),
          OptionsBundle.message("cannot.save.settings.default.dialog.title"),
          Messages.getErrorIcon()
        );
      }
    }
  }

  public String getHelpTopic() {
    return myDelegate.getHelpTopic();
  }

  public void disposeUIResources() {
    myDelegate.disposeUIResources();
  }

  public boolean isModified() {
    return myDelegate.isModified();
  }

  public JComponent createComponent() {
    return myDelegate.createComponent();
  }

  public Icon getIcon() {
    return myDelegate.getIcon();
  }

  @NonNls
  public String getId() {
    return myDelegate instanceof SearchableConfigurable ? ((SearchableConfigurable)myDelegate).getId() : "";
  }

  public boolean clearSearch() {
    return myDelegate instanceof SearchableConfigurable && ((SearchableConfigurable)myDelegate).clearSearch();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return myDelegate instanceof SearchableConfigurable ? ((SearchableConfigurable)myDelegate).enableSearch(option) : null;  
  }
}
