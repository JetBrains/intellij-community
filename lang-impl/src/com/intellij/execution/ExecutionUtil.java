package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Function;

import javax.swing.*;

/**
 * @author spleaner
 */
public class ExecutionUtil {

  private static final Icon INVALID_CONFIGURATION = IconLoader.getIcon("/runConfigurations/invalidConfigurationLayer.png");

  private ExecutionUtil() {
  }

  public static Icon getConfigurationIcon(final Project project, final RunnerAndConfigurationSettings settings, final boolean invalid) {
    final RunManager runManager = RunManager.getInstance(project);
    RunConfiguration configuration = settings.getConfiguration();
    final Icon icon = settings.getFactory().getIcon(configuration);
    final Icon configurationIcon = runManager.isTemporary(configuration) ? IconLoader.getTransparentIcon(icon, 0.3f) : icon;
    if (invalid) {
      return LayeredIcon.create(configurationIcon, INVALID_CONFIGURATION);
    }

    return configurationIcon;
  }

  public static Icon getConfigurationIcon(final Project project, final RunnerAndConfigurationSettings settings) {
    final Icon baseIcon = getConfigurationIcon(project, settings, false);
    return new DeferredIconImpl<RunnerAndConfigurationSettings>(baseIcon, settings, new Function<RunnerAndConfigurationSettings, Icon>() {
      public Icon fun(RunnerAndConfigurationSettings runnerAndConfigurationSettings) {
        try {
          settings.checkSettings();
          return baseIcon;
        }
        catch (RuntimeConfigurationException ex) {
          return getConfigurationIcon(project, settings, true);
        }
      }
    });
  }

  public static String shortenName(final String name, final int toBeAdded) {
    if (name == null) return "";
    final int symbols = Math.max(10, 20 - toBeAdded);
    if (name.length() < symbols) return name;
    else return name.substring(0, symbols) + "...";
  }

}
