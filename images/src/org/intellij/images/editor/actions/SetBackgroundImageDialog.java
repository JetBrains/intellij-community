/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.intellij.images.editor.actions;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.SimpleEditorPreview;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.ColorSettingsPages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.*;

public class SetBackgroundImageDialog extends DialogWrapper {
  private final String myPropertyTmp;
  private final Project myProject;
  private JPanel myRoot;
  private JBRadioButton myEditorRb;
  private JBRadioButton myScaleRb;
  private JBRadioButton myCenterRb;
  private JSlider myOpacitySlider;
  private JSpinner myOpacitySpinner;
  private JPanel myPreviewPanel;
  private ComboboxWithBrowseButton myPathField;
  private JBCheckBox myThisProjectOnlyCb;

  boolean myAdjusting;
  private Map<String, String> myResults = ContainerUtil.newHashMap();

  private final SimpleEditorPreview myEditorPreview;
  private final JComponent myIdePreview;

  public SetBackgroundImageDialog(@NotNull Project project, @Nullable String selectedPath) {
    super(project, true);
    myProject = project;
    setTitle("Background Image");
    myEditorPreview = createEditorPreview();
    myIdePreview = createIdePreview();
    myPropertyTmp = getSystemProp() + "#" + project.getLocationHash();
    UiNotifyConnector.doWhenFirstShown(myRoot, () -> createTemporaryBackgroundTransform(myPreviewPanel, myPropertyTmp, getDisposable()));
    setupComponents();
    restoreRecentImages();
    if (StringUtil.isNotEmpty(selectedPath)) {
      myResults.put(getSystemProp(true), selectedPath);
      myResults.put(getSystemProp(false), selectedPath);
      setSelectedPath(selectedPath);
    }
    targetChanged(null);
    init();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return ArrayUtil.append(super.createActions(), new AbstractAction("Clear") {
      @Override
      public void actionPerformed(ActionEvent e) {
        doClearAction();
      }
    });
  }

  private void createUIComponents() {
    ComboBox<String> comboBox = new ComboBox<>(new CollectionComboBoxModel<String>(), 100);
    myPathField = new ComboboxWithBrowseButton(comboBox);
  }

  @Override
  protected void dispose() {
    super.dispose();
    myEditorPreview.disposeUIResources();
    System.getProperties().remove(myPropertyTmp);
  }

