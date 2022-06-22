// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.Divider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobLauncher;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class InspectionEngine {
  private static final Logger LOG = Logger.getInstance(InspectionEngine.class);
  private static final Set<Class<? extends LocalInspectionTool>> RECURSIVE_VISITOR_TOOL_CLASSES = ContainerUtil.newConcurrentSet();

  public static boolean createVisitorAndAcceptElements(@NotNull LocalInspectionTool tool,
                                                     @NotNull ProblemsHolder holder,
                                                     boolean isOnTheFly,
                                                     @NotNull LocalInspectionToolSession session,
                                                     @NotNull List<? extends PsiElement> elements) {
    PsiElementVisitor visitor = createVisitor(tool, holder, isOnTheFly, session);
    // if inspection returned empty visitor then it should be skipped
    if (visitor == PsiElementVisitor.EMPTY_VISITOR) return false;

    tool.inspectionStarted(session, isOnTheFly);
    acceptElements(elements, visitor);
    tool.inspectionFinished(session, holder);
    return true;
  }

  @NotNull
  public static PsiElementVisitor createVisitor(@NotNull LocalInspectionTool tool,
                                                @NotNull ProblemsHolder holder,
                                                boolean isOnTheFly,
                                                @NotNull LocalInspectionToolSession session) {
    PsiElementVisitor visitor = tool.buildVisitor(holder, isOnTheFly, session);
    //noinspection ConstantConditions
    if (visitor == null) {
      LOG.error("Tool " + tool + " (" + tool.getClass() + ") must not return null from the buildVisitor() method");
    }
    else if (visitor instanceof PsiRecursiveVisitor && RECURSIVE_VISITOR_TOOL_CLASSES.add(tool.getClass())) {
      LOG.error("The visitor returned from LocalInspectionTool.buildVisitor() must not be recursive: " + tool);
    }
    return visitor;
  }

  public static void acceptElements(@NotNull List<? extends PsiElement> elements, @NotNull PsiElementVisitor elementVisitor) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, elementsSize = elements.size(); i < elementsSize; i++) {
      PsiElement element = elements.get(i);
      element.accept(elementVisitor);
      ProgressManager.checkCanceled();
    }
  }

  /**
   * @deprecated use {@link #inspectEx(List, PsiFile, TextRange, TextRange, boolean, boolean, boolean, ProgressIndicator, PairProcessor)}
   */
  @Deprecated
  // returns map (toolName -> problem descriptors)
  public static @NotNull Map<String, List<ProblemDescriptor>> inspectEx(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                        @NotNull PsiFile file,
                                                                        @NotNull InspectionManager iManager,
                                                                        boolean isOnTheFly,
                                                                        @NotNull ProgressIndicator indicator) {
    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
      inspectEx(toolWrappers, file, file.getTextRange(), file.getTextRange(), isOnTheFly, false, true, indicator, PairProcessor.alwaysTrue());
    return map.entrySet().stream().map(e->Pair.create(e.getKey().getShortName(), e.getValue())).collect(Collectors.toMap(p->p.getFirst(), p->p.getSecond()));
  }

  // returns map (tool -> problem descriptors)
  @NotNull
  public static Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> inspectEx(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                                   @NotNull PsiFile file,
                                                                                   @NotNull TextRange restrictRange,
                                                                                   @NotNull TextRange priorityRange,
                                                                                   boolean isOnTheFly,
                                                                                   boolean inspectInjectedPsi,
                                                                                   boolean ignoreSuppressedElements,
                                                                                   @NotNull ProgressIndicator indicator,
                                                                                   // when returned true -> add to the holder, false -> do not add to the holder
                                                                                   @NotNull PairProcessor<? super LocalInspectionToolWrapper, ? super ProblemDescriptor> foundDescriptorCallback) {
    if (toolWrappers.isEmpty()) return Collections.emptyMap();

    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(file, restrictRange, priorityRange, __ -> true, new CommonProcessors.CollectProcessor<>(allDivided));

    List<PsiElement> elements = ContainerUtil.concat(
      (List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> ContainerUtil.concat(d.inside, d.outside, d.parents)));

    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map = inspectElements(toolWrappers, file, restrictRange, ignoreSuppressedElements, isOnTheFly, indicator, elements, foundDescriptorCallback);
    if (inspectInjectedPsi) {
      InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(file.getProject());
      List<Pair<PsiFile, PsiElement>> injectedFiles = new ArrayList<>();
      for (PsiElement element : elements) {
        if (element instanceof PsiLanguageInjectionHost) {
          List<Pair<PsiElement, TextRange>> files = injectedLanguageManager.getInjectedPsiFiles(element);
          if (files != null) {
            for (Pair<PsiElement, TextRange> pair : files) {
              PsiFile injectedFile = (PsiFile)pair.getFirst();
              injectedFiles.add(Pair.create(injectedFile, element));
            }
          }
        }
      }
      if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(injectedFiles, indicator, pair -> {
        PsiFile injectedFile = pair.getFirst();
        PsiElement host = pair.getSecond();
        List<PsiElement> injectedElements = new ArrayList<>();
        Set<String> injectedDialects = new HashSet<>();
        getAllElementsAndDialectsFrom(injectedFile, injectedElements, injectedDialects);
        Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> result =
          inspectElements(toolWrappers, injectedFile, injectedFile.getTextRange(), isOnTheFly, indicator, ignoreSuppressedElements,
                          injectedElements,
                          injectedDialects, foundDescriptorCallback);
        for (Map.Entry<LocalInspectionToolWrapper, List<ProblemDescriptor>> entry : result.entrySet()) {
          LocalInspectionToolWrapper toolWrapper = entry.getKey();
          List<ProblemDescriptor> descriptors = entry.getValue();
          List<ProblemDescriptor> filtered = ignoreSuppressedElements ? ContainerUtil.filter(descriptors, descriptor -> !toolWrapper.getTool().isSuppressedFor(host)) : descriptors;
          // in case two injected fragments contain result of the same inspection, concatenate them
          // assume map is ConcurrentHashMap here, otherwise synchronization would be needed
          map.merge(toolWrapper, filtered, (oldList, newList)->ContainerUtil.concat(oldList, newList));
        }
        return true;
      })) {
        throw new ProcessCanceledException();
      }
    }

    return map;
  }

  private static void getAllElementsAndDialectsFrom(@NotNull PsiFile file,
                                                    @NotNull List<? super PsiElement> outElements,
                                                    @NotNull Set<? super String> outDialects) {
    FileViewProvider viewProvider = file.getViewProvider();
    Set<Language> processedLanguages = new SmartHashSet<>();
    // we hope that injected file here is small enough for PsiRecursiveElementVisitor
    PsiElementVisitor visitor = new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        ProgressManager.checkCanceled();
        PsiElement child = element.getFirstChild();
        while (child != null) {
          outElements.add(child);
          child.accept(this);
          appendDialects(child, processedLanguages, outDialects);
          child = child.getNextSibling();
        }
      }
    };
    for (Language language : viewProvider.getLanguages()) {
      PsiFile psiRoot = viewProvider.getPsi(language);
      if (psiRoot == null || !HighlightingLevelManager.getInstance(file.getProject()).shouldInspect(psiRoot)) {
        continue;
      }
      outElements.add(psiRoot);
      psiRoot.accept(visitor);
      appendDialects(psiRoot, processedLanguages, outDialects);
    }
  }

  private static void appendDialects(@NotNull PsiElement element,
                                     @NotNull Set<? super Language> outProcessedLanguages,
                                     @NotNull Set<? super String> outDialectIds) {
    Language language = element.getLanguage();
    outDialectIds.add(language.getID());
    if (outProcessedLanguages.add(language)) {
      for (Language dialect : language.getDialects()) {
        outDialectIds.add(dialect.getID());
      }
    }
  }

  // returns map tool -> list of descriptors found
  public static @NotNull Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> inspectElements(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                                                  @NotNull PsiFile file,
                                                                                                  @NotNull TextRange restrictRange,
                                                                                                  boolean ignoreSuppressedElements,
                                                                                                  boolean isOnTheFly,
                                                                                                  @NotNull ProgressIndicator indicator,
                                                                                                  @NotNull List<? extends PsiElement> elements,
                                                                                                  // when returned true -> add to the holder, false -> do not add to the holder
                                                                                                  @NotNull PairProcessor<? super LocalInspectionToolWrapper, ? super ProblemDescriptor> foundDescriptorCallback) {
    return inspectElements(toolWrappers, file, restrictRange, isOnTheFly, indicator, ignoreSuppressedElements, elements, calcElementDialectIds(elements),
                           foundDescriptorCallback);
  }

  @ApiStatus.Internal
  public static void withSession(@NotNull PsiFile file,
                                 @NotNull TextRange restrictRange,
                                 @NotNull TextRange priorityRange,
                                 boolean isOnTheFly,
                                 @NotNull Consumer<? super LocalInspectionToolSession> runnable) {
    LocalInspectionToolSession session = new LocalInspectionToolSession(file, priorityRange);
    runnable.accept(session);
  }

  private static final Set<String> ourToolsWithInformationProblems = ConcurrentCollectionFactory.createConcurrentSet(HashingStrategy.canonical());

  private static @NotNull Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> inspectElements(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                                                   @NotNull PsiFile file,
                                                                                                   @NotNull TextRange restrictRange,
                                                                                                   boolean isOnTheFly,
                                                                                                   @NotNull ProgressIndicator indicator,
                                                                                                   boolean ignoreSuppressedElements,
                                                                                                   @NotNull List<? extends PsiElement> elements,
                                                                                                   @NotNull Set<String> elementDialectIds,
                                                                                                   // when returned true -> add to the holder, false -> do not add to the holder
                                                                                                   @NotNull PairProcessor<? super LocalInspectionToolWrapper, ? super ProblemDescriptor> foundDescriptorCallback) {
    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> resultDescriptors = new ConcurrentHashMap<>();
    withSession(file, restrictRange, restrictRange, isOnTheFly, session -> {
      InspectListener publisher = file.getProject().getMessageBus().syncPublisher(GlobalInspectionContextEx.INSPECT_TOPIC);
      List<LocalInspectionToolWrapper> filtered = filterToolsApplicableByLanguage(toolWrappers, elementDialectIds);
      Processor<LocalInspectionToolWrapper> processor = toolWrapper -> {
        ProblemsHolder holder = new ProblemsHolder(InspectionManager.getInstance(file.getProject()), file, isOnTheFly){
          @Override
          public void registerProblem(@NotNull ProblemDescriptor descriptor) {
            if (!isOnTheFly) {
              ProblemHighlightType highlightType = descriptor.getHighlightType();
              if (highlightType == ProblemHighlightType.INFORMATION) {
                String shortName = toolWrapper.getShortName();
                if (ourToolsWithInformationProblems.add(shortName)) {
                  String message = "Tool #" + shortName + " (" + toolWrapper.getTool().getClass()+")"+
                                   " registers 'INFORMATION'-level problem in batch mode on " + file + ". " +
                                   "Warnings of the 'INFORMATION' level are invisible in the editor and should not become visible in batch mode. " +
                                   "Moreover, since 'INFORMATION'-level fixes act more like intention actions, they could e.g. change semantics and " +
                                   "thus should not be suggested for batch transformations";
                  LocalInspectionEP extension = toolWrapper.getExtension();
                  if (extension != null) {
                    LOG.error(new PluginException(message, extension.getPluginDescriptor().getPluginId()));
                  }
                  else {
                    LOG.error(message);
                  }
                }
                return;
              }
              if (highlightType == ProblemHighlightType.POSSIBLE_PROBLEM) {
                return;
              }
            }

            if (foundDescriptorCallback.process(toolWrapper, descriptor)) {
              super.registerProblem(descriptor);
            }
          }
        };
        LocalInspectionTool tool = toolWrapper.getTool();

        long inspectionStartTime = System.currentTimeMillis();
        boolean inspectionWasRun = createVisitorAndAcceptElements(tool, holder, isOnTheFly, session, elements);
        long inspectionDuration = System.currentTimeMillis() - inspectionStartTime;

        boolean needToReportStatsToQodana = (inspectionWasRun && !isOnTheFly);
        if (needToReportStatsToQodana) {
          publisher.inspectionFinished(inspectionDuration, Thread.currentThread().getId(), holder.getResultCount(), toolWrapper,
                                       InspectListener.InspectionKind.LOCAL, file, file.getProject());
        }

        if (holder.hasResults()) {
          List<ProblemDescriptor> descriptors = ContainerUtil.filter(holder.getResults(), descriptor -> {
            PsiElement element = descriptor.getPsiElement();
            return element == null || !ignoreSuppressedElements || !SuppressionUtil.inspectionResultSuppressed(element, tool);
          });
          resultDescriptors.put(toolWrapper, descriptors);
        }

        return true;
      };
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(filtered, indicator, processor);
    });

    return resultDescriptors;
  }

  public static @NotNull @Unmodifiable List<ProblemDescriptor> runInspectionOnFile(@NotNull PsiFile file,
                                                                                   @NotNull InspectionToolWrapper<?, ?> toolWrapper,
                                                                                   @NotNull GlobalInspectionContext inspectionContext) {
    InspectionManager inspectionManager = InspectionManager.getInstance(file.getProject());
    toolWrapper.initialize(inspectionContext);
    RefManagerImpl refManager = (RefManagerImpl)inspectionContext.getRefManager();
    List<ProblemDescriptor> result = new ArrayList<>();
    refManager.runInsideInspectionReadAction(() -> {
      try {
        if (toolWrapper instanceof LocalInspectionToolWrapper) {
          Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> problemDescriptors =
            inspectEx(Collections.singletonList((LocalInspectionToolWrapper)toolWrapper), file, file.getTextRange(), file.getTextRange(),
                      false,
                      false, true, new EmptyProgressIndicator(), PairProcessor.alwaysTrue());

          for (List<ProblemDescriptor> group : problemDescriptors.values()) {
            result.addAll(group);
          }
        }
        else if (toolWrapper instanceof GlobalInspectionToolWrapper) {
          GlobalInspectionTool globalTool = ((GlobalInspectionToolWrapper)toolWrapper).getTool();
          if (globalTool instanceof GlobalSimpleInspectionTool) {
            GlobalSimpleInspectionTool simpleTool = (GlobalSimpleInspectionTool)globalTool;
            ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, file, false);
            ProblemDescriptionsProcessor collectProcessor = new ProblemDescriptionsProcessor() {
              @Override
              public CommonProblemDescriptor[] getDescriptions(@NotNull RefEntity refEntity) {
                return result.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
              }

              @Override
              public void ignoreElement(@NotNull RefEntity refEntity) {
                throw new UnsupportedOperationException();
              }

              @Override
              public void resolveProblem(@NotNull CommonProblemDescriptor descriptor) {
                throw new UnsupportedOperationException();
              }

              @Override
              public void addProblemElement(@Nullable RefEntity refEntity, CommonProblemDescriptor @NotNull ... commonProblemDescriptors) {
                if (!(refEntity instanceof RefElement)) return;
                PsiElement element = ((RefElement)refEntity).getPsiElement();
                convertToProblemDescriptors(element, commonProblemDescriptors, result);
              }

              @Override
              public RefEntity getElement(@NotNull CommonProblemDescriptor descriptor) {
                throw new RuntimeException();
              }
            };
            simpleTool.checkFile(file, inspectionManager, problemsHolder, inspectionContext, collectProcessor);
          }
          else {
            RefElement fileRef = refManager.getReference(file);
            AnalysisScope scope = new AnalysisScope(file);
            assert fileRef != null;
            fileRef.accept(new RefVisitor() {
              @Override
              public void visitElement(@NotNull RefEntity elem) {
                CommonProblemDescriptor[] elemDescriptors = globalTool.checkElement(elem, scope, inspectionManager, inspectionContext);
                if (elemDescriptors != null) {
                  convertToProblemDescriptors(file, elemDescriptors, result);
                }

                for (RefEntity child : elem.getChildren()) {
                  child.accept(this);
                }
              }
            });
          }
        }
      }
      finally {
        toolWrapper.cleanup(file.getProject());
        inspectionContext.cleanup();
      }
    });
    return result;
  }

  private static void convertToProblemDescriptors(@NotNull PsiElement element,
                                                  CommonProblemDescriptor @NotNull [] commonProblemDescriptors,
                                                  @NotNull List<? super ProblemDescriptor> outDescriptors) {
    for (CommonProblemDescriptor common : commonProblemDescriptors) {
      if (common instanceof ProblemDescriptor) {
        outDescriptors.add((ProblemDescriptor)common);
      }
      else {
        ProblemDescriptorBase base =
          new ProblemDescriptorBase(element, element, common.getDescriptionTemplate(), (LocalQuickFix[])common.getFixes(),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, null, false, false);
        outDescriptors.add(base);
      }
    }
  }

  public static @NotNull List<LocalInspectionToolWrapper> filterToolsApplicableByLanguage(@NotNull Collection<? extends LocalInspectionToolWrapper> tools,
                                                                                          @NotNull Set<String> elementDialectIds) {
    Map<String, Boolean> resultsWithDialects = new HashMap<>();
    Map<String, Boolean> resultsNoDialects = new HashMap<>();
    return ContainerUtil.filter(tools, tool -> {
      String language = tool.getLanguage();
      if (language == null) return true;

      boolean applyToDialects = tool.applyToDialects();
      Map<String, Boolean> map = applyToDialects ? resultsWithDialects : resultsNoDialects;
      return map.computeIfAbsent(language, __ ->
        ContainerUtil.intersects(elementDialectIds, getDialectIdsSpecifiedForTool(language, applyToDialects)));
    });
  }

  private static @NotNull Set<String> getDialectIdsSpecifiedForTool(@NotNull String langId, boolean applyToDialects) {
    Language language = Language.findLanguageByID(langId);
    Set<String> result;
    if (language == null) {
      // unknown language in plugin.xml, ignore
      result = Collections.singleton(langId);
    }
    else if (language instanceof MetaLanguage) {
      Collection<Language> matchingLanguages = ((MetaLanguage) language).getMatchingLanguages();
      result = new HashSet<>();
      for (Language matchingLanguage : matchingLanguages) {
        result.addAll(getLanguageWithDialects(matchingLanguage, applyToDialects));
      }
    }
    else {
      result = getLanguageWithDialects(language, applyToDialects);
    }
    return result;
  }

  private static @NotNull Set<String> getLanguageWithDialects(@NotNull Language language, boolean applyToDialects) {
    List<Language> dialects = language.getDialects();
    if (!applyToDialects || dialects.isEmpty()) return Collections.singleton(language.getID());

    Set<String> result = new HashSet<>(1 + dialects.size());
    result.add(language.getID());
    addDialects(language, result);
    return result;
  }

  private static void addDialects(@NotNull Language language, @NotNull Set<? super String> result) {
    for (Language dialect : language.getDialects()) {
      if (result.add(dialect.getID())) {
        addDialects(dialect, result);
      }
    }
  }

  public static @NotNull Set<String> calcElementDialectIds(@NotNull List<? extends PsiElement> inside, @NotNull List<? extends PsiElement> outside) {
    Set<String> dialectIds = new HashSet<>();
    Set<Language> processedLanguages = new HashSet<>();
    addDialects(inside, processedLanguages, dialectIds);
    addDialects(outside, processedLanguages, dialectIds);
    return dialectIds;
  }

  public static @NotNull Set<String> calcElementDialectIds(@NotNull List<? extends PsiElement> elements) {
    Set<String> dialectIds = new HashSet<>();
    Set<Language> processedLanguages = new HashSet<>();
    addDialects(elements, processedLanguages, dialectIds);
    return dialectIds;
  }

  private static void addDialects(@NotNull List<? extends PsiElement> elements,
                                  @NotNull Set<? super Language> outProcessedLanguages,
                                  @NotNull Set<? super String> outDialectIds) {
    for (PsiElement element : elements) {
      Language language = element.getLanguage();
      outDialectIds.add(language.getID());
      addDialects(language, outProcessedLanguages, outDialectIds);
    }
  }

  private static void addDialects(@NotNull Language language,
                                  @NotNull Set<? super Language> outProcessedLanguages,
                                  @NotNull Set<? super String> outDialectIds) {
    if (outProcessedLanguages.add(language)) {
      for (Language dialect : language.getDialects()) {
        outDialectIds.add(dialect.getID());
        addDialects(dialect, outProcessedLanguages, outDialectIds);
      }
    }
  }
}
