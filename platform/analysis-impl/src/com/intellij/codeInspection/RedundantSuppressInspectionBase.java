// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public abstract class RedundantSuppressInspectionBase extends GlobalSimpleInspectionTool {
  public static final String SHORT_NAME = "RedundantSuppression";
  private static final Logger LOG = Logger.getInstance(RedundantSuppressInspectionBase.class);
  public boolean IGNORE_ALL;
  private BidirectionalMap<String, QuickFix<?>> myQuickFixes;

  @Override
  public @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (IGNORE_ALL) {
      super.writeSettings(node);
    }
  }

  @Override
  public void checkFile(@NotNull PsiFile file,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    InspectionSuppressor extension = ContainerUtil.find(LanguageInspectionSuppressors.INSTANCE.allForLanguage(file.getLanguage()), s -> s instanceof RedundantSuppressionDetector);
    if (!(extension instanceof RedundantSuppressionDetector redundantSuppressionDetector)) return;
    InspectionProfileImpl profile = getProfile(manager, globalContext);
    CommonProblemDescriptor[] descriptors = checkElement(file, redundantSuppressionDetector, manager, profile);
    for (CommonProblemDescriptor descriptor : descriptors) {
      if (descriptor instanceof ProblemDescriptor problemDescriptor) {
        PsiElement psiElement = problemDescriptor.getPsiElement();
        if (psiElement != null) {
          PsiElement member = globalContext.getRefManager().getContainerElement(psiElement);
          RefElement reference = globalContext.getRefManager().getReference(member);
          if (reference != null) {
            problemDescriptionsProcessor.addProblemElement(reference, descriptor);
            continue;
          }
        }
      }
      problemsHolder.registerProblem(file, descriptor.getDescriptionTemplate());
    }
  }

  private ProblemDescriptor @NotNull [] checkElement(@NotNull PsiFile psiFile,
                                                     @NotNull RedundantSuppressionDetector extension,
                                                     @NotNull InspectionManager manager,
                                                     @NotNull InspectionProfile profile) {
    Map<PsiElement, Collection<String>> suppressedScopes = new HashMap<>();
    psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        collectSuppressions(element, suppressedScopes, IGNORE_ALL, extension);
      }
    });

    if (suppressedScopes.isEmpty()) return ProblemDescriptor.EMPTY_ARRAY;
    // have to visit all file from scratch since inspections can be written in any pervasive way including checkFile() overriding
    Map<InspectionToolWrapper<?, ?>, String> suppressedTools = new HashMap<>();
    List<InspectionToolWrapper<?, ?>> toolWrappers = getInspectionTools(psiFile, profile);
    Language language = psiFile.getLanguage();
    for (Collection<String> ids : suppressedScopes.values()) {
      for (Iterator<String> iterator = ids.iterator(); iterator.hasNext(); ) {
        String suppressId = iterator.next().trim();
        List<InspectionToolWrapper<?, ?>> reportingWrappers = findReportingTools(toolWrappers, suppressId, language);
        if (reportingWrappers.isEmpty()) {
          iterator.remove();
        }
        else {
          for (InspectionToolWrapper<?, ?> toolWrapper : reportingWrappers) {
            suppressedTools.put(toolWrapper, suppressId);
          }
        }
      }
    }

    AnalysisScope scope = new AnalysisScope(psiFile);

    GlobalInspectionContextBase globalContext = createContext(psiFile);
    globalContext.setCurrentScope(scope);
    List<ProblemDescriptor> result = new ArrayList<>();
    ((RefManagerImpl)globalContext.getRefManager()).runInsideInspectionReadAction(() -> {
      try {
        for (Map.Entry<InspectionToolWrapper<?, ?>, String> entry : suppressedTools.entrySet()) {
          InspectionToolWrapper<?, ?> toolWrapper = entry.getKey();
          String toolId = entry.getValue();
          toolWrapper.initialize(globalContext);
          Collection<CommonProblemDescriptor> descriptors;
          if (toolWrapper instanceof LocalInspectionToolWrapper local) {
            if (local.isUnfair()) {
              continue; // can't work with passes other than LocalInspectionPass
            }
            LocalInspectionTool tool = local.getTool();
            List<ProblemDescriptor> found = Collections.synchronizedList(new ArrayList<>());
            // shouldn't use standard ProblemsHolder because it filters out suppressed elements by default
            InspectionEngine.inspectEx(Collections.singletonList(new LocalInspectionToolWrapper(tool)), psiFile, psiFile.getTextRange(),
                                       psiFile.getTextRange(), false,
                                       true, false, ProgressIndicatorProvider.getGlobalProgressIndicator(),
                                       (wrapper, descriptor) -> found.add(descriptor));
            descriptors = new ArrayList<>(found);
          }
          else if (toolWrapper instanceof GlobalInspectionToolWrapper global) {
            GlobalInspectionTool globalTool = global.getTool();
            //when graph is needed, results probably depend on outer files so absence of results on one file (in current context) doesn't guarantee anything
            if (globalTool.isGraphNeeded()) continue;
            if (globalTool instanceof RedundantSuppressInspectionBase) continue;
            descriptors = new ArrayList<>(InspectionEngine.runInspectionOnFile(psiFile, global, globalContext));
          }
          else {
            continue;
          }
          for (Map.Entry<PsiElement, Collection<String>> e : suppressedScopes.entrySet()) {
            Collection<String> suppressedIds = e.getValue();
            PsiElement suppressedScope = e.getKey();
            if (!suppressedIds.contains(toolId)) continue;
            for (CommonProblemDescriptor descriptor : descriptors) {
              if (!(descriptor instanceof ProblemDescriptor problemDescriptor)) continue;
              PsiElement element = problemDescriptor.getPsiElement();
              if (element == null) continue;
              PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element);
              if (extension.isSuppressionFor(suppressedScope, ObjectUtils.notNull(host, element), toolId)) {
                suppressedIds.remove(toolId);
                break;
              }
            }
          }
        }
        for (Map.Entry<PsiElement, Collection<String>> entry : suppressedScopes.entrySet()) {
          PsiElement suppressedScope = entry.getKey();
          Collection<String> suppressedIds = entry.getValue();
          for (String toolId : suppressedIds) {
            PsiElement documentedElement = globalContext.getRefManager().getContainerElement(suppressedScope);
            if (documentedElement != null && documentedElement.isValid()) {
              QuickFix<?> fix;
              synchronized (this) {
                if (myQuickFixes == null) myQuickFixes = new BidirectionalMap<>();
                String key = toolId + ";" + suppressedScope.getLanguage().getID();
                fix = myQuickFixes.get(key);
                if (fix == null) {
                  fix = createQuickFix(key);
                  myQuickFixes.put(key, fix);
                }
              }
              PsiElement identifier;
              if (suppressedScope instanceof PsiNameIdentifierOwner nameIdentifierOwner && suppressedScope == documentedElement) {
                identifier = ObjectUtils.notNull(nameIdentifierOwner.getNameIdentifier(), suppressedScope);
              }
              else {
                identifier = suppressedScope;
              }
              result.add(
                manager.createProblemDescriptor(identifier, InspectionsBundle.message("inspection.redundant.suppression.description"),
                                                (LocalQuickFix)fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                false));
            }
          }
        }
      }
      finally {
        globalContext.close(true);
      }
    });
    return result.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  public @NotNull LocalInspectionTool createLocalTool(@NotNull RedundantSuppressionDetector suppressor,
                                                      @NotNull Map<String, ? extends Set<PsiElement>> toolToSuppressScopes,
                                                      @NotNull Set<String> activeTools,
                                                      @NotNull TextRange restrictRange) {
    return new LocalRedundantSuppressionInspection(suppressor, activeTools, toolToSuppressScopes, restrictRange);
  }

  protected @NotNull List<InspectionToolWrapper<?, ?>> getInspectionTools(@NotNull PsiElement psiElement, @NotNull InspectionProfile profile) {
    return profile.getInspectionTools(psiElement);
  }

  @Override
  public synchronized @Nullable QuickFix<?> getQuickFix(String hint) {
    return myQuickFixes != null ? myQuickFixes.get(hint) : createQuickFix(hint);
  }

  @Override
  public synchronized @Nullable String getHint(@NotNull QuickFix fix) {
    if (myQuickFixes != null) {
      List<String> list = myQuickFixes.getKeysByValue(fix);
      if (list != null) {
        LOG.assertTrue(list.size() == 1);
        return list.get(0);
      }
    }
    return null;
  }

  @Override
  public boolean worksInBatchModeOnly() {
    return false;
  }

  protected static @NotNull GlobalInspectionContextBase createContext(@NotNull PsiFile file) {
    InspectionManager inspectionManagerEx = InspectionManager.getInstance(file.getProject());
    return (GlobalInspectionContextBase)inspectionManagerEx.createNewGlobalContext();
  }

  private static @NotNull InspectionProfileImpl getProfile(@NotNull InspectionManager manager, @NotNull GlobalInspectionContext globalContext) {
    if (globalContext instanceof GlobalInspectionContextBase globalInspectionContext) {
      InspectionProfileImpl profile = globalInspectionContext.getCurrentProfile();
      if (profile.getSingleTool() == null) {
        return profile;
      }
    }
    String currentProfileName = ((InspectionManagerBase)manager).getCurrentProfile();
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(manager.getProject());
    return ObjectUtils.notNull(profileManager.getProfile(currentProfileName, false), profileManager.getCurrentProfile());
  }

  private static @NotNull List<InspectionToolWrapper<?, ?>> findReportingTools(@NotNull List<? extends InspectionToolWrapper<?, ?>> toolWrappers,
                                                                               @NotNull String suppressedId,
                                                                               @NotNull Language language) {
    List<InspectionToolWrapper<?, ?>> wrappers = Collections.emptyList();
    List<String> mergedToolName = InspectionElementsMerger.getMergedToolNames(suppressedId);
    for (InspectionToolWrapper<?, ?> toolWrapper : toolWrappers) {
      String toolWrapperShortName = toolWrapper.getShortName();
      String alternativeID = toolWrapper.getTool().getAlternativeID();
      if (toolWrapper instanceof LocalInspectionToolWrapper wrapper &&
          (wrapper.getTool().getID().equals(suppressedId) ||
           suppressedId.equals(alternativeID) ||
           mergedToolName.contains(toolWrapperShortName))) {
        if (!wrapper.isUnfair() && toolWrapper.isApplicable(language)) {
          if (wrappers.isEmpty()) wrappers = new ArrayList<>();
          wrappers.add(toolWrapper);
        }
      }
      else if (toolWrapperShortName.equals(suppressedId) ||
               mergedToolName.contains(toolWrapperShortName) ||
               suppressedId.equals(alternativeID)) {
        //ignore global unused as it won't be checked anyway
        if (toolWrapper instanceof LocalInspectionToolWrapper ||
            toolWrapper instanceof GlobalInspectionToolWrapper wrapper && !wrapper.getTool().isGraphNeeded()) {
          if (wrappers.isEmpty()) wrappers = new ArrayList<>();
          wrappers.add(toolWrapper);
        }
      }
    }
    return wrappers;
  }

  private static boolean collectSuppressions(@NotNull PsiElement element,
                                             @NotNull Map<? super PsiElement, Collection<String>> suppressedScopes,
                                             boolean ignoreAll,
                                             @NotNull RedundantSuppressionDetector suppressor) {
    String idsString = suppressor.getSuppressionIds(element);
    if (idsString != null && !idsString.isEmpty()) {
      List<String> ids = new ArrayList<>();
      StringUtil.tokenize(idsString, "[, ]").forEach(ids::add);
      boolean isSuppressAll = ContainerUtil.exists(ids, id -> id.equalsIgnoreCase(SuppressionUtil.ALL));
      if (ignoreAll && isSuppressAll) {
        return false;
      }
      Collection<String> suppressed = suppressedScopes.get(element);
      if (suppressed == null) {
        suppressed = ids;
      }
      else {
        for (String id : ids) {
          if (!suppressed.contains(id)) {
            suppressed.add(id);
          }
        }
      }
      suppressedScopes.put(element, suppressed);
      return isSuppressAll;
    }
    return false;
  }

  private static QuickFix<ProblemDescriptor> createQuickFix(@NotNull String key) {
    String[] toolAndLang = key.split(";");
    Language language = toolAndLang.length < 2 ? null : Language.findLanguageByID(toolAndLang[1]);
    if (language == null) return null;
    InspectionSuppressor suppressor = ContainerUtil.find(LanguageInspectionSuppressors.INSTANCE.allForLanguage(language), s -> s instanceof RedundantSuppressionDetector);
    return (suppressor instanceof RedundantSuppressionDetector redundantSuppressionDetector)
           ? redundantSuppressionDetector.createRemoveRedundantSuppressionFix(toolAndLang[0]) : null;
  }

  private final class LocalRedundantSuppressionInspection extends LocalInspectionTool implements UnfairLocalInspectionTool {
    private final @NotNull RedundantSuppressionDetector mySuppressor;
    private final @NotNull Set<String> myActiveTools;
    private final @NotNull Map<String, ? extends Set<PsiElement>> myToolToSuppressScopes;
    private final @NotNull TextRange myRestrictRange;

    private LocalRedundantSuppressionInspection(@NotNull RedundantSuppressionDetector suppressor,
                                                @NotNull Set<String> activeTools,
                                                @NotNull Map<String, ? extends Set<PsiElement>> toolToSuppressScopes,
                                                @NotNull TextRange restrictRange) {
      mySuppressor = suppressor;
      myActiveTools = activeTools;
      myToolToSuppressScopes = toolToSuppressScopes;
      myRestrictRange = restrictRange;
    }

    @Override
    public @NotNull String getShortName() {
      return SHORT_NAME;
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new PsiElementVisitor() {

        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (!myRestrictRange.contains(element.getTextRange())) {
            // We are analyzing element `suppressionElement1` which has attached suppression `suppression1` for inspection `tool1`.
            // E.g. for `@SuppressWarnings("rawtypes") int parameter1`,
            //      suppression1 = @SuppressWarnings("rawtypes"),
            //      suppressionElement1 = PsiParameter("parameter1"),
            //      tool1 = RawUseOfParameterizedTypeInspection (its shortName="rawtypes")
            // The contract is:
            //  the inspection tool `tool1` must be run for all elements inside element `suppressionElement1`,
            //  because some of these elements might have generated the warning, which suppression we are analyzing.
            // If the inspection `tool1` hasn't run for all these elements,
            // we have no enough information to decide whether `suppression1` is redundant or not
            return;
          }
          super.visitElement(element);
          HashMap<PsiElement, Collection<String>> scopes = new HashMap<>();
          boolean suppressAll = collectSuppressions(element, scopes, IGNORE_ALL, mySuppressor);
          if (suppressAll) {
            return;
          }
          Collection<String> suppressIds = scopes.get(element);
          if (suppressIds != null) {
            for (String suppressId : suppressIds) {
              if (myActiveTools.contains(suppressId) &&
                  !isSuppressedFor(element, suppressId, myToolToSuppressScopes.get(suppressId)) &&
                  //suppression in local pass is intentionally disabled to pass ALL
                  !SuppressionUtil.inspectionResultSuppressed(element, LocalRedundantSuppressionInspection.this)) {
                TextRange highlightingRange = mySuppressor.getHighlightingRange(element, suppressId);
                if (highlightingRange != null) {
                  holder.registerProblem(element, highlightingRange,
                                         InspectionsBundle.message("inspection.redundant.suppression.description"),
                                         mySuppressor.createRemoveRedundantSuppressionFix(suppressId));
                }
              }
            }
          }
        }

        private boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String suppressId, Set<? extends PsiElement> suppressedPlaces) {
          return suppressedPlaces != null &&
                 ContainerUtil.exists(suppressedPlaces, place -> mySuppressor.isSuppressionFor(element, place, suppressId));
        }
      };
    }

    @Override
    public boolean isDumbAware() {
      return mySuppressor.isDumbAware();
    }
  }
}
