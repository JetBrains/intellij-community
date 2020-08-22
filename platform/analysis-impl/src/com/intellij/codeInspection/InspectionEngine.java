// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.Divider;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveVisitor;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InspectionEngine {
  private static final Logger LOG = Logger.getInstance(InspectionEngine.class);
  private static final Set<Class<? extends LocalInspectionTool>> RECURSIVE_VISITOR_TOOL_CLASSES = ContainerUtil.newConcurrentSet();

  public static @NotNull PsiElementVisitor createVisitorAndAcceptElements(@NotNull LocalInspectionTool tool,
                                                                          @NotNull ProblemsHolder holder,
                                                                          boolean isOnTheFly,
                                                                          @NotNull LocalInspectionToolSession session,
                                                                          @NotNull List<? extends PsiElement> elements) {
    PsiElementVisitor visitor = tool.buildVisitor(holder, isOnTheFly, session);
    //noinspection ConstantConditions
    if (visitor == null) {
      LOG.error("Tool " + tool + " (" + tool.getClass()+ ") must not return null from the buildVisitor() method");
    }
    else if (visitor instanceof PsiRecursiveVisitor && RECURSIVE_VISITOR_TOOL_CLASSES.add(tool.getClass())) {
      LOG.error("The visitor returned from LocalInspectionTool.buildVisitor() must not be recursive: " + tool);
    }
    // if inspection returned empty visitor then it should be skipped
    if (visitor != PsiElementVisitor.EMPTY_VISITOR) {
      tool.inspectionStarted(session, isOnTheFly);
      acceptElements(elements, visitor);
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

  private static @NotNull List<ProblemDescriptor> inspect(final @NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                          final @NotNull PsiFile file,
                                                          final @NotNull InspectionManager iManager,
                                                          final @NotNull ProgressIndicator indicator) {
    final Map<String, List<ProblemDescriptor>> problemDescriptors = inspectEx(toolWrappers, file, iManager, false, indicator);

    final List<ProblemDescriptor> result = new ArrayList<>();
    for (List<ProblemDescriptor> group : problemDescriptors.values()) {
      result.addAll(group);
    }
    return result;
  }

  // public for Upsource
  // returns map (toolName -> problem descriptors)
  public static @NotNull Map<String, List<ProblemDescriptor>> inspectEx(final @NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                        final @NotNull PsiFile file,
                                                                        final @NotNull InspectionManager iManager,
                                                                        final boolean isOnTheFly,
                                                                        final @NotNull ProgressIndicator indicator) {
    if (toolWrappers.isEmpty()) return Collections.emptyMap();

    TextRange range = file.getTextRange();
    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(file, range, range, __ -> true, new CommonProcessors.CollectProcessor<>(allDivided));

    List<PsiElement> elements = ContainerUtil.concat(
      (List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> ContainerUtil.concat(d.inside, d.outside, d.parents)));

    return inspectElements(toolWrappers, file, iManager, isOnTheFly, indicator, elements,
                           calcElementDialectIds(elements));
  }

  // returns map tool.shortName -> list of descriptors found
  static @NotNull Map<String, List<ProblemDescriptor>> inspectElements(@NotNull List<? extends LocalInspectionToolWrapper> toolWrappers,
                                                                       final @NotNull PsiFile file,
                                                                       final @NotNull InspectionManager iManager,
                                                                       final boolean isOnTheFly,
                                                                       @NotNull ProgressIndicator indicator,
                                                                       final @NotNull List<? extends PsiElement> elements,
                                                                       final @NotNull Set<String> elementDialectIds) {
    TextRange range = file.getTextRange();
    final LocalInspectionToolSession session = new LocalInspectionToolSession(file, range.getStartOffset(), range.getEndOffset());

    toolWrappers = filterToolsApplicableByLanguage(toolWrappers, elementDialectIds);
    final Map<String, List<ProblemDescriptor>> resultDescriptors = new ConcurrentHashMap<>();
    Processor<LocalInspectionToolWrapper> processor = wrapper -> {
      ProblemsHolder holder = new ProblemsHolder(iManager, file, isOnTheFly);
      LocalInspectionTool tool = wrapper.getTool();
      createVisitorAndAcceptElements(tool, holder, isOnTheFly, session, elements);

      tool.inspectionFinished(session, holder);

      if (holder.hasResults()) {
        resultDescriptors.put(tool.getShortName(), ContainerUtil.filter(holder.getResults(), descriptor -> {
          PsiElement element = descriptor.getPsiElement();
          return element == null || !SuppressionUtil.inspectionResultSuppressed(element, tool);
        }));
      }

      return true;
    };
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(toolWrappers, indicator, processor);

    return resultDescriptors;
  }

  public static @NotNull List<ProblemDescriptor> runInspectionOnFile(final @NotNull PsiFile file,
                                                                     @NotNull InspectionToolWrapper<?, ?> toolWrapper,
                                                                     final @NotNull GlobalInspectionContext inspectionContext) {
    final InspectionManager inspectionManager = InspectionManager.getInstance(file.getProject());
    toolWrapper.initialize(inspectionContext);
    RefManagerImpl refManager = (RefManagerImpl)inspectionContext.getRefManager();
    refManager.inspectionReadActionStarted();
    try {
      if (toolWrapper instanceof LocalInspectionToolWrapper) {
        return inspect(Collections.singletonList((LocalInspectionToolWrapper)toolWrapper), file, inspectionManager, new EmptyProgressIndicator());
      }
      if (toolWrapper instanceof GlobalInspectionToolWrapper) {
        final GlobalInspectionTool globalTool = ((GlobalInspectionToolWrapper)toolWrapper).getTool();
        final List<ProblemDescriptor> descriptors = new ArrayList<>();
        if (globalTool instanceof GlobalSimpleInspectionTool) {
          GlobalSimpleInspectionTool simpleTool = (GlobalSimpleInspectionTool)globalTool;
          ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, file, false);
          ProblemDescriptionsProcessor collectProcessor = new ProblemDescriptionsProcessor() {
            @Override
            public CommonProblemDescriptor[] getDescriptions(@NotNull RefEntity refEntity) {
              return descriptors.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
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
              convertToProblemDescriptors(element, commonProblemDescriptors, descriptors);
            }

            @Override
            public RefEntity getElement(@NotNull CommonProblemDescriptor descriptor) {
              throw new RuntimeException();
            }
          };
          simpleTool.checkFile(file, inspectionManager, problemsHolder, inspectionContext, collectProcessor);
          return descriptors;
        }
        RefElement fileRef = refManager.getReference(file);
        final AnalysisScope scope = new AnalysisScope(file);
        assert fileRef != null;
        fileRef.accept(new RefVisitor(){
          @Override
          public void visitElement(@NotNull RefEntity elem) {
            CommonProblemDescriptor[] elemDescriptors = globalTool.checkElement(elem, scope, inspectionManager, inspectionContext);
            if (elemDescriptors != null) {
              convertToProblemDescriptors(file, elemDescriptors, descriptors);
            }

            for (RefEntity child : elem.getChildren()) {
              child.accept(this);
            }
          }
        });
        return descriptors;
      }
    }
    finally {
      refManager.inspectionReadActionFinished();
      toolWrapper.cleanup(file.getProject());
      inspectionContext.cleanup();
    }
    return Collections.emptyList();
  }

  private static void convertToProblemDescriptors(@NotNull PsiElement element,
                                                  CommonProblemDescriptor @NotNull [] commonProblemDescriptors,
                                                  @NotNull List<? super ProblemDescriptor> descriptors) {
    for (CommonProblemDescriptor common : commonProblemDescriptors) {
      if (common instanceof ProblemDescriptor) {
        descriptors.add((ProblemDescriptor)common);
      }
      else {
        ProblemDescriptorBase base =
          new ProblemDescriptorBase(element, element, common.getDescriptionTemplate(), (LocalQuickFix[])common.getFixes(),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, null, false, false);
        descriptors.add(base);
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

  private static @NotNull Set<String> getDialectIdsSpecifiedForTool(String langId, boolean applyToDialects) {
    Language language = Language.findLanguageByID(langId);
    Set<String> result;
    if (language == null) {
      // unknown language in plugin.xml, ignore
      result = Collections.singleton(langId);
    }
    else if (language instanceof MetaLanguage) {
      Collection<Language> matchingLanguages = ((MetaLanguage) language).getMatchingLanguages();
      result = new THashSet<>();
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

    Set<String> result = new THashSet<>(1 + dialects.size());
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
    Set<String> dialectIds = new SmartHashSet<>();
    Set<Language> processedLanguages = new SmartHashSet<>();
    addDialects(inside, processedLanguages, dialectIds);
    addDialects(outside, processedLanguages, dialectIds);
    return dialectIds;
  }

  public static @NotNull Set<String> calcElementDialectIds(@NotNull List<? extends PsiElement> elements) {
    Set<String> dialectIds = new SmartHashSet<>();
    Set<Language> processedLanguages = new SmartHashSet<>();
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
