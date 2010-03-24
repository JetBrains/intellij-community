/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.introduceparameterobject.usageInfo.*;
import com.intellij.refactoring.psi.PropertyUtils;
import com.intellij.refactoring.psi.TypeParametersVisitor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntroduceParameterObjectProcessor extends FixableUsagesRefactoringProcessor {
  private static final Logger logger = Logger.getInstance("com.siyeh.rpp.introduceparameterobject.IntroduceParameterObjectProcessor");

  private final PsiMethod method;
  private final String className;
  private final String packageName;
  private final boolean keepMethodAsDelegate;
  private final boolean myUseExistingClass;
  private final boolean myCreateInnerClass;
  private final String myNewVisibility;
  private final boolean myGenerateAccessors;
  private final List<ParameterChunk> parameters;
  private final int[] paramsToMerge;
  private final List<PsiTypeParameter> typeParams;
  private final Set<PsiParameter> paramsNeedingSetters = new HashSet<PsiParameter>();
  private final Set<PsiParameter> paramsNeedingGetters = new HashSet<PsiParameter>();
  private final PsiClass existingClass;
  private PsiMethod myExistingClassCompatibleConstructor;

  public IntroduceParameterObjectProcessor(String className,
                                           String packageName,
                                           PsiMethod method,
                                           ParameterTablePanel.VariableData[] parameters, boolean keepMethodAsDelegate, final boolean useExistingClass,
                                           final boolean createInnerClass,
                                           String newVisibility,
                                           boolean generateAccessors) {
    super(method.getProject());
    this.method = method;
    this.className = className;
    this.packageName = packageName;
    this.keepMethodAsDelegate = keepMethodAsDelegate;
    myUseExistingClass = useExistingClass;
    myCreateInnerClass = createInnerClass;
    myNewVisibility = newVisibility;
    myGenerateAccessors = generateAccessors;
    this.parameters = new ArrayList<ParameterChunk>();
    for (ParameterTablePanel.VariableData parameter : parameters) {
      this.parameters.add(new ParameterChunk(parameter));
    }
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
      if (myExistingClassCompatibleConstructor == null) {
        conflicts.putValue(existingClass, RefactorJBundle.message("cannot.perform.the.refactoring") + "Selected class has no compatible constructors");
      }
    }
    else if (existingClass != null) {
      conflicts.putValue(existingClass,
                    RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("there.already.exists.a.class.with.the.chosen.name"));
    }
    for (UsageInfo usageInfo : refUsages.get()) {
      if (usageInfo instanceof FixableUsageInfo) {
        final String conflictMessage = ((FixableUsageInfo)usageInfo).getConflictMessage();
        if (conflictMessage != null) {
          conflicts.putValue(usageInfo.getElement(), conflictMessage);
        }
      }
    }
    return showConflicts(conflicts);
  }

  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    if (myUseExistingClass && existingClass != null) {
      myExistingClassCompatibleConstructor = existingClassIsCompatible(existingClass, parameters);
    }
    findUsagesForMethod(method, usages);

    if (myUseExistingClass && existingClass != null && !(paramsNeedingGetters.isEmpty() && paramsNeedingSetters.isEmpty())) {
      usages.add(new AppendAccessorsUsageInfo(existingClass, myGenerateAccessors, paramsNeedingGetters, paramsNeedingSetters, parameters));
    }

    final PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY);
    for (PsiMethod siblingMethod : overridingMethods) {
      findUsagesForMethod(siblingMethod, usages);
    }

    if (myNewVisibility != null) {
      usages.add(new BeanClassVisibilityUsageInfo(existingClass, usages.toArray(new UsageInfo[usages.size()]), myNewVisibility, myExistingClassCompatibleConstructor));
    }
  }

  private void findUsagesForMethod(PsiMethod overridingMethod, List<FixableUsageInfo> usages) {
    final PsiCodeBlock body = overridingMethod.getBody();
    final String baseParameterName = StringUtil.decapitalize(className);
    final String fixedParamName =
      body != null
      ? JavaCodeStyleManager.getInstance(myProject).suggestUniqueVariableName(baseParameterName, body.getLBrace(), true)
      : JavaCodeStyleManager.getInstance(myProject).propertyNameToVariableName(baseParameterName, VariableKind.PARAMETER);

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
      final ParameterChunk parameterChunk = ParameterChunk.getChunkByParameter(parameter, parameters);

      @NonNls String getter = parameterChunk != null ? parameterChunk.getter : null;
      if (getter == null) {
        getter = PropertyUtil.suggestGetterName(replacedParameter.getName(), replacedParameter.getType());
        paramsNeedingGetters.add(replacedParameter);
      }
      @NonNls String setter = parameterChunk != null ? parameterChunk.setter : null;
      if (setter == null) {
        setter = PropertyUtil.suggestSetterName(replacedParameter.getName());
      }
      if (RefactoringUtil.isPlusPlusOrMinusMinus(paramUsage.getParent())) {
        usages.add(new ReplaceParameterIncrementDecrement(paramUsage, fixedParamName, setter, getter));
        if (parameterChunk == null || parameterChunk.setter == null) {
          paramsNeedingSetters.add(replacedParameter);
        }
      }
      else if (RefactoringUtil.isAssignmentLHS(paramUsage)) {
        usages.add(new ReplaceParameterAssignmentWithCall(paramUsage, fixedParamName, setter, getter));
        if (parameterChunk == null || parameterChunk.setter == null) {
          paramsNeedingSetters.add(replacedParameter);
        }
      }
      else {
        usages.add(new ReplaceParameterReferenceWithCall(paramUsage, fixedParamName, getter));
      }
    }
  }

  protected void performRefactoring(UsageInfo[] usageInfos) {
    final PsiClass psiClass = buildClass();
    if (psiClass != null) {
      fixJavadocForConstructor(psiClass);
      super.performRefactoring(usageInfos);
      if (!myUseExistingClass) {
        for (PsiReference reference : ReferencesSearch.search(method)) {
          final PsiElement place = reference.getElement();
          VisibilityUtil.escalateVisibility(psiClass, place);
          for (PsiMethod constructor : psiClass.getConstructors()) {
            VisibilityUtil.escalateVisibility(constructor, place);
          }
        }
      }
    }
  }

  private PsiClass buildClass() {
    if (existingClass != null) {
      return existingClass;
    }
    final ParameterObjectBuilder beanClassBuilder = new ParameterObjectBuilder();
    beanClassBuilder.setVisibility(myCreateInnerClass ? PsiModifier.PRIVATE : PsiModifier.PUBLIC);
    beanClassBuilder.setProject(myProject);
    beanClassBuilder.setTypeArguments(typeParams);
    beanClassBuilder.setClassName(className);
    beanClassBuilder.setPackageName(packageName);
    for (ParameterChunk parameterChunk : parameters) {
      final ParameterTablePanel.VariableData parameter = parameterChunk.parameter;
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
        return (PsiClass)JavaCodeStyleManager.getInstance(newFile.getProject()).shortenClassReferences(innerClass);
      } else {
        final PsiFile containingFile = method.getContainingFile();
        final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
        final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, containingDirectory, true);

        if (directory != null) {

          final CodeStyleManager codeStyleManager = method.getManager().getCodeStyleManager();
          final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(newFile.getProject()).shortenClassReferences(newFile);
          final PsiElement reformattedFile = codeStyleManager.reformat(shortenedFile);
          return ((PsiJavaFile)directory.add(reformattedFile)).getClasses()[0];
        }
      }
    }
    catch (IncorrectOperationException e) {
      logger.info(e);
    }
    return null;
  }

  private void fixJavadocForConstructor(PsiClass psiClass) {
    final PsiDocComment docComment = method.getDocComment();
    if (docComment != null) {
      final List<PsiDocTag> mergedTags = new ArrayList<PsiDocTag>();
      final PsiDocTag[] paramTags = docComment.findTagsByName("param");
      for (PsiDocTag paramTag : paramTags) {
        final PsiElement[] dataElements = paramTag.getDataElements();
        if (dataElements.length > 0) {
          mergedTags.add((PsiDocTag)paramTag.copy());
        }
      }

      PsiMethod compatibleParamObjectConstructor = null;
      if (myExistingClassCompatibleConstructor != null && myExistingClassCompatibleConstructor.getDocComment() == null) {
        compatibleParamObjectConstructor = myExistingClassCompatibleConstructor;
      } else if (!myUseExistingClass){
        compatibleParamObjectConstructor = psiClass.getConstructors()[0];
      }

      if (compatibleParamObjectConstructor != null) {
        PsiDocComment psiDocComment =
          JavaPsiFacade.getElementFactory(myProject).createDocCommentFromText("/**\n*/", compatibleParamObjectConstructor);
        psiDocComment = (PsiDocComment)compatibleParamObjectConstructor.addBefore(psiDocComment, compatibleParamObjectConstructor.getFirstChild());

        for (PsiDocTag tag : mergedTags) {
          psiDocComment.add(tag);
        }
      }
    }
  }

  protected String getCommandName() {
    final PsiClass containingClass = method.getContainingClass();
    return RefactorJBundle.message("introduced.parameter.class.command.name", className, containingClass.getName(), method.getName());
  }


  private static class ParamUsageVisitor extends JavaRecursiveElementVisitor {
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

  @Nullable
  private static PsiMethod existingClassIsCompatible(PsiClass aClass, List<ParameterChunk> params) {
    if (params.size() == 1) {
      final ParameterChunk parameterChunk = params.get(0);
      final PsiType paramType = parameterChunk.parameter.type;
      if (TypeConversionUtil.isPrimitiveWrapper(aClass.getQualifiedName())) {
        parameterChunk.setField(aClass.findFieldByName("value", false));
        parameterChunk.setGetter(paramType.getCanonicalText() + "Value");
        for (PsiMethod constructor : aClass.getConstructors()) {
          if (constructorIsCompatible(constructor, params)) return constructor;
        }
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
      return null;
    }
    final PsiParameterList parameterList = compatibleConstructor.getParameterList();
    final PsiParameter[] constructorParams = parameterList.getParameters();
    for (int i = 0;  i < constructorParams.length; i++) {
      final PsiParameter param = constructorParams[i];
      final ParameterChunk parameterChunk = params.get(i);

      final PsiField field = findFieldAssigned(param, compatibleConstructor);
      if (field == null) {
        return null;
      }

      parameterChunk.setField(field);

      final PsiMethod getterForField = PropertyUtils.findGetterForField(field);
      if (getterForField != null) {
        parameterChunk.setGetter(getterForField.getName());
      }

      final PsiMethod setterForField = PropertyUtils.findSetterForField(field);
      if (setterForField != null) {
        parameterChunk.setSetter(setterForField.getName());
      }
    }
    return compatibleConstructor;
  }

  private static boolean constructorIsCompatible(PsiMethod constructor, List<ParameterChunk> params) {
    final PsiParameterList parameterList = constructor.getParameterList();
    final PsiParameter[] constructorParams = parameterList.getParameters();
    if (constructorParams.length != params.size()) {
      return false;
    }
    for (int i = 0; i < constructorParams.length; i++) {
      if (!TypeConversionUtil.isAssignable(constructorParams[i].getType(), params.get(i).parameter.type)) {
        return false;
      }
    }
    return true;
  }

  public static class ParameterChunk {
    private ParameterTablePanel.VariableData parameter;
    private PsiField field;
    private String getter;
    private String setter;

    public ParameterChunk(ParameterTablePanel.VariableData parameter) {
      this.parameter = parameter;
    }

    public void setField(PsiField field) {
      this.field = field;
    }

    public void setGetter(String getter) {
      this.getter = getter;
    }

    public void setSetter(String setter) {
      this.setter = setter;
    }

    @Nullable
    public PsiField getField() {
      return field;
    }

    @Nullable
    public static ParameterChunk getChunkByParameter(PsiParameter param, List<ParameterChunk> params) {
      for (ParameterChunk chunk : params) {
        if (chunk.parameter.variable.equals(param)) {
          return chunk;
        }
      }
      return null;
    }
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
