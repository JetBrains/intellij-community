/*
 * Copyright (c) 2005 Jet Brains. All Rights Reserved.
 */
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.role.EjbImplMethodRole;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.IdeBorderFactory;
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
import java.util.*;

public class JavaDocLocalInspection extends LocalInspectionTool {
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
    public String ACCESS_JAVADOC_REQUIRED_FOR = JavaDocLocalInspection.NONE;
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

  @NonNls public Options TOP_LEVEL_CLASS_OPTIONS = new Options("public", "");
  @NonNls public Options INNER_CLASS_OPTIONS = new Options("protected", "");
  @NonNls public Options METHOD_OPTIONS = new Options("protected", "@return@param@throws or @exception");
  @NonNls public Options FIELD_OPTIONS = new Options("protected", "");
  public boolean IGNORE_DEPRECATED = false;
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
        box.setSelected(options.REQUIRED_TAGS.indexOf(tags[i]) != -1);
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
          if (myOptions.REQUIRED_TAGS.indexOf(myTagName) == -1) {
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
        sliderLabels.put(new Integer(i + 1), new JLabel(modifiers[i]));
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
        sliderLabels.get(new Integer(i + 1)).setForeground(fore);

        if (modifiers[i].equals(options.ACCESS_JAVADOC_REQUIRED_FOR)) {
          slider.setValue(i + 1);
          fore = new Color(100, 100, 100);
        }
      }

      panel.add(slider, BorderLayout.WEST);

