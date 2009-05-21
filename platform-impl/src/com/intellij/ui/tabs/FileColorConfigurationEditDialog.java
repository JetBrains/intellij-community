package com.intellij.ui.tabs;

import com.intellij.notification.impl.ui.StickyButton;
import com.intellij.notification.impl.ui.StickyButtonUI;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Collection;

/**
 * @author spleaner
 */
public class FileColorConfigurationEditDialog extends DialogWrapper {
  private FileColorConfiguration myConfiguration;
  private JTextField myPathField;
  private JCheckBox myShareCheckbox;
  private FileColorManagerImpl myManager;
  private HashMap<String,AbstractButton> myColorToButtonMap;

  public FileColorConfigurationEditDialog(@NotNull final FileColorManagerImpl manager, @Nullable final FileColorConfiguration configuration) {
    super(true);

    setTitle(configuration == null ? "Add color label" : "Edit color label");
    setResizable(false);

    myManager = manager;
    myConfiguration = configuration;

    init();
    updateOKButton();
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel result = new JPanel();
    result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));

    myPathField = new JTextField(myConfiguration == null ? "" : myConfiguration.getPath());
    myPathField.setEditable(false);
    myPathField.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        updateOKButton();
      }

      public void removeUpdate(DocumentEvent e) {
        updateOKButton();
      }

      public void changedUpdate(DocumentEvent e) {
        updateOKButton();
      }
    });

    final JPanel pathPanel = new JPanel();
    pathPanel.setLayout(new BorderLayout());

    final TextFieldWithBrowseButton withBrowseButton = new TextFieldWithBrowseButton(myPathField);
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, true, false, false);
    descriptor.setRoot(myManager.getProject().getBaseDir());

    withBrowseButton.addBrowseFolderListener("Choose path", "Choose path", myManager.getProject(), descriptor);

    withBrowseButton.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
    final JLabel pathLabel = new JLabel("Path:");
    pathLabel.setDisplayedMnemonic('P');
    pathLabel.setLabelFor(withBrowseButton);
    pathPanel.add(pathLabel, BorderLayout.WEST);
    pathPanel.add(withBrowseButton, BorderLayout.CENTER);
    result.add(pathPanel);

    final JPanel colorPanel = new JPanel();
    colorPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
    colorPanel.setLayout(new BoxLayout(colorPanel, BoxLayout.X_AXIS));
    final JLabel colorLabel = new JLabel("Color:");
    colorPanel.add(colorLabel);
    colorPanel.add(createColorButtonsPanel(myConfiguration));
    colorPanel.add(Box.createHorizontalGlue());
    result.add(colorPanel);

    final JPanel checkboxPanel = new JPanel(new BorderLayout());
    checkboxPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
    myShareCheckbox = new JCheckBox("Share configuration", myConfiguration == null ? false : myManager.isShared(myConfiguration));
    myShareCheckbox.setMnemonic('S');
    checkboxPanel.add(myShareCheckbox, BorderLayout.WEST);
    result.add(checkboxPanel);

    return result;
  }

  @Override
  protected void doOKAction() {
    close(OK_EXIT_CODE);

    if (myConfiguration != null) {
      myConfiguration.setPath(myPathField.getText());
      myConfiguration.setColorName(getColorName());
    } else {
      myConfiguration = new FileColorConfiguration(myPathField.getText(), getColorName());
    }
  }

  public boolean isShared() {
    return myShareCheckbox.isSelected();
  }

  public FileColorConfiguration getConfiguration() {
    return myConfiguration;
  }

  private JComponent createColorButtonsPanel(final FileColorConfiguration configuration) {
    final JPanel result = new JPanel();
    result.setLayout(new BoxLayout(result, BoxLayout.X_AXIS));
    result.setBackground(Color.WHITE);
    result.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    final ButtonGroup group = new ButtonGroup();
    
    myColorToButtonMap = new HashMap<String, AbstractButton>();

    final Collection<String> names = myManager.getColorNames();
    for (final String name : names) {
      final ColorButton colorButton = new ColorButton(name, myManager.getColor(name));
      colorButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          updateOKButton();
        }
      });
      colorButton.setBackground(Color.WHITE);
      colorButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
      group.add(colorButton);
      result.add(colorButton);
      myColorToButtonMap.put(name, colorButton);
      result.add(Box.createHorizontalStrut(5));
    }

    if (configuration != null) {
      final AbstractButton button = myColorToButtonMap.get(configuration.getColorName());
      if (button != null) {
        button.setSelected(true);
      }
    }

    return result;
  }

  private String getColorName() {
    for (String name : myColorToButtonMap.keySet()) {
      final AbstractButton button = myColorToButtonMap.get(name);
      if (button.isSelected()) {
        return name;
      }
    }

    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPathField;
  }

  private void updateOKButton() {
    getOKAction().setEnabled(isOKActionEnabled());
  }

  @Override
  public boolean isOKActionEnabled() {
    final String path = myPathField.getText();
    return path != null && path.length() > 0 && getColorName() != null; 
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  private class ColorButton extends StickyButton {
    private Color myColor;

    private ColorButton(final String text, final Color color) {
      super(text);

      setUI(new ColorButtonUI());

      myColor = color;
    }

    Color getColor() {
      return myColor;
    }

    @Override
    public Color getForeground() {
      if (getModel().isSelected()) {
        return Color.BLACK;
      } else if (getModel().isRollover()) {
        return Color.GRAY;
      } else {
        return getColor();
      }
    }

    @Override
    protected ButtonUI createUI() {
      return new ColorButtonUI();
    }
  }

  private class ColorButtonUI extends StickyButtonUI<ColorButton> {

    @Override
    protected Color getBackgroundColor(final ColorButton button) {
      return button.getColor();
    }

    @Override
    protected Color getFocusColor(ColorButton button) {
      return button.getColor().darker();
    }

    @Override
    protected Color getSelectionColor(ColorButton button) {
      return button.getColor();
    }

    @Override
    protected Color getRolloverColor(ColorButton button) {
      return button.getColor();
    }

    @Override
    protected int getArcSize() {
      return 20;
    }
  }
}
