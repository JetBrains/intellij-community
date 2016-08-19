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
package com.intellij.facet.impl.ui.libraries;

import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.frameworkSupport.OldCustomLibraryDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileSetVersions;
import com.intellij.util.ui.RadioButtonEnumModel;
import com.intellij.xml.util.XmlStringUtil;
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
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class LibraryOptionsPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel");

  private JBLabel myMessageLabel;
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
  private JRadioButton myUseFromProviderRadioButton;
  private JPanel mySimplePanel;
  private ButtonGroup myButtonGroup;

  private LibraryCompositionSettings mySettings;
  private final CustomLibraryDescription myLibraryDescription;
  private final LibrariesContainer myLibrariesContainer;
  private SortedComboBoxModel<LibraryEditor> myLibraryComboBoxModel;
  private FrameworkLibraryProvider myLibraryProvider;
  private boolean myDisposed;

  private enum Choice {
    USE_LIBRARY,
    DOWNLOAD,
    SETUP_LIBRARY_LATER,
    USE_FROM_PROVIDER
  }

  private RadioButtonEnumModel<Choice> myButtonEnumModel;

  public LibraryOptionsPanel(@NotNull final CustomLibraryDescription libraryDescription,
                             @NotNull final String path,
                             @NotNull final FrameworkLibraryVersionFilter versionFilter,
                             @NotNull final LibrariesContainer librariesContainer,
                             final boolean showDoNotCreateOption) {

    this(libraryDescription, () -> path, versionFilter, librariesContainer, showDoNotCreateOption);
  }

  public LibraryOptionsPanel(@NotNull final CustomLibraryDescription libraryDescription,
                             @NotNull final NotNullComputable<String> pathProvider,
                             @NotNull final FrameworkLibraryVersionFilter versionFilter,
                             @NotNull final LibrariesContainer librariesContainer,
                             final boolean showDoNotCreateOption) {
    myLibraryDescription = libraryDescription;
    myLibrariesContainer = librariesContainer;
    final DownloadableLibraryDescription description = getDownloadableDescription(libraryDescription);
    if (description != null) {
      showCard("loading");
      description.fetchVersions(new DownloadableFileSetVersions.FileSetVersionsCallback<FrameworkLibraryVersion>() {
        @Override
        public void onSuccess(@NotNull final List<? extends FrameworkLibraryVersion> versions) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            if (!myDisposed) {
              showSettingsPanel(libraryDescription, pathProvider, versionFilter, showDoNotCreateOption, versions);
              onVersionChanged(getPresentableVersion());
            }
          });
        }
      });
    }
    else {
      showSettingsPanel(libraryDescription, pathProvider, versionFilter, showDoNotCreateOption,
                        new ArrayList<>());
    }
  }

  @Nullable
  private String getPresentableVersion() {
    switch (myButtonEnumModel.getSelected()) {
      case DOWNLOAD:
        LibraryDownloadSettings settings = mySettings.getDownloadSettings();
        if (settings != null) {
          return settings.getVersion().getVersionNumber();
        }
        break;
      case USE_LIBRARY:
        LibraryEditor item = myLibraryComboBoxModel.getSelectedItem();
        if (item instanceof ExistingLibraryEditor) {
          return item.getName();
        }
        break;
      default:
        return null;
    }
    return null;
  }

  protected void onVersionChanged(@Nullable String version) {
  }

  public JPanel getSimplePanel() {
    return mySimplePanel;
  }

  @Nullable
  private static DownloadableLibraryDescription getDownloadableDescription(CustomLibraryDescription libraryDescription) {
    final DownloadableLibraryType type = libraryDescription.getDownloadableLibraryType();
    if (type != null) return type.getLibraryDescription();
    if (libraryDescription instanceof OldCustomLibraryDescription) {
      return ((OldCustomLibraryDescription)libraryDescription).getDownloadableDescription();
    }
    return null;
  }

  private void showCard(final String editing) {
    ((CardLayout)myRootPanel.getLayout()).show(myRootPanel, editing);
  }

  private void showSettingsPanel(CustomLibraryDescription libraryDescription,
                                 NotNullComputable<String> pathProvider,
                                 FrameworkLibraryVersionFilter versionFilter,
                                 boolean showDoNotCreateOption, final List<? extends FrameworkLibraryVersion> versions) {
    //todo[nik] create mySettings only in apply() method
    mySettings = new LibraryCompositionSettings(libraryDescription, pathProvider, versionFilter, versions);
    Disposer.register(this, mySettings);
    List<Library> libraries = calculateSuitableLibraries();

    myButtonEnumModel = RadioButtonEnumModel.bindEnum(Choice.class, myButtonGroup);
    myButtonEnumModel.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateState();
        onVersionChanged(getPresentableVersion());
      }
    });

    myDoNotCreateRadioButton.setVisible(showDoNotCreateOption);
    myLibraryComboBoxModel = new SortedComboBoxModel<>((o1, o2) -> {
      final String name1 = o1.getName();
      final String name2 = o2.getName();
      return -StringUtil.notNullize(name1).compareToIgnoreCase(StringUtil.notNullize(name2));
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
        onVersionChanged(getPresentableVersion());
      }
    });
    myExistingLibraryComboBox.setRenderer(new ColoredListCellRenderer(myExistingLibraryComboBox) {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          append("[No library selected]");
        }
        else if (value instanceof ExistingLibraryEditor) {
          final Library library = ((ExistingLibraryEditor)value).getLibrary();
          final boolean invalid = !((LibraryEx)library).getInvalidRootUrls(OrderRootType.CLASSES).isEmpty();
          OrderEntryAppearanceService.getInstance().forLibrary(getProject(), library, invalid).customize(this);
        }
        else if (value instanceof NewLibraryEditor) {
          setIcon(PlatformIcons.LIBRARY_ICON);
          final String name = ((NewLibraryEditor)value).getName();
          append(name != null ? name : "<unnamed>");
        }
      }
    });

    boolean canDownload = mySettings.getDownloadSettings() != null;
    boolean canUseFromProvider = myLibraryProvider != null;
    myDownloadRadioButton.setVisible(canDownload);
    myUseFromProviderRadioButton.setVisible(canUseFromProvider);
    Choice selectedOption;
    if (canUseFromProvider) {
      selectedOption = Choice.USE_FROM_PROVIDER;
    }
    else if (libraries.isEmpty() && canDownload) {
      selectedOption = Choice.DOWNLOAD;
    }
    else {
      selectedOption = Choice.USE_LIBRARY;
      doCreate(true);
    }
    myButtonEnumModel.setSelected(selectedOption);

    if (!canDownload && !canUseFromProvider && !showDoNotCreateOption) {
      myUseLibraryRadioButton.setVisible(false);
      myUseLibraryLabel.setVisible(true);
    }
    else {
      myUseLibraryLabel.setVisible(false);
    }

    final Dimension minimumSize = new Dimension(-1, myMessageLabel.getFontMetrics(myMessageLabel.getFont()).getHeight() * 2);
    myHiddenLabel.setMinimumSize(minimumSize);

    myCreateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doCreate(false);
      }
    });
    myConfigureButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, () -> doConfigure());
      }
    });
    updateState();
    showCard("editing");
  }

  private Project getProject() {
    Project project = myLibrariesContainer.getProject();
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    return project;
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
              protected void run(@NotNull final Result result) {
                ((ExistingLibraryEditor)item).commit();
              }
            }.execute();
          }
        }
        break;

      case USE_FROM_PROVIDER:
      case SETUP_LIBRARY_LATER:
        break;
    }
    updateState();
  }

  public void setLibraryProvider(@Nullable FrameworkLibraryProvider provider) {
    if (provider != null && !ContainerUtil.intersects(provider.getAvailableLibraryKinds(), myLibraryDescription.getSuitableLibraryKinds())) {
      provider = null;
    }

    if (!Comparing.equal(myLibraryProvider, provider)) {
      myLibraryProvider = provider;

      if (mySettings != null) {
        if (provider != null && !myUseFromProviderRadioButton.isVisible()) {
          myUseFromProviderRadioButton.setSelected(true);
        }
        myUseFromProviderRadioButton.setVisible(provider != null);
        updateState();
      }
    }
  }

  public void setVersionFilter(@NotNull FrameworkLibraryVersionFilter versionFilter) {
    if (mySettings != null) {
      mySettings.setVersionFilter(versionFilter);
      updateState();
    }
  }

  private void doCreate(boolean useDefaultSettings) {
    final NewLibraryConfiguration libraryConfiguration = useDefaultSettings
                                                         ? myLibraryDescription.createNewLibraryWithDefaultSettings(getBaseDirectory())
                                                         : myLibraryDescription.createNewLibrary(myCreateButton, getBaseDirectory());
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
    List<Library> suitableLibraries = new ArrayList<>();
    for (Library library : myLibrariesContainer.getAllLibraries()) {
      if (myLibraryDescription instanceof OldCustomLibraryDescription &&
          ((OldCustomLibraryDescription)myLibraryDescription).isSuitableLibrary(library, myLibrariesContainer)
          || LibraryPresentationManager.getInstance().isLibraryOfKind(library, myLibrariesContainer, myLibraryDescription.getSuitableLibraryKinds())) {
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
    final LibraryDownloadSettings settings = mySettings.getDownloadSettings();
    myDownloadRadioButton.setVisible(settings != null);
    myUseFromProviderRadioButton.setVisible(myLibraryProvider != null);
    if (!myUseFromProviderRadioButton.isVisible() && myUseFromProviderRadioButton.isSelected()) {
      if (myDownloadRadioButton.isVisible()) {
        myDownloadRadioButton.setSelected(true);
      }
      else {
        myUseLibraryRadioButton.setSelected(true);
      }
    }
    if (!myDownloadRadioButton.isVisible() && myDownloadRadioButton.isSelected() && myUseLibraryRadioButton.isVisible()) {
      myUseLibraryRadioButton.setSelected(true);
    }
    String message = "";
    boolean showConfigurePanel = true;
    switch (myButtonEnumModel.getSelected()) {
      case DOWNLOAD:
        message = getDownloadFilesMessage();
        break;
      case USE_FROM_PROVIDER:
        if (myLibraryProvider != null) {
          message = "Library from " + myLibraryProvider.getPresentableName() + " will be used";
        }
        myConfigureButton.setVisible(false);
        break;
      case USE_LIBRARY:
        final Object item = myExistingLibraryComboBox.getSelectedItem();
        if (item == null) {
          myMessageLabel.setIcon(AllIcons.RunConfigurations.ConfigurationWarning);
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

    if (myLibraryProvider != null) {
      myUseFromProviderRadioButton.setText("Use library from " + myLibraryProvider.getPresentableName());
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
    myMessageLabel.setText(XmlStringUtil.wrapInHtml(message));
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
    return MessageFormat.format("{0} {0, choice, 1#JAR|2#JARs} will be downloaded into <b>{1}</b> directory<br>" +
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

    mySettings.setLibraryProvider(option == Choice.USE_FROM_PROVIDER ? myLibraryProvider : null);
    return mySettings;
  }

  public JComponent getMainPanel() {
    return myRootPanel;
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }
}
