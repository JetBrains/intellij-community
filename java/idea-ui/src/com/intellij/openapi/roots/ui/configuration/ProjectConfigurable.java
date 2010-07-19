/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.InsertPathAction;
import com.intellij.util.Alarm;
import com.intellij.util.Chunk;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 15, 2003
 */
public class ProjectConfigurable extends NamedConfigurable<Project> implements DetailsComponent.Facade {

  private final Project myProject;

  private static final Icon PROJECT_ICON = IconLoader.getIcon("/nodes/project.png");

  private boolean myStartModuleWizardOnShow;
  private LanguageLevelCombo myLanguageLevelCombo;
  private ProjectJdkConfigurable myProjectJdkConfigurable;

  private FieldPanel myProjectCompilerOutput;

  private MyJPanel myPanel;

  private final Alarm myUpdateWarningAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private final JLabel myWarningLabel = new JLabel("");
  private final ModulesConfigurator myModulesConfigurator;
  private JPanel myWholePanel;

  private boolean myFreeze = false;
  private DetailsComponent myDetailsComponent;

  public ProjectConfigurable(Project project, ModulesConfigurator configurator, ProjectSdksModel model) {
    myProject = project;
    myModulesConfigurator = configurator;
    init(model);
  }


  public DetailsComponent getDetailsComponent() {
    return myDetailsComponent;
  }

  public JComponent createOptionsPanel() {
    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setContent(myPanel);
    myDetailsComponent.setText(getBannerSlogan());

    myProjectJdkConfigurable.createComponent(); //reload changed jdks

    return myDetailsComponent.getComponent();
  }

  private void init(final ProjectSdksModel model) {
    myPanel = new MyJPanel();
    myPanel.setPreferredSize(new Dimension(700, 500));

    myProjectJdkConfigurable = new ProjectJdkConfigurable(myProject, model);
    myPanel.add(myProjectJdkConfigurable.createComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0,
                                                                                   GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                                                   new Insets(4, 4, 0, 0), 0, 0));

