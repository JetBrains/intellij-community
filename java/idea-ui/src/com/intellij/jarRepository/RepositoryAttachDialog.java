/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.jarRepository;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.xml.util.XmlStringUtil;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RepositoryAttachDialog extends DialogWrapper {
  @NonNls private static final String PROPERTY_DOWNLOAD_TO_PATH = "Downloaded.Files.Path";
  @NonNls private static final String PROPERTY_DOWNLOAD_TO_PATH_ENABLED = "Downloaded.Files.Path.Enabled";
  @NonNls private static final String PROPERTY_ATTACH_JAVADOC = "Repository.Attach.JavaDocs";
  @NonNls private static final String PROPERTY_ATTACH_SOURCES = "Repository.Attach.Sources";
  @NotNull private final Mode myMode;

  public enum Mode { SEARCH, DOWNLOAD }
  private final Project myProject;

  private JBLabel myInfoLabel;
  private JCheckBox myJavaDocCheckBox;
  private JCheckBox mySourcesCheckBox;
  private AsyncProcessIcon myProgressIcon;
  private ComboboxWithBrowseButton myComboComponent;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myDirectoryField;
  private JBCheckBox myDownloadToCheckBox;
  private JBLabel myCaptionLabel;
  private JPanel myDownloadOptionsPanel;
  private JBCheckBox myIncludeTransitiveDepsCheckBox;

  private final JComboBox myCombobox;

  private final Map<String, RepositoryArtifactDescription> myCoordinates = ContainerUtil.newTroveMap();
  private final List<String> myShownItems = ContainerUtil.newArrayList();
  private final String myDefaultDownloadFolder;

  private String myFilterString;
  private boolean myInUpdate;

  public RepositoryAttachDialog(@NotNull Project project, final @Nullable String initialFilter, @NotNull Mode mode) {
    super(project, true);
    myMode = mode;
    setTitle(mode == Mode.DOWNLOAD ? "Download Library from Maven Repository" : "Search Library in Maven Repositories");
    myProject = project;
    myProgressIcon.suspend();
    myCaptionLabel.setText(
      XmlStringUtil.wrapInHtml(StringUtil.escapeXml("keyword or class name to search by or exact Maven coordinates, " +
                                                    "i.e. 'spring', 'Logger' or 'ant:ant-junit:1.6.5'")
      ));
    myInfoLabel.setPreferredSize(
      new Dimension(myInfoLabel.getFontMetrics(myInfoLabel.getFont()).stringWidth("Showing: 1000"), myInfoLabel.getPreferredSize().height));

    myComboComponent.setButtonIcon(AllIcons.Actions.Menu_find);
    myComboComponent.getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        performSearch();
      }
    });
    myCombobox = myComboComponent.getComboBox();
    myCombobox.setModel(new CollectionComboBoxModel(myShownItems, null));
    myCombobox.setEditable(true);
    final JTextField textField = (JTextField)myCombobox.getEditor().getEditorComponent();
    textField.setColumns(20);
    if (initialFilter != null) {
      textField.setText(initialFilter);
    }
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (myProgressIcon.isDisposed()) return;
          ApplicationManager.getApplication().invokeLater(() -> {
            if (myProgressIcon.isDisposed()) return;
            handleMavenDependencyInsertion(e, textField);
            updateComboboxSelection(false);
          });

          updateComboboxSelection(false);
        });
      }
    });
    myCombobox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean popupVisible = myCombobox.isPopupVisible();
        if (!myInUpdate && (!popupVisible || myCoordinates.isEmpty())) {
          performSearch();
        }
        else {
          final String item = (String)myCombobox.getSelectedItem();
          if (StringUtil.isNotEmpty(item)) {
            ((JTextField)myCombobox.getEditor().getEditorComponent()).setText(item);
          }
        }
      }
    });
    VirtualFile baseDir = !myProject.isDefault() ? myProject.getBaseDir() : null;
    myDefaultDownloadFolder = baseDir != null ? FileUtil.toSystemDependentName(baseDir.getPath() + "/lib") : "";

    PropertiesComponent storage = PropertiesComponent.getInstance(myProject);
    myDownloadToCheckBox.setSelected(storage.isTrueValue(PROPERTY_DOWNLOAD_TO_PATH_ENABLED));
    myDirectoryField.setText(StringUtil.notNullize(StringUtil.nullize(storage.getValue(PROPERTY_DOWNLOAD_TO_PATH)), myDefaultDownloadFolder));
    myDirectoryField.setEnabled(myDownloadToCheckBox.isSelected());
    myDownloadToCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDirectoryField.setEnabled(myDownloadToCheckBox.isSelected());
      }
    });
    myJavaDocCheckBox.setSelected(storage.isTrueValue(PROPERTY_ATTACH_JAVADOC));
    mySourcesCheckBox.setSelected(storage.isTrueValue(PROPERTY_ATTACH_SOURCES));

    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.putUserData(FileChooserDialog.PREFER_LAST_OVER_TO_SELECT, Boolean.TRUE);
    myDirectoryField.addBrowseFolderListener(ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.title"),
                                             ProjectBundle.message("file.chooser.directory.for.downloaded.libraries.description"), null,
                                             descriptor);
    updateInfoLabel();
    setOKActionEnabled(false);
    myDownloadOptionsPanel.setVisible(mode == Mode.DOWNLOAD);
    init();
  }

  private static void handleMavenDependencyInsertion(DocumentEvent e, JTextField textField) {
    if (e.getType() == DocumentEvent.EventType.INSERT) {
      String text = textField.getText();
      if (isMvnDependency(text)) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        try {
          DocumentBuilder builder = factory.newDocumentBuilder();
          try {
            Document document = builder.parse(new InputSource(new StringReader(text)));
            String mavenCoordinates = extractMavenCoordinates(document);
            if (mavenCoordinates != null) {
              textField.setText(mavenCoordinates);
            }
          }
          catch (SAXException | IOException ignored) {
          }
        }
        catch (ParserConfigurationException ignored) {
        }
      }
    }
  }

  public boolean getAttachJavaDoc() {
    return myJavaDocCheckBox.isSelected();
  }

  public boolean getAttachSources() {
    return mySourcesCheckBox.isSelected();
  }

  public boolean getIncludeTransitiveDependencies() {
    return myIncludeTransitiveDepsCheckBox.isSelected();
  }

  @Nullable
  public String getDirectoryPath() {
    return myDownloadToCheckBox.isSelected()? myDirectoryField.getText() : null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombobox;
  }

  private void updateComboboxSelection(boolean force) {
    final String prevFilter = myFilterString;
    final JTextComponent field = (JTextComponent)myCombobox.getEditor().getEditorComponent();
    final int caret = field.getCaretPosition();
    myFilterString = field.getText();

    if (!force && Comparing.equal(myFilterString, prevFilter)) return;
    int prevSize = myShownItems.size();
    myShownItems.clear();

    myInUpdate = true;
    final boolean itemSelected = myCoordinates.containsKey(myFilterString) && Comparing.strEqual((String)myCombobox.getSelectedItem(), myFilterString, false);
    final boolean filtered;
    if (itemSelected) {
      myShownItems.addAll(myCoordinates.keySet());
      filtered = false;
    }
    else {
      final String[] parts = myFilterString.split(" ");
      main:
      for (String coordinate : myCoordinates.keySet()) {
        for (String part : parts) {
          if (!StringUtil.containsIgnoreCase(coordinate, part)) {
            continue main;
          }
        }
        myShownItems.add(coordinate);
      }
      filtered = !myShownItems.isEmpty();
      if (!filtered) {
        myShownItems.addAll(myCoordinates.keySet());
      }
      myCombobox.setSelectedItem(null);
    }

    // use maven version sorter
    ArrayList<LibItem> items = new ArrayList<>(myShownItems.size());
    for (String coord : myShownItems) {
      items.add(new LibItem(coord));
    }
    Collections.sort(items, (o1, o2) -> Comparing.compare(o1, o2));
    myShownItems.clear();
    for (LibItem it : items) {
      myShownItems.add(it.coord);
    }

    ((CollectionComboBoxModel)myCombobox.getModel()).update();
    myInUpdate = false;
    field.setText(myFilterString);
    field.setCaretPosition(caret);
    updateInfoLabel();
    if (filtered) {
      if (prevSize < 10 && myShownItems.size() > prevSize && myCombobox.isPopupVisible()) {
        myCombobox.setPopupVisible(false);
      }
      if (!myCombobox.isPopupVisible()) {
        myCombobox.setPopupVisible(true);
      }
    }
  }

  private static final class LibItem implements Comparable<LibItem> {
    final String prefix;
    final Version ver;
    final String coord;

    public LibItem(String coord) {
      this.coord = coord;
      final JpsMavenRepositoryLibraryDescriptor desc = new JpsMavenRepositoryLibraryDescriptor(coord);
      prefix = desc.getGroupId() + ":" + desc.getArtifactId();
      Version ver = null;
      try {
        ver = ArtifactRepositoryManager.asVersion(desc.getVersion());
      }
      catch (InvalidVersionSpecificationException ignored) {
      }
      this.ver = ver;
    }

    @Override
    public int compareTo(@NotNull LibItem that) {
      final int prefixCompare = prefix.compareTo(that.prefix);
      return prefixCompare != 0 ? prefixCompare : Comparing.compare(that.ver, ver);
    }
  }

  private boolean performSearch() {
    final String text = getCoordinateText();
    if (myProgressIcon.isRunning() || StringUtil.isEmptyOrSpaces(text) || myCoordinates.containsKey(text)) {
      return false;
    }
    myProgressIcon.resume();
    JarRepositoryManager.searchArtifacts(myProject, text, (pairs) -> {
      if (myProgressIcon.isDisposed()) {
        return;
      }
      myProgressIcon.suspend(); // finished
      final int prevSize = myCoordinates.size();
      for (Pair<RepositoryArtifactDescription, RemoteRepositoryDescription> pair : pairs) {
        final RepositoryArtifactDescription artifact = pair.first;
        myCoordinates.put(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion(), artifact);
      }
      updateComboboxSelection(prevSize != myCoordinates.size());
      setOKActionEnabled(true);
    });
    return true;
  }

  private void updateInfoLabel() {
    myInfoLabel.setText("<html>Found: " + myCoordinates.size() + "<br>Showing: " + myCombobox.getModel().getSize() + "</html>");
  }

  @Override
  protected ValidationInfo doValidate() {
    if (!isValidCoordinateSelected()) {
      return new ValidationInfo("Please enter valid coordinate, discover it or select one from the list", myCombobox);
    }
    else if (myDownloadToCheckBox.isSelected()) {
      final File dir = new File(myDirectoryField.getText());
      if (!dir.exists() && !dir.mkdirs() || !dir.isDirectory()) {
        return new ValidationInfo("Please enter valid library files path", myDirectoryField.getTextField());
      }
    }
    return super.doValidate();
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    return myPanel;
  }


  @Override
  protected void dispose() {
    Disposer.dispose(myProgressIcon);
    PropertiesComponent storage = PropertiesComponent.getInstance(myProject);
    storage.setValue(PROPERTY_DOWNLOAD_TO_PATH_ENABLED, String.valueOf(myDownloadToCheckBox.isSelected()));
    String downloadPath = myDirectoryField.getText();
    if (StringUtil.isEmptyOrSpaces(downloadPath)) downloadPath = myDefaultDownloadFolder;
    storage.setValue(PROPERTY_DOWNLOAD_TO_PATH, downloadPath, myDefaultDownloadFolder);
    storage.setValue(PROPERTY_ATTACH_JAVADOC, String.valueOf(myJavaDocCheckBox.isSelected()));
    storage.setValue(PROPERTY_ATTACH_SOURCES, String.valueOf(mySourcesCheckBox.isSelected()));
    super.dispose();
  }

  @Override
  protected String getDimensionServiceKey() {
    return RepositoryAttachDialog.class.getName() + "-" + myMode;
  }

  private boolean isValidCoordinateSelected() {
    final String text = getCoordinateText();
    return text.split(":").length == 3;
  }

  public String getCoordinateText() {
    final JTextField field = (JTextField)myCombobox.getEditor().getEditorComponent();
    return field.getText();
  }

  private void createUIComponents() {
    myProgressIcon = new AsyncProcessIcon("Progress");
  }

  private static boolean isMvnDependency(String text) {
    String trimmed = text.trim();
    if (trimmed.startsWith("<dependency>") && trimmed.endsWith("</dependency>")) {
      return true;
    }
    return false;
  }

  @Nullable
  private static String extractMavenCoordinates(Document document) {
    String groupId = getGroupId(document);
    String artifactId = getArtifactId(document);
    if (groupId.isEmpty() && artifactId.isEmpty()) {
      return null;
    }
    String version = getVersion(document);
    String classifier = getClassifier(document);
    String gradleClassifier = classifier.isEmpty() ? "" : ":" + classifier;
    return groupId + ":" + artifactId + ":" + version + gradleClassifier;
  }

  private static String getVersion(@NotNull Document document) {
    return firstOrEmpty(document.getElementsByTagName("version"));
  }

  private static String getArtifactId(@NotNull Document document) {
    return firstOrEmpty(document.getElementsByTagName("artifactId"));
  }

  private static String getGroupId(@NotNull Document document) {
    return firstOrEmpty(document.getElementsByTagName("groupId"));
  }

  private static String getClassifier(@NotNull Document document) {
    return firstOrEmpty(document.getElementsByTagName("classifier"));
  }

  private static String firstOrEmpty(@NotNull NodeList list) {
    Node first = list.item(0);
    return first != null ? first.getTextContent() : "";
  }
}
