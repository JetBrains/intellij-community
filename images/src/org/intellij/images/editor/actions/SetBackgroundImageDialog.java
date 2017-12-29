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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.ColorSettingsPages;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.*;

public class SetBackgroundImageDialog extends DialogWrapper {
  private static final String EDITOR = "editor";
  private static final String FRAME = "ide";
  
  private final String myPropertyTmp;
  private final Project myProject;

  private final ButtonGroup myAnchorGroup = new ButtonGroup();
  private final ButtonGroup myFillGroup = new ButtonGroup();
  private String myPreviewTarget;
  private ActionToolbar myToolbar;

  private JPanel myRoot;
  private JSlider myOpacitySlider;
  private JSpinner myOpacitySpinner;
  private JPanel myPreviewPanel;
  private ComboboxWithBrowseButton myPathField;
  private JBCheckBox myThisProjectOnlyCb;
  private JPanel myAnchorPanel;
  private JPanel myFillPanel;
  private JPanel myTargetPanel;

  boolean myAdjusting;
  private Map<String, String> myResults = ContainerUtil.newHashMap();

  private final SimpleEditorPreview myEditorPreview;
  private final JComponent myIdePreview;

  public SetBackgroundImageDialog(@NotNull Project project, @Nullable String selectedPath) {
    super(project, true);
    myProject = project;
    setTitle("Background Image");
    myEditorPreview = createEditorPreview();
    myIdePreview = createFramePreview();
    myPropertyTmp = getSystemProp() + "#" + project.getLocationHash();
    UiNotifyConnector.doWhenFirstShown(myRoot, () -> createTemporaryBackgroundTransform(myPreviewPanel, myPropertyTmp, getDisposable()));
    setupComponents();
    restoreRecentImages();
    if (StringUtil.isNotEmpty(selectedPath)) {
      myResults.put(getSystemProp(true), selectedPath);
      myResults.put(getSystemProp(false), selectedPath);
      setSelectedPath(selectedPath);
    }
    targetChanged(EDITOR);
    init();
    myEditorPreview.getPanel().setPreferredSize(new Dimension(0, 0));
    pack();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return ArrayUtil.append(super.createActions(), new AbstractAction("Clear and Close") {
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
  private static JComponent createFramePreview() {
    EditorEmptyTextPainter painter = ServiceManager.getService(EditorEmptyTextPainter.class);
    JBPanelWithEmptyText panel = new JBPanelWithEmptyText() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        painter.paintEmptyText(this, g);
      }

      @Override
      public Color getBackground() {
        return getIdeBackgroundColor();
      }

      @Override
      public boolean isOpaque() {
        return true;
      }
    };
    panel.getEmptyText().clear();
    return panel;
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
    myPreviewPanel.add(myEditorPreview.getPanel(), EDITOR);
    myPreviewPanel.add(myIdePreview, FRAME);
    UIUtil.removeScrollBorder(myPreviewPanel);
    myPreviewPanel.setBorder(new SideBorder(JBColor.border(), SideBorder.ALL));
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(createToggleAction(EDITOR, "Editor and tools"));
    actionGroup.add(createToggleAction(FRAME, "Empty frame"));
    myToolbar = ActionManager.getInstance().createActionToolbar(getTitle(), actionGroup, true);
    JComponent toolbarComponent = myToolbar.getComponent();
    toolbarComponent.setBorder(JBUI.Borders.empty());
    myTargetPanel.add(toolbarComponent);

    myAnchorPanel.add(createAnchorPanel(myAnchorGroup), BorderLayout.CENTER);
    myFillPanel.add(createFillPanel(myFillGroup, getDisposable()), BorderLayout.CENTER);
    ((CardLayout)myPreviewPanel.getLayout()).show(myPreviewPanel, EDITOR);
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
    for (Enumeration<AbstractButton> e = getFillRbGroup().getElements(); e.hasMoreElements();) {
      AbstractButton button = e.nextElement();
      button.setActionCommand(button.getText());
      button.addItemListener(this::fillOrAnchorChanged);
    }
    for (Enumeration<AbstractButton> e = getAnchorRbGroup().getElements(); e.hasMoreElements();) {
      AbstractButton button = e.nextElement();
      button.setActionCommand(button.getText());
      button.addItemListener(this::fillOrAnchorChanged);
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
    boolean perProject = !Comparing.equal(getBackgroundSpec(myProject, getSystemProp(true)), getBackgroundSpec(null, getSystemProp(true)));
    myThisProjectOnlyCb.setSelected(perProject);
    myAdjusting = false;
  }

  private AnAction createToggleAction(String target, String text) {
    class A extends IconWithTextAction implements DumbAware, Toggleable {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setText(text);
        e.getPresentation().putClientProperty(Toggleable.SELECTED_PROPERTY, target.equals(myPreviewTarget));
        super.update(e);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        targetChanged(target);
        myToolbar.updateActionsImmediately();
      }
    }
    return new A();
  }

  private void opacityChanged() {
    updatePreview();
  }

  private void imagePathChanged() {
    updatePreview();
  }

  /** @noinspection unused*/
  private void fillOrAnchorChanged(ItemEvent event) {
    updatePreview();
  }

  private void targetChanged(String target) {
    myPreviewTarget = target;
    retrieveExistingValue();

    ((CardLayout)myPreviewPanel.getLayout()).show(myPreviewPanel, myPreviewTarget);
    if (EDITOR.equals(myPreviewTarget)) {
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
    String anchor = split.length > 3 ? split[3] : "center";
    setSelectedPath(split[0]);
    myOpacitySlider.setValue(opacity);
    myOpacitySpinner.setValue(opacity);
    setSelected(getFillRbGroup(), fill);
    setSelected(getAnchorRbGroup(), anchor);
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
    return getSystemProp(EDITOR.equals(myPreviewTarget));
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
    String anchor = getAnchorRbGroup().getSelection().getActionCommand().replace('-', '_');
    return path.trim() + "," + myOpacitySpinner.getValue() + "," + (type + "," + anchor).toLowerCase(Locale.ENGLISH);
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
    return myFillGroup;
  }

  private ButtonGroup getAnchorRbGroup() {
    return myAnchorGroup;
  }

  @NotNull
  private static Color getSelectionBackground() {
    return ColorUtil.mix(UIUtil.getListSelectionBackground(), UIUtil.getLabelBackground(), UIUtil.isUnderDarcula() ? .5 : .75);
  }

  @NotNull
  private static JPanel createAnchorPanel(@NotNull ButtonGroup buttonGroup) {
    IdeBackgroundUtil.Anchor[] values = IdeBackgroundUtil.Anchor.values();
    String[] names = new String[values.length];
    for (int i = 0; i < names.length; i++) {
      names[i] = values[i].name().replace('_', '-').toLowerCase(Locale.ENGLISH);
    }
    Color color = getSelectionBackground();
    JPanel buttonPanel = new JPanel(new GridLayout(3, 3, 1, 1));
    for (int i = 0; i < names.length; i ++) {
      JRadioButton button = new JRadioButton(names[i], values[i] == IdeBackgroundUtil.Anchor.CENTER);
      addClickablePanel(buttonPanel, buttonGroup, button, color);
    }
    return buttonPanel;
  }

  @NotNull
  private static JPanel createFillPanel(@NotNull ButtonGroup buttonGroup, @NotNull Disposable disposable) {
    Fill[] values = Fill.values();
    String[] names = new String[values.length];
    BufferedImage image = sampleImage();
    for (int i = 0; i < names.length; i++) {
      names[i] = values[i].name().replace('_', '-').toLowerCase(Locale.ENGLISH);
    }
    Color color = getSelectionBackground();
    JPanel buttonPanel = new JPanel(new GridLayout(1, values.length, 1, 1));
    for (int i = 0; i < values.length; i ++) {
      JRadioButton radioButton = new JRadioButton(names[i], values[i] == Fill.SCALE);
      JBPanelWithEmptyText clickablePanel = addClickablePanel(buttonPanel, buttonGroup, radioButton, color);
      createTemporaryBackgroundTransform(
        clickablePanel, image, values[i], IdeBackgroundUtil.Anchor.CENTER, 1f, JBUI.insets(2), disposable);
    }
    return buttonPanel;
  }

  @NotNull
  private static BufferedImage sampleImage() {
    int size = 16;
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics ig = image.getGraphics();
    ig.setColor(new Color(0, true));
    ig.fillRect(0, 0, size, size);
    Color imageColor = UIUtil.getLabelForeground();
    ig.setColor(imageColor);
    ig.drawRect(size / 4, size / 4, size / 2 - 1, size / 2 - 1);
    ig.drawRect(1, 1, size - 3, size - 3);
    ig.dispose();
    return image;
  }

  private static JBPanelWithEmptyText addClickablePanel(@NotNull JPanel buttonPanel,
                                                        @NotNull ButtonGroup buttonGroup,
                                                        @NotNull JRadioButton button,
                                                        @NotNull Color color) {
    JBPanelWithEmptyText panel = new JBPanelWithEmptyText() {

      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getSize();
        d.width = Math.max(d.width, d.height);
        d.height = Math.max(d.width, d.height);
        return d;
      }

      @Override
      public Dimension getMinimumSize() {
        return getPreferredSize();
      }

      @Override
      public Dimension getMaximumSize() {
        return getPreferredSize();
      }

      @Override
      public Color getBackground() {
        return button.isSelected() ? color : super.getBackground();
      }

      @Override
      public boolean isOpaque() {
        return true;
      }
    };
    panel.getEmptyText().clear();
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        button.setSelected(true);
        buttonPanel.invalidate();
        buttonPanel.repaint();
        return true;
      }
    }.installOn(panel);
    panel.setBorder(BorderFactory.createLineBorder(color));
    buttonGroup.add(button);
    buttonPanel.add(panel);
    return panel;
  }
}
