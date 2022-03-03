// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.AddJavadocIntention;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.pom.Navigatable;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
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

import static com.intellij.util.ObjectUtils.notNull;

public class JavaDocLocalInspection extends LocalInspectionTool {
  private static final ExtensionPointName<Condition<PsiMember>> EP_NAME = new ExtensionPointName<>("com.intellij.javaDocNotNecessary");

  public static final String SHORT_NAME = "JavaDoc";
  protected static final String NONE = "none";
  protected static final String PACKAGE_LOCAL = "package";
  protected static final String PUBLIC = PsiModifier.PUBLIC;
  protected static final String PROTECTED = PsiModifier.PROTECTED;
  protected static final String PRIVATE = PsiModifier.PRIVATE;
  private static final String IGNORE_ACCESSORS_ATTR_NAME = "IGNORE_ACCESSORS";
  private static final String IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME = "IGNORE_DUPLICATED_THROWS_TAGS";
  private static final String MODULE_OPTIONS_TAG_NAME = "MODULE_OPTIONS";
  protected final Options PACKAGE_OPTIONS = new Options("none", "");
  protected final Options MODULE_OPTIONS = new Options("none", "");
  public Options TOP_LEVEL_CLASS_OPTIONS = new Options("none", "");
  public Options INNER_CLASS_OPTIONS = new Options("none", "");
  public Options METHOD_OPTIONS = new Options("none", "@return@param@throws or @exception");
  public Options FIELD_OPTIONS = new Options("none", "");
  public boolean IGNORE_DEPRECATED;
  public boolean IGNORE_JAVADOC_PERIOD = true;
  /** @deprecated unused, left to avoid modifications in config files */
  @Deprecated
  public boolean IGNORE_DUPLICATED_THROWS;
  public boolean IGNORE_POINT_TO_ITSELF;
  public @NlsSafe String myAdditionalJavadocTags = "";
  private boolean myIgnoreDuplicatedThrows = true;
  private boolean myIgnoreEmptyDescriptions;
  private boolean myIgnoreSimpleAccessors;

  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private static LocalQuickFix createAddJavadocFix(@NotNull PsiElement nameIdentifier, boolean isOnTheFly) {
    return isOnTheFly ? new AddJavadocFix(nameIdentifier) : null;
  }

  private static LocalQuickFix createAddMissingTagFix(@NotNull String tag, @NotNull String value) {
    return new AddMissingTagFix(tag, value);
  }

  private static LocalQuickFix createAddMissingParamTagFix(@NotNull String name) {
    return new AddMissingParamTagFix(name);
  }

  protected LocalQuickFix createRegisterTagFix(@NotNull String tag) {
    return new AddUnknownTagToCustoms(this, tag);
  }

  private static LocalQuickFix createRemoveTagFix(@NotNull String tag) {
    return new RemoveTagFix(tag);
  }

  public void setPackageOption(String modifier, String tags) {
    PACKAGE_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = modifier;
    PACKAGE_OPTIONS.REQUIRED_TAGS = tags;
  }

  public void registerAdditionalTag(@NotNull String tag) {
    if (!myAdditionalJavadocTags.isEmpty()) {
      myAdditionalJavadocTags += "," + tag;
    }
    else {
      myAdditionalJavadocTags = tag;
    }
  }

  public boolean isIgnoreDuplicatedThrows() {
    return myIgnoreDuplicatedThrows;
  }

  public void setIgnoreDuplicatedThrows(boolean ignoreDuplicatedThrows) {
    myIgnoreDuplicatedThrows = ignoreDuplicatedThrows;
  }

  public void setIgnoreEmptyDescriptions(boolean ignoreEmptyDescriptions) {
    myIgnoreEmptyDescriptions = ignoreEmptyDescriptions;
  }

  public boolean isIgnoreSimpleAccessors() {
    return myIgnoreSimpleAccessors;
  }

  public void setIgnoreSimpleAccessors(boolean ignoreSimpleAccessors) {
    myIgnoreSimpleAccessors = ignoreSimpleAccessors;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);

    if (myIgnoreSimpleAccessors) {
      JDOMExternalizerUtil.writeCustomField(node, IGNORE_ACCESSORS_ATTR_NAME, String.valueOf(true));
    }

    if (!myIgnoreDuplicatedThrows) {
      JDOMExternalizerUtil.writeCustomField(node, IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME, String.valueOf(false));
    }

    if (MODULE_OPTIONS.isModified()) {
      MODULE_OPTIONS.writeExternal(JDOMExternalizerUtil.writeOption(node, MODULE_OPTIONS_TAG_NAME));
    }

