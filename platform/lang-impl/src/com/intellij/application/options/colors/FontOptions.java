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

package com.intellij.application.options.colors;

import com.intellij.application.options.OptionsConstants;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FontOptions extends JPanel implements OptionsPanel{

  private static List<String> myFontNames;
  private static List<String> myMonospacedFontNames;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  @NotNull private final ColorAndFontOptions myOptions;

  @NotNull private final JTextField myEditorFontSizeField = new JTextField(4);
  @NotNull private final JTextField myLineSpacingField    = new JTextField(4);
  private final FontNameCombo myPrimaryCombo = new FontNameCombo(null);
  private final JCheckBox myUseSecondaryFontCheckbox = new JCheckBox(ApplicationBundle.message("secondary.font"));
  private final FontNameCombo mySecondaryCombo = new FontNameCombo(null);

  @NotNull private final JBCheckBox myOnlyMonospacedCheckBox =
    new JBCheckBox(ApplicationBundle.message("checkbox.show.only.monospaced.fonts"));

  private boolean myIsInSchemeChange;


  public FontOptions(ColorAndFontOptions options) {
    this(options, ApplicationBundle.message("group.editor.font"));
  }

  protected FontOptions(@NotNull ColorAndFontOptions options, final String title) {
    setLayout(new MigLayout("ins 0, gap 5, flowx"));
    Insets borderInsets = new Insets(IdeBorderFactory.TITLED_BORDER_TOP_INSET,
                                     IdeBorderFactory.TITLED_BORDER_LEFT_INSET,
                                     0,
                                     IdeBorderFactory.TITLED_BORDER_RIGHT_INSET);
    setBorder(IdeBorderFactory.createTitledBorder(title, false, borderInsets));
    myOptions = options;
    add(myOnlyMonospacedCheckBox, "sgx b, sx 2");

    add(new JLabel(ApplicationBundle.message("primary.font")), "newline, ax right");
    add(myPrimaryCombo, "sgx b");
    add(new JLabel(ApplicationBundle.message("editbox.font.size")), "gapleft 20");
    add(myEditorFontSizeField);
    add(new JLabel(ApplicationBundle.message("editbox.line.spacing")), "gapleft 20");
    add(myLineSpacingField);

    add(new JLabel(ApplicationBundle.message("label.fallback.fonts.list.description"),
                   MessageType.INFO.getDefaultIcon(),
                   SwingConstants.LEFT), "newline, sx 5");
    add(myUseSecondaryFontCheckbox, "newline, ax right");
    add(mySecondaryCombo, "sgx b");

    myOnlyMonospacedCheckBox.setBorder(null);
    myUseSecondaryFontCheckbox.setBorder(null);
    mySecondaryCombo.setEnabled(false);

    myOnlyMonospacedCheckBox.setSelected(EditorColorsManager.getInstance().isUseOnlyMonospacedFonts());
    myOnlyMonospacedCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        EditorColorsManager.getInstance().setUseOnlyMonospacedFonts(myOnlyMonospacedCheckBox.isSelected());
        myPrimaryCombo.updateModel();
        mySecondaryCombo.updateModel();
      }
    });
    myUseSecondaryFontCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySecondaryCombo.setEnabled(myUseSecondaryFontCheckbox.isSelected());
        syncFontFamilies();
      }
    });
    ItemListener itemListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          syncFontFamilies();
        }
      }
    };
    myPrimaryCombo.addItemListener(itemListener);
    mySecondaryCombo.addItemListener(itemListener);

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        syncFontFamilies();
      }
    };
    myPrimaryCombo.addActionListener(actionListener);
    mySecondaryCombo.addActionListener(actionListener);

    myEditorFontSizeField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        if (myIsInSchemeChange || !SwingUtilities.isEventDispatchThread()) return;
        Object selectedFont = myPrimaryCombo.getSelectedItem();
        if (selectedFont instanceof String) {
          FontPreferences fontPreferences = getFontPreferences();
          fontPreferences.register((String)selectedFont, getFontSizeFromField());
        }
        updateDescription(true);
      }
    });

    myLineSpacingField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        if (myIsInSchemeChange) return;
        float lineSpacing = getLineSpacingFromField();
        if (getLineSpacing() != lineSpacing) {
          setCurrentLineSpacing(lineSpacing);
        }
        updateDescription(true);
      }
    });
  }

  private int getFontSizeFromField() {
    try {
      return Math.min(OptionsConstants.MAX_EDITOR_FONT_SIZE, Math.max(1, Integer.parseInt(myEditorFontSizeField.getText())));
    }
    catch (NumberFormatException e) {
      return OptionsConstants.DEFAULT_EDITOR_FONT_SIZE;
    }
  }

  private float getLineSpacingFromField() {
    try {
       return Math.min(30, Math.max(.6F, Float.parseFloat(myLineSpacingField.getText())));
    } catch (NumberFormatException e){
      return 1;
    }
  }

  private void syncFontFamilies() {
    if (myIsInSchemeChange) {
      return;
    }
    FontPreferences fontPreferences = getFontPreferences();
    fontPreferences.clearFonts();
    String primaryFontFamily = (String)myPrimaryCombo.getSelectedItem();
    String secondaryFontFamily = mySecondaryCombo.isEnabled() ? (String)mySecondaryCombo.getSelectedItem() : null;
    int fontSize = getFontSizeFromField();
    if (primaryFontFamily != null ) {
      if (!FontPreferences.DEFAULT_FONT_NAME.equals(primaryFontFamily)) {
        fontPreferences.addFontFamily(primaryFontFamily);
      }
      fontPreferences.register(primaryFontFamily, fontSize);
    }
    if (secondaryFontFamily != null) {
      if (!FontPreferences.DEFAULT_FONT_NAME.equals(secondaryFontFamily)){
        fontPreferences.addFontFamily(secondaryFontFamily);
      }
      fontPreferences.register(secondaryFontFamily, fontSize);
    }
    updateDescription(true);
  }


  public static void showReadOnlyMessage(JComponent parent, final boolean sharedScheme) {
    if (!sharedScheme) {
      Messages.showMessageDialog(
        parent,
        ApplicationBundle.message("error.readonly.scheme.cannot.be.modified"),
        ApplicationBundle.message("title.cannot.modify.readonly.scheme"),
        Messages.getInformationIcon()
      );
    }
    else {
      Messages.showMessageDialog(
        parent,
        ApplicationBundle.message("error.shared.scheme.cannot.be.modified"),
        ApplicationBundle.message("title.cannot.modify.readonly.scheme"),
        Messages.getInformationIcon()
      );
    }
  }

  @Override
  public void updateOptionsList() {
    myIsInSchemeChange = true;

    myLineSpacingField.setText(Float.toString(getLineSpacing()));
    FontPreferences fontPreferences = getFontPreferences();
    List<String> fontFamilies = fontPreferences.getEffectiveFontFamilies();
    myPrimaryCombo.setSelectedItem(fontPreferences.getFontFamily());
    boolean isThereSecondaryFont = fontFamilies.size() > 1;
    myUseSecondaryFontCheckbox.setSelected(isThereSecondaryFont);
    mySecondaryCombo.setSelectedItem(isThereSecondaryFont ? fontFamilies.get(1) : null);
    myEditorFontSizeField.setText(String.valueOf(fontPreferences.getSize(fontPreferences.getFontFamily())));

    boolean readOnly = ColorAndFontOptions.isReadOnly(myOptions.getSelectedScheme());
    myPrimaryCombo.setEnabled(!readOnly);
    mySecondaryCombo.setEnabled(isThereSecondaryFont && !readOnly);
    myOnlyMonospacedCheckBox.setEnabled(!readOnly);
    myLineSpacingField.setEnabled(!readOnly);
    myEditorFontSizeField.setEditable(!readOnly);
    myUseSecondaryFontCheckbox.setEnabled(!readOnly);

    myIsInSchemeChange = false;
  }

  @NotNull
  protected FontPreferences getFontPreferences() {
    return getCurrentScheme().getFontPreferences();
  }

  protected float getLineSpacing() {
    return getCurrentScheme().getLineSpacing();
  }

  protected void setCurrentLineSpacing(float lineSpacing) {
    getCurrentScheme().setLineSpacing(lineSpacing);
  }

  @Override
  @Nullable
  public Runnable showOption(final String option) {
    return null;
  }

  @Override
  public void applyChangesToScheme() {
  }

  @Override
  public void selectOption(final String typeToSelect) {
  }

  protected EditorColorsScheme getCurrentScheme() {
    return myOptions.getSelectedScheme();
  }

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
  private void initFontTables(FontNameCombo popupCallback) {
    if (myFontNames == null) {
      myFontNames = new ArrayList<String>();
      myMonospacedFontNames = new ArrayList<String>();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new InitFontsRunnable(popupCallback), ApplicationBundle.message("progress.analyzing.fonts"), false, null);
    }
  }

  public boolean updateDescription(boolean modified) {
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    if (modified && (ColorAndFontOptions.isReadOnly(scheme) || ColorSettingsUtil.isSharedScheme(scheme))) {
      showReadOnlyMessage(this, ColorSettingsUtil.isSharedScheme(scheme));
      return false;
    }

    myDispatcher.getMulticaster().fontChanged();

    return true;
  }

  @Override
  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public JPanel getPanel() {
    return this;
  }

  @Override
  public Set<String> processListOptions() {
    return new HashSet<String>();
  }

  private class InitFontsRunnable implements Runnable {
    private final FontNameCombo myPopupCallback;

    private InitFontsRunnable(FontNameCombo popupCallback) {
      myPopupCallback = popupCallback;
    }

    @Override
    public void run() {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

      GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
      String[] fontNames = graphicsEnvironment.getAvailableFontFamilyNames();
      for (final String fontName : fontNames) {
        //noinspection HardCodedStringLiteral
        if (fontName.endsWith(".bold") || fontName.endsWith(".italic")) {
          continue;
        }
        try {
          Font plainFont = new Font(fontName, Font.PLAIN, OptionsConstants.DEFAULT_EDITOR_FONT_SIZE);
          if (plainFont.canDisplay('W')) {
            Font boldFont = plainFont.deriveFont(Font.BOLD);
            if (progress != null) {
              progress.setText(ApplicationBundle.message("progress.analysing.font", fontName));
            }
            FontMetrics plainMetrics = getFontMetrics(plainFont);
            FontMetrics boldMetrics = getFontMetrics(boldFont);
            if (plainMetrics.getDescent() < 0 ||
                boldMetrics.getDescent() < 0 ||
                plainMetrics.getAscent() < 0 ||
                boldMetrics.getAscent() < 0) {
              continue;
            }
            int plainL = plainMetrics.charWidth('l');
            int boldL = boldMetrics.charWidth('l');
            int plainW = plainMetrics.charWidth('W');
            int boldW = boldMetrics.charWidth('W');
            int plainSpace = plainMetrics.charWidth(' ');
            int boldSpace = boldMetrics.charWidth(' ');
            if (plainL <= 0 || boldL <= 0 || plainW <= 0 || boldW <= 0 || plainSpace <= 0 || boldSpace <= 0) {
              continue;
            }
            myFontNames.add(fontName);
            if (plainL == plainW && plainL == boldL && plainW == boldW && plainSpace == boldSpace) {
              myMonospacedFontNames.add(fontName);
            }
          }
        }
        catch (Throwable e) {
          // JRE has problems working with the font. Just skip.
        }
      }

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myPrimaryCombo.updateModel();
          mySecondaryCombo.updateModel();
          myPopupCallback.showPopup();
        }
      });
    }
  }

  private class FontNameCombo extends JComboBox {
    private final DefaultComboBoxModel myModel;
    private Boolean myMonospacedOnly = null;

    private FontNameCombo(String selectedName) {
      setModel(myModel = new DefaultComboBoxModel());
      updateModel();
      setSelectedItem(selectedName);
    }

    private void updateModel() {
      if (myFontNames == null || myMonospacedFontNames == null) return;

      if (myMonospacedOnly == null || myMonospacedOnly.booleanValue() != EditorColorsManager.getInstance().isUseOnlyMonospacedFonts()) {
        myMonospacedOnly = EditorColorsManager.getInstance().isUseOnlyMonospacedFonts();

        Object tmp = getSelectedItem();
        myModel.removeAllElements();
        List toAdd = myMonospacedOnly ? myMonospacedFontNames : myFontNames;
        for (Object o : toAdd) {
          myModel.addElement(o);
        }
        if (myModel.getIndexOf(tmp) != -1) {
          setSelectedItem(tmp);
        } else {
          setSelectedItem(FontPreferences.DEFAULT_FONT_NAME);
        }

        fireActionEvent();
        revalidate();
        repaint();
      }
    }

    @Override
    public void setSelectedItem(Object anObject) {
      if (myModel.getSize() == 0 && anObject != null) {
        myModel.addElement(anObject);
      }
      super.setSelectedItem(anObject);
    }


  @Nullable
    private JList getPopupList() {
      ComboPopup popup = ReflectionUtil.getField(getUI().getClass(), getUI(), ComboPopup.class, "popup");
      return (popup != null) ? popup.getList() : null;
    }

    @Override
    public void firePopupMenuWillBecomeVisible() {
      super.firePopupMenuWillBecomeVisible();
      if (myFontNames == null) {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            initFontTables(FontNameCombo.this);
          }
        });
      }
      final JList list = getPopupList();
      if (list != null && !(list.getCellRenderer() instanceof MyListCellRenderer)) {
        list.setCellRenderer(new MyListCellRenderer());
      }
    }
  }

  private static class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(
      JList list,
      Object value,
      int index,
      boolean isSelected,
      boolean cellHasFocus) {
      Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof String) {
        c.setFont(new Font((String) value, Font.PLAIN, 14));
      }
      return c;
    }
  }
}
