// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.DaemonTooltipsUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ProblemDescriptorUtil.ProblemPresentation;
import com.intellij.codeInspection.ex.*;
import com.intellij.diagnostic.PluginException;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.UnmodifiableTextAttributes;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedFileViewProvider;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Interner;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public class LocalInspectionsPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance(LocalInspectionsPass.class);
  public static final TextRange EMPTY_PRIORITY_RANGE = TextRange.EMPTY_RANGE;
  private final TextRange myPriorityRange;
  private final boolean myIgnoreSuppressed;
  private volatile List<? extends HighlightInfo> myInfos = Collections.emptyList(); // updated atomically
  private final InspectionProfileWrapper myProfileWrapper;
  // toolId -> suppressed elements (for which tool.isSuppressedFor(element) == true)
  private final Map<String, Set<PsiElement>> mySuppressedElements = new ConcurrentHashMap<>();
  private final boolean myInspectInjectedPsi;

  LocalInspectionsPass(@NotNull PsiFile file,
                       @NotNull Document document,
                       int startOffset,
                       int endOffset,
                       @NotNull TextRange priorityRange,
                       boolean ignoreSuppressed,
                       @NotNull HighlightInfoProcessor highlightInfoProcessor,
                       boolean inspectInjectedPsi) {
    super(file.getProject(), document, DaemonBundle.message("pass.inspection"), file, null, new TextRange(startOffset, endOffset), true, highlightInfoProcessor);
    assert file.isPhysical() : "can't inspect non-physical file: " + file + "; " + file.getVirtualFile();
    myPriorityRange = priorityRange;
    myIgnoreSuppressed = ignoreSuppressed;
    setId(Pass.LOCAL_INSPECTIONS);

    InspectionProfileImpl profileToUse = ProjectInspectionProfileManager.getInstance(myProject).getCurrentProfile();
    Function<? super InspectionProfile, ? extends InspectionProfileWrapper>
      custom = InspectionProfileWrapper.getCustomInspectionProfileWrapper(file);
    myProfileWrapper = custom == null ? new InspectionProfileWrapper(profileToUse) : custom.apply(profileToUse);
    assert myProfileWrapper != null;
    myInspectInjectedPsi = inspectInjectedPsi;

    // initial guess
    setProgressLimit(300 * 2);
  }

  @NotNull
  private static String getShortcutText() {
    String myShortcutText;
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null) {
      myShortcutText = "";
    }
    else {
      myShortcutText = "(" + KeymapUtil.getShortcutsText(keymapManager.getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")";
    }
    return myShortcutText;
  }

  private @NotNull PsiFile getFile() {
    return myFile;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    List<? extends LocalInspectionToolWrapper> toolWrappers = getInspectionTools(myProfileWrapper);
    if (toolWrappers.isEmpty()) {
      return;
    }
    Consumer<InspectionRunner.InspectionContext> afterInsideProcessedCallback = context -> {
      InspectionRunner.InspectionProblemHolder holder = context.holder;
      holder.applyIncrementally = false; // do not apply incrementally outside visible range
      advanceProgress(1);
    };
    Consumer<InspectionRunner.InspectionContext> afterOutsideProcessedCallback = __ -> advanceProgress(1);
    BiPredicate<ProblemDescriptor, LocalInspectionToolWrapper> applyIncrementallyCallback = (descriptor, wrapper) -> {
      addDescriptorIncrementally(descriptor, wrapper, progress);
      return true;
    };
    InspectionRunner runner =
      new InspectionRunner(getFile(), myRestrictRange, myPriorityRange, myInspectInjectedPsi, true, progress, myIgnoreSuppressed,
                           myProfileWrapper, mySuppressedElements);
    List<? extends InspectionRunner.InspectionContext> contexts = runner.inspect(toolWrappers, true, applyIncrementallyCallback,
                                                                                 afterInsideProcessedCallback,
                                                                                 afterOutsideProcessedCallback);
    ProgressManager.checkCanceled();
    myInfos = createHighlightsFromContexts(contexts);
  }

  private static final TextAttributes NONEMPTY_TEXT_ATTRIBUTES = new UnmodifiableTextAttributes(){
    @Override
    public boolean isEmpty() {
      return false;
    }
  };

  private @Nullable static HighlightInfo highlightInfoFromDescriptor(@NotNull ProblemDescriptor problemDescriptor,
                                                                     @NotNull HighlightInfoType highlightInfoType,
                                                                     @NotNull @NlsContexts.DetailedDescription String message,
                                                                     @Nullable @NlsContexts.Tooltip String toolTip,
                                                                     @NotNull PsiElement psiElement,
                                                                     @NotNull List<IntentionAction> quickFixes,
                                                                     @NotNull String toolID,
                                                                     @Nullable EditorColorsScheme editorColorsScheme,
                                                                     @NotNull SeverityRegistrar severityRegistrar) {
    TextRange textRange = ((ProblemDescriptorBase)problemDescriptor).getTextRange();
    if (textRange == null) return null;
    boolean isFileLevel = psiElement instanceof PsiFile && textRange.equals(psiElement.getTextRange());

    HighlightSeverity severity = highlightInfoType.getSeverity(psiElement);
    TextAttributesKey attributesKey = ((ProblemDescriptorBase)problemDescriptor).getEnforcedTextAttributes();
    TextAttributes attributes = attributesKey == null || editorColorsScheme == null
                                ? severityRegistrar.getTextAttributesBySeverity(severity)
                                : editorColorsScheme.getAttributes(attributesKey);
    HighlightInfo.Builder b = HighlightInfo.newHighlightInfo(highlightInfoType)
      .range(psiElement, textRange.getStartOffset(), textRange.getEndOffset())
      .description(message)
      .severity(severity)
      .inspectionToolId(toolID);
    if (toolTip != null) b.escapedToolTip(toolTip);
    if (HighlightSeverity.INFORMATION.equals(severity) && attributes == null && toolTip == null && !quickFixes.isEmpty()) {
      // Hack to avoid filtering this info out in HighlightInfoFilterImpl even though its attributes are empty.
      // But it has quick fixes, so it needs to be created.
      attributes = NONEMPTY_TEXT_ATTRIBUTES;
    }
    if (attributes != null) b.textAttributes(attributes);
    if (problemDescriptor.isAfterEndOfLine()) b.endOfLine();
    if (isFileLevel) b.fileLevelAnnotation();
    if (problemDescriptor.getProblemGroup() != null) b.problemGroup(problemDescriptor.getProblemGroup());

    return b.create();
  }

  private final Long2ObjectMap<RangeMarker> ranges2markersCache = new Long2ObjectOpenHashMap<>(); // accessed in EDT only
  private final InjectedLanguageManager myInjectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
  private final List<HighlightInfo> infos = new ArrayList<>(2); // accessed in EDT only
  private final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
  private final Set<Pair<TextRange, String>> emptyActionRegistered = Collections.synchronizedSet(new HashSet<>());

  private void addDescriptorIncrementally(@NotNull ProblemDescriptor descriptor,
                                          @NotNull LocalInspectionToolWrapper tool,
                                          @NotNull ProgressIndicator indicator) {
    if (myIgnoreSuppressed) {
      LocalInspectionToolWrapper toolWrapper = tool;
      PsiElement psiElement = descriptor.getPsiElement();
      if (descriptor instanceof ProblemDescriptorWithReporterName) {
        String reportingToolName = ((ProblemDescriptorWithReporterName)descriptor).getReportingToolName();
        toolWrapper = (LocalInspectionToolWrapper)myProfileWrapper.getInspectionTool(reportingToolName, psiElement);
      }
      if (toolWrapper.getTool().isSuppressedFor(psiElement)) {
        registerSuppressedElements(psiElement, toolWrapper.getID(), toolWrapper.getAlternativeID(), mySuppressedElements);
        return;
      }
    }

    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return;
    PsiFile file = psiElement.getContainingFile();
    Document thisDocument = Objects.requireNonNull(documentManager.getDocument(file));

    HighlightSeverity severity = myProfileWrapper.getErrorLevel(tool.getDisplayKey(), file).getSeverity();
    
    ArrayList<HighlightInfo> newInfos = new ArrayList<>(2);
    createHighlightsForDescriptor(newInfos, emptyActionRegistered, file, thisDocument, tool, severity, descriptor, psiElement);
    ApplicationManager.getApplication().invokeLater(() -> {
      infos.clear();
      infos.addAll(newInfos);
      for (HighlightInfo info : infos) {
        EditorColorsScheme colorsScheme = getColorsScheme();
        UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, myDocument, getFile(),
                                                                   myRestrictRange.getStartOffset(),
                                                                   myRestrictRange.getEndOffset(),
                                                                   info, colorsScheme, getId(),
                                                                   ranges2markersCache);
      }
    }, __ -> myProject.isDisposed() || indicator.isCanceled());
  }

  @Override
  protected void applyInformationWithProgress() {
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myRestrictRange.getStartOffset(), myRestrictRange.getEndOffset(), myInfos, getColorsScheme(), getId());
  }

  private @NotNull List<HighlightInfo> createHighlightsFromContexts(@NotNull List<? extends InspectionRunner.InspectionContext> contexts) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Set<Pair<TextRange, String>> emptyActionRegistered = new HashSet<>();
    List<HighlightInfo> result = new ArrayList<>();
    for (InspectionRunner.InspectionContext context : contexts) {
      ProgressManager.checkCanceled();
      PsiFile file = context.holder.getFile();
      Document documentRange = documentManager.getDocument(file);
      if (documentRange == null) continue;

      LocalInspectionToolWrapper toolWrapper = context.tool;
      for (ProblemDescriptor descriptor : context.holder.getResults()) {
        ProgressManager.checkCanceled();
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
          continue;
        }

        if (SuppressionUtil.inspectionResultSuppressed(element, toolWrapper.getTool())) {
          continue;
        }
        createHighlightsForDescriptor(result, emptyActionRegistered, file, documentRange, toolWrapper, descriptor, element);
      }
    }
    return result;
  }

  private void createHighlightsForDescriptor(@NotNull List<? super HighlightInfo> outInfos,
                                             @NotNull Set<? super Pair<TextRange, String>> emptyActionRegistered,
                                             @NotNull PsiFile file,
                                             @NotNull Document documentRange,
                                             @NotNull LocalInspectionToolWrapper toolWrapper,
                                             @NotNull ProblemDescriptor descriptor,
                                             @NotNull PsiElement element) {
    HighlightSeverity severity;
    if (descriptor instanceof ProblemDescriptorWithReporterName) {
      String reportingToolName = ((ProblemDescriptorWithReporterName)descriptor).getReportingToolName();
      InspectionToolWrapper<?, ?> reportingTool = myProfileWrapper.getInspectionTool(reportingToolName, element);
      LOG.assertTrue(reportingTool instanceof LocalInspectionToolWrapper, reportingToolName);
      toolWrapper = (LocalInspectionToolWrapper)reportingTool;
      severity = myProfileWrapper.getErrorLevel(HighlightDisplayKey.find(reportingToolName), file).getSeverity();
    }
    else {
      severity = myProfileWrapper.getErrorLevel(toolWrapper.getDisplayKey(), file).getSeverity();
    }
    if (myIgnoreSuppressed && toolWrapper.getTool().isSuppressedFor(element)) {
      registerSuppressedElements(element, toolWrapper.getID(), toolWrapper.getAlternativeID(), mySuppressedElements);
      return;
    }
    createHighlightsForDescriptor(outInfos, emptyActionRegistered, file, documentRange, toolWrapper, severity, descriptor, element);
  }

  private void createHighlightsForDescriptor(@NotNull List<? super HighlightInfo> outInfos,
                                             @NotNull Set<? super Pair<TextRange, String>> emptyActionRegistered,
                                             @NotNull PsiFile file,
                                             @NotNull Document documentRange,
                                             @NotNull LocalInspectionToolWrapper toolWrapper,
                                             @NotNull HighlightSeverity severity,
                                             @NotNull ProblemDescriptor descriptor,
                                             @NotNull PsiElement element) {
    SeverityRegistrar severityRegistrar = myProfileWrapper.getProfileManager().getSeverityRegistrar();
    HighlightInfoType level = ProblemDescriptorUtil.highlightTypeFromDescriptor(descriptor, severity, severityRegistrar);
    ProblemPresentation presentation = ProblemDescriptorUtil.renderDescriptor(descriptor, element, ProblemDescriptorUtil.NONE);
    String message = presentation.getDescription();

    ProblemGroup problemGroup = descriptor.getProblemGroup();
    String problemName = problemGroup != null ? problemGroup.getProblemName() : null;
    String shortName = problemName != null ? problemName : toolWrapper.getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    InspectionProfile inspectionProfile = myProfileWrapper.getInspectionProfile();
    if (!inspectionProfile.isToolEnabled(key, file)) return;

    HighlightInfoType type = new InspectionHighlightInfoType(level, element);
    String plainMessage = message.startsWith("<html>")
                          ? StringUtil.unescapeXmlEntities(XmlStringUtil.stripHtml(message).replaceAll("<[^>]*>", ""))
                            .replaceAll("&nbsp;", " ")
                          : message;

    @NlsSafe String tooltip = null;
    if (descriptor.showTooltip()) {
      String rendered = presentation.getTooltip();
      tooltip = tooltips.intern(DaemonTooltipsUtil.getWrappedTooltip(rendered, shortName, getShortcutText(), showToolDescription(toolWrapper)));
    }
    List<IntentionAction> fixes = getQuickFixes(key, descriptor, emptyActionRegistered);
    HighlightInfo info = highlightInfoFromDescriptor(descriptor, type, plainMessage, tooltip, element, fixes, key.getID(),
                                                     getColorsScheme(), severityRegistrar);
    if (info == null) return;
    registerQuickFixes(info, fixes, shortName);

    PsiFile context = getTopLevelFileInBaseLanguage(element, file.getProject());
    PsiFile myContext = getTopLevelFileInBaseLanguage(file, file.getProject());
    if (context != myContext) {
      String errorMessage = "Reported element " + element +
                            " is not from the file '" + file.getVirtualFile().getPath() +
                            "' the inspection '" + shortName +
                            "' (" + toolWrapper.getTool().getClass() +
                            ") was invoked for. Message: '" + descriptor + "'.\nElement containing file: " +
                            context + "\nInspection invoked for file: " + myContext + "\n";
      PluginException.logPluginError(LOG, errorMessage, null, toolWrapper.getTool().getClass());
    }
    boolean isInInjected = myInspectInjectedPsi && file.getViewProvider() instanceof InjectedFileViewProvider;
    if (isInInjected) {
      injectToHost(outInfos, file, documentRange, element, fixes, info, shortName);
    }
    else {
      outInfos.add(info);
    }
  }

  private static void registerSuppressedElements(@NotNull PsiElement element,
                                                 @NotNull String id,
                                                 @Nullable String alternativeID,
                                                 @NotNull Map<? super String, Set<PsiElement>> outSuppressedElements) {
    outSuppressedElements.computeIfAbsent(id, __ -> new HashSet<>()).add(element);
    if (alternativeID != null) {
      outSuppressedElements.computeIfAbsent(alternativeID, __ -> new HashSet<>()).add(element);
    }
  }

  private void injectToHost(@NotNull List<? super HighlightInfo> outInfos,
                            @NotNull PsiFile file,
                            @NotNull Document documentRange,
                            @NotNull PsiElement element,
                            @NotNull List<? extends IntentionAction> fixes,
                            @NotNull HighlightInfo info,
                            @NotNull String shortName) {
    // todo we got to separate our "internal" prefixes/suffixes from user-defined ones
    // todo in the latter case the errors should be highlighted, otherwise not
    List<TextRange> editables = myInjectedLanguageManager.intersectWithAllEditableFragments(file, new TextRange(info.startOffset, info.endOffset));
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
        patched.markFromInjection();
        registerQuickFixes(patched, fixes, shortName);
        outInfos.add(patched);
      }
    }
  }

  private static PsiFile getTopLevelFileInBaseLanguage(@NotNull PsiElement element, @NotNull Project project) {
    PsiFile file = InjectedLanguageManager.getInstance(project).getTopLevelFile(element);
    FileViewProvider viewProvider = file.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }


  private static final Interner<String> tooltips = Interner.createWeakInterner();

  private static boolean showToolDescription(@NotNull LocalInspectionToolWrapper tool) {
    String staticDescription = tool.getStaticDescription();
    return staticDescription == null || !staticDescription.isEmpty();
  }

  private static void registerQuickFixes(@NotNull HighlightInfo highlightInfo,
                                         @NotNull List<? extends IntentionAction> quickFixes,
                                         @NotNull String shortName) {
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    for (IntentionAction quickFix : quickFixes) {
      QuickFixAction.registerQuickFixAction(highlightInfo, quickFix, key);
    }
  }

  private static @NotNull List<IntentionAction> getQuickFixes(@NotNull HighlightDisplayKey key,
                                                              @NotNull ProblemDescriptor descriptor,
                                                              @NotNull Set<? super Pair<TextRange, String>> emptyActionRegistered) {
    List<IntentionAction> result = new SmartList<>();
    boolean needEmptyAction = true;
    QuickFix<?>[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length != 0) {
      for (int k = 0; k < fixes.length; k++) {
        QuickFix<?> fix = fixes[k];
        if (fix == null) {
          throw new IllegalStateException("Inspection " + key + " returns null quick fix in its descriptor: " + descriptor + "; array: " + Arrays.toString(fixes));
        }
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
    if (needEmptyAction && emptyActionRegistered.add(Pair.create(((ProblemDescriptorBase)descriptor).getTextRange(), key.toString()))) {
      String displayNameByKey = HighlightDisplayKey.getDisplayNameByKey(key);
      LOG.assertTrue(displayNameByKey != null, key.toString());
      IntentionAction emptyIntentionAction = new EmptyIntentionAction(displayNameByKey);
      result.add(emptyIntentionAction);
    }
    return result;
  }

  @NotNull
  List<LocalInspectionToolWrapper> getInspectionTools(@NotNull InspectionProfileWrapper profile) {
    List<InspectionToolWrapper<?, ?>> toolWrappers = profile.getInspectionProfile().getInspectionTools(getFile());
    InspectionProfileWrapper.checkInspectionsDuplicates(toolWrappers);
    List<LocalInspectionToolWrapper> enabled = new ArrayList<>();
    for (InspectionToolWrapper<?, ?> toolWrapper : toolWrappers) {
      ProgressManager.checkCanceled();
      if (toolWrapper instanceof LocalInspectionToolWrapper && !isAcceptableLocalTool((LocalInspectionToolWrapper)toolWrapper)) {
        continue;
      }
      HighlightDisplayKey key = toolWrapper.getDisplayKey();
      if (!profile.isToolEnabled(key, getFile())) continue;
      if (HighlightDisplayLevel.DO_NOT_SHOW.equals(profile.getErrorLevel(key, getFile()))) continue;
      LocalInspectionToolWrapper wrapper;
      if (toolWrapper instanceof LocalInspectionToolWrapper) {
        wrapper = (LocalInspectionToolWrapper)toolWrapper;
      }
      else {
        wrapper = ((GlobalInspectionToolWrapper)toolWrapper).getSharedLocalInspectionToolWrapper();
        if (wrapper == null || !isAcceptableLocalTool(wrapper)) continue;
      }
      String language = wrapper.getLanguage();
      if (language != null && Language.findLanguageByID(language) == null) {
        continue; // filter out at least unknown languages
      }
      if (myIgnoreSuppressed && wrapper.getTool().isSuppressedFor(getFile())) {
        continue;
      }
      enabled.add(wrapper);
    }
    return enabled;
  }

  protected boolean isAcceptableLocalTool(@NotNull LocalInspectionToolWrapper wrapper) {
    return true;
  }

  @Override
  public @NotNull List<HighlightInfo> getInfos() {
    return Collections.unmodifiableList(myInfos);
  }

  public static class InspectionHighlightInfoType extends HighlightInfoType.HighlightInfoTypeImpl {
    InspectionHighlightInfoType(@NotNull HighlightInfoType level, @NotNull PsiElement element) {
      super(level.getSeverity(element), level.getAttributesKey());
    }
  }
}
