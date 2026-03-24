// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileSetVersions;
import com.intellij.util.ui.RadioButtonEnumModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * @author Dmitry Avdeev
 */
public class LibraryOptionsPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(LibraryOptionsPanel.class);

  private final JBLabel myMessageLabel;
  private final JPanel myPanel;
  private final JButton myConfigureButton;
  private final JComboBox<LibraryEditor> myExistingLibraryComboBox;
  private final JRadioButton myDoNotCreateRadioButton;
  private final JPanel myConfigurationPanel;
  private final JButton myCreateButton;
  private final JRadioButton myDownloadRadioButton;
  private final JRadioButton myUseLibraryRadioButton;
  private final JLabel myUseLibraryLabel;
  private final JLabel myHiddenLabel;
  private final JPanel myRootPanel;
  private final JRadioButton myUseFromProviderRadioButton;
  private final JPanel mySimplePanel;
  private final ButtonGroup myButtonGroup;

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

  public LibraryOptionsPanel(final @NotNull CustomLibraryDescription libraryDescription,
                             final @NotNull String path,
                             final @NotNull FrameworkLibraryVersionFilter versionFilter,
                             final @NotNull LibrariesContainer librariesContainer,
                             final boolean showDoNotCreateOption) {

    this(libraryDescription, () -> path, versionFilter, librariesContainer, showDoNotCreateOption);
  }

  public LibraryOptionsPanel(final @NotNull CustomLibraryDescription libraryDescription,
                             final @NotNull NotNullComputable<String> pathProvider,
                             final @NotNull FrameworkLibraryVersionFilter versionFilter,
                             final @NotNull LibrariesContainer librariesContainer,
                             final boolean showDoNotCreateOption) {
    myLibraryDescription = libraryDescription;
    myLibrariesContainer = librariesContainer;
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
      myRootPanel = new JPanel();
      myRootPanel.setLayout(new CardLayout(0, 0));
      panel1.add(myRootPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.setEnabled(false);
      myRootPanel.add(myPanel, "editing");
      myDownloadRadioButton = new JRadioButton();
      myDownloadRadioButton.setSelected(false);
      this.$$$loadButtonText$$$(myDownloadRadioButton, this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "radio.button.download"));
      myPanel.add(myDownloadRadioButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(207, 22), null, 0,
                                                             false));
      myDoNotCreateRadioButton = new JRadioButton();
      this.$$$loadButtonText$$$(myDoNotCreateRadioButton,
                                this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "radio.button.set.up.library.later"));
      myPanel.add(myDoNotCreateRadioButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer1 = new Spacer();
      myPanel.add(spacer1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                              0, false));
      mySimplePanel = new JPanel();
      mySimplePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      panel2.add(mySimplePanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
      myExistingLibraryComboBox = new JComboBox();
      mySimplePanel.add(myExistingLibraryComboBox,
                        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                            false));
      myCreateButton = new JButton();
      this.$$$loadButtonText$$$(myCreateButton, this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "button.create"));
      mySimplePanel.add(myCreateButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myUseLibraryRadioButton = new JRadioButton();
      myUseLibraryRadioButton.setSelected(true);
      this.$$$loadButtonText$$$(myUseLibraryRadioButton,
                                this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "radio.button.use.library"));
      panel2.add(myUseLibraryRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myUseLibraryLabel = new JLabel();
      this.$$$loadLabelText$$$(myUseLibraryLabel, this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "label.use.library"));
      myUseLibraryLabel.setVisible(true);
      panel2.add(myUseLibraryLabel,
                 new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myConfigurationPanel = new JPanel();
      myConfigurationPanel.setLayout(new CardLayout(0, 0));
      myPanel.add(myConfigurationPanel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
      final JPanel panel3 = new JPanel();
      panel3.setLayout(new GridLayoutManager(1, 3, new Insets(10, 0, 0, 0), -1, -1));
      myConfigurationPanel.add(panel3, "configure");
      myConfigureButton = new JButton();
      this.$$$loadButtonText$$$(myConfigureButton, this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "button.configure"));
      panel3.add(myConfigureButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JPanel panel4 = new JPanel();
      panel4.setLayout(new CardLayout(0, 0));
      panel3.add(panel4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                             0, false));
      myMessageLabel = new JBLabel();
      myMessageLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
      myMessageLabel.setEnabled(true);
      myMessageLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
      this.$$$loadLabelText$$$(myMessageLabel,
                               this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "label.library.will.be.created.description.text"));
      panel4.add(myMessageLabel, "message");
      myHiddenLabel = new JLabel();
      this.$$$loadLabelText$$$(myHiddenLabel, this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "label.label"));
      panel4.add(myHiddenLabel, "hidden");
      final Spacer spacer2 = new Spacer();
      panel3.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      final JPanel panel5 = new JPanel();
      panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
      myConfigurationPanel.add(panel5, "empty");
      myUseFromProviderRadioButton = new JRadioButton();
      this.$$$loadButtonText$$$(myUseFromProviderRadioButton,
                                this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "radio.button.use.library.from.0"));
      myPanel.add(myUseFromProviderRadioButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
      final JPanel panel6 = new JPanel();
      panel6.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
      myRootPanel.add(panel6, "loading");
      final JLabel label1 = new JLabel();
      this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/JavaUiBundle", "label.loading.versions"));
      panel6.add(label1,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer3 = new Spacer();
      panel6.add(spacer3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      myButtonGroup = new ButtonGroup();
      myButtonGroup.add(myUseLibraryRadioButton);
      myButtonGroup.add(myDownloadRadioButton);
      myButtonGroup.add(myDoNotCreateRadioButton);
      myButtonGroup.add(myUseFromProviderRadioButton);
    }
    final DownloadableLibraryDescription description = getDownloadableDescription(libraryDescription);
    if (description != null) {
      showCard("loading");
      description.fetchVersions(new DownloadableFileSetVersions.FileSetVersionsCallback<>() {
        @Override
        public void onSuccess(final @NotNull List<? extends FrameworkLibraryVersion> versions) {
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

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  private @Nullable String getPresentableVersion() {
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

  private static @Nullable DownloadableLibraryDescription getDownloadableDescription(CustomLibraryDescription libraryDescription) {
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
    //todo create mySettings only in apply() method
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
      case DOWNLOAD -> {
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
      }
      case USE_LIBRARY -> {
        final Object item = myExistingLibraryComboBox.getSelectedItem();
        if (item instanceof LibraryEditor) {
          EditLibraryDialog dialog = new EditLibraryDialog(myPanel, mySettings, (LibraryEditor)item);
          dialog.show();
          if (item instanceof ExistingLibraryEditor) {
            WriteAction.run(() -> ((ExistingLibraryEditor)item).commit());
          }
        }
      }
      case USE_FROM_PROVIDER, SETUP_LIBRARY_LATER -> {
      }
    }
    updateState();
  }

  public void setLibraryProvider(@Nullable FrameworkLibraryProvider provider) {
    if (provider != null &&
        !ContainerUtil.intersects(provider.getAvailableLibraryKinds(), myLibraryDescription.getSuitableLibraryKinds())) {
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
      final NewLibraryEditor libraryEditor =
        new NewLibraryEditor(libraryConfiguration.getLibraryType(), libraryConfiguration.getProperties());
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
          ||
          LibraryPresentationManager.getInstance()
            .isLibraryOfKind(library, myLibrariesContainer, myLibraryDescription.getSuitableLibraryKinds())) {
        suitableLibraries.add(library);
      }
    }
    return suitableLibraries;
  }

  private @Nullable VirtualFile getBaseDirectory() {
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
        else if (item instanceof NewLibraryEditor libraryEditor) {
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

  public @Nullable LibraryCompositionSettings apply() {
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
