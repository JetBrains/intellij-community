// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentConfigurations;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.ui.BrowseFolderRunnable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class JrePathEditor extends LabeledComponent<ComboBox<JrePathEditor.JreComboBoxItem>> implements PanelWithAnchor {
  private final JreComboboxEditor myComboboxEditor;
  private final DefaultJreItem myDefaultJreItem;
  private DefaultJreSelector myDefaultJreSelector;
  private final SortedComboBoxModel<JreComboBoxItem> myComboBoxModel;
  private String myPreviousCustomJrePath;
  private boolean myRemoteTarget;

  public JrePathEditor(DefaultJreSelector defaultJreSelector) {
    this();
    setDefaultJreSelector(defaultJreSelector);
  }

  /**
   * This constructor can be used in UI forms. <strong>Don't forget to call {@link #setDefaultJreSelector(DefaultJreSelector)}!</strong>
   */
  public JrePathEditor() {
    this(true);
  }

  public JrePathEditor(boolean editable) {
    myComboBoxModel = new SortedComboBoxModel<>((o1, o2) -> {
      int result = Comparing.compare(o1.getOrder(), o2.getOrder());
      if (result != 0) {
        return result;
      }
      return o1.getPresentableText().compareToIgnoreCase(o2.getPresentableText());
    }) {
      @Override
      public void setSelectedItem(Object anItem) {
        if (anItem instanceof AddJreItem) {
          getComponent().hidePopup();
          getBrowseRunnable().run();
        }
        else {
          super.setSelectedItem(anItem);
        }
      }
    };
    myDefaultJreItem = new DefaultJreItem();
    buildModel(editable);
    myComboBoxModel.setSelectedItem(myDefaultJreItem);

    ComboBox<JreComboBoxItem> comboBox = new ComboBox<>(myComboBoxModel, JBUI.scale(300));
    comboBox.setEditable(editable);
    comboBox.setRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends JreComboBoxItem> list,
                                           JreComboBoxItem value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value != null) {
          value.render(this, selected);
        }
      }
    });
    setComponent(comboBox);

    myComboboxEditor = new JreComboboxEditor(myComboBoxModel) {
      @Override
      protected JTextField createEditorComponent() {
        JBTextField field = new ExtendableTextField().addBrowseExtension(getBrowseRunnable(), null);
        field.setBorder(null);
        field.addFocusListener(new FocusListener() {
          @Override
          public void focusGained(FocusEvent e) {
            update(e);
          }

          @Override
          public void focusLost(FocusEvent e) {
            update(e);
          }

          private void update(FocusEvent e) {
            Component c = e.getComponent().getParent();
            if (c != null) {
              c.revalidate();
              c.repaint();
            }
          }
        });
        field.setTextToTriggerEmptyTextStatus(ExecutionBundle.message("default.jre.name"));

        return field;
      }
    };
    comboBox.setEditor(myComboboxEditor);
    InsertPathAction.addTo(myComboboxEditor.getEditorComponent());

    setLabelLocation(BorderLayout.WEST);
    setText(ExecutionBundle.message("run.configuration.jre.label"));

    updateUI();
  }

  /**
   * @return true if selection update needed
   */
  public boolean updateModel(@NotNull Project project, @Nullable String targetName) {
    myComboBoxModel.clear();
    myRemoteTarget = false;
    TargetEnvironmentConfiguration config = TargetEnvironmentConfigurations.getEffectiveConfiguration(targetName, project);
    if (config != null) {
      myRemoteTarget = true;
      List<CustomJreItem> items = ContainerUtil.mapNotNull(config.getRuntimes().resolvedConfigs(),
                                                           configuration -> configuration instanceof JavaLanguageRuntimeConfiguration ?
                                                                            new CustomJreItem(
                                                                              (JavaLanguageRuntimeConfiguration)configuration) : null);
      myComboBoxModel.addAll(items);
      if (!items.isEmpty()) {
        myComboBoxModel.setSelectedItem(items.get(0));
      }
      return false;
    }
    buildModel(getComponent().isEditable());
    return true;
  }

  private void buildModel(boolean editable) {
    myComboBoxModel.add(myDefaultJreItem);
    final Sdk[] allJDKs = ProjectJdkTable.getInstance().getAllJdks();
    for (Sdk sdk : allJDKs) {
      myComboBoxModel.add(new SdkAsJreItem(sdk));
    }

    final Set<String> jrePaths = new HashSet<>();
    for (JreProvider provider : JreProvider.EP_NAME.getExtensionList()) {
      if (provider.isAvailable()) {
        String path = provider.getJrePath();
        if (!StringUtil.isEmpty(path)) {
          jrePaths.add(path);
          myComboBoxModel.add(new CustomJreItem(provider));
        }
      }
    }

    for (Sdk jdk : allJDKs) {
      String homePath = jdk.getHomePath();

      if (!SystemInfo.isMac) {
        final File jre = new File(jdk.getHomePath(), "jre");
        if (jre.isDirectory()) {
          homePath = jre.getPath();
        }
      }
      if (jrePaths.add(homePath)) {
        myComboBoxModel.add(new CustomJreItem(homePath, null, jdk.getVersionString()));
      }
    }
    if (!editable) {
      myComboBoxModel.add(new AddJreItem());
    }
  }

  @NotNull
  private Runnable getBrowseRunnable() {
    return new BrowseFolderRunnable<>(ExecutionBundle.message("run.configuration.select.alternate.jre.label"),
                                      ExecutionBundle.message("run.configuration.select.jre.dir.label"),
                                      null,
                                      BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR,
                                      getComponent(),
                                      JreComboboxEditor.TEXT_COMPONENT_ACCESSOR);
  }

  @Nullable
  public String getJrePathOrName() {
    JreComboBoxItem jre = getSelectedJre();
    if (jre instanceof DefaultJreItem || myRemoteTarget) {
      return myPreviousCustomJrePath;
    }
    return jre.getPathOrName();
  }

  public boolean isAlternativeJreSelected() {
    return !(getSelectedJre() instanceof DefaultJreItem) && !myRemoteTarget;
  }

  private JreComboBoxItem getSelectedJre() {
    ComboBox<?> comboBox = getComponent();
    return comboBox.isEditable() ? (JreComboBoxItem)comboBox.getEditor().getItem() : (JreComboBoxItem)comboBox.getSelectedItem();
  }

  public void setDefaultJreSelector(DefaultJreSelector defaultJreSelector) {
    myDefaultJreSelector = defaultJreSelector;
    myDefaultJreSelector.addChangeListener(() -> updateDefaultJrePresentation());
  }

  public void setPathOrName(@Nullable String pathOrName, boolean useAlternativeJre) {
    JreComboBoxItem toSelect = myDefaultJreItem;
    if (!StringUtil.isEmpty(pathOrName)) {
      myPreviousCustomJrePath = pathOrName;
      JreComboBoxItem alternative = findOrAddCustomJre(pathOrName);
      if (useAlternativeJre) {
        toSelect = alternative;
      }
    }
    getComponent().setSelectedItem(toSelect);
    updateDefaultJrePresentation();
  }

  private void updateDefaultJrePresentation() {
    updateDefaultJrePresentation((@Nls String description) -> {
      StatusText text = myComboboxEditor.getEmptyText();
      text.clear();
      text.appendText(ExecutionBundle.message("default.jre.name"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      text.appendText(description, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    });
  }

  private void updateDefaultJrePresentation(@NotNull Consumer<? super @Nls String> uiUpdater) {
    ReadAction
      .nonBlocking(myDefaultJreSelector::getDescriptionString)
      .coalesceBy(this, uiUpdater)
      .finishOnUiThread(ModalityState.stateForComponent(this), uiUpdater)
      .expireWhen(() -> !myDefaultJreSelector.isValid())
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private JreComboBoxItem findOrAddCustomJre(@NotNull String pathOrName) {
    for (JreComboBoxItem item : myComboBoxModel.getItems()) {
      if (item instanceof CustomJreItem && FileUtil.pathsEqual(pathOrName, ((CustomJreItem)item).myPath)
          || pathOrName.equals(item.getPathOrName())) {
        return item;
      }
    }
    CustomJreItem item = new CustomJreItem(pathOrName);
    myComboBoxModel.add(item);
    return item;
  }

  public void addActionListener(ActionListener listener) {
    getComponent().addActionListener(listener);
  }

  interface JreComboBoxItem {
    void render(SimpleColoredComponent component, boolean selected);

    String getPresentableText();

    default @NonNls @Nullable String getID() { return null; }

    @Nullable @NlsSafe
    String getPathOrName();

    @Nullable
    default String getVersion() { return null; }

    default @NlsSafe @Nullable String getDescription() { return getPresentableText(); }

    int getOrder();
  }

  private static class SdkAsJreItem implements JreComboBoxItem {
    private final Sdk mySdk;

    SdkAsJreItem(Sdk sdk) {
      mySdk = sdk;
    }

    @Override
    public void render(SimpleColoredComponent component, boolean selected) {
      OrderEntryAppearanceService.getInstance().forJdk(mySdk, false, selected, true).customize(component);
    }

    @Override
    public String getPresentableText() {
      return mySdk.getName();
    }

    @Override
    public String getPathOrName() {
      return mySdk.getName();
    }

    @Override
    public String getVersion() {
      return mySdk.getVersionString();
    }

    @Override
    public @Nullable String getDescription() {
      return mySdk.getVersionString();
    }

    @Override
    public int getOrder() {
      return 1;
    }
  }

  static class CustomJreItem implements JreComboBoxItem {
    private final @NlsSafe String myPath;
    private final @NlsContexts.Label String myName;
    private final String myVersion;
    private final String myID;

    CustomJreItem(String path) {
      myPath = path;
      myName = null;
      JdkVersionDetector.JdkVersionInfo info = SdkVersionUtil.getJdkVersionInfo(path);
      myVersion = info == null ? null : info.toString();
      myID = null;
    }

    CustomJreItem(@NotNull JreProvider provider) {
      myPath = provider.getJrePath();
      myName = provider.getPresentableName();
      myVersion = null;
      myID = provider.getID();
    }

    CustomJreItem(String path, @NlsContexts.Label String name, String version) {
      myPath = path;
      myName = name;
      myVersion = version;
      myID = null;
    }

    CustomJreItem(JavaLanguageRuntimeConfiguration runtimeConfiguration) {
      myPath = runtimeConfiguration.getHomePath();
      myName = null;
      myVersion = runtimeConfiguration.getJavaVersionString();
      myID = null;
    }

    @Override
    public void render(SimpleColoredComponent component, boolean selected) {
      component.append(getPresentableText());
      component.setIcon(AllIcons.Nodes.Folder);
    }

    @Override
    public @NlsContexts.Label String getPresentableText() {
      return myName != null && !myName.equals(myPath) ? myName : FileUtil.toSystemDependentName(myPath);
    }

    @Override
    public @NonNls @Nullable String getID() {
      return myID;
    }

    @Override
    public String getPathOrName() {
      return myPath;
    }

    @Override
    public String getVersion() {
      return myVersion;
    }

    @Override
    public @NlsSafe @Nullable String getDescription() {
      return null;
    }

    @Override
    public int getOrder() {
      return 2;
    }
  }

  private class DefaultJreItem implements JreComboBoxItem {
    @Override
    public void render(SimpleColoredComponent component, boolean selected) {
      component.append(ExecutionBundle.message("default.jre.name"));
      component.setIcon(EmptyIcon.ICON_16);
      //may be null if JrePathEditor is added to a GUI Form where the default constructor is used and setDefaultJreSelector isn't called
      if (myDefaultJreSelector != null) {
        updateDefaultJrePresentation((@Nls String description) -> component.append(description, SimpleTextAttributes.GRAY_ATTRIBUTES));
      }
    }

    @Override
    public String getPresentableText() {
      return ExecutionBundle.message("default.jre.name");
    }

    @Override
    public String getPathOrName() {
      return null;
    }

    @Override
    public String getVersion() {
      return myDefaultJreSelector.getVersion();
    }

    @Override
    public @Nullable String getDescription() {
      return myDefaultJreSelector.getNameAndDescription().second;
    }

    @Override
    public int getOrder() {
      return 0;
    }
  }

  private static class AddJreItem implements JreComboBoxItem {

    @Override
    public void render(SimpleColoredComponent component, boolean selected) {
      component.append(getPresentableText());
      component.setIcon(EmptyIcon.ICON_16);
    }

    @Override
    public @NlsContexts.Label String getPresentableText() {
      return ExecutionBundle.message("run.configuration.select.alternate.jre.action");
    }

    @Override
    public @Nullable String getPathOrName() {
      return null;
    }

    @Override
    public int getOrder() {
      return Integer.MAX_VALUE;
    }
  }
}

