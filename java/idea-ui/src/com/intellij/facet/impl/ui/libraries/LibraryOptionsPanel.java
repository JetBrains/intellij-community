/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.facet.impl.ui.libraries;

import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureDialogCellAppearanceUtils;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryFilter;
import com.intellij.openapi.roots.ui.configuration.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.RadioButtonEnumModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class LibraryOptionsPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel");
  private JLabel myMessageLabel;
  private JPanel myPanel;
  private JButton myConfigureButton;
  private JComboBox myExistingLibraryComboBox;
  private JRadioButton myDoNotCreateRadioButton;
  private JPanel myConfigurationPanel;
  private JButton myCreateButton;
  private JRadioButton myDownloadRadioButton;
  private JRadioButton myUseLibraryRadioButton;
  private JLabel myUseLibraryLabel;
  private JLabel myHiddenLabel;
  private JPanel myRootPanel;
  private ButtonGroup myButtonGroup;

  private LibraryCompositionSettings mySettings;
  private final LibrariesContainer myLibrariesContainer;
  private SortedComboBoxModel<LibraryEditor> myLibraryComboBoxModel;
  private boolean myDisposed;

  private enum Choice {
    USE_LIBRARY,
    DOWNLOAD,
    SETUP_LIBRARY_LATER
  }

  private RadioButtonEnumModel<Choice> myButtonEnumModel;

  public LibraryOptionsPanel(final @NotNull CustomLibraryDescription libraryDescription,
                                    final @NotNull String baseDirectoryPath,
                                    @Nullable final FrameworkVersion currentFrameworkVersion,
                             @NotNull final LibrariesContainer librariesContainer,
                             final boolean showDoNotCreateOption) {
    myLibrariesContainer = librariesContainer;
    final DownloadableLibraryDescription description = libraryDescription.getDownloadableDescription();
    if (description != null) {
      showCard("loading");
      description.fetchLibraryVersions(new DownloadableLibraryDescription.LibraryVersionsCallback() {
        @Override
        public void onSuccess(@NotNull final List<? extends FrameworkLibraryVersion> versions) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              if (!myDisposed) {
                showSettingsPanel(libraryDescription, baseDirectoryPath, currentFrameworkVersion, showDoNotCreateOption, versions);
              }
            }
          });
        }
      });
    }
    else {
      showSettingsPanel(libraryDescription, baseDirectoryPath, currentFrameworkVersion, showDoNotCreateOption,
                        new ArrayList<FrameworkLibraryVersion>());
    }
  }

  private void showCard(final String editing) {
    ((CardLayout)myRootPanel.getLayout()).show(myRootPanel, editing);
  }

  private void showSettingsPanel(CustomLibraryDescription libraryDescription,
                                 String baseDirectoryPath,
                                 FrameworkVersion currentFrameworkVersion,
                                 boolean showDoNotCreateOption, final List<? extends FrameworkLibraryVersion> versions) {
    //todo[nik] create mySettings only in apply() method
    mySettings = new LibraryCompositionSettings(libraryDescription, baseDirectoryPath, currentFrameworkVersion, versions);
    List<Library> libraries = calculateSuitableLibraries();

    myButtonEnumModel = RadioButtonEnumModel.bindEnum(Choice.class, myButtonGroup);
    myButtonEnumModel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateState();
      }
    });

    myDoNotCreateRadioButton.setVisible(showDoNotCreateOption);
    myLibraryComboBoxModel = new SortedComboBoxModel<LibraryEditor>(new Comparator<LibraryEditor>() {
      @Override
      public int compare(LibraryEditor o1, LibraryEditor o2) {
        final String name1 = o1.getName();
        final String name2 = o2.getName();
        return -StringUtil.notNullize(name1).compareToIgnoreCase(StringUtil.notNullize(name2));
      }
    });

    for (Library library : libraries) {
      ExistingLibraryEditor libraryEditor = myLibrariesContainer.getLibraryEditor(library);
      if (libraryEditor == null) {
        libraryEditor = mySettings.getOrCreateEditor(library);
      }
      myLibraryComboBoxModel.add(libraryEditor);
    }
    myExistingLibraryComboBox.setModel(myLibraryComboBoxModel);
    if (libraries.isEmpty()) {
      myLibraryComboBoxModel.add(null);
    }
    myExistingLibraryComboBox.setSelectedIndex(0);
    myExistingLibraryComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() != null) {
          myButtonEnumModel.setSelected(Choice.USE_LIBRARY);
        }
        updateState();
      }
    });
    myExistingLibraryComboBox.setRenderer(new LibraryListCellRenderer());

    boolean canDownload = mySettings.getDownloadSettings() != null;
    myDownloadRadioButton.setVisible(canDownload);
    myButtonEnumModel.setSelected(libraries.isEmpty() && canDownload ? Choice.DOWNLOAD : Choice.USE_LIBRARY);

    if (!canDownload && !showDoNotCreateOption) {
      myUseLibraryRadioButton.setVisible(false);
      myUseLibraryLabel.setVisible(true);
    }

    final Dimension minimumSize = new Dimension(-1, myMessageLabel.getFontMetrics(myMessageLabel.getFont()).getHeight() * 2);
    myMessageLabel.setMinimumSize(minimumSize);
    myHiddenLabel.setMinimumSize(minimumSize);

    myCreateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doCreate();
      }
    });
    myConfigureButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        doConfigure();
      }
    });
    updateState();
    showCard("editing");
  }

  private void doConfigure() {
    switch (myButtonEnumModel.getSelected()) {
      case DOWNLOAD:
        final LibraryDownloadSettings oldDownloadSettings = mySettings.getDownloadSettings();
        LOG.assertTrue(oldDownloadSettings != null);
        final LibraryDownloadSettings newDownloadSettings = DownloadingOptionsDialog.showDialog(myPanel, oldDownloadSettings,
                                                                                                mySettings.getCompatibleVersions(), true);
        if (newDownloadSettings != null) {
          mySettings.setDownloadSettings(newDownloadSettings);
        }
        break;

      case USE_LIBRARY:
        final Object item = myExistingLibraryComboBox.getSelectedItem();
        if (item instanceof LibraryEditor) {
          EditLibraryDialog dialog = new EditLibraryDialog(myPanel, mySettings, (LibraryEditor)item);
          dialog.show();
          if (item instanceof ExistingLibraryEditor) {
            new WriteAction() {
              protected void run(final Result result) {
                ((ExistingLibraryEditor)item).commit();
              }
            }.execute();
          }
        }
        break;

      case SETUP_LIBRARY_LATER:
        break;
    }
    updateState();
  }

  public void changeBaseDirectoryPath(@NotNull String directoryForLibrariesPath) {
    if (mySettings != null) {
      mySettings.changeBaseDirectoryPath(directoryForLibrariesPath);
      updateState();
    }
  }

  public void updateDownloadableVersions(FrameworkVersion version) {
    if (mySettings != null) {
      mySettings.updateDownloadableVersions(version);
      updateState();
    }
  }

  private void doCreate() {
    final NewLibraryConfiguration libraryConfiguration = mySettings.getLibraryDescription().createNewLibrary(myPanel, getBaseDirectory());
    if (libraryConfiguration != null) {
      final NewLibraryEditor libraryEditor = new NewLibraryEditor(libraryConfiguration.getLibraryType(), libraryConfiguration.getProperties());
      libraryEditor.setName(myLibrariesContainer.suggestUniqueLibraryName(libraryConfiguration.getDefaultLibraryName()));
      libraryConfiguration.addRoots(libraryEditor);
      if (myLibraryComboBoxModel.get(0) == null) {
        myLibraryComboBoxModel.remove(0);
      }
      myLibraryComboBoxModel.add(libraryEditor);
      myLibraryComboBoxModel.setSelectedItem(libraryEditor);
      myButtonEnumModel.setSelected(Choice.USE_LIBRARY);
    }
  }

  private List<Library> calculateSuitableLibraries() {
    final LibraryFilter filter = mySettings.getLibraryDescription().getSuitableLibraryFilter();
    List<Library> suitableLibraries = new ArrayList<Library>();
    for (Library library : myLibrariesContainer.getAllLibraries()) {
      final VirtualFile[] files = myLibrariesContainer.getLibraryFiles(library, OrderRootType.CLASSES);
      if (filter.isSuitableLibrary(Arrays.asList(files), ((LibraryEx)library).getType())) {
        suitableLibraries.add(library);
      }
    }
    return suitableLibraries;
  }

  @Nullable
  private VirtualFile getBaseDirectory() {
    String path = mySettings.getBaseDirectoryPath();
    VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(path);
    if (dir == null) {
      path = path.substring(0, path.lastIndexOf('/'));
      dir = LocalFileSystem.getInstance().findFileByPath(path);
    }
    return dir;
  }

  private void updateState() {
    myMessageLabel.setIcon(null);
    myConfigureButton.setVisible(true);
    myDownloadRadioButton.setEnabled(mySettings.getDownloadSettings() != null);
    if (!myDownloadRadioButton.isEnabled() && myDownloadRadioButton.isSelected() && myUseLibraryRadioButton.isVisible()) {
      myUseLibraryRadioButton.setSelected(true);
    }
    String message = "";
    boolean showConfigurePanel = true;
    switch (myButtonEnumModel.getSelected()) {
      case DOWNLOAD:
        message = getDownloadFilesMessage();
        break;
      case USE_LIBRARY:
        final Object item = myExistingLibraryComboBox.getSelectedItem();
        if (item == null) {
          myMessageLabel.setIcon(IconLoader.getIcon("/runConfigurations/configurationWarning.png"));
          message = "<b>Error:</b> library is not specified";
          myConfigureButton.setVisible(false);
        }
        else if (item instanceof NewLibraryEditor) {
          final LibraryEditor libraryEditor = (LibraryEditor)item;
          message = IdeBundle.message("label.library.will.be.created.description.text", mySettings.getNewLibraryLevel(),
                                      libraryEditor.getName(), libraryEditor.getFiles(OrderRootType.CLASSES).length);
        }
        else {
          message = MessageFormat.format("<b>{0}</b> library will be used", ((ExistingLibraryEditor)item).getName());
        }
        break;
      default:
        showConfigurePanel = false;
    }

    //show the longest message on the hidden card to ensure that dialog won't jump if user selects another option
    if (mySettings.getDownloadSettings() != null) {
      myHiddenLabel.setText(getDownloadFilesMessage());
    }
    else {
      myHiddenLabel.setText(IdeBundle.message("label.library.will.be.created.description.text", mySettings.getNewLibraryLevel(),
                                              "name", 10));
    }
    ((CardLayout)myConfigurationPanel.getLayout()).show(myConfigurationPanel, showConfigurePanel ? "configure" : "empty");
    myMessageLabel.setText("<html>" + message + "</html>");
  }

  private String getDownloadFilesMessage() {
    final LibraryDownloadSettings downloadSettings = mySettings.getDownloadSettings();
    if (downloadSettings == null) return "";

    final String downloadPath = downloadSettings.getDirectoryForDownloadedLibrariesPath();
    final String basePath = mySettings.getBaseDirectoryPath();
    String path;
    if (!StringUtil.isEmpty(basePath) && FileUtil.startsWith(downloadPath, basePath)) {
      path = FileUtil.getRelativePath(basePath, downloadPath, '/');
    }
    else {
      path = PathUtil.getFileName(downloadPath);
    }
    return MessageFormat.format("{0} {0, choice, 1#jar|2#jars} will be downloaded into <b>{1}</b> directory<br>" +
                                   "{2} library <b>{3}</b> will be created",
                                   downloadSettings.getSelectedDownloads().size(),
                                   path,
                                   downloadSettings.getLibraryLevel(),
                                   downloadSettings.getLibraryName());
  }

  public LibraryCompositionSettings getSettings() {
    return mySettings;
  }

  @Nullable
  public LibraryCompositionSettings apply() {
    if (mySettings == null) return null;

    final Choice option = myButtonEnumModel.getSelected();
    mySettings.setDownloadLibraries(option == Choice.DOWNLOAD);

    final Object item = myExistingLibraryComboBox.getSelectedItem();
    if (option == Choice.USE_LIBRARY && item instanceof ExistingLibraryEditor) {
      mySettings.setSelectedExistingLibrary(((ExistingLibraryEditor)item).getLibrary());
    }
    else {
      mySettings.setSelectedExistingLibrary(null);
    }

    if (option == Choice.USE_LIBRARY && item instanceof NewLibraryEditor) {
      mySettings.setNewLibraryEditor((NewLibraryEditor)item);
    }
    else {
      mySettings.setNewLibraryEditor(null);
    }
    return mySettings;
  }

  public JComponent getMainPanel() {
    return myRootPanel;
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  private static class LibraryListCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value == null) {
        append("[No library selected]");
      }
      else if (value instanceof ExistingLibraryEditor) {
        ProjectStructureDialogCellAppearanceUtils.forLibrary(((ExistingLibraryEditor)value).getLibrary(), null).customize(this);
      }
      else if (value instanceof NewLibraryEditor) {
        setIcon(PlatformIcons.LIBRARY_ICON);
        final String name = ((NewLibraryEditor)value).getName();
        append(name != null ? name : "<unnamed>");
      }
    }
  }
}