    if (PACKAGE_OPTIONS.isModified()) {
      PACKAGE_OPTIONS.writeExternal(node);
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);

    String ignoreAccessors = JDOMExternalizerUtil.readCustomField(node, IGNORE_ACCESSORS_ATTR_NAME);
    if (ignoreAccessors != null) {
      myIgnoreSimpleAccessors = Boolean.parseBoolean(ignoreAccessors);
    }

    String ignoreDuplicatedThrows = JDOMExternalizerUtil.readCustomField(node, IGNORE_DUPLICATED_THROWS_TAGS_ATTR_NAME);
    if (ignoreDuplicatedThrows != null) {
      myIgnoreDuplicatedThrows = Boolean.parseBoolean(ignoreDuplicatedThrows);
    }

    Element moduleOptions = JDOMExternalizerUtil.readOption(node, MODULE_OPTIONS_TAG_NAME);
    if (moduleOptions != null) {
      MODULE_OPTIONS.readExternal(moduleOptions);
    }

    PACKAGE_OPTIONS.readExternal(node);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(PsiJavaFile file) {
        if (PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
          checkFile(file, holder, isOnTheFly);
        }
      }

      @Override
      public void visitModule(PsiJavaModule module) {
        checkModule(module, holder, isOnTheFly);
      }

      @Override
      public void visitClass(PsiClass aClass) {
        checkClass(aClass, holder, isOnTheFly);
      }

