/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.Map;

/**
 * @author Sergey Simonchik
 */
public class WebModuleGenerationStep extends ModuleWizardStep {

  private final ModuleBuilder myModuleBuilder;
  private final Icon myIcon;
  private final String myHelpId;
  private final Map<WebProjectGenerator, WebProjectGenerator.GeneratorPeer> myGeneratorPeers = ContainerUtil.newHashMap();
  private JComponent myRootComponent;
  private WebProjectGenerator myCurrentGenerator;
  private JPanel myRightPanel;

  public WebModuleGenerationStep(@NotNull ModuleBuilder moduleBuilder,
                                 @NotNull Icon icon,
                                 @NotNull String helpId) {
    myModuleBuilder = moduleBuilder;
    myIcon = icon;
    myHelpId = helpId;
  }

  @Override
  public JComponent getComponent() {
    if (myRootComponent == null) {
      myRootComponent = createComponent();
    }
    return myRootComponent;
  }

  @NotNull
  private JComponent createComponent() {
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    final JBList generatorList = new JBList();

    DirectoryProjectGenerator[] generators = Extensions.getExtensions(DirectoryProjectGenerator.EP_NAME);
    DefaultListModel listModel = new DefaultListModel();

    EmptyProjectGenerator emptyProjectGenerator = new EmptyProjectGenerator();
    listModel.addElement(emptyProjectGenerator);
    for (DirectoryProjectGenerator generator : generators) {
      if (generator instanceof WebProjectGenerator) {
        listModel.addElement(generator);
      }
    }
    generatorList.setModel(listModel);
    generatorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    generatorList.getExpandableItemsHandler().setEnabled(false);
    generatorList.setCellRenderer(new ListCellRendererWrapper<DirectoryProjectGenerator>() {
      @Override
      public void customize(JList list,
                            DirectoryProjectGenerator value,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        setText(" " + value.getName());
      }
    });
    generatorList.addListSelectionListener(new ListSelectionListener() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        @SuppressWarnings("unchecked")
        WebProjectGenerator<Object> selectedGenerator =
          (WebProjectGenerator<Object>) generatorList.getSelectedValue();
        myCurrentGenerator = selectedGenerator;
        showGenerator(selectedGenerator);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            generatorList.requestFocusInWindow();
          }
        });
      }
    });

    JPanel leftPanel = createLeftPanel(generatorList);
    splitPane.setLeftComponent(leftPanel);
    myRightPanel = new JPanel(new CardLayout());
    splitPane.setRightComponent(myRightPanel);

    generatorList.setSelectedValue(emptyProjectGenerator, true);
    return splitPane;
  }

  private <T> void showGenerator(@NotNull WebProjectGenerator<T> generator) {
    WebProjectGenerator.GeneratorPeer peer = myGeneratorPeers.get(generator);
    if (peer == null) {
      peer = generator.createPeer();
      JComponent component = peer.getComponent();
      JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 25));
      panel.add(component);
      myRightPanel.add(panel, generator.getName());
      myRightPanel.revalidate();
      myRightPanel.repaint();
      myGeneratorPeers.put(generator, peer);
    }
    CardLayout cardLayout = (CardLayout) myRightPanel.getLayout();
    cardLayout.show(myRightPanel, generator.getName());
  }

  @NotNull
  private static JPanel createLeftPanel(@NotNull JList generatorList) {
    JPanel panel = new JPanel();
    LayoutManager boxLayoutManager = new BoxLayout(panel, BoxLayout.Y_AXIS);
    panel.setLayout(boxLayoutManager);
    JLabel label = new JLabel("Select Web Module Type:");
    panel.add(label, Component.LEFT_ALIGNMENT);
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.add(Box.createVerticalStrut(5));
    JBScrollPane scrollPane = new JBScrollPane(
      generatorList,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    );
    Dimension listPrefSize = calcTypeListPreferredSize(generatorList);
    scrollPane.setPreferredSize(listPrefSize);
    panel.add(scrollPane, Component.LEFT_ALIGNMENT);
    scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    return panel;
  }

  @NotNull
  private static Dimension calcTypeListPreferredSize(@NotNull JList list) {
    FontMetrics fontMetrics = list.getFontMetrics(list.getFont());
    int fontHeight = fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent();
    int width = 0;
    int height = 0;
    ListModel listModel = list.getModel();
    int size = listModel.getSize();
    for (int i = 0; i < size; i++) {
      DirectoryProjectGenerator generator = (DirectoryProjectGenerator) listModel.getElementAt(i);
      height += fontHeight + 6;
      width = Math.max(width, fontMetrics.stringWidth(generator.getName()));
    }
    return new Dimension(width, height);
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (myCurrentGenerator == null) {
      throw new RuntimeException("Current generator should be not-null");
    }
    WebProjectGenerator.GeneratorPeer peer = myGeneratorPeers.get(myCurrentGenerator);
    if (peer == null) {
      throw new ConfigurationException("Peer should be not-null for " + myCurrentGenerator.getName());
    }
    ValidationInfo validate = peer.validate();
    if (validate != null) {
      throw new ConfigurationException(validate.message);
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void updateDataModel() {
    final WebProjectGenerator generator = myCurrentGenerator;
    if (generator == null) {
      throw new RuntimeException("Current generator should be not-null");
    }
    WebProjectGenerator.GeneratorPeer peer = myGeneratorPeers.get(myCurrentGenerator);
    if (peer == null) {
      throw new RuntimeException("Peer should be not-null for " + myCurrentGenerator.getName());
    }
    final Object settings = peer.getSettings();
    File dir = new File(myModuleBuilder.getModuleFileDirectory());
    final VirtualFile moduleDir = VfsUtil.findFileByIoFile(dir, true);
    if (moduleDir != null && moduleDir.isValid()) {
      myModuleBuilder.addListener(new ModuleBuilderListener() {
        @Override
        public void moduleCreated(@NotNull final Module module) {
          myModuleBuilder.removeListener(this);

          final Project project = module.getProject();
          final Runnable task = new Runnable() {
            @Override
            public void run() {
              generator.generateProject(project, moduleDir, settings, module);
            }
          };
          final boolean scheduledAsPostStartupActivity;
          StartupManagerEx startupManager = StartupManagerEx.getInstanceEx(project);
          //noinspection SynchronizationOnLocalVariableOrMethodParameter
          synchronized (startupManager) {
            scheduledAsPostStartupActivity = !startupManager.postStartupActivityPassed();
            if (scheduledAsPostStartupActivity) {
              startupManager.registerPostStartupActivity(task);
            }
          }
          if (!scheduledAsPostStartupActivity) {
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              public void run() {
                if (!project.isDisposed()) {
                  task.run();
                }
              }
            });
          }
        }
      });
    }
  }

  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public String getHelpId() {
    return myHelpId;
  }

  private static class EmptyProjectGenerator extends WebProjectGenerator<Object> {
    @Nls
    @Override
    public String getName() {
      return "Empty module";
    }

    @Override
    public String getDescription() {
      return "";
    }

    @Override
    public void generateProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Object settings, @NotNull Module module) {}

    @NotNull
    @Override
    public WebProjectGenerator.GeneratorPeer<Object> createPeer() {
      return new WebProjectGenerator.GeneratorPeer<Object>() {

        @NotNull
        @Override
        public JComponent getComponent() {
          return new JLabel("No extra files will be created.");
        }

        @Nullable
        @Override
        public Pair<String, JComponent> getSettingsField() {
          return null;
        }

        @NotNull
        @Override
        public Object getSettings() {
          return new Object();
        }

        @Override
        @Nullable
        public ValidationInfo validate() {
          return null;
        }

        @Override
        public void addSettingsStateListener(@NotNull WebProjectGenerator.SettingsStateListener listener) {}
      };
    }
  }

}
