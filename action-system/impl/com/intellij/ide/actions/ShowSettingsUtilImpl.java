package com.intellij.ide.actions;

import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.ProjectCodeStyleConfigurable;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ControlPanelSettingsEditor;
import com.intellij.openapi.options.ex.ExplorerSettingsEditor;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author max
 */
public class ShowSettingsUtilImpl extends ShowSettingsUtil {
  @NonNls
  private static final String PREFER_CLASSIC_OPTIONS_EDITOR = "PREFER_CLASSIC_OPTIONS_EDITOR";

  public void showSettingsDialog(Project project, ConfigurableGroup[] group) {
    if (Boolean.toString(true).equals(PropertiesComponent.getInstance().getValue(PREFER_CLASSIC_OPTIONS_EDITOR))) {
      showExplorerOptions(project, group);
    }
    else {
      showControlPanelOptions(project, group, null);
    }
  }

  public void showControlPanelOptions(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    PropertiesComponent.getInstance().setValue(PREFER_CLASSIC_OPTIONS_EDITOR, Boolean.toString(false));

    ControlPanelSettingsEditor editor = new ControlPanelSettingsEditor(project, groups, preselectedConfigurable);
    editor.show();
  }

  public void showExplorerOptions(Project project, ConfigurableGroup[] group) {
    PropertiesComponent.getInstance().setValue(PREFER_CLASSIC_OPTIONS_EDITOR, Boolean.toString(true));
    ExplorerSettingsEditor editor = new ExplorerSettingsEditor(project, group);
    editor.show();
  }

  public boolean editConfigurable(Project project, Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(project, configurable, createDimensionKey(configurable));
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public <T extends Configurable> T findApplicationConfigurable(final Class<T> confClass) {
    return selectConfigurable(confClass, ApplicationManager.getApplication().getExtensions(Configurable.APPLICATION_CONFIGURABLES));
  }

  public <T extends Configurable> T findProjectConfigurable(final Project project, final Class<T> confClass) {
    return selectConfigurable(confClass, project.getExtensions(Configurable.PROJECT_CONFIGURABLES));
  }

  private static <T extends Configurable> T selectConfigurable(final Class<T> confClass, final Configurable... configurables) {
    for (Configurable configurable : configurables) {
      if (confClass.isAssignableFrom(configurable.getClass())) return (T)configurable;
    }

    throw new IllegalStateException("Can't find configurable of class " + confClass.getName());
  }

  public boolean editConfigurable(Project project, String dimensionServiceKey, Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(project, configurable, dimensionServiceKey);
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public boolean editConfigurable(Component parent, Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(parent, configurable, createDimensionKey(configurable));
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public boolean editConfigurable(final Component parent, final Configurable configurable, final Runnable advancedInitialization) {
    SingleConfigurableEditor editor = new SingleConfigurableEditor(parent, configurable, createDimensionKey(configurable));
    advancedInitialization.run();
    editor.show();
    return editor.isOK();
  }

  private static String createDimensionKey(Configurable configurable) {
    String displayName = configurable.getDisplayName();
    displayName = displayName.replaceAll("\n", "_").replaceAll(" ", "_");
    return "#" + displayName;
  }

  public boolean editConfigurable(Component parent, String dimensionServiceKey,Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(parent, configurable, dimensionServiceKey);
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public boolean editConfigurable(Project project, Configurable configurable, Runnable advancedInitialization) {
    SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable, createDimensionKey(configurable));
    advancedInitialization.run();
    editor.show();
    return editor.isOK();
  }

  /**
   * Shows code style settings sutable for the project passed. I.e. it shows project code style page if one
   * is configured to use own code style scheme or global one in other case.
   * @param project
   * @return Returns true if settings were modified during editing session.
   */
  public boolean showCodeStyleSettings(Project project, final Class pageToSelect) {
    CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    boolean usePerProject = settingsManager.USE_PER_PROJECT_SETTINGS;
    CodeStyleSettings savedSettings = (CodeStyleSettings)settingsManager.getCurrentSettings().clone();
    if (usePerProject) {
      final ProjectCodeStyleConfigurable configurable = ProjectCodeStyleConfigurable.getInstance(project);
      Runnable selectPage = new Runnable() {
        public void run() {
          if (pageToSelect != null) {
            configurable.selectPage(pageToSelect);
          }
        }
      };
      editConfigurable(project, configurable, selectPage);
    }
    else {
      final CodeStyleSchemesConfigurable configurable = CodeStyleSchemesConfigurable.getInstance();
      Runnable selectPage = new Runnable() {
        public void run() {
          if (pageToSelect != null) {
            configurable.selectPage(pageToSelect);
          }
        }
      };
      editConfigurable(project, configurable, selectPage);
    }

    return !savedSettings.equals(settingsManager.getCurrentSettings());
  }

}
