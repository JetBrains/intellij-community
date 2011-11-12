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
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findUsages.DefaultFindUsagesHandler");
  public static final String ACTION_STRING = FindBundle.message("find.super.method.warning.action.verb");

  private final PsiElement[] myElementsToSearch;
  private final JavaFindUsagesHandlerFactory myFactory;

  public JavaFindUsagesHandler(@NotNull PsiElement psiElement, JavaFindUsagesHandlerFactory factory) {
    this(psiElement, PsiElement.EMPTY_ARRAY, factory);
  }


  public JavaFindUsagesHandler(@NotNull PsiElement psiElement, @NotNull PsiElement[] elementsToSearch, JavaFindUsagesHandlerFactory factory) {
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

  private static PsiElement[] getParameterElementsToSearch(final PsiParameter parameter) {
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
          final boolean doSearch;
          boolean containsPhysical = ContainerUtil.find(accessors, new Condition<PsiMethod>() {
            @Override
            public boolean value(PsiMethod psiMethod) {
              return psiMethod.isPhysical();
            }
          }) != null;
          if (!containsPhysical) {
            doSearch = true;
          }
          else {
            doSearch = Messages.showOkCancelDialog(FindBundle.message("find.field.accessors.prompt", fieldName),
                                           FindBundle.message("find.field.accessors.title"),
                                           CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(),
                                           Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE;
          }
          if (doSearch) {
            final Set<PsiElement> elements = new THashSet<PsiElement>();
            for (PsiMethod accessor : accessors) {
              ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(accessor, ACTION_STRING));
            }
            return PsiUtilBase.toPsiElementArray(elements);
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
              result.add(topName + qname.substring(topName.length()).replace('.', '$'));
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
  public void processElementUsages(@NotNull final PsiElement element, @NotNull final Processor<UsageInfo> processor, @NotNull final FindUsagesOptions options) {
    if (options instanceof JavaVariableFindUsagesOptions) {
      final JavaVariableFindUsagesOptions varOptions = (JavaVariableFindUsagesOptions) options;
      if (varOptions.isReadAccess || varOptions.isWriteAccess){
        if (varOptions.isReadAccess && varOptions.isWriteAccess){
          addElementUsages(element, processor, options);
        }
        else{
          addElementUsages(element, new Processor<UsageInfo>() {
            @Override
            public boolean process(UsageInfo info) {
              final PsiElement element = info.getElement();
              boolean isWrite = element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element);
              if (isWrite == varOptions.isWriteAccess) {
                if (!processor.process(info)) return false;
              }
              return true;
            }
          }, varOptions);
        }
      }
    }
    else if (options.isUsages) {
      addElementUsages(element, processor, options);
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (ThrowSearchUtil.isSearchable (element) && options instanceof JavaThrowFindUsagesOptions && options.isUsages) {
          ThrowSearchUtil.Root root = options.getUserData(ThrowSearchUtil.THROW_SEARCH_ROOT_KEY);
          if (root == null) {
            final ThrowSearchUtil.Root[] roots = ThrowSearchUtil.getSearchRoots(element);
            if (roots != null && roots.length > 0) {
              root = roots [0];
            }
          }
          if (root != null) {
            ThrowSearchUtil.addThrowUsages(processor, root, options);
          }
        }
      }
    });

    if (options instanceof JavaPackageFindUsagesOptions && ((JavaPackageFindUsagesOptions)options).isClassesUsages){
      addClassesUsages((PsiPackage)element, processor, (JavaPackageFindUsagesOptions)options);
    }

    if (options instanceof JavaClassFindUsagesOptions) {
      final JavaClassFindUsagesOptions classOptions = (JavaClassFindUsagesOptions)options;
      final PsiClass psiClass = (PsiClass)element;
      if (classOptions.isMethodsUsages){
        addMethodsUsages(psiClass, processor, classOptions);
      }
      if (classOptions.isFieldsUsages){
        addFieldsUsages(psiClass, processor, classOptions);
      }
      if (psiClass.isInterface()) {
        if (classOptions.isDerivedInterfaces){
          if (classOptions.isImplementingClasses){
            addInheritors(psiClass, processor, classOptions);
          }
          else{
            addDerivedInterfaces(psiClass, processor, classOptions);
          }
        }
        else if (classOptions.isImplementingClasses){
          addImplementingClasses(psiClass, processor, classOptions);
        }
      }
      else if (classOptions.isDerivedClasses) {
        addInheritors(psiClass, processor, classOptions);
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
        processOverridingMethods(psiMethod, processor, methodOptions);
      }
    }

    if (element instanceof PomTarget) {
       addAliasingUsages((PomTarget)element, processor, options);
    }
    final Boolean isSearchable = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return ThrowSearchUtil.isSearchable(element);
      }
    });
    if (!isSearchable && options.isSearchForTextOccurrences && options.searchScope instanceof GlobalSearchScope) {
      // todo add to fastTrack
      processUsagesInText(element, processor, (GlobalSearchScope)options.searchScope);
    }
  }

  private static void addAliasingUsages(PomTarget pomTarget, final Processor<UsageInfo> processor, final FindUsagesOptions options) {
    for (AliasingPsiTargetMapper aliasingPsiTargetMapper : Extensions.getExtensions(AliasingPsiTargetMapper.EP_NAME)) {
      for (AliasingPsiTarget psiTarget : aliasingPsiTargetMapper.getTargets(pomTarget)) {
          ReferencesSearch.search(new ReferencesSearch.SearchParameters(PomService.convertToPsi(psiTarget), options.searchScope, false, options.fastTrack)).forEach(new ReadActionProcessor<PsiReference>() {
            @Override
            public boolean processInReadAction(final PsiReference reference) {
              addResult(processor, reference, options);
              return true;
            }
          });
      }
    }
  }

  private static void processOverridingMethods(PsiMethod psiMethod, final Processor<UsageInfo> processor, final JavaMethodFindUsagesOptions options) {
    OverridingMethodsSearch.search(psiMethod, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiMethod>(
      new PsiElementProcessor<PsiMethod>() {
      @Override
      public boolean execute(@NotNull PsiMethod element) {
        addResult(processor, element.getNavigationElement(), options);
        return true;
      }
    }));
  }


  private static void addClassesUsages(PsiPackage aPackage, final Processor<UsageInfo> results, final JavaPackageFindUsagesOptions options) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null){
      progress.pushState();
    }

    ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
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
      ReferencesSearch.search(new ReferencesSearch.SearchParameters(aClass, options.searchScope, false, options.fastTrack)).forEach(new ReadActionProcessor<PsiReference>() {
        @Override
        public boolean processInReadAction(final PsiReference psiReference) {
          return addResult(results, psiReference, options);
        }
      });
    }

    if (progress != null){
      progress.popState();
    }
  }

  private static void addClassesInPackage(PsiPackage aPackage, boolean includeSubpackages, ArrayList<PsiClass> array) {
    PsiDirectory[] dirs = aPackage.getDirectories();
    for (PsiDirectory dir : dirs) {
      addClassesInDirectory(dir, includeSubpackages, array);
    }
  }

  private static void addClassesInDirectory(final PsiDirectory dir, final boolean includeSubdirs, final ArrayList<PsiClass> array) {
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

  private static void addMethodsUsages(final PsiClass aClass, final Processor<UsageInfo> results, final JavaClassFindUsagesOptions options) {
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
            addElementUsages(methods[i], results, options);
          }
          else{
            MethodReferencesSearch.search(new MethodReferencesSearch.SearchParameters(method, options.searchScope, true, options.fastTrack)).forEach(new PsiReferenceProcessorAdapter(new PsiReferenceProcessor() {
                      @Override
                      public boolean execute(PsiReference reference) {
                        addResultFromReference(reference, methodClass, manager, aClass, results, options);
                        return true;
                      }
                    }));
          }
        }
    }
    else {
      for (PsiMethod method : aClass.getMethods()) {
        addElementUsages(method, results, options);
      }
    }
  }

  private static void addFieldsUsages(final PsiClass aClass, final Processor<UsageInfo> results, final JavaClassFindUsagesOptions options) {
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
          addElementUsages(fields[i], results, options);
        }
        else {
          ReferencesSearch.search(new ReferencesSearch.SearchParameters(field, options.searchScope, false, options.fastTrack)).forEach(new ReadActionProcessor<PsiReference>() {
            @Override
            public boolean processInReadAction(final PsiReference reference) {
              addResultFromReference(reference, fieldClass, manager, aClass, results, options);
              return true;
            }
          });
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
        addElementUsages(field, results, options);
      }
    }
  }

  @Nullable
  private static PsiClass getFieldOrMethodAccessedClass(PsiReferenceExpression ref, PsiClass fieldOrMethodClass) {
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

  private static void addInheritors(final PsiClass aClass, final Processor<UsageInfo> results, final JavaClassFindUsagesOptions options) {
    ClassInheritorsSearch.search(aClass, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiClass>(
      new PsiElementProcessor<PsiClass>() {
      @Override
      public boolean execute(@NotNull PsiClass element) {
        addResult(results, element, options);
        return true;
      }

    }));
  }

  private static void addDerivedInterfaces(PsiClass anInterface, final Processor<UsageInfo> results, final JavaClassFindUsagesOptions options) {
    ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiClass>(
      new PsiElementProcessor<PsiClass>() {
      @Override
      public boolean execute(@NotNull PsiClass inheritor) {
        if (inheritor.isInterface()) {
          addResult(results, inheritor, options);
        }
        return true;
      }

    }));
  }

  private static void addImplementingClasses(PsiClass anInterface, final Processor<UsageInfo> results, final JavaClassFindUsagesOptions options) {
    ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiClass>(
      new PsiElementProcessor<PsiClass>() {
      @Override
      public boolean execute(@NotNull PsiClass inheritor) {
        if (!inheritor.isInterface()) {
          addResult(results, inheritor, options);
        }
        return true;
      }

    }));
  }

  private static void addResultFromReference(final PsiReference reference,
                                             final PsiClass methodClass,
                                             final PsiManager manager,
                                             final PsiClass aClass,
                                             final Processor<UsageInfo> results,
                                             final FindUsagesOptions options) {
    PsiElement refElement = reference.getElement();
    if (refElement instanceof PsiReferenceExpression) {
      PsiClass usedClass = getFieldOrMethodAccessedClass((PsiReferenceExpression)refElement, methodClass);
      if (usedClass != null) {
        if (manager.areElementsEquivalent(usedClass, aClass) || usedClass.isInheritor(aClass, true)) {
          addResult(results, refElement, options);
        }
      }
    }
  }

  public static void addElementUsages(final PsiElement element, final Processor<UsageInfo> result, final FindUsagesOptions options) {
    final SearchScope searchScope = options.searchScope;
    if (element instanceof PsiMethod && ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return ((PsiMethod)element).isConstructor();
      }
    })){
      PsiMethod method = (PsiMethod)element;
      final PsiClass parentClass = method.getContainingClass();

      if (parentClass != null) {
        MethodReferencesSearch.search(new MethodReferencesSearch.SearchParameters(method, searchScope, options instanceof JavaMethodFindUsagesOptions ? !((JavaMethodFindUsagesOptions)options).isIncludeOverloadUsages : true, options.fastTrack)).forEach(new ReadActionProcessor<PsiReference>() {
          @Override
          public boolean processInReadAction(final PsiReference ref) {
            return addResult(result, ref, options);
          }
        });
      }
      return;
    }

    final ReadActionProcessor<PsiReference> consumer = new ReadActionProcessor<PsiReference>() {
      @Override
      public boolean processInReadAction(final PsiReference ref) {
        return addResult(result, ref, options);
      }
    };

    if (element instanceof PsiMethod) {
      final boolean strictSignatureSearch = !(options instanceof JavaMethodFindUsagesOptions) || // field with getter
                                            !((JavaMethodFindUsagesOptions)options).isIncludeOverloadUsages;
      MethodReferencesSearch.search(new MethodReferencesSearch.SearchParameters((PsiMethod)element, searchScope, strictSignatureSearch, options.fastTrack)).forEach(consumer);
    } else {
      ReferencesSearch.search(new ReferencesSearch.SearchParameters(element, searchScope, false, options.fastTrack)).forEach(consumer);
    }
  }

  public static void addResult(Processor<UsageInfo> total, PsiElement element, FindUsagesOptions options) {
    if (filterUsage(element, options)){
      total.process(new UsageInfo(element));
    }
  }

  public static boolean addResult(Processor<UsageInfo> results, PsiReference ref, FindUsagesOptions options) {
    if (filterUsage(ref.getElement(), options)){
      TextRange rangeInElement = ref.getRangeInElement();
      return results.process(new UsageInfo(ref.getElement(), rangeInElement.getStartOffset(), rangeInElement.getEndOffset(), false));
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
  protected boolean isSearchForTextOccurencesAvailable(PsiElement psiElement, boolean isSingleFile) {
    if (isSingleFile) return false;
    return new JavaNonCodeSearchElementDescriptionProvider().getElementDescription(psiElement, NonCodeSearchDescriptionLocation.NON_JAVA) != null;

  }

  @Override
  public Collection<PsiReference> findReferencesToHighlight(final PsiElement target, final SearchScope searchScope) {
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