      return panel;
    }

    public OptionsPanel() {
      super(new BorderLayout());

      JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
      @NonNls String[] tags = new String[]{"@author", "@version", "@since"};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title"), createOptionsPanel(new String[]{JavaDocLocalInspection.NONE, JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PACKAGE_LOCAL},
                                                                                                    tags,
                                                                                                    TOP_LEVEL_CLASS_OPTIONS));
      tags = new String[]{"@return", "@param", InspectionsBundle.message("inspection.javadoc.throws.or.exception.option")};
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title1"), createOptionsPanel(new String[]{JavaDocLocalInspection.NONE, JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PROTECTED, JavaDocLocalInspection.PACKAGE_LOCAL, JavaDocLocalInspection.PRIVATE},
                                                                                                     tags,
                                                                                                     METHOD_OPTIONS));
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title2"), createOptionsPanel(new String[]{JavaDocLocalInspection.NONE, JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PROTECTED, JavaDocLocalInspection.PACKAGE_LOCAL, JavaDocLocalInspection.PRIVATE},
                                                                                                     null,
                                                                                                     FIELD_OPTIONS));
      tabs.add(InspectionsBundle.message("inspection.javadoc.option.tab.title3"), createOptionsPanel(new String[]{JavaDocLocalInspection.NONE, JavaDocLocalInspection.PUBLIC, JavaDocLocalInspection.PROTECTED, JavaDocLocalInspection.PACKAGE_LOCAL, JavaDocLocalInspection.PRIVATE},
                                                                                                     null,
                                                                                                     INNER_CLASS_OPTIONS));

      add(tabs, BorderLayout.CENTER);

      final JCheckBox checkBox = new JCheckBox(InspectionsBundle.message("inspection.javadoc.option.ignore.deprecated"),
                                               IGNORE_DEPRECATED);
      checkBox.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          IGNORE_DEPRECATED = checkBox.isSelected();
        }
      });
      add(checkBox, BorderLayout.SOUTH);
      add(createAdditionalJavadocTagsPanel(), BorderLayout.NORTH);
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

  private ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template) {
    return InspectionManager.getInstance(element.getProject()).createProblemDescriptor(element, template, (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  private ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, LocalQuickFix fix) {
    return InspectionManager.getInstance(element.getProject()).createProblemDescriptor(element, template, new LocalQuickFix []{fix}, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
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
             ? new ProblemDescriptor[]{createDescriptor(elementToHighlight, JavaDocLocalInspection.REQUIRED_JAVADOC_IS_ABSENT)}
             : null;
    }

    PsiDocTag[] tags = docComment.getTags();

    boolean isAuthorRequired = isTagRequired(psiClass, "author");
    boolean isVersionRequired = isTagRequired(psiClass, "version");
    boolean isSinceRequired = isTagRequired(psiClass, "since");

    boolean isAuthorAbsent = true;
    boolean isVersionAbsent = true;
    boolean isSinceAbsent = true;

    if (isAuthorRequired || isVersionRequired) {
      for (int i = 0; i < tags.length && (isAuthorAbsent || isVersionAbsent || isSinceAbsent); i++) {
        PsiDocTag tag = tags[i];
        if ("author".equals(tag.getName())) isAuthorAbsent = false;
        if ("version".equals(tag.getName())) isVersionAbsent = false;
        if ("since".equals(tag.getName())) isSinceAbsent = false;
      }
    }

    ArrayList<ProblemDescriptor> problems = null;


    if (isAuthorRequired && isAuthorAbsent) {
      problems = new ArrayList<ProblemDescriptor>();
      problems.add(createDescriptor(elementToHighlight, InspectionsBundle.message("inspection.javadoc.problem.descriptor2", "<code>@author</code>")));
    }

    if (isVersionRequired && isVersionAbsent) {
      if (problems == null) problems = new ArrayList<ProblemDescriptor>();
      problems.add(createDescriptor(elementToHighlight, InspectionsBundle.message("inspection.javadoc.problem.descriptor2", "<code>@version</code>")));
    }

    if (isSinceRequired && isSinceAbsent) {
      if (problems == null) problems = new ArrayList<ProblemDescriptor>();
      problems.add(createDescriptor(elementToHighlight, InspectionsBundle.message("inspection.javadoc.problem.descriptor2", "<code>@since</code>")));
    }

    problems = checkForPeriodInDoc(docComment, docComment, problems);

    for (PsiDocTag tag : tags) {
      if ("author".equals(tag.getName())) {
        if (JavaDocLocalInspection.extractTagDescription(tag).length() == 0) {
          if (problems == null) problems = new ArrayList<ProblemDescriptor>();
          problems.add(createDescriptor(elementToHighlight, InspectionsBundle.message("inspection.javadoc.problem.descriptor5", "<code>@author</code> ")));
        }
      }
      else
        if ("version".equals(tag.getName())) {
          if (JavaDocLocalInspection.extractTagDescription(tag).length() == 0) {
            if (problems == null) problems = new ArrayList<ProblemDescriptor>();
            problems.add(createDescriptor(elementToHighlight, InspectionsBundle.message("inspection.javadoc.problem.descriptor6", "<code>@version</code>")));
          }
        }
        else
          if ("since".equals(tag.getName())) {
            if (JavaDocLocalInspection.extractTagDescription(tag).length() == 0) {
              if (problems == null) problems = new ArrayList<ProblemDescriptor>();
              problems.add(createDescriptor(elementToHighlight, InspectionsBundle.message("inspection.javadoc.problem.descriptor7", "<code>@since</code> ")));
            }
          }
    }

    problems = checkDuplicateTags(tags, problems);

    return problems == null
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
             ? new ProblemDescriptor[]{createDescriptor(psiField.getNameIdentifier(), JavaDocLocalInspection.REQUIRED_JAVADOC_IS_ABSENT)}
             : null;
    }

    ArrayList<ProblemDescriptor> problems = null;
    problems = checkForPeriodInDoc(docComment, docComment, problems);
    problems = checkDuplicateTags(docComment.getTags(), problems);
    return problems == null
           ? null
           : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Nullable
  public ProblemDescriptor[] checkMethod(PsiMethod psiMethod, InspectionManager manager, boolean isOnTheFly) {
    if (IGNORE_DEPRECATED && (psiMethod.isDeprecated() || psiMethod.getContainingClass().isDeprecated())) {
      return null;
    }
    PsiDocComment docComment = psiMethod.getDocComment();
    if (docComment == null) {
      if (isJavaDocRequired(psiMethod)) {
        PsiMethod[] superMethods = psiMethod.findSuperMethods();
        if (superMethods.length > 0) return null;
        if (J2EERolesUtil.getEjbRole(psiMethod) instanceof EjbImplMethodRole) return null;
        return superMethods.length == 0
               ? new ProblemDescriptor[]{createDescriptor(psiMethod.getNameIdentifier(), JavaDocLocalInspection.REQUIRED_JAVADOC_IS_ABSENT)}
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

    if (docComment.findTagByName("inheritDoc") != null) return null;

    PsiDocTag[] tags = docComment.getTags();

    boolean isReturnRequired = false;
    boolean isReturnAbsent = true;
    if (!psiMethod.isConstructor() && PsiType.VOID != psiMethod.getReturnType() && isTagRequired(psiMethod, "return")) {
      isReturnRequired = true;
      for (PsiDocTag tag : tags) {
        if ("return".equals(tag.getName())) {
          isReturnAbsent = false;
          break;
        }
      }
    }

    ArrayList<PsiParameter> absentParameters = null;
    if (isTagRequired(psiMethod, "param")) {
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
          if (absentParameters == null) absentParameters = new ArrayList<PsiParameter>();
          absentParameters.add(param);
        }
      }
    }

    ArrayList<ProblemDescriptor> problems = null;

    if (isReturnRequired && isReturnAbsent) {
      problems = new ArrayList<ProblemDescriptor>();
      problems.add(createDescriptor(psiMethod.getNameIdentifier(), InspectionsBundle.message("inspection.javadoc.problem.descriptor2", "<code>@return</code>")));
    }

    if (absentParameters != null) {
      if (problems == null) problems = new ArrayList<ProblemDescriptor>();
      for (PsiParameter psiParameter : absentParameters) {
        problems.add(createDescriptor(psiMethod.getNameIdentifier(),
                                      InspectionsBundle.message("inspection.javadoc.method.problem.descriptor3", "<code>@param</code>",
                                                                "<code>" + psiParameter.getName() + "</code>")));
      }
    }

    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName()) && tag.getDataElements().length < 2) {
        if (problems == null) problems = new ArrayList<ProblemDescriptor>();
        final PsiDocTagValue valueElement = tag.getValueElement();
        if (valueElement != null) {
          problems.add(createDescriptor(psiMethod.getNameIdentifier(),
                                        InspectionsBundle.message("inspection.javadoc.method.problem.descriptor2", "<code>@param " + valueElement.getText() + "</code>")));
        }
      }
    }

    if (isTagRequired(psiMethod, "@throws") && psiMethod.getThrowsList().getReferencedTypes().length > 0) {
      boolean found = false;
      for (PsiDocTag tag : tags) {
        if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
          found = true;
          break;
        }
      }

      if (!found) {
        if (problems == null) problems = new ArrayList<ProblemDescriptor>();
        problems.add(createDescriptor(psiMethod.getNameIdentifier(), InspectionsBundle.message("inspection.javadoc.problem.descriptor2", "<code>@throws</code>")));
      }
    }

    ArrayList<ProblemDescriptor> tagProblems = getTagValuesProblems(psiMethod, tags);
    if (tagProblems != null) {
      if (problems == null) problems = new ArrayList<ProblemDescriptor>();
      problems.addAll(tagProblems);
    }

    problems = checkForPeriodInDoc(docComment, docComment, problems);

    for (PsiDocTag tag : tags) {
      if ("param".equals(tag.getName())) {
        if (JavaDocLocalInspection.extractTagDescription(tag).length() == 0) {
          PsiDocTagValue value = tag.getValueElement();
          if (value instanceof PsiDocParamRef) {
            PsiDocParamRef paramRef = (PsiDocParamRef)value;
            PsiParameter[] params = psiMethod.getParameterList().getParameters();
            for (PsiParameter param : params) {
              if (paramRef.getReference().isReferenceTo(param)) {
                if (problems == null) problems = new ArrayList<ProblemDescriptor>();
                problems.add(createDescriptor(psiMethod.getNameIdentifier(),
                                              InspectionsBundle.message("inspection.javadoc.method.problem.descriptor", "<code>@param</code>", "<code>" + param.getName() + "</code>")));
              }
            }
          }
        }
      }
      else
        if ("return".equals(tag.getName())) {
          if (extractTagDescription(tag).length() == 0) {
            if (problems == null) problems = new ArrayList<ProblemDescriptor>();
            problems.add(manager.createProblemDescriptor(tag, InspectionsBundle.message("inspection.javadoc.method.problem.descriptor1", "<code>@return</code>"), null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true));
          }
        }
    }

    problems = checkDuplicateTags(tags, problems);

    return problems == null
           ? null
           : problems.toArray(new ProblemDescriptorImpl[problems.size()]);
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

  private ArrayList<ProblemDescriptor> checkForPeriodInDoc(PsiElement psiElement,
                                                           PsiDocComment docComment,
                                                           ArrayList<ProblemDescriptor> problems) {
    PsiDocTag[] tags = docComment.getTags();
    int dotIndex = docComment.getText().indexOf('.');
    int tagOffset = tags.length == 0 ? 0 : tags[0].getTextOffset();

    if (dotIndex == -1 || tagOffset > 0 && dotIndex + docComment.getTextOffset() > tagOffset) {
      if (problems == null) problems = new ArrayList<ProblemDescriptor>();
      problems.add(InspectionManager.getInstance(psiElement.getProject()).createProblemDescriptor(docComment.getFirstChild(),
                                                                                                  InspectionsBundle.message("inspection.javadoc.problem.descriptor1"),
                                                                                                  null,
                                                                                                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                                                  false));
    }
    return problems;
  }

  private ArrayList<ProblemDescriptor> getTagValuesProblems(PsiElement context, PsiDocTag[] tags) {
    ArrayList<ProblemDescriptor> problems = null;

    nextTag:
    for (PsiDocTag tag : tags) {
      final JavadocManager manager = tag.getManager().getJavadocManager();
      String tagName = tag.getName();
      JavadocTagInfo tagInfo = manager.getTagInfo(tagName);

      if (tagInfo == null || !tagInfo.isValidInContext(context)) {
        final StringTokenizer tokenizer = new StringTokenizer(myAdditionalJavadocTags, ", ");
        while (tokenizer.hasMoreTokens()) {
          if (tagName.equals(tokenizer.nextToken())) continue nextTag;
        }

        if (problems == null) problems = new ArrayList<ProblemDescriptor>();

        if (tagInfo == null){
          problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.descriptor10", "<code>" + tagName + "</code>"), new AddUnknownTagToCustoms(tag)));
        } else {
          problems.add(createDescriptor(tag.getNameElement(), InspectionsBundle.message("inspection.javadoc.problem.descriptor11", "<code>" + tagName + "</code>"), new AddUnknownTagToCustoms(tag)));
        }

      }

      PsiDocTagValue value = tag.getValueElement();
      final JavadocTagInfo info = manager.getTagInfo(tagName);
      if (info != null && !info.isValidInContext(context)) continue;
      String message = info == null ? null : info.checkTagValue(value);

      if (message == null && value != null && value.getReference() != null) {
        PsiElement element = value.getReference().resolve();
        if (element == null) {
          final int textOffset = value.getTextOffset();

          if (textOffset == value.getTextRange().getEndOffset()) {
            message = InspectionsBundle.message("inspection.javadoc.problem.descriptor9");
          }
          else {
            message = InspectionsBundle.message("inspection.javadoc.problem.descriptor8", "<code>" + new String(value.getContainingFile().textToCharArray(),
                                                                                                                textOffset,
                                                                                                                value.getTextRange().getEndOffset() - textOffset) + "</code>");
          }
        }
      }

      if (message != null) {
        if (problems == null) problems = new ArrayList<ProblemDescriptor>();
        problems.add(createDescriptor(tag.getValueElement(), message));
      }

      final String[] refMessage = new String[]{null};
      final PsiJavaCodeReferenceElement[] references = new PsiJavaCodeReferenceElement[]{null};
      tag.accept(new PsiElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          PsiElement resolved = reference.resolve();
          if (resolved == null) {
            refMessage[0] = InspectionsBundle.message("inspection.javadoc.problem.descriptor8", "<code>" + reference.getText() + "</code>");
            references[0] = reference;
          }
        }

        public void visitElement(PsiElement element) {
          PsiElement[] children = element.getChildren();
          for (PsiElement child : children) {
            child.accept(this);
          }
        }
      });

      if (refMessage[0] != null) {
        if (problems == null) problems = new ArrayList<ProblemDescriptor>();
        problems.add(createDescriptor(references[0], refMessage[0]));
      }
    }

    return problems;
  }

  private boolean isTagRequired(PsiElement context, @NonNls String tag) {
    if (context instanceof PsiClass) {
      if (PsiTreeUtil.getParentOfType(context, PsiClass.class) != null) {
        return JavaDocLocalInspection.isTagRequired(INNER_CLASS_OPTIONS, tag);
      }

      return JavaDocLocalInspection.isTagRequired(TOP_LEVEL_CLASS_OPTIONS, tag);
    }

    if (context instanceof PsiMethod) {
      return JavaDocLocalInspection.isTagRequired(METHOD_OPTIONS, tag);
    }

    if (context instanceof PsiField) {
      return JavaDocLocalInspection.isTagRequired(FIELD_OPTIONS, tag);
    }

    return false;
  }

  private static boolean isTagRequired(JavaDocLocalInspection.Options options, String tag) {
    return options.REQUIRED_TAGS.indexOf(tag) != -1;
  }

  private boolean isJavaDocRequired(PsiModifierListOwner psiElement) {
    int actualAccess = JavaDocLocalInspection.getAccessNumber(RefUtil.getAccessModifier(psiElement));
    if (psiElement instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)psiElement;
      if (PsiTreeUtil.getParentOfType(psiClass, PsiClass.class) != null) {
        return actualAccess <= JavaDocLocalInspection.getAccessNumber(INNER_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
      }

      return actualAccess <= JavaDocLocalInspection.getAccessNumber(TOP_LEVEL_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    if (psiElement instanceof PsiMethod) {
      psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      while (psiElement != null) {
        actualAccess = Math.max(actualAccess, JavaDocLocalInspection.getAccessNumber(RefUtil.getAccessModifier(psiElement)));
        psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      }

      return actualAccess <= JavaDocLocalInspection.getAccessNumber(METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    if (psiElement instanceof PsiField) {
      psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      while (psiElement != null) {
        actualAccess = Math.max(actualAccess, JavaDocLocalInspection.getAccessNumber(RefUtil.getAccessModifier(psiElement)));
        psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
      }

      return actualAccess <= JavaDocLocalInspection.getAccessNumber(FIELD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
    }

    return false;
  }

  private ArrayList<ProblemDescriptor> checkDuplicateTags(final PsiDocTag[] tags,
                                                          ArrayList<ProblemDescriptor> problems) {
    Set<String> documentedParamNames = null;
    Set<String> documentedExceptions = null;
    Set<String> uniqueTags = null;
    for(PsiDocTag tag: tags) {
      if ("param".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value instanceof PsiDocParamRef) {
          PsiDocParamRef paramRef = (PsiDocParamRef)value;
          final String paramName = paramRef.getReference().getCanonicalText();
          if (documentedParamNames == null) {
            documentedParamNames = new HashSet<String>();
          }
          if (documentedParamNames.contains(paramName)) {
            if (problems == null) {
              problems = new ArrayList<ProblemDescriptor>();
            }
            problems.add(createDescriptor(tag, InspectionsBundle.message("inspection.javadoc.problem.duplicate.param", paramName)));
          }
          documentedParamNames.add(paramName);
        }
      }
      else if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
        PsiDocTagValue value = tag.getValueElement();
        if (value.getFirstChild() != null && value.getFirstChild().getFirstChild() != null &&
            value.getFirstChild().getFirstChild() instanceof PsiJavaCodeReferenceElement) {
          PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement) value.getFirstChild().getFirstChild();
          PsiElement element = refElement.resolve();
          if (element != null && element instanceof PsiClass) {
            String fqName = ((PsiClass) element).getQualifiedName();
            if (documentedExceptions == null) {
              documentedExceptions = new HashSet<String>();
            }
            if (documentedExceptions.contains(fqName)) {
              if (problems == null) {
                problems = new ArrayList<ProblemDescriptor>();
              }
              problems.add(createDescriptor(tag, InspectionsBundle.message("inspection.javadoc.problem.duplicate.throws", fqName)));
            }
            documentedExceptions.add(fqName);
          }
        }
      }
      else if (JavaDocLocalInspection.ourUniqueTags.contains(tag.getName())) {
        if (uniqueTags == null) {
          uniqueTags = new HashSet<String>();
        }
        if (uniqueTags.contains(tag.getName())) {
          if (problems == null) {
            problems = new ArrayList<ProblemDescriptor>();
          }
          problems.add(createDescriptor(tag, InspectionsBundle.message("inspection.javadoc.problem.duplicate.tag", tag.getName())));
        }
        uniqueTags.add(tag.getName());
      }
    }
    return problems;
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

  public boolean isEnabledByDefault() {
    return true;
  }

  private class AddUnknownTagToCustoms implements LocalQuickFix {
    PsiDocTag myTag;

    public AddUnknownTagToCustoms(PsiDocTag tag) {
      myTag = tag;
    }

    public String getName() {
      return QuickFixBundle.message("add.doctag.to.custom.tags", myTag.getName());
    }

    public String getFamilyName() {
     return QuickFixBundle.message("fix.javadoc.family");
   }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      if (myAdditionalJavadocTags.length() > 0) {
        myAdditionalJavadocTags += "," + myTag.getName();
      }
      else {
        myAdditionalJavadocTags = myTag.getName();
      }
      //todo commit changes to profile
      DaemonCodeAnalyzer.getInstance(project).restart();
    }
  }
}
