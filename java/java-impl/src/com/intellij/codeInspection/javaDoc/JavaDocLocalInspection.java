/*
 * Copyright (c) 2005 Jet Brains. All Rights Reserved.
 */
package com.intellij.codeInspection.javaDoc;

import com.intellij.ExtensionPoints;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.reference.RefJavaUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
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
import java.util.*;

public class JavaDocLocalInspection extends BaseLocalInspectionTool {
  private static final String REQUIRED_JAVADOC_IS_ABSENT = InspectionsBundle.message("inspection.javadoc.problem.descriptor");

  @NonNls private static final String NONE = "none";
  @NonNls private static final String PUBLIC = "public";
  @NonNls private static final String PROTECTED = "protected";
  @NonNls private static final String PACKAGE_LOCAL = "package";
  @NonNls private static final String PRIVATE = "private";
  @NonNls private static final Set<String> ourUniqueTags = new HashSet<String>();
  @NonNls public static final String SHORT_NAME = "JavaDoc";

  static {
    ourUniqueTags.add("return");
    ourUniqueTags.add("deprecated");
    ourUniqueTags.add("serial");
    ourUniqueTags.add("serialData");
  }


  public static class Options implements JDOMExternalizable {
    @NonNls public String ACCESS_JAVADOC_REQUIRED_FOR = NONE;
    @NonNls public String REQUIRED_TAGS = "";

    public Options() {
    }

    public Options(String ACCESS_JAVADOC_REQUIRED_FOR, String REQUIRED_TAGS) {
      this.ACCESS_JAVADOC_REQUIRED_FOR = ACCESS_JAVADOC_REQUIRED_FOR;
      this.REQUIRED_TAGS = REQUIRED_TAGS;
    }

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }

  @NonNls public Options TOP_LEVEL_CLASS_OPTIONS = new Options("none", "");
  @NonNls public Options INNER_CLASS_OPTIONS = new Options("none", "");
  @NonNls public Options METHOD_OPTIONS = new Options("none", "@return@param@throws or @exception");
  @NonNls public Options FIELD_OPTIONS = new Options("none", "");
  public boolean IGNORE_DEPRECATED = false;
  public boolean IGNORE_JAVADOC_PERIOD = true;
  public boolean IGNORE_DUPLICATED_THROWS = false;
  public String myAdditionalJavadocTags = "";

  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.javaDoc.JavaDocLocalInspection");

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
      panel.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createTitledBorder(InspectionsBundle.message("inspection.javadoc.required.tags.option.title")),
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

      public void stateChanged(ChangeEvent e) {
        if (myCheckBox.isSelected()) {
          if (!isTagRequired(myOptions,myTagName)) {
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
      panel.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createTitledBorder(InspectionsBundle.message("inspection.scope.for.title")),
                                                         BorderFactory.createEmptyBorder(0, 3, 3, 3)));

      final Hashtable<Integer, JLabel> sliderLabels = new Hashtable<Integer, JLabel>();
      for (int i = 0; i < modifiers.length; i++) {
        sliderLabels.put(i + 1, new JLabel(modifiers[i]));
      }

      final JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, modifiers.length, 1);

