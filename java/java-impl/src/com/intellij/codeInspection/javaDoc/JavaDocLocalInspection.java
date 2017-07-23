/*
 * Copyright (c) 2005 Jet Brains. All Rights Reserved.
 */
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.AddJavadocIntention;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.util.Hashtable;

public class JavaDocLocalInspection extends JavaDocLocalInspectionBase {
  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @Override
  protected LocalQuickFix createAddJavadocFix(@NotNull PsiElement nameIdentifier, boolean isOnTheFly) {
    return isOnTheFly ? new AddJavadocFix(nameIdentifier) : null;
  }

  @Override
  protected LocalQuickFix createAddMissingTagFix(@NotNull String tag, @NotNull String value, boolean isOnTheFly) {
    return new AddMissingTagFix(tag, value);
  }

  @Override
  protected LocalQuickFix createAddMissingParamTagFix(@NotNull String name, boolean isOnTheFly) {
    return new AddMissingParamTagFix(name);
  }

  @Override
  protected LocalQuickFix createRegisterTagFix(@NotNull String tag, boolean isOnTheFly) {
    return new AddUnknownTagToCustoms(this, tag);
  }

  private class OptionsPanel extends JPanel {
    public OptionsPanel() {
      super(new GridBagLayout());
      GridBagConstraints gc =
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                               JBUI.emptyInsets(), 0, 0);

