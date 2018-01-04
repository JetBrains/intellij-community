// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.concurrency.JobLauncher;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.containers.WeakInterner;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * @author max
 */
public class LocalInspectionsPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LocalInspectionsPass");
  public static final TextRange EMPTY_PRIORITY_RANGE = TextRange.EMPTY_RANGE;
  private static final Condition<PsiFile> SHOULD_INSPECT_FILTER = file -> HighlightingLevelManager.getInstance(file.getProject()).shouldInspect(file);
  private final TextRange myPriorityRange;
  private final boolean myIgnoreSuppressed;
  private final ConcurrentMap<PsiFile, List<InspectionResult>> result = ContainerUtil.newConcurrentMap();
  private static final String PRESENTABLE_NAME = DaemonBundle.message("pass.inspection");
  private volatile List<HighlightInfo> myInfos = Collections.emptyList();
  private final String myShortcutText;
  private final SeverityRegistrar mySeverityRegistrar;
  private final InspectionProfileWrapper myProfileWrapper;
  private boolean myFailFastOnAcquireReadAction;

  public LocalInspectionsPass(@NotNull PsiFile file,
                              @Nullable Document document,
                              int startOffset,
                              int endOffset,
                              @NotNull TextRange priorityRange,
                              boolean ignoreSuppressed,
                              @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    super(file.getProject(), document, PRESENTABLE_NAME, file, null, new TextRange(startOffset, endOffset), true, highlightInfoProcessor);
    assert file.isPhysical() : "can't inspect non-physical file: " + file + "; " + file.getVirtualFile();
    myPriorityRange = priorityRange;
    myIgnoreSuppressed = ignoreSuppressed;
    setId(Pass.LOCAL_INSPECTIONS);

    final KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null) {
      final Keymap keymap = keymapManager.getActiveKeymap();
      myShortcutText = keymap == null ? "" : "(" + KeymapUtil.getShortcutsText(keymap.getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")";
    }
    else {
      myShortcutText = "";
    }
    InspectionProfileImpl profileToUse = ProjectInspectionProfileManager.getInstance(myProject).getCurrentProfile();
    Function<InspectionProfileImpl, InspectionProfileWrapper> custom = file.getUserData(InspectionProfileWrapper.CUSTOMIZATION_KEY);
    myProfileWrapper = custom == null ? new InspectionProfileWrapper(profileToUse) : custom.apply(profileToUse);
    assert myProfileWrapper != null;
    mySeverityRegistrar = myProfileWrapper.getInspectionProfile().getProfileManager().getSeverityRegistrar();

    // initial guess
    setProgressLimit(300 * 2);
  }

  @NotNull
  private PsiFile getFile() {
    //noinspection ConstantConditions
    return myFile;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    try {
      if (!HighlightingLevelManager.getInstance(myProject).shouldInspect(getFile())) return;
      inspect(getInspectionTools(myProfileWrapper), InspectionManager.getInstance(myProject), true, true, progress);
    }
    finally {
      disposeDescriptors();
    }
  }

  private void disposeDescriptors() {
    result.clear();
  }

  private static Set<String> ourToolsWithInformationProblems = new HashSet<>();
  public void doInspectInBatch(@NotNull final GlobalInspectionContextImpl context,
                               @NotNull final InspectionManager iManager,
                               @NotNull final List<LocalInspectionToolWrapper> toolWrappers) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    inspect(new ArrayList<>(toolWrappers), iManager, false, false, progress);
    addDescriptorsFromInjectedResults(iManager, context);
    List<InspectionResult> resultList = result.get(getFile());
    if (resultList == null) return;
    for (InspectionResult inspectionResult : resultList) {
      LocalInspectionToolWrapper toolWrapper = inspectionResult.tool;
      final String shortName = toolWrapper.getShortName();
      for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
        if (descriptor.getHighlightType() == ProblemHighlightType.INFORMATION) {
          if (ourToolsWithInformationProblems.add(shortName)) {
            LOG.error("Tool '" + shortName + "' registers INFORMATION level problem in batch mode on " + getFile() + ". " +
                      "INFORMATION level fixes could change semantics and should not be used in batch transformations");
          }
          continue;
        }
        addDescriptors(toolWrapper, descriptor, context);
      }
    }
  }

  private void addDescriptors(@NotNull LocalInspectionToolWrapper toolWrapper,
                              @NotNull ProblemDescriptor descriptor,
                              @NotNull GlobalInspectionContextImpl context) {
    InspectionToolPresentation toolPresentation = context.getPresentation(toolWrapper);
    LocalDescriptorsUtil.addProblemDescriptors(Collections.singletonList(descriptor), toolPresentation, myIgnoreSuppressed,
                                               context,
                                               toolWrapper.getTool());
  }

  private void addDescriptorsFromInjectedResults(@NotNull InspectionManager iManager, @NotNull GlobalInspectionContextImpl context) {
    InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);

    for (Map.Entry<PsiFile, List<InspectionResult>> entry : result.entrySet()) {
      PsiFile file = entry.getKey();
      if (file == getFile()) continue; // not injected
      DocumentWindow documentRange = (DocumentWindow)documentManager.getDocument(file);
      List<InspectionResult> resultList = entry.getValue();
      for (InspectionResult inspectionResult : resultList) {
        LocalInspectionToolWrapper toolWrapper = inspectionResult.tool;
        for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
          PsiElement psiElement = descriptor.getPsiElement();
          if (psiElement == null) continue;
          if (SuppressionUtil.inspectionResultSuppressed(psiElement, toolWrapper.getTool())) continue;
          List<TextRange> editables = ilManager.intersectWithAllEditableFragments(file, ((ProblemDescriptorBase)descriptor).getTextRange());
          for (TextRange editable : editables) {
            TextRange hostRange = documentRange.injectedToHost(editable);
            QuickFix[] fixes = descriptor.getFixes();
            LocalQuickFix[] localFixes = null;
            if (fixes != null) {
              localFixes = new LocalQuickFix[fixes.length];
              for (int k = 0; k < fixes.length; k++) {
                QuickFix fix = fixes[k];
                localFixes[k] = (LocalQuickFix)fix;
              }
            }
            ProblemDescriptor patchedDescriptor = iManager.createProblemDescriptor(getFile(), hostRange, descriptor.getDescriptionTemplate(),
                                                                                   descriptor.getHighlightType(), true, localFixes);
            addDescriptors(toolWrapper, patchedDescriptor, context);
          }
        }
      }
    }
  }

  private void inspect(@NotNull final List<LocalInspectionToolWrapper> toolWrappers,
                       @NotNull final InspectionManager iManager,
                       final boolean isOnTheFly,
                       boolean failFastOnAcquireReadAction,
                       @NotNull final ProgressIndicator progress) {
    myFailFastOnAcquireReadAction = failFastOnAcquireReadAction;
    if (toolWrappers.isEmpty()) return;
    List<Divider.DividedElements> allDivided = new ArrayList<>();
    Divider.divideInsideAndOutsideAllRoots(myFile, myRestrictRange, myPriorityRange, SHOULD_INSPECT_FILTER, new CommonProcessors.CollectProcessor<>(allDivided));
    List<PsiElement> inside = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> d.inside));
    List<PsiElement> outside = ContainerUtil.concat((List<List<PsiElement>>)ContainerUtil.map(allDivided, d -> ContainerUtil.concat(d.outside, d.parents)));

    Set<String> elementDialectIds = InspectionEngine.calcElementDialectIds(inside, outside);
    Map<LocalInspectionToolWrapper, Set<String>> toolToSpecifiedLanguageIds = InspectionEngine.getToolsToSpecifiedLanguages(toolWrappers);

    setProgressLimit(toolToSpecifiedLanguageIds.size() * 2L);
    final LocalInspectionToolSession session = new LocalInspectionToolSession(getFile(), myRestrictRange.getStartOffset(), myRestrictRange.getEndOffset());

    List<InspectionContext> init = visitPriorityElementsAndInit(toolToSpecifiedLanguageIds, iManager, isOnTheFly, progress, inside, session,
                                                                elementDialectIds);
    inspectInjectedPsi(inside, isOnTheFly, progress, iManager, true, toolWrappers);
    visitRestElementsAndCleanup(progress, outside, session, init, elementDialectIds);
    inspectInjectedPsi(outside, isOnTheFly, progress, iManager, false, toolWrappers);
    ProgressManager.checkCanceled();

    myInfos = new ArrayList<>();
    addHighlightsFromResults(myInfos);
  }

  @NotNull
  private List<InspectionContext> visitPriorityElementsAndInit(@NotNull Map<LocalInspectionToolWrapper, Set<String>> toolToSpecifiedLanguageIds,
                                                               @NotNull final InspectionManager iManager,
                                                               final boolean isOnTheFly,
                                                               @NotNull final ProgressIndicator indicator,
                                                               @NotNull final List<PsiElement> elements,
                                                               @NotNull final LocalInspectionToolSession session,
                                                               @NotNull final Set<String> elementDialectIds) {
    final List<InspectionContext> init = new ArrayList<>();
    List<Map.Entry<LocalInspectionToolWrapper, Set<String>>> entries = new ArrayList<>(toolToSpecifiedLanguageIds.entrySet());

    Processor<Map.Entry<LocalInspectionToolWrapper, Set<String>>> processor =
      pair -> {
        LocalInspectionToolWrapper toolWrapper = pair.getKey();
        Set<String> dialectIdsSpecifiedForTool = pair.getValue();
        runToolOnElements(toolWrapper, dialectIdsSpecifiedForTool, iManager, isOnTheFly, indicator, elements, session, init, elementDialectIds);
        return true;
      };
    boolean result = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(entries, indicator, myFailFastOnAcquireReadAction, processor);
    if (!result) throw new ProcessCanceledException();
    return init;
  }

  private void runToolOnElements(@NotNull final LocalInspectionToolWrapper toolWrapper,
                                    Set<String> dialectIdsSpecifiedForTool,
                                    @NotNull final InspectionManager iManager,
                                    final boolean isOnTheFly,
                                    @NotNull final ProgressIndicator indicator,
                                    @NotNull final List<PsiElement> elements,
                                    @NotNull final LocalInspectionToolSession session,
                                    @NotNull List<InspectionContext> init,
                                    @NotNull Set<String> elementDialectIds) {
    ProgressManager.checkCanceled();

    ApplicationManager.getApplication().assertReadAccessAllowed();
    final LocalInspectionTool tool = toolWrapper.getTool();
    final boolean[] applyIncrementally = {isOnTheFly};
    ProblemsHolder holder = new ProblemsHolder(iManager, getFile(), isOnTheFly) {
        @Override
        public void registerProblem(@NotNull ProblemDescriptor descriptor) {
          super.registerProblem(descriptor);
          if (applyIncrementally[0]) {
            addDescriptorIncrementally(descriptor, toolWrapper, indicator);
          }
        }
    };

    PsiElementVisitor visitor = InspectionEngine.createVisitorAndAcceptElements(tool, holder, isOnTheFly, session, elements, elementDialectIds,
                                                                                dialectIdsSpecifiedForTool);

    synchronized (init) {
      init.add(new InspectionContext(toolWrapper, holder, holder.getResultCount(), visitor, dialectIdsSpecifiedForTool));
    }
    advanceProgress(1);

    if (holder.hasResults()) {
      appendDescriptors(getFile(), holder.getResults(), toolWrapper);
    }
    applyIncrementally[0] = false; // do not apply incrementally outside visible range
  }

  private void visitRestElementsAndCleanup(@NotNull final ProgressIndicator indicator,
                                           @NotNull final List<PsiElement> elements,
                                           @NotNull final LocalInspectionToolSession session,
                                           @NotNull List<InspectionContext> init,
                                           @NotNull final Set<String> elementDialectIds) {
    Processor<InspectionContext> processor =
      context -> {
        ProgressManager.checkCanceled();
        ApplicationManager.getApplication().assertReadAccessAllowed();
        InspectionEngine.acceptElements(elements, context.visitor, elementDialectIds, context.dialectIdsSpecifiedForTool);
        advanceProgress(1);
        context.tool.getTool().inspectionFinished(session, context.holder);

        if (context.holder.hasResults()) {
          List<ProblemDescriptor> allProblems = context.holder.getResults();
          List<ProblemDescriptor> restProblems = allProblems.subList(context.problemsSize, allProblems.size());
          appendDescriptors(getFile(), restProblems, context.tool);
        }
        return true;
      };
    boolean result = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(init, indicator, myFailFastOnAcquireReadAction, processor);
    if (!result) {
      throw new ProcessCanceledException();
    }
  }

  void inspectInjectedPsi(@NotNull final List<PsiElement> elements,
                          final boolean onTheFly,
                          @NotNull final ProgressIndicator indicator,
                          @NotNull final InspectionManager iManager,
                          final boolean inVisibleRange,
                          @NotNull final List<LocalInspectionToolWrapper> wrappers) {
    final Set<PsiFile> injected = new THashSet<>();
    for (PsiElement element : elements) {
      PsiFile containingFile = getFile();
      InjectedLanguageManager.getInstance(containingFile.getProject()).enumerateEx(element, containingFile, false,
                                                                                   (injectedPsi, places) -> injected.add(injectedPsi));
    }
    if (injected.isEmpty()) return;
    Processor<PsiFile> processor = injectedPsi -> {
      doInspectInjectedPsi(injectedPsi, onTheFly, indicator, iManager, inVisibleRange, wrappers);
      return true;
    };
    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(injected), indicator, myFailFastOnAcquireReadAction, processor)) {
      throw new ProcessCanceledException();
    }
  }

  private static final TextAttributes NONEMPTY_TEXT_ATTRIBUTES = new TextAttributes() {
    @Override
    public boolean isEmpty() {
      return false;
    }
  };

  @Nullable
  private HighlightInfo highlightInfoFromDescriptor(@NotNull ProblemDescriptor problemDescriptor,
                                                    @NotNull HighlightInfoType highlightInfoType,
                                                    @NotNull String message,
                                                    String toolTip,
                                                    PsiElement psiElement,
                                                    @NotNull List<IntentionAction> quickFixes) {
    TextRange textRange = ((ProblemDescriptorBase)problemDescriptor).getTextRange();
    if (textRange == null || psiElement == null) return null;
    boolean isFileLevel = psiElement instanceof PsiFile && textRange.equals(psiElement.getTextRange());

    final HighlightSeverity severity = highlightInfoType.getSeverity(psiElement);
    TextAttributes attributes = mySeverityRegistrar.getTextAttributesBySeverity(severity);
    HighlightInfo.Builder b = HighlightInfo.newHighlightInfo(highlightInfoType)
                              .range(psiElement, textRange.getStartOffset(), textRange.getEndOffset())
                              .description(message)
                              .severity(severity);
    if (toolTip != null) b.escapedToolTip(toolTip);
    if (HighlightSeverity.INFORMATION.equals(severity) && attributes == null && toolTip == null && !quickFixes.isEmpty()) {
      // Hack to avoid filtering this info out in HighlightInfoFilterImpl even though its attributes are empty.
      // But it has quick fixes so it needs to be created.
      attributes = NONEMPTY_TEXT_ATTRIBUTES;
    }
    if (attributes != null) b.textAttributes(attributes);
    if (problemDescriptor.isAfterEndOfLine()) b.endOfLine();
    if (isFileLevel) b.fileLevelAnnotation();
    if (problemDescriptor.getProblemGroup() != null) b.problemGroup(problemDescriptor.getProblemGroup());

    return b.create();
  }

  private final Map<TextRange, RangeMarker> ranges2markersCache = new THashMap<>();
  private final TransferToEDTQueue<Trinity<ProblemDescriptor, LocalInspectionToolWrapper,ProgressIndicator>> myTransferToEDTQueue
    = new TransferToEDTQueue<>("Apply inspection results", new Processor<Trinity<ProblemDescriptor, LocalInspectionToolWrapper, ProgressIndicator>>() {
    private final InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    private final List<HighlightInfo> infos = new ArrayList<>(2);
    private final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);

    @Override
    public boolean process(Trinity<ProblemDescriptor, LocalInspectionToolWrapper, ProgressIndicator> trinity) {
      ProgressIndicator indicator = trinity.getThird();
      if (indicator.isCanceled()) {
        return false;
      }

      ProblemDescriptor descriptor = trinity.first;
      LocalInspectionToolWrapper tool = trinity.second;
      PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement == null) return true;
      PsiFile file = psiElement.getContainingFile();
      Document thisDocument = documentManager.getDocument(file);

      HighlightSeverity severity = myProfileWrapper.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), file).getSeverity();

      infos.clear();
      createHighlightsForDescriptor(infos, emptyActionRegistered, ilManager, file, thisDocument, tool, severity, descriptor, psiElement);
      for (HighlightInfo info : infos) {
        final EditorColorsScheme colorsScheme = getColorsScheme();
        UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, myDocument, getFile(),
                                                                   myRestrictRange.getStartOffset(),
                                                                   myRestrictRange.getEndOffset(),
                                                                   info, colorsScheme, getId(),
                                                                   ranges2markersCache);
      }

      return true;
    }
  }, myProject.getDisposed());

  private final Set<Pair<TextRange, String>> emptyActionRegistered = Collections.synchronizedSet(new THashSet<Pair<TextRange, String>>());

  private void addDescriptorIncrementally(@NotNull final ProblemDescriptor descriptor,
                                          @NotNull final LocalInspectionToolWrapper tool,
                                          @NotNull final ProgressIndicator indicator) {
    if (myIgnoreSuppressed && SuppressionUtil.inspectionResultSuppressed(descriptor.getPsiElement(), tool.getTool())) {
      return;
    }
    myTransferToEDTQueue.offer(Trinity.create(descriptor, tool, indicator));
  }

  private void appendDescriptors(@NotNull PsiFile file, @NotNull List<ProblemDescriptor> descriptors, @NotNull LocalInspectionToolWrapper tool) {
    for (ProblemDescriptor descriptor : descriptors) {
      if (descriptor == null) {
        LOG.error("null descriptor. all descriptors(" + descriptors.size() +"): " +
                   descriptors + "; file: " + file + " (" + file.getVirtualFile() +"); tool: " + tool);
      }
    }
    InspectionResult result = new InspectionResult(tool, descriptors);
    appendResult(file, result);
  }

  private void appendResult(@NotNull PsiFile file, @NotNull InspectionResult result) {
    List<InspectionResult> resultList = this.result.get(file);
    if (resultList == null) {
      resultList = ConcurrencyUtil.cacheOrGet(this.result, file, new ArrayList<>());
    }
    synchronized (resultList) {
      resultList.add(result);
    }
  }

  @Override
  protected void applyInformationWithProgress() {
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myRestrictRange.getStartOffset(), myRestrictRange.getEndOffset(), myInfos, getColorsScheme(), getId());
  }

  private void addHighlightsFromResults(@NotNull List<HighlightInfo> outInfos) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    Set<Pair<TextRange, String>> emptyActionRegistered = new THashSet<>();

    for (Map.Entry<PsiFile, List<InspectionResult>> entry : result.entrySet()) {
      ProgressManager.checkCanceled();
      PsiFile file = entry.getKey();
      Document documentRange = documentManager.getDocument(file);
      if (documentRange == null) continue;
      List<InspectionResult> resultList = entry.getValue();
      synchronized (resultList) {
        for (InspectionResult inspectionResult : resultList) {
          ProgressManager.checkCanceled();
          LocalInspectionToolWrapper tool = inspectionResult.tool;
          HighlightSeverity severity = myProfileWrapper.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), file).getSeverity();
          for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
            ProgressManager.checkCanceled();
            PsiElement element = descriptor.getPsiElement();
            if (element != null) {
              createHighlightsForDescriptor(outInfos, emptyActionRegistered, ilManager, file, documentRange, tool, severity, descriptor, element);
            }
          }
        }
      }
    }
  }

  private void createHighlightsForDescriptor(@NotNull List<HighlightInfo> outInfos,
                                             @NotNull Set<Pair<TextRange, String>> emptyActionRegistered,
                                             @NotNull InjectedLanguageManager ilManager,
                                             @NotNull PsiFile file,
                                             @NotNull Document documentRange,
                                             @NotNull LocalInspectionToolWrapper toolWrapper,
                                             @NotNull HighlightSeverity severity,
                                             @NotNull ProblemDescriptor descriptor,
                                             @NotNull PsiElement element) {
    LocalInspectionTool tool = toolWrapper.getTool();
    if (myIgnoreSuppressed && SuppressionUtil.inspectionResultSuppressed(element, tool)) return;
    HighlightInfoType level = ProblemDescriptorUtil.highlightTypeFromDescriptor(descriptor, severity, mySeverityRegistrar);
    @NonNls String message = ProblemDescriptorUtil.renderDescriptionMessage(descriptor, element);

    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    final InspectionProfile inspectionProfile = myProfileWrapper.getInspectionProfile();
    if (!inspectionProfile.isToolEnabled(key, getFile())) return;

    HighlightInfoType type = new HighlightInfoType.HighlightInfoTypeImpl(level.getSeverity(element), level.getAttributesKey());
    final String plainMessage = message.startsWith("<html>") ? StringUtil.unescapeXml(XmlStringUtil.stripHtml(message).replaceAll("<[^>]*>", "")) : message;
    @NonNls String link = "";
    if (showToolDescription(toolWrapper)) {
      link = " <a "
             + "href=\"#inspection/" + tool.getShortName() + "\""
             + (UIUtil.isUnderDarcula() ? " color=\"7AB4C9\" " : "")
             + ">" + DaemonBundle.message("inspection.extended.description")
             + "</a> " + myShortcutText;
    }

    @NonNls String tooltip = null;
    if (descriptor.showTooltip()) {
      tooltip = tooltips.intern(XmlStringUtil.wrapInHtml((message.startsWith("<html>") ? XmlStringUtil.stripHtml(message): XmlStringUtil.escapeString(message)) + link));
    }
    List<IntentionAction> fixes = getQuickFixes(toolWrapper, descriptor, emptyActionRegistered);
    HighlightInfo info = highlightInfoFromDescriptor(descriptor, type, plainMessage, tooltip, element, fixes);
    if (info == null) return;
    registerQuickFixes(toolWrapper, info, fixes);

    PsiFile context = getTopLevelFileInBaseLanguage(element);
    PsiFile myContext = getTopLevelFileInBaseLanguage(getFile());
    if (context != getFile()) {
      ClassLoader toolClassLoader = tool.getClass().getClassLoader();
      PluginId pluginId = null;
      if (toolClassLoader instanceof PluginClassLoader) {
        pluginId = ((PluginClassLoader)toolClassLoader).getPluginId();
      }
      String errorMessage = "Reported element " + element +
                       " is not from the file '" + file +
                       "' the inspection '" + toolWrapper +
                       "' (" + tool.getClass() +
                       ") was invoked for. Message: '" + descriptor + "'.\nElement' containing file: " +
                       context + "\nInspection invoked for file: " + myContext + "\n";
      if (pluginId == null) {
        LOG.error(errorMessage);
      }
      else {
        LOG.error(new PluginException(errorMessage, pluginId));
      }
    }
    boolean isInjected = file != getFile();
    if (!isInjected) {
      outInfos.add(info);
      return;
    }
    injectToHost(outInfos, ilManager, file, documentRange, toolWrapper, element, fixes, info);
  }

  private static void injectToHost(@NotNull List<HighlightInfo> outInfos,
                                   @NotNull InjectedLanguageManager ilManager,
                                   @NotNull PsiFile file,
                                   @NotNull Document documentRange,
                                   @NotNull LocalInspectionToolWrapper toolWrapper,
                                   @NotNull PsiElement element, 
                                   @NotNull List<IntentionAction> fixes, 
                                   @NotNull HighlightInfo info) {
    // todo we got to separate our "internal" prefixes/suffixes from user-defined ones
    // todo in the latter case the errors should be highlighted, otherwise not
    List<TextRange> editables = ilManager.intersectWithAllEditableFragments(file, new TextRange(info.startOffset, info.endOffset));
    for (TextRange editable : editables) {
      TextRange hostRange = ((DocumentWindow)documentRange).injectedToHost(editable);
      int start = hostRange.getStartOffset();
      int end = hostRange.getEndOffset();
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(info.type).range(element, start, end);
      String description = info.getDescription();
      if (description != null) {
        builder.description(description);
      }
      String toolTip = info.getToolTip();
      if (toolTip != null) {
        builder.escapedToolTip(toolTip);
      }
      HighlightInfo patched = builder.createUnconditionally();
      if (patched.startOffset != patched.endOffset || info.startOffset == info.endOffset) {
        patched.setFromInjection(true);
        registerQuickFixes(toolWrapper, patched, fixes);
        outInfos.add(patched);
      }
    }
  }

  private PsiFile getTopLevelFileInBaseLanguage(@NotNull PsiElement element) {
    PsiFile file = InjectedLanguageManager.getInstance(myProject).getTopLevelFile(element);
    FileViewProvider viewProvider = file.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }


  private static final WeakInterner<String> tooltips = new WeakInterner<>();

  private static boolean showToolDescription(@NotNull LocalInspectionToolWrapper tool) {
    return tool.getStaticDescription() == null || !tool.getStaticDescription().isEmpty();
  }

  private static void registerQuickFixes(@NotNull LocalInspectionToolWrapper tool,
                                         @NotNull HighlightInfo highlightInfo,
                                         @NotNull List<IntentionAction> quickFixes) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    for (IntentionAction quickFix : quickFixes) {
      QuickFixAction.registerQuickFixAction(highlightInfo, quickFix, key);
    }
  }

  private static List<IntentionAction> getQuickFixes(@NotNull LocalInspectionToolWrapper tool,
                                                     @NotNull ProblemDescriptor descriptor,
                                                     @NotNull Set<Pair<TextRange, String>> emptyActionRegistered) {
    List<IntentionAction> result = new SmartList<>();
    boolean needEmptyAction = true;
    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length != 0) {
      for (int k = 0; k < fixes.length; k++) {
        QuickFix fix = fixes[k];
        if (fix == null) throw new IllegalStateException("Inspection " + tool + " returns null quick fix in its descriptor: " + descriptor + "; array: " +
                                                         Arrays.toString(fixes));
        result.add(QuickFixWrapper.wrap(descriptor, k));
        needEmptyAction = false;
      }
    }
    HintAction hintAction = descriptor instanceof ProblemDescriptorImpl ? ((ProblemDescriptorImpl)descriptor).getHintAction() : null;
    if (hintAction != null) {
      result.add(hintAction);
      needEmptyAction = false;
    }
    if (((ProblemDescriptorBase)descriptor).getEnforcedTextAttributes() != null) {
      needEmptyAction = false;
    }
    if (needEmptyAction && emptyActionRegistered.add(Pair.create(((ProblemDescriptorBase)descriptor).getTextRange(), tool.getShortName()))) {
      IntentionAction emptyIntentionAction = new EmptyIntentionAction(tool.getDisplayName());
      result.add(emptyIntentionAction);
    }
    return result;
  }

  private static void getElementsAndDialectsFrom(@NotNull PsiFile file,
                                                 @NotNull List<PsiElement> outElements,
                                                 @NotNull Set<String> outDialects) {
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<PsiElement> result = new LinkedHashSet<>();
    Set<Language> processedLanguages = new SmartHashSet<>();
    final PsiElementVisitor visitor = new PsiRecursiveElementVisitor() {
      @Override public void visitElement(PsiElement element) {
        ProgressManager.checkCanceled();
        PsiElement child = element.getFirstChild();
        if (child == null) {
          // leaf element
        }
        else {
          // composite element
          while (child != null) {
            child.accept(this);
            result.add(child);
            appendDialects(child, processedLanguages, outDialects);
            child = child.getNextSibling();
          }
        }
      }
    };
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile psiRoot = viewProvider.getPsi(language);
      if (psiRoot == null || !HighlightingLevelManager.getInstance(file.getProject()).shouldInspect(psiRoot)) {
        continue;
      }
      psiRoot.accept(visitor);
      result.add(psiRoot);
      appendDialects(psiRoot, processedLanguages, outDialects);
    }
    outElements.addAll(result);
  }

  private static void appendDialects(PsiElement element, Set<Language> outProcessedLanguages, Set<String> outDialectIds) {
    Language language = element.getLanguage();
    outDialectIds.add(language.getID());
    if (outProcessedLanguages.add(language)) {
      for (Language dialect : language.getDialects()) {
        outDialectIds.add(dialect.getID());
      }
    }
  }

  @NotNull
  List<LocalInspectionToolWrapper> getInspectionTools(@NotNull InspectionProfileWrapper profile) {
    final InspectionToolWrapper[] toolWrappers = profile.getInspectionProfile().getInspectionTools(getFile());
    InspectionProfileWrapper.checkInspectionsDuplicates(toolWrappers);
    List<LocalInspectionToolWrapper> enabled = new ArrayList<>();
    for (InspectionToolWrapper toolWrapper : toolWrappers) {
      ProgressManager.checkCanceled();
      final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName());
      if (!profile.isToolEnabled(key, getFile())) continue;
      if (HighlightDisplayLevel.DO_NOT_SHOW.equals(profile.getErrorLevel(key, getFile()))) continue;
      LocalInspectionToolWrapper wrapper = null;
      if (toolWrapper instanceof LocalInspectionToolWrapper) {
        wrapper = (LocalInspectionToolWrapper)toolWrapper;
      }
      else if (toolWrapper instanceof GlobalInspectionToolWrapper) {
        final GlobalInspectionToolWrapper globalInspectionToolWrapper = (GlobalInspectionToolWrapper)toolWrapper;
        wrapper = globalInspectionToolWrapper.getSharedLocalInspectionToolWrapper();
      }
      if (wrapper == null) continue;
      String language = wrapper.getLanguage();
      if (language != null && Language.findLanguageByID(language) == null) {
        continue; // filter out at least unknown languages
      }
      if (myIgnoreSuppressed && SuppressionUtil.inspectionResultSuppressed(getFile(), wrapper.getTool())) {
        continue;
      }
      enabled.add(wrapper);
    }
    return enabled;
  }

  private void doInspectInjectedPsi(@NotNull PsiFile injectedPsi,
                                    final boolean isOnTheFly,
                                    @NotNull final ProgressIndicator indicator,
                                    @NotNull InspectionManager iManager,
                                    final boolean inVisibleRange,
                                    @NotNull List<LocalInspectionToolWrapper> wrappers) {
    final PsiElement host = InjectedLanguageManager.getInstance(injectedPsi.getProject()).getInjectionHost(injectedPsi);

    List<PsiElement> elements = new ArrayList<>();
    Set<String> elementDialectIds = new SmartHashSet<>();
    getElementsAndDialectsFrom(injectedPsi, elements, elementDialectIds);
    if (elements.isEmpty()) {
      return;
    }
    Map<LocalInspectionToolWrapper, Set<String>> toolToSpecifiedLanguageIds = InspectionEngine.getToolsToSpecifiedLanguages(wrappers);
    for (Map.Entry<LocalInspectionToolWrapper, Set<String>> pair : toolToSpecifiedLanguageIds.entrySet()) {
      ProgressManager.checkCanceled();
      final LocalInspectionToolWrapper wrapper = pair.getKey();
      final LocalInspectionTool tool = wrapper.getTool();
      if (host != null && myIgnoreSuppressed && SuppressionUtil.inspectionResultSuppressed(host, tool)) {
        continue;
      }
      ProblemsHolder holder = new ProblemsHolder(iManager, injectedPsi, isOnTheFly) {
        @Override
        public void registerProblem(@NotNull ProblemDescriptor descriptor) {
          super.registerProblem(descriptor);
          if (isOnTheFly && inVisibleRange) {
            addDescriptorIncrementally(descriptor, wrapper, indicator);
          }
        }
      };

      LocalInspectionToolSession injSession = new LocalInspectionToolSession(injectedPsi, 0, injectedPsi.getTextLength());
      Set<String> dialectIdsSpecifiedForTool = pair.getValue();
      InspectionEngine.createVisitorAndAcceptElements(tool, holder, isOnTheFly, injSession, elements, elementDialectIds, dialectIdsSpecifiedForTool);
      tool.inspectionFinished(injSession, holder);
      List<ProblemDescriptor> problems = holder.getResults();
      if (!problems.isEmpty()) {
        appendDescriptors(injectedPsi, problems, wrapper);
      }
    }
  }

  @Override
  @NotNull
  public List<HighlightInfo> getInfos() {
    return myInfos;
  }

  private static class InspectionResult {
    @NotNull private final LocalInspectionToolWrapper tool;
    @NotNull private final List<ProblemDescriptor> foundProblems;

    private InspectionResult(@NotNull LocalInspectionToolWrapper tool, @NotNull List<ProblemDescriptor> foundProblems) {
      this.tool = tool;
      this.foundProblems = new ArrayList<>(foundProblems);
    }
  }

  private static class InspectionContext {
    private InspectionContext(@NotNull LocalInspectionToolWrapper tool,
                              @NotNull ProblemsHolder holder,
                              int problemsSize, // need this to diff between found problems in visible part and the rest
                              @NotNull PsiElementVisitor visitor,
                              @Nullable Set<String> dialectIdsSpecifiedForTool) {
      this.tool = tool;
      this.holder = holder;
      this.problemsSize = problemsSize;
      this.visitor = visitor;
      this.dialectIdsSpecifiedForTool = dialectIdsSpecifiedForTool;
    }

    @NotNull private final LocalInspectionToolWrapper tool;
    @NotNull private final ProblemsHolder holder;
    private final int problemsSize;
    @NotNull private final PsiElementVisitor visitor;
    @Nullable private final Set<String> dialectIdsSpecifiedForTool;
  }
}
