/*
 * User: anna
 * Date: 14-May-2007
 */
package com.intellij.execution.junit2.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.execution.util.EnvironmentVariable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentVariablesComponent extends LabeledComponent<TextFieldWithBrowseButton> {
  private boolean myPassParentEnvs;
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

  public void setEnvs(String envs) {
    getComponent().setText(envs);
  }

  public String getEnvs() {
    return getComponent().getText();
  }

  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  public void setPassParentEnvs(final boolean passDefaultVariables) {
    myPassParentEnvs = passDefaultVariables;
  }

  private static void envsFromString(final String envs, final Map<String, String> envsMap) {
    if (envs == null || envs.length() == 0) return;
    final String[] envVars = envs.split(";");
    for (String envVar : envVars) {
      final String[] nameValue = envVar.split("=");
      if (nameValue != null && nameValue.length > 0) {
        envsMap.put(nameValue[0], nameValue.length > 1 ? nameValue[1] : "");
      }
    }
  }

  public static void setupEnvs(JavaParameters javaParameters, String envs, boolean passDefault) {
    final HashMap<String, String> envsMap = new HashMap<String, String>();
    envsFromString(envs, envsMap);
    if (!envsMap.isEmpty()) {
      javaParameters.setEnv(envsMap);
      javaParameters.setPassParentEnvs(passDefault);
    }
  }


  private class MyEnvironmentVariablesDialog extends DialogWrapper {
    private final EnvVariablesTable myEnvVariablesTable;
    private final JCheckBox myUseDefaultCb = new JCheckBox(ExecutionBundle.message("env.vars.checkbox.title"));
    private final JPanel myWholePanel = new JPanel(new BorderLayout());

    protected MyEnvironmentVariablesDialog() {
      super(EnvironmentVariablesComponent.this, true);
      myEnvVariablesTable = new EnvVariablesTable();
      final List<EnvironmentVariable> envVariables = new ArrayList<EnvironmentVariable>();
      final HashMap<String, String> envsMap = new HashMap<String, String>();
      envsFromString(getComponent().getText(), envsMap);
      for (String envVariable : envsMap.keySet()) {
        envVariables.add(new EnvironmentVariable(envVariable, envsMap.get(envVariable), false));
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
      @NonNls final StringBuilder buf = StringBuilderSpinAllocator.alloc();
      try {
        for (EnvironmentVariable variable : myEnvVariablesTable.getEnvironmentVariables()) {
          buf.append(variable.getName()).append("=").append(variable.getValue()).append(";");
        }
        if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1); //trim last ;
        setEnvs(buf.toString());
        setPassParentEnvs(myUseDefaultCb.isSelected());
      }
      finally {
        StringBuilderSpinAllocator.dispose(buf);
      }
      super.doOKAction();
    }
  }
}