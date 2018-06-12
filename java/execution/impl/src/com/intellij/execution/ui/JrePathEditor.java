// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class JrePathEditor extends LabeledComponent<ComboboxWithBrowseButton> implements PanelWithAnchor {
  private static final String DEFAULT_JRE_TEXT = "Default";
  private final JreComboboxEditor myComboboxEditor;
  private final DefaultJreItem myDefaultJreItem;
  private DefaultJreSelector myDefaultJreSelector;
  private final SortedComboBoxModel<JreComboBoxItem> myComboBoxModel;
  private String myPreviousCustomJrePath;

  public JrePathEditor(DefaultJreSelector defaultJreSelector) {
    this();
    setDefaultJreSelector(defaultJreSelector);
  }

  /**
   * This constructor can be used in UI forms. <strong>Don't forget to call {@link #setDefaultJreSelector(DefaultJreSelector)}!</strong>
   */
  public JrePathEditor() {
    myComboBoxModel = new SortedComboBoxModel<>((o1, o2) -> {
      int result = Comparing.compare(o1.getOrder(), o2.getOrder());
      if (result != 0) {
        return result;
      }
      return o1.getPresentableText().compareToIgnoreCase(o2.getPresentableText());
    });
    myDefaultJreItem = new DefaultJreItem();
    myComboBoxModel.add(myDefaultJreItem);
    final Sdk[] allJDKs = ProjectJdkTable.getInstance().getAllJdks();
    for (Sdk sdk : allJDKs) {
      myComboBoxModel.add(new SdkAsJreItem(sdk));
    }

    final Set<String> jrePaths = new HashSet<>();
    for (JreProvider provider : JreProvider.EP_NAME.getExtensions()) {
      String path = provider.getJrePath();
      if (!StringUtil.isEmpty(path)) {
        jrePaths.add(path);
        myComboBoxModel.add(new CustomJreItem(path));
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
        myComboBoxModel.add(new CustomJreItem(homePath));
      }
    }
    ComboBox<JreComboBoxItem> comboBox = new ComboBox<>(myComboBoxModel, 100);
    comboBox.setEditable(true);
    comboBox.setRenderer(new ColoredListCellRenderer<JreComboBoxItem>() {
      {
        setIpad(JBUI.insets(1, 0));
        setMyBorder(null);
      }

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
    myComboboxEditor = new JreComboboxEditor(myComboBoxModel);
    myComboboxEditor.getEditorComponent().setTextToTriggerEmptyTextStatus(DEFAULT_JRE_TEXT);
    comboBox.setEditor(myComboboxEditor);
    InsertPathAction.addTo(myComboboxEditor.getEditorComponent());

    ComboboxWithBrowseButton pathField = new ComboboxWithBrowseButton(comboBox);
    pathField.addBrowseFolderListener(ExecutionBundle.message("run.configuration.select.alternate.jre.label"),
                                      ExecutionBundle.message("run.configuration.select.jre.dir.label"),
                                      null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR,
                                      JreComboboxEditor.TEXT_COMPONENT_ACCESSOR);

    setLabelLocation(BorderLayout.WEST);
    setText(ExecutionBundle.message("run.configuration.jre.label"));
    setComponent(pathField);

    updateUI();
  }

  @Nullable
  public String getJrePathOrName() {
    JreComboBoxItem jre = getSelectedJre();
    if (jre instanceof DefaultJreItem) {
      return myPreviousCustomJrePath;
    }
    return jre.getPathOrName();
  }

  public boolean isAlternativeJreSelected() {
    return !(getSelectedJre() instanceof DefaultJreItem);
  }

  private JreComboBoxItem getSelectedJre() {
    return (JreComboBoxItem)getComponent().getComboBox().getEditor().getItem();
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
    getComponent().getChildComponent().setSelectedItem(toSelect);
    updateDefaultJrePresentation();
  }

  private void updateDefaultJrePresentation() {
    StatusText text = myComboboxEditor.getEmptyText();
    text.clear();
    text.appendText(DEFAULT_JRE_TEXT, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    text.appendText(myDefaultJreSelector.getDescriptionString(), SimpleTextAttributes.GRAY_ATTRIBUTES);
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
    getComponent().getComboBox().addActionListener(listener);
  }

  interface JreComboBoxItem {
    void render(SimpleColoredComponent component, boolean selected);
    String getPresentableText();
    @Nullable
    String getPathOrName();
    int getOrder();
  }

  private static class SdkAsJreItem implements JreComboBoxItem {
    private final Sdk mySdk;

    public SdkAsJreItem(Sdk sdk) {
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
    public int getOrder() {
      return 1;
    }
  }

  static class CustomJreItem implements JreComboBoxItem {
    private final String myPath;

    public CustomJreItem(String path) {
      myPath = path;
    }

    @Override
    public void render(SimpleColoredComponent component, boolean selected) {
      component.append(getPresentableText());
      component.setIcon(AllIcons.Nodes.Folder);
    }

    @Override
    public String getPresentableText() {
      return FileUtil.toSystemDependentName(myPath);
    }

    @Override
    public String getPathOrName() {
      return myPath;
    }

    @Override
    public int getOrder() {
      return 2;
    }
  }

  private class DefaultJreItem implements JreComboBoxItem {
    @Override
    public void render(SimpleColoredComponent component, boolean selected) {
      component.append(DEFAULT_JRE_TEXT);
      //may be null if JrePathEditor is added to a GUI Form where the default constructor is used and setDefaultJreSelector isn't called
      if (myDefaultJreSelector != null) {
        component.append(myDefaultJreSelector.getDescriptionString(), SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    @Override
    public String getPresentableText() {
      return DEFAULT_JRE_TEXT;
    }

    @Override
    public String getPathOrName() {
      return null;
    }

    @Override
    public int getOrder() {
      return 0;
    }
  }
}

