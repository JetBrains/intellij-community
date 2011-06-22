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
package com.intellij.facet.impl.ui.facetType;

import com.intellij.facet.FacetType;
import com.intellij.facet.impl.autodetecting.DisabledAutodetectionByTypeElement;
import com.intellij.facet.impl.autodetecting.DisabledAutodetectionInModuleElement;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManagerImpl;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * @author nik
 */
public class FacetAutodetectionConfigurable implements Configurable {
  private final Project myProject;
  private final StructureConfigurableContext myContext;
  private final FacetType<?, ?> myFacetType;
  private JPanel myMainPanel;
  private JCheckBox myEnableAutoDetectionCheckBox;
  private JList myModulesList;
  private JButton myAddModuleButton;
  private JButton myRemoveModuleButton;
  private JList myFilesList;
  private JButton myRemoveFileButton;
  private JPanel mySettingsPanel;
  private JPanel mySkipFilesListPanel;
  private final DefaultListModel myModulesListModel;
  private final DefaultListModel myFilesListModel;
  private final BidirectionalMap<String, String> myFile2Module = new BidirectionalMap<String, String>();
  private final Set<String> myRemovedModules = new HashSet<String>();
  private final Set<String> myAddedModules = new HashSet<String>();
  private final Set<String> myRemovedFiles = new HashSet<String>();
  private boolean myAutodetectionWasEnabled;

