/**
 * @author cdr
 */
package com.intellij.codeInspection.i18n;

import com.intellij.ExtensionPoints;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.impl.ConcatenationToMessageFormatAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.ui.AddDeleteListPanel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class I18nInspection extends BaseLocalInspectionTool {
  public boolean ignoreForAssertStatements = true;
  public boolean ignoreForExceptionConstructors = true;
  @NonNls public String ignoreForSpecifiedExceptionConstructors = "";
  public boolean ignoreForJUnitAsserts = true;
  public boolean ignoreForClassReferences = true;
  public boolean ignoreForPropertyKeyReferences = true;
  public boolean ignoreForNonAlpha = true;
  public boolean ignoreAssignedToConstants = false;
  public boolean ignoreToString = false;
  @NonNls public String nonNlsCommentPattern = "NON-NLS";

  private static final LocalQuickFix I18N_QUICK_FIX = new I18nizeQuickFix();
  private static final I18nizeConcatenationQuickFix I18N_CONCATENATION_QUICK_FIX = new I18nizeConcatenationQuickFix();

  @Nullable private Pattern myCachedNonNlsPattern;
  @NonNls private static final String TO_STRING = "toString";

  public I18nInspection() {
    cacheNonNlsCommentPattern();
  }

  public void readSettings(Element node) throws InvalidDataException {
    super.readSettings(node);
    cacheNonNlsCommentPattern();
  }

  public String getGroupDisplayName() {
    return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
  }

  public String getDisplayName() {
    return CodeInsightBundle.message("inspection.i18n.display.name");
  }

  public String getShortName() {
    return "HardCodedStringLiteral";
  }

  public JComponent createOptionsPanel() {
    final GridBagLayout layout = new GridBagLayout();
    final JPanel panel = new JPanel(layout);
    final JCheckBox assertStatementsCheckbox = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.assert"), ignoreForAssertStatements);
    assertStatementsCheckbox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForAssertStatements = assertStatementsCheckbox.isSelected();
      }
    });
    final JCheckBox exceptionConstructorCheck =
      new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.for.exception.constructor.arguments"),
                    ignoreForExceptionConstructors);
    exceptionConstructorCheck.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForExceptionConstructors = exceptionConstructorCheck.isSelected();
      }
    });

    final JTextField specifiedExceptions = new JTextField(ignoreForSpecifiedExceptionConstructors);
    specifiedExceptions.getDocument().addDocumentListener(new DocumentAdapter(){
      protected void textChanged(DocumentEvent e) {
        ignoreForSpecifiedExceptionConstructors = specifiedExceptions.getText();
      }
    });

    final JCheckBox junitAssertCheckbox = new JCheckBox(
      CodeInsightBundle.message("inspection.i18n.option.ignore.for.junit.assert.arguments"), ignoreForJUnitAsserts);
    junitAssertCheckbox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForJUnitAsserts = junitAssertCheckbox.isSelected();
      }
    });
    final JCheckBox classRef = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.qualified.class.names"), ignoreForClassReferences);
    classRef.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForClassReferences = classRef.isSelected();
      }
    });
    final JCheckBox propertyRef = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.property.keys"), ignoreForPropertyKeyReferences);
    propertyRef.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForPropertyKeyReferences = propertyRef.isSelected();
      }
    });
    final JCheckBox nonAlpha = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.nonalphanumerics"), ignoreForNonAlpha);
    nonAlpha.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreForNonAlpha = nonAlpha.isSelected();
      }
    });
    final JCheckBox assignedToConstants = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.assigned.to.constants"), ignoreAssignedToConstants);
    assignedToConstants.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreAssignedToConstants = assignedToConstants.isSelected();
      }
    });
    final JCheckBox chkToString = new JCheckBox(CodeInsightBundle.message("inspection.i18n.option.ignore.tostring"), ignoreToString);
    chkToString.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ignoreToString = chkToString.isSelected();
      }
    });

    final GridBagConstraints gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.HORIZONTAL;

    gc.gridx = GridBagConstraints.REMAINDER;
    gc.gridy = 0;
    gc.weightx = 1;
    gc.weighty = 0;
    panel.add(assertStatementsCheckbox, gc);

    gc.gridy ++;
    panel.add(junitAssertCheckbox, gc);

    gc.gridy ++;
    panel.add(exceptionConstructorCheck, gc);

    gc.gridy ++;
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    panel.add(new FieldPanel(specifiedExceptions,
                             CodeInsightBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"),
                             CodeInsightBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"),
                             openProjects.length == 0 ? null :
                             new ActionListener() {
                               public void actionPerformed(ActionEvent e) {
                                 createIgnoreExceptionsConfigurationDialog(openProjects[0], specifiedExceptions).show();
                               }
                             },
                             null), gc);

    gc.gridy ++;
    panel.add(classRef, gc);

    gc.gridy ++;
    panel.add(propertyRef, gc);

    gc.gridy++;
    panel.add(assignedToConstants, gc);

    gc.gridy++;
    panel.add(chkToString, gc);

    gc.gridy ++;
    panel.add(nonAlpha, gc);

    gc.gridy ++;
    final JTextField text = new JTextField(nonNlsCommentPattern);
    final FieldPanel nonNlsCommentPatternComponent =
      new FieldPanel(text, CodeInsightBundle.message("inspection.i18n.option.ignore.comment.pattern"),
                     CodeInsightBundle.message("inspection.i18n.option.ignore.comment.title"), null, new Runnable() {
        public void run() {
          nonNlsCommentPattern = text.getText();
          cacheNonNlsCommentPattern();
        }
      });
    panel.add(nonNlsCommentPatternComponent, gc);

    gc.gridy ++;
    gc.weighty = 1;
    panel.add(new JPanel(), gc);
    return panel;
  }

  @SuppressWarnings({"NonStaticInitializer"})
  private DialogWrapper createIgnoreExceptionsConfigurationDialog(final Project project, final JTextField specifiedExceptions) {
    return new DialogWrapper(true) {
      private AddDeleteListPanel myPanel;
      {
        setTitle(CodeInsightBundle.message(
          "inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"));
        init();
      }

      protected JComponent createCenterPanel() {
        final String[] ignored = ignoreForSpecifiedExceptionConstructors.split(",");
        final List<String> initialList = new ArrayList<String>();
        if (ignored != null){
          for (String e : ignored) {
            if (e.length() > 0) initialList.add(e);
          }
        }
        myPanel = new AddDeleteListPanel(null,
                                         initialList) {
          protected Object findItemToAdd() {
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).
              createInheritanceClassChooser(
                CodeInsightBundle.message("inspection.i18n.option.ignore.for.specified.exception.constructor.arguments"),
                scope,
                PsiManager.getInstance(project).findClass("java.lang.Throwable", scope),
                true,
                true,
                null);
            chooser.showDialog();
            PsiClass selectedClass = chooser.getSelectedClass();
            return selectedClass != null ? selectedClass.getQualifiedName() : null;
          }
        };
        return myPanel;
      }

      protected void doOKAction() {
        StringBuffer buf = new StringBuffer();
        final Object[] exceptions = myPanel.getListItems();
        for (Object exception : exceptions) {
          buf.append(",").append(exception);
        }
        specifiedExceptions.setText(buf.length() > 0 ? buf.substring(1) : buf.toString());
        super.doOKAction();
      }
    };
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(method.getContainingClass())) {
      return null;
    }
    final PsiCodeBlock body = method.getBody();
    if (body != null) {
      return checkElement(body, manager);
    }
    return null;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(aClass)) {
      return null;
    }
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] descriptors = checkElement(initializer, manager);
      if (descriptors != null) {
        result.addAll(Arrays.asList(descriptors));
      }
    }

    return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkField(PsiField field, InspectionManager manager, boolean isOnTheFly) {
    if (isClassNonNls(field.getContainingClass())) {
      return null;
    }
    if (field.getModifierList() != null) {
      for(PsiAnnotation annotation: field.getModifierList().getAnnotations()) {
        if (AnnotationUtil.NON_NLS.equals(annotation.getQualifiedName())) {
          return null;
        }
      }
    }
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) return checkElement(initializer, manager);

    if (field instanceof PsiEnumConstant) {
      return checkElement(((PsiEnumConstant)field).getArgumentList(), manager);
    }
    return null;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    final Object[] fileCheckingInspections = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.I18N_INSPECTION_TOOL).getExtensions();
    for(Object obj: fileCheckingInspections) {
      FileCheckingInspection inspection = (FileCheckingInspection) obj;
      ProblemDescriptor[] descriptors = inspection.checkFile(file, manager, isOnTheFly);
      if (descriptors != null) {
        return descriptors;
      }
    }

    return null;
  }

  private ProblemDescriptor[] checkElement(final PsiElement element, InspectionManager manager) {
    StringI18nVisitor visitor = new StringI18nVisitor(manager);
    element.accept(visitor);
    List<ProblemDescriptor> problems = visitor.getProblems();
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  public static LocalQuickFix createIntroduceConstantFix(final PsiExpression... expressions) {
    //noinspection unchecked
    final SmartPsiElementPointer<PsiExpression>[] pointers = new SmartPsiElementPointer[expressions.length];
    for(int i=0; i<expressions.length; i++) {
      pointers [i] = SmartPointerManager.getInstance(expressions [i].getProject()).createSmartPsiElementPointer(expressions [i]);
    }
    return new LocalQuickFix() {
      @NotNull
      public String getName() {
        return IntroduceConstantHandler.REFACTORING_NAME;
      }

      public void applyFix(final Project project, ProblemDescriptor descriptor) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            List<PsiExpression> exprList = new ArrayList<PsiExpression>();
            for(SmartPsiElementPointer<PsiExpression> ptr: pointers) {
              PsiExpression expr = ptr.getElement();
              if (expr != null && expr.isValid()) {
                exprList.add(expr);
              }
            }
            new IntroduceConstantHandler().invoke(project, exprList.toArray(new PsiExpression[exprList.size()]));
          }
        });
      }

      @NotNull
      public String getFamilyName() {
        return getName();
      }
    };
  }

  private class StringI18nVisitor extends PsiRecursiveElementVisitor {
    private List<ProblemDescriptor> myProblems = new ArrayList<ProblemDescriptor>();
    private InspectionManager myManager;

    public StringI18nVisitor(final InspectionManager manager) {
      myManager = manager;
    }

    public void visitAnonymousClass(PsiAnonymousClass aClass) {
      final PsiExpressionList argumentList = aClass.getArgumentList();
      if (argumentList != null) {
        argumentList.accept(this);
      }
    }

    public void visitClass(PsiClass aClass) {}

    public void visitField(PsiField field) {}

    public void visitMethod(PsiMethod method) {}

    public void visitLiteralExpression(PsiLiteralExpression expression) {
      Object value = expression.getValue();
      if (!(value instanceof String)) return;
      String stringValue = (String)value;
      if (stringValue.trim().length() == 0) {
        return;
      }

      Set<PsiModifierListOwner> nonNlsTargets = new HashSet<PsiModifierListOwner>();
      if (canBeI18ned(expression, stringValue, nonNlsTargets)) {
        final String description = CodeInsightBundle.message("inspection.i18n.message.general.with.value",
                                                             JDOMUtil.escapeText(stringValue));

        List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
        if (ConcatenationToMessageFormatAction.getEnclosingLiteralConcatenation(expression) != null) {
          fixes.add(I18N_CONCATENATION_QUICK_FIX);
        }
        fixes.add(I18N_QUICK_FIX);

        if (!isNotConstantFieldInitializer(expression)) {
          fixes.add(createIntroduceConstantFix(expression));
        }

        final PsiManager manager = expression.getManager();
        if (PsiUtil.getLanguageLevel(expression).hasEnumKeywordAndAutoboxing() &&
            manager.findClass(AnnotationUtil.NON_NLS, expression.getResolveScope()) != null) {
          for(PsiModifierListOwner element: nonNlsTargets) {
            if (manager.isInProject(element)) {
              fixes.add(new AnnotateQuickFix(element, AnnotationUtil.NON_NLS));
            }
          }
        }

        final ProblemDescriptor problem = myManager
          .createProblemDescriptor(expression,
                                   description,
                                   fixes.toArray(new LocalQuickFix[fixes.size()]), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        myProblems.add(problem);
      }
    }

    private boolean isNotConstantFieldInitializer(final PsiExpression expression) {
      PsiField parentField = expression.getParent() instanceof PsiField ? (PsiField) expression.getParent() : null;
      return parentField != null && expression == parentField.getInitializer() &&
             parentField.getModifierList().hasModifierProperty(PsiModifier.FINAL) &&
             parentField.getModifierList().hasModifierProperty(PsiModifier.STATIC);
    }


    public void visitAnnotation(PsiAnnotation annotation) {
      //prevent from @SuppressWarnings
      if (!"java.lang.SuppressWarnings".equals(annotation.getQualifiedName())){
        super.visitAnnotation(annotation);
      }
    }

    public List<ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }

  private boolean canBeI18ned(PsiLiteralExpression expression, String value, final Set<PsiModifierListOwner> nonNlsTargets) {
    if (ignoreForNonAlpha && !StringUtil.containsAlphaCharacters(value)) {
      return false;
    }

    PsiField parentField = PsiTreeUtil.getParentOfType(expression, PsiField.class);
    if (parentField != null) {
      nonNlsTargets.add(parentField);
    }

    if (I18nUtil.isPassedToAnnotatedParam(expression, AnnotationUtil.NON_NLS, new HashMap<String, Object>(), nonNlsTargets)) {
      return false;
    }

    if (isInNonNlsCall(expression, nonNlsTargets)) {
      return false;
    }

    if (isInNonNlsEquals(expression, nonNlsTargets)) {
      return false;
    }

    if (isPassedToNonNlsVariable(expression, nonNlsTargets)) {
      return false;
    }

    if (I18nUtil.mustBePropertyKey(expression, new HashMap<String, Object>())) {
      return false;
    }

    if (isReturnedFromNonNlsMethod(expression, nonNlsTargets)) {
      return false;
    }
    if (ignoreForAssertStatements && isArgOfAssertStatement(expression)) {
      return false;
    }
    if (ignoreForExceptionConstructors && isArgOfExceptionConstructor(expression)) {
      return false;
    }
    if (!ignoreForExceptionConstructors && isArgOfSpecifiedExceptionConstructor(expression, ignoreForSpecifiedExceptionConstructors.split(","))) {
      return false;
    }
    if (ignoreForJUnitAsserts && isArgOfJUnitAssertion(expression)) {
      return false;
    }
    if (ignoreForClassReferences && isClassRef(expression, value)) {
      return false;
    }
    if (ignoreForPropertyKeyReferences && I18nUtil.isPropertyRef(expression, value, null)) {
      return false;
    }
    if (ignoreToString && isToString(expression)) {
      return false;
    }

    Pattern pattern = myCachedNonNlsPattern;
    if (pattern != null) {
      Project project = expression.getProject();
      Document document = PsiDocumentManager.getInstance(project).getDocument(expression.getContainingFile());
      int line = document.getLineNumber(expression.getTextRange().getStartOffset());
      String lineText = document.getText().substring(document.getLineStartOffset(line), document.getLineEndOffset(line));
      if (pattern.matcher(lineText).matches()) {
        return false;
      }
    }

    return true;
  }

  public void cacheNonNlsCommentPattern() {
    if (nonNlsCommentPattern.trim().length() == 0) {
      myCachedNonNlsPattern = null;
    }
    else {
      @NonNls String regex = ".*//\\s*" + nonNlsCommentPattern + ".*";
      myCachedNonNlsPattern = Pattern.compile(regex);
    }
  }

  private static boolean isClassRef(final PsiLiteralExpression expression, String value) {
    if (StringUtil.startsWithChar(value,'#')) {
      value = value.substring(1); // A favor for JetBrains team to catch common Logger usage practice.
    }

    return expression.getManager().findClass(value, GlobalSearchScope.allScope(expression.getProject())) != null;
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  private static boolean isClassNonNls(final PsiClass clazz) {
    final PsiDirectory directory = clazz.getContainingFile().getContainingDirectory();
    if (directory != null && isPackageNonNls(directory.getPackage())) {
      return true;
    }

    /*HashSet<PsiClass> classes = new HashSet<PsiClass>();
    return isClassNonNls(clazz, classes);*/
    return false;
  }

  public static boolean isPackageNonNls(final PsiPackage psiPackage) {
    if (psiPackage == null) {
      return false;
    }
    final PsiModifierList pkgModifierList = psiPackage.getAnnotationList();
    if (pkgModifierList != null && pkgModifierList.findAnnotation(AnnotationUtil.NON_NLS) != null) {
      return true;
    }
    return isPackageNonNls(psiPackage.getParentPackage());
  }

  private boolean isPassedToNonNlsVariable(final PsiLiteralExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    PsiExpression toplevel = I18nUtil.getToplevelExpression(expression);
    PsiVariable var = null;
    if (toplevel instanceof PsiAssignmentExpression) {
      PsiExpression lExpression = ((PsiAssignmentExpression)toplevel).getLExpression();
      while (lExpression instanceof PsiArrayAccessExpression) {
        lExpression = ((PsiArrayAccessExpression)lExpression).getArrayExpression();
      }
      if (lExpression instanceof PsiReferenceExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
        if (resolved instanceof PsiVariable) var = (PsiVariable)resolved;
      }
    }

    if (var == null) {
      PsiElement parent = toplevel.getParent();
      if (parent instanceof PsiVariable && toplevel.equals(((PsiVariable)parent).getInitializer())) {
        var = (PsiVariable)parent;
      }
    }

    if (var != null) {
      if (annotatedAsNonNls(var)) {
        return true;
      }
      if (ignoreAssignedToConstants &&
          var.getModifierList().hasModifierProperty(PsiModifier.STATIC) &&
          var.getModifierList().hasModifierProperty(PsiModifier.FINAL)) {
        return true;
      }
      nonNlsTargets.add(var);
    }
    return false;
  }

  private static boolean annotatedAsNonNls(final PsiModifierListOwner parent) {
    if (parent instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)parent;
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)declarationScope;
        final int index = method.getParameterList().getParameterIndex(parameter);
        return I18nUtil.isMethodParameterAnnotatedWith(method, index, new HashSet<PsiMethod>(), AnnotationUtil.NON_NLS,
                                                       new HashMap<String, Object>(), null);
      }
    }
    final PsiModifierList modifierList = parent.getModifierList();
    return modifierList != null && modifierList.findAnnotation(AnnotationUtil.NON_NLS) != null;
  }

  private static boolean isInNonNlsEquals(PsiExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    if (!(expression.getParent().getParent() instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call = (PsiMethodCallExpression)expression.getParent().getParent();
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != expression) {
      return false;
    }
    if (!"equals".equals(methodExpression.getReferenceName())) {
      return false;
    }
    final PsiElement resolved = methodExpression.resolve();
    if (!(resolved instanceof PsiMethod)) {
      return false;
    }
    PsiType objectType = PsiType.getJavaLangObject(resolved.getManager(), resolved.getResolveScope());
    MethodSignature equalsSignature = MethodSignatureUtil.createMethodSignature("equals",
                                                                                new PsiType[]{objectType},
                                                                                PsiTypeParameter.EMPTY_ARRAY,
                                                                                PsiSubstitutor.EMPTY);
    if (!equalsSignature.equals(((PsiMethod)resolved).getSignature(PsiSubstitutor.EMPTY))) {
      return false;
    }
    final PsiExpression[] expressions = call.getArgumentList().getExpressions();
    if (expressions.length != 1) {
      return false;
    }
    final PsiExpression arg = expressions[0];
    PsiReferenceExpression ref = null;
    if (arg instanceof PsiReferenceExpression) {
      ref = (PsiReferenceExpression)arg;
    }
    else if (arg instanceof PsiMethodCallExpression) ref = ((PsiMethodCallExpression)arg).getMethodExpression();
    if (ref != null) {
      final PsiElement resolvedEntity = ref.resolve();
      if (resolvedEntity instanceof PsiModifierListOwner) {
        PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)resolvedEntity;
        if (annotatedAsNonNls(modifierListOwner)) {
          return true;
        }
        nonNlsTargets.add(modifierListOwner);
      }
    }
    return false;
  }

  private static boolean isInNonNlsCall(PsiExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    expression = I18nUtil.getToplevelExpression(expression);
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpressionList) {
      final PsiElement grParent = parent.getParent();
      if (grParent instanceof PsiMethodCallExpression) {
        final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)grParent).getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier instanceof PsiReferenceExpression) {
          final PsiElement resolved = ((PsiReferenceExpression)qualifier).resolve();
          if (resolved instanceof PsiModifierListOwner) {
            final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)resolved;
            if (annotatedAsNonNls(modifierListOwner)) {
              return true;
            }
            nonNlsTargets.add(modifierListOwner);
            return false;
          }
        }
      }
      else if (grParent instanceof PsiNewExpression) {
        final PsiElement parentOfNew = grParent.getParent();
        if (parentOfNew instanceof PsiLocalVariable) {
          final PsiLocalVariable newVariable = (PsiLocalVariable)parentOfNew;
          if (annotatedAsNonNls(newVariable)) {
            return true;
          }
          nonNlsTargets.add(newVariable);
          return false;
        }
        else if (parentOfNew instanceof PsiAssignmentExpression) {
          final PsiExpression lExpression = ((PsiAssignmentExpression)parentOfNew).getLExpression();
          if (lExpression instanceof PsiReferenceExpression) {
            final PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
            if (resolved instanceof PsiModifierListOwner) {
              final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)resolved;
              if (annotatedAsNonNls(modifierListOwner)) {
                return true;
              }
              nonNlsTargets.add(modifierListOwner);
              return false;
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isReturnedFromNonNlsMethod(final PsiLiteralExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    final PsiElement returnStmt = PsiTreeUtil.getParentOfType(expression,
                                                              PsiReturnStatement.class, PsiMethodCallExpression.class);
    if (returnStmt == null || !(returnStmt instanceof PsiReturnStatement)) {
      return false;
    }
    final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (method == null) return false;

    if (isMethodAnnotated(method, new HashSet<PsiMethod>(), AnnotationUtil.NON_NLS)) {
      return true;
    }
    nonNlsTargets.add(method);
    return false;
  }

  private static boolean isMethodAnnotated(final PsiMethod method, Collection<PsiMethod> processed, final String annFqn) {
    if (processed.contains(method)) return false;
    processed.add(method);

    if (method.getModifierList().findAnnotation(annFqn) != null) return true;


    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      if (isMethodAnnotated(superMethod, processed, annFqn)) return true;
    }

    return false;
  }

  private static boolean isToString(final PsiLiteralExpression expression) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (method == null) return false;
    final PsiType returnType = method.getReturnType();
    if (TO_STRING.equals(method.getName()) && method.getParameterList().getParameters().length == 0 &&
        returnType != null && "java.lang.String".equals(returnType.getCanonicalText())) {
      return true;
    }
    return false;
  }

  private static boolean isArgOfJUnitAssertion(PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    if (!(grandparent instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call = (PsiMethodCallExpression)grandparent;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final @NonNls String methodName = methodExpression.getReferenceName();
    if (methodName == null) {
      return false;
    }

    if (!methodName.startsWith("assert")) {
      return false;
    }
    final PsiMethod method = call.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final PsiManager manager = expression.getManager();
    final Project project = manager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass junitAssert = manager.findClass("junit.framework.Assert", scope);
    return junitAssert != null && !containingClass.isInheritor(junitAssert, true);
  }

  private static boolean isArgOfExceptionConstructor(PsiExpression expression) {
    final PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, PsiClass.class);
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    if (!(grandparent instanceof PsiNewExpression)) {
      return false;
    }
    final PsiJavaCodeReferenceElement reference =
      ((PsiNewExpression)grandparent).getClassReference();
    if (reference == null) {
      return false;
    }
    final PsiElement referent = reference.resolve();
    if (!(referent instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)referent;
    final PsiManager manager = expression.getManager();
    final Project project = manager.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass throwable = manager.findClass("java.lang.Throwable", scope);
    return aClass.isInheritor(throwable, true);
  }

  private static boolean isArgOfSpecifiedExceptionConstructor(PsiExpression expression, String[] specifiedExceptions) {
    if (specifiedExceptions.length == 0) return false;

    final PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, PsiClass.class);
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    if (!(grandparent instanceof PsiNewExpression)) {
      return false;
    }
    final PsiJavaCodeReferenceElement reference =
      ((PsiNewExpression)grandparent).getClassReference();
    if (reference == null) {
      return false;
    }
    final PsiElement referent = reference.resolve();
    if (!(referent instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)referent;

    for (String specifiedException : specifiedExceptions) {
      if (specifiedException.equals(aClass.getQualifiedName())) return true;

    }

    return false;
  }

  private static boolean isArgOfAssertStatement(PsiExpression expression) {
    return PsiTreeUtil.getParentOfType(expression, PsiAssertStatement.class, PsiClass.class) instanceof PsiAssertStatement;
  }



}
