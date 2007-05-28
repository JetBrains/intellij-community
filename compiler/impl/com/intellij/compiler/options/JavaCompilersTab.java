package com.intellij.compiler.options;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class JavaCompilersTab implements Configurable{
  private JPanel myPanel;
  private JPanel myContentPanel;
  private JComboBox myCompiler;
  private CardLayout myCardLayout;

  private BackendCompiler myDefaultCompiler;
  private BackendCompiler mySelectedCompiler;
  private CompilerConfigurationImpl myCompilerConfiguration;
  private final Collection<Configurable> myConfigurables;

  public JavaCompilersTab(final Project project, Collection<BackendCompiler> compilers, BackendCompiler defaultCompiler) {
    myDefaultCompiler = defaultCompiler;
    myCompilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    myConfigurables = new ArrayList<Configurable>(compilers.size());

    myCardLayout = new CardLayout();
    myContentPanel.setLayout(myCardLayout);

    for (BackendCompiler compiler : compilers) {
      Configurable configurable = compiler.createConfigurable();
      myConfigurables.add(configurable);

      myContentPanel.add(configurable.createComponent(), compiler.getId());
    }
    myCompiler.setModel(new DefaultComboBoxModel(new Vector(compilers)));
    myCompiler.setRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel component = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        component.setText(((BackendCompiler)value).getPresentableName());
        return component;
      }
    });
    myCompiler.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        BackendCompiler compiler = (BackendCompiler)myCompiler.getSelectedItem();
        if (compiler == null) return;
        selectCompiler(compiler);
      }
    });
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    if (!Comparing.equal(mySelectedCompiler, myCompilerConfiguration.getDefaultCompiler())) {
      return true;
    }
    for (Configurable configurable : myConfigurables) {
      if (configurable.isModified()) {
        return true;
      }
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (Configurable configurable : myConfigurables) {
      configurable.apply();
    }
    myCompilerConfiguration.setDefaultCompiler(mySelectedCompiler);
  }

  public void reset() {
    for (Configurable configurable : myConfigurables) {
      configurable.reset();
    }
    selectCompiler(myCompilerConfiguration.getDefaultCompiler());
  }

  public void disposeUIResources() {
  }

  private void selectCompiler(BackendCompiler compiler) {
    if(compiler == null) {
      compiler = myDefaultCompiler;
    }
    myCompiler.setSelectedItem(compiler);
    mySelectedCompiler = compiler;
    myCardLayout.show(myContentPanel, compiler.getId());
    myContentPanel.revalidate();
    myContentPanel.repaint();
  }
}