  public FacetAutodetectionConfigurable(@NotNull Project project, final StructureConfigurableContext context, final @NotNull FacetType<?, ?> facetType) {
    myProject = project;
    myContext = context;
    myFacetType = facetType;

    myModulesList.setCellRenderer(new ModulesListCellRenderer());
    myModulesListModel = new DefaultListModel();
    myModulesList.setModel(myModulesListModel);

    myFilesList.setCellRenderer(new FilesListCellRenderer());
    myFilesListModel = new DefaultListModel();
    myFilesList.setModel(myFilesListModel);

    myAddModuleButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        String description = ProjectBundle.message("choose.description.facet.auto.detection.will.be.disabled.in.the.selected.modules",
                                                    myFacetType.getPresentableName());
        String title = ProjectBundle.message("choose.modules.dialog.title");
        ChooseModulesDialog dialog = new ChooseModulesDialog(myProject, getEnabledModules(), title, description);
        dialog.show();
        List<Module> chosenElements = dialog.getChosenElements();
        if (dialog.isOK() && !chosenElements.isEmpty()) {
          for (Module module : chosenElements) {
            String moduleName = module.getName();
            myModulesListModel.addElement(moduleName);
            if (!myRemovedModules.remove(moduleName)) {
              myAddedModules.add(moduleName);
            }
          }
          updateButtons();
          myModulesList.repaint();
        }
      }
    });
    UIUtil.addKeyboardShortcut(myModulesList, myAddModuleButton, CommonShortcuts.getInsertKeystroke());

    myRemoveModuleButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        //noinspection unchecked
        List<String> removed = ListUtil.removeSelectedItems(myModulesList);
        for (String moduleName : removed) {
          if (!myAddedModules.remove(moduleName)) {
            myRemovedModules.add(moduleName);
          }
        }
        updateButtons();
      }
    });
    KeyStroke delete = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
    UIUtil.addKeyboardShortcut(myModulesList, myRemoveModuleButton, delete);

    myRemoveFileButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        //noinspection unchecked
        List<String> removed = ListUtil.removeSelectedItems(myFilesList);
        myRemovedFiles.addAll(removed);
        updateButtons();
      }
    });
    UIUtil.addKeyboardShortcut(myFilesList, myRemoveFileButton, delete);

    myEnableAutoDetectionCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateButtons();
      }
    });

    ListSelectionListener selectionListener = new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        updateRemoveButtons();
      }
    };
    myFilesList.addListSelectionListener(selectionListener);
    myModulesList.addListSelectionListener(selectionListener);
  }

  private List<Module> getEnabledModules() {
    List<Module> modules = new ArrayList<Module>(Arrays.asList(getAllModules()));
    Iterator<Module> iterator = modules.iterator();

    Set<String> disabled = getDisabledModules();

    while (iterator.hasNext()) {
      Module module = iterator.next();
      if (disabled.contains(module.getName())) {
        iterator.remove();
      }
    }
    return modules;
  }

  protected Module[] getAllModules() {
    return myContext.getModules();
  }

  private Set<String> getDisabledModules() {
    Set<String> disabled = new LinkedHashSet<String>();
    for (int i = 0; i < myModulesListModel.getSize(); i++) {
      disabled.add((String)myModulesListModel.getElementAt(i));
    }
    return disabled;
  }

  public String getDisplayName() {
    return ProjectBundle.message("auto.detection.configurable.display.name");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.facet";
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public boolean isModified() {
    return myAutodetectionWasEnabled != myEnableAutoDetectionCheckBox.isSelected()
           || !myRemovedFiles.isEmpty() || !myAddedModules.isEmpty() || !myRemovedModules.isEmpty();
  }

  public void apply() throws ConfigurationException {
    DisabledAutodetectionByTypeElement state = getAutodetectingManager().getDisabledAutodetectionState(myFacetType);
    DisabledAutodetectionByTypeElement newState = modifyState(state);
    getAutodetectingManager().setDisabledAutodetectionState(myFacetType, newState);
  }

  @Nullable
  private DisabledAutodetectionByTypeElement modifyState(final DisabledAutodetectionByTypeElement state) {
    if (!myEnableAutoDetectionCheckBox.isSelected()) {
      return new DisabledAutodetectionByTypeElement(myFacetType.getStringId());
    }

    if (state == null) {
      if (myAddedModules.isEmpty()) {
        return null;
      }
      DisabledAutodetectionByTypeElement newState = new DisabledAutodetectionByTypeElement(myFacetType.getStringId());
      for (String moduleName : myAddedModules) {
        newState.getModuleElements().add(new DisabledAutodetectionInModuleElement(moduleName));
      }
      return newState;
    }

    DisabledAutodetectionByTypeElement newState = copyState(state);
    boolean someModuleRemoved = false;
    for (String url : myRemovedFiles) {
      String moduleName = myFile2Module.get(url);
      DisabledAutodetectionInModuleElement element = newState.findElement(moduleName);
      if (element != null) {
        boolean removed = element.getFiles().remove(url) | element.getDirectories().remove(url);
        if (removed && element.isDisableInWholeModule()) {
          newState.removeDisabled(moduleName);
          someModuleRemoved = true;
        }
      }
    }
    for (String moduleName : myAddedModules) {
      newState.getModuleElements().add(new DisabledAutodetectionInModuleElement(moduleName));
    }
    for (String moduleName : myRemovedModules) {
      someModuleRemoved |= newState.findElement(moduleName) != null;
      newState.removeDisabled(moduleName);
    }
    if ((someModuleRemoved || !myAutodetectionWasEnabled) && newState.getModuleElements().isEmpty()) {
      return null;
    }
    return newState;
  }

  private DisabledAutodetectionByTypeElement copyState(final DisabledAutodetectionByTypeElement state) {
    DisabledAutodetectionByTypeElement newState = new DisabledAutodetectionByTypeElement(myFacetType.getStringId());
    for (DisabledAutodetectionInModuleElement moduleElement : state.getModuleElements()) {
      DisabledAutodetectionInModuleElement newModuleElement = new DisabledAutodetectionInModuleElement(moduleElement.getModuleName());
      newModuleElement.getFiles().addAll(moduleElement.getFiles());
      newModuleElement.getDirectories().addAll(moduleElement.getDirectories());
      newState.getModuleElements().add(newModuleElement);
    }
    return newState;
  }

  public void reset() {
    DisabledAutodetectionByTypeElement autodetectionInfo = getAutodetectingManager().getDisabledAutodetectionState(myFacetType);
    myModulesListModel.removeAllElements();
    myFilesListModel.removeAllElements();
    myFile2Module.clear();
    myRemovedModules.clear();
    myAddedModules.clear();
    myRemovedFiles.clear();

    myEnableAutoDetectionCheckBox.setSelected(true);
    if (autodetectionInfo != null) {
      List<DisabledAutodetectionInModuleElement> moduleElements = autodetectionInfo.getModuleElements();
      if (moduleElements.isEmpty()) {
        myEnableAutoDetectionCheckBox.setSelected(false);
      }
      else {
        List<String> modules = new ArrayList<String>();
        List<String> urls = new ArrayList<String>();

        for (DisabledAutodetectionInModuleElement moduleElement : moduleElements) {
          String moduleName = moduleElement.getModuleName();
          if (moduleElement.isDisableInWholeModule()) {
            modules.add(moduleName);
          }
          else {
            for (String url : moduleElement.getFiles()) {
              myFile2Module.put(url, moduleName);
              urls.add(url);
            }
            for (String url : moduleElement.getDirectories()) {
              myFile2Module.put(url, moduleName);
              urls.add(url);
            }
          }
        }
        
        Collections.sort(urls);
        Collections.sort(modules);
        for (String url : urls) {
          myFilesListModel.addElement(url);
        }
        for (String moduleName : modules) {
          myModulesListModel.addElement(moduleName);
        }
      }
    }
    myAutodetectionWasEnabled = myEnableAutoDetectionCheckBox.isSelected();
    mySkipFilesListPanel.setVisible(!myFilesListModel.isEmpty());
    updateButtons();
  }

  @TestOnly
  public Set<String> getRemovedModules() {
    return myRemovedModules;
  }

  @TestOnly
  public Set<String> getAddedModules() {
    return myAddedModules;
  }

  @TestOnly
  public Set<String> getRemovedFiles() {
    return myRemovedFiles;
  }

  @TestOnly
  public JCheckBox getEnableAutoDetectionCheckBox() {
    return myEnableAutoDetectionCheckBox;
  }

  private void updateButtons() {
    if (!myEnableAutoDetectionCheckBox.isSelected()) {
      UIUtil.setEnabled(mySettingsPanel, false, true);
      return;
    }

    UIUtil.setEnabled(mySettingsPanel, true, true);
    myAddModuleButton.setEnabled(!getEnabledModules().isEmpty());
    updateRemoveButtons();
  }

  private void updateRemoveButtons() {
    myRemoveModuleButton.setEnabled(myEnableAutoDetectionCheckBox.isSelected() && myModulesList.getSelectedIndices().length > 0);
    myRemoveFileButton.setEnabled(myEnableAutoDetectionCheckBox.isSelected() && myFilesList.getSelectedIndices().length > 0);
  }


  private FacetAutodetectingManagerImpl getAutodetectingManager() {
    return (FacetAutodetectingManagerImpl)FacetAutodetectingManager.getInstance(myProject);
  }

  public void disposeUIResources() {
  }

  private static class FilesListCellRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(final JList list,
                                         final Object value,
                                         final int index,
                                         final boolean selected,
                                         final boolean hasFocus) {
      String url = (String)value;
      String path = VfsUtil.urlToPath(url);
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file != null) {
        append(path, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(file.isDirectory() ? PlatformIcons.FOLDER_ICON : file.getIcon());
      }
      else {
        append(path, SimpleTextAttributes.ERROR_ATTRIBUTES);
        setIcon(null);
      }
    }
  }

  private class ModulesListCellRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(final JList list,
                                         final Object value,
                                         final int index,
                                         final boolean selected,
                                         final boolean hasFocus) {
      String moduleName = (String)value;
      Module module = myContext.myModulesConfigurator.getModule(moduleName);
      if (module != null) {
        append(moduleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(module.getModuleType().getNodeIcon(false));
      }
      else {
        append(moduleName, SimpleTextAttributes.ERROR_ATTRIBUTES);
        setIcon(null);
      }
    }
  }
}
