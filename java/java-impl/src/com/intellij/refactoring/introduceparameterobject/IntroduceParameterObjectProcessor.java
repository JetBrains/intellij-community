/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.JavaFileType;
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
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase;
import com.intellij.refactoring.introduceparameterobject.usageInfo.*;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntroduceParameterObjectProcessor extends FixableUsagesRefactoringProcessor {
  private static final Logger logger = Logger.getInstance("com.siyeh.rpp.introduceparameterobject.IntroduceParameterObjectProcessor");

  private final MoveDestination myMoveDestination;
  private final PsiMethod method;
  private final String className;
  private final String packageName;
  private final boolean myUseExistingClass;
  private final boolean myCreateInnerClass;
  private final String myNewVisibility;
  private final boolean myGenerateAccessors;
  private final List<ParameterChunk> parameters;
  private final int[] paramsToMerge;
  private final List<PsiTypeParameter> typeParams;
  private final PsiClass existingClass;
  private PsiMethod myExistingClassCompatibleConstructor;
  private ChangeInfo myChangeInfo;
  private final String fixedParamName;

  public IntroduceParameterObjectProcessor(String className,
                                           String packageName,
                                           MoveDestination moveDestination,
                                           PsiMethod method,
                                           VariableData[] parameters, boolean keepMethodAsDelegate, final boolean useExistingClass,
                                           final boolean createInnerClass,
                                           String newVisibility,
                                           boolean generateAccessors) {
    super(method.getProject());
    myMoveDestination = moveDestination;
    this.method = method;
    this.className = className;
    this.packageName = packageName;
    myUseExistingClass = useExistingClass;
    myCreateInnerClass = createInnerClass;
    myNewVisibility = newVisibility;
    myGenerateAccessors = generateAccessors;
    this.parameters = new ArrayList<ParameterChunk>();
    for (VariableData parameter : parameters) {
      this.parameters.add(new ParameterChunk(parameter));
    }
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] methodParams = parameterList.getParameters();
    paramsToMerge = new int[parameters.length];
    for (int p = 0; p < parameters.length; p++) {
      VariableData parameter = parameters[p];
      for (int i = 0; i < methodParams.length; i++) {
        final PsiParameter methodParam = methodParams[i];
        if (parameter.variable.equals(methodParam)) {
          paramsToMerge[p] = i;
          break;
        }
      }
    }
    final Set<PsiTypeParameter> typeParamSet = new HashSet<PsiTypeParameter>();
    final PsiTypeVisitor<Object> typeParametersVisitor = new PsiTypeVisitor<Object>() {
      @Override
      public Object visitClassType(PsiClassType classType) {
        final PsiClass referent = classType.resolve();
        if (referent instanceof PsiTypeParameter) {
          typeParamSet.add((PsiTypeParameter)referent);
        }
        return super.visitClassType(classType);
      }
    };
    for (VariableData parameter : parameters) {
      parameter.type.accept(typeParametersVisitor);
    }
    typeParams = new ArrayList<PsiTypeParameter>(typeParamSet);

    final String qualifiedName = StringUtil.getQualifiedName(packageName, className);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    existingClass = JavaPsiFacade.getInstance(myProject).findClass(qualifiedName, scope);

    final PsiCodeBlock body = method.getBody();
    final String baseParameterName = StringUtil.decapitalize(className);

    fixedParamName = body != null
                     ? JavaCodeStyleManager.getInstance(myProject).suggestUniqueVariableName(baseParameterName, body.getLBrace(), true)
                     : JavaCodeStyleManager.getInstance(myProject).propertyNameToVariableName(baseParameterName, VariableKind.PARAMETER);


    myChangeInfo =
      new MergeMethodArguments(method, className, packageName, fixedParamName, paramsToMerge, typeParams, keepMethodAsDelegate,
                               myCreateInnerClass ? method.getContainingClass() : null).createChangeInfo();

  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usageInfos) {
    return new IntroduceParameterObjectUsageViewDescriptor(method);
  }


  @Override
  protected boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    if (myUseExistingClass) {
      if (existingClass == null) {
        conflicts.putValue(null, RefactorJBundle.message("cannot.perform.the.refactoring") + "Could not find the selected class");
      }
      if (myExistingClassCompatibleConstructor == null) {
        conflicts.putValue(existingClass, RefactorJBundle.message("cannot.perform.the.refactoring") + "Selected class has no compatible constructors");
      }
    }
    else {
      if (existingClass != null) {
        conflicts.putValue(existingClass,
                           RefactorJBundle.message("cannot.perform.the.refactoring") +
                           RefactorJBundle.message("there.already.exists.a.class.with.the.chosen.name"));
      }
      if (myMoveDestination != null) {
        if (!myMoveDestination.isTargetAccessible(myProject, method.getContainingFile().getVirtualFile())) {
          conflicts.putValue(method, "Created class won't be accessible");
        }
      }
    }
    List<UsageInfo> changeSignatureUsages = new ArrayList<>();
    for (UsageInfo usageInfo : refUsages.get()) {
      if (usageInfo instanceof FixableUsageInfo) {
        final String conflictMessage = ((FixableUsageInfo)usageInfo).getConflictMessage();
        if (conflictMessage != null) {
          conflicts.putValue(usageInfo.getElement(), conflictMessage);
        }
      }
      else {
        changeSignatureUsages.add(usageInfo);
      }
    }

    ChangeSignatureProcessorBase.collectConflictsFromExtensions(new Ref<>(changeSignatureUsages.toArray(new UsageInfo[changeSignatureUsages.size()])), conflicts, myChangeInfo);

    return showConflicts(conflicts, refUsages.get());
  }

  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    if (myUseExistingClass && existingClass != null) {
      myExistingClassCompatibleConstructor = JavaIntroduceParameterObjectDelegate.existingClassIsCompatible(existingClass, parameters);
    }

    for (UsageInfo info : ChangeSignatureProcessorBase.findUsages(myChangeInfo)) {
      usages.add(new ChangeSignatureUsageWrapper(info));
    }

    final PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
    for (int i : paramsToMerge) {
      final PsiParameter parameterInBase = method.getParameterList().getParameters()[i];
      ParameterChunk parameterChunk = ParameterChunk.getChunkByParameter(parameterInBase, parameters);
      assert parameterChunk != null;
      @NonNls String getter = parameterChunk.getGetterName(myProject);
      @NonNls String setter = parameterChunk.getSetterName(myProject);

      final boolean[] needAccessors = {false, false};
      findUsagesForMethod(method, usages, fixedParamName, i, needAccessors, getter, setter);

      for (PsiMethod siblingMethod : overridingMethods) {
        findUsagesForMethod(siblingMethod, usages, fixedParamName, i, needAccessors, getter, setter);
      }

      final boolean useExisting = myUseExistingClass && existingClass != null;

      if (needAccessors[0] && parameterChunk.getGetter() == null) {
        usages.add(new AppendAccessorsUsageInfo(existingClass, myGenerateAccessors || !useExisting, parameterInBase, true, parameters));
      }
      if (needAccessors[1] && parameterChunk.getSetter() == null) {
        usages.add(new AppendAccessorsUsageInfo(existingClass, myGenerateAccessors || !useExisting, parameterInBase, false, parameters));
      }
    }

    if (myNewVisibility != null) {
      usages.add(new BeanClassVisibilityUsageInfo(existingClass, usages.toArray(new UsageInfo[usages.size()]), myNewVisibility, myExistingClassCompatibleConstructor));
    }
  }

  private static void findUsagesForMethod(PsiMethod overridingMethod,
                                          List<FixableUsageInfo> usages,
                                          String fixedParamName,
                                          int i,
                                          final boolean[] needAccessors, String getter, String setter) {
    final LocalSearchScope localSearchScope = new LocalSearchScope(overridingMethod);
    final PsiParameter[] params = overridingMethod.getParameterList().getParameters();
    final PsiParameter parameter = params[i];
    ReferencesSearch.search(parameter, localSearchScope).forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
        final PsiElement refElement = reference.getElement();
        if (refElement instanceof PsiReferenceExpression) {
          final PsiReferenceExpression paramUsage = (PsiReferenceExpression)refElement;
          needAccessors[0] = true;
          if (RefactoringUtil.isPlusPlusOrMinusMinus(paramUsage.getParent())) {
            usages.add(new ReplaceParameterIncrementDecrement(paramUsage, fixedParamName, setter, getter));
            needAccessors[1] = true;
          }
          else if (RefactoringUtil.isAssignmentLHS(paramUsage)) {
            usages.add(new ReplaceParameterAssignmentWithCall(paramUsage, fixedParamName, setter, getter));
            needAccessors[1] = true;
          }
          else {
            usages.add(new ReplaceParameterReferenceWithCall(paramUsage, fixedParamName, getter));
          }
        }
        return true;
      }
    });
  }

  protected void performRefactoring(@NotNull UsageInfo[] usageInfos) {
    final PsiClass psiClass = buildClass(usageInfos);
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
      List<UsageInfo> changeSignatureUsages = new ArrayList<>();
      for (UsageInfo info : usageInfos) {
        if (info instanceof ChangeSignatureUsageWrapper) {
          changeSignatureUsages.add(((ChangeSignatureUsageWrapper)info).getInfo());
        }
      }
      ChangeSignatureProcessorBase.doChangeSignature(myChangeInfo, changeSignatureUsages.toArray(new UsageInfo[changeSignatureUsages.size()]));
    }
  }

  private PsiClass buildClass(UsageInfo[] usageInfos) {
    if (existingClass != null) {
      return existingClass;
    }

    Set<PsiParameter> paramsWithSetters = new HashSet<>();
    for (UsageInfo info : usageInfos) {
      if (info instanceof AppendAccessorsUsageInfo && !((AppendAccessorsUsageInfo)info).isGetter()) {
        paramsWithSetters.add(((AppendAccessorsUsageInfo)info).getParameter());
      }
    }

    final ParameterObjectBuilder beanClassBuilder = new ParameterObjectBuilder();
    beanClassBuilder.setVisibility(myCreateInnerClass ? PsiModifier.PRIVATE : PsiModifier.PUBLIC);
    beanClassBuilder.setProject(myProject);
    beanClassBuilder.setTypeArguments(typeParams);
    beanClassBuilder.setClassName(className);
    beanClassBuilder.setPackageName(packageName);
    for (ParameterChunk parameterChunk : parameters) {
      final VariableData parameter = parameterChunk.getParameter();
      final boolean setterRequired = paramsWithSetters.contains(parameter.variable);
      beanClassBuilder.addField((PsiParameter)parameter.variable,  parameter.name, parameter.type, setterRequired);
    }
    final String classString = beanClassBuilder.buildBeanClass();

    try {
      final PsiFileFactory factory = PsiFileFactory.getInstance(method.getProject());
      final PsiJavaFile newFile = (PsiJavaFile)factory.createFileFromText(className + ".java", JavaFileType.INSTANCE, classString);
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
        final PsiDirectory directory;
        if (myMoveDestination != null) {
          directory = myMoveDestination.getTargetDirectory(containingDirectory);
        } else {
          final Module module = ModuleUtil.findModuleForPsiElement(containingFile);
          directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, containingDirectory, true, true);
        }

        if (directory != null) {

          final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(method.getManager().getProject());
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
          if (dataElements[0] instanceof PsiDocParamRef) {
            final PsiReference reference = dataElements[0].getReference();
            if (reference != null) {
              final PsiElement resolve = reference.resolve();
              if (resolve instanceof PsiParameter) {
                final int parameterIndex = method.getParameterList().getParameterIndex((PsiParameter)resolve);
                if (ArrayUtil.find(paramsToMerge, parameterIndex) < 0) continue;
              }
            }
          }
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
        PsiDocComment psiDocComment = JavaPsiFacade.getElementFactory(myProject).createDocCommentFromText("/**\n*/");
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


  private static class ChangeSignatureUsageWrapper extends FixableUsageInfo {
    private final UsageInfo myInfo;

    public ChangeSignatureUsageWrapper(UsageInfo info) {
      super(info.getElement());
      myInfo = info;
    }

    public UsageInfo getInfo() {
      return myInfo;
    }

    @Override
    public void fixUsage() throws IncorrectOperationException {}
  }
}
