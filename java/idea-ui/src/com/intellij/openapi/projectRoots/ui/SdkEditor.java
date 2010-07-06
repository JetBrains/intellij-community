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
package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.OrderRootTypeUIFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
 * @author: MYakovlev
 * Date: Aug 15, 2002
 * Time: 1:27:59 PM
 */

public class SdkEditor implements Configurable, Place.Navigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.ui.SdkEditor");
  private Sdk mySdk;
  private final Map<OrderRootType, PathEditor> myPathEditors = new HashMap<OrderRootType, PathEditor>();

  private TextFieldWithBrowseButton myHomeComponent;
  private final Map<SdkType, AdditionalDataConfigurable> myAdditionalDataConfigurables = new HashMap<SdkType, AdditionalDataConfigurable>();
  private final Map<AdditionalDataConfigurable, JComponent> myAdditionalDataComponents = new HashMap<AdditionalDataConfigurable, JComponent>();
  private JPanel myAdditionalDataPanel;
  private final SdkModificator myEditedSdkModificator = new EditedSdkModificator();

  // GUI components
  private JPanel myMainPanel;
  private TabbedPaneWrapper myTabbedPane;
  private final NotifiableSdkModel mySdkModel;
  private JLabel myHomeFieldLabel;
  private String myVersionString;

  private String myInitialName;
  private String myInitialPath;
  private final History myHistory;

  private final Disposable myDisposable = Disposer.newDisposable();

  public SdkEditor(NotifiableSdkModel sdkModel, History history, final ProjectJdkImpl sdk) {
    mySdkModel = sdkModel;
    myHistory = history;
    mySdk = sdk;
    createMainPanel();
    initSdk(sdk);
  }

  private void initSdk(Sdk sdk){
    mySdk = sdk;
    if (mySdk != null) {
      myInitialName = mySdk.getName();
      myInitialPath = mySdk.getHomePath();
    } else {
      myInitialName = "";
      myInitialPath = "";
    }
    final AdditionalDataConfigurable additionalDataConfigurable = getAdditionalDataConfigurable();
    if (additionalDataConfigurable != null) {
      additionalDataConfigurable.setSdk(sdk);
    }
    if (myMainPanel != null){
      reset();
    }
  }

  public String getDisplayName(){
    return ProjectBundle.message("sdk.configure.editor.title");
  }

  public Icon getIcon(){
    return null;
  }

  public String getHelpTopic(){
    return null;
  }

  public JComponent createComponent(){
    return myMainPanel;
  }

  private void createMainPanel(){
    myMainPanel = new JPanel(new GridBagLayout());

    myTabbedPane = new TabbedPaneWrapper(myDisposable);
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      if (mySdk == null || mySdk.getSdkType().isRootTypeApplicable(type)) {
        final PathEditor pathEditor = OrderRootTypeUIFactory.FACTORY.getByKey(type).createPathEditor(mySdk);
        myTabbedPane.addTab(pathEditor.getDisplayName(), pathEditor.createComponent());
        myPathEditors.put(type, pathEditor);
      }
    }

    myTabbedPane.addChangeListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        myHistory.pushQueryPlace();
      }
    });

    myHomeComponent = new TextFieldWithBrowseButton(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        doSelectHomePath();
      }
    });
    myHomeComponent.getTextField().setEditable(false);

    myHomeFieldLabel = new JLabel(getHomeFieldLabelValue());
    myMainPanel.add(myHomeFieldLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 2), 0, 0));
    myMainPanel.add(myHomeComponent, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 0), 0, 0));

    myAdditionalDataPanel = new JPanel(new BorderLayout());
    myMainPanel.add(myAdditionalDataPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 0, 0, 0), 0, 0));

    myMainPanel.add(myTabbedPane.getComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 0, 0, 0), 0, 0));
  }

  private String getHomeFieldLabelValue() {
    if (mySdk != null) {
      return mySdk.getSdkType().getHomeFieldLabel();
    }
    return ProjectBundle.message("sdk.configure.general.home.path");
  }

  public boolean isModified(){
    boolean isModified = !Comparing.equal(mySdk == null? null : mySdk.getName(), myInitialName);
    isModified = isModified || !Comparing.equal(FileUtil.toSystemIndependentName(getHomeValue()), FileUtil.toSystemIndependentName(myInitialPath));
    for (PathEditor pathEditor : myPathEditors.values()) {
      isModified = isModified || pathEditor.isModified();
    }
    final AdditionalDataConfigurable configurable = getAdditionalDataConfigurable();
    if (configurable != null) {
      isModified = isModified || configurable.isModified();
    }
    return isModified;
  }

  public void apply() throws ConfigurationException{
    if(!Comparing.equal(myInitialName, (mySdk == null) ? "" : mySdk.getName())){
      if(mySdk == null || mySdk.getName().length() == 0){
        throw new ConfigurationException(ProjectBundle.message("sdk.list.name.required.error"));
      }
    }
    if (mySdk != null){
      myInitialName = mySdk.getName();
      myInitialPath = mySdk.getHomePath();
      final SdkModificator sdkModificator = mySdk.getSdkModificator();
      sdkModificator.setHomePath(getHomeValue().replace(File.separatorChar, '/'));
      for (PathEditor pathEditor : myPathEditors.values()) {
        pathEditor.apply(sdkModificator);
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() { // fix SCR #29193
        public void run() {
          sdkModificator.commitChanges();
        }
      });
      final AdditionalDataConfigurable configurable = getAdditionalDataConfigurable();
      if (configurable != null) {
        configurable.apply();
      }
    }
  }

  public void reset(){
    if (mySdk == null){
      setHomePathValue("");
      for (PathEditor pathEditor : myPathEditors.values()) {
        pathEditor.reset(null);
      }
    }
    else{
      final SdkModificator sdkModificator = mySdk.getSdkModificator();
      for (OrderRootType type : myPathEditors.keySet()) {
        myPathEditors.get(type).reset(sdkModificator.getRoots(type));
      }
      sdkModificator.commitChanges();
      setHomePathValue(mySdk.getHomePath().replace('/', File.separatorChar));
    }
    myVersionString = null;
    myHomeFieldLabel.setText(getHomeFieldLabelValue());
    updateAdditionalDataComponent();
    final AdditionalDataConfigurable configurable = getAdditionalDataConfigurable();
    if (configurable != null) {
      configurable.reset();
    }

    myHomeComponent.setEnabled(mySdk != null);

    for(int i = 0; i < myTabbedPane.getTabCount(); i++){
      myTabbedPane.setEnabledAt(i, mySdk != null);
    }
  }

  public void disposeUIResources(){
    for (final SdkType sdkType : myAdditionalDataConfigurables.keySet()) {
      final AdditionalDataConfigurable configurable = myAdditionalDataConfigurables.get(sdkType);
      configurable.disposeUIResources();
    }
    myAdditionalDataConfigurables.clear();
    myAdditionalDataComponents.clear();

    Disposer.dispose(myDisposable);
  }

  private String getHomeValue() {
    return myHomeComponent.getText().trim();
  }

  private void clearAllPaths(){
    for(PathEditor editor: myPathEditors.values()) {
      editor.clearList();
    }
  }

  private void setHomePathValue(String absolutePath) {
    myHomeComponent.setText(absolutePath);
    final Color fg;
    if (absolutePath != null && absolutePath.length() > 0) {
      final File homeDir = new File(absolutePath);
      boolean homeMustBeDirectory = mySdk == null || mySdk.getSdkType().getHomeChooserDescriptor().isChooseFolders();
      fg = homeDir.exists() && (homeDir.isDirectory() == homeMustBeDirectory)
           ? UIUtil.getFieldForegroundColor() 
           : PathEditor.INVALID_COLOR;
    }
    else {
      fg = UIUtil.getFieldForegroundColor();
    }
    myHomeComponent.getTextField().setForeground(fg);
  }

  @Nullable
  public static String selectSdkHome(final Component parentComponent, final SdkType sdkType){
    final FileChooserDescriptor descriptor = sdkType.getHomeChooserDescriptor();
    VirtualFile[] files = FileChooser.chooseFiles(parentComponent, descriptor, getSuggestedSdkRoot(sdkType));
    if (files.length != 0){
      final String path = files[0].getPath();
      if (sdkType.isValidSdkHome(path)) return path;
      String adjustedPath = sdkType.adjustSelectedSdkHome(path);
      return sdkType.isValidSdkHome(adjustedPath) ? adjustedPath : null;
    }
    return null;
  }

  @Nullable
  private static VirtualFile getSuggestedSdkRoot(SdkType sdkType) {
    final String homepath = sdkType.suggestHomePath();
    if (homepath == null) return null;
    return LocalFileSystem.getInstance().findFileByPath(homepath);
  }

  private void doSelectHomePath(){
    final SdkType sdkType = mySdk.getSdkType();
    final String homePath = selectSdkHome(myHomeComponent, sdkType);
    doSetHomePath(homePath, sdkType);
  }

  private void doSetHomePath(final String homePath, final SdkType sdkType) {
    if (homePath == null){
      return;
    }
    setHomePathValue(homePath.replace('/', File.separatorChar));

    final String newSdkName = suggestSdkName(homePath);
    ((ProjectJdkImpl)mySdk).setName(newSdkName);

    try {
      final Sdk dummySdk = (Sdk)mySdk.clone();
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
        myPathEditors.get(type).addPaths(sdkModificator.getRoots(type));
      }
      mySdkModel.getMulticaster().sdkHomeSelected(dummySdk, homePath);
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e); // should not happen in normal program
    }
  }

  private String suggestSdkName(final String homePath) {
    final String currentName = mySdk.getName();
    final String suggestedName = mySdk.getSdkType().suggestSdkName(currentName , homePath);
    if (Comparing.equal(currentName, suggestedName)) return currentName;
    String newSdkName = suggestedName;
    final Set<String> allNames = new HashSet<String>();
    Sdk[] sdks = mySdkModel.getSdks();
    for (Sdk sdk : sdks) {
      allNames.add(sdk.getName());
    }
    int i = 0;
    while(allNames.contains(newSdkName)){
      newSdkName = suggestedName + " (" + (++i) + ")";
    }
    return newSdkName;
  }

  private void updateAdditionalDataComponent() {
    myAdditionalDataPanel.removeAll();
    final AdditionalDataConfigurable configurable = getAdditionalDataConfigurable();
    if (configurable != null) {
      JComponent component = myAdditionalDataComponents.get(configurable);
      if (component == null) {
        component = configurable.createComponent();
        myAdditionalDataComponents.put(configurable, component);
      }      
      myAdditionalDataPanel.add(component, BorderLayout.CENTER);
    }
  }

  @Nullable
  private AdditionalDataConfigurable getAdditionalDataConfigurable() {
    if (mySdk == null) {
      return null;
    }
    return initAdditionalDataConfigurable(mySdk);
  }

  @Nullable
  private AdditionalDataConfigurable initAdditionalDataConfigurable(Sdk sdk) {
    final SdkType sdkType = sdk.getSdkType();
    AdditionalDataConfigurable configurable = myAdditionalDataConfigurables.get(sdkType);
    if (configurable == null) {
      configurable = sdkType.createAdditionalDataConfigurable(mySdkModel, myEditedSdkModificator);
      if (configurable != null) {
        myAdditionalDataConfigurables.put(sdkType, configurable);
      }
    }
    return configurable;
  }

  private class EditedSdkModificator implements SdkModificator {
    public String getName() {
      return mySdk.getName();
    }

    public void setName(String name) {
      ((ProjectJdkImpl)mySdk).setName(name);
    }

    public String getHomePath() {
      return getHomeValue();
    }

    public void setHomePath(String path) {
      doSetHomePath(path, mySdk.getSdkType());
    }

    public String getVersionString() {
      return myVersionString != null? myVersionString : mySdk.getVersionString();
    }

    public void setVersionString(String versionString) {
      throw new UnsupportedOperationException(); // not supported for this editor
    }

    public SdkAdditionalData getSdkAdditionalData() {
      return mySdk.getSdkAdditionalData();
    }

    public void setSdkAdditionalData(SdkAdditionalData data) {
      throw new UnsupportedOperationException(); // not supported for this editor
    }

    public VirtualFile[] getRoots(OrderRootType rootType) {
      return myPathEditors.get(rootType).getRoots();
    }

    public void addRoot(VirtualFile root, OrderRootType rootType) {
      myPathEditors.get(rootType).addPaths(root);
    }

    public void removeRoot(VirtualFile root, OrderRootType rootType) {
      myPathEditors.get(rootType).removePaths(root);
    }

    public void removeRoots(OrderRootType rootType) {
      myPathEditors.get(rootType).clearList();
    }

    public void removeAllRoots() {
      for(PathEditor editor: myPathEditors.values()) {
        editor.clearList();
      }
    }

    public void commitChanges() {
    }

    public boolean isWritable() {
      return true;
    }
  }

  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    myTabbedPane.setSelectedTitle((String)place.getPath("sdkTab"));
    return new ActionCallback.Done();
  }

  public void queryPlace(@NotNull final Place place) {
    place.putPath("sdkTab", myTabbedPane.getSelectedTitle());
  }

  public void setHistory(final History history) {
  }
}
