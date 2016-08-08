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
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.StatusText;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class JrePathEditor extends JPanel implements PanelWithAnchor {
  private static final String DEFAULT_JRE_TEXT = "Default";
  private final ComboboxWithBrowseButton myPathField;
  private final JBLabel myLabel;
  private final JreComboboxEditor myComboboxEditor;
  private final DefaultJreItem myDefaultJreItem;
  private DefaultJreSelector myDefaultJreSelector;
  private JComponent myAnchor;
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
    myLabel = new JBLabel(ExecutionBundle.message("run.configuration.jre.label"));

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
    ComboBox comboBox = new ComboBox(myComboBoxModel, 100);
    comboBox.setEditable(true);
    comboBox.setRenderer(new ColoredListCellRendererWrapper<JreComboBoxItem>() {
      @Override
      protected void doCustomize(JList list, JreComboBoxItem value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          value.render(this, selected);
        }
      }
    });
    myComboboxEditor = new JreComboboxEditor(myComboBoxModel);
    myComboboxEditor.getEditorComponent().setTextToTriggerEmptyTextStatus(DEFAULT_JRE_TEXT);
    comboBox.setEditor(myComboboxEditor);
    myPathField = new ComboboxWithBrowseButton(comboBox);
    myPathField.addBrowseFolderListener(ExecutionBundle.message("run.configuration.select.alternate.jre.label"),
                                        ExecutionBundle.message("run.configuration.select.jre.dir.label"),
                                        null, BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR,
                                        JreComboboxEditor.TEXT_COMPONENT_ACCESSOR);

    setLayout(new MigLayout("ins 0, gap 10, fill, flowx"));
    add(myLabel, "shrinkx");
    add(myPathField, "growx, pushx");

    InsertPathAction.addTo(myComboboxEditor.getEditorComponent());

    setAnchor(myLabel);

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
    return (JreComboBoxItem)myPathField.getComboBox().getEditor().getItem();
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
    myPathField.getChildComponent().setSelectedItem(toSelect);
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

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    myAnchor = anchor;
    myLabel.setAnchor(anchor);
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

