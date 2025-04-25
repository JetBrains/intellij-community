// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.Divider;
import com.intellij.codeInsight.daemon.impl.InspectionVisitorOptimizer;
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
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class InspectionEngine {
  private static final Logger LOG = Logger.getInstance(InspectionEngine.class);

  public static @NotNull PsiElementVisitor createVisitor(@NotNull LocalInspectionTool tool,
                                                         @NotNull ProblemsHolder holder,
                                                         boolean isOnTheFly,
                                                         @NotNull LocalInspectionToolSession session) {
    if (!tool.isAvailableForFile(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    PsiElementVisitor visitor;
    try {
      visitor = tool.buildVisitor(holder, isOnTheFly, session);
    }
    catch (Throwable e) {
      if (Logger.shouldRethrow(e)) {
        throw e;
      }
      Throwable t = PluginException.createByClass("Inspection tool '"+tool.getShortName()+"' ("+tool.getClass()+") thrown exception from its buildVisitor()", e, tool.getClass());
      LOG.error(t);
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    //noinspection ConstantConditions
    if (visitor == null) {
      LOG.error("Tool " + tool + " (" + tool.getClass() + ") must not return null from the buildVisitor() method");
    }
    else if (visitor instanceof PsiRecursiveVisitor) {
      LOG.error("The visitor returned from LocalInspectionTool.buildVisitor() must not be recursive: " + tool);
    }
    return visitor;
  }

  /**
   * @deprecated use {@link #inspectEx(List, PsiFile, TextRange, TextRange, boolean, boolean, boolean, ProgressIndicator, PairProcessor)}
   */
  @Deprecated
  // returns map (toolName -> problem descriptors)
  public static @NotNull Map<String, List<ProblemDescriptor>> inspectEx(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                        @NotNull PsiFile psiFile,
                                                                        @NotNull InspectionManager iManager,
                                                                        boolean isOnTheFly,
                                                                        @NotNull ProgressIndicator indicator) {
    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
      inspectEx(toolWrappers, psiFile, psiFile.getTextRange(), psiFile.getTextRange(), isOnTheFly, false, true, indicator, PairProcessor.alwaysTrue());
    return map.entrySet().stream().map(e->Pair.create(e.getKey().getShortName(), e.getValue())).collect(Collectors.toMap(p->p.getFirst(), p->p.getSecond()));
  }

  public static @NotNull Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> inspectEx(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                                            @NotNull PsiFile psiFile,
                                                                                            @NotNull TextRange restrictRange,
                                                                                            @NotNull TextRange priorityRange,
                                                                                            boolean isOnTheFly,
                                                                                            boolean inspectInjectedPsi,
                                                                                            boolean ignoreSuppressedElements,
                                                                                            @NotNull ProgressIndicator indicator,
                                                                                            // when returned true -> add to the holder, false -> do not add to the holder
                                                                                            @NotNull PairProcessor<? super LocalInspectionToolWrapper, ? super ProblemDescriptor> foundDescriptorCallback) {
    return inspectEx(toolWrappers, psiFile, restrictRange, priorityRange, isOnTheFly, inspectInjectedPsi, ignoreSuppressedElements, indicator, null, foundDescriptorCallback);
  }

  @ApiStatus.Internal
  // returns map (tool -> problem descriptors)
  public static @NotNull Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> inspectEx(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                                   @NotNull PsiFile psiFile,
                                                                                   @NotNull TextRange restrictRange,
                                                                                   @NotNull TextRange priorityRange,
                                                                                   boolean isOnTheFly,
                                                                                   boolean inspectInjectedPsi,
                                                                                   boolean ignoreSuppressedElements,
                                                                                   @NotNull ProgressIndicator indicator,
                                                                                   @Nullable UserDataHolderBase userData,
                                                                                   // when returned true -> add to the holder, false -> do not add to the holder
                                                                                   @NotNull PairProcessor<? super LocalInspectionToolWrapper, ? super ProblemDescriptor> foundDescriptorCallback) {
    if (toolWrappers.isEmpty()) return Collections.emptyMap();

    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(psiFile, restrictRange, priorityRange, Predicates.alwaysTrue(), new CommonProcessors.CollectProcessor<>(allDivided));

    List<PsiElement> elements = ContainerUtil.concat(
      ContainerUtil.map(allDivided, d -> ContainerUtil.concat(d.inside(), d.outside(), d.parents())));

    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map = inspectElements(toolWrappers, psiFile, restrictRange, ignoreSuppressedElements, isOnTheFly, indicator, elements, userData, foundDescriptorCallback);
    if (inspectInjectedPsi) {
      InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(psiFile.getProject());
      Set<Pair<PsiFile, PsiElement>> injectedFiles = new HashSet<>();
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
      if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(injectedFiles), indicator, pair -> {
        PsiFile injectedFile = pair.getFirst();
        PsiElement host = pair.getSecond();
        List<PsiElement> injectedElements = new ArrayList<>();
        Set<String> injectedDialects = new HashSet<>();
        getAllElementsAndDialectsFrom(injectedFile, injectedElements, injectedDialects);
        Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> result =
          inspectElements(toolWrappers, injectedFile, injectedFile.getTextRange(), isOnTheFly, indicator, ignoreSuppressedElements,
                          injectedElements, injectedDialects, userData, foundDescriptorCallback);
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

  private static void getAllElementsAndDialectsFrom(@NotNull PsiFile psiFile,
                                                    @NotNull List<? super PsiElement> outElements,
                                                    @NotNull Set<? super String> outDialects) {
    FileViewProvider viewProvider = psiFile.getViewProvider();
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
      if (psiRoot == null || !HighlightingLevelManager.getInstance(psiFile.getProject()).shouldInspect(psiRoot)) {
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
                                                                                                  @NotNull PsiFile psiFile,
                                                                                                  @NotNull TextRange restrictRange,
                                                                                                  boolean ignoreSuppressedElements,
                                                                                                  boolean isOnTheFly,
                                                                                                  @NotNull ProgressIndicator indicator,
                                                                                                  @NotNull List<? extends PsiElement> elements,
                                                                                                  // when returned true -> add to the holder, false -> do not add to the holder
                                                                                                  @NotNull PairProcessor<? super LocalInspectionToolWrapper, ? super ProblemDescriptor> foundDescriptorCallback) {
    return inspectElements(toolWrappers, psiFile, restrictRange, isOnTheFly, indicator, ignoreSuppressedElements, elements, calcElementDialectIds(elements),
                           null, foundDescriptorCallback);
  }

  @ApiStatus.Internal
  // returns map tool -> list of descriptors found
  public static @NotNull Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> inspectElements(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                                                  @NotNull PsiFile psiFile,
                                                                                                  @NotNull TextRange restrictRange,
                                                                                                  boolean ignoreSuppressedElements,
                                                                                                  boolean isOnTheFly,
                                                                                                  @NotNull ProgressIndicator indicator,
                                                                                                  @NotNull List<? extends PsiElement> elements,
                                                                                                  @Nullable UserDataHolderBase userData,
                                                                                                  // when returned true -> add to the holder, false -> do not add to the holder
                                                                                                  @NotNull PairProcessor<? super LocalInspectionToolWrapper, ? super ProblemDescriptor> foundDescriptorCallback) {
    return inspectElements(toolWrappers, psiFile, restrictRange, isOnTheFly, indicator, ignoreSuppressedElements, elements, calcElementDialectIds(elements),
                           userData, foundDescriptorCallback);
  }

  @ApiStatus.Internal
  public static void withSession(@NotNull PsiFile psiFile,
                                 @NotNull TextRange restrictRange,
                                 @NotNull TextRange priorityRange,
                                 @Nullable HighlightSeverity minimumSeverity,
                                 boolean isOnTheFly,
                                 @Nullable UserDataHolderBase userData,
                                 @NotNull Consumer<? super LocalInspectionToolSession> runnable) {
    LocalInspectionToolSession session = new LocalInspectionToolSession(psiFile, priorityRange, restrictRange, minimumSeverity);
    if (userData != null) {
      userData.copyUserDataTo(session);
    }
    runnable.accept(session);
  }

  private static final Set<String> ourToolsWithInformationProblems = ConcurrentCollectionFactory.createConcurrentSet();

  private static boolean warnAboutInformationLevelInBatchMode(@NotNull ProblemHighlightType highlightType,
                                                              @NotNull LocalInspectionToolWrapper toolWrapper,
                                                              @NotNull PsiFile psiFile) {
    if (highlightType == ProblemHighlightType.INFORMATION) {
      String shortName = toolWrapper.getShortName();
      if (ourToolsWithInformationProblems.add(shortName)) {
        String message = "Tool #" + shortName + " (" + toolWrapper.getTool().getClass()+")"+
                         " registers 'INFORMATION'-level problem in batch mode on " + psiFile + ". " +
                         "Warnings of the 'INFORMATION' level are invisible in the editor and should not become visible in batch mode. " +
                         "Moreover, since 'INFORMATION'-level fixes act more like intention actions, they could e.g. change semantics and " +
                         "thus should not be suggested for batch transformations";
        LocalInspectionEP extension = toolWrapper.getExtension();
        if (extension == null) {
          LOG.error(message);
        }
        else {
          LOG.warn(new PluginException(message, extension.getPluginDescriptor().getPluginId()));
        }
      }
      return true;
    }
    return false;
  }

  private static @NotNull Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> inspectElements(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                                                   @NotNull PsiFile psiFile,
                                                                                                   @NotNull TextRange restrictRange,
                                                                                                   boolean isOnTheFly,
                                                                                                   @NotNull ProgressIndicator indicator,
                                                                                                   boolean ignoreSuppressedElements,
                                                                                                   @NotNull List<? extends PsiElement> elements,
                                                                                                   @NotNull Set<String> elementDialectIds,
                                                                                                   @Nullable UserDataHolderBase userData,
                                                                                                   // when returned true -> add to the holder, false -> do not add to the holder
                                                                                                   @NotNull PairProcessor<? super LocalInspectionToolWrapper, ? super ProblemDescriptor> foundDescriptorCallback) {
    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> resultDescriptors = new ConcurrentHashMap<>();
    withSession(psiFile, restrictRange, restrictRange, HighlightSeverity.INFORMATION, isOnTheFly, userData, session -> {
      List<LocalInspectionToolWrapper> applicableTools = filterToolsApplicableByLanguage(toolWrappers, elementDialectIds, elementDialectIds);

      InspectionVisitorOptimizer inspectionVisitorsOptimizer = new InspectionVisitorOptimizer(elements);

      InspectListener inspectionListener = isOnTheFly ? null : psiFile.getProject().getMessageBus().syncPublisher(GlobalInspectionContextEx.INSPECT_TOPIC);

      Processor<LocalInspectionToolWrapper> processor = toolWrapper -> {
        ProblemsHolder holder = new ProblemsHolder(InspectionManager.getInstance(psiFile.getProject()), psiFile, isOnTheFly) {
          @Override
          public void registerProblem(@NotNull ProblemDescriptor descriptor) {
            if (!isOnTheFly) {
              ProblemHighlightType highlightType = descriptor.getHighlightType();
              if (warnAboutInformationLevelInBatchMode(highlightType, toolWrapper, psiFile)) {
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

        try {
          long inspectionStartTime = System.nanoTime();
          boolean inspectionWasRun;
          PsiElementVisitor visitor = createVisitor(tool, holder, isOnTheFly, session);
          // if inspection returned an empty visitor, then it should be skipped
          if (visitor == PsiElementVisitor.EMPTY_VISITOR) {
            inspectionWasRun = false;
          }
          else {
            inspectionWasRun = true;
            tool.inspectionStarted(session, isOnTheFly);
            inspectionVisitorsOptimizer.acceptElements(elements, visitor);
            tool.inspectionFinished(session, holder);
          }

          long inspectionDuration = TimeoutUtil.getDurationMillis(inspectionStartTime);

          if (inspectionListener != null && inspectionWasRun) {
            inspectionListener.inspectionFinished(
              inspectionDuration,
              Thread.currentThread().getId(),
              holder.getResultCount(),
              toolWrapper,
              InspectListener.InspectionKind.LOCAL,
              psiFile,
              psiFile.getProject()
            );
          }
        }
        catch (Exception e) {
          if (inspectionListener != null) {
            inspectionListener.inspectionFailed(
              toolWrapper.getID(),
              e,
              psiFile,
              psiFile.getProject()
            );
          }
          throw e;
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
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(applicableTools, indicator, processor);
    });

    return resultDescriptors;
  }

  public static @NotNull @Unmodifiable List<ProblemDescriptor> runInspectionOnFile(@NotNull PsiFile psiFile,
                                                                                   @NotNull InspectionToolWrapper<?, ?> toolWrapper,
                                                                                   @NotNull GlobalInspectionContext inspectionContext) {
    InspectionManager inspectionManager = InspectionManager.getInstance(psiFile.getProject());
    toolWrapper.initialize(inspectionContext);
    RefManagerImpl refManager = (RefManagerImpl)inspectionContext.getRefManager();
    List<ProblemDescriptor> result = new ArrayList<>();
    refManager.runInsideInspectionReadAction(() -> {
      try {
        if (toolWrapper instanceof LocalInspectionToolWrapper) {
          Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> problemDescriptors =
            inspectEx(Collections.singletonList((LocalInspectionToolWrapper)toolWrapper), psiFile, psiFile.getTextRange(), psiFile.getTextRange(),
                      false,
                      false, true, new EmptyProgressIndicator(), PairProcessor.alwaysTrue());

          for (List<ProblemDescriptor> group : problemDescriptors.values()) {
            result.addAll(group);
          }
        }
        else if (toolWrapper instanceof GlobalInspectionToolWrapper) {
          GlobalInspectionTool globalTool = ((GlobalInspectionToolWrapper)toolWrapper).getTool();
          if (globalTool.isGlobalSimpleInspectionTool()) {
            ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, psiFile, false);
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
            globalTool.checkFile(psiFile, inspectionManager, problemsHolder, inspectionContext, collectProcessor);
          }
          else {
            RefElement fileRef = refManager.getReference(psiFile);
            AnalysisScope scope = new AnalysisScope(psiFile);
            assert fileRef != null;
            fileRef.accept(new RefVisitor() {
              @Override
              public void visitElement(@NotNull RefEntity elem) {
                CommonProblemDescriptor[] elemDescriptors = globalTool.checkElement(elem, scope, inspectionManager, inspectionContext);
                if (elemDescriptors != null) {
                  convertToProblemDescriptors(psiFile, elemDescriptors, result);
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
        toolWrapper.cleanup(psiFile.getProject());
        inspectionContext.cleanup();
      }
    });
    return result;
  }

  private static void convertToProblemDescriptors(@NotNull PsiElement element,
                                                  @NotNull CommonProblemDescriptor @NotNull [] commonProblemDescriptors,
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

  public static @NotNull @Unmodifiable List<LocalInspectionToolWrapper> filterToolsApplicableByLanguage(@NotNull Collection<? extends LocalInspectionToolWrapper> tools,
                                                                                                        @NotNull Set<String> elementDialectIdsForRegularTool,
                                                                                                        @NotNull Set<String> elementDialectIdsForWholeFileTool) {
    Map<String, Boolean> resultsWithDialects = new HashMap<>();
    Map<String, Boolean> resultsNoDialects = new HashMap<>();
    return ContainerUtil.filter(tools, tool -> {
      String toolLanguageId = tool.getLanguage();
      if (toolLanguageId == null || toolLanguageId.isBlank() || "any".equals(toolLanguageId)) return true;

      boolean applyToDialects = tool.applyToDialects();
      Map<String, Boolean> map = applyToDialects ? resultsWithDialects : resultsNoDialects;
      return map.computeIfAbsent(toolLanguageId, __ ->
        ToolLanguageUtil.isToolLanguageOneOf(tool.runForWholeFile() ? elementDialectIdsForWholeFileTool : elementDialectIdsForRegularTool, toolLanguageId, applyToDialects));
    });
  }

  public static @NotNull Set<String> calcElementDialectIds(@NotNull List<? extends PsiElement> inside, @NotNull List<? extends PsiElement> outside) {
    Set<String> dialectIds = new HashSet<>();
    Set<Language> processedLanguages = new HashSet<>();
    addDialects(inside, processedLanguages, dialectIds);
    addDialects(outside, processedLanguages, dialectIds);
    return dialectIds;
  }

  private static @NotNull Set<String> calcElementDialectIds(@NotNull List<? extends PsiElement> elements) {
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
      Collection<Language> dialects = language.getTransitiveDialects();
      outProcessedLanguages.addAll(dialects);
      for (Language dialect : dialects) {
        outDialectIds.add(dialect.getID());
      }
    }
  }
}
