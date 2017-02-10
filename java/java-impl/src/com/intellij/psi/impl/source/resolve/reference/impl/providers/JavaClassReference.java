/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixActionRegistrarImpl;
import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassKind;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class JavaClassReference extends GenericReference implements PsiJavaReference, LocalQuickFixProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference");
  protected final int myIndex;
  private TextRange myRange;
  private final String myText;
  private final boolean myInStaticImport;
  private final JavaClassReferenceSet myJavaClassReferenceSet;

  public JavaClassReference(final JavaClassReferenceSet referenceSet, TextRange range, int index, String text, final boolean staticImport) {
    super(referenceSet.getProvider());
    myInStaticImport = staticImport;
    LOG.assertTrue(range.getEndOffset() <= referenceSet.getElement().getTextLength());
    myIndex = index;
    myRange = range;
    myText = text;
    myJavaClassReferenceSet = referenceSet;
  }

  @Override
  @Nullable
  public PsiElement getContext() {
    final PsiReference contextRef = getContextReference();
    assert contextRef != this : getCanonicalText();
    return contextRef != null ? contextRef.resolve() : null;
  }

  @Override
  public void processVariants(@NotNull final PsiScopeProcessor processor) {
    if (processor instanceof JavaCompletionProcessor) {
      final Map<CustomizableReferenceProvider.CustomizationKey, Object> options = getOptions();
      if (options != null &&
          (JavaClassReferenceProvider.EXTEND_CLASS_NAMES.getValue(options) != null ||
           JavaClassReferenceProvider.NOT_INTERFACE.getBooleanValue(options) ||
           JavaClassReferenceProvider.CONCRETE.getBooleanValue(options)) ||
           JavaClassReferenceProvider.CLASS_KIND.getValue(options) != null) {
        ((JavaCompletionProcessor)processor).setCompletionElements(getVariants());
        return;
      }
    }

    PsiScopeProcessor processorToUse = processor;
    if (myInStaticImport) {
      // allows to complete members
      processor.handleEvent(JavaScopeProcessorEvent.CHANGE_LEVEL, null);
    }
    else {
      if (isDefinitelyStatic()) {
        processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
      }
      processorToUse = new PsiScopeProcessor() {
        @Override
        public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
          return !(element instanceof PsiClass || element instanceof PsiPackage) || processor.execute(element, state);
        }

        @Override
        public <V> V getHint(@NotNull Key<V> hintKey) {
          return processor.getHint(hintKey);
        }

        @Override
        public void handleEvent(@NotNull Event event, Object associated) {
          processor.handleEvent(event, associated);
        }
      };
    }
    super.processVariants(processorToUse);
  }

  private boolean isDefinitelyStatic() {
    final String s = getElement().getText();
    return isStaticClassReference(s, true);
  }

  private boolean isStaticClassReference(final String s, boolean strict) {
    if (myIndex == 0) {
      return false;
    }
    char c = s.charAt(getRangeInElement().getStartOffset() - 1);
    return myJavaClassReferenceSet.isStaticSeparator(c, strict);
  }

  @Override
  @Nullable
  public PsiReference getContextReference() {
    return myIndex > 0 ? myJavaClassReferenceSet.getReference(myIndex - 1) : null;
  }

  private boolean canReferencePackage() {
    return myJavaClassReferenceSet.canReferencePackage(myIndex);
  }

  @Override
  public PsiElement getElement() {
    return myJavaClassReferenceSet.getElement();
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return (element instanceof PsiClass || element instanceof PsiPackage) && super.isReferenceTo(element);
  }

  @Override
  public TextRange getRangeInElement() {
    return myRange;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myText;
  }

  @Override
  public boolean isSoft() {
    return myJavaClassReferenceSet.isSoft();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    final PsiElement element = manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName);
    myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
    return element;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return getElement();

    final String newName;
    if (element instanceof PsiClass) {
      newName = getQualifiedClassNameToInsert((PsiClass)element);
    }
    else if (element instanceof PsiPackage) {
      PsiPackage psiPackage = (PsiPackage)element;
      newName = psiPackage.getQualifiedName();
    }
    else {
      throw new IncorrectOperationException("Cannot bind to " + element);
    }
    assert newName != null;

    int end = getRangeInElement().getEndOffset();
    String text = getElement().getText();
    int lt = text.indexOf('<', getRangeInElement().getStartOffset());
    if (lt >= 0 && lt < end) {
      end = CharArrayUtil.shiftBackward(text, lt - 1, "\n\t ") + 1;
    }
    TextRange range = new TextRange(myJavaClassReferenceSet.getReference(0).getRangeInElement().getStartOffset(), end);
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    final PsiElement finalElement = manipulator.handleContentChange(getElement(), range, newName);
    myJavaClassReferenceSet.reparse(finalElement, TextRange.from(range.getStartOffset(), newName.length()));
    return finalElement;
  }

  private String getQualifiedClassNameToInsert(PsiClass psiClass) {
    final boolean jvmFormat = Boolean.TRUE.equals(JavaClassReferenceProvider.JVM_FORMAT.getValue(getOptions()));
    return jvmFormat ? ClassUtil.getJVMClassName(psiClass) : psiClass.getQualifiedName();
  }

  @Override
  public PsiElement resolveInner() {
    return advancedResolve(true).getElement();
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    PsiElement context = getCompletionContext();
    if (context instanceof PsiPackage) {
      return processPackage((PsiPackage)context);
    }
    if (context instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)context;

      if (myInStaticImport) {
        return ArrayUtil.mergeArrays(aClass.getInnerClasses(), aClass.getFields(), ArrayUtil.OBJECT_ARRAY_FACTORY);
      }
      else if (isDefinitelyStatic()) {
        final PsiClass[] psiClasses = aClass.getInnerClasses();
        final List<PsiClass> staticClasses = new ArrayList<>(psiClasses.length);

        for (PsiClass c : psiClasses) {
          if (c.hasModifierProperty(PsiModifier.STATIC)) {
            staticClasses.add(c);
          }
        }
        return staticClasses.isEmpty() ? PsiClass.EMPTY_ARRAY : staticClasses.toArray(new PsiClass[staticClasses.size()]);
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  public PsiElement getCompletionContext() {
    PsiElement context = getContext();
    return context == null ? JavaPsiFacade.getInstance(getElement().getProject()).findPackage("") : context;
  }

  public String[] getExtendClassNames() {
    return JavaClassReferenceProvider.EXTEND_CLASS_NAMES.getValue(getOptions());
  }

  @NotNull
  private LookupElement[] processPackage(@NotNull PsiPackage aPackage) {
    final ArrayList<LookupElement> list = ContainerUtil.newArrayList();
    final int startOffset = StringUtil.isEmpty(aPackage.getName()) ? 0 : aPackage.getQualifiedName().length() + 1;
    final GlobalSearchScope scope = getScope(getJavaContextFile());
    for (final PsiPackage subPackage : aPackage.getSubPackages(scope)) {
      final String shortName = subPackage.getQualifiedName().substring(startOffset);
      if (PsiNameHelper.getInstance(subPackage.getProject()).isIdentifier(shortName)) {
        list.add(LookupElementBuilder.create(subPackage).withIcon(subPackage.getIcon(Iconable.ICON_FLAG_VISIBILITY)));
      }
    }

    final List<PsiClass> classes = ContainerUtil.filter(aPackage.getClasses(scope), psiClass -> StringUtil.isNotEmpty(psiClass.getName()));
    final Map<CustomizableReferenceProvider.CustomizationKey, Object> options = getOptions();
    if (options != null) {
      final boolean instantiatable = JavaClassReferenceProvider.INSTANTIATABLE.getBooleanValue(options);
      final boolean concrete = JavaClassReferenceProvider.CONCRETE.getBooleanValue(options);
      final boolean notInterface = JavaClassReferenceProvider.NOT_INTERFACE.getBooleanValue(options);
      final boolean notEnum = JavaClassReferenceProvider.NOT_ENUM.getBooleanValue(options);
      final ClassKind classKind = getClassKind();

      for (PsiClass clazz : classes) {
        if (isClassAccepted(clazz, classKind, instantiatable, concrete, notInterface, notEnum)) {
          list.add(JavaClassNameCompletionContributor.createClassLookupItem(clazz, false));
        }
      }
    }
    else {
      for (PsiClass clazz : classes) {
        list.add(JavaClassNameCompletionContributor.createClassLookupItem(clazz, false));
      }
    }
    return list.toArray(new LookupElement[list.size()]);
  }

  @Nullable
  public ClassKind getClassKind() {
    return JavaClassReferenceProvider.CLASS_KIND.getValue(getOptions());
  }

  private static boolean isClassAccepted(final PsiClass clazz,
                                         @Nullable final ClassKind classKind,
                                         final boolean instantiatable,
                                         final boolean concrete,
                                         final boolean notInterface,
                                         final boolean notEnum) {
    if (classKind == ClassKind.ANNOTATION)  return clazz.isAnnotationType();
    if (classKind == ClassKind.ENUM) return clazz.isEnum();

    if (instantiatable) {
      if (PsiUtil.isInstantiatable(clazz)) {
        return true;
      }
    }
    else if (concrete) {
      if (!clazz.hasModifierProperty(PsiModifier.ABSTRACT) && !clazz.isInterface()) {
        return true;
      }
    }
    else if (notInterface) {
      if (!clazz.isInterface()) {
        return true;
      }
    }
    else if (notEnum) {
      if (!clazz.isEnum()) {
        return true;
      }
    }
    else {
      return true;
    }
    return false;
  }

  @Override
  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    PsiFile file = getJavaContextFile();
    final ResolveCache resolveCache = ResolveCache.getInstance(file.getProject());
    return (JavaResolveResult) resolveCache.resolveWithCaching(this, MyResolver.INSTANCE, false, false,file)[0];
  }

  private PsiFile getJavaContextFile() {
    return myJavaClassReferenceSet.getProvider().getContextFile(getElement());
  }

  @NotNull
  private JavaResolveResult doAdvancedResolve(@NotNull PsiFile containingFile) {
    final PsiElement psiElement = getElement();

    if (!psiElement.isValid()) return JavaResolveResult.EMPTY;

    final String elementText = psiElement.getText();

    final PsiElement context = getContext();
    if (context instanceof PsiClass) {
      if (isStaticClassReference(elementText, false)) {
        final PsiClass psiClass = ((PsiClass)context).findInnerClassByName(getCanonicalText(), false);
        if (psiClass != null) {
          return new ClassCandidateInfo(psiClass, PsiSubstitutor.EMPTY, false, psiElement);
        }
        PsiElement member = doResolveMember((PsiClass)context, myText);
        return member == null ? JavaResolveResult.EMPTY : new CandidateInfo(member, PsiSubstitutor.EMPTY, false, false, psiElement);
      }
      else if (!myInStaticImport && myJavaClassReferenceSet.isAllowDollarInNames()) {
        return JavaResolveResult.EMPTY;
      }
    }

    final int endOffset = getRangeInElement().getEndOffset();
    LOG.assertTrue(endOffset <= elementText.length(), elementText);
    final int startOffset = myJavaClassReferenceSet.getReference(0).getRangeInElement().getStartOffset();
    final String qName = elementText.substring(startOffset, endOffset);
    if (!qName.contains(".")) {
      final String defaultPackage = JavaClassReferenceProvider.DEFAULT_PACKAGE.getValue(getOptions());
      if (StringUtil.isNotEmpty(defaultPackage)) {
        final JavaResolveResult resolveResult = advancedResolveInner(psiElement, defaultPackage + "." + qName, containingFile);
        if (resolveResult != JavaResolveResult.EMPTY) {
          return resolveResult;
        }
      }
    }
    return advancedResolveInner(psiElement, qName, containingFile);
  }

  private JavaResolveResult advancedResolveInner(@NotNull PsiElement psiElement, @NotNull String qName, @NotNull PsiFile containingFile) {
    final PsiManager manager = containingFile.getManager();
    final GlobalSearchScope scope = getScope(containingFile);
    if (myIndex == myJavaClassReferenceSet.getReferences().length - 1) {
      final PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(qName, scope);
      if (aClass != null) {
        return new ClassCandidateInfo(aClass, PsiSubstitutor.EMPTY, false, psiElement);
      }
      else {
        if (!JavaClassReferenceProvider.ADVANCED_RESOLVE.getBooleanValue(getOptions())) {
          return JavaResolveResult.EMPTY;
        }
      }
    }
    PsiElement resolveResult = JavaPsiFacade.getInstance(manager.getProject()).findPackage(qName);
    if (resolveResult == null) {
      resolveResult = JavaPsiFacade.getInstance(manager.getProject()).findClass(qName, scope);
    }
    if (myInStaticImport && resolveResult == null) {
      resolveResult = resolveMember(qName, manager, getElement().getResolveScope());
    }
    if (resolveResult == null) {
      if (containingFile instanceof PsiJavaFile) {
        if (containingFile instanceof ServerPageFile) {
          containingFile = containingFile.getViewProvider().getPsi(JavaLanguage.INSTANCE);
          if (containingFile == null) return JavaResolveResult.EMPTY;
        }

        final ClassResolverProcessor processor = new ClassResolverProcessor(getCanonicalText(), psiElement, containingFile);
        PsiClass contextClass = myJavaClassReferenceSet.getProvider().getContextClass(psiElement);
        if (contextClass != null) {
          PsiScopesUtil.treeWalkUp(processor, contextClass, null);
        }
        else {
          containingFile.processDeclarations(processor, ResolveState.initial(), null, psiElement);
        }

        if (processor.getResult().length == 1) {
          final JavaResolveResult javaResolveResult = processor.getResult()[0];

          if (javaResolveResult != JavaResolveResult.EMPTY && getOptions() != null) {
            final Boolean value = JavaClassReferenceProvider.RESOLVE_QUALIFIED_CLASS_NAME.getValue(getOptions());
            final PsiClass psiClass = (PsiClass)javaResolveResult.getElement();
            if (value != null && value.booleanValue() && psiClass != null) {
              final String qualifiedName = psiClass.getQualifiedName();

              if (!qName.equals(qualifiedName)) {
                return JavaResolveResult.EMPTY;
              }
            }
          }

          return javaResolveResult;
        }
      }
    }
    return resolveResult != null
           ? new CandidateInfo(resolveResult, PsiSubstitutor.EMPTY, false, false, psiElement)
           : JavaResolveResult.EMPTY;
  }

  private GlobalSearchScope getScope(@NotNull PsiFile containingFile) {
    Project project = containingFile.getProject();
    GlobalSearchScope scope = myJavaClassReferenceSet.getProvider().getScope(project);
    if (scope == null) {
      Module module = ModuleUtilCore.findModuleForPsiElement(containingFile);
      return module == null ? GlobalSearchScope.allScope(project) : module.getModuleWithDependenciesAndLibrariesScope(true);
    }
    return scope;
  }

  @Nullable
  private Map<CustomizableReferenceProvider.CustomizationKey, Object> getOptions() {
    return myJavaClassReferenceSet.getOptions();
  }

  @Override
  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    final JavaResolveResult javaResolveResult = advancedResolve(incompleteCode);
    if (javaResolveResult.getElement() == null) return JavaResolveResult.EMPTY_ARRAY;
    return new JavaResolveResult[]{javaResolveResult};
  }

  @Nullable
  private List<? extends LocalQuickFix> registerFixes() {
    final List<LocalQuickFix> list = QuickFixFactory.getInstance().registerOrderEntryFixes(new QuickFixActionRegistrarImpl(null), this);

    final String[] extendClasses = getExtendClassNames();
    final String extendClass = extendClasses != null && extendClasses.length > 0 ? extendClasses[0] : null;

    final JavaClassReference[] references = getJavaClassReferenceSet().getAllReferences();
    PsiPackage contextPackage = null;
    for (int i = myIndex; i >= 0; i--) {
      final PsiElement context = references[i].getContext();
      if (context != null) {
        if (context instanceof PsiPackage) {
          contextPackage = (PsiPackage)context;
        }
        break;
      }
    }

    boolean createJavaClass = !canReferencePackage();
    ClassKind kind = createJavaClass ? getClassKind() : null;
    if (createJavaClass && kind == null) kind = ClassKind.CLASS;
    final String templateName = JavaClassReferenceProvider.CLASS_TEMPLATE.getValue(getOptions());
    final TextRange range = new TextRange(references[0].getRangeInElement().getStartOffset(),
                                          getRangeInElement().getEndOffset());
    final String qualifiedName = range.substring(getElement().getText());
    final CreateClassOrPackageFix action = CreateClassOrPackageFix.createFix(qualifiedName, getScope(getJavaContextFile()), getElement(), contextPackage,
                                                                             kind, extendClass, templateName);
    if (action != null) {
      if (list == null) {
        return Collections.singletonList(action);
      }
      else {
        final ArrayList<LocalQuickFix> fixes = new ArrayList<>(list.size() + 1);
        fixes.addAll(list);
        fixes.add(action);
        return fixes;
      }
    }
    return list;
  }

  public void processSubclassVariants(@NotNull PsiPackage context, @NotNull String[] extendClasses, Consumer<LookupElement> result) {
    GlobalSearchScope packageScope = PackageScope.packageScope(context, true);
    GlobalSearchScope scope = myJavaClassReferenceSet.getProvider().getScope(getElement().getProject());
    if (scope != null) {
      packageScope = packageScope.intersectWith(scope);
    }
    final GlobalSearchScope allScope = ProjectScope.getAllScope(context.getProject());
    final boolean instantiatable = JavaClassReferenceProvider.INSTANTIATABLE.getBooleanValue(getOptions());
    final boolean notInterface = JavaClassReferenceProvider.NOT_INTERFACE.getBooleanValue(getOptions());
    final boolean notEnum = JavaClassReferenceProvider.NOT_ENUM.getBooleanValue(getOptions());
    final boolean concrete = JavaClassReferenceProvider.CONCRETE.getBooleanValue(getOptions());

    final ClassKind classKind = getClassKind();

    for (String extendClassName : extendClasses) {
      final PsiClass extendClass = JavaPsiFacade.getInstance(context.getProject()).findClass(extendClassName, allScope);
      if (extendClass != null) {
        // add itself
        if (packageScope.contains(extendClass.getContainingFile().getVirtualFile())) {
          if (isClassAccepted(extendClass, classKind, instantiatable, concrete, notInterface, notEnum)) {
            result.consume(createSubclassLookupValue(extendClass));
          }
        }
        for (final PsiClass clazz : ClassInheritorsSearch.search(extendClass, packageScope, true)) {
          String qname = clazz.getQualifiedName();
          if (qname != null && isClassAccepted(clazz, classKind, instantiatable, concrete, notInterface, notEnum)) {
            result.consume(createSubclassLookupValue(clazz));
          }
        }
      }
    }
  }

  @NotNull
  private LookupElementBuilder createSubclassLookupValue(@NotNull PsiClass clazz) {
    return JavaLookupElementBuilder.forClass(clazz, getQualifiedClassNameToInsert(clazz), true)
      .withPresentableText(ObjectUtils.assertNotNull(clazz.getName()));
  }

  @Override
  public LocalQuickFix[] getQuickFixes() {
    final List<? extends LocalQuickFix> list = registerFixes();
    return list == null ? LocalQuickFix.EMPTY_ARRAY : list.toArray(new LocalQuickFix[list.size()]);
  }

  @Nullable
  public static PsiElement resolveMember(String fqn, PsiManager manager, GlobalSearchScope resolveScope) {
    PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(fqn, resolveScope);
    if (aClass != null) return aClass;
    int i = fqn.lastIndexOf('.');
    if (i == -1) return null;
    String memberName = fqn.substring(i + 1);
    fqn = fqn.substring(0, i);
    aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(fqn, resolveScope);
    if (aClass == null) return null;
    return doResolveMember(aClass, memberName);
  }

  @Nullable
  private static PsiElement doResolveMember(PsiClass aClass, String memberName) {
    PsiMember member = aClass.findFieldByName(memberName, true);
    if (member != null) return member;

    PsiMethod[] methods = aClass.findMethodsByName(memberName, true);
    return methods.length == 0 ? null : methods[0];
  }

  public JavaClassReferenceSet getJavaClassReferenceSet() {
    return myJavaClassReferenceSet;
  }

  @NotNull
  @Override
  public String getUnresolvedMessagePattern() {
    return myJavaClassReferenceSet.getUnresolvedMessagePattern(myIndex);
  }

  private static class MyResolver implements ResolveCache.PolyVariantContextResolver<JavaClassReference> {
    private static final MyResolver INSTANCE = new MyResolver();

    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull JavaClassReference ref, @NotNull PsiFile containingFile, boolean incompleteCode) {
      return new JavaResolveResult[]{ref.doAdvancedResolve(containingFile)};
    }
  }

}
