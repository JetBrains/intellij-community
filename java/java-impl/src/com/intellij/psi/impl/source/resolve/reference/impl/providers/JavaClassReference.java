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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixActionRegistrarImpl;
import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInsight.lookup.LookupElementFactoryImpl;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassKind;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class JavaClassReference extends GenericReference implements PsiJavaReference, QuickFixProvider, LocalQuickFixProvider {
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

  @Nullable
  public PsiElement getContext() {
    final PsiReference contextRef = getContextReference();
    return contextRef != null ? contextRef.resolve() : null;
  }

  public void processVariants(final PsiScopeProcessor processor) {
    if (processor instanceof JavaCompletionProcessor) {
      final Map<CustomizableReferenceProvider.CustomizationKey, Object> options = getOptions();
      if (options != null &&
          (JavaClassReferenceProvider.EXTEND_CLASS_NAMES.getValue(options) != null ||
           JavaClassReferenceProvider.NOT_INTERFACE.getBooleanValue(options) ||
           JavaClassReferenceProvider.CONCRETE.getBooleanValue(options))) {
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
        public boolean execute(PsiElement element, ResolveState state) {
          return !(element instanceof PsiClass || element instanceof PsiPackage) || processor.execute(element, state);
        }

        public <V> V getHint(Key<V> hintKey) {
          return processor.getHint(hintKey);
        }

        public void handleEvent(Event event, Object associated) {
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

  @Nullable
  public PsiReference getContextReference() {
    return myIndex > 0 ? myJavaClassReferenceSet.getReference(myIndex - 1) : null;
  }

  private boolean canReferencePackage() {
    return myJavaClassReferenceSet.canReferencePackage(myIndex);
  }

  public PsiElement getElement() {
    return myJavaClassReferenceSet.getElement();
  }

  public boolean isReferenceTo(PsiElement element) {
    return (element instanceof PsiClass || element instanceof PsiPackage) && super.isReferenceTo(element);
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  @NotNull
  public String getCanonicalText() {
    return myText;
  }

  public boolean isSoft() {
    return myJavaClassReferenceSet.isSoft();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    if (manipulator != null) {
      final PsiElement element = manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName);
      myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
      return element;
    }
    throw new IncorrectOperationException("Manipulator for this element is not defined: " + getElement());
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return getElement();

    final String newName;
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)element;
      final boolean jvmFormat = Boolean.TRUE.equals(JavaClassReferenceProvider.JVM_FORMAT.getValue(getOptions()));
      newName = jvmFormat ? ClassUtil.getJVMClassName(psiClass) : psiClass.getQualifiedName();
    }
    else if (element instanceof PsiPackage) {
      PsiPackage psiPackage = (PsiPackage)element;
      newName = psiPackage.getQualifiedName();
    }
    else {
      throw new IncorrectOperationException("Cannot bind to " + element);
    }
    assert newName != null;

    TextRange range =
        new TextRange(myJavaClassReferenceSet.getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    if (manipulator != null) {
      final PsiElement finalElement = manipulator.handleContentChange(getElement(), range, newName);
      range = new TextRange(range.getStartOffset(), range.getStartOffset() + newName.length());
      myJavaClassReferenceSet.reparse(finalElement, range);
      return finalElement;
    }
    return element;
  }

  public PsiElement resolveInner() {
    return advancedResolve(true).getElement();
  }

  @NotNull
  public Object[] getVariants() {
    PsiElement context = getContext();
    if (context == null) {
      context = JavaPsiFacade.getInstance(getElement().getProject()).findPackage("");
    }
    if (context instanceof PsiPackage) {
      final String[] extendClasses = getExtendClassNames();
      if (extendClasses != null) {
        return getSubclassVariants((PsiPackage)context, extendClasses);
      }
      return processPackage((PsiPackage)context);
    }
    if (context instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)context;

      if (myInStaticImport) {
        return ArrayUtil.mergeArrays(aClass.getInnerClasses(), aClass.getFields(), Object.class);
      }
      else if (isDefinitelyStatic()) {
        final PsiClass[] psiClasses = aClass.getInnerClasses();
        final List<PsiClass> staticClasses = new ArrayList<PsiClass>(psiClasses.length);

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

  public String[] getExtendClassNames() {
    return JavaClassReferenceProvider.EXTEND_CLASS_NAMES.getValue(getOptions());
  }

  private Object[] processPackage(final PsiPackage aPackage) {
    final ArrayList<Object> list = new ArrayList<Object>();
    final int startOffset = StringUtil.isEmpty(aPackage.getName()) ? 0 : aPackage.getQualifiedName().length() + 1;
    final GlobalSearchScope scope = getScope();
    for (final PsiPackage subPackage : aPackage.getSubPackages(scope)) {
      final String shortName = subPackage.getQualifiedName().substring(startOffset);
      if (JavaPsiFacade.getInstance(subPackage.getProject()).getNameHelper().isIdentifier(shortName)) {
        list.add(subPackage);
      }
    }

    final PsiClass[] classes = aPackage.getClasses(scope);
    final Map<CustomizableReferenceProvider.CustomizationKey, Object> options = getOptions();
    if (options != null) {
      final boolean instantiatable = JavaClassReferenceProvider.INSTANTIATABLE.getBooleanValue(options);
      final boolean concrete = JavaClassReferenceProvider.CONCRETE.getBooleanValue(options);
      final boolean notInterface = JavaClassReferenceProvider.NOT_INTERFACE.getBooleanValue(options);
      final boolean notEnum = JavaClassReferenceProvider.NOT_ENUM.getBooleanValue(options);
      for (PsiClass clazz : classes) {
        if (isClassAccepted(clazz, instantiatable, concrete, notInterface, notEnum)) {
          list.add(clazz);
        }
      }
    }
    else {
      ContainerUtil.addAll(list, classes);
    }
    return list.toArray();
  }

  private static boolean isClassAccepted(final PsiClass clazz,
                                         final boolean instantiatable,
                                         final boolean concrete,
                                         final boolean notInterface,
                                         final boolean notEnum) {
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

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final PsiManager manager = getElement().getManager();
    if(manager instanceof PsiManagerImpl){
      return (JavaResolveResult)((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, MyResolver.INSTANCE, false, false)[0];
    }
    return doAdvancedResolve();
  }

  private JavaResolveResult doAdvancedResolve() {
    final PsiElement psiElement = getElement();

    if (!psiElement.isValid()) return JavaResolveResult.EMPTY;

    final String elementText = psiElement.getText();

    final PsiElement context = getContext();
    if (context instanceof PsiClass) {
      if (isStaticClassReference(elementText, false)) {
        final PsiClass psiClass = ((PsiClass)context).findInnerClassByName(getCanonicalText(), false);
        if (psiClass != null) return new ClassCandidateInfo(psiClass, PsiSubstitutor.EMPTY, false, psiElement);
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
        final JavaResolveResult resolveResult = advancedResolveInner(psiElement, defaultPackage + "." + qName);
        if (resolveResult != JavaResolveResult.EMPTY) {
          return resolveResult;
        }
      }
    }
    return advancedResolveInner(psiElement, qName);
  }

  private JavaResolveResult advancedResolveInner(final PsiElement psiElement, final String qName) {
    final PsiManager manager = psiElement.getManager();
    final GlobalSearchScope scope = getScope();
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
      PsiFile containingFile = psiElement.getContainingFile();

      if (containingFile instanceof PsiJavaFile) {
        if (containingFile instanceof JspFile) {
          containingFile = containingFile.getViewProvider().getPsi(StdLanguages.JAVA);
          if (containingFile == null) return JavaResolveResult.EMPTY;
        }

        final ClassResolverProcessor processor = new ClassResolverProcessor(getCanonicalText(), psiElement);
        containingFile.processDeclarations(processor, ResolveState.initial(), null, psiElement);

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

  private GlobalSearchScope getScope() {
    final GlobalSearchScope scope = myJavaClassReferenceSet.getProvider().getScope();
    if (scope == null) {
      final Module module = ModuleUtil.findModuleForPsiElement(getElement());
      if (module != null) {
        return module.getModuleWithDependenciesAndLibrariesScope(true);
      }
      return GlobalSearchScope.allScope(getElement().getProject());
    }
    return scope;
  }

  @Nullable
  private Map<CustomizableReferenceProvider.CustomizationKey, Object> getOptions() {
    return myJavaClassReferenceSet.getOptions();
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    final JavaResolveResult javaResolveResult = advancedResolve(incompleteCode);
    if (javaResolveResult.getElement() == null) return JavaResolveResult.EMPTY_ARRAY;
    return new JavaResolveResult[]{javaResolveResult};
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    registerFixes(info);
  }

  @Nullable
  private List<? extends LocalQuickFix> registerFixes(final HighlightInfo info) {

    final List<LocalQuickFix> list = OrderEntryFix.registerFixes(new QuickFixActionRegistrarImpl(info), this);

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
    ClassKind kind = createJavaClass ? JavaClassReferenceProvider.CLASS_KIND.getValue(getOptions()) : null;
    if (createJavaClass && kind == null) kind = ClassKind.CLASS;
    final String templateName = JavaClassReferenceProvider.CLASS_TEMPLATE.getValue(getOptions());
    final TextRange range = new TextRange(references[0].getRangeInElement().getStartOffset(),
                                          getRangeInElement().getEndOffset());
    final String qualifiedName = range.substring(getElement().getText());
    final CreateClassOrPackageFix action = CreateClassOrPackageFix.createFix(qualifiedName, getScope(), getElement(), contextPackage, 
                                                                             kind, extendClass, templateName);
    if (action != null) {
      QuickFixAction.registerQuickFixAction(info, action);
      if (list == null) {
        return Arrays.asList(action);
      }
      else {
        final ArrayList<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>(list.size() + 1);
        fixes.addAll(list);
        fixes.add(action);
        return fixes;
      }
    }
    return list;
  }

  @NotNull
  private Object[] getSubclassVariants(@NotNull PsiPackage context, @NotNull String[] extendClasses) {
    HashSet<Object> lookups = new HashSet<Object>();
    GlobalSearchScope packageScope = PackageScope.packageScope(context, true);
    GlobalSearchScope scope = myJavaClassReferenceSet.getProvider().getScope();
    if (scope != null) {
      packageScope = packageScope.intersectWith(scope);
    }
    final GlobalSearchScope allScope = ProjectScope.getAllScope(context.getProject());
    final boolean instantiatable = JavaClassReferenceProvider.INSTANTIATABLE.getBooleanValue(getOptions());
    final boolean notInterface = JavaClassReferenceProvider.NOT_INTERFACE.getBooleanValue(getOptions());
    final boolean notEnum = JavaClassReferenceProvider.NOT_ENUM.getBooleanValue(getOptions());
    final boolean concrete = JavaClassReferenceProvider.CONCRETE.getBooleanValue(getOptions());

    for (String extendClassName : extendClasses) {
      final PsiClass extendClass = JavaPsiFacade.getInstance(context.getProject()).findClass(extendClassName, allScope);
      if (extendClass != null) {
        // add itself
        if (packageScope.contains(extendClass.getContainingFile().getVirtualFile())) {
          if (isClassAccepted(extendClass, instantiatable, concrete, notInterface, notEnum)) {
            ContainerUtil.addIfNotNull(createSubclassLookupValue(context, extendClass), lookups);
          }
        }
        for (final PsiClass clazz : ClassInheritorsSearch.search(extendClass, packageScope, true)) {
          if (isClassAccepted(clazz, instantiatable, concrete, notInterface, notEnum)) {
            ContainerUtil.addIfNotNull(createSubclassLookupValue(context, clazz), lookups);
          }
        }
      }
    }
    return lookups.toArray();
  }

  @Nullable
  private static Object createSubclassLookupValue(@NotNull final PsiPackage context, @NotNull final PsiClass clazz) {
    String name = clazz.getQualifiedName();
    if (name == null) return null;
    final String pack = context.getQualifiedName();
    if (pack.length() > 0) {
      if (name.startsWith(pack)) {
        name = name.substring(pack.length() + 1);
      }
      else {
        return null;
      }
    }
    final LookupItem<PsiClass> lookup = LookupElementFactoryImpl.getInstance().createLookupElement(clazz, name);
    lookup.addLookupStrings(clazz.getName());
    return JavaCompletionUtil.setShowFQN(lookup).setTailType(TailType.NONE);
  }

  public LocalQuickFix[] getQuickFixes() {
    final List<? extends LocalQuickFix> list = registerFixes(null);
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

  public String getUnresolvedMessagePattern() {
    return myJavaClassReferenceSet.getUnresolvedMessagePattern(myIndex);
  }

  private static class MyResolver implements ResolveCache.PolyVariantResolver<JavaClassReference> {
    private static final MyResolver INSTANCE = new MyResolver();

    public JavaResolveResult[] resolve(JavaClassReference javaClassReference, boolean incompleteCode) {
      return new JavaResolveResult[]{javaClassReference.doAdvancedResolve()};
    }
  }

}
