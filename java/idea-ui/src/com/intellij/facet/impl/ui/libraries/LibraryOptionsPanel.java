// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl.ui.libraries;

import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.frameworkSupport.OldCustomLibraryDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.util.NlsContexts;
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
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class LibraryOptionsPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(LibraryOptionsPanel.class);

  private JBLabel myMessageLabel;
  private JPanel myPanel;
  private JButton myConfigureButton;
  private JComboBox<LibraryEditor> myExistingLibraryComboBox;
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
      description.fetchVersions(new DownloadableFileSetVersions.FileSetVersionsCallback<>() {
        @Override
        public void onSuccess(@NotNull final List<? extends FrameworkLibraryVersion> versions) {
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
      case DOWNLOAD -> {
        LibraryDownloadSettings settings = mySettings.getDownloadSettings();
        if (settings != null) {
          return settings.getVersion().getVersionNumber();
        }
      }
      case USE_LIBRARY -> {
        LibraryEditor item = myLibraryComboBoxModel.getSelectedItem();
        if (item instanceof ExistingLibraryEditor) {
          return item.getName();
        }
      }
      default -> {
        return null;
      }
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
    myExistingLibraryComboBox.setRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends LibraryEditor> list, LibraryEditor value, int index, boolean selected,
                                           boolean hasFocus) {
        if (value == null) {
          append(JavaUiBundle.message("library.options.panel.existing.library.combobox.label.no.library.selected"));
        }
        else if (value instanceof ExistingLibraryEditor) {
          final Library library = ((ExistingLibraryEditor)value).getLibrary();
          final boolean invalid = !((LibraryEx)library).getInvalidRootUrls(OrderRootType.CLASSES).isEmpty();
          OrderEntryAppearanceService.getInstance().forLibrary(getProject(), library, invalid).customize(this);
        }
        else if (value instanceof NewLibraryEditor) {
          setIcon(PlatformIcons.LIBRARY_ICON);
          final String name = value.getName();
          append(name != null ? name : JavaUiBundle.message("unnamed.title"));
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
      @Override
      public void actionPerformed(final ActionEvent e) {
        doConfigure();
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
        List<? extends FrameworkLibraryVersion> versions = mySettings.getCompatibleVersions();
        if (versions.isEmpty()) {
          LOG.error("No compatible version for " + mySettings.getLibraryDescription() + " with filter " + mySettings.getVersionFilter());
        }
        final LibraryDownloadSettings newDownloadSettings = DownloadingOptionsDialog.showDialog(myPanel, oldDownloadSettings,
                                                                                                versions, true);
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
            WriteAction.run(() -> ((ExistingLibraryEditor)item).commit());
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
      int index = path.lastIndexOf('/');
      if (index >= 0) {
        path = path.substring(0, index);
        dir = LocalFileSystem.getInstance().findFileByPath(path);
      }
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
      case DOWNLOAD -> message = getDownloadFilesMessage();
      case USE_FROM_PROVIDER -> {
        if (myLibraryProvider != null) {
          message =
            JavaUiBundle.message("library.options.panel.update.state.library.from.0.will.be.used", myLibraryProvider.getPresentableName());
        }
        myConfigureButton.setVisible(false);
      }
      case USE_LIBRARY -> {
        final Object item = myExistingLibraryComboBox.getSelectedItem();
        if (item == null) {
          myMessageLabel.setIcon(AllIcons.General.BalloonError);
          message = JavaUiBundle.message("library.options.panel.update.state.error.library.is.not.specified");
          myConfigureButton.setVisible(false);
        }
        else if (item instanceof NewLibraryEditor) {
          final LibraryEditor libraryEditor = (LibraryEditor)item;
          message = JavaUiBundle.message("label.library.will.be.created.description.text", mySettings.getNewLibraryLevel(),
                                         libraryEditor.getName(), libraryEditor.getFiles(OrderRootType.CLASSES).length);
        }
        else {
          message = JavaUiBundle.message("label.existing.library.will.be.used", ((ExistingLibraryEditor)item).getName());
        }
      }
      default -> showConfigurePanel = false;
    }

    if (myLibraryProvider != null) {
      myUseFromProviderRadioButton.setText(JavaUiBundle.message("radio.button.use.library.from.0", myLibraryProvider.getPresentableName()));
    }

    //show the longest message on the hidden card to ensure that dialog won't jump if user selects another option
    if (mySettings.getDownloadSettings() != null) {
      myHiddenLabel.setText(getDownloadFilesMessage());
    }
    else {
      myHiddenLabel.setText(JavaUiBundle.message("label.library.will.be.created.description.text", mySettings.getNewLibraryLevel(),
                                              "name", 10));
    }
    ((CardLayout)myConfigurationPanel.getLayout()).show(myConfigurationPanel, showConfigurePanel ? "configure" : "empty");
    myMessageLabel.setText(XmlStringUtil.wrapInHtml(message));
  }

  private @NlsContexts.Label String getDownloadFilesMessage() {
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
    return JavaUiBundle.message("library.options.panel.update.state.download.files.message",
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
