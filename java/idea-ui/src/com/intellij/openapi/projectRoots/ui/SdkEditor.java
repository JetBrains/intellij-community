// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.ui;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.plugins.newui.TwoLineProgressIndicator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.SdkEditorAdditionalOptionsProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.status.InlineProgressIndicator;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

/**
 * @author MYakovlev
 */
public class SdkEditor implements Configurable, Place.Navigator {
  private static final Logger LOG = Logger.getInstance(SdkEditor.class);
  private static final String SDK_TAB = "sdkTab";

  @NotNull
  private final Sdk mySdk;
  private final Map<OrderRootType, SdkPathEditor> myPathEditors = new HashMap<>();

  private TextFieldWithBrowseButton myHomeComponent;
  private final Map<SdkType, List<AdditionalDataConfigurable>> myAdditionalDataConfigurables = new HashMap<>();
  private final Map<AdditionalDataConfigurable, JComponent> myAdditionalDataComponents = new HashMap<>();
  private JPanel myAdditionalDataPanel;
  private JPanel myDownloadingPanel;
  private InlineProgressIndicator myDownloadProgressIndicator;
  private final SdkModificator myEditedSdkModificator = new EditedSdkModificator();

  // GUI components
  private JPanel myMainPanel;
  private TabbedPaneWrapper myTabbedPane;
  private final Project myProject;
  private final ProjectSdksModel mySdkModel;
  private JLabel myHomeFieldLabel;
  private String myVersionString;

  private String myInitialName;
  private String myModifiedName;
  private String myInitialPath;
  private boolean myIsDownloading = false;
  private final History myHistory;

  private final Disposable myDisposable = Disposer.newDisposable();

  private boolean myIsDisposed = false;
  private final Consumer<Boolean> myResetCallback = __ -> {
    if (!myIsDisposed) reset();
  };

  public SdkEditor(@NotNull Project project,
                   @NotNull ProjectSdksModel sdkModel,
                   @NotNull History history,
                   @NotNull Sdk sdk) {
    myProject = project;
    mySdkModel = sdkModel;
    myHistory = history;
    mySdk = sdk;
    myInitialName = mySdk.getName();
    myModifiedName = myInitialName;
    myInitialPath = mySdk.getHomePath();
    createMainPanel();
    for (final AdditionalDataConfigurable additionalDataConfigurable : getAdditionalDataConfigurable()) {
      additionalDataConfigurable.setSdk(sdk);
    }
    reset();
  }

