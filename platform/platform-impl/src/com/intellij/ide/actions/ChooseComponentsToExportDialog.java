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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FieldPanel;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

public class ChooseComponentsToExportDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ChooseComponentsToExportDialog");

  private final ElementsChooser<ComponentElementProperties> myChooser;
  private final FieldPanel myPathPanel;
  @NonNls
  public static final String DEFAULT_PATH = FileUtil.toSystemDependentName(PathManager.getConfigPath()+"/"+"settings.jar");
  private final boolean myShowFilePath;
  private final String myDescription;

  public ChooseComponentsToExportDialog(List<ExportableComponent> components,
                                        Map<File, Set<ExportableComponent>> fileToComponents,
                                        boolean showFilePath, final String title, String description) {
    super(false);
    myDescription = description;
    myShowFilePath = showFilePath;
    Map<ExportableComponent, ComponentElementProperties> componentToContainingListElement = new LinkedHashMap<ExportableComponent, ComponentElementProperties>();

    for (ExportableComponent component : components) {
      if (!addToExistingListElement(component, componentToContainingListElement, fileToComponents)) {
        ComponentElementProperties componentElementProperties = new ComponentElementProperties();
        componentElementProperties.addComponent(component);

        componentToContainingListElement.put(component, componentElementProperties);
      }
    }
    final Set<ComponentElementProperties> componentElementProperties = new LinkedHashSet<ComponentElementProperties>(componentToContainingListElement.values());
    myChooser = new ElementsChooser<ComponentElementProperties>(true);
    myChooser.setColorUnmarkedElements(false);
    for (final ComponentElementProperties componentElementProperty : componentElementProperties) {
      myChooser.addElement(componentElementProperty, true, componentElementProperty);
    }
    myChooser.sort(new Comparator<ComponentElementProperties>() {
      @Override
      public int compare(ComponentElementProperties o1,
                         ComponentElementProperties o2) {
        return o1.toString().compareTo(o2.toString());
      }
    });

    final ActionListener browseAction = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        chooseSettingsFile(myPathPanel.getText(), getWindow(), IdeBundle.message("title.export.file.location"), IdeBundle.message("prompt.choose.export.settings.file.path"))
          .doWhenDone(new Consumer<String>() {
            @Override
            public void consume(String path) {
              myPathPanel.setText(FileUtil.toSystemDependentName(path));
            }
          });
      }
    };

    myPathPanel = new FieldPanel(IdeBundle.message("editbox.export.settings.to"), null, browseAction, null);

    String exportPath = PropertiesComponent.getInstance().getOrInit("export.settings.path", DEFAULT_PATH);
    myPathPanel.setText(exportPath);
    myPathPanel.setChangeListener(new Runnable() {
      @Override
      public void run() {
        updateControls();
      }
    });
    updateControls();

    setTitle(title);
    init();
  }

  private void updateControls() {
    setOKActionEnabled(!StringUtil.isEmptyOrSpaces(myPathPanel.getText()));    
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    AbstractAction selectAll = new AbstractAction("Select &All") {
      @Override
      public void actionPerformed(ActionEvent e) {
        myChooser.setAllElementsMarked(true);
      }
    };
    AbstractAction selectNone = new AbstractAction("Select &None") {
      @Override
      public void actionPerformed(ActionEvent e) {
        myChooser.setAllElementsMarked(false);
      }
    };
    AbstractAction invert = new AbstractAction("&Invert") {
      @Override
      public void actionPerformed(ActionEvent e) {
        myChooser.invertSelection();
      }
    };
    return new Action[]{selectAll, selectNone, invert};
  }

  @Override
  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue("export.settings.path", myPathPanel.getText());
    super.doOKAction();
  }

  private static boolean addToExistingListElement(ExportableComponent component,
                                           Map<ExportableComponent,ComponentElementProperties> componentToContainingListElement,
                                           Map<File, Set<ExportableComponent>> fileToComponents) {
    final File[] exportFiles = component.getExportFiles();
    File file = null;
    for (File exportFile : exportFiles) {
      final Set<ExportableComponent> tiedComponents = fileToComponents.get(exportFile);

      for (final ExportableComponent tiedComponent : tiedComponents) {
        if (tiedComponent == component) continue;
        final ComponentElementProperties elementProperties = componentToContainingListElement.get(tiedComponent);
        if (elementProperties != null && !FileUtil.filesEqual(exportFile, file)) {
          LOG.assertTrue(file == null, "Component " + component + " serialize itself into " + file + " and " + exportFile);
          // found
          elementProperties.addComponent(component);
          componentToContainingListElement.put(component, elementProperties);
          file = exportFile;
        }
      }
    }
    return file != null;
  }

  @NotNull
  public static AsyncResult<String> chooseSettingsFile(String oldPath, Component parent, final String title, final String description) {
    FileChooserDescriptor chooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    chooserDescriptor.setDescription(description);
    chooserDescriptor.setHideIgnored(false);
    chooserDescriptor.setTitle(title);

    VirtualFile initialDir;
    if (oldPath != null) {
      final File oldFile = new File(oldPath);
      initialDir = LocalFileSystem.getInstance().findFileByIoFile(oldFile);
      if (initialDir == null && oldFile.getParentFile() != null) {
        initialDir = LocalFileSystem.getInstance().findFileByIoFile(oldFile.getParentFile());
      }
    }
    else {
      initialDir = null;
    }
    final AsyncResult<String> result = new AsyncResult<String>();
    FileChooser.chooseFiles(chooserDescriptor, null, parent, initialDir, new FileChooser.FileChooserConsumer() {
      @Override
      public void consume(List<VirtualFile> files) {
        VirtualFile file = files.get(0);
        if (file.isDirectory()) {
          result.setDone(file.getPath() + '/' + new File(DEFAULT_PATH).getName());
        }
        else {
          result.setDone(file.getPath());
        }
      }

      @Override
      public void cancelled() {
        result.setRejected();
      }
    });
    return result;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPathPanel.getTextField();
  }

  @Override
  protected JComponent createNorthPanel() {
    return new JLabel(myDescription);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myChooser;
  }

  @Override
  protected JComponent createSouthPanel() {
    final JComponent buttons = super.createSouthPanel();
    if (!myShowFilePath) return buttons;
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.add(myPathPanel);
    panel.add(buttons);
    return panel;
  }

  Set<ExportableComponent> getExportableComponents() {
    final List<ComponentElementProperties> markedElements = myChooser.getMarkedElements();
    final Set<ExportableComponent> components = new HashSet<ExportableComponent>();
    for (ComponentElementProperties elementProperties : markedElements) {
      components.addAll(elementProperties.myComponents);
    }
    return components;
  }

  private static class ComponentElementProperties implements ElementsChooser.ElementProperties {
    private final Set<ExportableComponent> myComponents = new HashSet<ExportableComponent>();

    private boolean addComponent(ExportableComponent component) {
      return myComponents.add(component);
    }

    @Override
    @Nullable
    public Icon getIcon() {
      return null;
    }

    @Override
    @Nullable
    public Color getColor() {
      return null;
    }

    public String toString() {
      Set<String> names = new LinkedHashSet<String>();
      
      for (final ExportableComponent component : myComponents) {
        names.add(component.getPresentableName());
      }

      return StringUtil.join(names.toArray(new String[names.size()]), ", ");
    }
  }

  File getExportFile() {
    return new File(myPathPanel.getText());
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.actions.ChooseComponentsToExportDialog";
  }
}
