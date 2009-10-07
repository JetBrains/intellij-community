package com.intellij.refactoring.introduceparameterobject;

import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.introduceparameterobject.usageInfo.MergeMethodArguments;
import com.intellij.refactoring.introduceparameterobject.usageInfo.ReplaceParameterAssignmentWithCall;
import com.intellij.refactoring.introduceparameterobject.usageInfo.ReplaceParameterIncrementDecrement;
import com.intellij.refactoring.introduceparameterobject.usageInfo.ReplaceParameterReferenceWithCall;
import com.intellij.refactoring.psi.PropertyUtils;
import com.intellij.refactoring.psi.TypeParametersVisitor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class IntroduceParameterObjectProcessor extends FixableUsagesRefactoringProcessor {
  private static final Logger logger = Logger.getInstance("com.siyeh.rpp.introduceparameterobject.IntroduceParameterObjectProcessor");

  private final PsiMethod method;
  private final String className;
  private final String packageName;
  private final List<String> getterNames;
  private final boolean keepMethodAsDelegate;
  private final boolean myUseExistingClass;
  private final boolean myCreateInnerClass;
  private final List<ParameterTablePanel.VariableData> parameters;
  private final int[] paramsToMerge;
  private final List<PsiTypeParameter> typeParams;
  private final Set<PsiParameter> paramsNeedingSetters = new HashSet<PsiParameter>();
  private final PsiClass existingClass;
  private boolean myExistingClassCompatible = true;

  public IntroduceParameterObjectProcessor(String className,
                                           String packageName,
                                           PsiMethod method,
                                           ParameterTablePanel.VariableData[] parameters,
                                           List<String> getterNames,
                                           boolean keepMethodAsDelegate, final boolean useExistingClass, final boolean createInnerClass) {
    super(method.getProject());
    this.method = method;
    this.className = className;
    this.packageName = packageName;
    this.getterNames = getterNames;
    this.keepMethodAsDelegate = keepMethodAsDelegate;
    myUseExistingClass = useExistingClass;
    myCreateInnerClass = createInnerClass;
    this.parameters = new ArrayList<ParameterTablePanel.VariableData>(Arrays.asList(parameters));
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] methodParams = parameterList.getParameters();
    paramsToMerge = new int[parameters.length];
    for (int p = 0; p < parameters.length; p++) {
      ParameterTablePanel.VariableData parameter = parameters[p];
      for (int i = 0; i < methodParams.length; i++) {
        final PsiParameter methodParam = methodParams[i];
        if (parameter.variable.equals(methodParam)) {
          paramsToMerge[p] = i;
          break;
        }
      }
    }
    final Set<PsiTypeParameter> typeParamSet = new HashSet<PsiTypeParameter>();
    final JavaRecursiveElementWalkingVisitor visitor = new TypeParametersVisitor(typeParamSet);
    for (ParameterTablePanel.VariableData parameter : parameters) {
      parameter.variable.accept(visitor);
    }
    typeParams = new ArrayList<PsiTypeParameter>(typeParamSet);

    final String qualifiedName = StringUtil.getQualifiedName(packageName, className);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    existingClass = JavaPsiFacade.getInstance(myProject).findClass(qualifiedName, scope);

  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usageInfos) {
    return new IntroduceParameterObjectUsageViewDescriptor(method);
  }


  @Override
  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    if (myUseExistingClass) {
      if (existingClass == null) {
        conflicts.putValue(null, RefactorJBundle.message("cannot.perform.the.refactoring") + "Could not find the selected class");
      }
      final String incompatibilityMessage = "Selected class is not compatible with chosen parameters";
      if (!myExistingClassCompatible) {
        conflicts
          .putValue(existingClass, RefactorJBundle.message("cannot.perform.the.refactoring") + incompatibilityMessage);

      }
      if (!paramsNeedingSetters.isEmpty()) {
        conflicts.putValue(existingClass, RefactorJBundle.message("cannot.perform.the.refactoring") + incompatibilityMessage);
      }
    }
    else if (existingClass != null) {
      conflicts.putValue(existingClass,
                    RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("there.already.exists.a.class.with.the.chosen.name"));
    }
    return showConflicts(conflicts);
  }

  @Override
  protected boolean showConflicts(final MultiMap<PsiElement, String> conflicts) {
    if (!conflicts.isEmpty() && ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(StringUtil.join(conflicts.values(), "\n"));
    }
    return super.showConflicts(conflicts);
  }

  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    if (myUseExistingClass && existingClass != null) {
      myExistingClassCompatible = existingClassIsCompatible(existingClass, parameters, getterNames);
      if (!myExistingClassCompatible) return;
    }
    findUsagesForMethod(method, usages);

    final PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY);
    for (PsiMethod siblingMethod : overridingMethods) {
      findUsagesForMethod(siblingMethod, usages);
    }
  }

  private void findUsagesForMethod(PsiMethod overridingMethod, List<FixableUsageInfo> usages) {
    final String fixedParamName = calculateNewParamNameForMethod(overridingMethod);

    usages.add(new MergeMethodArguments(overridingMethod, className, packageName, fixedParamName, paramsToMerge, typeParams, keepMethodAsDelegate, myCreateInnerClass ? method.getContainingClass() : null));

    final ParamUsageVisitor visitor = new ParamUsageVisitor(overridingMethod, paramsToMerge);
    overridingMethod.accept(visitor);
    final Set<PsiReferenceExpression> values = visitor.getParameterUsages();
    for (PsiReferenceExpression paramUsage : values) {
      final PsiParameter parameter = (PsiParameter)paramUsage.resolve();
      assert parameter != null;
      final PsiMethod containingMethod = (PsiMethod)parameter.getDeclarationScope();
      final int index = containingMethod.getParameterList().getParameterIndex(parameter);
      final PsiParameter replacedParameter = method.getParameterList().getParameters()[index];
      @NonNls String getter = null;
      if (getterNames != null) {
        for (int i = 0; i < parameters.size(); i++) {
          ParameterTablePanel.VariableData data = parameters.get(i);
          if (data.variable.equals(parameter)) {
            getter = getterNames.get(i);
            break;
          }
        }
      }
      if (getter == null) {
        getter = PropertyUtil.suggestGetterName(replacedParameter.getName(), replacedParameter.getType());
      }
      @NonNls final String setter = PropertyUtil.suggestSetterName(replacedParameter.getName());
      if (RefactoringUtil.isPlusPlusOrMinusMinus(paramUsage.getParent())) {
        usages.add(new ReplaceParameterIncrementDecrement(paramUsage, fixedParamName, setter, getter));
        paramsNeedingSetters.add(replacedParameter);
      }
      else if (RefactoringUtil.isAssignmentLHS(paramUsage)) {
        usages.add(new ReplaceParameterAssignmentWithCall(paramUsage, fixedParamName, setter, getter));
        paramsNeedingSetters.add(replacedParameter);
      }
      else {
        usages.add(new ReplaceParameterReferenceWithCall(paramUsage, fixedParamName, getter));
      }
    }
  }

  private String calculateNewParamNameForMethod(PsiMethod testMethod) {
    final Project project = testMethod.getProject();
    final CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    final CodeStyleSettings settings = settingsManager.getCurrentSettings();
    final String baseParamName = settings.PARAMETER_NAME_PREFIX.length() == 0 ? StringUtil.decapitalize(className) : className;
    final String newParamName = settings.PARAMETER_NAME_PREFIX + baseParamName + settings.PARAMETER_NAME_SUFFIX;
    if (!isParamNameUsed(newParamName, testMethod)) {
      return newParamName;
    }
    int count = 1;
    while (true) {
      final String testParamName = settings.PARAMETER_NAME_PREFIX + baseParamName + count + settings.PARAMETER_NAME_SUFFIX;
      if (!isParamNameUsed(testParamName, testMethod)) {
        return testParamName;
      }
      count++;
    }
  }

  private boolean isParamNameUsed(String paramName, PsiMethod testMethod) {
    final PsiParameterList testParamList = testMethod.getParameterList();
    final PsiParameter[] testParameters = testParamList.getParameters();
    for (int i = 0; i < testParameters.length; i++) {
      if (!isParamToMerge(i)) {
        if (testParameters[i].getName().equals(paramName)) {
          return true;
        }
      }
    }
    final PsiCodeBlock body = testMethod.getBody();
    if (body == null) {
      return false;
    }
    final NameUsageVisitor visitor = new NameUsageVisitor(paramName);
    body.accept(visitor);
    return visitor.isNameUsed();
  }

  private boolean isParamToMerge(int i) {
    for (int j : paramsToMerge) {
      if (i == j) {
        return true;
      }
    }
    return false;
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    if (buildClass()) {
      super.performRefactoring(usageInfos);
    }
  }

  private boolean buildClass() {
    if (existingClass != null) {
      return true;
    }
    final ParameterObjectBuilder beanClassBuilder = new ParameterObjectBuilder();
    beanClassBuilder.setProject(myProject);
    beanClassBuilder.setTypeArguments(typeParams);
    beanClassBuilder.setClassName(className);
    beanClassBuilder.setPackageName(packageName);
    for (ParameterTablePanel.VariableData parameter : parameters) {
      final boolean setterRequired = paramsNeedingSetters.contains(parameter.variable);
      beanClassBuilder.addField((PsiParameter)parameter.variable,  parameter.name, parameter.type, setterRequired);
    }
    final String classString = beanClassBuilder.buildBeanClass();

    try {
      final PsiJavaFile newFile = (PsiJavaFile)PsiFileFactory.getInstance(method.getProject()).createFileFromText(className + ".java", classString);
      if (myCreateInnerClass) {
        final PsiClass containingClass = method.getContainingClass();
        final PsiClass[] classes = newFile.getClasses();
        assert classes.length > 0 : classString;
        final PsiClass innerClass = (PsiClass)containingClass.add(classes[0]);
        PsiUtil.setModifierProperty(innerClass, PsiModifier.STATIC, true);
        JavaCodeStyleManager.getInstance(newFile.getProject()).shortenClassReferences(innerClass);
      } else {
        final PsiFile containingFile = method.getContainingFile();
        final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
        final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, containingDirectory, true);

        if (directory != null) {

          final CodeStyleManager codeStyleManager = method.getManager().getCodeStyleManager();
          final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(newFile.getProject()).shortenClassReferences(newFile);
          final PsiElement reformattedFile = codeStyleManager.reformat(shortenedFile);
          directory.add(reformattedFile);
        } else {
          return false;
        }
      }
    }
    catch (IncorrectOperationException e) {
      logger.info(e);
      return false;
    }
    return true;
  }

  protected String getCommandName() {
    final PsiClass containingClass = method.getContainingClass();
    return RefactorJBundle.message("introduced.parameter.class.command.name", className, containingClass.getName(), method.getName());
  }


  private static class ParamUsageVisitor extends JavaRecursiveElementWalkingVisitor {
    private final Set<PsiParameter> paramsToMerge = new HashSet<PsiParameter>();
    private final Set<PsiReferenceExpression> parameterUsages = new HashSet<PsiReferenceExpression>(4);

    ParamUsageVisitor(PsiMethod method, int[] paramIndicesToMerge) {
      super();
      final PsiParameterList paramList = method.getParameterList();
      final PsiParameter[] parameters = paramList.getParameters();
      for (int i : paramIndicesToMerge) {
        paramsToMerge.add(parameters[i]);
      }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiParameter)) {
        return;
      }
      final PsiParameter parameter = (PsiParameter)referent;
      if (paramsToMerge.contains(parameter)) {
        parameterUsages.add(expression);
      }
    }

    public Set<PsiReferenceExpression> getParameterUsages() {
      return parameterUsages;
    }
  }

  private static class NameUsageVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean nameUsed = false;
    private final String paramName;

    NameUsageVisitor(String paramName) {
      super();
      this.paramName = paramName;
    }

    public void visitElement(PsiElement element) {
      if (nameUsed) {
        return;
      }
      super.visitElement(element);
    }

    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      final String variableName = variable.getName();
      if (paramName.equals(variableName)) {
        nameUsed = true;
      }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.getQualifier() == null) {
        return;
      }
      final String referenceName = expression.getReferenceName();
      if (paramName.equals(referenceName)) {
        nameUsed = true;
      }
    }

    public boolean isNameUsed() {
      return nameUsed;
    }
  }

  private static boolean existingClassIsCompatible(PsiClass aClass, List<ParameterTablePanel.VariableData> params, @NonNls List<String> getterNames) {
    if (params.size() == 1) {
      final PsiType paramType = params.get(0).type;
      if (TypeConversionUtil.isPrimitiveWrapper(aClass.getQualifiedName())) {
        getterNames.add(paramType.getCanonicalText() + "Value");
        return true;
      }
    }
    final PsiMethod[] constructors = aClass.getConstructors();
    PsiMethod compatibleConstructor = null;
    for (PsiMethod constructor : constructors) {
      if (constructorIsCompatible(constructor, params)) {
        compatibleConstructor = constructor;
        break;
      }
    }
    if (compatibleConstructor == null) {
      return false;
    }
    final PsiParameterList parameterList = compatibleConstructor.getParameterList();
    final PsiParameter[] constructorParams = parameterList.getParameters();
    for (PsiParameter param : constructorParams) {
      final PsiField field = findFieldAssigned(param, compatibleConstructor);
      if (field == null) {
        return false;
      }
      final PsiMethod getter = PropertyUtils.findGetterForField(field);
      if (getter == null) {
        return false;
      }
      getterNames.add(getter.getName());
    }
    //TODO: this fails if there are any setters required
    return true;
  }

  private static boolean constructorIsCompatible(PsiMethod constructor, List<ParameterTablePanel.VariableData> params) {
    if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    final PsiParameterList parameterList = constructor.getParameterList();
    final PsiParameter[] constructorParams = parameterList.getParameters();
    if (constructorParams.length != params.size()) {
      return false;
    }
    for (int i = 0; i < constructorParams.length; i++) {
      if (!TypeConversionUtil.isAssignable(constructorParams[i].getType(), params.get(i).type)) {
        return false;
      }
    }
    return true;
  }

  private static PsiField findFieldAssigned(PsiParameter param, PsiMethod constructor) {
    final ParamAssignmentFinder visitor = new ParamAssignmentFinder(param);
    constructor.accept(visitor);
    return visitor.getFieldAssigned();
  }

  private static class ParamAssignmentFinder extends JavaRecursiveElementWalkingVisitor {

    private final PsiParameter param;

    private PsiField fieldAssigned = null;

    ParamAssignmentFinder(PsiParameter param) {
      this.param = param;
    }

    public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final PsiExpression lhs = assignment.getLExpression();
      final PsiExpression rhs = assignment.getRExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)rhs).resolve();
      if (referent == null || !referent.equals(param)) {
        return;
      }
      final PsiElement assigned = ((PsiReference)lhs).resolve();
      if (assigned == null || !(assigned instanceof PsiField)) {
        return;
      }
      fieldAssigned = (PsiField)assigned;
    }

    public PsiField getFieldAssigned() {
      return fieldAssigned;
    }

  }
}
