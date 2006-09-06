/*
 * Copyright (c) 2005 Jet Brains. All Rights Reserved.
 */
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.javaee.ejb.EjbHelper;
import com.intellij.javaee.ejb.role.EjbImplMethodRole;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
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
    public String ACCESS_JAVADOC_REQUIRED_FOR = NONE;
    public String REQUIRED_TAGS = "";

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
  public String myAdditionalJavadocTags = "";

  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.javaDoc.JavaDocLocalInspection");

  private class OptionsPanel extends JPanel {
    private JPanel createOptionsPanel(String[] modifiers, String[] tags, JavaDocLocalInspection.Options options) {
      JPanel pane = new JPanel(new GridLayout(1, tags == null ? 1 : 2));

      pane.add(createScopePanel(modifiers, options));
      if (tags != null) {
        pane.add(createTagsPanel(tags, options));
      }

      pane.validate();

      return pane;
    }

    private JPanel createTagsPanel(String[] tags, JavaDocLocalInspection.Options options) {
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
        box.addChangeListener(new JavaDocLocalInspection.OptionsPanel.MyChangeListener(box, options, tags[i]));
      }

      return panel;
    }

    private class MyChangeListener implements ChangeListener {
      private final JCheckBox myCheckBox;
      private final JavaDocLocalInspection.Options myOptions;
      private final String myTagName;

      public MyChangeListener(JCheckBox checkBox, JavaDocLocalInspection.Options options, String tagName) {
        myCheckBox = checkBox;
        myOptions = options;
        myTagName = tagName;
      }

      public void stateChanged(ChangeEvent e) {
        if (myCheckBox.isSelected()) {
          if (!isTagRequired(myOptions,myTagName)) {
            myOptions.REQUIRED_TAGS = myOptions.REQUIRED_TAGS.concat(myTagName);
          }
        }
        else {
          myOptions.REQUIRED_TAGS = myOptions.REQUIRED_TAGS.replaceAll(myTagName, "");
        }
      }
    }

    private JPanel createScopePanel(final String[] modifiers, final JavaDocLocalInspection.Options options) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createTitledBorder(InspectionsBundle.message("inspection.scope.for.title")),
                                                         BorderFactory.createEmptyBorder(0, 3, 3, 3)));

      final Hashtable<Integer, JLabel> sliderLabels = new Hashtable<Integer, JLabel>();
      for (int i = 0; i < modifiers.length; i++) {
        sliderLabels.put(i + 1, new JLabel(modifiers[i]));
      }

      final JSlider slider = new JSlider(JSlider.VERTICAL, 1, modifiers.length, 1);

      slider.setLabelTable(sliderLabels);
      slider.putClientProperty(UIUtil.JSLIDER_ISFILLED, Boolean.TRUE);
      slider.setPreferredSize(new Dimension(80, 50));
      slider.setPaintLabels(true);
      slider.setSnapToTicks(true);
      slider.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          int value = slider.getValue();
          options.ACCESS_JAVADOC_REQUIRED_FOR = modifiers[value - 1];
          for (Enumeration<Integer> enumeration = sliderLabels.keys(); enumeration.hasMoreElements();) {
            Integer key = enumeration.nextElement();
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
      JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
      @NonNls String[] tags = new String[]{"@author", "@version", "@since"};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title"), createOptionsPanel(new String[]{JavaDocLocalInspection.NONE, JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PACKAGE_LOCAL},
                                                                                                    tags,
                                                                                                    TOP_LEVEL_CLASS_OPTIONS));
      tags = new String[]{"@return", "@param", InspectionsBundle.message("inspection.javadoc.throws.or.exception.option")};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.method"), createOptionsPanel(new String[]{JavaDocLocalInspection.NONE, JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PROTECTED, JavaDocLocalInspection.PACKAGE_LOCAL, JavaDocLocalInspection.PRIVATE},
                                                                                                           tags,
                                                                                                           METHOD_OPTIONS));
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.field"), createOptionsPanel(new String[]{JavaDocLocalInspection.NONE, JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PROTECTED, JavaDocLocalInspection.PACKAGE_LOCAL, JavaDocLocalInspection.PRIVATE},
                                                                                                          null,
                                                                                                          FIELD_OPTIONS));
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title.inner.class"), createOptionsPanel(new String[]{JavaDocLocalInspection.NONE, JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PROTECTED, JavaDocLocalInspection.PACKAGE_LOCAL, JavaDocLocalInspection.PRIVATE},
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

  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, InspectionManager manager) {
    return manager.createProblemDescriptor(element, template, (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, @NotNull LocalQuickFix fix, InspectionManager manager) {
    return manager.createProblemDescriptor(element, template, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
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

    public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
      PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();
      try {
        PsiDocCommentOwner owner = PsiTreeUtil.getParentOfType(descriptor.getEndElement(), PsiDocCommentOwner.class);
        if (owner != null) {
          PsiDocComment docComment = owner.getDocComment();
          PsiDocTag tag = factory.createDocTagFromText("@" + myTag+" "+myValue, docComment);
          if (docComment != null) {
            PsiElement addedTag = docComment.add(tag);
            moveCaretTo(addedTag);
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
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
  public ProblemDescriptor[] checkClass(PsiClass psiClass, InspectionManager manager, boolean isOnTheFly) {
    if (psiClass instanceof PsiAnonymousClass) return null;
    if (IGNORE_DEPRECATED && psiClass.isDeprecated()) {
      return null;
    }
    PsiDocComment docComment = psiClass.getDocComment();
    final PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
    final PsiElement elementToHighlight = nameIdentifier != null ? nameIdentifier : psiClass;
    if (docComment == null) {
      return isJavaDocRequired(psiClass)
             ? new ProblemDescriptor[]{createDescriptor(elementToHighlight, JavaDocLocalInspection.REQUIRED_JAVADOC_IS_ABSENT, manager)}
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
        problems.add(createMissingTagDescriptor(elementToHighlight, tagToCheck, manager));
      }
    }
    checkForPeriodInDoc(docComment, problems, manager);

    for (PsiDocTag tag : tags) {
      for (int i = 0; i < tagsToCheck.length; i++) {
        final String tagToCheck = tagsToCheck[i];
        if (tagToCheck.equals(tag.getName()) && extractTagDescription(tag).length() == 0) {
          problems.add(createDescriptor(elementToHighlight, InspectionsBundle.message(absentDescriptionKeys[i]), manager));
        }
      }
    }

    checkDuplicateTags(tags, problems, manager);

    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptorImpl[problems.size()]);
  }

  @Nullable
  public ProblemDescriptor[] checkField(PsiField psiField, InspectionManager manager, boolean isOnTheFly) {
    if (IGNORE_DEPRECATED && (psiField.isDeprecated() || psiField.getContainingClass().isDeprecated())) {
      return null;
    }

    PsiDocComment docComment = psiField.getDocComment();
    if (docComment == null) {
      return isJavaDocRequired(psiField)
             ? new ProblemDescriptor[]{createDescriptor(psiField.getNameIdentifier(), JavaDocLocalInspection.REQUIRED_JAVADOC_IS_ABSENT, manager)}
             : null;
    }

    final ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);
    checkForPeriodInDoc(docComment, problems, manager);
    checkDuplicateTags(docComment.getTags(), problems, manager);
    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Nullable
  public ProblemDescriptor[] checkMethod(PsiMethod psiMethod, InspectionManager manager, boolean isOnTheFly) {
    if (IGNORE_DEPRECATED && (psiMethod.isDeprecated() || psiMethod.getContainingClass().isDeprecated())) {
      return null;
    }
    PsiDocComment docComment = psiMethod.getDocComment();
    final PsiMethod[] superMethods = psiMethod.findSuperMethods();
    if (docComment == null) {
      if (isJavaDocRequired(psiMethod)) {
        if (superMethods.length > 0) return null;
        if (EjbHelper.getEjbHelper().getEjbRole(psiMethod) instanceof EjbImplMethodRole) return null;
        return superMethods.length == 0
               ? new ProblemDescriptor[]{createDescriptor(psiMethod.getNameIdentifier(), JavaDocLocalInspection.REQUIRED_JAVADOC_IS_ABSENT, manager)}
               : null;
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

    final PsiDocTag tagByName = docComment.findTagByName("inheritDoc");
    if (tagByName != null) {
      final String tagName = tagByName.getName();
      final JavadocTagInfo tagInfo = tagByName.getManager().getJavadocManager().getTagInfo(tagName);
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
        boolean found = false;
        for (PsiDocTag tag : tags) {
          if ("param".equals(tag.getName())) {
            PsiDocTagValue value = tag.getValueElement();
            if (value instanceof PsiDocParamRef) {
              PsiDocParamRef paramRef = (PsiDocParamRef)value;
              if (paramRef.getReference().isReferenceTo(param)) {
                found = true;
                break;
              }
            }
          }
        }

        if (!found) {
          if (absentParameters == null) absentParameters = new ArrayList<PsiParameter>(2);
          absentParameters.add(param);
        }
      }
    }



    if (isReturnRequired && isReturnAbsent) {
      ProblemDescriptor descriptor = createMissingTagDescriptor(psiMethod.getNameIdentifier(), "return", manager);
      problems.add(descriptor);
    }

    if (absentParameters != null) {
      for (PsiParameter psiParameter : absentParameters) {
        ProblemDescriptor descriptor = createMissingParamTagDescriptor(psiMethod.getNameIdentifier(), psiParameter.getName(), manager);
        problems.add(descriptor);
      }
    }

    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName())) {
        final PsiElement[] dataElements = tag.getDataElements();
        final PsiDocTagValue valueElement = tag.getValueElement();
        boolean hasProblemsWithTag = dataElements.length < 2;
        if (!hasProblemsWithTag) {
          final StringBuffer buf = new StringBuffer();
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
                                          manager));
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
      processThrowsTags(psiMethod, tags, declaredExceptions, manager, problems);
      if (!declaredExceptions.isEmpty()) {
        for (PsiClassType declaredException : declaredExceptions.keySet()) {
          problems.add(createMissingThrowsTagDescriptor(psiMethod, manager, declaredException, true));
        }
      }
    }

    ArrayList<ProblemDescriptor> tagProblems = getTagValuesProblems(psiMethod, tags, manager);
    if (tagProblems != null) {
      problems.addAll(tagProblems);
    }

    checkForPeriodInDoc(docComment, problems, manager);

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
                                              manager));
              }
            }
          }
        }
      }
      else
        if ("return".equals(tag.getName())) {
          if (extractTagDescription(tag).length() == 0) {
            String message = InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", "<code>@return</code>");
            ProblemDescriptor descriptor = manager.createProblemDescriptor(tag, message, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true);
            problems.add(descriptor);
          }
        }
    }

    checkDuplicateTags(tags, problems, manager);

    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptorImpl[problems.size()]);
  }

  private static void processThrowsTags(final PsiMethod method,
                                        final PsiDocTag[] tags,
                                        final Map<PsiClassType, PsiClass> declaredExceptions,
                                        final InspectionManager mananger,
                                        @NotNull final ArrayList<ProblemDescriptor> problems) {
    for (PsiDocTag tag : tags) {
      if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
        final PsiDocTagValue value = tag.getValueElement();
        if (value == null) continue;
        final PsiElement firstChild = value.getFirstChild();
        if (firstChild == null) continue;
        final PsiElement psiElement = firstChild.getFirstChild();
        if (!(psiElement instanceof PsiJavaCodeReferenceElement)) continue;
        final PsiJavaCodeReferenceElement ref = ((PsiJavaCodeReferenceElement)psiElement);
        final PsiElement element = ref.resolve();
        if (element instanceof PsiClass){
          final PsiClass exceptionClass = (PsiClass)element;
          for (Iterator<PsiClassType> it = declaredExceptions.keySet().iterator(); it.hasNext();) {
            PsiClassType classType = it.next();
            final PsiClass psiClass = declaredExceptions.get(classType);
            if (InheritanceUtil.isInheritorOrSelf(exceptionClass, psiClass, true)) {
              if (extractThrowsTagDescription(tag).length() == 0) {
                problems.add(createMissingThrowsTagDescriptor(method, mananger, classType, false));
              }
              it.remove();
            }
          }
        }
      }
    }
  }

  private static ProblemDescriptor createMissingThrowsTagDescriptor(final PsiMethod method,
                                                                    final InspectionManager manager,
                                                                    final PsiClassType exceptionClassType,
                                                                    final boolean quickfixNeeded) {
    @NonNls String tag = "throws";
    String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + tag + "</code> " + exceptionClassType.getCanonicalText());
    final String firstDeclaredException = exceptionClassType.getCanonicalText();
    final PsiIdentifier nameIdentifier = method.getNameIdentifier();
    LOG.assertTrue(nameIdentifier != null);
    if (quickfixNeeded) {
      return createDescriptor(nameIdentifier, message,new AddMissingTagFix(tag, firstDeclaredException), manager);
    } else {
      return createDescriptor(nameIdentifier, message, manager);
    }
  }

  private static ProblemDescriptor createMissingTagDescriptor(PsiElement elementToHighlight,
                                                              @NonNls String tag,
                                                              final InspectionManager manager) {
    String message = InspectionsBundle.message("inspection.javadoc.problem.missing.tag", "<code>@" + tag + "</code>");
    return createDescriptor(elementToHighlight, message,new AddMissingTagFix(tag), manager);
  }
  private static ProblemDescriptor createMissingParamTagDescriptor(PsiElement elementToHighlight,
                                                                   String param,
                                                                   final InspectionManager manager) {
    String message = InspectionsBundle.message("inspection.javadoc.method.problem.missing.param.tag", "<code>@param</code>", "<code>" + param + "</code>");
    return createDescriptor(elementToHighlight, message, new AddMissingParamTagFix(param), manager);
  }

  private static class AddMissingParamTagFix extends AddMissingTagFix {
    private final String myParamName;

    public AddMissingParamTagFix(final String paramName) {
      super("param", paramName);
      myParamName = paramName;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.javadoc.problem.add.param.tag", myParamName);
    }
  }

  private static String extractTagDescription(PsiDocTag tag) {
    StringBuffer buf = new StringBuffer();
    PsiElement[] children = tag.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken)child;
        if (token.getTokenType() == PsiDocToken.DOC_COMMENT_DATA) {
          buf.append(token.getText());
        }
      }
      else if (child instanceof PsiDocTagValue) {
        buf.append(child.getText());
      }
    }

    String s = buf.toString();
    return s.trim();
  }

  private static String extractThrowsTagDescription(PsiDocTag tag) {
    StringBuffer buf = new StringBuffer();
    PsiElement[] children = tag.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken)child;
        if (token.getTokenType() == PsiDocToken.DOC_COMMENT_DATA) {
          buf.append(token.getText());
        }
      }
    }

    return buf.toString().trim();
  }

  private void checkForPeriodInDoc(PsiDocComment docComment,
                                   ArrayList<ProblemDescriptor> problems,
                                   InspectionManager manager) {
    if (IGNORE_JAVADOC_PERIOD) return;
    PsiDocTag[] tags = docComment.getTags();
    int dotIndex = docComment.getText().indexOf('.');
    int tagOffset = tags.length == 0 ? 0 : tags[0].getTextOffset();

    if (dotIndex == -1 || tagOffset > 0 && dotIndex + docComment.getTextOffset() > tagOffset) {
      problems.add(manager.createProblemDescriptor(docComment.getFirstChild(),
                                                   InspectionsBundle.message("inspection.javadoc.problem.descriptor1"),
                                                   null,
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                   false));
    }
  }

  @Nullable
  private ArrayList<ProblemDescriptor> getTagValuesProblems(PsiDocCommentOwner context, PsiDocTag[] tags, InspectionManager inspectionManager) {
    final ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(2);
    nextTag:
    for (PsiDocTag tag : tags) {
      final JavadocManager manager = tag.getManager().getJavadocManager();
      String tagName = tag.getName();
      JavadocTagInfo tagInfo = manager.getTagInfo(tagName);

      if (tagInfo == null || !tagInfo.isValidInContext(context)) {
        final StringTokenizer tokenizer = new StringTokenizer(myAdditionalJavadocTags, ", ");
        while (tokenizer.hasMoreTokens()) {
          if (Comparing.strEqual(tagName, tokenizer.nextToken())) continue nextTag;
        }

        if (tagInfo == null){
          problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.wrong.tag", "<code>" + tagName + "</code>"), new AddUnknownTagToCustoms(tag), inspectionManager));
        } else {
          problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.disallowed.tag", "<code>" + tagName + "</code>"), new AddUnknownTagToCustoms(tag), inspectionManager));
        }

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
            problems.add(inspectionManager.createProblemDescriptor(tag, InspectionsBundle.message("inspection.javadoc.problem.name.expected"), null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true));
          }
        }
      }

      if (message != null) {
        final PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement == null){
          problems.add(inspectionManager.createProblemDescriptor(tag, InspectionsBundle.message("inspection.javadoc.method.problem.missing.tag.description", "<code>" + tag.getName() + "</code>"), null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true));
        } else {
          problems.add(createDescriptor(valueElement, message, inspectionManager));
        }
      }
    }

    return problems.isEmpty() ? null : problems;
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
    final RefUtil refUtil = RefUtil.getInstance();
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
        actualAccess = Math.max(actualAccess, JavaDocLocalInspection.getAccessNumber(refUtil.getAccessModifier(psiElement)));
        psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      }

      return actualAccess <= getAccessNumber(FIELD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    return false;
  }

  private static void checkDuplicateTags(final PsiDocTag[] tags,
                                         ArrayList<ProblemDescriptor> problems,
                                         final InspectionManager manager) {
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
              problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.duplicate.param", paramName), manager));
            }
            documentedParamNames.add(paramName);
          }
        }
      }
      else if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
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
                                                manager));
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
          problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.duplicate.tag", tag.getName()), manager));
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

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.javadoc.display.name");
  }

  public String getGroupDisplayName() {
    return "";
  }

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

    public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
      if (myTag == null || !myTag.isValid()) return;
      if (myAdditionalJavadocTags.length() > 0) {
        myAdditionalJavadocTags += "," + myTag.getName();
      }
      else {
        myAdditionalJavadocTags = myTag.getName();
      }
      final InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(project).getInspectionProfile(myTag);
      //correct save settings
      ((InspectionProfileImpl)inspectionProfile).isProperSetting(HighlightDisplayKey.find(SHORT_NAME));
      inspectionProfile.save();
    }
  }
}
