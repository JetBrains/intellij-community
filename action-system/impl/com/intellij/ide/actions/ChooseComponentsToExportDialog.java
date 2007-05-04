package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.ElementsChooser;
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
import org.jetbrains.annotations.NonNls;
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

  private ElementsChooser<ComponentElementProperties> myChooser;
  private FieldPanel myPathPanel;
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

    for (ExportableApplicationComponent component : components) {
      if (!addToExistingListElement(component, componentToContainingListElement, fileToComponents)) {
        ComponentElementProperties componentElementProperties = new ComponentElementProperties();
        componentElementProperties.addComponent(component);

        componentToContainingListElement.put(component, componentElementProperties);
      }
    }
    final Set<ComponentElementProperties> componentElementProperties = new LinkedHashSet<ComponentElementProperties>(componentToContainingListElement.values());
    myChooser = new ElementsChooser<ComponentElementProperties>(true);
    for (final ComponentElementProperties componentElementProperty : componentElementProperties) {
      myChooser.addElement(componentElementProperty, true, componentElementProperty);
    }

    final ActionListener browseAction = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String oldPath = myPathPanel.getText();
        String path = chooseSettingsFile(oldPath, getWindow(), IdeBundle.message("title.export.file.location"),
                                         IdeBundle.message("prompt.choose.export.settings.file.path"));
        if (path == null) return;
        myPathPanel.setText(FileUtil.toSystemDependentName(path));
      }
    };

    myPathPanel = new FieldPanel(IdeBundle.message("editbox.export.settings.to"), null, browseAction, null);
    myPathPanel.setText(DEFAULT_PATH);

    setTitle(title);
    init();
  }

  private static boolean addToExistingListElement(ExportableApplicationComponent component,
                                           Map<ExportableApplicationComponent,ComponentElementProperties> componentToContainingListElement,
                                           Map<File, Set<ExportableApplicationComponent>> fileToComponents) {
    final File[] exportFiles = component.getExportFiles();
    File file = null;
    for (File exportFile : exportFiles) {
      final Set<ExportableApplicationComponent> tiedComponents = fileToComponents.get(exportFile);

      for (final ExportableApplicationComponent tiedComponent : tiedComponents) {
        if (tiedComponent == component) continue;
        final ComponentElementProperties elementProperties = componentToContainingListElement.get(tiedComponent);
        if (elementProperties != null && !exportFile.equals(file)) {
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

  @Nullable
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
    for (ComponentElementProperties elementProperties : markedElements) {
      components.addAll(elementProperties.myComponents);
    }
    return components;
  }

  private static class ComponentElementProperties implements ElementsChooser.ElementProperties {
    private final Set<ExportableApplicationComponent> myComponents = new HashSet<ExportableApplicationComponent>();

    private boolean addComponent(ExportableApplicationComponent component) {
      return myComponents.add(component);
    }

    @Nullable
    public Icon getIcon() {
      return null;
    }

    @Nullable
    public Color getColor() {
      return null;
    }

    public String toString() {
      String result = "";

      for (final ExportableApplicationComponent component : myComponents) {
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
