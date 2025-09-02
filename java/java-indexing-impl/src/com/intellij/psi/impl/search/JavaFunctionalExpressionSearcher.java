// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobLauncher;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.java.FunExprOccurrence;
import com.intellij.psi.impl.java.JavaFunctionalExpressionIndex;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.index.JavaMethodParameterTypesIndex;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch.SearchParameters;
import com.intellij.psi.stubs.StubInconsistencyReporter;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubTextInconsistencyException;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@ApiStatus.Internal
public final class JavaFunctionalExpressionSearcher extends QueryExecutorBase<PsiFunctionalExpression, SearchParameters> {
  private static final Logger LOG = Logger.getInstance(JavaFunctionalExpressionSearcher.class);
  public static final int SMART_SEARCH_THRESHOLD = 5;

  @Override
  public void processQuery(@NotNull SearchParameters p, @NotNull Processor<? super PsiFunctionalExpression> consumer) {
    if (SearchScope.isEmptyScope(ReadAction.compute(() -> p.getEffectiveSearchScope()))) {
      return;
    }
    Session session = ReadAction.compute(() -> new Session(p, consumer));
    session.processResults();
    if (!session.filesLookedInside.isEmpty() && LOG.isDebugEnabled()) {
      LOG.debug(session.toString());
    }
  }

  private static @NotNull List<SamDescriptor> calcDescriptors(@NotNull Session session) {
    PsiClass aClass = session.elementToSearch;
    Project project = PsiUtilCore.getProjectInReadAction(aClass);

    Callable<List<SamDescriptor>> runnable = () -> {
      if (!aClass.isValid() || !aClass.isInterface()) {
        return List.of();
      }
      if (InjectedLanguageManager.getInstance(project).isInjectedFragment(aClass.getContainingFile()) || !hasModuleWithFunctionalExpressions(project)) {
        return List.of();
      }
      PsiSearchHelper psiSearchHelper = PsiSearchHelper.getInstance(project);

      Set<PsiClass> visited = new HashSet<>();
      processSubInterfaces(aClass, visited);
      List<SamDescriptor> descriptors = new ArrayList<>();
      for (PsiClass samClass : visited) {
        if (LambdaUtil.isFunctionalClass(samClass)) {
          PsiMethod saMethod = Objects.requireNonNull(LambdaUtil.getFunctionalInterfaceMethod(samClass));
          PsiType samType = saMethod.getReturnType();
          if (samType == null) continue;
          if (session.method != null &&
              !saMethod.equals(session.method) &&
              !MethodSignatureUtil.isSuperMethod(saMethod, session.method)) {
            continue;
          }

          SearchScope scope = psiSearchHelper.getUseScope(samClass).intersectWith(session.scope);
          descriptors.add(new SamDescriptor(samClass, saMethod, samType, GlobalSearchScopeUtil.toGlobalSearchScope(scope, project)));
        }
      }
      return descriptors;
    };
    return ReadAction.nonBlocking(runnable).inSmartMode(project).executeSynchronously();
  }

  private static @NotNull Set<VirtualFile> getLikelyFiles(@NotNull List<? extends SamDescriptor> descriptors,
                                                          @NotNull Collection<? extends VirtualFile> candidateFiles,
                                                          @NotNull Project project) {
    final GlobalSearchScope candidateFilesScope = ReadAction.compute(() -> GlobalSearchScope.filesScope(project, candidateFiles));
    return JBIterable.from(descriptors).flatMap(descriptor -> ((SamDescriptor)descriptor).getMostLikelyFiles(candidateFilesScope)).toSet();
  }

  private static @NotNull MultiMap<VirtualFile, FunExprOccurrence> getAllOccurrences(@NotNull List<? extends SamDescriptor> descriptors) {
    MultiMap<VirtualFile, FunExprOccurrence> result = MultiMap.createLinkedSet();
    descriptors.get(0).dumbService.runReadActionInSmartMode(() -> {
      for (SamDescriptor descriptor : descriptors) {
        GlobalSearchScope scope = new JavaSourceFilterScope(descriptor.effectiveUseScope, true);
        for (FunctionalExpressionKey key : descriptor.keys) {
          FileBasedIndex.getInstance().processValues(JavaFunctionalExpressionIndex.INDEX_ID, key, null, (file, infos) -> {
            result.putValues(file, ContainerUtil.map(infos, entry -> entry.occurrence));
            return true;
          }, scope);
        }
      }
    });
    LOG.debug("Found " + result.values().size() + " fun-expressions in " + result.keySet().size() + " files");
    return result;
  }