      @Override
      public void visitField(PsiField field) {
        checkField(field, holder, isOnTheFly);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        checkMethod(method, holder, isOnTheFly);
      }
    };
  }

  private void checkFile(PsiJavaFile file, ProblemsHolder delegate, boolean isOnTheFly) {
    PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(file.getContainingDirectory());
    if (pkg == null) return;

    PsiDocComment docComment = PsiTreeUtil.getChildOfType(file, PsiDocComment.class);
    if (IGNORE_DEPRECATED && isDeprecated(pkg, docComment)) {
      return;
    }

    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, pkg);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);
    if (docComment != null) {
      PsiDocTag[] tags = docComment.getTags();
      checkBasics(docComment, tags, pkg, required, PACKAGE_OPTIONS, holder);
    }
    else if (required) {
      PsiElement toHighlight = notNull(file.getPackageStatement(), file);
      JavadocHighlightUtil.reportMissingTag(toHighlight, holder);
    }
  }

  private void checkModule(PsiJavaModule module, ProblemsHolder delegate, boolean isOnTheFly) {
    PsiDocComment docComment = module.getDocComment();
    if (IGNORE_DEPRECATED && isDeprecated(module, docComment)) {
      return;
    }

    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, module);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);

    if (docComment != null) {
      checkBasics(docComment, docComment.getTags(), module, required, MODULE_OPTIONS, holder);
    }
    else if (required) {
      JavadocHighlightUtil.reportMissingTag(module.getNameIdentifier(), holder);
    }
  }

  private void checkClass(PsiClass aClass, ProblemsHolder delegate, boolean isOnTheFly) {
    if (aClass instanceof PsiAnonymousClass || aClass instanceof PsiSyntheticClass || aClass instanceof PsiTypeParameter) {
      return;
    }
    if (IGNORE_DEPRECATED && aClass.isDeprecated()) {
      return;
    }

    PsiDocComment docComment = aClass.getDocComment();
    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, aClass);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);

    if (docComment != null) {
      PsiDocTag[] tags = docComment.getTags();

      Options options = ClassUtil.isTopLevelClass(aClass) ? TOP_LEVEL_CLASS_OPTIONS : INNER_CLASS_OPTIONS;
      checkBasics(docComment, tags, aClass, required, options, holder);

      if (required && isTagRequired(options, "param")) {
        JavadocHighlightUtil.checkMissingTypeParamTags(aClass, tags, docComment.getFirstChild(), holder);
      }
    }
    else if (required) {
      PsiElement toHighlight = notNull(aClass.getNameIdentifier(), aClass);
      JavadocHighlightUtil.reportMissingTag(toHighlight, holder);
    }
  }

  private void checkField(PsiField field, ProblemsHolder delegate, boolean isOnTheFly) {
    if (IGNORE_DEPRECATED && isDeprecated(field)) {
      return;
    }

    PsiDocComment docComment = field.getDocComment();
    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, field);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);

    if (docComment != null) {
      checkBasics(docComment, docComment.getTags(), field, required, FIELD_OPTIONS, holder);
    }
    else if (required) {
      JavadocHighlightUtil.reportMissingTag(field.getNameIdentifier(), holder);
    }
  }

  private void checkMethod(PsiMethod method, ProblemsHolder delegate, boolean isOnTheFly) {
    if (method instanceof SyntheticElement) {
      return;
    }
    if (IGNORE_DEPRECATED && isDeprecated(method)) {
      return;
    }
    if (myIgnoreSimpleAccessors && PropertyUtilBase.isSimplePropertyAccessor(method)) {
      return;
    }

    PsiDocComment docComment = method.getDocComment();
    boolean hasSupers = method.findSuperMethods().length > 0;
    boolean required = JavadocHighlightUtil.isJavaDocRequired(this, method);
    ProblemHolderImpl holder = new ProblemHolderImpl(delegate, isOnTheFly);

    if (docComment != null) {
      if (!isInherited(docComment, method)) {
        PsiDocTag[] tags = docComment.getTags();

        if (required && !hasSupers) {
          if (isTagRequired(METHOD_OPTIONS, "return")) {
            JavadocHighlightUtil.checkMissingReturnTag(tags, method, docComment.getFirstChild(), holder);
          }
          if (isTagRequired(METHOD_OPTIONS, "param")) {
            JavadocHighlightUtil.checkMissingParamTags(tags, method, docComment.getFirstChild(), holder);
            JavadocHighlightUtil.checkMissingTypeParamTags(method, tags, docComment.getFirstChild(), holder);
          }
          if (isTagRequired(METHOD_OPTIONS, "throws")) {
            JavadocHighlightUtil.checkMissingThrowsTags(tags, method, docComment.getFirstChild(), holder);
          }
        }

        if (!myIgnoreEmptyDescriptions) {
          JavadocHighlightUtil.checkEmptyMethodTagsDescription(tags, method, holder);
        }

        checkBasics(docComment, tags, method, false, METHOD_OPTIONS, holder);
      }
    }
    else if (required && !hasSupers) {
      PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null) {
        if (EP_NAME.extensions().noneMatch(condition -> condition.value(method))) {
          JavadocHighlightUtil.reportMissingTag(nameIdentifier, holder);
        }
      }
    }
  }

  private void checkBasics(PsiDocComment docComment, PsiDocTag[] tags, PsiElement context, boolean required, Options options, ProblemHolderImpl holder) {
    if (required) {
      JavadocHighlightUtil.checkRequiredTags(tags, options, docComment.getFirstChild(), holder);
    }

    JavadocHighlightUtil.checkRequiredTagDescriptions(tags, holder);

    JavadocHighlightUtil.checkTagValues(tags, context, holder);

    if (!IGNORE_JAVADOC_PERIOD) {
      JavadocHighlightUtil.checkForPeriod(docComment, context, holder);
    }

    JavadocHighlightUtil.checkInlineTags(docComment.getDescriptionElements(), holder);

    JavadocHighlightUtil.checkForBadCharacters(docComment, holder);

    JavadocHighlightUtil.checkDuplicateTags(tags, holder);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.javadoc.issues");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "javadoc";
  }

  private static boolean isDeprecated(PsiModifierListOwner element, PsiDocComment docComment) {
    return PsiImplUtil.isDeprecatedByAnnotation(element) || docComment != null && docComment.findTagByName("deprecated") != null;
  }

  protected static boolean isTagRequired(Options options, String tag) {
    return options.REQUIRED_TAGS.contains(tag);
  }

  private static boolean isDeprecated(PsiDocCommentOwner element) {
    return element.isDeprecated() || element.getContainingClass() != null && element.getContainingClass().isDeprecated();
  }

  private static boolean isInherited(PsiDocComment docComment, PsiMethod psiMethod) {
    for (PsiElement descriptionElement : docComment.getDescriptionElements()) {
      if (descriptionElement instanceof PsiInlineDocTag && "inheritDoc".equals(((PsiInlineDocTag)descriptionElement).getName())) {
        return true;
      }
    }

    if (docComment.findTagByName("inheritDoc") != null) {
      JavadocTagInfo tagInfo = JavadocManager.SERVICE.getInstance(psiMethod.getProject()).getTagInfo("inheritDoc");
      if (tagInfo != null && tagInfo.isValidInContext(psiMethod)) {
        return true;
      }
    }

    return false;
  }

  @SuppressWarnings("deprecation")
  public static class Options implements JDOMExternalizable {
    public String ACCESS_JAVADOC_REQUIRED_FOR = NONE;
    public String REQUIRED_TAGS = "";

    public Options() {}

    public Options(String accessJavadocRequiredFor, String requiredTags) {
      ACCESS_JAVADOC_REQUIRED_FOR = accessJavadocRequiredFor;
      REQUIRED_TAGS = requiredTags;
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }

    private boolean isModified() {
      return !(ACCESS_JAVADOC_REQUIRED_FOR.equals(NONE) && REQUIRED_TAGS.isEmpty());
    }
  }

  private class OptionsPanel extends InspectionOptionsPanel {
    OptionsPanel() {
      super();

      String title = JavaBundle.message("inspection.javadoc.dialog.title");
      FieldPanel additionalTagsPanel = new FieldPanel(JavaBundle.message("inspection.javadoc.label.text"), title, null, null);
      additionalTagsPanel.setPreferredSize(new Dimension(150, additionalTagsPanel.getPreferredSize().height));
      additionalTagsPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
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
      add(additionalTagsPanel, "growx, wrap");

      JCheckBox checkBox = new JCheckBox(JavaBundle.message("inspection.javadoc.option.ignore.deprecated"), IGNORE_DEPRECATED);
      checkBox.addActionListener(e -> IGNORE_DEPRECATED = checkBox.isSelected());
      add(checkBox);

      JCheckBox periodCheckBox = new JCheckBox(JavaBundle.message("inspection.javadoc.option.ignore.period"), IGNORE_JAVADOC_PERIOD);
      periodCheckBox.addActionListener(e -> IGNORE_JAVADOC_PERIOD = periodCheckBox.isSelected());
      add(periodCheckBox);

      JCheckBox ignoreDuplicateThrowsCheckBox = new JCheckBox(JavaBundle.message("inspection.javadoc.option.ignore.throws"), isIgnoreDuplicatedThrows());
      ignoreDuplicateThrowsCheckBox.addActionListener(e -> setIgnoreDuplicatedThrows(ignoreDuplicateThrowsCheckBox.isSelected()));
      add(ignoreDuplicateThrowsCheckBox);

      JCheckBox ignorePointToItselfCheckBox = new JCheckBox(JavaBundle.message("inspection.javadoc.option.ignore.self.ref"), IGNORE_POINT_TO_ITSELF);
      ignorePointToItselfCheckBox.addActionListener(e -> IGNORE_POINT_TO_ITSELF = ignorePointToItselfCheckBox.isSelected());
      add(ignorePointToItselfCheckBox);

      JCheckBox ignoreSimpleAccessorsCheckBox = new JCheckBox(JavaBundle.message("inspection.javadoc.option.ignore.simple"), isIgnoreSimpleAccessors());
      ignoreSimpleAccessorsCheckBox.addActionListener(e -> setIgnoreSimpleAccessors(ignoreSimpleAccessorsCheckBox.isSelected()));
      add(ignoreSimpleAccessorsCheckBox);

      JTabbedPane tabs = new JBTabbedPane();
      String[] tags = {"@author", "@version", "@since"};
      tabs.addTab(JavaBundle.message("inspection.javadoc.option.tab.title.package"),
                  createOptionsPanel(new String[]{NONE, PUBLIC}, tags, PACKAGE_OPTIONS));
      tabs.addTab(JavaBundle.message("inspection.javadoc.option.tab.title.module"),
                  createOptionsPanel(new String[]{NONE, PUBLIC}, tags, MODULE_OPTIONS));
      tags = new String[]{"@author", "@version", "@since", "@param"};
      tabs.addTab(JavaBundle.message("inspection.javadoc.option.tab.title"),
                  createOptionsPanel(new String[]{NONE, PUBLIC, PACKAGE_LOCAL}, tags, TOP_LEVEL_CLASS_OPTIONS));
      tags = new String[]{"@return", "@param", JavaBundle.message("inspection.javadoc.throws.or.exception.option")};
      tabs.addTab(JavaBundle.message("inspection.javadoc.option.tab.title.method"),
                  createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE}, tags, METHOD_OPTIONS));
      tabs.addTab(JavaBundle.message("inspection.javadoc.option.tab.title.field"),
                  createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE}, null, FIELD_OPTIONS));
      tabs.addTab(JavaBundle.message("inspection.javadoc.option.tab.title.inner.class"),
                  createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE}, null, INNER_CLASS_OPTIONS));
      add(tabs, "growx, gaptop 20");
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
        IdeBorderFactory.createTitledBorder(JavaBundle.message("inspection.scope.for.title")),
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
      panel.setBorder(IdeBorderFactory.createTitledBorder(JavaBundle.message("inspection.javadoc.required.tags.option.title")));

      GridBagConstraints gc = new GridBagConstraints();
      gc.weightx = 1;
      gc.weighty = 0;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;
      gc.insets.bottom = 8;
      gc.insets.left = -2;

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

      MyChangeListener(JCheckBox checkBox, Options options, String tagName) {
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

    AddJavadocFix(PsiElement nameIdentifier) {
      super(nameIdentifier);
      myIntention = new AddJavadocIntention();
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
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

    AddMissingTagFix(@NotNull String tag, @NotNull String value) {
      myTag = tag;
      myValue = value;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiDocComment docComment = PsiTreeUtil.getParentOfType(descriptor.getEndElement(), PsiDocComment.class);
      if (docComment != null) {
        PsiDocTag tag = JavaPsiFacade.getElementFactory(project).createDocTagFromText("@" + myTag + " " + myValue);

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
      return JavaBundle.message("inspection.javadoc.problem.add.tag", myTag, myValue);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaBundle.message("inspection.javadoc.problem.add.tag.family");
    }
  }

  private static class AddMissingParamTagFix extends AddMissingTagFix {
    private final String myName;

    AddMissingParamTagFix(String name) {
      super("param", name);
      myName = name;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.javadoc.problem.add.param.tag.family");
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
      return valueElement != null && valueElement.getText().trim().startsWith(name);
    }

    @Override
    @NotNull
    public String getName() {
      return JavaBundle.message("inspection.javadoc.problem.add.param.tag", myName);
    }
  }

  private static class AddUnknownTagToCustoms implements LocalQuickFix {
    private final JavaDocLocalInspection myInspection;
    private final String myTag;

    AddUnknownTagToCustoms(@NotNull JavaDocLocalInspection inspection, @NotNull String tag) {
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

  private static class RemoveTagFix implements LocalQuickFix {
    private final String myTagName;

    RemoveTagFix(String tagName) {
      myTagName = tagName;
    }

    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("quickfix.text.remove.javadoc.0", myTagName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("quickfix.family.remove.javadoc.tag");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiDocTag tag = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiDocTag.class);
      if (tag != null) {
        tag.delete();
      }
    }
  }

  private final class ProblemHolderImpl implements JavadocHighlightUtil.ProblemHolder {
    private final ProblemsHolder myHolder;
    private final boolean myOnTheFly;

    private ProblemHolderImpl(ProblemsHolder holder, boolean onTheFly) {
      myHolder = holder;
      myOnTheFly = onTheFly;
    }

    @Override
    public Project project() {
      return myHolder.getManager().getProject();
    }

    @Override
    public JavaDocLocalInspection inspection() {
      return JavaDocLocalInspection.this;
    }

    @Override
    public void problem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix) {
      final LocalQuickFix[] fixes = new LocalQuickFix[] { fix };
      problemWithFixes(toHighlight, message, fixes);
    }

    @Override
    public void problemWithFixes(@NotNull PsiElement toHighlight, @NotNull @Nls String message, LocalQuickFix@NotNull [] fixes) {
      myHolder.registerProblem(myHolder.getManager().createProblemDescriptor(
        toHighlight, message, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly, false));
    }
    @Override
    public void eolProblem(@NotNull PsiElement toHighlight, @NotNull @Nls String message, @Nullable LocalQuickFix fix) {
      LocalQuickFix[] fixes = fix != null ? new LocalQuickFix[]{fix} : null;
      myHolder.registerProblem(myHolder.getManager().createProblemDescriptor(
        toHighlight, message, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly, true));
    }

    @Override
    public LocalQuickFix addJavadocFix(@NotNull PsiElement nameIdentifier) {
      return createAddJavadocFix(nameIdentifier, myOnTheFly);
    }

    @Override
    public LocalQuickFix addMissingTagFix(@NotNull String tag, @NotNull String value) {
      return createAddMissingTagFix(tag, value);
    }

    @Override
    public LocalQuickFix addMissingParamTagFix(@NotNull String name) {
      return createAddMissingParamTagFix(name);
    }

    @Override
    public LocalQuickFix registerTagFix(@NotNull String tag) {
      return createRegisterTagFix(tag);
    }

    @Override
    public LocalQuickFix removeTagFix(@NotNull String tag) {
      return createRemoveTagFix(tag);
    }
  }
}