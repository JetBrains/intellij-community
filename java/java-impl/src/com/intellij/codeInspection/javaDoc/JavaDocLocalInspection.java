/*
 * Copyright (c) 2005 Jet Brains. All Rights Reserved.
 */
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.intention.impl.AddJavadocIntention;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.Gray;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;

public class JavaDocLocalInspection extends JavaDocLocalInspectionBase {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.javaDoc.JavaDocLocalInspection");
  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @Override
  protected LocalQuickFix createAddJavadocFix(@NotNull final PsiElement nameIdentifier, boolean isOnTheFly) {
    if (isOnTheFly) {
      final AddJavadocIntention intention = new AddJavadocIntention();
      return new LocalQuickFixAndIntentionActionOnPsiElement(nameIdentifier) {
        @Override
        public void invoke(@NotNull Project project,
                           @NotNull PsiFile file,
                           @Nullable("is null when called from inspection") Editor editor,
                           @NotNull PsiElement startElement,
                           @NotNull PsiElement endElement) {
          intention.invoke(project, editor, startElement);
        }

        @NotNull
        @Override
        public String getText() {
          return intention.getText();
        }

        @Nls
        @NotNull
        @Override
        public String getFamilyName() {
          return intention.getFamilyName();
        }
      };
    }

    return super.createAddJavadocFix(nameIdentifier, false);
  }

  private class OptionsPanel extends JPanel {
    private JPanel createOptionsPanel(String[] modifiers, String[] tags, Options options) {
      JPanel pane = new JPanel(new GridLayout(1, tags == null ? 1 : 2));

      pane.add(createScopePanel(modifiers, options));
      if (tags != null) {
        pane.add(createTagsPanel(tags, options));
      }

      pane.validate();

      return pane;
    }

    private JPanel createTagsPanel(String[] tags, Options options) {
      JPanel panel = new JPanel(new GridBagLayout());
      panel.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createTitledBorder(
        InspectionsBundle.message("inspection.javadoc.required.tags.option.title"), true),
                                                         BorderFactory.createEmptyBorder(0, 3, 3, 3)));

      GridBagConstraints gc = new GridBagConstraints();
      gc.weightx = 1;
      gc.weighty = 0;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;


      for (int i = 0; i < tags.length; i++) {
        JCheckBox box = new JCheckBox(tags[i]);
        gc.gridy = i;
        if (i == tags.length - 1) gc.weighty = 1;
        panel.add(box, gc);
        box.setSelected(isTagRequired(options, tags[i]));
        box.addChangeListener(new MyChangeListener(box, options, tags[i]));
      }

