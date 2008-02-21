/*
 * User: anna
 * Date: 14-May-2007
 */
package com.intellij.execution.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.execution.util.EnvironmentVariable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentVariablesComponent extends LabeledComponent<TextFieldWithBrowseButton> {
  private boolean myPassParentEnvs;
  private Map<String, String> myEnvs;
  @NonNls private static final String ENVS = "envs";
  @NonNls private static final String ENV = "env";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String VALUE = "value";
  @NonNls private static final String OPTION = "option";
  @NonNls private static final String ENV_VARIABLES = "ENV_VARIABLES";

  public EnvironmentVariablesComponent() {
    super();
    setComponent(new TextFieldWithBrowseButton());
    setText(ExecutionBundle.message("environment.variables.component.title"));
    getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        new MyEnvironmentVariablesDialog().show();
      }
    });
  }

  public void setEnvs(Map<String, String> envs) {
    myEnvs = envs;
    @NonNls final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      for (String variable : myEnvs.keySet()) {
        buf.append(variable).append("=").append(myEnvs.get(variable)).append(";");
      }
      if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1); //trim last ;
      getComponent().setText(buf.toString());
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  public Map<String, String> getEnvs() {
    return myEnvs;
  }

  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  public void setPassParentEnvs(final boolean passDefaultVariables) {
    myPassParentEnvs = passDefaultVariables;
  }

  public static void readExternal(Element element, Map<String, String> envs) {
    final Element envsElement = element.getChild(ENVS);
    if (envsElement != null) {
      for (Object o : envsElement.getChildren(ENV)) {
        Element envElement = (Element)o;
        final String envName = envElement.getAttributeValue(NAME);
        final String envValue = envElement.getAttributeValue(VALUE);
        if (envName != null && envValue != null) {
          envs.put(envName, envValue);
        }
      }
    } else { //compatibility
      for (Object o : element.getChildren(OPTION)) {
        if (Comparing.strEqual(((Element)o).getAttributeValue(NAME), ENV_VARIABLES)) {
          final String val = ((Element)o).getAttributeValue(VALUE);
          if (val != null) {
            final String[] envVars = val.split(";");
            if (envVars != null) {
              for (String envVar : envVars) {
                final int idx = envVar.indexOf('=');
                if (idx > -1) {
                  envs.put(envVar.substring(0, idx), idx < envVar.length() - 1 ? envVar.substring(idx + 1) : "");
                }
              }
            }
          }
          break;
        }
      }
    }
  }

  public static void writeExternal(Element element, Map<String, String> envs) {
    final Element envsElement = new Element(ENVS);
    for (String envName : envs.keySet()) {
      final Element envElement = new Element(ENV);
      envElement.setAttribute(NAME, envName);
      envElement.setAttribute(VALUE, envs.get(envName));
      envsElement.addContent(envElement);
    }
    element.addContent(envsElement);
  }

  private class MyEnvironmentVariablesDialog extends DialogWrapper {
    private final EnvVariablesTable myEnvVariablesTable;
    private final JCheckBox myUseDefaultCb = new JCheckBox(ExecutionBundle.message("env.vars.checkbox.title"));
    private final JPanel myWholePanel = new JPanel(new BorderLayout());

    protected MyEnvironmentVariablesDialog() {
      super(EnvironmentVariablesComponent.this, true);
      myEnvVariablesTable = new EnvVariablesTable();
      final List<EnvironmentVariable> envVariables = new ArrayList<EnvironmentVariable>();
      for (String envVariable : myEnvs.keySet()) {
        envVariables.add(new EnvironmentVariable(envVariable, myEnvs.get(envVariable), false));
      }
      myEnvVariablesTable.setValues(envVariables);
      myUseDefaultCb.setSelected(isPassParentEnvs());
      myWholePanel.add(myEnvVariablesTable.getComponent(), BorderLayout.CENTER);
      myWholePanel.add(myUseDefaultCb, BorderLayout.SOUTH);
      setTitle(ExecutionBundle.message("environment.variables.dialog.title"));
      init();
    }

    @Nullable
    protected JComponent createCenterPanel() {
      return myWholePanel;
    }

    protected void doOKAction() {
      final Map<String, String> envs = new LinkedHashMap<String, String>();
      for (EnvironmentVariable variable : myEnvVariablesTable.getEnvironmentVariables()) {
        envs.put(variable.getName(), variable.getValue());
      }
      setEnvs(envs);
      setPassParentEnvs(myUseDefaultCb.isSelected());
      super.doOKAction();
    }
  }
}