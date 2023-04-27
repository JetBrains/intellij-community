// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.reference.PsiMemberReference;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiPackageImpl;
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
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaClassReference extends GenericReference implements PsiJavaReference, LocalQuickFixProvider, PsiMemberReference {
  private static final Logger LOG = Logger.getInstance(JavaClassReference.class);
  protected final int myIndex;
  private TextRange myRange;
  @NotNull
  private final String myText;
  private final boolean myInStaticImport;
  private final JavaClassReferenceSet myJavaClassReferenceSet;

  public JavaClassReference(@NotNull JavaClassReferenceSet referenceSet, @NotNull TextRange range, int index, @NotNull String text, boolean staticImport) {
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
    PsiReference contextRef = getContextReference();
    assert contextRef != this : getCanonicalText();
    return contextRef != null ? contextRef.resolve() : null;
  }

  @Override
  public void processVariants(@NotNull PsiScopeProcessor processor) {
    if (processor instanceof JavaCompletionProcessor) {
      Map<CustomizableReferenceProvider.CustomizationKey, Object> options = getOptions();
      if (options != null &&
          (JavaClassReferenceProvider.SUPER_CLASSES.getValue(options) != null ||
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
    String s = getElement().getText();
    return isStaticClassReference(s, true);
  }

  private boolean isStaticClassReference(String s, boolean strict) {
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

  @NotNull
  @Override
  public PsiElement getElement() {
    return myJavaClassReferenceSet.getElement();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return (element instanceof PsiMember || element instanceof PsiPackage) && super.isReferenceTo(element);
  }

  @NotNull
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
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    TextRange rangeInElement = getRangeInElement();
    PsiElement element = manipulator.handleContentChange(getElement(), rangeInElement, newElementName);
    myRange = new TextRange(rangeInElement.getStartOffset(), rangeInElement.getStartOffset() + newElementName.length());
    return element;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return getElement();

    String newName;
    if (element instanceof PsiClass) {
      newName = getQualifiedClassNameToInsert((PsiClass)element);
    }
    else if (element instanceof PsiPackage psiPackage) {
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
    ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    PsiElement finalElement = manipulator.handleContentChange(getElement(), range, newName);
    myJavaClassReferenceSet.reparse(finalElement, TextRange.from(range.getStartOffset(), newName.length()));
    return finalElement;
  }

  private String getQualifiedClassNameToInsert(PsiClass psiClass) {
    boolean jvmFormat = Boolean.TRUE.equals(JavaClassReferenceProvider.JVM_FORMAT.getValue(getOptions()));
    return jvmFormat ? ClassUtil.getJVMClassName(psiClass) : psiClass.getQualifiedName();
  }

  @Override
  public PsiElement resolveInner() {
    return advancedResolve(true).getElement();
  }

  @Override
  public Object @NotNull [] getVariants() {
    List<Object> result = new ArrayList<>();
    for (PsiElement context : getCompletionContexts()) {
      if (context instanceof PsiPackage) {
        result.addAll(processPackage((PsiPackage)context));
      }
      else if (context instanceof PsiClass) {
        if (myInStaticImport) {
          Collections.addAll(result, ((PsiClass)context).getInnerClasses());
          Collections.addAll(result, ((PsiClass)context).getFields());
        }
        else if (isDefinitelyStatic()) {
          result.addAll(ContainerUtil.filter(((PsiClass)context).getInnerClasses(), c -> c.hasModifierProperty(PsiModifier.STATIC)));
        }
      }
    }
    return result.toArray();
  }

  private List<? extends PsiElement> getCompletionContexts() {
    List<PsiElement> result = new ArrayList<>();

    ContainerUtil.addIfNotNull(result, getCompletionContext());

    List<String> imports = JavaClassReferenceProvider.IMPORTS.getValue(getOptions());
    if (imports != null && getContextReference() == null) {
      result.addAll(ContainerUtil.mapNotNull(imports, JavaPsiFacade.getInstance(getElement().getProject())::findPackage));
    }

    return result;
  }

  @Nullable
  public PsiElement getCompletionContext() {
    PsiReference contextRef = getContextReference();
    if (contextRef == null) {
      return JavaPsiFacade.getInstance(getElement().getProject()).findPackage("");
    }
    return contextRef.resolve();
  }

  @NotNull
  public List<String> getSuperClasses() {
    List<String> values = JavaClassReferenceProvider.SUPER_CLASSES.getValue(getOptions());
    return values == null ? Collections.emptyList() : values;
  }

  @NotNull
  private List<LookupElement> processPackage(@NotNull PsiPackage aPackage) {
    ArrayList<LookupElement> list = new ArrayList<>();
    int startOffset = StringUtil.isEmpty(aPackage.getName()) ? 0 : aPackage.getQualifiedName().length() + 1;
    GlobalSearchScope scope = getScope(getJavaContextFile());
    for (PsiPackage subPackage : aPackage.getSubPackages(scope)) {
      String shortName = subPackage.getQualifiedName().substring(startOffset);
      if (PsiNameHelper.getInstance(subPackage.getProject()).isIdentifier(shortName)) {
        list.add(LookupElementBuilder.create(subPackage).withIcon(subPackage.getIcon(Iconable.ICON_FLAG_VISIBILITY)));
      }
    }

    List<PsiClass> classes = ContainerUtil.filter(aPackage.getClasses(scope), psiClass -> StringUtil.isNotEmpty(psiClass.getName()));
    Map<CustomizableReferenceProvider.CustomizationKey, Object> options = getOptions();
    if (options != null) {
      boolean instantiatable = JavaClassReferenceProvider.INSTANTIATABLE.getBooleanValue(options);
      boolean concrete = JavaClassReferenceProvider.CONCRETE.getBooleanValue(options);
      boolean notInterface = JavaClassReferenceProvider.NOT_INTERFACE.getBooleanValue(options);
      boolean notEnum = JavaClassReferenceProvider.NOT_ENUM.getBooleanValue(options);
      ClassKind classKind = getClassKind();

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
    return list;
  }

  @Nullable
  public ClassKind getClassKind() {
    return JavaClassReferenceProvider.CLASS_KIND.getValue(getOptions());
  }

  private static boolean isClassAccepted(PsiClass clazz,
                                         @Nullable ClassKind classKind,
                                         boolean instantiatable,
                                         boolean concrete,
                                         boolean notInterface,
                                         boolean notEnum) {
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
    ResolveCache resolveCache = ResolveCache.getInstance(file.getProject());
    return (JavaResolveResult) resolveCache.resolveWithCaching(this, MyResolver.INSTANCE, false, false,file)[0];
  }

  private @NotNull PsiFile getJavaContextFile() {
    return myJavaClassReferenceSet.getProvider().getContextFile(getElement());
  }

  @NotNull
  private JavaResolveResult doAdvancedResolve(@NotNull PsiFile containingFile) {
    PsiElement psiElement = getElement();

    if (!psiElement.isValid()) return JavaResolveResult.EMPTY;

    String elementText = psiElement.getText();

    PsiElement context = getContext();
    if (context instanceof PsiClass) {
      if (isStaticClassReference(elementText, false)) {
        PsiClass psiClass = ((PsiClass)context).findInnerClassByName(getCanonicalText(), false);
        if (psiClass != null) {
          return new ClassCandidateInfo(psiClass, PsiSubstitutor.EMPTY, false, psiElement);
        }
        PsiElement member = doResolveMember((PsiClass)context, myText);
        return member == null ? JavaResolveResult.EMPTY : new CandidateInfo(member, PsiSubstitutor.EMPTY, false, false, psiElement);
      }
      if (!myInStaticImport && myJavaClassReferenceSet.isAllowDollarInNames()) {
        return JavaResolveResult.EMPTY;
      }
    }

    TextRange rangeInElement = getRangeInElement();
    int endOffset = rangeInElement.getEndOffset();
    if (endOffset > elementText.length()) {
      LOG.error(elementText+": rangeInElement="+rangeInElement+"; "+getClass());
    }
    int startOffset = myJavaClassReferenceSet.getReference(0).getRangeInElement().getStartOffset();
    String qName = elementText.substring(startOffset, endOffset);
    if (!qName.contains(".")) {
      JavaResolveResult viaImports = JBIterable.from(JavaClassReferenceProvider.IMPORTS.getValue(getOptions()))
        .map(o -> o == null ? JavaResolveResult.EMPTY : advancedResolveInner(psiElement, o + "." + qName, containingFile))
        .find(o -> o != JavaResolveResult.EMPTY);
      if (viaImports != null) {
        return viaImports;
      }
    }
    return advancedResolveInner(psiElement, qName, containingFile);
  }

  private JavaResolveResult advancedResolveInner(@NotNull PsiElement psiElement, @NotNull String qName, @NotNull PsiFile containingFile) {
    PsiManager manager = containingFile.getManager();
    GlobalSearchScope scope = getScope(containingFile);
    if (myIndex == myJavaClassReferenceSet.getReferences().length - 1) {
      PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(qName, scope);
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

        ClassResolverProcessor processor = new ClassResolverProcessor(getCanonicalText(), psiElement, containingFile);
        PsiClass contextClass = myJavaClassReferenceSet.getProvider().getContextClass(psiElement);
        if (contextClass != null) {
          PsiScopesUtil.treeWalkUp(processor, contextClass, null);
        }
        else {
          containingFile.processDeclarations(processor, ResolveState.initial(), null, psiElement);
        }

        if (processor.getResult().length == 1) {
          JavaResolveResult javaResolveResult = processor.getResult()[0];

          if (javaResolveResult != JavaResolveResult.EMPTY && getOptions() != null) {
            Boolean value = JavaClassReferenceProvider.RESOLVE_QUALIFIED_CLASS_NAME.getValue(getOptions());
            PsiClass psiClass = (PsiClass)javaResolveResult.getElement();
            if (value != null && value.booleanValue() && psiClass != null) {
              String qualifiedName = psiClass.getQualifiedName();

              if (!qName.equals(qualifiedName)) {
                return JavaResolveResult.EMPTY;
              }
            }
          }

          return javaResolveResult;
        }
      }
    }
    if (resolveResult instanceof PsiPackageImpl && !((PsiPackageImpl)resolveResult).mayHaveContentInScope(scope)) {
      return JavaResolveResult.EMPTY;
    }
    return resolveResult != null
           ? new CandidateInfo(resolveResult, PsiSubstitutor.EMPTY, false, false, psiElement)
           : JavaResolveResult.EMPTY;
  }

  @NotNull
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
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    JavaResolveResult javaResolveResult = advancedResolve(incompleteCode);
    if (javaResolveResult.getElement() == null) return JavaResolveResult.EMPTY_ARRAY;
    return new JavaResolveResult[]{javaResolveResult};
  }

  @NotNull
  private List<? extends @NotNull LocalQuickFix> registerFixes() {
    List<LocalQuickFix> list = QuickFixFactory.getInstance().registerOrderEntryFixes(this, new ArrayList<>());

    String extendClass = ContainerUtil.getFirstItem(getSuperClasses());

    JavaClassReference[] references = getJavaClassReferenceSet().getAllReferences();
    PsiPackage contextPackage = null;
    for (int i = myIndex; i >= 0; i--) {
      PsiElement context = references[i].getContext();
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
    String templateName = JavaClassReferenceProvider.CLASS_TEMPLATE.getValue(getOptions());
    TextRange range = new TextRange(references[0].getRangeInElement().getStartOffset(),
                                          getRangeInElement().getEndOffset());
    String qualifiedName = range.substring(getElement().getText());
    CreateClassOrPackageFix action = CreateClassOrPackageFix.createFix(qualifiedName, getScope(getJavaContextFile()), getElement(), contextPackage,
                                                                             kind, extendClass, templateName);
    if (action != null) {
      list = ContainerUtil.append(list, action);
    }
    return list;
  }

  public void processSubclassVariants(@NotNull PsiPackage context, String @NotNull [] extendClasses, Consumer<? super LookupElement> result) {
    GlobalSearchScope packageScope = PackageScope.packageScope(context, true);
    GlobalSearchScope scope = myJavaClassReferenceSet.getProvider().getScope(getElement().getProject());
    if (scope != null) {
      packageScope = packageScope.intersectWith(scope);
    }
    GlobalSearchScope allScope = ProjectScope.getAllScope(context.getProject());
    boolean instantiatable = JavaClassReferenceProvider.INSTANTIATABLE.getBooleanValue(getOptions());
    boolean notInterface = JavaClassReferenceProvider.NOT_INTERFACE.getBooleanValue(getOptions());
    boolean notEnum = JavaClassReferenceProvider.NOT_ENUM.getBooleanValue(getOptions());
    boolean concrete = JavaClassReferenceProvider.CONCRETE.getBooleanValue(getOptions());

    ClassKind classKind = getClassKind();

    for (String extendClassName : extendClasses) {
      PsiClass extendClass = JavaPsiFacade.getInstance(context.getProject()).findClass(extendClassName, allScope);
      if (extendClass != null) {
        // add itself
        if (packageScope.contains(extendClass.getContainingFile().getVirtualFile())) {
          if (isClassAccepted(extendClass, classKind, instantiatable, concrete, notInterface, notEnum)) {
            result.consume(createSubclassLookupValue(extendClass));
          }
        }
        for (PsiClass clazz : ClassInheritorsSearch.search(extendClass, packageScope, true)) {
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
      .withPresentableText(Objects.requireNonNull(clazz.getName()));
  }

  @Override
  public @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
    List<? extends @NotNull LocalQuickFix> list = registerFixes();
    return list.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Nullable
  public static PsiElement resolveMember(@NotNull String fqn, @NotNull PsiManager manager, GlobalSearchScope resolveScope) {
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
  private static PsiElement doResolveMember(@NotNull PsiClass aClass, @NotNull String memberName) {
    PsiMember member = aClass.findFieldByName(memberName, true);
    if (member != null) return member;

    PsiMethod[] methods = aClass.findMethodsByName(memberName, true);
    return methods.length == 0 ? null : methods[0];
  }

  @NotNull
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

    @Override
    public ResolveResult @NotNull [] resolve(@NotNull JavaClassReference ref, @NotNull PsiFile containingFile, boolean incompleteCode) {
      return new JavaResolveResult[]{ref.doAdvancedResolve(containingFile)};
    }
  }

  @Override
  public String toString() {
    return getClass().getName() + "(" + getRangeInElement() + ", provider=" + myJavaClassReferenceSet.getProvider() + "}";
  }
}
