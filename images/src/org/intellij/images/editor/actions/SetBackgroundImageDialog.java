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
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.ColorSettingsPages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SetBackgroundImageDialog extends DialogWrapper {
  private final String myPropertyTmp;
  private JPanel myRoot;
  private JBRadioButton myEditorRb;
  private JSlider myOpacitySlider;
  private JSpinner myOpacitySpinner;
  private JPanel myPreviewPanel;
  private ComboboxWithBrowseButton myPathField;
  private JBRadioButton myScaleRb;

  boolean myAdjusting;
  private String mySelectedPath;
  private Map<String, String> myResults = ContainerUtil.newHashMap();

  private final SimpleEditorPreview myEditorPreview;
  private final JComponent myIdePreview;

  public SetBackgroundImageDialog(@NotNull Project project, @Nullable String selectedPath) {
    super(project, true);
    setTitle("Background Image");
    mySelectedPath = selectedPath;
    myEditorPreview = createEditorPreview();
    myIdePreview = createIdePreview();
    myPropertyTmp = IdeBackgroundUtil.BG_PROPERTY_PREFIX + project.getLocationHash();
    UiNotifyConnector.doWhenFirstShown(myRoot, new Runnable() {
      @Override
      public void run() {
        IdeBackgroundUtil.createTemporaryBackgroundTransform(myRoot, myPropertyTmp, getDisposable());
      }
    });
    setupComponents();
    restoreRecentImages();
    setSelectedPath(mySelectedPath);
    targetChanged(null);
    init();
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
    int round = (int)Math.round(Math.random() * (pages.length - 1));
    return new SimpleEditorPreview(options, pages[round], false);
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
    myPathField.getButton().setAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, true, false)
          .withFileFilter(file -> ImageFileTypeManager.getInstance().isImage(file));
        VirtualFile file = FileChooser.chooseFile(descriptor, myRoot, null, null);
        if (file != null) {
          setSelectedPath(file.getPath());
        }
      }
    });
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
      button.addActionListener(this::targetChanged);
    }
    for (Enumeration<AbstractButton> e = getTypeRbGroup().getElements(); e.hasMoreElements();) {
      AbstractButton button = e.nextElement();
      button.setActionCommand(button.getText());
      button.addActionListener(this::fillTypeChanged);
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
    myEditorRb.setSelected(true);
    myAdjusting = false;
  }

  private void opacityChanged() {
    updatePreview();
  }

  private void imagePathChanged() {
    updatePreview();
  }

  private void fillTypeChanged(ActionEvent event) {
    updatePreview();
  }

  private void targetChanged(ActionEvent event) {
    if (StringUtil.isEmptyOrSpaces(mySelectedPath)) {
      retrieveExistingValue();
    }

    ((CardLayout)myPreviewPanel.getLayout()).show(myPreviewPanel, myEditorRb.isSelected() ? "editor" : "ide");
    if (myEditorRb.isSelected()) {
      myEditorPreview.updateView();
    }
    updatePreview();
  }

  public void setSelectedPath(String path) {
    mySelectedPath = path;
    if (StringUtil.isEmptyOrSpaces(path)) return;
    CollectionComboBoxModel<String> comboModel = getComboModel();
    if (!comboModel.getItems().contains(path)) {
      comboModel.add(path);
    }
    comboModel.setSelectedItem(path);
    getComboEditor().setCaretPosition(0);
  }

  private void retrieveExistingValue() {
    myAdjusting = true;
    String prop = getSystemProp();
    String value = StringUtil.notNullize(myResults.get(prop), getProperty(prop));
    String[] split = value.split(",");
    setSelectedPath(split[0]);
    mySelectedPath = null;
    int opacity = split.length > 1 ? StringUtil.parseInt(split[1], 15) : 15;
    myOpacitySlider.setValue(opacity);
    myOpacitySpinner.setValue(opacity);
    String type = split.length > 2 ? split[2] : "scale";
    for (Enumeration<AbstractButton> e = getTypeRbGroup().getElements(); e.hasMoreElements(); ) {
      AbstractButton button = e.nextElement();
      String s = button.getActionCommand().replace(' ', '_');
      if (s.equalsIgnoreCase(type)) {
        button.setSelected(true);
        break;
      }
    }
    myAdjusting = false;
  }

  @Override
  public void doCancelAction() {
    storeRecentImages();
    super.doCancelAction();
  }

  @Override
  protected void doOKAction() {
    storeRecentImages();
    String value = calcNewValue();
    String prop = getSystemProp();
    myResults.put(prop, value);

    if (value.startsWith(",")) return;
    PropertiesComponent.getInstance().setValue(prop, value);

    super.doOKAction();
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
    for (String s : value.split("\n")) {
      //noinspection unchecked
      getComboModel().add(s);
    }
  }

  private static String getProperty(@NotNull String prop) {
    return StringUtil.notNullize(PropertiesComponent.getInstance().getValue(prop), System.getProperty(prop, ""));
  }

  @NotNull
  private String getSystemProp() {
    return IdeBackgroundUtil.BG_PROPERTY_PREFIX + (myEditorRb.isSelected() ? "editor" : "ide");
  }

  private void updatePreview() {
    String prop = getSystemProp();
    String value = calcNewValue();
    System.setProperty(myPropertyTmp, value);
    myResults.put(prop, value);
    myPreviewPanel.validate();
    myPreviewPanel.repaint();
  }

  @NotNull
  private String calcNewValue() {
    String path = (String)myPathField.getComboBox().getEditor().getItem();
    String type = getTypeRbGroup().getSelection().getActionCommand()
      .replace(' ', '_').toLowerCase(Locale.ENGLISH);

    return path.trim() + "," + myOpacitySpinner.getValue() + "," + type;
  }

  private ButtonGroup getTypeRbGroup() {
    return ((DefaultButtonModel)myScaleRb.getModel()).getGroup();
  }

  private ButtonGroup getTargetRbGroup() {
    return ((DefaultButtonModel)myEditorRb.getModel()).getGroup();
  }
}