    myPanel.add(myWholePanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                                     GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));

    //myWarningLabel.setUI(new MultiLineLabelUI());
    myPanel.add(myWarningLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                                                       GridBagConstraints.BOTH, new Insets(10, 6, 10, 0), 0, 0));

    myProjectCompilerOutput.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        if (myFreeze) return;
        myModulesConfigurator.processModuleCompilerOutputChanged(getCompilerOutputUrl());
      }
    });
  }

  public void disposeUIResources() {
    myUpdateWarningAlarm.cancelAllRequests();
    if (myProjectJdkConfigurable != null) {
      myProjectJdkConfigurable.disposeUIResources();
    }
  }

  public void reset() {
    myFreeze = true;
    try {
      myProjectJdkConfigurable.reset();
      final String compilerOutput = CompilerProjectExtension.getInstance(myProject).getCompilerOutputUrl();
      if (compilerOutput != null) {
        myProjectCompilerOutput.setText(FileUtil.toSystemDependentName(VfsUtil.urlToPath(compilerOutput)));
      }
      myLanguageLevelCombo.reset(myProject);
      updateCircularDependencyWarning();
    }
    finally {
      myFreeze = false;
    }
  }

  void updateCircularDependencyWarning() {
    myUpdateWarningAlarm.cancelAllRequests();
    myUpdateWarningAlarm.addRequest(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable(){
          public void run() {
            final Graph<Chunk<ModifiableRootModel>> graph = ModuleCompilerUtil.toChunkGraph(myModulesConfigurator.createGraphGenerator());
            final Collection<Chunk<ModifiableRootModel>> chunks = graph.getNodes();
            String cycles = "";
            int count = 0;
            for (Chunk<ModifiableRootModel> chunk : chunks) {
              final Set<ModifiableRootModel> modules = chunk.getNodes();
              String cycle = "";
              for (ModifiableRootModel model : modules) {
                cycle += ", " + model.getModule().getName();
              }
              if (modules.size() > 1) {
                @NonNls final String br = "<br>&nbsp;&nbsp;&nbsp;&nbsp;";
                cycles += br + (++count) + ". " + cycle.substring(2);
              }
            }
            @NonNls final String leftBrace = "<html>";
            @NonNls final String rightBrace = "</html>";
            final String warningMessage =
              leftBrace + (count > 0 ? ProjectBundle.message("module.circular.dependency.warning", cycles, count) : "") + rightBrace;
            final int count1=count;
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                myWarningLabel.setIcon(count1 > 0 ? Messages.getWarningIcon() : null);
                myWarningLabel.setText(warningMessage);
                myWarningLabel.repaint();}
              }
            );
          }
        });
      }
    }, 300);
  }


  public void apply() throws ConfigurationException {
    final CompilerProjectExtension compilerProjectExtension = CompilerProjectExtension.getInstance(myProject);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        // set the output path first so that handlers of RootsChanged event sent after JDK is set
        // would see the updated path
        String canonicalPath = myProjectCompilerOutput.getText();
        if (canonicalPath != null && canonicalPath.length() > 0) {
          try {
            canonicalPath = FileUtil.resolveShortWindowsName(canonicalPath);
          }
          catch (IOException e) {
            //file doesn't exist yet
          }
          canonicalPath = FileUtil.toSystemIndependentName(canonicalPath);
          compilerProjectExtension.setCompilerOutputUrl(VfsUtil.pathToUrl(canonicalPath));
        }
        else {
          compilerProjectExtension.setCompilerOutputPointer(null);
        }

        final LanguageLevel newLevel = (LanguageLevel)myLanguageLevelCombo.getSelectedItem();
        LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(newLevel);
        try {
          myProjectJdkConfigurable.apply();
        }
        catch (ConfigurationException e) {
          //cant't be
        }
      }
    });
  }


  public void setDisplayName(final String name) {
    //do nothing
  }

  public Project getEditableObject() {
    return myProject;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.project.banner.text", myProject.getName());
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.project.display.name");
  }

  public Icon getIcon() {
    return PROJECT_ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.general";
  }


  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isModified() {
    if (!LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel().equals(myLanguageLevelCombo.getSelectedItem())) {
      return true;
    }
    final CompilerProjectExtension compilerProjectExtension = CompilerProjectExtension.getInstance(myProject);
    final String compilerOutput = compilerProjectExtension.getCompilerOutputUrl();
    if (!Comparing.strEqual(FileUtil.toSystemIndependentName(VfsUtil.urlToPath(compilerOutput)),
                            FileUtil.toSystemIndependentName(myProjectCompilerOutput.getText()))) return true;
    if (myProjectJdkConfigurable.isModified()) return true;
    return false;
  }

  private void createUIComponents() {
    myLanguageLevelCombo = new LanguageLevelCombo();
    final JTextField textField = new JTextField();
    final FileChooserDescriptor outputPathsChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);
    outputPathsChooserDescriptor.setHideIgnored(false);
    BrowseFilesListener listener = new BrowseFilesListener(textField, "", ProjectBundle.message("project.compiler.output"), outputPathsChooserDescriptor);
    myProjectCompilerOutput = new FieldPanel(textField, null, null, listener, EmptyRunnable.getInstance());
    FileChooserFactory.getInstance().installFileCompletion(myProjectCompilerOutput.getTextField(), outputPathsChooserDescriptor, true, null);
  }

  public void setStartModuleWizardOnShow(final boolean show) {
    myStartModuleWizardOnShow = show;
  }

  public String getCompilerOutputUrl() {
    return VfsUtil.pathToUrl(myProjectCompilerOutput.getText().trim());
  }

  private class MyJPanel extends JPanel {
    public MyJPanel() {
      super(new GridBagLayout());
    }

    public void addNotify() {
      super.addNotify();
      if (myStartModuleWizardOnShow) {
        final Window parentWindow = (Window)SwingUtilities.getAncestorOfClass(Window.class, this);
        parentWindow.addWindowListener(new WindowAdapter() {
          public void windowActivated(WindowEvent e) {
            parentWindow.removeWindowListener(this);
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                myModulesConfigurator.addModule(parentWindow);
              }
            });
          }
        });
      }
    }
  }


}
