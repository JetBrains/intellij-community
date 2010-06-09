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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
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
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
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
  private final FindUsagesOptions myFindPackageOptions;
  private final FindUsagesOptions myFindClassOptions;
  private final FindUsagesOptions myFindMethodOptions;
  private final FindUsagesOptions myFindVariableOptions;
  private final FindUsagesOptions myFindThrowOptions;

  public JavaFindUsagesHandler(@NotNull PsiElement psiElement,
                                @NotNull FindUsagesOptions findClassOptions,
                                @NotNull FindUsagesOptions findMethodOptions,
                                @NotNull FindUsagesOptions findPackageOptions,
                                @NotNull FindUsagesOptions findThrowOptions,
                                @NotNull FindUsagesOptions findVariableOptions) {
    this(psiElement, PsiElement.EMPTY_ARRAY, findClassOptions, findMethodOptions, findPackageOptions, findThrowOptions, findVariableOptions);
  }


  public JavaFindUsagesHandler(@NotNull PsiElement psiElement,
                                  @NotNull PsiElement[] elementsToSearch,
                                  @NotNull FindUsagesOptions findClassOptions,
                                  @NotNull FindUsagesOptions findMethodOptions,
                                  @NotNull FindUsagesOptions findPackageOptions,
                                  @NotNull FindUsagesOptions findThrowOptions,
                                  @NotNull FindUsagesOptions findVariableOptions) {
    super(psiElement);
    myElementsToSearch = elementsToSearch;
    myFindClassOptions = findClassOptions;
    myFindMethodOptions = findMethodOptions;
    myFindPackageOptions = findPackageOptions;
    myFindThrowOptions = findThrowOptions;
    myFindVariableOptions = findVariableOptions;
  }

  @NotNull
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
    PsiElement element = getPsiElement();
    if (element instanceof PsiPackage) {
      return new FindPackageUsagesDialog(element, getProject(), myFindPackageOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (element instanceof PsiClass) {
      return new FindClassUsagesDialog(element, getProject(), myFindClassOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (element instanceof PsiMethod) {
      return new FindMethodUsagesDialog(element, getProject(), myFindMethodOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (element instanceof PsiVariable) {
      return new FindVariableUsagesDialog(element, getProject(), myFindVariableOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return new FindThrowUsagesDialog(element, getProject(), myFindThrowOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
    }
    return super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab);
  }

  private static boolean askWhetherShouldSearchForParameterInOverridingMethods(final PsiElement psiElement, final PsiParameter parameter) {
    return Messages.showDialog(psiElement.getProject(),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.prompt", parameter.getName()),
                               FindBundle.message("find.parameter.usages.in.overriding.methods.title"),
                               new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 0,
                               Messages.getQuestionIcon()) == 0;
  }

  private static PsiElement[] getParameterElementsToSearch(final PsiParameter parameter) {
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    PsiMethod[] overrides = OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY);
    for (int i = 0; i < overrides.length; i++) {
      overrides[i] = (PsiMethod)overrides[i].getNavigationElement();
    }
    PsiElement[] elementsToSearch = new PsiElement[overrides.length + 1];
    elementsToSearch[0] = parameter;
    int idx = method.getParameterList().getParameterIndex(parameter);
    for (int i = 0; i < overrides.length; i++) {
      elementsToSearch[i + 1] = overrides[i].getParameterList().getParameters()[idx];
    }
    return elementsToSearch;
  }


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

  @NotNull
  public PsiElement[] getSecondaryElements() {
    PsiElement element = getPsiElement();
    if (ApplicationManager.getApplication().isUnitTestMode()) return PsiElement.EMPTY_ARRAY;
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null) {
        final String propertyName = JavaCodeStyleManager.getInstance(getProject()).variableNameToPropertyName(field.getName(), VariableKind.FIELD);
        PsiMethod getter = PropertyUtil.
          findPropertyGetterWithType(propertyName, field.hasModifierProperty(PsiModifier.STATIC), field.getType(),
                                     ContainerUtil.iterate(containingClass.getMethods()));
        PsiMethod setter = PropertyUtil.
          findPropertySetterWithType(propertyName, field.hasModifierProperty(PsiModifier.STATIC), field.getType(),
                                     ContainerUtil.iterate(containingClass.getMethods()));
        if (getter != null || setter != null) {
          final boolean doSearch;
          if ((getter == null || !getter.isPhysical()) && (setter == null || !setter.isPhysical())) {
            doSearch = true;
          } else {
            doSearch = Messages.showDialog(FindBundle.message("find.field.accessors.prompt", field.getName()),
                                           FindBundle.message("find.field.accessors.title"),
                                           new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 0,
                                           Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE;
          }
          if (doSearch) {
            final List<PsiElement> elements = new ArrayList<PsiElement>();
            if (getter != null) {
              elements.addAll(Arrays.asList(SuperMethodWarningUtil.checkSuperMethods(getter, ACTION_STRING)));
            }
            if (setter != null) {
              elements.addAll(Arrays.asList(SuperMethodWarningUtil.checkSuperMethods(setter, ACTION_STRING)));
            }
            return elements.toArray(new PsiElement[elements.size()]);
          }
        }
      }
    }
    return super.getSecondaryElements();
  }

  @NotNull
  public FindUsagesOptions getFindUsagesOptions() {
    PsiElement element = getPsiElement();
    if (element instanceof PsiPackage) {
      return myFindPackageOptions;
    }
    if (element instanceof PsiClass) {
      return myFindClassOptions;
    }
    if (element instanceof PsiMethod) {
      return myFindMethodOptions;
    }
    if (element instanceof PsiVariable) {
      return myFindVariableOptions;
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return myFindThrowOptions;
    }
    return super.getFindUsagesOptions();
  }

  @Override
  protected String getStringToSearch(final PsiElement element) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        PsiElement norm = element;
        if (element instanceof PsiDirectory) {  // normalize a directory to a corresponding package
          norm = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
        }
        if (norm instanceof PsiPackage) {
          return ((PsiPackage)norm).getQualifiedName();
        }
        if (norm instanceof PsiClass) {
          return ((PsiClass)norm).getQualifiedName();
        }
        if (norm instanceof PsiMethod) {
          return ((PsiMethod)norm).getName();
        }
        if (norm instanceof PsiVariable) {
          return ((PsiVariable)norm).getName();
        }
        if (norm instanceof PsiMetaOwner) {
          final PsiMetaData metaData = ((PsiMetaOwner)norm).getMetaData();
          if (metaData != null) {
            return metaData.getName();
          }
        }
        if (norm instanceof PsiNamedElement) {
          return ((PsiNamedElement)norm).getName();
        }
        if (norm instanceof XmlAttributeValue) {
          return ((XmlAttributeValue)norm).getValue();
        }

        LOG.error("Unknown element type: " + element);
        return null;
      }
    });
  }

  @Override
  public void processElementUsages(@NotNull final PsiElement element, @NotNull final Processor<UsageInfo> processor, @NotNull final FindUsagesOptions options) {
    if (element instanceof PsiVariable){
      if (options.isReadAccess || options.isWriteAccess){
        if (options.isReadAccess && options.isWriteAccess){
          //todo[myakovlev] this also shows param in javadoc (PsiDocParamRef), but should not
          addElementUsages(element, processor, options);
        }
        else{
          addElementUsages(element, new Processor<UsageInfo>() {
            public boolean process(UsageInfo info) {
              final PsiElement element = info.getElement();
              boolean isWrite = element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element);
              if (isWrite == options.isWriteAccess) {
                if (!processor.process(info)) return false;
              }
              return true;
            }
          }, options);
        }
      }
    }
    else if (options.isUsages) {
      addElementUsages(element, processor, options);
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (ThrowSearchUtil.isSearchable (element) && options.isThrowUsages) {
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

    if (element instanceof PsiPackage && options.isClassesUsages){
      addClassesUsages((PsiPackage)element, processor, options);
    }

    if (element instanceof PsiClass && options.isMethodsUsages){
      addMethodsUsages((PsiClass)element, processor, options);
    }

    if (element instanceof PsiClass && options.isFieldsUsages){
      addFieldsUsages((PsiClass)element, processor, options);
    }

    if (element instanceof PsiClass){
      if (((PsiClass)element).isInterface()) {
        if (options.isDerivedInterfaces){
          if (options.isImplementingClasses){
            addInheritors((PsiClass)element, processor, options);
          }
          else{
            addDerivedInterfaces((PsiClass)element, processor, options);
          }
        }
        else if (options.isImplementingClasses){
          addImplementingClasses((PsiClass)element, processor, options);
        }
      }
      else if (options.isDerivedClasses) {
        addInheritors((PsiClass)element, processor, options);
      }
    }

    if (element instanceof PsiMethod){
      final PsiMethod psiMethod = (PsiMethod)element;
      boolean isAbstract = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return psiMethod.hasModifierProperty(PsiModifier.ABSTRACT);
        }
      });
      if (isAbstract && options.isImplementingMethods || options.isOverridingMethods) {
        processOverridingMethods(psiMethod, processor, options);
      }
    }

    if (!ThrowSearchUtil.isSearchable(element) && options.isSearchForTextOccurences && options.searchScope instanceof GlobalSearchScope) {
      processUsagesInText(element, processor, (GlobalSearchScope)options.searchScope);
    }
  }

  private static void processOverridingMethods(PsiMethod psiMethod, final Processor<UsageInfo> processor, final FindUsagesOptions options) {
    OverridingMethodsSearch.search(psiMethod, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiMethod>(
      new PsiElementProcessor<PsiMethod>() {
      public boolean execute(PsiMethod element) {
        addResult(processor, element.getNavigationElement(), options, null);
        return true;
      }
    }));
  }


  private static void addClassesUsages(PsiPackage aPackage, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    final HashSet<PsiFile> filesSet = new HashSet<PsiFile>();
    final ArrayList<PsiFile> files = new ArrayList<PsiFile>();
    ReferencesSearch.search(aPackage, options.searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
      public boolean processInReadAction(final PsiReference psiReference) {
        PsiElement ref = psiReference.getElement();
        PsiFile file = ref.getContainingFile();
        if (filesSet.add(file)) {
          files.add(file);
        }
        return true;
      }
    });

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null){
      progress.pushState();
    }

    ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
    addClassesInPackage(aPackage, options.isIncludeSubpackages, classes);
    for (final PsiClass aClass : classes) {
      if (progress != null) {
        progress.setText(FindBundle.message("find.searching.for.references.to.class.progress", aClass.getName()));
      }
      for (PsiFile file : files) {
        if (progress != null) {
          progress.checkCanceled();
        }
        ReferencesSearch.search(aClass, new LocalSearchScope(file), false).forEach(new ReadActionProcessor<PsiReference>() {
          public boolean processInReadAction(final PsiReference psiReference) {
            return addResult(results, psiReference, options, aClass);
          }
        });
      }
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
      public void run() {
        PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
        array.addAll(Arrays.asList(classes));
        if (includeSubdirs) {
          PsiDirectory[] dirs = dir.getSubdirectories();
          for (PsiDirectory directory : dirs) {
            addClassesInDirectory(directory, includeSubdirs, array);
          }
        }
      }
    });
  }

  private static void addMethodsUsages(final PsiClass aClass, final Processor<UsageInfo> results, final FindUsagesOptions options) {
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
            boolean strictSignatureSearch = !options.isIncludeOverloadUsages;
            MethodReferencesSearch.search(method, options.searchScope, strictSignatureSearch).forEach(new PsiReferenceProcessorAdapter(new PsiReferenceProcessor() {
                      public boolean execute(PsiReference reference) {
                        addResultFromReference(reference, methodClass, manager, aClass, results, options, method);
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

  private static void addFieldsUsages(final PsiClass aClass, final Processor<UsageInfo> results, final FindUsagesOptions options) {
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
          ReferencesSearch.search(field, options.searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
            public boolean processInReadAction(final PsiReference reference) {
              addResultFromReference(reference, fieldClass, manager, aClass, results, options, field);
              return true;
            }
          });
        }
      }
    }
    else {
      PsiField[] fields = ApplicationManager.getApplication().runReadAction(new Computable<PsiField[]>() {
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

  private static void addInheritors(final PsiClass aClass, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    ClassInheritorsSearch.search(aClass, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiClass>(
      new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        addResult(results, element, options, null);
        return true;
      }

    }));
  }

  private static void addDerivedInterfaces(PsiClass anInterface, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiClass>(
      new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass inheritor) {
        if (inheritor.isInterface()) {
          addResult(results, inheritor, options, null);
        }
        return true;
      }

    }));
  }

  private static void addImplementingClasses(PsiClass anInterface, final Processor<UsageInfo> results, final FindUsagesOptions options) {
    ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<PsiClass>(
      new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass inheritor) {
        if (!inheritor.isInterface()) {
          addResult(results, inheritor, options, null);
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
                                             final FindUsagesOptions options,
                                             final PsiElement element) {
    PsiElement refElement = reference.getElement();
    if (refElement instanceof PsiReferenceExpression) {
      PsiClass usedClass = getFieldOrMethodAccessedClass((PsiReferenceExpression)refElement, methodClass);
      if (usedClass != null) {
        if (manager.areElementsEquivalent(usedClass, aClass) || usedClass.isInheritor(aClass, true)) {
          addResult(results, refElement, options, element);
        }
      }
    }
  }

  public static void addElementUsages(final PsiElement element, final Processor<UsageInfo> result, final FindUsagesOptions options) {
    final SearchScope searchScope = options.searchScope;
    if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()){
      PsiMethod method = (PsiMethod)element;
      if (options.isIncludeOverloadUsages) {
        for (PsiMethod constructor : method.getContainingClass().getConstructors()) {
          addConstructorUsages(constructor, searchScope, result, options);
        }
      }
      else {
        addConstructorUsages(method, searchScope, result, options);
      }
      return;
    }

    final ReadActionProcessor<PsiReference> consumer = new ReadActionProcessor<PsiReference>() {
      public boolean processInReadAction(final PsiReference ref) {
        return addResult(result, ref, options, element);
      }
    };

    if (options.fastTrack != null) {
      SearchRequestor.collectRequests(element, options, options.fastTrack);
      return;
    }

    if (element instanceof PsiMethod) {
      MethodReferencesSearch.search((PsiMethod)element, searchScope, !options.isIncludeOverloadUsages).forEach(consumer);
    } else {
      ReferencesSearch.search(element, searchScope, false).forEach(consumer);
    }
  }

  private static void addConstructorUsages(PsiMethod method, SearchScope searchScope, final Processor<UsageInfo> result, final FindUsagesOptions options) {
    final PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return;

    ReferencesSearch.search(method, searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
      public boolean processInReadAction(final PsiReference ref) {
        return addResult(result, ref, options, parentClass);
      }
    });
  }

  public static void addResult(Processor<UsageInfo> total, PsiElement element, FindUsagesOptions options, PsiElement refElement) {
    if (filterUsage(element, options, refElement)){
      total.process(new UsageInfo(element));
    }
  }

  public static boolean addResult(Processor<UsageInfo> results, PsiReference ref, FindUsagesOptions options, PsiElement refElement) {
    if (filterUsage(ref.getElement(), options, refElement)){
      TextRange rangeInElement = ref.getRangeInElement();
      return results.process(new UsageInfo(ref.getElement(), rangeInElement.getStartOffset(), rangeInElement.getEndOffset(), false));
    }
    return true;
  }

  private static boolean filterUsage(PsiElement usage, FindUsagesOptions options, PsiElement refElement) {
    if (!(usage instanceof PsiJavaCodeReferenceElement)) {
      return true;
    }
    if (refElement instanceof PsiPackage && !options.isIncludeSubpackages &&
        ((PsiReference)usage).resolve() instanceof PsiPackage) {
      PsiElement parent = usage.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)parent).resolve() instanceof PsiPackage) {
        return false;
      }
    }

    if (!(usage instanceof PsiReferenceExpression)){
      if (options.isSkipImportStatements){
        PsiElement parent = usage.getParent();
        while(parent instanceof PsiJavaCodeReferenceElement){
          parent = parent.getParent();
        }
        if (parent instanceof PsiImportStatement){
          return false;
        }
      }

      if (options.isSkipPackageStatements){
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


  protected boolean isSearchForTextOccurencesAvailable(PsiElement psiElement, boolean isSingleFile) {
    if (isSingleFile) return false;
    if (psiElement instanceof PsiClass) {
      return ((PsiClass)psiElement).getQualifiedName() != null;
    }
    return psiElement instanceof PsiPackage;
  }

  public Collection<PsiReference> findReferencesToHighlight(final PsiElement target, final SearchScope searchScope) {
    if (target instanceof PsiMethod) {
      return MethodReferencesSearch.search((PsiMethod)target, searchScope, true).findAll();
    }
    return super.findReferencesToHighlight(target, searchScope);
  }

}
