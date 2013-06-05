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
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBMovePanel;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class FontOptions extends JPanel implements OptionsPanel{

  private static List<String> myFontNames;
  private static List<String> myMonospacedFontNames;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  @NotNull private final ColorAndFontOptions myOptions;

  @NotNull private final DefaultListModel myAllFontsModel      = new DefaultListModel();
  @NotNull private final JBList           myAllFontsList       = new JBList(myAllFontsModel);
  @NotNull private final DefaultListModel mySelectedFontsModel = new DefaultListModel();
  @NotNull private final JBList           mySelectedFontsList  = new JBList(mySelectedFontsModel);
  @NotNull private final JBMovePanel      myFontsControl       = new JBMovePanel(myAllFontsList, mySelectedFontsList) {
    @SuppressWarnings("SSBasedInspection")
    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (myFontNames == null) {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            initFontTables();
          }
        });
      }
    }
  };

  @NotNull private final JTextField myEditorFontSizeField = new MyTextField(4);
  @NotNull private final JTextField myLineSpacingField    = new MyTextField(4);

  @NotNull private final JBCheckBox myOnlyMonospacedCheckBox =
    new JBCheckBox(ApplicationBundle.message("checkbox.show.only.monospaced.fonts"));
  @Nullable private final Dimension myPreferredSize;

  private boolean myIsInSchemeChange;
  private final String  myTitle;

  public FontOptions(ColorAndFontOptions options) {
    this(options, ApplicationBundle.message("group.editor.font"));
  }

  protected FontOptions(@NotNull ColorAndFontOptions options, final String title) {
    super(new GridBagLayout());
    myOptions = options;
    myTitle = title;
    add(createEditorFontPanel(), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
    add(new JLabel(ApplicationBundle.message("label.fallback.fonts.list.description"),
                   MessageType.INFO.getDefaultIcon(),
                   SwingConstants.LEFT),
        new GridBag().anchor(GridBagConstraints.WEST));
    myFontsControl.setShowButtons(JBMovePanel.ButtonType.LEFT, JBMovePanel.ButtonType.RIGHT);
    myFontsControl.setListLabels(ApplicationBundle.message("title.font.available"), ApplicationBundle.message("title.font.selected"));
    myFontsControl.setEnabled(false); // Disable the controls until fonts are loaded.
    myFontsControl.setLeftInsertionStrategy(JBMovePanel.NATURAL_ORDER);
    myOnlyMonospacedCheckBox.setSelected(EditorColorsManager.getInstance().isUseOnlyMonospacedFonts());
    new ListSpeedSearch(myAllFontsList);
    new ListSpeedSearch(mySelectedFontsList);

    // Almost all other color scheme pages use the following pattern:
    //
    //    __________________________________________
    //   |  color keys  |  color and font settings  |
    //
    // Here page's height is calculated on the 'color and font settings' preferred height (debugged a lot to ensure that).
    // The idea is to configure current page to use the same height as other pages. That's why we set it up to use the same preferred
    // size as 'color and font settings' control.
    myPreferredSize = new ColorAndFontDescriptionPanel().getPreferredSize();
    if (myFontNames != null) {
      onFontsInit();
    }
    initListeners();
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    return myPreferredSize == null ? super.getPreferredSize() : myPreferredSize;
  }

  private void initListeners() {
    myOnlyMonospacedCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean onlyMonospaced = myOnlyMonospacedCheckBox.isSelected();
        EditorColorsManager.getInstance().setUseOnlyMonospacedFonts(onlyMonospaced);
        onFontsInit();
      }
    });

    mySelectedFontsModel.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        syncFontFamilies();
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        syncFontFamilies();
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        syncFontFamilies();
      }

      private void syncFontFamilies() {
        if (myIsInSchemeChange) {
          return;
        }
        FontPreferences fontPreferences = getFontPreferences();
        fontPreferences.clearFonts();
        Enumeration elements = mySelectedFontsModel.elements();
        while (elements.hasMoreElements()) {
          String fontFamily = (String)elements.nextElement();
          // Don't save a single 'default' font family at the font preferences.
          if (mySelectedFontsModel.getSize() > 1 || !FontPreferences.DEFAULT_FONT_NAME.equals(fontFamily)) {
            fontPreferences.addFontFamily(fontFamily);
          }
        }
      }
    });

    mySelectedFontsList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        Object value = mySelectedFontsList.getSelectedValue();
        if (value != null) {
          boolean toRestore = myIsInSchemeChange;
          myIsInSchemeChange = true;
          try {
            myEditorFontSizeField.setText(String.valueOf(getFontPreferences().getSize((String)value)));
          }
          finally {
            myIsInSchemeChange = toRestore;
          }
        }
      }
    });

    myEditorFontSizeField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        if (myIsInSchemeChange || !SwingUtilities.isEventDispatchThread()) return;
        try {
          int fontSize = Integer.parseInt(myEditorFontSizeField.getText());
          if (fontSize < 1) fontSize = 1;
          if (fontSize > OptionsConstants.MAX_EDITOR_FONT_SIZE) fontSize = OptionsConstants.MAX_EDITOR_FONT_SIZE;
          Object selectedFont = mySelectedFontsList.getSelectedValue();
          if (selectedFont != null) {
            FontPreferences fontPreferences = getFontPreferences();
            fontPreferences.register((String)selectedFont, fontSize);
          }
        }
        catch (NumberFormatException e) {
          // OK, ignore
        }
        finally {
          updateDescription(true);
        }
      }
    });

    myLineSpacingField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        if (myIsInSchemeChange) return;
        float lineSpacing = 1;
        try {
          lineSpacing = Float.parseFloat(myLineSpacingField.getText());
        }
        catch (NumberFormatException e) {
          // OK, ignore
        }
        finally {
          if (lineSpacing <= 0) lineSpacing = 1;
          if (lineSpacing > 30) lineSpacing = 30;
          if (getLineSpacing() != lineSpacing) {
            setCurrentLineSpacing(lineSpacing);
          }
          updateDescription(true);
        }
      }
    });

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
    mySelectedFontsModel.clear();
    FontPreferences fontPreferences = getFontPreferences();
    List<String> fontFamilies = fontPreferences.getEffectiveFontFamilies();
    Set<String> selectedFonts = ContainerUtilRt.newHashSet();
    Object selectedValue = mySelectedFontsList.getSelectedValue();
    mySelectedFontsModel.clear();
    if (fontFamilies.isEmpty()) {
      // Add default font.
      mySelectedFontsModel.addElement(fontPreferences.getFontFamily());
      selectedFonts.add(fontPreferences.getFontFamily());
    }
    else {
      for (String fontFamily : fontFamilies) {
        mySelectedFontsModel.addElement(fontFamily);
        selectedFonts.add(fontFamily);
      }
    }

    int newSelectionIndex = 0;
    if (selectedValue != null) {
      newSelectionIndex = Math.max(0, mySelectedFontsModel.indexOf(selectedValue));
    }
    mySelectedFontsList.setSelectedIndex(newSelectionIndex);

    for (int i = myAllFontsModel.size() - 1; i >= 0; i--) {
      if (selectedFonts.contains(myAllFontsModel.getElementAt(i))) {
        myAllFontsModel.remove(i);
      }
    }
    myEditorFontSizeField.setText(String.valueOf(fontPreferences.getSize(fontPreferences.getFontFamily())));

    boolean enabled = !ColorAndFontOptions.isReadOnly(myOptions.getSelectedScheme());
    myOnlyMonospacedCheckBox.setEnabled(enabled);
    myLineSpacingField.setEnabled(enabled);
    myEditorFontSizeField.setEditable(enabled);
    myFontsControl.setEnabled(enabled);

    myIsInSchemeChange = false;
  }

  private void onFontsInit() {
    assert myFontNames != null;
    Object selectedValue = myAllFontsList.getSelectedValue();
    myAllFontsModel.clear();
    List<String> availableFonts = myOnlyMonospacedCheckBox.isSelected() ? myMonospacedFontNames : myFontNames;
    int newSelectionIndex = 0;
    int i = 0;
    for (String name : availableFonts) {
      if (!mySelectedFontsModel.contains(name)) { // Don't bother with performance here in assumption that fallback fonts sequence is short
        myAllFontsModel.addElement(name);
        if (name.equals(selectedValue)) {
          newSelectionIndex = i;
        }
        i++;
      }
    }
    myAllFontsList.setSelectedIndex(newSelectionIndex);
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

  private JPanel createEditorFontPanel() {
    JPanel editorFontPanel = new JPanel(new GridBagLayout());
    Insets borderInsets = new Insets(IdeBorderFactory.TITLED_BORDER_TOP_INSET,
                                     IdeBorderFactory.TITLED_BORDER_LEFT_INSET,
                                     0,
                                     IdeBorderFactory.TITLED_BORDER_RIGHT_INSET);
    editorFontPanel.setBorder(IdeBorderFactory.createTitledBorder(myTitle, false, borderInsets));

    Insets insets = new Insets(0, 0, 5, 0);
    GridBag constraints = new GridBag().insets(insets);
    editorFontPanel.add(myOnlyMonospacedCheckBox, constraints);

    insets.left = 8;
    editorFontPanel.add(new JLabel(ApplicationBundle.message("editbox.font.size")), constraints);

    insets.left = 2;
    editorFontPanel.add(myEditorFontSizeField, constraints);

    insets.left = 8;
    editorFontPanel.add(new JLabel(ApplicationBundle.message("editbox.line.spacing")), constraints);

    insets.left = 2;
    editorFontPanel.add(myLineSpacingField, constraints);

    editorFontPanel.add(new JLabel(""), new GridBag().insets(insets).weightx(1).fillCellHorizontally().coverLine());

    editorFontPanel.add(myFontsControl, new GridBag().weightx(1).weighty(1).fillCell().coverLine().insets(insets));

    return editorFontPanel;
  }

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
  private void initFontTables() {
    if (myFontNames == null) {
      myFontNames = new ArrayList<String>();
      myMonospacedFontNames = new ArrayList<String>();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new InitFontsRunnable(), ApplicationBundle.message("progress.analyzing.fonts"), false, null);
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

  private static class MyTextField extends JTextField {
    private MyTextField(int size) {
      super(size);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }
  }

  private class InitFontsRunnable implements Runnable {
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
          onFontsInit();
        }
      });
    }
  }
}