  @NotNull
  private static SimpleEditorPreview createEditorPreview() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    ColorAndFontOptions options = new ColorAndFontOptions();
    options.reset();
    options.selectScheme(scheme.getName());
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    int index;
    int attempt = 0;
    do {
      index = (int)Math.round(Math.random() * (pages.length - 1));
    }
    while (StringUtil.countNewLines(pages[index].getDemoText()) < 8 && ++attempt < 10);
    return new SimpleEditorPreview(options, pages[index], false);
  }

  @NotNull
  private static JComponent createIdePreview() {
    return new JBLabel() {
      EditorEmptyTextPainter p = ServiceManager.getService(EditorEmptyTextPainter.class);

      @Override
      protected Graphics getComponentGraphics(Graphics g) {
        return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g));
      }

      @Override
      public void paint(Graphics gg) {
        Graphics g = getComponentGraphics(gg);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.translate(0, -50);
        p.paintEmptyText(this, g);
      }
    };
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @NotNull
  private String getRecentItemsKey() {
    return getDimensionServiceKey() + "#recent";
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myRoot;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPathField;
  }

  private void setupComponents() {
    myAdjusting = true;
    myPreviewPanel.setLayout(new CardLayout());
    myPreviewPanel.add(myEditorPreview.getPanel(), "editor");
    myPreviewPanel.add(myIdePreview, "ide");
    ((CardLayout)myPreviewPanel.getLayout()).show(myPreviewPanel, "editor");
    myPathField.getComboBox().setEditable(true);
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, true, false)
      .withFileFilter(file -> ImageFileTypeManager.getInstance().isImage(file));
    myPathField.addBrowseFolderListener(null, null, null, descriptor, TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);
    JTextComponent textComponent = getComboEditor();
    textComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myAdjusting) return;
        imagePathChanged();
      }
    });
    for (Enumeration<AbstractButton> e = getTargetRbGroup().getElements(); e.hasMoreElements();) {
      AbstractButton button = e.nextElement();
      button.setActionCommand(button.getText());
      button.addItemListener(this::targetChanged);
    }
    for (Enumeration<AbstractButton> e = getFillRbGroup().getElements(); e.hasMoreElements();) {
      AbstractButton button = e.nextElement();
      button.setActionCommand(button.getText());
      button.addItemListener(this::fillOrPlaceChanged);
    }
    for (Enumeration<AbstractButton> e = getPlaceRbGroup().getElements(); e.hasMoreElements();) {
      AbstractButton button = e.nextElement();
      button.setActionCommand(button.getText());
      button.addItemListener(this::fillOrPlaceChanged);
    }
    ChangeListener opacitySync = new ChangeListener() {

      @Override
      public void stateChanged(ChangeEvent e) {
        if (myAdjusting) return;
        myAdjusting = true;
        boolean b = e.getSource() == myOpacitySpinner;
        if (b) {
          int value = (Integer)myOpacitySpinner.getValue();
          myOpacitySpinner.setValue(Math.min(Math.max(0, value), 100));
          myOpacitySlider.setValue(value);
        }
        else {
          myOpacitySpinner.setValue(myOpacitySlider.getValue());
        }
        myAdjusting = false;
        opacityChanged();
      }
    };
    myOpacitySpinner.addChangeListener(opacitySync);
    myOpacitySlider.addChangeListener(opacitySync);
    myOpacitySlider.setValue(15);
    myOpacitySpinner.setValue(15);
    myScaleRb.setSelected(true);
    myCenterRb.setSelected(true);
    myEditorRb.setSelected(true);
    boolean perProject = !Comparing.equal(getBackgroundSpec(myProject, getSystemProp(true)), getBackgroundSpec(null, getSystemProp(true)));
    myThisProjectOnlyCb.setSelected(perProject);
    myAdjusting = false;
  }

  private void opacityChanged() {
    updatePreview();
  }

  private void imagePathChanged() {
    updatePreview();
  }

  private void fillOrPlaceChanged(ItemEvent event) {
    updatePreview();
  }

  private void targetChanged(ItemEvent event) {
    if (event != null && event.getStateChange() == ItemEvent.DESELECTED) {
      return;
    }
    retrieveExistingValue();

    ((CardLayout)myPreviewPanel.getLayout()).show(myPreviewPanel, myEditorRb.isSelected() ? "editor" : "ide");
    if (myEditorRb.isSelected()) {
      myEditorPreview.updateView();
    }
    updatePreview();
  }

  public void setSelectedPath(String path) {
    if (StringUtil.isEmptyOrSpaces(path)) {
      getComboEditor().setText("");
    }
    else {
      CollectionComboBoxModel<String> comboModel = getComboModel();
      if (!comboModel.contains(path)) {
        comboModel.add(path);
      }
      comboModel.setSelectedItem(path);
      getComboEditor().setCaretPosition(0);
    }
  }

  private void retrieveExistingValue() {
    myAdjusting = true;
    String prop = getSystemProp();
    JBIterable<String> possibleValues = JBIterable.of(myResults.get(prop))
      .append(StringUtil.nullize(getBackgroundSpec(myProject, prop)))
      .append(myResults.values());
    String value = StringUtil.notNullize(ObjectUtils.coalesce(possibleValues));
    String[] split = value.split(",");
    int opacity = split.length > 1 ? StringUtil.parseInt(split[1], 15) : 15;
    String fill = split.length > 2 ? split[2] : "scale";
    String place = split.length > 3 ? split[3] : "center";
    setSelectedPath(split[0]);
    myOpacitySlider.setValue(opacity);
    myOpacitySpinner.setValue(opacity);
    setSelected(getFillRbGroup(), fill);
    setSelected(getPlaceRbGroup(), place);
    myAdjusting = false;
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
    storeRecentImages();
  }

  private void doClearAction() {
    close(OK_EXIT_CODE);
    storeRecentImages();
    String prop = getSystemProp();
    PropertiesComponent.getInstance(myProject).setValue(prop, null);
    PropertiesComponent.getInstance().setValue(prop, null);
    repaintAllWindows();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    storeRecentImages();
    String value = calcNewValue();
    String prop = getSystemProp();
    myResults.put(prop, value);

    if (value.startsWith(",")) value = null;

    boolean perProject = myThisProjectOnlyCb.isSelected();
    PropertiesComponent.getInstance(myProject).setValue(prop, perProject ? value : null);
    if (!perProject) {
      PropertiesComponent.getInstance().setValue(prop, value);
    }

    repaintAllWindows();
  }

  private void storeRecentImages() {
    List<String> items = getComboModel().getItems();
    PropertiesComponent.getInstance().setValue(
      getRecentItemsKey(),
      StringUtil.join(items.subList(0, Math.min(items.size(), 5)), "\n"));
  }

  private CollectionComboBoxModel<String> getComboModel() {
    //noinspection unchecked
    return (CollectionComboBoxModel<String>)myPathField.getComboBox().getModel();
  }

  private JTextComponent getComboEditor() {
    return (JTextComponent)myPathField.getComboBox().getEditor().getEditorComponent();
  }

  private void restoreRecentImages() {
    String value = PropertiesComponent.getInstance().getValue(getRecentItemsKey());
    if (value == null) return;
    CollectionComboBoxModel<String> model = getComboModel();
    for (String s : value.split("\n")) {
      if (StringUtil.isEmptyOrSpaces(s) || model.contains(s)) continue;
      model.add(s);
    }
  }

  private String getSystemProp() {
    return getSystemProp(myEditorRb.isSelected());
  }

  @NotNull
  private static String getSystemProp(boolean forEditor) {
    return forEditor ? EDITOR_PROP : FRAME_PROP;
  }

  private void updatePreview() {
    if (myAdjusting) return;
    String prop = getSystemProp();
    String value = calcNewValue();
    System.setProperty(myPropertyTmp, value);
    myResults.put(prop, value);
    myPreviewPanel.validate();
    myPreviewPanel.repaint();
    boolean clear = value.startsWith(",");
    getOKAction().setEnabled(!clear);
  }

  @NotNull
  private String calcNewValue() {
    String path = (String)myPathField.getComboBox().getEditor().getItem();
    String type = getFillRbGroup().getSelection().getActionCommand().replace('-', '_');
    String place = getPlaceRbGroup().getSelection().getActionCommand().replace('-', '_');
    return path.trim() + "," + myOpacitySpinner.getValue() + "," + (type + "," + place).toLowerCase(Locale.ENGLISH);
  }

  private static void setSelected(ButtonGroup group, String fill) {
    for (Enumeration<AbstractButton> e = group.getElements(); e.hasMoreElements(); ) {
      AbstractButton button = e.nextElement();
      String s = button.getActionCommand().replace('-', '_');
      if (s.equalsIgnoreCase(fill)) {
        button.setSelected(true);
        break;
      }
    }
  }

  private ButtonGroup getFillRbGroup() {
    return ((DefaultButtonModel)myScaleRb.getModel()).getGroup();
  }

  private ButtonGroup getPlaceRbGroup() {
    return ((DefaultButtonModel)myCenterRb.getModel()).getGroup();
  }

  private ButtonGroup getTargetRbGroup() {
    return ((DefaultButtonModel)myEditorRb.getModel()).getGroup();
  }
}