      slider.setLabelTable(sliderLabels);
      slider.putClientProperty(UIUtil.JSLIDER_ISFILLED, Boolean.TRUE);
      slider.setPreferredSize(new Dimension(80, 50));
      slider.setPaintLabels(true);
      slider.setSnapToTicks(true);
      slider.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          int value = slider.getValue();
          options.ACCESS_JAVADOC_REQUIRED_FOR = modifiers[value - 1];
          for (Integer key : sliderLabels.keySet()) {
            sliderLabels.get(key).setForeground(key.intValue() <= value ? Color.black : new Color(100, 100, 100));
          }
        }
      });

      Color fore = Color.black;
      for (int i = 0; i < modifiers.length; i++) {
        sliderLabels.get(i + 1).setForeground(fore);

        if (modifiers[i].equals(options.ACCESS_JAVADOC_REQUIRED_FOR)) {
          slider.setValue(i + 1);
          fore = new Color(100, 100, 100);
        }
      }

      panel.add(slider, BorderLayout.WEST);

      return panel;
    }

    public OptionsPanel() {
      super(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0,0,0,0),0,0 );
      gc.weighty = 0;
      add(createAdditionalJavadocTagsPanel(), gc);
      JTabbedPane tabs = new JTabbedPane(SwingConstants.BOTTOM);
      @NonNls String[] tags = new String[]{"@author", "@version", "@since", "@param"};
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
        public void actionPerformed(ActionEvent e) {
          IGNORE_DEPRECATED = checkBox.isSelected();
        }
      });
      gc.gridwidth = 1;
      add(checkBox, gc);
      final JCheckBox periodCheckBox = new JCheckBox(InspectionsBundle.message("inspection.javadoc.option.ignore.period"),
                                                     IGNORE_JAVADOC_PERIOD);
      periodCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          IGNORE_JAVADOC_PERIOD = periodCheckBox.isSelected();
        }
      });
      add(periodCheckBox, gc);

      final JCheckBox ignoreDuplicateThrowsCheckBox = new JCheckBox("Ignore duplicate throws tag",
                                                     IGNORE_DUPLICATED_THROWS);
      ignoreDuplicateThrowsCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          IGNORE_DUPLICATED_THROWS = ignoreDuplicateThrowsCheckBox.isSelected();
        }
      });
      add(ignoreDuplicateThrowsCheckBox, gc);
    }

    public FieldPanel createAdditionalJavadocTagsPanel(){
      FieldPanel additionalTagsPanel = new FieldPanel(InspectionsBundle.message("inspection.javadoc.label.text"), InspectionsBundle.message("inspection.javadoc.dialog.title"), null, null);
      additionalTagsPanel.setPreferredSize(new Dimension(150, additionalTagsPanel.getPreferredSize().height));
      additionalTagsPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
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

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, InspectionManager manager,
                                                    boolean onTheFly) {
    return manager.createProblemDescriptor(element, template, onTheFly, (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, @NotNull LocalQuickFix fix,
                                                    InspectionManager manager, boolean onTheFly) {
    return manager.createProblemDescriptor(element, template, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly);
  }

  private static class AddMissingTagFix implements LocalQuickFix {
    private final String myTag;
    private final String myValue;

    public AddMissingTagFix(@NonNls String tag, String value) {
      myTag = tag;
      myValue = value;
    }
    public AddMissingTagFix(String tag) {
      this(tag, "");
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.javadoc.problem.add.tag", myTag, myValue);
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      try {
        final PsiDocCommentOwner owner = PsiTreeUtil.getParentOfType(descriptor.getEndElement(), PsiDocCommentOwner.class);
        if (owner != null) {
          if (!CodeInsightUtil.preparePsiElementsForWrite(owner)) return;
          final PsiDocComment docComment = owner.getDocComment();
          final PsiDocTag tag = factory.createDocTagFromText("@" + myTag+" "+myValue, docComment);
          if (docComment != null) {
            PsiElement addedTag;
            final PsiElement anchor = getAnchor();
            if (anchor != null) {
              addedTag = docComment.addBefore(tag, anchor);
            }
            else {
              addedTag = docComment.add(tag);
            }
            moveCaretTo(addedTag);
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @Nullable
    protected PsiElement getAnchor() {
      return null;
    }

    private static void moveCaretTo(final PsiElement newCaretPosition) {
      Project project = newCaretPosition.getProject();
      final PsiFile psiFile = newCaretPosition.getContainingFile();
      final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor != null && IJSwingUtilities.hasFocus(editor.getComponent())) {
        final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == psiFile) {
          editor.getCaretModel().moveToOffset(newCaretPosition.getTextRange().getEndOffset());
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }
    }

    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.javadoc.problem.add.tag.family");
    }
  }
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (psiClass instanceof PsiAnonymousClass) return null;
    if (psiClass instanceof JspClass) return null;
    if (psiClass instanceof PsiTypeParameter) return null;
    if (IGNORE_DEPRECATED && psiClass.isDeprecated()) {
      return null;
    }
    PsiDocComment docComment = psiClass.getDocComment();
    final PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
    final PsiElement elementToHighlight = nameIdentifier != null ? nameIdentifier : psiClass;
    if (docComment == null) {
      return isJavaDocRequired(psiClass)
             ? new ProblemDescriptor[]{createDescriptor(elementToHighlight, REQUIRED_JAVADOC_IS_ABSENT, manager, isOnTheFly)}
             : null;
    }

    PsiDocTag[] tags = docComment.getTags();
    @NonNls String[] tagsToCheck = {"author", "version", "since"};
    @NonNls String[] absentDescriptionKeys = {
      "inspection.javadoc.problem.missing.author.description",
      "inspection.javadoc.problem.missing.version.description",
      "inspection.javadoc.problem.missing.since.description"};

    boolean[] isTagRequired = new boolean[tagsToCheck.length];
    boolean[] isTagPresent = new boolean[tagsToCheck.length];

    boolean someTagsAreRequired = false;
    for (int i = 0; i < tagsToCheck.length; i++) {
      final String tag = tagsToCheck[i];
      someTagsAreRequired |= isTagRequired[i] = isTagRequired(psiClass, tag);
    }

    if (someTagsAreRequired) {
      for (PsiDocTag tag : tags) {
        String tagName = tag.getName();
        for (int i = 0; i < tagsToCheck.length; i++) {
          final String tagToCheck = tagsToCheck[i];
          if (tagToCheck.equals(tagName)) {
            isTagPresent[i] = true;
          }
        }
      }
    }

    final ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);

    for (int i = 0; i < tagsToCheck.length; i++) {
      final String tagToCheck = tagsToCheck[i];
      if (isTagRequired[i] && !isTagPresent[i]) {
        problems.add(createMissingTagDescriptor(elementToHighlight, tagToCheck, manager, isOnTheFly));
      }
    }
    ArrayList<ProblemDescriptor> tagProblems = getTagValuesProblems(psiClass, tags, manager, isOnTheFly);
    if (tagProblems != null) {
      problems.addAll(tagProblems);
    }
    checkForPeriodInDoc(docComment, problems, manager, isOnTheFly);
    checkInlineTags(manager, problems, docComment.getDescriptionElements(),
                    JavaPsiFacade.getInstance(docComment.getProject()).getJavadocManager(), isOnTheFly);
    checkForBadCharacters(docComment, problems, manager, isOnTheFly);
    for (PsiDocTag tag : tags) {
      for (int i = 0; i < tagsToCheck.length; i++) {
        final String tagToCheck = tagsToCheck[i];
        if (tagToCheck.equals(tag.getName()) && extractTagDescription(tag).length() == 0) {
          problems.add(createDescriptor(elementToHighlight, InspectionsBundle.message(absentDescriptionKeys[i]), manager, isOnTheFly));
        }
      }
    }

    checkDuplicateTags(tags, problems, manager, isOnTheFly);

    if (isTagRequired(psiClass, "param") && psiClass.hasTypeParameters() && nameIdentifier != null) {
      ArrayList<PsiTypeParameter> absentParameters = null;
      final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
      for (PsiTypeParameter typeParameter : typeParameters) {
        if (!isFound(tags, typeParameter)) {
          if (absentParameters == null) absentParameters = new ArrayList<PsiTypeParameter>(1);
          absentParameters.add(typeParameter);
        }
      }
      if (absentParameters != null) {
        for (PsiTypeParameter psiTypeParameter : absentParameters) {
          problems.add(createMissingParamTagDescriptor(nameIdentifier, psiTypeParameter, manager, isOnTheFly));
        }
      }
    }

    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private static ProblemDescriptor createMissingParamTagDescriptor(final PsiIdentifier nameIdentifier,
                                                                   final PsiTypeParameter psiTypeParameter,
                                                                   final InspectionManager manager, boolean isOnTheFly) {
    String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@param</code>");
    return createDescriptor(nameIdentifier, message, new AddMissingTagFix("param", "<" + psiTypeParameter.getName() + ">"), manager,
                            isOnTheFly);
  }

  @Nullable
  public ProblemDescriptor[] checkField(@NotNull PsiField psiField, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (IGNORE_DEPRECATED && (psiField.isDeprecated() || psiField.getContainingClass().isDeprecated())) {
      return null;
    }

    PsiDocComment docComment = psiField.getDocComment();
    if (docComment == null) {
      return isJavaDocRequired(psiField)
             ? new ProblemDescriptor[]{createDescriptor(psiField.getNameIdentifier(), REQUIRED_JAVADOC_IS_ABSENT, manager, isOnTheFly)}
             : null;
    }

    final ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);
    ArrayList<ProblemDescriptor> tagProblems = getTagValuesProblems(psiField, docComment.getTags(), manager, isOnTheFly);
    if (tagProblems != null) {
      problems.addAll(tagProblems);
    }
    checkInlineTags(manager, problems, docComment.getDescriptionElements(),
                    JavaPsiFacade.getInstance(docComment.getProject()).getJavadocManager(), isOnTheFly);
    checkForPeriodInDoc(docComment, problems, manager, isOnTheFly);
    checkDuplicateTags(docComment.getTags(), problems, manager, isOnTheFly);
    checkForBadCharacters(docComment, problems, manager, isOnTheFly);
    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod psiMethod, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (psiMethod instanceof JspHolderMethod) return null;
    if (IGNORE_DEPRECATED && (psiMethod.isDeprecated() || psiMethod.getContainingClass().isDeprecated())) {
      return null;
    }
    PsiDocComment docComment = psiMethod.getDocComment();
    final PsiMethod[] superMethods = psiMethod.findSuperMethods();
    if (docComment == null) {
      if (isJavaDocRequired(psiMethod)) {
        if (superMethods.length > 0) return null;
        ExtensionPoint<Condition<PsiMember>> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.JAVADOC_LOCAL);
        final Condition<PsiMember>[] addins = point.getExtensions();
        for (Condition<PsiMember> addin : addins) {
          if (addin.value(psiMethod)) return null;
        }
        if (superMethods.length == 0) {
          final PsiIdentifier nameIdentifier = psiMethod.getNameIdentifier();
          return nameIdentifier != null ? new ProblemDescriptor[] { createDescriptor(nameIdentifier, REQUIRED_JAVADOC_IS_ABSENT, manager,
                                                                                     isOnTheFly)} : null;
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }
    }

    final PsiElement[] descriptionElements = docComment.getDescriptionElements();
    for (PsiElement descriptionElement : descriptionElements) {
      if (descriptionElement instanceof PsiInlineDocTag) {
        if ("inheritDoc".equals(((PsiInlineDocTag)descriptionElement).getName())) return null;
      }
    }

    final ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);

    checkInlineTags(manager, problems, descriptionElements,
                    JavaPsiFacade.getInstance(docComment.getProject()).getJavadocManager(), isOnTheFly);

    final PsiDocTag tagByName = docComment.findTagByName("inheritDoc");
    if (tagByName != null) {
      final String tagName = tagByName.getName();
      final JavadocTagInfo tagInfo = JavaPsiFacade.getInstance(tagByName.getProject()).getJavadocManager().getTagInfo(tagName);
      if (tagInfo != null && tagInfo.isValidInContext(psiMethod)){
        return null;
      }
    }

    PsiDocTag[] tags = docComment.getTags();

    boolean isReturnRequired = false;
    boolean isReturnAbsent = true;
    if (superMethods.length == 0 && !psiMethod.isConstructor() && PsiType.VOID != psiMethod.getReturnType() && isTagRequired(psiMethod, "return")) {
      isReturnRequired = true;
      for (PsiDocTag tag : tags) {
        if ("return".equals(tag.getName())) {
          isReturnAbsent = false;
          break;
        }
      }
    }

    ArrayList<PsiParameter> absentParameters = null;
    if (superMethods.length == 0 && isTagRequired(psiMethod, "param") ) {
      PsiParameter[] params = psiMethod.getParameterList().getParameters();
      for (PsiParameter param : params) {
        if (!isFound(tags, param)) {
          if (absentParameters == null) absentParameters = new ArrayList<PsiParameter>(2);
          absentParameters.add(param);
        }
      }
    }



    if (isReturnRequired && isReturnAbsent) {
      final PsiIdentifier psiIdentifier = psiMethod.getNameIdentifier();
      if (psiIdentifier != null) {
        problems.add(createMissingTagDescriptor(psiIdentifier, "return", manager, isOnTheFly));
      }
    }

    if (absentParameters != null) {
      for (PsiParameter psiParameter : absentParameters) {
        final PsiIdentifier nameIdentifier = psiMethod.getNameIdentifier();
        if (nameIdentifier != null) {
          problems.add(createMissingParamTagDescriptor(nameIdentifier, psiParameter, manager, isOnTheFly));
        }
      }
    }

    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName())) {
        final PsiElement[] dataElements = tag.getDataElements();
        final PsiDocTagValue valueElement = tag.getValueElement();
        boolean hasProblemsWithTag = dataElements.length < 2;
        if (!hasProblemsWithTag) {
          final StringBuilder buf = new StringBuilder();
          for (PsiElement element : dataElements) {
            if (element != valueElement){
              buf.append(element.getText());
            }
          }
          hasProblemsWithTag = buf.toString().trim().length() == 0;
        }
        if (hasProblemsWithTag) {
          if (valueElement != null) {
            problems.add(createDescriptor(valueElement,
                                          InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", "<code>@param " + valueElement.getText() + "</code>"),
                                          manager, isOnTheFly));
          }

        }
      }
    }

    if (superMethods.length == 0 && isTagRequired(psiMethod, "@throws") && psiMethod.getThrowsList().getReferencedTypes().length > 0) {
      final Map<PsiClassType, PsiClass> declaredExceptions = new HashMap<PsiClassType, PsiClass>();
      final PsiClassType[] classTypes = psiMethod.getThrowsList().getReferencedTypes();
      for (PsiClassType classType : classTypes) {
        final PsiClass psiClass = classType.resolve();
        if (psiClass != null){
          declaredExceptions.put(classType, psiClass);
        }
      }
      processThrowsTags(tags, declaredExceptions, manager, problems, isOnTheFly);
      if (!declaredExceptions.isEmpty()) {
        for (PsiClassType declaredException : declaredExceptions.keySet()) {
          problems.add(createMissingThrowsTagDescriptor(psiMethod, manager, declaredException, isOnTheFly));
        }
      }
    }

    ArrayList<ProblemDescriptor> tagProblems = getTagValuesProblems(psiMethod, tags, manager, isOnTheFly);
    if (tagProblems != null) {
      problems.addAll(tagProblems);
    }

    checkForPeriodInDoc(docComment, problems, manager, isOnTheFly);
    checkForBadCharacters(docComment, problems, manager, isOnTheFly);
    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName())) {
        if (extractTagDescription(tag).length() == 0) {
          PsiDocTagValue value = tag.getValueElement();
          if (value instanceof PsiDocParamRef) {
            PsiDocParamRef paramRef = (PsiDocParamRef)value;
            PsiParameter[] params = psiMethod.getParameterList().getParameters();
            for (PsiParameter param : params) {
              if (paramRef.getReference().isReferenceTo(param)) {
                problems.add(createDescriptor(value,
                                              InspectionsBundle.message("inspection.javadoc.method.problem.descriptor", "<code>@param</code>", "<code>" + param.getName() + "</code>"),
                                              manager, isOnTheFly));
              }
            }
          }
        }
      }
      else
        if ("return".equals(tag.getName())) {
          if (extractTagDescription(tag).length() == 0) {
            String message = InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", "<code>@return</code>");
            ProblemDescriptor descriptor = manager.createProblemDescriptor(tag.getNameElement(), message, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                           isOnTheFly, true);
            problems.add(descriptor);
          }
        }
    }

    checkDuplicateTags(tags, problems, manager, isOnTheFly);

    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  public static boolean isFound(final PsiDocTag[] tags, final PsiElement param) {
    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value instanceof PsiDocParamRef) {
          PsiDocParamRef paramRef = (PsiDocParamRef)value;
          final PsiReference psiReference = paramRef.getReference();
          if (psiReference != null && psiReference.isReferenceTo(param)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static void processThrowsTags(final PsiDocTag[] tags,
                                        final Map<PsiClassType, PsiClass> declaredExceptions,
                                        final InspectionManager mananger,
                                        @NotNull final ArrayList<ProblemDescriptor> problems, boolean isOnTheFly) {
    for (PsiDocTag tag : tags) {
      if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
        final PsiDocTagValue value = tag.getValueElement();
        if (value == null) continue;
        final PsiElement firstChild = value.getFirstChild();
        if (firstChild == null) continue;
        final PsiElement psiElement = firstChild.getFirstChild();
        if (!(psiElement instanceof PsiJavaCodeReferenceElement)) continue;
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)psiElement;
        final PsiElement element = ref.resolve();
        if (element instanceof PsiClass){
          final PsiClass exceptionClass = (PsiClass)element;
          for (Iterator<PsiClassType> it = declaredExceptions.keySet().iterator(); it.hasNext();) {
            PsiClassType classType = it.next();
            final PsiClass psiClass = declaredExceptions.get(classType);
            if (InheritanceUtil.isInheritorOrSelf(exceptionClass, psiClass, true)) {
              if (extractThrowsTagDescription(tag).length() == 0) {
                problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", "<code>" + tag.getName() + "</code>"), mananger,
                                              isOnTheFly));
              }
              it.remove();
            }
          }
        }
      }
    }
  }

  @Nullable
  private static ProblemDescriptor createMissingThrowsTagDescriptor(final PsiMethod method,
                                                                    final InspectionManager manager,
                                                                    final PsiClassType exceptionClassType, boolean isOnTheFly) {
    @NonNls String tag = "throws";
    String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + tag + "</code> " + exceptionClassType.getCanonicalText());
    final String firstDeclaredException = exceptionClassType.getCanonicalText();
    final PsiIdentifier nameIdentifier = method.getNameIdentifier();
    return nameIdentifier != null ? createDescriptor(nameIdentifier, message,new AddMissingTagFix(tag, firstDeclaredException), manager,
                                                     isOnTheFly) : null;
  }

  private static ProblemDescriptor createMissingTagDescriptor(PsiElement elementToHighlight,
                                                              @NonNls String tag,
                                                              final InspectionManager manager, boolean isOnTheFly) {
    String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + tag + "</code>");
    return createDescriptor(elementToHighlight, message,new AddMissingTagFix(tag), manager, isOnTheFly);
  }
  private static ProblemDescriptor createMissingParamTagDescriptor(PsiElement elementToHighlight,
                                                                   PsiParameter param,
                                                                   final InspectionManager manager, boolean isOnTheFly) {
    String message = InspectionsBundle.message("inspection.javadoc.method.problem.missing.param.tag", "<code>@param</code>", "<code>" + param.getName() + "</code>");
    return createDescriptor(elementToHighlight, message, new AddMissingParamTagFix(param), manager, isOnTheFly);
  }

  private static class AddMissingParamTagFix extends AddMissingTagFix {
    private final PsiParameter myParam;

    public AddMissingParamTagFix(final PsiParameter param) {
      super("param", param.getName());
      myParam = param;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.javadoc.problem.add.param.tag", myParam.getName());
    }

    @Nullable
    protected PsiElement getAnchor() {
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(myParam, PsiMethod.class);
      LOG.assertTrue(psiMethod != null);
      final PsiDocComment docComment = psiMethod.getDocComment();
      LOG.assertTrue(docComment != null);
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

    private static boolean matches(final PsiParameter param, final PsiDocTag tag) {
      return tag.getValueElement().getText().trim().startsWith(param.getName());
    }
  }

  private static String extractTagDescription(PsiDocTag tag) {
    StringBuilder buf = new StringBuilder();
    PsiElement[] children = tag.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken)child;
        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
          buf.append(token.getText());
        }
      }
      else if (child instanceof PsiDocTagValue) {
        buf.append(child.getText());
      } else if (child instanceof PsiInlineDocTag) {
        buf.append(child.getText());
      }
    }

    String s = buf.toString();
    return s.trim();
  }

  private static String extractThrowsTagDescription(PsiDocTag tag) {
    StringBuilder buf = new StringBuilder();
    PsiElement[] children = tag.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken)child;
        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
          buf.append(token.getText());
        }
      }
    }

    return buf.toString().trim();
  }

  private void checkForBadCharacters(PsiDocComment docComment,
                                   final ArrayList<ProblemDescriptor> problems,
                                   final InspectionManager manager, final boolean onTheFly) {
    docComment.accept(new PsiRecursiveElementVisitor(){
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        final ASTNode node = element.getNode();
        if (node != null) {
          if (node.getElementType() == JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
            problems.add(manager.createProblemDescriptor(element, "Illegal character", (LocalQuickFix)null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly));
          }
        }
      }
    });
  }

  private void checkForPeriodInDoc(PsiDocComment docComment,
                                   ArrayList<ProblemDescriptor> problems,
                                   InspectionManager manager, boolean onTheFly) {
    if (IGNORE_JAVADOC_PERIOD) return;
    PsiDocTag[] tags = docComment.getTags();
    int dotIndex = docComment.getText().indexOf('.');
    int tagOffset = 0;
    if (dotIndex >= 0) {      //need to find first valid tag
      final PsiDocCommentOwner owner = PsiTreeUtil.getParentOfType(docComment, PsiDocCommentOwner.class);
      for (PsiDocTag tag : tags) {
        final String tagName = tag.getName();
        final JavadocTagInfo tagInfo = JavaPsiFacade.getInstance(tag.getProject()).getJavadocManager().getTagInfo(tagName);
        if (tagInfo != null && tagInfo.isValidInContext(owner) && !tagInfo.isInline()) {
          tagOffset = tag.getTextOffset();
          break;
        }
      }
    }

    if (dotIndex == -1 || tagOffset > 0 && dotIndex + docComment.getTextOffset() > tagOffset) {
      problems.add(manager.createProblemDescriptor(docComment.getFirstChild(),
                                                   InspectionsBundle.message("inspection.javadoc.problem.descriptor1"),
                                                   null,
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly, false));
    }
  }

  @Nullable
  private ArrayList<ProblemDescriptor> getTagValuesProblems(PsiDocCommentOwner context, PsiDocTag[] tags, InspectionManager inspectionManager,
                                                            boolean isOnTheFly) {
    final ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);
    nextTag:
    for (PsiDocTag tag : tags) {
      final JavadocManager manager = JavaPsiFacade.getInstance(tag.getProject()).getJavadocManager();
      String tagName = tag.getName();
      JavadocTagInfo tagInfo = manager.getTagInfo(tagName);

      if (tagInfo == null || !tagInfo.isValidInContext(context)) {
        if (checkTagInfo(inspectionManager, tagInfo, tag, isOnTheFly, problems)) continue nextTag;
      }

      PsiDocTagValue value = tag.getValueElement();
      final JavadocTagInfo info = manager.getTagInfo(tagName);
      if (info != null && !info.isValidInContext(context)) continue;
      String message = info == null ? null : info.checkTagValue(value);

      final PsiReference reference = value != null ? value.getReference() : null;
      if (message == null && reference != null) {
        PsiElement element = reference.resolve();
        if (element == null) {
          final int textOffset = value.getTextOffset();

          if (textOffset == value.getTextRange().getEndOffset()) {
            problems.add(inspectionManager.createProblemDescriptor(tag, InspectionsBundle.message("inspection.javadoc.problem.name.expected"), null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                   isOnTheFly, true));
          }
        }
      }

      if (message != null) {
        final PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement == null){
          problems.add(inspectionManager.createProblemDescriptor(tag, InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", "<code>" + tag.getName() + "</code>"), null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                 isOnTheFly, true));
        } else {
          problems.add(createDescriptor(valueElement, message, inspectionManager, isOnTheFly));
        }
      }
      checkInlineTags(inspectionManager, problems, tag.getDataElements(), manager, isOnTheFly);
    }

    return problems.isEmpty() ? null : problems;
  }

  private boolean checkTagInfo(InspectionManager inspectionManager, JavadocTagInfo tagInfo, PsiDocTag tag, boolean isOnTheFly, ArrayList<ProblemDescriptor> problems) {
    final String tagName = tag.getName();
    final StringTokenizer tokenizer = new StringTokenizer(myAdditionalJavadocTags, ", ");
    while (tokenizer.hasMoreTokens()) {
      if (Comparing.strEqual(tagName, tokenizer.nextToken())) return true;
    }

    final PsiElement nameElement = tag.getNameElement();
    if (nameElement != null) {
      if (tagInfo == null) {
        problems.add(
          createDescriptor(nameElement, InspectionsBundle.message("inspection.javadoc.problem.wrong.tag", "<code>" + tagName + "</code>"),
                           new AddUnknownTagToCustoms(tag), inspectionManager, isOnTheFly));
      }
      else {
        problems.add(createDescriptor(nameElement, InspectionsBundle.message("inspection.javadoc.problem.disallowed.tag",
                                                                             "<code>" + tagName + "</code>"),
                                      new AddUnknownTagToCustoms(tag), inspectionManager, isOnTheFly));
      }
    }
    return false;
  }

  private void checkInlineTags(final InspectionManager inspectionManager,
                               final ArrayList<ProblemDescriptor> problems,
                               final PsiElement[] dataElements,
                               final JavadocManager manager, boolean isOnTheFly) {
    for (PsiElement dataElement : dataElements) {
      if (dataElement instanceof PsiInlineDocTag) {
        final PsiInlineDocTag inlineDocTag = (PsiInlineDocTag)dataElement;
        final PsiElement nameElement = inlineDocTag.getNameElement();
        if (manager.getTagInfo(inlineDocTag.getName()) == null) {
          checkTagInfo(inspectionManager, null, inlineDocTag, isOnTheFly, problems);
        }
        final PsiDocTagValue value = inlineDocTag.getValueElement();
        if (value != null) {
          final PsiReference reference = value.getReference();
          if (reference != null) {
            final PsiElement ref = reference.resolve();
            if (ref != null){
              if (PsiTreeUtil.getParentOfType(inlineDocTag, PsiDocCommentOwner.class) == PsiTreeUtil.getParentOfType(ref, PsiDocCommentOwner.class, false)) {
                if (nameElement != null) {
                  problems.add(createDescriptor(nameElement, InspectionsBundle.message("inspection.javadoc.problem.pointing.to.itself"), inspectionManager,
                                                isOnTheFly));
                }
              }
            }
          }
        }
      }
    }
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  private boolean isTagRequired(PsiElement context, @NonNls String tag) {
    if (context instanceof PsiClass) {
      if (PsiTreeUtil.getParentOfType(context, PsiClass.class) != null) {
        return isTagRequired(INNER_CLASS_OPTIONS, tag);
      }

      return isTagRequired(TOP_LEVEL_CLASS_OPTIONS, tag);
    }

    if (context instanceof PsiMethod) {
      return isTagRequired(METHOD_OPTIONS, tag);
    }

    if (context instanceof PsiField) {
      return isTagRequired(FIELD_OPTIONS, tag);
    }

    return false;
  }

  private static boolean isTagRequired(Options options, String tag) {
    return options.REQUIRED_TAGS.contains(tag);
  }

  private boolean isJavaDocRequired(PsiModifierListOwner psiElement) {
    final RefJavaUtil refUtil = RefJavaUtil.getInstance();
    int actualAccess = getAccessNumber(refUtil.getAccessModifier(psiElement));
    if (psiElement instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)psiElement;
      if (PsiTreeUtil.getParentOfType(psiClass, PsiClass.class) != null) {
        return actualAccess <= getAccessNumber(INNER_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
      }

      return actualAccess <= getAccessNumber(TOP_LEVEL_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    if (psiElement instanceof PsiMethod) {
      psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      while (psiElement != null) {
        actualAccess = Math.max(actualAccess, getAccessNumber(refUtil.getAccessModifier(psiElement)));
        psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      }

      return actualAccess <= getAccessNumber(METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    if (psiElement instanceof PsiField) {
      psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      while (psiElement != null) {
        actualAccess = Math.max(actualAccess, getAccessNumber(refUtil.getAccessModifier(psiElement)));
        psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      }

      return actualAccess <= getAccessNumber(FIELD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    return false;
  }

  private void checkDuplicateTags(final PsiDocTag[] tags,
                                         ArrayList<ProblemDescriptor> problems,
                                         final InspectionManager manager, boolean isOnTheFly) {
    Set<String> documentedParamNames = null;
    Set<String> documentedExceptions = null;
    Set<String> uniqueTags = null;
    for(PsiDocTag tag: tags) {
      if ("param".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value instanceof PsiDocParamRef) {
          PsiDocParamRef paramRef = (PsiDocParamRef)value;
          final PsiReference reference = paramRef.getReference();
          if (reference != null) {
            final String paramName = reference.getCanonicalText();
            if (documentedParamNames == null) {
              documentedParamNames = new HashSet<String>();
            }
            if (documentedParamNames.contains(paramName)) {
              problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.duplicate.param", paramName), manager,
                                            isOnTheFly));
            }
            documentedParamNames.add(paramName);
          }
        }
      }
      else if (!IGNORE_DUPLICATED_THROWS && ("throws".equals(tag.getName()) || "exception".equals(tag.getName()))) {
        PsiDocTagValue value = tag.getValueElement();
        if (value != null) {
          final PsiElement firstChild = value.getFirstChild();
          if (firstChild != null && firstChild.getFirstChild() instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement) firstChild.getFirstChild();
            if (refElement != null) {
              PsiElement element = refElement.resolve();
              if (element instanceof PsiClass) {
                String fqName = ((PsiClass)element).getQualifiedName();
                if (documentedExceptions == null) {
                  documentedExceptions = new HashSet<String>();
                }
                if (documentedExceptions.contains(fqName)) {
                  problems.add(createDescriptor(tag.getNameElement(),
                                                InspectionsBundle.message("inspection.javadoc.problem.duplicate.throws", fqName),
                                                manager, isOnTheFly));
                }
                documentedExceptions.add(fqName);
              }
            }
          }
        }
      }
      else if (JavaDocLocalInspection.ourUniqueTags.contains(tag.getName())) {
        if (uniqueTags == null) {
          uniqueTags = new HashSet<String>();
        }
        if (uniqueTags.contains(tag.getName())) {
          problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.duplicate.tag", tag.getName()), manager,
                                        isOnTheFly));
        }
        uniqueTags.add(tag.getName());
      }
    }
  }

  private static int getAccessNumber(@NonNls String accessModifier) {
    if (accessModifier.startsWith("none")) return 0;
    if (accessModifier.startsWith("public")) return 1;
    if (accessModifier.startsWith("protected")) return 2;
    if (accessModifier.startsWith("package")) return 3;
    if (accessModifier.startsWith("private")) return 4;

    return 5;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.javadoc.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private class AddUnknownTagToCustoms implements LocalQuickFix {
    PsiDocTag myTag;

    public AddUnknownTagToCustoms(PsiDocTag tag) {
      myTag = tag;
    }

    @NotNull
    public String getName() {
      return QuickFixBundle.message("add.doctag.to.custom.tags", myTag.getName());
    }

    @NotNull
    public String getFamilyName() {
     return QuickFixBundle.message("fix.javadoc.family");
   }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (myTag == null || !myTag.isValid()) return;
      if (myAdditionalJavadocTags.length() > 0) {
        myAdditionalJavadocTags += "," + myTag.getName();
      }
      else {
        myAdditionalJavadocTags = myTag.getName();
      }
      final InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      //correct save settings
      ((ModifiableModel)inspectionProfile).isProperSetting(HighlightDisplayKey.find(SHORT_NAME));
      InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
      //TODO lesya

      /*

      try {
        inspectionProfile.save();
      }
      catch (IOException e) {
        Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
      }

      */
    }
  }
}
