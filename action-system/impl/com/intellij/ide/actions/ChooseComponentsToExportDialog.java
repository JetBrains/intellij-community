package com.intellij.ide.actions;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FieldPanel;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.IdeBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

import org.jetbrains.annotations.NonNls;

public class ChooseComponentsToExportDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ChooseComponentsToExportDialog");

  private ElementsChooser<ComponentElementProperties> myChooser;
  private FieldPanel myPathPanel;
  private ActionListener myBrowseAction;
  @NonNls
  public static final String DEFAULT_PATH = FileUtil.toSystemDependentName(PathManager.getConfigPath()+"/"+"settings.jar");
  private final boolean myShowFilePath;
  private final String myDescription;

  public ChooseComponentsToExportDialog(List<ExportableApplicationComponent> components,
                                        Map<File, Set<ExportableApplicationComponent>> fileToComponents,
                                        boolean showFilePath, final String title, String description) {
    super(false);
    myDescription = description;
    myShowFilePath = showFilePath;
    Map<ExportableApplicationComponent, ComponentElementProperties> componentToContainingListElement = new LinkedHashMap<ExportableApplicationComponent, ComponentElementProperties>();

    for (int i = 0; i < components.size(); i++) {
      ExportableApplicationComponent component = components.get(i);
      if (!addToExistingListElement(component, componentToContainingListElement, fileToComponents)) {
        ComponentElementProperties componentElementProperties = new ComponentElementProperties();
        componentElementProperties.addComponent(component);

        componentToContainingListElement.put(component, componentElementProperties);
      }
    }
    final Set<ComponentElementProperties> componentElementProperties = new LinkedHashSet<ComponentElementProperties>(componentToContainingListElement.values());
    myChooser = new ElementsChooser<ComponentElementProperties>();
    for (Iterator iterator = componentElementProperties.iterator(); iterator.hasNext();) {
      ComponentElementProperties elementProperties = (ComponentElementProperties)iterator.next();
      myChooser.addElement(elementProperties, true, elementProperties);
    }

    myBrowseAction = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String oldPath = myPathPanel.getText();
        String path = chooseSettingsFile(oldPath, getWindow(), IdeBundle.message("title.export.file.location"), 
                                         IdeBundle.message("prompt.choose.export.settings.file.path"));
        if (path == null) return;
        myPathPanel.setText(FileUtil.toSystemDependentName(path));
      }
    };

    myPathPanel = new FieldPanel(IdeBundle.message("editbox.export.settings.to"), null, myBrowseAction, null);
    myPathPanel.setText(DEFAULT_PATH);

    setTitle(title);
    init();
  }

  private boolean addToExistingListElement(ExportableApplicationComponent component,
                                           Map<ExportableApplicationComponent,ComponentElementProperties> componentToContainingListElement,
                                           Map<File, Set<ExportableApplicationComponent>> fileToComponents) {
    final File[] exportFiles = component.getExportFiles();
    File file = null;
    for (int i = 0; i < exportFiles.length; i++) {
      File exportFile = exportFiles[i];
      final Set<ExportableApplicationComponent> tiedComponents = fileToComponents.get(exportFile);

      for (Iterator iterator = tiedComponents.iterator(); iterator.hasNext();) {
        ExportableApplicationComponent tiedComponent = (ExportableApplicationComponent)iterator.next();
        if (tiedComponent == component) continue;
        final ComponentElementProperties elementProperties = componentToContainingListElement.get(tiedComponent);
        if (elementProperties != null && !exportFile.equals(file)) {
          LOG.assertTrue(file == null, "Component "+component+" serialize itself into "+file+" and "+exportFile);
          // found
          elementProperties.addComponent(component);
          componentToContainingListElement.put(component, elementProperties);
          file = exportFile;
        }
      }
    }
    return file != null;
  }

  public static String chooseSettingsFile(String oldPath, Component parent, final String title, final String description) {
    FileChooserDescriptor chooserDescriptor;
    chooserDescriptor = new FileChooserDescriptor(true, true, true, true, false, false);
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
    final VirtualFile[] files = FileChooser.chooseFiles(parent, chooserDescriptor, initialDir);
    if (files.length == 0) {
      return null;
    }
    VirtualFile file = files[0];
    String path;
    if (file.isDirectory()) {
      String defaultName = new File(DEFAULT_PATH).getName();
      path = file.getPath() + "/" + defaultName;
    }
    else {
      path = file.getPath();
    }
    return path;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPathPanel.getTextField();
  }

  protected JComponent createNorthPanel() {
    return new JLabel(myDescription);
  }

  protected JComponent createCenterPanel() {
    return myChooser;
  }

  protected JComponent createSouthPanel() {
    final JComponent buttons = super.createSouthPanel();
    if (!myShowFilePath) return buttons;
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.add(myPathPanel);
    panel.add(buttons);
    return panel;
  }

  Set<ExportableApplicationComponent> getExportableComponents() {
    final List<ComponentElementProperties> markedElements = myChooser.getMarkedElements();
    final Set<ExportableApplicationComponent> components = new HashSet<ExportableApplicationComponent>();
    for (int i = 0; i < markedElements.size(); i++) {
      ComponentElementProperties elementProperties = markedElements.get(i);
      components.addAll(elementProperties.myComponents);
    }
    return components;
  }

  private static class ComponentElementProperties implements ElementsChooser.ElementProperties {
    private final Set<ExportableApplicationComponent> myComponents = new HashSet<ExportableApplicationComponent>();

    private boolean addComponent(ExportableApplicationComponent component) {
      return myComponents.add(component);
    }

    public Icon getIcon() {
      return null;
    }

    public Color getColor() {
      return null;
    }

    public String toString() {
      String result = "";
      for (Iterator iterator = myComponents.iterator(); iterator.hasNext();) {
        ExportableApplicationComponent component = (ExportableApplicationComponent)iterator.next();
        result += (result.length() == 0 ? "" : ", ") + component.getPresentableName();
      }
      return result;
    }
  }

  File getExportFile() {
    return new File(myPathPanel.getText());
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.actions.ChooseComponentsToExportDialog";
  }
}
