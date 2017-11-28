/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.configurationStore;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FieldPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class ChooseComponentsToExportDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(ChooseComponentsToExportDialog.class);

  private final ElementsChooser<ComponentElementProperties> myChooser;
  private final FieldPanel myPathPanel;
  @NonNls
  public static final String DEFAULT_PATH = FileUtil.toSystemDependentName(PathManager.getConfigPath()+"/"+"settings.jar");
  private final boolean myShowFilePath;
  private final String myDescription;

  public ChooseComponentsToExportDialog(@NotNull Map<Path, List<ExportableItem>> fileToComponents,
                                        boolean showFilePath, final String title, String description) {
    super(false);

    myDescription = description;
    myShowFilePath = showFilePath;
    Map<ExportableItem, ComponentElementProperties> componentToContainingListElement = new LinkedHashMap<>();
    for (List<ExportableItem> list : fileToComponents.values()) {
      for (ExportableItem item : list) {
        if (!addToExistingListElement(item, componentToContainingListElement, fileToComponents)) {
          ComponentElementProperties componentElementProperties = new ComponentElementProperties();
          componentElementProperties.items.add(item);

          componentToContainingListElement.put(item, componentElementProperties);
        }
      }
    }
    myChooser = new ElementsChooser<>(true);
    myChooser.setColorUnmarkedElements(false);
    for (ComponentElementProperties componentElementProperty : new LinkedHashSet<>(componentToContainingListElement.values())) {
      myChooser.addElement(componentElementProperty, true, componentElementProperty);
    }
    myChooser.sort(Comparator.comparing(ComponentElementProperties::toString));

    final ActionListener browseAction = new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        chooseSettingsFile(myPathPanel.getText(), getWindow(), IdeBundle.message("title.export.file.location"), IdeBundle.message("prompt.choose.export.settings.file.path"))
          .done(path -> myPathPanel.setText(FileUtil.toSystemDependentName(path)));
      }
    };

    myPathPanel = new FieldPanel(IdeBundle.message("editbox.export.settings.to"), null, browseAction, null);

    String exportPath = PropertiesComponent.getInstance().getValue("export.settings.path", DEFAULT_PATH);
    myPathPanel.setText(exportPath);
    myPathPanel.setChangeListener(this::updateControls);
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
      public void actionPerformed(@NotNull ActionEvent e) {
        myChooser.setAllElementsMarked(true);
      }
    };
    AbstractAction selectNone = new AbstractAction("Select &None") {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        myChooser.setAllElementsMarked(false);
      }
    };
    AbstractAction invert = new AbstractAction("&Invert") {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        myChooser.invertSelection();
      }
    };
    return new Action[]{selectAll, selectNone, invert};
  }

  @Override
  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue("export.settings.path", myPathPanel.getText(), DEFAULT_PATH);
    super.doOKAction();
  }

  private static boolean addToExistingListElement(@NotNull ExportableItem item,
                                                  @NotNull Map<ExportableItem, ComponentElementProperties> itemToContainingListElement,
                                                  @NotNull Map<Path, List<ExportableItem>> fileToItem) {
    List<ExportableItem> list = fileToItem.get(item.getFile());
    if (ContainerUtil.isEmpty(list)) {
      return false;
    }

    Path file = null;
    for (ExportableItem tiedItem : list) {
      if (tiedItem == item) {
        continue;
      }

      final ComponentElementProperties elementProperties = itemToContainingListElement.get(tiedItem);
      if (elementProperties != null && item.getFile() != file) {
        LOG.assertTrue(file == null, "Component " + item + " serialize itself into " + file + " and " + item.getFile());
        // found
        elementProperties.items.add(item);
        itemToContainingListElement.put(item, elementProperties);
        file = item.getFile();
      }
    }
    return file != null;
  }

  @NotNull
  public static Promise<String> chooseSettingsFile(String oldPath, Component parent, final String title, final String description) {
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
    final AsyncPromise<String> result = new AsyncPromise<>();
    FileChooser.chooseFiles(chooserDescriptor, null, parent, initialDir, new FileChooser.FileChooserConsumer() {
      @Override
      public void consume(List<VirtualFile> files) {
        VirtualFile file = files.get(0);
        if (file.isDirectory()) {
          result.setResult(file.getPath() + '/' + new File(DEFAULT_PATH).getName());
        }
        else {
          result.setResult(file.getPath());
        }
      }

      @Override
      public void cancelled() {
        result.setError("");
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

  Set<ExportableItem> getExportableComponents() {
    Set<ExportableItem> components = new THashSet<>();
    for (ComponentElementProperties elementProperties : myChooser.getMarkedElements()) {
      components.addAll(elementProperties.items);
    }
    return components;
  }

  private static class ComponentElementProperties implements ElementsChooser.ElementProperties {
    private final Set<ExportableItem> items = new THashSet<>();

    public String toString() {
      Set<String> names = new LinkedHashSet<>();
      for (ExportableItem component : items) {
        names.add(component.getPresentableName());
      }
      return StringUtil.join(ArrayUtil.toStringArray(names), ", ");
    }
  }

  @NotNull
  Path getExportFile() {
    return Paths.get(myPathPanel.getText());
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.actions.ChooseComponentsToExportDialog";
  }
}
