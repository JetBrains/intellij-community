// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.OptionTreeWithPreviewPanel;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.ui.border.CustomLineBorder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class JavaDocFormattingPanel extends OptionTreeWithPreviewPanel {
  private JCheckBox myEnableCheckBox;

  private final JPanel myJavaDocPanel = new JPanel(new BorderLayout());

  public JavaDocFormattingPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  protected void init() {
    super.init();

    myEnableCheckBox = new JCheckBox(JavaBundle.message("checkbox.enable.javadoc.formatting"));
    myEnableCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        update();
      }
    });

    myPanel.setBorder(new CustomLineBorder(OnePixelDivider.BACKGROUND, 1, 0, 0, 0));
    myJavaDocPanel.add(BorderLayout.CENTER, myPanel);
    myJavaDocPanel.add(myEnableCheckBox, BorderLayout.NORTH);
  }

  @Override
  public LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.LANGUAGE_SPECIFIC;
  }

  @Override
  public JComponent getPanel() {
    return myJavaDocPanel;
  }

  private void update() {
    setEnabled(getPanel(), myEnableCheckBox.isSelected());
    myEnableCheckBox.setEnabled(true);
  }

  @Override
  protected void initTables() {
    initCustomOptions(getAlignmentGroup());
    initCustomOptions(getBlankLinesGroup());
    initCustomOptions(getInvalidTagsGroup());
    initBooleanField("WRAP_COMMENTS", JavaBundle.message("checkbox.wrap.at.right.margin"), getOtherGroup());
    initCustomOptions(getOtherGroup());
  }

  @Override
  protected int getRightMargin() {
    return 47;
  }

  @Override
  protected String getPreviewText() {                    //| Margin is here
    return "package sample;\n" +
           "public class Sample {\n" +
           "  /**\n" +
           "   * This is a method description that is long enough to exceed right margin.\n" +
           "   *\n" +
           "   * Another paragraph of the description placed after blank line.\n" +
           "   * <p/>\n" +
           "   * Line with manual\n" +
           "   * line feed.\n" +
           "   * @param i short named parameter description\n" +
           "   * @param longParameterName long named parameter description\n" +
           "   * @param missingDescription\n" +
           "   * @return return description.\n" +
           "   * @throws XXXException description.\n" +
           "   * @throws YException description.\n" +
           "   * @throws ZException\n" +
           "   *\n" +
           "   * @invalidTag" +
           "   */\n" +
           "  public abstract String sampleMethod(int i, int longParameterName, int missingDescription) throws XXXException, YException, ZException;\n" +
           "\n" +
           "  /** One-line comment */\n" +
           "  public abstract String sampleMethod2();\n" +
           "\n" +
           "  /**\n" +
           "   * Simple method description\n" +
           "   * @return\n" +
           "   */\n" +
           "  public abstract String sampleMethod3();\n";
  }


  private static void setEnabled(JComponent c, boolean enabled) {
    c.setEnabled(enabled);
    Component[] children = c.getComponents();
    for (Component child : children) {
      if (child instanceof JComponent) {
        setEnabled((JComponent)child, enabled);
      }
    }
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    super.apply(settings);
    settings.getCustomSettings(JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING = myEnableCheckBox.isSelected();
  }

  @Override
  protected void resetImpl(final CodeStyleSettings settings) {
    super.resetImpl(settings);
    myEnableCheckBox.setSelected(settings.getCustomSettings(JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING);
    update();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return super.isModified(settings) ||
           myEnableCheckBox.isSelected() != settings.getCustomSettings(JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING;
  }

  @Override
  @NotNull
  protected final FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  protected void customizeSettings() {
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(JavaLanguage.INSTANCE);
    if (provider != null) {
      provider.customizeSettings(this, getSettingsType());
    }
  }

  @Override
  protected @TabTitle @NotNull String getTabTitle() {
    return JavaBundle.message("title.javadoc");
  }

  @Nullable
  @Override
  public Language getDefaultLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @NlsContexts.Label
  public static String getOtherGroup() {
    return JavaBundle.message("group.javadoc.other");
  }

  @NlsContexts.Label
  public static String getInvalidTagsGroup() {
    return JavaBundle.message("group.javadoc.invalid.tags");
  }

  @NlsContexts.Label
  public static String getBlankLinesGroup() {
    return JavaBundle.message("group.javadoc.blank.lines");
  }

  @NlsContexts.Label
  public static String getAlignmentGroup() {
    return JavaBundle.message("group.javadoc.alignment");
  }
}
