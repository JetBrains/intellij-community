// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.SymbolCollectingProcessor;
import com.intellij.psi.impl.source.resolve.SymbolCollectingProcessor.ResultWithContext;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.scope.*;
import com.intellij.psi.scope.processor.MethodsProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public abstract class PsiJavaFileBaseImpl extends PsiFileImpl implements PsiJavaFile {
  private static final Logger LOG = Logger.getInstance(PsiJavaFileBaseImpl.class);
  private static final String[] IMPLICIT_IMPORTS = { CommonClassNames.DEFAULT_PACKAGE };

  private final CachedValue<MostlySingularMultiMap<String, ResultWithContext>> myResolveCache;
  private final CachedValue<Map<String, Iterable<ResultWithContext>>> myCachedDeclarations;
  private final CachedValue<ImplicitlyImportedElement[]> myCachedImplicitImportedElements;
  private volatile String myPackageName;

  protected PsiJavaFileBaseImpl(@NotNull IElementType elementType, @NotNull IElementType contentElementType, @NotNull FileViewProvider viewProvider) {
    super(elementType, contentElementType, viewProvider);

    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(myManager.getProject());
    myResolveCache = cachedValuesManager.createCachedValue(new MyCacheBuilder(this), false);
    myCachedDeclarations = cachedValuesManager.createCachedValue(() -> {
      Map<String, Iterable<ResultWithContext>> declarations = findEnumeratedDeclarations();
      if (!this.isPhysical()) {
        return Result.create(declarations, this.getContainingFile(), PsiModificationTracker.MODIFICATION_COUNT);
      }
      return Result.create(declarations, PsiModificationTracker.MODIFICATION_COUNT);
    }, false);
    myCachedImplicitImportedElements = cachedValuesManager.createCachedValue(() -> {
      return Result.create(PsiImplUtil.getImplicitImports(this), this.getContainingFile());
    }, false);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myPackageName = null;
  }

  @Override
  public PsiClass @NotNull [] getClasses() {
    return withGreenStubOrAst(
      stub -> stub.getChildrenByType(Constants.CLASS_BIT_SET, PsiClass.ARRAY_FACTORY),
      ast -> ast.getChildrenAsPsiElements(Constants.CLASS_BIT_SET, PsiClass.ARRAY_FACTORY)
    );
  }

  @Override
  public PsiPackageStatement getPackageStatement() {
    return withGreenStubOrAst(stub -> {
      StubElement<?> element = stub.findChildStubByElementType(JavaElementType.PACKAGE_STATEMENT);
      return element == null ? null : (PsiPackageStatement)element.getPsi();
    }, file -> {
      ASTNode childNode = file.findChildByType(JavaElementType.PACKAGE_STATEMENT);
      return childNode == null ? null : (PsiPackageStatement)childNode.getPsi();
    });
  }

  @Override
  public @NotNull String getPackageName() {
    return withGreenStubOrAst(
      PsiJavaFileStub.class,
      stub -> stub.getPackageName(),
      ast -> {
        String name = myPackageName;
        if (name == null) {
          PsiPackageStatement statement = getPackageStatement();
          myPackageName = name = statement == null ? "" : statement.getPackageName();
        }
        return name;
      }
    );
  }

  @Override
  public void setPackageName(@NotNull String packageName) throws IncorrectOperationException {
    if (PsiUtil.isModuleFile(this)) {
      throw new IncorrectOperationException("Cannot set package name for module declarations");
    }

    PsiPackageStatement packageStatement = getPackageStatement();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    if (packageStatement != null) {
      if (!packageName.isEmpty()) {
        PsiJavaCodeReferenceElement reference = packageStatement.getPackageReference();
        reference.replace(factory.createPackageStatement(packageName).getPackageReference());
      }
      else {
        packageStatement.delete();
      }
    }
    else if (!packageName.isEmpty()) {
      cleanupBrokenPackageKeyword();
      PsiElement anchor = getFirstChild();
      if (PsiPackage.PACKAGE_INFO_FILE.equals(getName())) {
        // If javadoc is already present in a package-info.java file, try to position the new package statement after it,
        // so the package becomes documented.
        anchor = getImportList();
        assert anchor != null; // import list always available inside package-info.java
        PsiElement prev = anchor.getPrevSibling();
        if (prev instanceof PsiComment) {
          String text = prev.getText().trim();
          if (text.startsWith("/*") && !text.endsWith("*/")) {
            // close any open javadoc/comments before the import list
            prev.replace(factory.createCommentFromText(text + (StringUtil.containsLineBreak(text) ? "\n*/" : " */"), prev));
          }
        }
      }
      addBefore(factory.createPackageStatement(packageName), anchor);
    }
  }

  private void cleanupBrokenPackageKeyword() {
    PsiElement child = getFirstChild();
    while (child instanceof PsiWhiteSpace || child instanceof PsiComment || child instanceof PsiErrorElement) {
      if (child instanceof PsiErrorElement && child.getFirstChild() != null && child.getFirstChild().textMatches(JavaKeywords.PACKAGE)) {
        child.delete();
        break;
      }
      child = child.getNextSibling();
    }
  }

  @Override
  public PsiImportList getImportList() {
    return withGreenStubOrAst(
      stub -> {
        PsiImportList[] nodes = stub.getChildrenByType(JavaStubElementTypes.IMPORT_LIST, PsiImportList.ARRAY_FACTORY);
        if (nodes.length == 1) return nodes[0];
        assert nodes.length == 0;
        return null;
      },
      ast -> {
        ASTNode node = ast.findChildByType(JavaElementType.IMPORT_LIST);
        return (PsiImportList)SourceTreeToPsiMap.treeElementToPsi(node);
      }
    );
  }

  @Override
  public PsiElement @NotNull [] getOnDemandImports(boolean includeImplicit, boolean checkIncludes) {
    PsiImportList importList = getImportList();
    if (importList == null) return EMPTY_ARRAY;

    List<PsiElement> array = new ArrayList<>();

    PsiImportStatement[] statements = importList.getImportStatements();
    for (PsiImportStatement statement : statements) {
      if (statement.isOnDemand()) {
        PsiElement resolved = statement.resolve();
        if (resolved != null) {
          array.add(resolved);
        }
      }
    }

    if (includeImplicit){
      PsiJavaCodeReferenceElement[] implicitRefs = getImplicitlyImportedPackageReferences();
      for (PsiJavaCodeReferenceElement implicitRef : implicitRefs) {
        PsiElement resolved = implicitRef.resolve();
        if (resolved != null) {
          array.add(resolved);
        }
      }
    }

    return PsiUtilCore.toPsiElementArray(array);
  }

  @Override
  public PsiClass @NotNull [] getSingleClassImports(boolean checkIncludes) {
    PsiImportList importList = getImportList();
    if (importList == null) return PsiClass.EMPTY_ARRAY;

    List<PsiClass> array = new ArrayList<>();
    PsiImportStatement[] statements = importList.getImportStatements();
    for (PsiImportStatement statement : statements) {
      if (!statement.isOnDemand()) {
        PsiElement ref = statement.resolve();
        if (ref instanceof PsiClass) {
          array.add((PsiClass)ref);
        }
      }
    }
    return array.toArray(PsiClass.EMPTY_ARRAY);
  }

  @Override
  public PsiJavaCodeReferenceElement findImportReferenceTo(@NotNull PsiClass aClass) {
    PsiImportList importList = getImportList();
    if (importList != null) {
      PsiImportStatement[] statements = importList.getImportStatements();
      for (PsiImportStatement statement : statements) {
        if (!statement.isOnDemand()) {
          PsiElement ref = statement.resolve();
          if (ref != null && getManager().areElementsEquivalent(ref, aClass)) {
            return statement.getImportReference();
          }
        }
      }
    }
    return null;
  }

  @Override
  public String @NotNull [] getImplicitlyImportedPackages() {
    return IMPLICIT_IMPORTS;
  }

  @Override
  public PsiJavaCodeReferenceElement @NotNull [] getImplicitlyImportedPackageReferences() {
    return PsiImplUtil.namesToPackageReferences(myManager, IMPLICIT_IMPORTS);
  }

  private static class StaticImportFilteringProcessor extends DelegatingScopeProcessor {
    private final Map<String, Iterable<ResultWithContext>> myExplicitlyEnumerated;
    private final Collection<PsiElement> myCollectedElements = new HashSet<>();

    StaticImportFilteringProcessor(@NotNull Map<String, Iterable<ResultWithContext>> explicitlyEnumerated, @NotNull PsiScopeProcessor delegate) {
      super(delegate);
      myExplicitlyEnumerated = explicitlyEnumerated;
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (element instanceof PsiModifierListOwner && ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
        PsiScopeProcessor delegate = getDelegate();
        if (element instanceof PsiNamedElement) {
          String name = ((PsiNamedElement)element).getName();
          Iterable<ResultWithContext> shadowing = myExplicitlyEnumerated.get(name);
          if (shadowing != null && ContainerUtil.exists(shadowing, rwc -> hasSameDeclarationKind(element, rwc.getElement()))) return true;

          if (delegate instanceof MethodsProcessor && element instanceof PsiMethod) {
            PsiClass containingClass = ((PsiMethod)element).getContainingClass();
            if (containingClass != null && containingClass.isInterface()) {
              PsiElement currentFileContext = ((MethodsProcessor)delegate).getCurrentFileContext();
              if (currentFileContext instanceof PsiImportStaticStatement &&
                  ((PsiImportStaticStatement)currentFileContext).isOnDemand() &&
                  !containingClass.isEquivalentTo(((PsiImportStaticStatement)currentFileContext).resolveTargetClass())) {
                return true;
              }
            }
          }
        }
        if (myCollectedElements.add(element)) {
          return delegate.execute(element, state);
        }
      }
      return true;
    }

    private static boolean hasSameDeclarationKind(PsiElement e1, PsiElement e2) {
      return e1 instanceof PsiClass ? e2 instanceof PsiClass : e1 instanceof PsiMethod ? e2 instanceof PsiMethod : e2 instanceof PsiField;
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint != null ? nameHint.getName(state) : null;

    Map<String, Iterable<ResultWithContext>> explicitlyEnumerated = getEnumeratedDeclarations();
    //noinspection unchecked
    Iterable<ResultWithContext> iterable = name != null ? explicitlyEnumerated.get(name)
                                                        : ContainerUtil.concat(explicitlyEnumerated.values().toArray(new Iterable[0]));
    if (iterable != null && !ContainerUtil.process(iterable, new MyResolveCacheProcessor(state, processor))) return false;

    if (processor instanceof ClassResolverProcessor &&
        (PsiFileEx.isBatchReferenceProcessingEnabled(this) || myResolveCache.hasUpToDateValue()) &&
        !PsiUtil.isInsideJavadocComment(place)) {
      MostlySingularMultiMap<String, ResultWithContext> cache = myResolveCache.getValue();
      MyResolveCacheProcessor cacheProcessor = new MyResolveCacheProcessor(state, processor);
      return name != null ? cache.processForKey(name, cacheProcessor) : cache.processAllValues(cacheProcessor);
    }

    return processOnDemandPackages(state, place, processor);
  }

  private @NotNull Map<String, Iterable<ResultWithContext>> getEnumeratedDeclarations() {
    return myCachedDeclarations.getValue();
  }

  private @NotNull Map<String, Iterable<ResultWithContext>> findEnumeratedDeclarations() {
    MultiMap<String, PsiClass> ownClasses = MultiMap.create();
    MultiMap<String, PsiImportStatement> typeImports = MultiMap.create();
    MultiMap<String, PsiImportStaticStatement> staticImports = MultiMap.create();

    for (PsiClass psiClass : getClasses()) {
      if (psiClass instanceof PsiImplicitClass) continue;
      String name = psiClass.getName();
      if (name != null) {
        ownClasses.putValue(name, psiClass);
      }
    }
    for (PsiImportStatement anImport : getImportStatements()) {
      if (!anImport.isOnDemand()) {
        String qName = anImport.getQualifiedName();
        if (qName != null) {
          typeImports.putValue(StringUtil.getShortName(qName), anImport);
        }
      }
    }
    List<PsiImportStaticStatement> implicitStaticImports = ContainerUtil.filterIsInstance(getImplicitImports(), PsiImportStaticStatement.class);
    for (PsiImportStaticStatement staticImport : ContainerUtil.append(implicitStaticImports, getImportStaticStatements())) {
      String name = staticImport.getReferenceName();
      if (name != null) {
        staticImports.putValue(name, staticImport);
      }
    }

    Map<String, Iterable<ResultWithContext>> result = new LinkedHashMap<>();
    for (String name : ContainerUtil.newLinkedHashSet(
      ContainerUtil.concat(ownClasses.keySet(), typeImports.keySet(), staticImports.keySet()))) {
      NotNullLazyValue<Iterable<ResultWithContext>> lazy =
        NotNullLazyValue.volatileLazy(() -> findExplicitDeclarations(name, ownClasses, typeImports, staticImports));
      result.put(name, () -> lazy.getValue().iterator());
    }
    return result;
  }

  private static @NotNull Iterable<ResultWithContext> findExplicitDeclarations(@NotNull String name,
                                                                               @NotNull MultiMap<String, PsiClass> ownClasses,
                                                                               @NotNull MultiMap<String, PsiImportStatement> typeImports,
                                                                               @NotNull MultiMap<String, PsiImportStaticStatement> staticImports) {
    List<ResultWithContext> result = new ArrayList<>();
    for (PsiClass psiClass : ownClasses.get(name)) {
      result.add(new ResultWithContext(psiClass, null));
    }
    for (PsiImportStatement statement : typeImports.get(name)) {
      PsiElement target = statement.resolve();
      if (target == null || target instanceof PsiClass) {
        result.add(new ResultWithContext((PsiNamedElement)target, statement));
      }
    }
    for (PsiImportStaticStatement statement : staticImports.get(name)) {
      PsiJavaCodeReferenceElement reference = statement.getImportReference();
      if (reference != null) {
        JavaResolveResult[] targets = reference.multiResolve(false);
        if (targets.length == 0) {
          result.add(new ResultWithContext(null, statement));
        } else {
          for (JavaResolveResult target : targets) {
            PsiElement element = target.getElement();
            if (element instanceof PsiNamedElement) {
              result.add(new ResultWithContext((PsiNamedElement)element, statement));
            }
          }
        }
      }
    }
    return JBIterable.from(result).unique(ResultWithContext::getElement);
  }

  private boolean processOnDemandPackages(@NotNull ResolveState state, @NotNull PsiElement place, @NotNull PsiScopeProcessor processor) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    boolean shouldProcessClasses = classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS);
    if (shouldProcessClasses && !processCurrentPackage(state, place, processor)) return false;

    if (!processOnDemandStaticImports(state, new StaticImportFilteringProcessor(getEnumeratedDeclarations(), processor))) {
      return false;
    }

    if (shouldProcessClasses && !processOnDemandTypeImports(state, place, processor)) return false;

    if (shouldProcessClasses && !processModules(state, place, processor)) return false;

    return !shouldProcessClasses || processImplicitImports(state, place, processor);
  }

  private boolean processModules(@NotNull ResolveState state, @NotNull PsiElement place, @NotNull PsiScopeProcessor processor) {
    List<PsiImportModuleStatement> implicitModuleImports =
      ContainerUtil.filterIsInstance(getImplicitImports(), PsiImportModuleStatement.class);
    for (PsiImportModuleStatement statement : ContainerUtil.append(implicitModuleImports, getImportModuleStatements())) {
      PsiElement resolved = statement.resolve();
      if (resolved != null) {
        processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, statement);
        if (!processOnDemandTarget(resolved, state, place, processor)) return false;
      }
    }
    return true;
  }

  private PsiImportStaticStatement @NotNull [] getImportStaticStatements() {
    return getImportList() != null ? getImportList().getImportStaticStatements() : PsiImportStaticStatement.EMPTY_ARRAY;
  }

  private PsiImportStatement @NotNull [] getImportStatements() {
    return getImportList() != null ? getImportList().getImportStatements() : PsiImportStatement.EMPTY_ARRAY;
  }

  private PsiImportModuleStatement @NotNull [] getImportModuleStatements() {
    return getImportList() != null ? getImportList().getImportModuleStatements() : PsiImportModuleStatement.EMPTY_ARRAY;
  }

  private boolean processCurrentPackage(@NotNull ResolveState state, @NotNull PsiElement place, @NotNull PsiScopeProcessor processor) {
    processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, null);
    PsiPackage aPackage = JavaPsiFacade.getInstance(myManager.getProject()).findPackage(getPackageName());
    return aPackage == null || processPackageDeclarations(state, place, aPackage, processor);
  }

  private boolean processOnDemandTypeImports(@NotNull ResolveState state, @NotNull PsiElement place, @NotNull PsiScopeProcessor processor) {
    for (PsiImportStatement statement : getImportStatements()) {
      if (statement.isOnDemand()) {
        PsiElement resolved = statement.resolve();
        if (resolved != null) {
          processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, statement);
          if (!processOnDemandTarget(resolved, state, place, processor)) return false;
        }
      }
    }
    return true;
  }

  private boolean processOnDemandStaticImports(@NotNull ResolveState state, @NotNull StaticImportFilteringProcessor processor) {
    List<PsiImportStaticStatement> implicitStaticImports = ContainerUtil.filterIsInstance(getImplicitImports(), PsiImportStaticStatement.class);
    for (PsiImportStaticStatement importStaticStatement : ContainerUtil.append(implicitStaticImports, getImportStaticStatements())) {
      if (!importStaticStatement.isOnDemand()) continue;
      PsiClass targetElement = importStaticStatement.resolveTargetClass();
      if (targetElement != null) {
        processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, importStaticStatement);
        if (!PsiClassImplUtil.processAllMembersWithoutSubstitutors(targetElement, processor, state)) return false;
      }
    }
    return true;
  }

  private boolean processImplicitImports(@NotNull ResolveState state, @NotNull PsiElement place, @NotNull PsiScopeProcessor processor) {
    processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, null);
    for (PsiJavaCodeReferenceElement aImplicitlyImported : getImplicitlyImportedPackageReferences()) {
      PsiElement resolved = aImplicitlyImported.resolve();
      if (resolved != null) {
        if (resolved instanceof PsiPackage && "java.lang".equals(((PsiPackage)resolved).getQualifiedName())) {
          LanguageLevel level = PsiUtil.getLanguageLevel(place);
          processor = new JavaLangClassesFilter(processor, level);
        }
        if (!processOnDemandTarget(resolved, state, place, processor)) return false;
      }
    }
    return true;
  }

  private static boolean processPackageDeclarations(@NotNull ResolveState state,
                                                    @NotNull PsiElement place,
                                                    @NotNull PsiPackage aPackage,
                                                    @NotNull PsiScopeProcessor processor) {
    if (!aPackage.getQualifiedName().isEmpty()) {
      processor = new DelegatingScopeProcessor(processor) {
        @Override
        public @Nullable <T> T getHint(@NotNull Key<T> hintKey) {
          if (hintKey == ElementClassHint.KEY) {
            //noinspection unchecked
            return (T)(ElementClassHint)kind -> kind == ElementClassHint.DeclarationKind.CLASS;
          }
          return super.getHint(hintKey);
        }
      };
    }
    return aPackage.processDeclarations(processor, state, null, place);
  }

  private static @NotNull PsiSubstitutor createRawSubstitutor(@NotNull PsiClass containingClass) {
    return JavaPsiFacade.getElementFactory(containingClass.getProject()).createRawSubstitutor(containingClass);
  }

  private static boolean processOnDemandTarget(@NotNull PsiElement target, @NotNull ResolveState substitutor, @NotNull PsiElement place, @NotNull PsiScopeProcessor processor) {
    if (target instanceof PsiPackage) {
      return processPackageDeclarations(substitutor, place, (PsiPackage)target, processor);
    }
    if (target instanceof PsiClass) {
      PsiClass[] inners = ((PsiClass)target).getInnerClasses();
      if (((PsiClass)target).hasTypeParameters()) {
        substitutor = substitutor.put(PsiSubstitutor.KEY, createRawSubstitutor((PsiClass)target));
      }

      for (PsiClass inner : inners) {
        if (!processor.execute(inner, substitutor)) return false;
      }
    }
    else if (target instanceof PsiJavaModule) {
      return processModuleDeclaration(substitutor, place, (PsiJavaModule)target, processor);
    }
    else {
      LOG.error("Unexpected target type: " + target);
    }
    return true;
  }

  private static boolean processModuleDeclaration(@NotNull ResolveState state,
                                                  @NotNull PsiElement place,
                                                  @NotNull PsiJavaModule target,
                                                  @NotNull PsiScopeProcessor processor) {
    processor = new DelegatingScopeProcessor(processor) {
      @Override
      public @Nullable <T> T getHint(@NotNull Key<T> hintKey) {
        if (hintKey == ElementClassHint.KEY) {
          //noinspection unchecked
          return (T)(ElementClassHint)kind -> kind == ElementClassHint.DeclarationKind.CLASS;
        } else {
          return super.getHint(hintKey);
        }
      }
    };

    return target.processDeclarations(processor, state, null, place);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitJavaFile(this);
    }
    else {
      visitor.visitFile(this);
    }
  }

  @Override
  public @NotNull Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public boolean importClass(@NotNull PsiClass aClass) {
    return JavaCodeStyleManager.getInstance(getProject()).addImport(this, aClass);
  }

  private static final NotNullLazyKey<LanguageLevel, PsiJavaFileBaseImpl> LANGUAGE_LEVEL_KEY = NotNullLazyKey.createLazyKey("LANGUAGE_LEVEL",
                                                                                                                     file -> file.getLanguageLevelInner());

  @Override
  public @NotNull LanguageLevel getLanguageLevel() {
    return LANGUAGE_LEVEL_KEY.getValue(this);
  }

  @Override
  public @Nullable PsiJavaModule getModuleDeclaration() {
    return null;
  }

  @Override
  public @NotNull ImplicitlyImportedElement @NotNull [] getImplicitlyImportedElements() {
    return myCachedImplicitImportedElements.getValue();
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    putUserData(LANGUAGE_LEVEL_KEY, null);
  }

  @Override
  public void setOriginalFile(@NotNull PsiFile originalFile) {
    super.setOriginalFile(originalFile);
    clearCaches();
  }

  private @NotNull @Unmodifiable List<PsiImportStatementBase> getImplicitImports() {
    return ContainerUtil.map(getImplicitlyImportedElements(), element -> element.createImportStatement());
  }

  private static final Key<String> SHEBANG_SOURCE_LEVEL = Key.create("SHEBANG_SOURCE_LEVEL");

  private @NotNull LanguageLevel getLanguageLevelInner() {
    if (myOriginalFile instanceof PsiJavaFile) {
      return ((PsiJavaFile)myOriginalFile).getLanguageLevel();
    }

    LanguageLevel forcedLanguageLevel = getUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY);
    if (forcedLanguageLevel != null) return forcedLanguageLevel;

    VirtualFile virtualFile = getVirtualFile();
    if (virtualFile == null) virtualFile = getViewProvider().getVirtualFile();

    String sourceLevel = null;
    FileElement treeElement = getTreeElement();
    ASTNode firstLeaf = treeElement == null ? null : TreeUtil.findFirstLeaf(treeElement, false);
    // optimization: do not (re)compute the whole file text on each PSI change - will lead to quadratic nightmare in case of many small changes otherwise
    if (firstLeaf == null || Strings.startsWith(firstLeaf.getChars(), 0, "#!")) {
      try {
        CharSequence contents = getViewProvider().getContents();
        int lineBound = Strings.indexOf(contents, "\n");
        CharSequence line = lineBound > 0 ? contents.subSequence(0, lineBound) : contents;
        if (Strings.startsWith(line, 0, "#!")) {
          List<String> params = ParametersListUtil.parse(line.toString());
          int srcIdx = params.indexOf("--source");
          if (srcIdx > 0 && srcIdx + 1 < params.size()) {
            sourceLevel = params.get(srcIdx + 1);
            LanguageLevel sheBangLevel = LanguageLevel.parse(sourceLevel);
            if (sheBangLevel != null) {
              return sheBangLevel;
            }
          }
        }
      }
      catch (Throwable ignored) {
      }
      finally {
        if (!Objects.equals(sourceLevel, virtualFile.getUserData(SHEBANG_SOURCE_LEVEL)) && virtualFile.isInLocalFileSystem()) {
          virtualFile.putUserData(SHEBANG_SOURCE_LEVEL, sourceLevel);
          VirtualFile file = virtualFile;
          ApplicationManager.getApplication().invokeLater(() -> FileContentUtilCore.reparseFiles(file),
                                                          ModalityState.nonModal(),
                                                          ApplicationManager.getApplication().getDisposed());
        }
      }
    }

    return JavaPsiImplementationHelper.getInstance(getProject()).getEffectiveLanguageLevel(virtualFile);
  }

  private static class MyCacheBuilder implements CachedValueProvider<MostlySingularMultiMap<String, ResultWithContext>> {
    private final @NotNull PsiJavaFileBaseImpl myFile;

    MyCacheBuilder(@NotNull PsiJavaFileBaseImpl file) {
      myFile = file;
    }

    @Override
    public @NotNull Result<MostlySingularMultiMap<String, ResultWithContext>> compute() {
      SymbolCollectingProcessor p = new SymbolCollectingProcessor();
      myFile.processOnDemandPackages(ResolveState.initial(), myFile, p);
      MostlySingularMultiMap<String, ResultWithContext> results = p.getResults();
      return Result.create(results, PsiModificationTracker.MODIFICATION_COUNT, myFile);
    }
  }

  private static class MyResolveCacheProcessor implements Processor<ResultWithContext> {
    private final PsiScopeProcessor myProcessor;
    private final ResolveState myState;

    MyResolveCacheProcessor(@NotNull ResolveState state, @NotNull PsiScopeProcessor processor) {
      myProcessor = processor;
      myState = state;
    }

    @Override
    public boolean process(@NotNull ResultWithContext result) {
      PsiElement context = result.getFileContext();
      myProcessor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, context);
      PsiNamedElement element = result.getElement();

      if (element == null) {
        return myProcessor.executeForUnresolved();
      }

      if (element instanceof PsiClass && context instanceof PsiImportStatement) {
        PsiClass containingClass = ((PsiClass)element).getContainingClass();
        if (containingClass != null && containingClass.hasTypeParameters()) {
          return myProcessor.execute(element, myState.put(PsiSubstitutor.KEY, createRawSubstitutor(containingClass)));
        }
      }

      return myProcessor.execute(element, myState);
    }
  }

  private static class JavaLangClassesFilter extends DelegatingScopeProcessor {
    private static final Map<String, LanguageLevel> ourJavaLangClassFeatures = new HashMap<>();

    static {
      // Only classes appeared in java.lang since Java 9 are listed here
      // As --release option works since Java 9
      ourJavaLangClassFeatures.put("MatchException", JavaFeature.PATTERNS_IN_SWITCH.getMinimumLevel());
      ourJavaLangClassFeatures.put("Module", JavaFeature.MODULES.getMinimumLevel());
      ourJavaLangClassFeatures.put("ModuleLayer", JavaFeature.MODULES.getMinimumLevel());
      ourJavaLangClassFeatures.put("ProcessHandle", LanguageLevel.JDK_1_9);
      ourJavaLangClassFeatures.put("Record", JavaFeature.RECORDS.getMinimumLevel());
      ourJavaLangClassFeatures.put("ScopedValue", JavaFeature.SCOPED_VALUES.getMinimumLevel());
      ourJavaLangClassFeatures.put("WrongThreadException", LanguageLevel.JDK_19);
    }
 
    private final LanguageLevel myLevel;

    JavaLangClassesFilter(@NotNull PsiScopeProcessor processor, LanguageLevel level) {
      super(processor);
      myLevel = level;
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (element instanceof PsiClass) {
        LanguageLevel classMinLevel = ourJavaLangClassFeatures.get(((PsiClass)element).getName());
        if (classMinLevel != null && myLevel.isLessThan(classMinLevel)) return true;
      }
      return super.execute(element, state);
    }
  }
}