      String title = InspectionsBundle.message("inspection.javadoc.dialog.title");
      FieldPanel additionalTagsPanel = new FieldPanel(InspectionsBundle.message("inspection.javadoc.label.text"), title, null, null);
      additionalTagsPanel.setPreferredSize(new Dimension(150, additionalTagsPanel.getPreferredSize().height));
      additionalTagsPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          Document document = e.getDocument();
          try {
            String text = document.getText(0, document.getLength());
            if (text != null) {
              myAdditionalJavadocTags = text.trim();
            }
          }
          catch (BadLocationException ex) {
            Logger.getInstance(JavaDocLocalInspection.class).error(ex);
          }
        }
      });
      additionalTagsPanel.setText(myAdditionalJavadocTags);
      add(additionalTagsPanel, gc);

      JTabbedPane tabs = new JBTabbedPane(SwingConstants.BOTTOM);
      String[] tags = {"@author", "@version", "@since"};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.package"),
               createOptionsPanel(new String[]{NONE, PUBLIC}, tags, PACKAGE_OPTIONS));
      tags = new String[]{"@author", "@version", "@since", "@param"};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title"),
               createOptionsPanel(new String[]{NONE, PUBLIC, PACKAGE_LOCAL}, tags, TOP_LEVEL_CLASS_OPTIONS));
      tags = new String[]{"@return", "@param", InspectionsBundle.message("inspection.javadoc.throws.or.exception.option")};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.method"),
               createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE}, tags, METHOD_OPTIONS));
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.field"),
               createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE}, null, FIELD_OPTIONS));
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.inner.class"),
               createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE}, null, INNER_CLASS_OPTIONS));
      add(tabs, gc);

      JCheckBox checkBox = new JCheckBox(InspectionsBundle.message("inspection.javadoc.option.ignore.deprecated"), IGNORE_DEPRECATED);
      checkBox.addActionListener(e -> IGNORE_DEPRECATED = checkBox.isSelected());
      gc.gridwidth = 1;
      add(checkBox, gc);

      JCheckBox periodCheckBox = new JCheckBox(InspectionsBundle.message("inspection.javadoc.option.ignore.period"), IGNORE_JAVADOC_PERIOD);
      periodCheckBox.addActionListener(e -> IGNORE_JAVADOC_PERIOD = periodCheckBox.isSelected());
      add(periodCheckBox, gc);

      JCheckBox ignoreDuplicateThrowsCheckBox = new JCheckBox("Ignore duplicate throws tag", isIgnoreDuplicatedThrows());
      ignoreDuplicateThrowsCheckBox.addActionListener(e -> setIgnoreDuplicatedThrows(ignoreDuplicateThrowsCheckBox.isSelected()));
      add(ignoreDuplicateThrowsCheckBox, gc);

      JCheckBox ignorePointToItselfCheckBox = new JCheckBox("Ignore javadoc pointing to itself", IGNORE_POINT_TO_ITSELF);
      ignorePointToItselfCheckBox.addActionListener(e -> IGNORE_POINT_TO_ITSELF = ignorePointToItselfCheckBox.isSelected());
      add(ignorePointToItselfCheckBox, gc);

      JCheckBox ignoreSimpleAccessorsCheckBox = new JCheckBox("Ignore simple property accessors", isIgnoreSimpleAccessors());
      ignoreSimpleAccessorsCheckBox.addActionListener(e -> setIgnoreSimpleAccessors(ignoreSimpleAccessorsCheckBox.isSelected()));
      add(ignoreSimpleAccessorsCheckBox, gc);
    }

    private JPanel createOptionsPanel(String[] modifiers, String[] tags, Options options) {
      JPanel pane = new JPanel(new GridLayout(1, tags == null ? 1 : 2));

      pane.add(createScopePanel(modifiers, options));
      if (tags != null) {
        pane.add(createTagsPanel(tags, options));
      }

      pane.validate();

      return pane;
    }

    private JPanel createScopePanel(String[] modifiers, Options options) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(BorderFactory.createCompoundBorder(
        IdeBorderFactory.createTitledBorder(InspectionsBundle.message("inspection.scope.for.title"), true),
        BorderFactory.createEmptyBorder(0, 3, 3, 3)));

      @SuppressWarnings("UseOfObsoleteCollectionType") Hashtable<Integer, JComponent> sliderLabels = new Hashtable<>();
      for (int i = 0; i < modifiers.length; i++) {
        sliderLabels.put(i + 1, new JLabel(modifiers[i]));
      }

      JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, modifiers.length, 1);
      slider.setLabelTable(sliderLabels);
      slider.putClientProperty(UIUtil.JSLIDER_ISFILLED, Boolean.TRUE);
      slider.setPreferredSize(JBUI.size(80, 50));
      slider.setPaintLabels(true);
      slider.setSnapToTicks(true);
      slider.addChangeListener(e -> {
        int value = slider.getValue();
        options.ACCESS_JAVADOC_REQUIRED_FOR = modifiers[value - 1];
        for (Integer key : sliderLabels.keySet()) {
          sliderLabels.get(key).setForeground(key.intValue() <= value ? JBColor.BLACK : Gray._100);
        }
      });

      Color fore = JBColor.BLACK;
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

    private JPanel createTagsPanel(String[] tags, Options options) {
      JPanel panel = new JPanel(new GridBagLayout());
      panel.setBorder(BorderFactory.createCompoundBorder(
        IdeBorderFactory.createTitledBorder(InspectionsBundle.message("inspection.javadoc.required.tags.option.title"), true),
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
      private final Options myOptions;
      private final String myTagName;

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
  }

  /*
   * Fixes
   */

  private static class AddJavadocFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final AddJavadocIntention myIntention;

    public AddJavadocFix(PsiElement nameIdentifier) {
      super(nameIdentifier);
      myIntention = new AddJavadocIntention();
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable("is null when called from inspection") Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      myIntention.invoke(project, editor, startElement);
    }

    @NotNull
    @Override
    public String getText() {
      //noinspection DialogTitleCapitalization
      return myIntention.getText();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myIntention.getFamilyName();
    }
  }

  private static class AddMissingTagFix implements LocalQuickFix {
    private final String myTag;
    private final String myValue;

    public AddMissingTagFix(@NotNull String tag, @NotNull String value) {
      myTag = tag;
      myValue = value;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiDocComment docComment = PsiTreeUtil.getParentOfType(descriptor.getEndElement(), PsiDocComment.class);
      if (docComment != null) {
        PsiDocTag tag = JavaPsiFacade.getInstance(project).getElementFactory().createDocTagFromText("@" + myTag + " " + myValue);

        PsiElement addedTag;
        PsiElement anchor = getAnchor(descriptor);
        if (anchor != null) {
          addedTag = docComment.addBefore(tag, anchor);
        }
        else {
          addedTag = docComment.add(tag);
        }
        moveCaretAfter(addedTag);
      }
    }

    @Nullable
    protected PsiElement getAnchor(ProblemDescriptor descriptor) {
      return null;
    }

    private static void moveCaretAfter(PsiElement newCaretPosition) {
      PsiElement sibling = newCaretPosition.getNextSibling();
      if (sibling != null) {
        ((Navigatable)sibling).navigate(true);
      }
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.javadoc.problem.add.tag", myTag, myValue);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      //noinspection DialogTitleCapitalization
      return InspectionsBundle.message("inspection.javadoc.problem.add.tag.family");
    }
  }

  private static class AddMissingParamTagFix extends AddMissingTagFix {
    private final String myName;

    public AddMissingParamTagFix(String name) {
      super("param", name);
      myName = name;
    }

    @Override
    @Nullable
    protected PsiElement getAnchor(ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element == null ? null : element.getParent();
      if (!(parent instanceof PsiDocComment)) return null;
      final PsiDocComment docComment = (PsiDocComment)parent;
      final PsiJavaDocumentedElement owner = docComment.getOwner();
      if (!(owner instanceof PsiMethod)) return null;
      PsiParameter[] parameters = ((PsiMethod)owner).getParameterList().getParameters();
      PsiParameter myParam = ContainerUtil.find(parameters, psiParameter -> myName.equals(psiParameter.getName()));
      if (myParam == null) return null;

      PsiDocTag[] tags = docComment.findTagsByName("param");
      if (tags.length == 0) { //insert as first tag or append to description
        tags = docComment.getTags();
        if (tags.length == 0) return null;
        return tags[0];
      }

      PsiParameter nextParam = PsiTreeUtil.getNextSiblingOfType(myParam, PsiParameter.class);
      while (nextParam != null) {
        for (PsiDocTag tag : tags) {
          if (matches(nextParam, tag)) {
            return tag;
          }
        }
        nextParam = PsiTreeUtil.getNextSiblingOfType(nextParam, PsiParameter.class);
      }

      PsiParameter prevParam = PsiTreeUtil.getPrevSiblingOfType(myParam, PsiParameter.class);
      while (prevParam != null) {
        for (PsiDocTag tag : tags) {
          if (matches(prevParam, tag)) {
            return PsiTreeUtil.getNextSiblingOfType(tag, PsiDocTag.class);
          }
        }
        prevParam = PsiTreeUtil.getPrevSiblingOfType(prevParam, PsiParameter.class);
      }

      return null;
    }

    private static boolean matches(PsiParameter param, PsiDocTag tag) {
      PsiDocTagValue valueElement = tag.getValueElement();
      String name = param.getName();
      return valueElement != null && name != null && valueElement.getText().trim().startsWith(name);
    }

    @Override
    @NotNull
    public String getName() {
      //noinspection DialogTitleCapitalization
      return InspectionsBundle.message("inspection.javadoc.problem.add.param.tag", myName);
    }
  }

  private static class AddUnknownTagToCustoms implements LocalQuickFix {
    private final JavaDocLocalInspectionBase myInspection;
    private final String myTag;

    public AddUnknownTagToCustoms(@NotNull JavaDocLocalInspectionBase inspection, @NotNull String tag) {
      myInspection = inspection;
      myTag = tag;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myInspection.registerAdditionalTag(myTag);
      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
    }

    @Override
    @NotNull
    public String getName() {
      return QuickFixBundle.message("add.docTag.to.custom.tags", myTag);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      //noinspection DialogTitleCapitalization
      return QuickFixBundle.message("fix.javadoc.family");
    }
  }
}