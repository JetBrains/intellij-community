// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

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
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.scope.*;
import com.intellij.psi.scope.processor.MethodsProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class PsiJavaFileBaseImpl extends PsiFileImpl implements PsiJavaFile {
  private static final Logger LOG = Logger.getInstance(PsiJavaFileBaseImpl.class);
  private static final String[] IMPLICIT_IMPORTS = { CommonClassNames.DEFAULT_PACKAGE };

  private final CachedValue<MostlySingularMultiMap<String, ResultWithContext>> myResolveCache;
  private volatile String myPackageName;

  protected PsiJavaFileBaseImpl(IElementType elementType, IElementType contentElementType, FileViewProvider viewProvider) {
    super(elementType, contentElementType, viewProvider);
    myResolveCache = CachedValuesManager.getManager(myManager.getProject()).createCachedValue(new MyCacheBuilder(this), false);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myPackageName = null;
  }

  @Override
  public PsiClass @NotNull [] getClasses() {
    final StubElement<?> stub = getGreenStub();
    if (stub != null) {
      return stub.getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(Constants.CLASS_BIT_SET, PsiClass.ARRAY_FACTORY);
  }

  @Override
  public PsiPackageStatement getPackageStatement() {
    ASTNode node = calcTreeElement().findChildByType(JavaElementType.PACKAGE_STATEMENT);
    return node != null ? (PsiPackageStatement)node.getPsi() : null;
  }

  @Override
  @NotNull
  public String getPackageName() {
    PsiJavaFileStub stub = (PsiJavaFileStub)getGreenStub();
    if (stub != null) {
      return stub.getPackageName();
    }

    String name = myPackageName;
    if (name == null) {
      PsiPackageStatement statement = getPackageStatement();
      myPackageName = name = statement == null ? "" : statement.getPackageName();
    }
    return name;
  }

  @Override
  public void setPackageName(final String packageName) throws IncorrectOperationException {
    if (PsiUtil.isModuleFile(this)) {
      throw new IncorrectOperationException("Cannot set package name for module declarations");
    }

    final PsiPackageStatement packageStatement = getPackageStatement();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    if (packageStatement != null) {
      if (!packageName.isEmpty()) {
        final PsiJavaCodeReferenceElement reference = packageStatement.getPackageReference();
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
        final PsiElement prev = anchor.getPrevSibling();
        if (prev instanceof PsiComment) {
          final String text = prev.getText().trim();
          if (text.startsWith("/*") && !text.endsWith("*/")) {
            // close any open javadoc/comments before import list
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
      if (child instanceof PsiErrorElement && child.getFirstChild() != null && child.getFirstChild().textMatches(PsiKeyword.PACKAGE)) {
        child.delete();
        break;
      }
      child = child.getNextSibling();
    }
  }

  @Override
  public PsiImportList getImportList() {
    StubElement<?> stub = getGreenStub();
    if (stub != null) {
      PsiImportList[] nodes = stub.getChildrenByType(JavaStubElementTypes.IMPORT_LIST, PsiImportList.ARRAY_FACTORY);
      if (nodes.length == 1) return nodes[0];
      assert nodes.length == 0;
      return null;
    }

    ASTNode node = calcTreeElement().findChildByType(JavaElementType.IMPORT_LIST);
    return (PsiImportList)SourceTreeToPsiMap.treeElementToPsi(node);
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
        final PsiElement resolved = implicitRef.resolve();
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
  public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass) {
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

    StaticImportFilteringProcessor(PsiScopeProcessor delegate, Map<String, Iterable<ResultWithContext>> explicitlyEnumerated) {
      super(delegate);
      myExplicitlyEnumerated = explicitlyEnumerated;
    }

    @Override
    public boolean execute(@NotNull final PsiElement element, @NotNull final ResolveState state) {
      if (element instanceof PsiModifierListOwner && ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
        PsiScopeProcessor delegate = getDelegate();
        if (element instanceof PsiNamedElement) {
          final String name = ((PsiNamedElement)element).getName();
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
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint != null ? nameHint.getName(state) : null;

    Map<String, Iterable<ResultWithContext>> explicitlyEnumerated = getExplicitlyEnumeratedDeclarations();
    //noinspection unchecked
    Iterable<ResultWithContext> iterable = name != null ? explicitlyEnumerated.get(name)
                                                        : ContainerUtil.concat(explicitlyEnumerated.values().toArray(new Iterable[0]));
    if (iterable != null && !ContainerUtil.process(iterable, new MyResolveCacheProcessor(processor, state))) return false;

    if (processor instanceof ClassResolverProcessor &&
        (getUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING) == Boolean.TRUE || myResolveCache.hasUpToDateValue()) &&
        !PsiUtil.isInsideJavadocComment(place)) {
      MostlySingularMultiMap<String, ResultWithContext> cache = myResolveCache.getValue();
      MyResolveCacheProcessor cacheProcessor = new MyResolveCacheProcessor(processor, state);
      return name != null ? cache.processForKey(name, cacheProcessor) : cache.processAllValues(cacheProcessor);
    }

    return processOnDemandPackages(processor, state, place);
  }

  private Map<String, Iterable<ResultWithContext>> getExplicitlyEnumeratedDeclarations() {
    return CachedValuesManager.getCachedValue(this, () -> {
      MultiMap<String, PsiClass> ownClasses = MultiMap.create();
      MultiMap<String, PsiImportStatement> typeImports = MultiMap.create();
      MultiMap<String, PsiImportStaticStatement> staticImports = MultiMap.create();

      for (PsiClass psiClass : getClasses()) {
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
      for (PsiImportStaticStatement staticImport : getImportStaticStatements()) {
        String name = staticImport.getReferenceName();
        if (name != null) {
          staticImports.putValue(name, staticImport);
        }
      }

      Map<String, Iterable<ResultWithContext>> result = new LinkedHashMap<>();
      for (String name : ContainerUtil.newLinkedHashSet(ContainerUtil.concat(ownClasses.keySet(), typeImports.keySet(), staticImports.keySet()))) {
        NotNullLazyValue<Iterable<ResultWithContext>> lazy = NotNullLazyValue.volatileLazy(() -> findExplicitDeclarations(name, ownClasses, typeImports, staticImports));
        result.put(name, () -> lazy.getValue().iterator());
      }
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private static Iterable<ResultWithContext> findExplicitDeclarations(String name,
                                                                      MultiMap<String, PsiClass> ownClasses,
                                                                      MultiMap<String, PsiImportStatement> typeImports,
                                                                      MultiMap<String, PsiImportStaticStatement> staticImports) {
    List<ResultWithContext> result = new ArrayList<>();
    for (PsiClass psiClass : ownClasses.get(name)) {
      result.add(new ResultWithContext(psiClass, null));
    }
    for (PsiImportStatement statement : typeImports.get(name)) {
      PsiElement target = statement.resolve();
      if (target instanceof PsiClass) {
        result.add(new ResultWithContext((PsiNamedElement)target, statement));
      }
    }
    for (PsiImportStaticStatement statement : staticImports.get(name)) {
      PsiJavaCodeReferenceElement reference = statement.getImportReference();
      if (reference != null) {
        for (JavaResolveResult result1 : reference.multiResolve(false)) {
          PsiElement element = result1.getElement();
          if (element instanceof PsiNamedElement) {
            result.add(new ResultWithContext((PsiNamedElement)element, statement));
          }
        }
      }
    }
    return JBIterable.from(result).unique(ResultWithContext::getElement);
  }

  private boolean processOnDemandPackages(PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement place) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    boolean shouldProcessClasses = classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS);
    if (shouldProcessClasses && !processCurrentPackage(processor, state, place)) return false;

    if (!processOnDemandStaticImports(state, new StaticImportFilteringProcessor(processor, getExplicitlyEnumeratedDeclarations()))) {
      return false;
    }

    if (shouldProcessClasses && !processOnDemandTypeImports(processor, state, place)) return false;

    return !shouldProcessClasses || processImplicitImports(processor, state, place);
  }

  private PsiImportStaticStatement[] getImportStaticStatements() {
    return getImportList() != null ? getImportList().getImportStaticStatements() : PsiImportStaticStatement.EMPTY_ARRAY;
  }

  private PsiImportStatement[] getImportStatements() {
    return getImportList() != null ? getImportList().getImportStatements() : PsiImportStatement.EMPTY_ARRAY;
  }

  private boolean processCurrentPackage(PsiScopeProcessor processor, ResolveState state, PsiElement place) {
    processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, null);
    PsiPackage aPackage = JavaPsiFacade.getInstance(myManager.getProject()).findPackage(getPackageName());
    return aPackage == null || processPackageDeclarations(processor, state, place, aPackage);
  }

  private boolean processOnDemandTypeImports(PsiScopeProcessor processor, ResolveState state, PsiElement place) {
    for (PsiImportStatement statement : getImportStatements()) {
      if (statement.isOnDemand()) {
        final PsiElement resolved = statement.resolve();
        if (resolved != null) {
          processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, statement);
          if (!processOnDemandTarget(resolved, processor, state, place)) return false;
        }
      }
    }
    return true;
  }

  private boolean processOnDemandStaticImports(ResolveState state, StaticImportFilteringProcessor processor) {
    for (PsiImportStaticStatement importStaticStatement : getImportStaticStatements()) {
      if (!importStaticStatement.isOnDemand()) continue;
      final PsiClass targetElement = importStaticStatement.resolveTargetClass();
      if (targetElement != null) {
        processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, importStaticStatement);
        if (!PsiClassImplUtil.processAllMembersWithoutSubstitutors(targetElement, processor, state)) return false;
      }
    }
    return true;
  }

  private boolean processImplicitImports(PsiScopeProcessor processor, ResolveState state, PsiElement place) {
    processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, null);
    for (PsiJavaCodeReferenceElement aImplicitlyImported : getImplicitlyImportedPackageReferences()) {
      final PsiElement resolved = aImplicitlyImported.resolve();
      if (resolved != null) {
        if (!processOnDemandTarget(resolved, processor, state, place)) return false;
      }
    }
    return true;
  }

  private static boolean processPackageDeclarations(PsiScopeProcessor processor,
                                                    @NotNull ResolveState state,
                                                    PsiElement place,
                                                    @NotNull PsiPackage aPackage) {
    if (!aPackage.getQualifiedName().isEmpty()) {
      processor = new DelegatingScopeProcessor(processor) {
        @Nullable
        @Override
        public <T> T getHint(@NotNull Key<T> hintKey) {
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

  @NotNull
  private static PsiSubstitutor createRawSubstitutor(PsiClass containingClass) {
    return JavaPsiFacade.getElementFactory(containingClass.getProject()).createRawSubstitutor(containingClass);
  }

  private static boolean processOnDemandTarget(PsiElement target, PsiScopeProcessor processor, ResolveState substitutor, PsiElement place) {
    if (target instanceof PsiPackage) {
      return processPackageDeclarations(processor, substitutor, place, (PsiPackage)target);
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
    else {
      LOG.error(target);
    }
    return true;
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
  @NotNull
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public boolean importClass(@NotNull PsiClass aClass) {
    return JavaCodeStyleManager.getInstance(getProject()).addImport(this, aClass);
  }

  private static final NotNullLazyKey<LanguageLevel, PsiJavaFileBaseImpl> LANGUAGE_LEVEL_KEY = NotNullLazyKey.create("LANGUAGE_LEVEL",
                                                                                                                     file -> file.getLanguageLevelInner());

  @Override
  @NotNull
  public LanguageLevel getLanguageLevel() {
    return LANGUAGE_LEVEL_KEY.getValue(this);
  }

  @Nullable
  @Override
  public PsiJavaModule getModuleDeclaration() {
    return null;
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

  private static final Key<String> SHEBANG_SOURCE_LEVEL = Key.create("SHEBANG_SOURCE_LEVEL");

  @NotNull
  private LanguageLevel getLanguageLevelInner() {
    if (myOriginalFile instanceof PsiJavaFile) {
      return ((PsiJavaFile)myOriginalFile).getLanguageLevel();
    }

    LanguageLevel forcedLanguageLevel = getUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY);
    if (forcedLanguageLevel != null) return forcedLanguageLevel;

    VirtualFile virtualFile = getVirtualFile();
    if (virtualFile == null) virtualFile = getViewProvider().getVirtualFile();

    String sourceLevel = null;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(virtualFile.getInputStream(), StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      if (line != null && line.startsWith("#!")) {
        List<String> params = ParametersListUtil.parse(line);
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
                                                        ModalityState.NON_MODAL,
                                                        ApplicationManager.getApplication().getDisposed());
      }
    }

    return JavaPsiImplementationHelper.getInstance(getProject()).getEffectiveLanguageLevel(virtualFile);
  }

  private static class MyCacheBuilder implements CachedValueProvider<MostlySingularMultiMap<String, ResultWithContext>> {
    private final PsiJavaFileBaseImpl myFile;

    MyCacheBuilder(PsiJavaFileBaseImpl file) {
      myFile = file;
    }

    @Override
    public Result<MostlySingularMultiMap<String, ResultWithContext>> compute() {
      SymbolCollectingProcessor p = new SymbolCollectingProcessor();
      myFile.processOnDemandPackages(p, ResolveState.initial(), myFile);
      MostlySingularMultiMap<String, ResultWithContext> results = p.getResults();
      return Result.create(results, PsiModificationTracker.MODIFICATION_COUNT, myFile);
    }
  }

  private static class MyResolveCacheProcessor implements Processor<ResultWithContext> {
    private final PsiScopeProcessor myProcessor;
    private final ResolveState myState;

    MyResolveCacheProcessor(PsiScopeProcessor processor, ResolveState state) {
      myProcessor = processor;
      myState = state;
    }

    @Override
    public boolean process(ResultWithContext result) {
      final PsiElement context = result.getFileContext();
      myProcessor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, context);
      final PsiNamedElement element = result.getElement();

      if (element instanceof PsiClass && context instanceof PsiImportStatement) {
        final PsiClass containingClass = ((PsiClass)element).getContainingClass();
        if (containingClass != null && containingClass.hasTypeParameters()) {
          return myProcessor.execute(element, myState.put(PsiSubstitutor.KEY, createRawSubstitutor(containingClass)));
        }
      }

      return myProcessor.execute(element, myState);
    }
  }
}