  @Override
  public String getDisplayName() {
    return JavaUiBundle.message("sdk.configure.editor.title");
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  private void createMainPanel() {
    myMainPanel = new JPanel(new GridBagLayout());

    myTabbedPane = new TabbedPaneWrapper(myDisposable);
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      if (showTabForType(type)) {
        final SdkPathEditor pathEditor = OrderRootTypeUIFactory.FACTORY.getByKey(type).createPathEditor(mySdk);
        if (pathEditor != null) {
          pathEditor.setAddBaseDir(mySdk.getHomeDirectory());
          myTabbedPane.addTab(pathEditor.getDisplayName(), pathEditor.createComponent());
          myPathEditors.put(type, pathEditor);
        }
      }
    }

    myTabbedPane.addChangeListener(e -> myHistory.pushQueryPlace());

    myHomeComponent = createHomeComponent();
    final JTextField textField = myHomeComponent.getTextField();
    textField.setEditable(false);
    textField.addHierarchyListener(new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0 || !textField.isShowing()) { return; }
        checkHomePathValidity();
      }
    });

    myHomeFieldLabel = new JLabel(getHomeFieldLabelValue());
    myHomeFieldLabel.setLabelFor(myHomeComponent.getTextField());
    myMainPanel.add(myHomeFieldLabel, new GridBagConstraints(
      0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(2, 10, 2, 2), 0, 0));
    myMainPanel.add(myHomeComponent, new GridBagConstraints(
      1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, JBUI.insets(2, 2, 2, 10), 0, 0));

    myAdditionalDataPanel = new JPanel(new BorderLayout());
    myMainPanel.add(myAdditionalDataPanel, new GridBagConstraints(
      0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insets(2, 10, 0, 10), 0, 0));

    myMainPanel.add(myTabbedPane.getComponent(), new GridBagConstraints(
      0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insetsTop(2), 0, 0));

    myDownloadingPanel = new JPanel(new BorderLayout());
    //myDownloadingPanel.add(new JBLabel("Downloading JDK..."), BorderLayout.NORTH);
    myDownloadProgressIndicator = new TwoLineProgressIndicator(true);
    myDownloadProgressIndicator.setIndeterminate(true);
    myDownloadingPanel.add(myDownloadProgressIndicator.getComponent(), BorderLayout.NORTH);
    myDownloadProgressIndicator.getComponent().setMaximumSize(JBUI.size(300, 200));

    myMainPanel.add(myDownloadingPanel, new GridBagConstraints(
      0, GridBagConstraints.RELATIVE, 2, 1, 0, 1.0, GridBagConstraints.SOUTH, GridBagConstraints.BOTH, JBUI.insets(8, 10, 0, 10), 0, 0));
  }

  protected TextFieldWithBrowseButton createHomeComponent() {
    return new TextFieldWithBrowseButton(e -> doSelectHomePath());
  }

  protected boolean showTabForType(@NotNull OrderRootType type) {
    return ((SdkType)mySdk.getSdkType()).isRootTypeApplicable(type);
  }

  private @NlsContexts.Label String getHomeFieldLabelValue() {
    return ((SdkType)mySdk.getSdkType()).getHomeFieldLabel();
  }

  @Override
  public boolean isModified() {
    boolean isModified = !Objects.equals(myModifiedName, myInitialName);
    if (myIsDownloading) return isModified;

    isModified =
      isModified || !Objects.equals(FileUtil.toSystemIndependentName(getHomeValue()), FileUtil.toSystemIndependentName(myInitialPath));
    for (PathEditor pathEditor : myPathEditors.values()) {
      isModified = isModified || pathEditor.isModified();
    }
    for (final AdditionalDataConfigurable configurable : getAdditionalDataConfigurable()) {
      isModified = isModified || configurable.isModified();
    }
    return isModified;
  }

  public void setNewSdkName(String name) {
    myModifiedName = name;
  }

  @NlsSafe
  public String getActualSdkName() {
    return myModifiedName;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myIsDownloading) return;

    if (!Objects.equals(myInitialName, myModifiedName)) {
      if (myModifiedName.isEmpty()) {
        throw new ConfigurationException(ProjectBundle.message("sdk.list.name.required.error"));
      }
    }
    myInitialName = myModifiedName;
    myInitialPath = mySdk.getHomePath();
    SdkModificator sdkModificator = mySdk.getSdkModificator();
    sdkModificator.setName(myModifiedName);
    sdkModificator.setHomePath(FileUtil.toSystemIndependentName(getHomeValue()));
    for (SdkPathEditor pathEditor : myPathEditors.values()) {
      pathEditor.apply(sdkModificator);
    }
    ApplicationManager.getApplication().runWriteAction(sdkModificator::commitChanges);
    for (final AdditionalDataConfigurable configurable : getAdditionalDataConfigurable()) {
      if (configurable != null) {
        configurable.apply();
      }
    }
  }

  @Override
  public void reset() {
    myIsDownloading = SdkDownloadTracker.getInstance().tryRegisterDownloadingListener(mySdk, myDisposable, myDownloadProgressIndicator, myResetCallback);
    if (!myIsDownloading) {
      final SdkModificator sdkModificator = mySdk.getSdkModificator();
      for (OrderRootType type : myPathEditors.keySet()) {
        myPathEditors.get(type).reset(sdkModificator);
      }
      ApplicationManager.getApplication().runWriteAction(sdkModificator::commitChanges);
    }

    setHomePathValue(FileUtil.toSystemDependentName(ObjectUtils.notNull(mySdk.getHomePath(), "")));
    myVersionString = null;
    myHomeFieldLabel.setText(getHomeFieldLabelValue());

    myTabbedPane.getComponent().setVisible(!myIsDownloading);
    myAdditionalDataPanel.setVisible(!myIsDownloading);
    myDownloadingPanel.setVisible(myIsDownloading);
    myHomeComponent.setEnabled(!myIsDownloading);

    if (!myIsDownloading) {
      updateAdditionalDataComponent();

      for (final AdditionalDataConfigurable configurable : getAdditionalDataConfigurable()) {
        configurable.reset();
      }

      for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
        myTabbedPane.setEnabledAt(i, true);
      }
    }
  }

  @Override
  public void disposeUIResources() {
    myIsDisposed = true;
    for (final SdkType sdkType : myAdditionalDataConfigurables.keySet()) {
      for (final AdditionalDataConfigurable configurable : myAdditionalDataConfigurables.get(sdkType)) {
        configurable.disposeUIResources();
      }
    }
    myAdditionalDataConfigurables.clear();
    myAdditionalDataComponents.clear();

    Disposer.dispose(myDisposable);
  }

  private String getHomeValue() {
    return myHomeComponent.getText().trim();
  }

  private void clearAllPaths() {
    for (PathEditor editor : myPathEditors.values()) {
      editor.clearList();
    }
  }

  private void setHomePathValue(@NlsSafe String absolutePath) {
    myHomeComponent.setText(absolutePath);
    myHomeComponent.getTextField().setForeground(UIUtil.getFieldForegroundColor());

    if (myHomeComponent.isShowing()) {
      checkHomePathValidity();
    }
  }

  private void checkHomePathValidity() {
    final JTextField textField = myHomeComponent.getTextField();
    if (textField.getText().isEmpty() && !mySdk.getSdkType().isLocalSdk(mySdk)) {
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      var homeDir = Path.of(textField.getText());
      var homeMustBeDirectory = ((SdkType)mySdk.getSdkType()).getHomeChooserDescriptor().isChooseFolders();
      var isValid = homeMustBeDirectory ? Files.isDirectory(homeDir) : Files.isRegularFile(homeDir);

      ApplicationManager.getApplication().invokeLater(
        () -> textField.setForeground(isValid ? UIUtil.getFieldForegroundColor() : PathEditor.INVALID_COLOR),
        ModalityState.stateForComponent(myHomeComponent)
      );
    });
  }

  private void doSelectHomePath() {
    final SdkType sdkType = (SdkType)mySdk.getSdkType();

    //handle tests behaviour
    if (SdkConfigurationUtil.selectSdkHomeForTests(sdkType, path -> doSetHomePath(path, sdkType))) {
      return;
    }

    SdkPopupFactory
      .newBuilder()
      .withSdkType(sdkType)
      .withSdkFilter(sdk -> {
        if (sdk.getName().equals(this.myInitialName)) return false;
        if (sdk.getName().equals(this.myModifiedName)) return false;

        if (FileUtil.pathsEqual(sdk.getHomePath(), mySdk.getHomePath())) return false;

        return true;
      })
      .onSdkSelected(sdk -> {
        SdkDownloadTracker tracker = SdkDownloadTracker.getInstance();
        if (tracker.isDownloading(sdk)) {
          //make sure the current SDK is registered as downloading one
          tracker.registerEditableSdk(sdk, mySdk);

          //we need to bind with the original Sdk too
          var originalSdkEntry = ContainerUtil.find(mySdkModel.getProjectSdks().entrySet(), p -> p.getValue().equals(mySdk));
          if (originalSdkEntry != null) {
            tracker.registerEditableSdk(sdk, originalSdkEntry.getKey());
          }

          //reset the view to make it bind to the downloading JDK
          reset();
        } else {
          doSetHomePath(sdk.getHomePath(), sdkType);
        }
      })
      .withOwnProjectSdksModel(new ProjectSdksModel() {
        @Override
        protected boolean forceAddActionToSelectFromDisk(@NotNull SdkType type) {
          //make the `Add` action use the original SdkConfigurationUtil.selectSdkHome method
          return true;
        }
      })
      .buildPopup()
      .showUnderneathToTheRightOf(myHomeComponent);
  }

  private void doSetHomePath(final String homePath, final SdkType sdkType) {
    if (homePath == null) {
      return;
    }
    setHomePathValue(homePath.replace('/', File.separatorChar));

    try {
      final Sdk dummySdk = mySdk.clone();
      SdkModificator sdkModificator = dummySdk.getSdkModificator();
      sdkModificator.setHomePath(homePath);
      sdkModificator.removeAllRoots();
      sdkModificator.commitChanges();

      sdkType.setupSdkPaths(dummySdk, mySdkModel);

      clearAllPaths();
      myVersionString = dummySdk.getVersionString();
      if (myVersionString == null) {
        Messages.showMessageDialog(ProjectBundle.message("sdk.java.corrupt.error", homePath),
                                   ProjectBundle.message("sdk.java.corrupt.title"), Messages.getErrorIcon());
      }
      sdkModificator = dummySdk.getSdkModificator();
      for (OrderRootType type : myPathEditors.keySet()) {
        SdkPathEditor pathEditor = myPathEditors.get(type);
        pathEditor.setAddBaseDir(dummySdk.getHomeDirectory());
        pathEditor.addPaths(sdkModificator.getRoots(type));
      }
      mySdkModel.getMulticaster().sdkHomeSelected(dummySdk, homePath);
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e); // should not happen in normal program
    }
  }

  private void updateAdditionalDataComponent() {
    myAdditionalDataPanel.removeAll();
    for (AdditionalDataConfigurable configurable : getAdditionalDataConfigurable()) {
      JComponent component = myAdditionalDataComponents.get(configurable);
      if (component == null) {
        component = configurable.createComponent();
        myAdditionalDataComponents.put(configurable, component);
      }
      if (component != null) {
        if (configurable.getTabName() != null) {
          for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            if (configurable.getTabName().equals(myTabbedPane.getTitleAt(i))) {
              myTabbedPane.removeTabAt(i);
            }
          }
          myTabbedPane.addTab(configurable.getTabName(), component);
        }
        else {
          myAdditionalDataPanel.add(component, BorderLayout.CENTER);
        }
      }
    }
  }

  @NotNull
  private List<AdditionalDataConfigurable> getAdditionalDataConfigurable() {
    return initAdditionalDataConfigurable(mySdk);
  }

  @NotNull
  private List<AdditionalDataConfigurable> initAdditionalDataConfigurable(Sdk sdk) {
    final SdkType sdkType = (SdkType)sdk.getSdkType();
    List<AdditionalDataConfigurable> configurables = myAdditionalDataConfigurables.get(sdkType);
    if (configurables == null) {
      configurables = new ArrayList<>();
      myAdditionalDataConfigurables.put(sdkType, configurables);


      AdditionalDataConfigurable sdkConfigurable = sdkType.createAdditionalDataConfigurable(mySdkModel, myEditedSdkModificator);
      if (sdkConfigurable != null) {
        configurables.add(sdkConfigurable);
      }

      for (SdkEditorAdditionalOptionsProvider factory : SdkEditorAdditionalOptionsProvider.getSdkOptionsFactory(mySdk.getSdkType())) {
        AdditionalDataConfigurable options = factory.createOptions(myProject, mySdk);
        if (options != null) {
          configurables.add(options);
        }
      }
    }

    return configurables;
  }

  private class EditedSdkModificator implements SdkModificator {
    @NotNull
    @Override
    public String getName() {
      return mySdk.getName();
    }

    @Override
    public void setName(@NotNull String name) {
      ((ProjectJdkImpl)mySdk).setName(name);
    }

    @Override
    public String getHomePath() {
      return getHomeValue();
    }

    @Override
    public void setHomePath(String path) {
      doSetHomePath(path, (SdkType)mySdk.getSdkType());
    }

    @Override
    public String getVersionString() {
      return myVersionString != null ? myVersionString : mySdk.getVersionString();
    }

    @Override
    public void setVersionString(String versionString) {
      throw new UnsupportedOperationException(); // not supported for this editor
    }

    @Override
    public SdkAdditionalData getSdkAdditionalData() {
      return mySdk.getSdkAdditionalData();
    }

    @Override
    public void setSdkAdditionalData(SdkAdditionalData data) {
      throw new UnsupportedOperationException(); // not supported for this editor
    }

    @Override
    public VirtualFile @NotNull [] getRoots(@NotNull OrderRootType rootType) {
      final PathEditor editor = myPathEditors.get(rootType);
      if (editor == null) throw new IllegalStateException("no editor for root type " + rootType);
      return editor.getRoots();
    }

    @Override
    public void addRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
      myPathEditors.get(rootType).addPaths(root);
    }

    @Override
    public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
      myPathEditors.get(rootType).removePaths(root);
    }

    @Override
    public void removeRoots(@NotNull OrderRootType rootType) {
      myPathEditors.get(rootType).clearList();
    }

    @Override
    public void removeAllRoots() {
      for (PathEditor editor : myPathEditors.values()) {
        editor.clearList();
      }
    }

    @Override
    public void commitChanges() { }

    @Override
    public boolean isWritable() {
      return true;
    }
  }

  @Override
  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    if (place == null) return ActionCallback.DONE;
    myTabbedPane.setSelectedTitle((String)place.getPath(SDK_TAB));
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull final Place place) {
    place.putPath(SDK_TAB, myTabbedPane.getSelectedTitle());
  }
}