      return panel;
    }

    private class MyChangeListener implements ChangeListener {
      private final JCheckBox myCheckBox;
      private final Options   myOptions;
      private final String    myTagName;

      public MyChangeListener(JCheckBox checkBox, Options options, String tagName) {
        myCheckBox = checkBox;
        myOptions = options;
        myTagName = tagName;
      }

      @Override
      public void stateChanged(ChangeEvent e) {
        if (myCheckBox.isSelected()) {
          if (!isTagRequired(myOptions, myTagName)) {
            myOptions.REQUIRED_TAGS += myTagName;
          }
        }
        else {
          myOptions.REQUIRED_TAGS = myOptions.REQUIRED_TAGS.replaceAll(myTagName, "");
        }
      }
    }

    private JPanel createScopePanel(final String[] modifiers, final Options options) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createTitledBorder(
        InspectionsBundle.message("inspection.scope.for.title"), true),
                                                         BorderFactory.createEmptyBorder(0, 3, 3, 3)));

      final Hashtable<Integer, JComponent> sliderLabels = new Hashtable<Integer, JComponent>();
      for (int i = 0; i < modifiers.length; i++) {
        sliderLabels.put(i + 1, new JLabel(modifiers[i]));
      }

      final JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, modifiers.length, 1);

      slider.setLabelTable(sliderLabels);
      slider.putClientProperty(UIUtil.JSLIDER_ISFILLED, Boolean.TRUE);
      slider.setPreferredSize(JBUI.size(80, 50));
      slider.setPaintLabels(true);
      slider.setSnapToTicks(true);
      slider.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          int value = slider.getValue();
          options.ACCESS_JAVADOC_REQUIRED_FOR = modifiers[value - 1];
          for (Integer key : sliderLabels.keySet()) {
            sliderLabels.get(key).setForeground(key.intValue() <= value ? Color.black : Gray._100);
          }
        }
      });

      Color fore = Color.black;
      for (int i = 0; i < modifiers.length; i++) {
        sliderLabels.get(i + 1).setForeground(fore);

        if (modifiers[i].equals(options.ACCESS_JAVADOC_REQUIRED_FOR)) {
          slider.setValue(i + 1);
          fore = Gray._100;
        }
      }

      panel.add(slider, BorderLayout.WEST);

      return panel;
    }

    public OptionsPanel() {
      super(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0),0,0 );
      add(createAdditionalJavadocTagsPanel(), gc);
      JTabbedPane tabs = new JBTabbedPane(SwingConstants.BOTTOM);
      @NonNls String[] tags = new String[]{"@author", "@version", "@since"};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.package"), createOptionsPanel(new String[]{NONE, PUBLIC},
                                                                                                            tags,PACKAGE_OPTIONS));

      tags = new String[]{"@author", "@version", "@since", "@param"};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title"), createOptionsPanel(new String[]{NONE, PUBLIC, PACKAGE_LOCAL},
                                                                                                    tags,
                                                                                                    TOP_LEVEL_CLASS_OPTIONS));
      tags = new String[]{"@return", "@param", InspectionsBundle.message("inspection.javadoc.throws.or.exception.option")};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.method"), createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE},
                                                                                                           tags,
                                                                                                           METHOD_OPTIONS));
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.field"), createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE},
                                                                                                          null,
                                                                                                          FIELD_OPTIONS));
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.inner.class"), createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE},
                                                                                                                null,
                                                                                                                INNER_CLASS_OPTIONS));
      add(tabs, gc);

      final JCheckBox checkBox = new JCheckBox(InspectionsBundle.message("inspection.javadoc.option.ignore.deprecated"),
                                               IGNORE_DEPRECATED);
      checkBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          IGNORE_DEPRECATED = checkBox.isSelected();
        }
      });
      gc.gridwidth = 1;
      add(checkBox, gc);
      final JCheckBox periodCheckBox = new JCheckBox(InspectionsBundle.message("inspection.javadoc.option.ignore.period"),
                                                     IGNORE_JAVADOC_PERIOD);
      periodCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          IGNORE_JAVADOC_PERIOD = periodCheckBox.isSelected();
        }
      });
      add(periodCheckBox, gc);

      final JCheckBox ignoreDuplicateThrowsCheckBox = new JCheckBox("Ignore duplicate throws tag",
                                                                    getIgnoreDuplicatedThrows());
      ignoreDuplicateThrowsCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          setIgnoreDuplicatedThrows(ignoreDuplicateThrowsCheckBox.isSelected());
        }
      });
      add(ignoreDuplicateThrowsCheckBox, gc);

      final JCheckBox ignorePointToItselfCheckBox = new JCheckBox("Ignore javadoc pointing to itself", IGNORE_POINT_TO_ITSELF);
      ignorePointToItselfCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          IGNORE_POINT_TO_ITSELF = ignorePointToItselfCheckBox.isSelected();
        }
      });
      add(ignorePointToItselfCheckBox, gc);
      final JCheckBox ignoreSimpleAccessorsCheckBox = new JCheckBox("Ignore simple property accessors", myIgnoreSimpleAccessors);
      ignoreSimpleAccessorsCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myIgnoreSimpleAccessors = ignoreSimpleAccessorsCheckBox.isSelected();
        }
      });
      add(ignoreSimpleAccessorsCheckBox, gc);
    }

    public FieldPanel createAdditionalJavadocTagsPanel(){
      FieldPanel additionalTagsPanel = new FieldPanel(InspectionsBundle.message("inspection.javadoc.label.text"), InspectionsBundle.message("inspection.javadoc.dialog.title"), null, null);
      additionalTagsPanel.setPreferredSize(new Dimension(150, additionalTagsPanel.getPreferredSize().height));
      additionalTagsPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          final Document document = e.getDocument();
          try {
            final String text = document.getText(0, document.getLength());
            if (text != null) {
              myAdditionalJavadocTags = text.trim();
            }
          }
          catch (BadLocationException e1) {
             LOG.error(e1);
          }
        }
      });
      additionalTagsPanel.setText(myAdditionalJavadocTags);
      return additionalTagsPanel;
    }
  }
}
