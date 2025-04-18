// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.OptionTreeWithPreviewPanel;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.frontback.impl.JavaFrontbackBundle;
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

    myEnableCheckBox = new JCheckBox(JavaFrontbackBundle.message("checkbox.enable.javadoc.formatting"));
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
    initBooleanField("WRAP_COMMENTS", JavaFrontbackBundle.message("checkbox.wrap.at.right.margin"), getOtherGroup());
    initCustomOptions(getOtherGroup());
  }

  @Override
  protected int getRightMargin() {
    return 47;
  }

  @Override
  protected String getPreviewText() {                    //| Margin is here
    return """
      package sample;
      public class Sample {
        /**
         * This is a method description that is long enough to exceed right margin.
         *
         * Another paragraph of the description placed after blank line.
         * <p/>
         * Line with manual
         * line feed.
         * @param i short named parameter description
         * @param longParameterName long named parameter description
         * @param missingDescription
         * @return return description.
         * @throws XXXException description.
         * @throws YException description.
         * @throws ZException
         *
         * @invalidTag   */
        public abstract String sampleMethod(int i, int longParameterName, int missingDescription) throws XXXException, YException, ZException;

        /** One-line comment */
        public abstract String sampleMethod2();

        /**
         * Simple method description
         * @return
         */
        public abstract String sampleMethod3();
      """;
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
  public void apply(@NotNull CodeStyleSettings settings) {
    super.apply(settings);
    settings.getCustomSettings(JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING = myEnableCheckBox.isSelected();
  }

  @Override
  protected void resetImpl(final @NotNull CodeStyleSettings settings) {
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
  protected final @NotNull FileType getFileType() {
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
    return JavaFrontbackBundle.message("title.javadoc");
  }

  @Override
  public @Nullable Language getDefaultLanguage() {
    return JavaLanguage.INSTANCE;
  }

  public static @NlsContexts.Label String getOtherGroup() {
    return JavaFrontbackBundle.message("group.javadoc.other");
  }

  public static @NlsContexts.Label String getInvalidTagsGroup() {
    return JavaFrontbackBundle.message("group.javadoc.invalid.tags");
  }

  public static @NlsContexts.Label String getBlankLinesGroup() {
    return JavaFrontbackBundle.message("group.javadoc.blank.lines");
  }

  public static @NlsContexts.Label String getAlignmentGroup() {
    return JavaFrontbackBundle.message("group.javadoc.alignment");
  }
}
