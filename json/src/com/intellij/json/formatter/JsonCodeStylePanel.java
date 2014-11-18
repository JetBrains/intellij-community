package com.intellij.json.formatter;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.json.JsonFileType;
import com.intellij.json.JsonLanguage;
import com.intellij.json.formatter.JsonCodeStyleSettings.PropertyAlignment;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Mikhail Golubev
 */
public class JsonCodeStylePanel extends CodeStyleAbstractPanel {
  public static final String ALIGNMENT_SAMPLE = "{\n" +
                                                "    \"foo\": {\n" +
                                                "        \"bar\": true,\n" +
                                                "        \"baz\": false\n" +
                                                "    },\n" +
                                                "    \"quux\": [\n" +
                                                "        1, 2.0, 3e0, 4.0e0\n" +
                                                "    ],\n" +
                                                "    \"longPropertyName\": null\n" +
                                                "}";

  private JComboBox myPropertiesAlignmentCombo;
  private JPanel myPreviewPanel;
  private JPanel myPanel;

  @SuppressWarnings("unchecked")
  public JsonCodeStylePanel(@NotNull CodeStyleSettings settings) {
    super(JsonLanguage.INSTANCE, null, settings);
    addPanelToWatch(myPanel);
    installPreviewPanel(myPreviewPanel);

    // Initialize combo box with property value alignment types
    for (PropertyAlignment alignment : PropertyAlignment.values()) {
      myPropertiesAlignmentCombo.addItem(alignment);
    }
    myPropertiesAlignmentCombo.setRenderer(new ListCellRendererWrapper<PropertyAlignment>() {
      @Override
      public void customize(JList list, PropertyAlignment value, int index, boolean selected, boolean hasFocus) {
        setText(value.getDescription());
      }
    });
    myPropertiesAlignmentCombo.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          somethingChanged();
        }
      }
    });
  }

  @Override
  protected int getRightMargin() {
    return 80;
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(new LightVirtualFile("a.json"), scheme, null);
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return JsonFileType.INSTANCE;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return ALIGNMENT_SAMPLE;
  }

  @Override
  public void apply(CodeStyleSettings settings) throws ConfigurationException {
    getCustomSettings(settings).PROPERTY_ALIGNMENT = getSelectedAlignmentType().getId();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return getCustomSettings(settings).PROPERTY_ALIGNMENT != getSelectedAlignmentType().getId();
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    for (int i = 0; i < myPropertiesAlignmentCombo.getItemCount(); i++) {
      if (((PropertyAlignment)myPropertiesAlignmentCombo.getItemAt(i)).getId() == getCustomSettings(settings).PROPERTY_ALIGNMENT) {
        myPropertiesAlignmentCombo.setSelectedIndex(i);
        break;
      }
    }
  }

  @NotNull
  private PropertyAlignment getSelectedAlignmentType() {
    return (PropertyAlignment)myPropertiesAlignmentCombo.getSelectedItem();
  }

  @NotNull
  private JsonCodeStyleSettings getCustomSettings(@NotNull CodeStyleSettings settings) {
    return settings.getCustomSettings(JsonCodeStyleSettings.class);
  }
}
