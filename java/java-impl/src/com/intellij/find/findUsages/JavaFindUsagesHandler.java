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
package com.intellij.find.findUsages;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.targets.AliasingPsiTarget;
import com.intellij.psi.targets.AliasingPsiTargetMapper;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.refactoring.util.JavaNonCodeSearchElementDescriptionProvider;
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class JavaFindUsagesHandler extends FindUsagesHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findUsages.JavaFindUsagesHandler");
  public static final String ACTION_STRING = FindBundle.message("find.super.method.warning.action.verb");

  private final PsiElement[] myElementsToSearch;
  private final JavaFindUsagesHandlerFactory myFactory;

  public JavaFindUsagesHandler(@NotNull PsiElement psiElement, @NotNull JavaFindUsagesHandlerFactory factory) {
    this(psiElement, PsiElement.EMPTY_ARRAY, factory);
  }

  public JavaFindUsagesHandler(@NotNull PsiElement psiElement, @NotNull PsiElement[] elementsToSearch, @NotNull JavaFindUsagesHandlerFactory factory) {
    super(psiElement);
    myElementsToSearch = elementsToSearch;
    myFactory = factory;
  }

  @Override
  @NotNull
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
    PsiElement element = getPsiElement();
    if (element instanceof PsiPackage) {
      return new FindPackageUsagesDialog(element, getProject(), myFactory.getFindPackageOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (element instanceof PsiClass) {
      return new FindClassUsagesDialog(element, getProject(), myFactory.getFindClassOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (element instanceof PsiMethod) {
      return new FindMethodUsagesDialog(element, getProject(), myFactory.getFindMethodOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (element instanceof PsiVariable) {
      return new FindVariableUsagesDialog(element, getProject(), myFactory.getFindVariableOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return new FindThrowUsagesDialog(element, getProject(), myFactory.getFindThrowOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    return super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab);
  }

  private static boolean askWhetherShouldSearchForParameterInOverridingMethods(final PsiElement psiElement, final PsiParameter parameter) {
    return Messages.showOkCancelDialog(psiElement.getProject(),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.prompt", parameter.getName()),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.title"),
                               CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(),
                               Messages.getQuestionIcon()) == 0;
  }

  @NotNull
  private static PsiElement[] getParameterElementsToSearch(@NotNull PsiParameter parameter) {
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    PsiMethod[] overrides = OverridingMethodsSearch.search(method, true).toArray(PsiMethod.EMPTY_ARRAY);
    for (int i = 0; i < overrides.length; i++) {
      overrides[i] = (PsiMethod)overrides[i].getNavigationElement();
    }
    List<PsiElement> elementsToSearch = new ArrayList<PsiElement>(overrides.length + 1);
    elementsToSearch.add(parameter);
    int idx = method.getParameterList().getParameterIndex(parameter);
    for (PsiMethod override : overrides) {
      final PsiParameter[] parameters = override.getParameterList().getParameters();
      if (idx < parameters.length) {
        elementsToSearch.add(parameters[idx]);
      }
    }
    return elementsToSearch.toArray(new PsiElement[elementsToSearch.size()]);
  }


  @Override
  @NotNull
  public PsiElement[] getPrimaryElements() {
    final PsiElement element = getPsiElement();
    if (element instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)element;
      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        if (PsiUtil.canBeOverriden(method)) {
          final PsiClass aClass = method.getContainingClass();
          LOG.assertTrue(aClass != null); //Otherwise can not be overriden

          boolean hasOverridden = OverridingMethodsSearch.search(method).findFirst() != null;
          if (hasOverridden && askWhetherShouldSearchForParameterInOverridingMethods(element, parameter)) {
            return getParameterElementsToSearch(parameter);
          }
        }
      }
    }
    return myElementsToSearch.length == 0 ? new PsiElement[]{element} : myElementsToSearch;
  }

  @Override
  @NotNull
  public PsiElement[] getSecondaryElements() {
    PsiElement element = getPsiElement();
    if (ApplicationManager.getApplication().isUnitTestMode()) return PsiElement.EMPTY_ARRAY;
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null) {
        String fieldName = field.getName();
        final String propertyName = JavaCodeStyleManager.getInstance(getProject()).variableNameToPropertyName(fieldName, VariableKind.FIELD);
        Set<PsiMethod> accessors = new THashSet<PsiMethod>();
        boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        PsiMethod getter = PropertyUtil.findPropertyGetterWithType(propertyName, isStatic, field.getType(),
                                     ContainerUtil.iterate(containingClass.getMethods()));
        if (getter != null) accessors.add(getter);
        PsiMethod setter = PropertyUtil.findPropertySetterWithType(propertyName, isStatic, field.getType(),
                                     ContainerUtil.iterate(containingClass.getMethods()));
        if (setter != null) accessors.add(setter);
        accessors.addAll(PropertyUtil.getAccessors(containingClass, fieldName));
        if (!accessors.isEmpty()) {
          boolean containsPhysical = ContainerUtil.find(accessors, new Condition<PsiMethod>() {
            @Override
            public boolean value(PsiMethod psiMethod) {
              return psiMethod.isPhysical();
            }
          }) != null;
          final boolean doSearch = !containsPhysical ||
                                   Messages.showOkCancelDialog(FindBundle.message("find.field.accessors.prompt", fieldName),
                                                               FindBundle.message("find.field.accessors.title"),
                                                               CommonBundle.getYesButtonText(),
                                                               CommonBundle.getNoButtonText(), Messages.getQuestionIcon()) ==
                                   DialogWrapper.OK_EXIT_CODE;
          if (doSearch) {
            final Set<PsiElement> elements = new THashSet<PsiElement>();
            for (PsiMethod accessor : accessors) {
              ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(accessor, ACTION_STRING));
            }
            return PsiUtilCore.toPsiElementArray(elements);
          }
        }
      }
    }
    return super.getSecondaryElements();
  }

  @Override
  @NotNull
  public FindUsagesOptions getFindUsagesOptions(@Nullable final DataContext dataContext) {
    PsiElement element = getPsiElement();
    if (element instanceof PsiPackage) {
      return myFactory.getFindPackageOptions();
    }
    if (element instanceof PsiClass) {
      return myFactory.getFindClassOptions();
    }
    if (element instanceof PsiMethod) {
      return myFactory.getFindMethodOptions();
    }
    if (element instanceof PsiVariable) {
      return myFactory.getFindVariableOptions();
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return myFactory.getFindThrowOptions();
    }
    return super.getFindUsagesOptions(dataContext);
  }

  @Override
  protected Set<String> getStringsToSearch(final PsiElement element) {
    if (element instanceof PsiDirectory) {  // normalize a directory to a corresponding package
      return getStringsToSearch(JavaDirectoryService.getInstance().getPackage((PsiDirectory)element));
    }

    final Set<String> result = new HashSet<String>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (element instanceof PsiPackage) {
          ContainerUtil.addIfNotNull(result, ((PsiPackage)element).getQualifiedName());
        }
        else if (element instanceof PsiClass) {
          final String qname = ((PsiClass)element).getQualifiedName();
          if (qname != null) {
            result.add(qname);
            PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
            if (topLevelClass != null) {
              String topName = topLevelClass.getQualifiedName();
              assert topName != null;
              if (qname.length() > topName.length()) {
                result.add(topName + qname.substring(topName.length()).replace('.', '$'));
              }
            }
          }
        }
        else if (element instanceof PsiMethod) {
          ContainerUtil.addIfNotNull(result, ((PsiMethod)element).getName());
        }
        else if (element instanceof PsiVariable) {
          ContainerUtil.addIfNotNull(result, ((PsiVariable)element).getName());
        }
        else if (element instanceof PsiMetaOwner) {
          final PsiMetaData metaData = ((PsiMetaOwner)element).getMetaData();
          if (metaData != null) {
            ContainerUtil.addIfNotNull(result, metaData.getName());
          }
        }
        else if (element instanceof PsiNamedElement) {
          ContainerUtil.addIfNotNull(result, ((PsiNamedElement)element).getName());
        }
        else if (element instanceof XmlAttributeValue) {
          ContainerUtil.addIfNotNull(result, ((XmlAttributeValue)element).getValue());
        } else {
          LOG.error("Unknown element type: " + element);
        }
      }
    });

    return result;
  }

  @Override
  public boolean processElementUsages(@NotNull final PsiElement element,
                                      @NotNull final Processor<UsageInfo> processor,
                                      @NotNull final FindUsagesOptions options) {
    if (options instanceof JavaVariableFindUsagesOptions) {
      final JavaVariableFindUsagesOptions varOptions = (JavaVariableFindUsagesOptions) options;
      if (varOptions.isReadAccess || varOptions.isWriteAccess){
        if (varOptions.isReadAccess && varOptions.isWriteAccess){
          if (!addElementUsages(element, processor, options)) return false;
        }
        else{
          if (!addElementUsages(element, new Processor<UsageInfo>() {
            @Override
            public boolean process(UsageInfo info) {
              final PsiElement element = info.getElement();
              boolean isWrite = element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element);
              if (isWrite == varOptions.isWriteAccess) {
                if (!processor.process(info)) return false;
              }
              return true;
            }
          }, varOptions)) return false;
        }
      }
    }
    else if (options.isUsages) {
      if (!addElementUsages(element, processor, options)) return false;
    }

    boolean success = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (ThrowSearchUtil.isSearchable (element) && options instanceof JavaThrowFindUsagesOptions && options.isUsages) {
          ThrowSearchUtil.Root root = options.getUserData(ThrowSearchUtil.THROW_SEARCH_ROOT_KEY);
          if (root == null) {
            final ThrowSearchUtil.Root[] roots = ThrowSearchUtil.getSearchRoots(element);
            if (roots != null && roots.length > 0) {
              root = roots [0];
            }
          }
          if (root != null) {
            return ThrowSearchUtil.addThrowUsages(processor, root, options);
          }
        }
        return true;
      }
    });
    if (!success) return false;

    if (options instanceof JavaPackageFindUsagesOptions && ((JavaPackageFindUsagesOptions)options).isClassesUsages){
      if (!addClassesUsages((PsiPackage)element, processor, (JavaPackageFindUsagesOptions)options)) return false;
    }

    if (options instanceof JavaClassFindUsagesOptions) {
      final JavaClassFindUsagesOptions classOptions = (JavaClassFindUsagesOptions)options;
      final PsiClass psiClass = (PsiClass)element;
      if (classOptions.isMethodsUsages){
        if (!addMethodsUsages(psiClass, processor, classOptions)) return false;
      }
      if (classOptions.isFieldsUsages){
        if (!addFieldsUsages(psiClass, processor, classOptions)) return false;
      }
      if (psiClass.isInterface()) {
        if (classOptions.isDerivedInterfaces){
          if (classOptions.isImplementingClasses){
            if (!addInheritors(psiClass, processor, classOptions)) return false;
          }
          else{
            if (!addDerivedInterfaces(psiClass, processor, classOptions)) return false;
          }
        }
        else if (classOptions.isImplementingClasses){
          if (!addImplementingClasses(psiClass, processor, classOptions)) return false;
        }
      }
      else if (classOptions.isDerivedClasses) {
        if (!addInheritors(psiClass, processor, classOptions)) return false;
      }
    }

    if (options instanceof JavaMethodFindUsagesOptions){
      final PsiMethod psiMethod = (PsiMethod)element;
      boolean isAbstract = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return psiMethod.hasModifierProperty(PsiModifier.ABSTRACT);
        }
      });
      final JavaMethodFindUsagesOptions methodOptions = (JavaMethodFindUsagesOptions)options;
      if (isAbstract && methodOptions.isImplementingMethods || methodOptions.isOverridingMethods) {
        if (!processOverridingMethods(psiMethod, processor, methodOptions)) return false;
      }
    }

    if (element instanceof PomTarget) {
       if (!addAliasingUsages((PomTarget)element, processor, options)) return false;
    }
    final Boolean isSearchable = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return ThrowSearchUtil.isSearchable(element);
      }
    });
    if (!isSearchable && options.isSearchForTextOccurrences && options.searchScope instanceof GlobalSearchScope) {
      // todo add to fastTrack
      if (!processUsagesInText(element, processor, (GlobalSearchScope)options.searchScope)) return false;
    }
    return true;
  }

  private static boolean addAliasingUsages(@NotNull PomTarget pomTarget,
                                           @NotNull final Processor<UsageInfo> processor,
                                           @NotNull final FindUsagesOptions options) {
    for (AliasingPsiTargetMapper aliasingPsiTargetMapper : Extensions.getExtensions(AliasingPsiTargetMapper.EP_NAME)) {
      for (AliasingPsiTarget psiTarget : aliasingPsiTargetMapper.getTargets(pomTarget)) {
        boolean success = ReferencesSearch
          .search(new ReferencesSearch.SearchParameters(PomService.convertToPsi(psiTarget), options.searchScope, false, options.fastTrack))
          .forEach(new ReadActionProcessor<PsiReference>() {
            @Override
            public boolean processInReadAction(final PsiReference reference) {
              return addResult(processor, reference, options);
            }
          });
        if (!success) return false;
      }
    }
    return true;
  }

  private static boolean processOverridingMethods(@NotNull PsiMethod psiMethod,
                                                  @NotNull final Processor<UsageInfo> processor,
                                                  @NotNull final JavaMethodFindUsagesOptions options) {
    return OverridingMethodsSearch.search(psiMethod, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiMethod>(
      new PsiElementProcessor<PsiMethod>() {
      @Override
      public boolean execute(@NotNull PsiMethod element) {
        return addResult(processor, element.getNavigationElement(), options);
      }
    }));
  }


  private static boolean addClassesUsages(@NotNull PsiPackage aPackage,
                                          @NotNull final Processor<UsageInfo> processor,
                                          @NotNull final JavaPackageFindUsagesOptions options) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null){
      progress.pushState();
    }

    List<PsiClass> classes = new ArrayList<PsiClass>();
    addClassesInPackage(aPackage, options.isIncludeSubpackages, classes);
    for (final PsiClass aClass : classes) {
      if (progress != null) {
        progress.setText(FindBundle.message("find.searching.for.references.to.class.progress", ApplicationManager.getApplication().runReadAction(new Computable<String>(){
          @Override
          public String compute() {
            return aClass.getName();
          }
        })));
        progress.checkCanceled();
      }
      boolean success = ReferencesSearch.search(new ReferencesSearch.SearchParameters(aClass, options.searchScope, false, options.fastTrack)).forEach(new ReadActionProcessor<PsiReference>() {
        @Override
        public boolean processInReadAction(final PsiReference psiReference) {
          return addResult(processor, psiReference, options);
        }
      });
      if (!success) return false;
    }

    if (progress != null){
      progress.popState();
    }
    return true;
  }

  private static void addClassesInPackage(@NotNull PsiPackage aPackage, boolean includeSubpackages, @NotNull List<PsiClass> array) {
    PsiDirectory[] dirs = aPackage.getDirectories();
    for (PsiDirectory dir : dirs) {
      addClassesInDirectory(dir, includeSubpackages, array);
    }
  }

  private static void addClassesInDirectory(@NotNull final PsiDirectory dir,
                                            final boolean includeSubdirs,
                                            @NotNull final List<PsiClass> array) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
        ContainerUtil.addAll(array, classes);
        if (includeSubdirs) {
          PsiDirectory[] dirs = dir.getSubdirectories();
          for (PsiDirectory directory : dirs) {
            addClassesInDirectory(directory, includeSubdirs, array);
          }
        }
      }
    });
  }

  private static boolean addMethodsUsages(@NotNull final PsiClass aClass,
                                          @NotNull final Processor<UsageInfo> processor,
                                          @NotNull final JavaClassFindUsagesOptions options) {
    if (options.isIncludeInherited) {
      final PsiManager manager = aClass.getManager();
      PsiMethod[] methods = aClass.getAllMethods();
      MethodsLoop:
      for(int i = 0; i < methods.length; i++){
        final PsiMethod method = methods[i];
        // filter overriden methods
        MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
        for(int j = 0; j < i; j++){
          if (methodSignature.equals(methods[j].getSignature(PsiSubstitutor.EMPTY))) continue MethodsLoop;
        }
        final PsiClass methodClass = method.getContainingClass();
        if (methodClass != null && manager.areElementsEquivalent(methodClass, aClass)){
          if (!addElementUsages(methods[i], processor, options)) return false;
        }
        else {
          boolean success = MethodReferencesSearch.search(new MethodReferencesSearch.SearchParameters(method, options.searchScope, true, options.fastTrack))
            .forEach(new PsiReferenceProcessorAdapter(new PsiReferenceProcessor() {
              @Override
              public boolean execute(PsiReference reference) {
                addResultFromReference(reference, methodClass, manager, aClass, processor, options);
                return true;
              }
            }));
          if (!success) return false;
        }
      }
    }
    else {
      for (PsiMethod method : aClass.getMethods()) {
        if (!addElementUsages(method, processor, options)) return false;
      }
    }
    return true;
  }

  private static boolean addFieldsUsages(@NotNull final PsiClass aClass,
                                         @NotNull final Processor<UsageInfo> processor,
                                         @NotNull final JavaClassFindUsagesOptions options) {
    if (options.isIncludeInherited) {
      final PsiManager manager = aClass.getManager();
      PsiField[] fields = aClass.getAllFields();
      FieldsLoop:
      for (int i = 0; i < fields.length; i++) {
        final PsiField field = fields[i];
        // filter hidden fields
        for (int j = 0; j < i; j++) {
          if (Comparing.strEqual(field.getName(), fields[j].getName())) continue FieldsLoop;
        }
        final PsiClass fieldClass = field.getContainingClass();
        if (manager.areElementsEquivalent(fieldClass, aClass)) {
          if (!addElementUsages(fields[i], processor, options)) return false;
        }
        else {
          boolean success = ReferencesSearch.search(new ReferencesSearch.SearchParameters(field, options.searchScope, false, options.fastTrack)).forEach(new ReadActionProcessor<PsiReference>() {
            @Override
            public boolean processInReadAction(final PsiReference reference) {
              return addResultFromReference(reference, fieldClass, manager, aClass, processor, options);
            }
          });
          if (!success) return false;
        }
      }
    }
    else {
      PsiField[] fields = ApplicationManager.getApplication().runReadAction(new Computable<PsiField[]>() {
        @Override
        public PsiField[] compute() {
          return aClass.getFields();
        }
      });
      for (PsiField field : fields) {
        if (!addElementUsages(field, processor, options)) return false;
      }
    }
    return true;
  }

  @Nullable
  private static PsiClass getFieldOrMethodAccessedClass(@NotNull PsiReferenceExpression ref, PsiClass fieldOrMethodClass) {
    PsiElement[] children = ref.getChildren();
    if (children.length > 1 && children[0] instanceof PsiExpression) {
      PsiExpression expr = (PsiExpression)children[0];
      PsiType type = expr.getType();
      if (type != null) {
        if (!(type instanceof PsiClassType)) return null;
        return PsiUtil.resolveClassInType(type);
      }
      else {
        if (expr instanceof PsiReferenceExpression) {
          PsiElement refElement = ((PsiReferenceExpression)expr).resolve();
          if (refElement instanceof PsiClass) return (PsiClass)refElement;
        }
        return null;
      }
    }
    PsiManager manager = ref.getManager();
    for(PsiElement parent = ref; parent != null; parent = parent.getParent()){
      if (parent instanceof PsiClass
        && (manager.areElementsEquivalent(parent, fieldOrMethodClass) || ((PsiClass)parent).isInheritor(fieldOrMethodClass, true))){
        return (PsiClass)parent;
      }
    }
    return null;
  }

  private static boolean addInheritors(@NotNull PsiClass aClass,
                                       @NotNull final Processor<UsageInfo> processor,
                                       @NotNull final JavaClassFindUsagesOptions options) {
    return ClassInheritorsSearch.search(aClass, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiClass>(
      new PsiElementProcessor<PsiClass>() {
      @Override
      public boolean execute(@NotNull PsiClass element) {
        return addResult(processor, element, options);
      }

    }));
  }

  private static boolean addDerivedInterfaces(@NotNull PsiClass anInterface,
                                              @NotNull final Processor<UsageInfo> processor,
                                              @NotNull final JavaClassFindUsagesOptions options) {
    return ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiClass>(
      new PsiElementProcessor<PsiClass>() {
      @Override
      public boolean execute(@NotNull PsiClass inheritor) {
        return !inheritor.isInterface() || addResult(processor, inheritor, options);
      }

    }));
  }

  private static boolean addImplementingClasses(@NotNull PsiClass anInterface,
                                                @NotNull final Processor<UsageInfo> processor,
                                                @NotNull final JavaClassFindUsagesOptions options) {
    return ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiClass>(
      new PsiElementProcessor<PsiClass>() {
      @Override
      public boolean execute(@NotNull PsiClass inheritor) {
        return inheritor.isInterface() || addResult(processor, inheritor, options);
      }
    }));
  }

  private static boolean addResultFromReference(@NotNull PsiReference reference,
                                                @NotNull PsiClass methodClass,
                                                @NotNull PsiManager manager,
                                                @NotNull PsiClass aClass,
                                                @NotNull Processor<UsageInfo> processor,
                                                @NotNull FindUsagesOptions options) {
    PsiElement refElement = reference.getElement();
    if (refElement instanceof PsiReferenceExpression) {
      PsiClass usedClass = getFieldOrMethodAccessedClass((PsiReferenceExpression)refElement, methodClass);
      if (usedClass != null) {
        if (manager.areElementsEquivalent(usedClass, aClass) || usedClass.isInheritor(aClass, true)) {
          if (!addResult(processor, refElement, options)) return false;
        }
      }
    }
    return true;
  }

  private static boolean addElementUsages(@NotNull final PsiElement element,
                                          @NotNull final Processor<UsageInfo> processor,
                                          @NotNull final FindUsagesOptions options) {
    final SearchScope searchScope = options.searchScope;
    if (element instanceof PsiMethod && ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return ((PsiMethod)element).isConstructor();
      }
    })) {
      PsiMethod method = (PsiMethod)element;
      final PsiClass parentClass = method.getContainingClass();

      if (parentClass != null) {
        boolean strictSignatureSearch =
          !(options instanceof JavaMethodFindUsagesOptions) || !((JavaMethodFindUsagesOptions)options).isIncludeOverloadUsages;
        return MethodReferencesSearch
          .search(new MethodReferencesSearch.SearchParameters(method, searchScope, strictSignatureSearch, options.fastTrack))
          .forEach(new ReadActionProcessor<PsiReference>() {
            @Override
            public boolean processInReadAction(final PsiReference ref) {
              return addResult(processor, ref, options);
            }
          });
      }
      return true;
    }

    final ReadActionProcessor<PsiReference> consumer = new ReadActionProcessor<PsiReference>() {
      @Override
      public boolean processInReadAction(final PsiReference ref) {
        return addResult(processor, ref, options);
      }
    };

    if (element instanceof PsiMethod) {
      final boolean strictSignatureSearch = !(options instanceof JavaMethodFindUsagesOptions) || // field with getter
                                            !((JavaMethodFindUsagesOptions)options).isIncludeOverloadUsages;
      return MethodReferencesSearch
        .search(new MethodReferencesSearch.SearchParameters((PsiMethod)element, searchScope, strictSignatureSearch, options.fastTrack))
        .forEach(consumer);
    }
    return ReferencesSearch.search(new ReferencesSearch.SearchParameters(element, searchScope, false, options.fastTrack)).forEach(consumer);
  }

  private static boolean addResult(@NotNull Processor<UsageInfo> processor, @NotNull PsiElement element, @NotNull FindUsagesOptions options) {
    return !filterUsage(element, options) || processor.process(new UsageInfo(element));
  }

  private static boolean addResult(Processor<UsageInfo> processor, PsiReference ref, FindUsagesOptions options) {
    if (filterUsage(ref.getElement(), options)){
      TextRange rangeInElement = ref.getRangeInElement();
      return processor.process(new UsageInfo(ref.getElement(), rangeInElement.getStartOffset(), rangeInElement.getEndOffset(), false));
    }
    return true;
  }

  private static boolean filterUsage(PsiElement usage, FindUsagesOptions options) {
    if (!(usage instanceof PsiJavaCodeReferenceElement)) {
      return true;
    }
    if (options instanceof JavaPackageFindUsagesOptions && !((JavaPackageFindUsagesOptions)options).isIncludeSubpackages &&
        ((PsiReference)usage).resolve() instanceof PsiPackage) {
      PsiElement parent = usage.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)parent).resolve() instanceof PsiPackage) {
        return false;
      }
    }

    if (!(usage instanceof PsiReferenceExpression)){
      if (options instanceof JavaFindUsagesOptions && ((JavaFindUsagesOptions)options).isSkipImportStatements){
        PsiElement parent = usage.getParent();
        while(parent instanceof PsiJavaCodeReferenceElement){
          parent = parent.getParent();
        }
        if (parent instanceof PsiImportStatement){
          return false;
        }
      }

      if (options instanceof JavaPackageFindUsagesOptions && ((JavaPackageFindUsagesOptions)options).isSkipPackageStatements){
        PsiElement parent = usage.getParent();
        while(parent instanceof PsiJavaCodeReferenceElement){
          parent = parent.getParent();
        }
        if (parent instanceof PsiPackageStatement){
          return false;
        }
      }
    }
    return true;
  }


  @Override
  protected boolean isSearchForTextOccurencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    if (isSingleFile) return false;
    return new JavaNonCodeSearchElementDescriptionProvider().getElementDescription(psiElement, NonCodeSearchDescriptionLocation.NON_JAVA) != null;

  }

  @Override
  public Collection<PsiReference> findReferencesToHighlight(@NotNull final PsiElement target, @NotNull final SearchScope searchScope) {
    if (target instanceof PsiMethod) {
      final PsiMethod[] superMethods = ((PsiMethod)target).findDeepestSuperMethods();
      if (superMethods.length == 0) {
        return MethodReferencesSearch.search((PsiMethod)target, searchScope, true).findAll();
      }
      final Collection<PsiReference> result = new ArrayList<PsiReference>();
      for (PsiMethod superMethod : superMethods) {
        result.addAll(MethodReferencesSearch.search(superMethod, searchScope, true).findAll());
      }
      return result;
    }
    return super.findReferencesToHighlight(target, searchScope);
  }

}