  private static void processOffsets(@NotNull List<? extends SamDescriptor> descriptors, @NotNull Session session) {
    if (descriptors.isEmpty()) return;

    List<PsiClass> samClasses = ContainerUtil.map(descriptors, d -> d.samClass);
    MultiMap<VirtualFile, FunExprOccurrence> allCandidates = getAllOccurrences(descriptors);
    if (allCandidates.isEmpty()) return;

    Set<VirtualFile> allFiles = allCandidates.keySet();
    session.filesConsidered.addAndGet(allFiles.size());

    Set<VirtualFile> filesFirst = getLikelyFiles(descriptors, allFiles, session.project);
    Processor<VirtualFile> vFileProcessor = vFile -> {
      if (vFile.isDirectory()) return true;
      Collection<FunExprOccurrence> occurrences = allCandidates.get(vFile);
      session.contextsConsidered.addAndGet(occurrences.size());
      Map<FunExprOccurrence, Confidence> toLoad = filterInapplicable(samClasses, vFile, occurrences, session.project);
      if (!toLoad.isEmpty()) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("To load " + vFile.getPath() + " with values: " + toLoad);
        }
        session.filesLookedInside.add(vFile);
        return processFile(descriptors, vFile, toLoad, session);
      }
      return true;
    };
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(filesFirst),
                     ProgressIndicatorProvider.getGlobalProgressIndicator(), vFileProcessor)) return;
    allFiles.removeAll(filesFirst);
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(allFiles),
                     ProgressIndicatorProvider.getGlobalProgressIndicator(), vFileProcessor);
  }

  private static @NotNull Map<FunExprOccurrence, Confidence> filterInapplicable(@NotNull List<? extends PsiClass> samClasses,
                                                                                @NotNull VirtualFile vFile,
                                                                                @NotNull Collection<? extends FunExprOccurrence> occurrences,
                                                                                @NotNull Project project) {
    return ReadAction.nonBlocking(() -> {
      Map<FunExprOccurrence, Confidence> map = new HashMap<>();
      for (FunExprOccurrence occurrence : occurrences) {
        ThreeState result = occurrence.checkHasTypeLight(samClasses, vFile, project);
        if (result != ThreeState.NO) {
          map.put(occurrence, result == ThreeState.YES ? Confidence.sure : Confidence.needsCheck);
        }
      }
      return map;
    }).inSmartMode(project)
      .executeSynchronously();
  }

  private enum Confidence { sure, needsCheck}

  private static boolean processFile(@NotNull List<? extends SamDescriptor> descriptors,
                                     @NotNull VirtualFile vFile,
                                     @NotNull Map<FunExprOccurrence, Confidence> occurrences,
                                     @NotNull Session session) {
    return descriptors.get(0).dumbService.runReadActionInSmartMode(() -> {
      PsiManager manager = descriptors.get(0).samClass.getManager();
      PsiFile file = manager.findFile(vFile);
      if (!(file instanceof PsiJavaFile)) {
        LOG.error("Non-java file " + file + "; " + vFile);
        return true;
      }

      Map<TextRange, PsiFile> fragmentCache = new HashMap<>();

      @NotNull List<JavaFunctionalExpressionIndex.IndexEntry> data = ContainerUtil.flatten(
        FileBasedIndex.getInstance().getFileData(JavaFunctionalExpressionIndex.INDEX_ID, vFile, manager.getProject()).values());
      for (JavaFunctionalExpressionIndex.IndexEntry entry : data) {
        Confidence confidence = occurrences.get(entry.occurrence);
        if (confidence != null) {
          (confidence == Confidence.sure ? session.sureExprsAfterLightCheck : session.exprsToHeavyCheck).incrementAndGet();

          int offset = entry.exprStart;
          boolean useAST = ((PsiFileEx)file).isContentsLoaded();
          PsiFunctionalExpression expression = useAST ? findPsiByAST(file, offset) : findPsiByStubs(file, entry.exprIndex);
          if (expression == null) {
            LOG.error("Fun expression not found in " + file + " at " + offset);
            continue;
          }

          if (confidence == Confidence.needsCheck) {
            PsiFunctionalExpression toCheck = useAST ? expression : getNonPhysicalCopy(fragmentCache, entry, expression);
            if (!hasType(descriptors, toCheck)) {
              continue;
            }
          }

          if (!session.consumer.process(expression)) {
            return false;
          }
        }
      }
      return true;
    });
  }

  private static @Nullable PsiFunctionalExpression findPsiByAST(PsiFile file, int offset) {
    PsiFunctionalExpression expression =
      PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiFunctionalExpression.class, false);
    if (expression == null || expression.getTextRange().getStartOffset() != offset) {
      return null;
    }
    return expression;
  }

  private static PsiFunctionalExpression findPsiByStubs(PsiFile file, int index) {
    StubbedSpine spine = ((PsiFileWithStubSupport)file).getStubbedSpine();
    int funExprIndex = 0;
    for (int i = 0; i < spine.getStubCount(); i++) {
      IElementType type = spine.getStubType(i);
      if (type == JavaStubElementTypes.LAMBDA_EXPRESSION || type == JavaStubElementTypes.METHOD_REF_EXPRESSION) {
        if (funExprIndex == index) {
          return (PsiFunctionalExpression)spine.getStubPsi(i);
        }
        funExprIndex++;
      }
    }
    return null;
  }

  private static @NotNull PsiFunctionalExpression getNonPhysicalCopy(Map<TextRange, PsiFile> fragmentCache,
                                                                     JavaFunctionalExpressionIndex.IndexEntry entry,
                                                                     PsiFunctionalExpression expression) {
    PsiFile file = expression.getContainingFile();
    FileViewProvider viewProvider = file.getViewProvider();
    try {
      PsiMember member = Objects.requireNonNull(PsiTreeUtil.getStubOrPsiParentOfType(expression, PsiMember.class));
      PsiFunctionalExpression psi = null;
      Exception ex = null;
      try {
        PsiFile fragment = fragmentCache.computeIfAbsent(TextRange.create(entry.contextStart, entry.contextEnd),
                                                         range -> createMemberCopyFromText(member, range));
        psi = findPsiByAST(fragment, entry.exprStart - entry.contextStart);
      }
      catch (IncorrectOperationException e) {
        ex = e;
      }
      if (psi == null) {
        StubTextInconsistencyException.checkStubTextConsistency(file, StubInconsistencyReporter.SourceOfCheck.NoPsiMatchingASTinJava);
        throw new RuntimeExceptionWithAttachments(
          "No functional expression at " + entry + ", file will be reindexed",
          ex, new Attachment(viewProvider.getVirtualFile().getPath(), viewProvider.getContents().toString()));
      }
      return psi;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      FileBasedIndex.getInstance().requestReindex(viewProvider.getVirtualFile());
      LOG.error(e);
      return expression;
    }
  }

  private static PsiFile createMemberCopyFromText(@NotNull PsiMember member, @NotNull TextRange memberRange) {
    PsiFile file = member.getContainingFile();
    CharSequence contents = file.getViewProvider().getContents();
    if (memberRange.getEndOffset() > contents.length()) {
      StubTextInconsistencyException.checkStubTextConsistency(file, StubInconsistencyReporter.SourceOfCheck.OffsetOutsideFileInJava);
      throw new RuntimeExceptionWithAttachments(
        "Range from the index " + memberRange + " exceeds the actual file length " + contents.length() + ", file will be reindexed",
        new Attachment(file.getVirtualFile().getPath(), contents.toString()));
    }
    String contextText = memberRange.subSequence(contents).toString();
    Project project = file.getProject();
    return member instanceof PsiEnumConstant
           ? PsiElementFactory.getInstance(project).createEnumConstantFromText(contextText, member).getContainingFile()
           : JavaCodeFragmentFactory.getInstance(project).createMemberCodeFragment(contextText, member, false);
  }

  private static boolean hasType(@NotNull List<? extends SamDescriptor> descriptors, @NotNull PsiFunctionalExpression expression) {
    ThreeState approximate = approximateHasType(expression, ContainerUtil.map(descriptors, d -> d.samClass));
    if (approximate != ThreeState.UNSURE) return approximate == ThreeState.YES;

    PsiClass actualClass = LambdaUtil.resolveFunctionalInterfaceClass(expression);
    return ContainerUtil.exists(descriptors, d -> InheritanceUtil.isInheritorOrSelf(actualClass, d.samClass, true));
  }

  private static ThreeState approximateHasType(@NotNull PsiFunctionalExpression expression, @NotNull List<? extends PsiClass> samClasses) {
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiMethodCallExpression) {
      PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
      int argIndex = Arrays.asList(args).indexOf(expression);
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)parent.getParent()).getMethodExpression();
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      String methodName = methodExpression.getReferenceName();
      if (qualifier != null && methodName != null && argIndex >= 0) {
        Set<PsiClass> approximateTypes = ApproximateResolver.getPossibleTypes(qualifier, 10);
        List<PsiMethod> methods = approximateTypes == null ? null :
                                  ApproximateResolver.getPossibleMethods(approximateTypes, methodName, args.length);
        return methods == null ? ThreeState.UNSURE :
               methods.isEmpty() ? ThreeState.NO :
               ThreeState.merge(JBIterable.from(methods).map(m -> FunExprOccurrence.hasCompatibleParameter(m, argIndex, samClasses)));
      }
    }
    return ThreeState.UNSURE;
  }

  private static boolean hasModuleWithFunctionalExpressions(@NotNull Project project) {
    final boolean projectLevelIsHigh = JavaFeature.LAMBDA_EXPRESSIONS.isSufficient(PsiUtil.getLanguageLevel(project));

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final LanguageLevelModuleExtension extension = ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
      if (extension != null) {
        final LanguageLevel level = extension.getLanguageLevel();
        if (level == null ? projectLevelIsHigh : JavaFeature.LAMBDA_EXPRESSIONS.isSufficient(level)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void processSubInterfaces(@NotNull PsiClass base, @NotNull Set<? super PsiClass> visited) {
    if (!visited.add(base)) return;

    DirectClassInheritorsSearch.search(base).forEach(candidate -> {
      if (candidate.isInterface()) {
        processSubInterfaces(candidate, visited);
      }
      return true;
    });
  }

  private static class SamDescriptor {
    final PsiClass samClass;
    final int samParamCount;
    final boolean booleanCompatible;
    final boolean isVoid;
    final DumbService dumbService;
    final List<FunctionalExpressionKey> keys;
    GlobalSearchScope effectiveUseScope;

    SamDescriptor(@NotNull PsiClass samClass, @NotNull PsiMethod samMethod, @NotNull PsiType samType, @NotNull GlobalSearchScope useScope) {
      this.samClass = samClass;
      effectiveUseScope = useScope;
      samParamCount = samMethod.getParameterList().getParametersCount();
      booleanCompatible = FunctionalExpressionKey.isBooleanCompatible(samType);
      isVoid = PsiTypes.voidType().equals(samType);
      dumbService = DumbService.getInstance(samClass.getProject());
      keys = generateKeys();
    }

    private @NotNull List<FunctionalExpressionKey> generateKeys() {
      String name = samClass.isValid() ? samClass.getName() : null;
      if (name == null) return Collections.emptyList();

      List<FunctionalExpressionKey> result = new ArrayList<>();
      for (String lambdaType : new String[]{Objects.requireNonNull(name), ""}) {
        for (int lambdaParamCount : new int[]{FunctionalExpressionKey.UNKNOWN_PARAM_COUNT, samParamCount}) {
          result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.UNKNOWN, lambdaType));
          if (isVoid) {
            result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.VOID, lambdaType));
          } else {
            if (booleanCompatible) {
              result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.BOOLEAN, lambdaType));
            }
            result.add(new FunctionalExpressionKey(lambdaParamCount, FunctionalExpressionKey.CoarseType.NON_VOID, lambdaType));
          }
        }
      }

      return result;
    }

    private @NotNull Set<VirtualFile> getMostLikelyFiles(@NotNull GlobalSearchScope searchScope) {
      Set<VirtualFile> files = new LinkedHashSet<>();
      dumbService.runReadActionInSmartMode(() -> {
        if (!samClass.isValid()) return;

        String className = samClass.getName();
        Project project = samClass.getProject();
        if (className == null) return;

        Set<String> likelyNames = ContainerUtil.newLinkedHashSet(className);
        StubIndex.getInstance().processElements(JavaMethodParameterTypesIndex.getInstance().getKey(), className,
                                                project, effectiveUseScope, PsiMethod.class, method -> {
            ProgressManager.checkCanceled();
            likelyNames.add(method.getName());
            return true;
          });

        PsiSearchHelperImpl helper = (PsiSearchHelperImpl)PsiSearchHelper.getInstance(project);
        Processor<VirtualFile> processor = Processors.cancelableCollectProcessor(files);
        for (String word : likelyNames) {
          helper.processCandidateFilesForText(searchScope, UsageSearchContext.IN_CODE, true, true, word, processor);
        }
      });
      return files;
    }
  }

  private static boolean performSearchUsingCompilerIndices(@NotNull List<? extends SamDescriptor> descriptors,
                                                           @NotNull GlobalSearchScope searchScope,
                                                           @NotNull Project project,
                                                           @NotNull Processor<? super PsiFunctionalExpression> consumer) {
    CompilerReferenceService compilerReferenceService = CompilerReferenceService.getInstanceIfEnabled(project);
    if (compilerReferenceService == null) {
      return true;
    }
    for (SamDescriptor descriptor : descriptors) {
      CompilerDirectHierarchyInfo info = compilerReferenceService.getFunExpressions(descriptor.samClass, searchScope, JavaFileType.INSTANCE);
      if (info != null && !processFunctionalExpressions(info, descriptor, consumer)) {
        return false;
      }
    }
    return true;
  }


  private static boolean processFunctionalExpressions(@NotNull CompilerDirectHierarchyInfo funExprInfo,
                                                      @NotNull SamDescriptor descriptor,
                                                      @NotNull Processor<? super PsiFunctionalExpression> consumer) {
    if (!ContainerUtil.process(funExprInfo.getHierarchyChildren().iterator(), fe -> consumer.process((PsiFunctionalExpression)fe))) {
      return false;
    }
    GlobalSearchScope dirtyScope = funExprInfo.getDirtyScope();
    descriptor.effectiveUseScope = descriptor.effectiveUseScope.intersectWith(dirtyScope);
    return true;
  }

  @VisibleForTesting
  public static class Session {
    private final Processor<? super PsiFunctionalExpression> consumer;
    private final Project project;
    private final SearchScope scope;
    private final PsiClass elementToSearch;

    // statistics
    private final AtomicInteger filesConsidered = new AtomicInteger();
    private final AtomicInteger contextsConsidered = new AtomicInteger();
    private final AtomicInteger sureExprsAfterLightCheck = new AtomicInteger();
    private final AtomicInteger exprsToHeavyCheck = new AtomicInteger();
    private final Set<VirtualFile> filesLookedInside = ConcurrentCollectionFactory.createConcurrentSet();
    private final @Nullable PsiMethod method;

    public Session(@NotNull SearchParameters parameters, @NotNull Processor<? super PsiFunctionalExpression> consumer) {
      this.consumer = consumer;
      elementToSearch = parameters.getElementToSearch();
      method = parameters.getMethod();
      project = parameters.getProject();
      scope = parameters.getEffectiveSearchScope();
    }

    @TestOnly
    public @NotNull Set<VirtualFile> getFilesLookedInside() {
      return filesLookedInside;
    }

    public void processResults() {
      List<SamDescriptor> descriptors = calcDescriptors(this);

      if (scope instanceof GlobalSearchScope &&
          !performSearchUsingCompilerIndices(descriptors, (GlobalSearchScope)scope, project, consumer)) {
        return;
      }

      PsiManager.getInstance(project).runInBatchFilesMode(() -> {
        processOffsets(descriptors, this);
        return null;
      });
    }

    @Override
    public String toString() {
      return "filesConsidered=" + filesConsidered +
             ", contextsConsidered=" + contextsConsidered +
             ", sureExprsAfterLightCheck=" + sureExprsAfterLightCheck +
             ", exprsToHeavyCheck=" + exprsToHeavyCheck +
             ", filesLookedInside=" + filesLookedInside.size();
    }
  }

}