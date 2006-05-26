package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Belyaev & Eugene Zhuravlev
 */
public class DebuggerConfigurable extends CompositeConfigurable implements ApplicationComponent, SearchableConfigurable {
  public DebuggerConfigurable() {
    super();
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableDebugger.png");
  }

  public String getDisplayName() {
    return DebuggerBundle.message("debugger.configurable.display.name");
  }

  public String getHelpTopic() {
    return "project.propDebugger";
  }

  public String getComponentName() {
    return "DebuggerConfigurable";
  }

  protected List<Configurable> createConfigurables() {
    ArrayList<Configurable> configurables = new ArrayList<Configurable>();
    Project project = (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);
    if(project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    configurables.add(new DebuggerGeneralConfigurable(project));
    configurables.add(new UserRenderersConfigurable(project));
    return configurables;
  }

  public void apply() throws ConfigurationException {
    super.apply();
    NodeRendererSettings.getInstance().fireRenderersChanged();
  }

  @NonNls
  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}