// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.DaemonTooltipsUtil;
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.UnmodifiableTextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedFileViewProvider;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

final class LocalInspectionsPass extends ProgressableTextEditorHighlightingPass implements PossiblyDumbAware {
  private static final Logger LOG = Logger.getInstance(LocalInspectionsPass.class);
  private final TextRange myPriorityRange;
  private final boolean myIgnoreSuppressed;
  private final HighlightInfoUpdater myHighlightInfoUpdater;
  private volatile List<? extends HighlightInfo> myInfos = Collections.emptyList(); // updated atomically
  private final InspectionProfileWrapper myProfileWrapper;
  // toolId -> suppressed elements (for which tool.isSuppressedFor(element) == true)
  private final Map<String, Set<PsiElement>> mySuppressedElements = new ConcurrentHashMap<>();
  private final boolean myInspectInjectedPsi;

  LocalInspectionsPass(@NotNull PsiFile file,
                       @NotNull Document document,
                       @NotNull TextRange restrictRange,
                       @NotNull TextRange priorityRange,
                       boolean ignoreSuppressed,
                       @NotNull HighlightInfoUpdater highlightInfoUpdater,
                       boolean inspectInjectedPsi) {
    super(file.getProject(), document, DaemonBundle.message("pass.inspection"), file, null, restrictRange, true, HighlightInfoProcessor.getEmpty());
    myHighlightInfoUpdater = highlightInfoUpdater;
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
  }

  private @NotNull PsiFile getFile() {
    return myFile;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    List<HighlightInfo> fileInfos = Collections.synchronizedList(new ArrayList<>());
    List<? extends LocalInspectionToolWrapper> toolWrappers = getInspectionTools(myProfileWrapper);
    var result = new Object() {
      List<? extends InspectionRunner.InspectionContext> resultContexts = List.of();
      List<PsiFile> injectedFragments = List.of();
    };

    // In dumb mode, we need to run dumb-aware inspections only.
    // But we need to keep highlights from currently enabled but inactive smart-only inspections.
    List<? extends LocalInspectionToolWrapper> activeToolWrappers;
    List<? extends LocalInspectionToolWrapper> disabledSmartOnlyToolWrappers;
    if (isDumbMode()) {
      activeToolWrappers = toolWrappers.stream().parallel().filter(wrapper -> wrapper.isDumbAware()).toList();

      if (activeToolWrappers.isEmpty()) {
        disabledSmartOnlyToolWrappers = toolWrappers;
      }
      else {
        disabledSmartOnlyToolWrappers = ContainerUtil.filter(toolWrappers, wrapper -> !wrapper.isDumbAware());
      }
    }
    else {
      activeToolWrappers = toolWrappers;
      disabledSmartOnlyToolWrappers = List.of();
    }

    if (!activeToolWrappers.isEmpty()) {
      Consumer<? super ManagedHighlighterRecycler> withRecycler = invalidPsiRecycler -> {
        InspectionRunner.ApplyIncrementallyCallback applyIncrementallyCallback = (descriptors, holder, visitingPsiElement, shortName) -> {
          List<HighlightInfo> allInfos = descriptors.isEmpty() ? null : new ArrayList<>(descriptors.size());
          for (ProblemDescriptor descriptor : descriptors) {
            PsiElement descriptorPsiElement = descriptor.getPsiElement();
            HighlightDisplayKey key = HighlightDisplayKey.find(holder.myToolWrapper.getShortName());
            if (LOG.isTraceEnabled()) {
              LOG.trace("collectInformationWithProgress:applyIncrementallyCallback: toolId:" + holder.myToolWrapper.getShortName() + ": " +
                        descriptor + "; psi:" + descriptorPsiElement + "; isEnabled:" +
                        myProfileWrapper.getInspectionProfile().isToolEnabled(key, getFile()));
            }
            if (descriptorPsiElement != null) {
              createHighlightsForDescriptor(descriptor, descriptorPsiElement, holder.myToolWrapper, info -> {
                synchronized (holder.toolInfos) {
                  holder.toolInfos.add(info);
                  allInfos.add(info);
                }
              });
            }
          }
          myHighlightInfoUpdater.psiElementVisited(shortName, visitingPsiElement, ContainerUtil.notNullize(allInfos), getDocument(), holder.getFile(),
                                                   myProject, getHighlightingSession(), invalidPsiRecycler);
          if (allInfos != null) {
            fileInfos.addAll(allInfos);
          }
        };

        Consumer<InspectionRunner.InspectionContext> contextFinishedCallback = context -> {
          InspectionRunner.InspectionProblemHolder holder = context.holder();
          Collection<HighlightInfo> infos;
          synchronized (holder.toolInfos) {
            infos = new ArrayList<>(holder.toolInfos);
          }
          if (LOG.isTraceEnabled()) {
            LOG.trace("contextFinishedCallback: " + context.tool() + "; toolId:" + context.tool().getShortName() + "; " +
                      infos + "; " + context.elementsInside().size() + "/" + context.elementsOutside().size()+" elements");
          }
        };

        InspectionRunner runner = new InspectionRunner(getFile(), myRestrictRange, myPriorityRange, myInspectInjectedPsi, true,
                                                       isDumbMode(), progress, myIgnoreSuppressed, myProfileWrapper, mySuppressedElements);

        result.resultContexts = runner.inspect(activeToolWrappers,
                                        ((HighlightingSessionImpl)getHighlightingSession()).getMinimumSeverity(),
                                        true,
                                        applyIncrementallyCallback,
                                        contextFinishedCallback,
                                        wrapper -> !wrapper.getTool().isSuppressedFor(getFile()));
        myInfos = fileInfos;
        result.injectedFragments = runner.getInjectedFragments();
      };
      if (myHighlightInfoUpdater instanceof HighlightInfoUpdaterImpl impl) {
        impl.runWithInvalidPsiRecycler(getHighlightingSession(), HighlightInfoUpdaterImpl.WhatTool.INSPECTION, withRecycler);
      }
      else {
        ManagedHighlighterRecycler.runWithRecycler(getHighlightingSession(), withRecycler);
      }
    }
    if (myHighlightInfoUpdater instanceof HighlightInfoUpdaterImpl impl) {
      Set<Pair<Object, PsiFile>> pairs = ContainerUtil.map2Set(result.resultContexts, context -> Pair.create(context.tool().getShortName(), context.psiFile()));
      impl.removeHighlightsForObsoleteTools(getFile(), getDocument(), result.injectedFragments, pairs, getHighlightingSession(), disabledSmartOnlyToolWrappers);
      impl.removeWarningsInsideErrors(result.injectedFragments, getDocument(), getHighlightingSession());  // must be the last
    }
  }

  private static final TextAttributes NONEMPTY_TEXT_ATTRIBUTES = new UnmodifiableTextAttributes(){
    @Override
    public boolean isEmpty() {
      return false;
    }
  };

  private HighlightInfo.Builder highlightInfoFromDescriptor(@NotNull ProblemDescriptor problemDescriptor,
                                                            @NotNull HighlightInfoType highlightInfoType,
                                                            @NotNull @NlsContexts.DetailedDescription String message,
                                                            @Nullable @NlsContexts.Tooltip String toolTip,
                                                            @NotNull PsiElement psiElement,
                                                            @NotNull List<? extends IntentionAction> quickFixes,
                                                            @NotNull HighlightDisplayKey key,
                                                            @Nullable EditorColorsScheme editorColorsScheme,
                                                            @NotNull SeverityRegistrar severityRegistrar) {
    TextRange textRange = ((ProblemDescriptorBase)problemDescriptor).getTextRange();
    if (textRange == null) return null;
    boolean isFileLevel = psiElement instanceof PsiFile && textRange.equals(psiElement.getTextRange());

    HighlightSeverity severity = highlightInfoType.getSeverity(psiElement);
    TextAttributesKey attributesKey = ((ProblemDescriptorBase)problemDescriptor).getEnforcedTextAttributes();
    if (problemDescriptor.getHighlightType() == ProblemHighlightType.GENERIC_ERROR_OR_WARNING && attributesKey == null) {
      attributesKey = myProfileWrapper.getInspectionProfile().getEditorAttributes(key.getShortName(), getFile());
    }
    TextAttributes attributes = attributesKey == null || editorColorsScheme == null || severity.getName().equals(attributesKey.getExternalName())
                                ? severityRegistrar.getTextAttributesBySeverity(severity)
                                : editorColorsScheme.getAttributes(attributesKey);
    if (attributesKey != null && (attributes == null || attributes.isEmpty())) {
      attributes = severityRegistrar.getCustomSeverityTextAttributes(attributesKey);
    }
    HighlightInfo.Builder b = HighlightInfo.newHighlightInfo(highlightInfoType)
      .range(psiElement, textRange.getStartOffset(), textRange.getEndOffset())
      .description(message)
      .severity(severity);
    if (toolTip != null) b.escapedToolTip(toolTip);
    if (HighlightSeverity.INFORMATION.equals(severity) && attributes == null && toolTip == null && !quickFixes.isEmpty()) {
      // Hack to avoid filtering this info out in HighlightInfoFilterImpl even though its attributes are empty.
      // But it has quick fixes, so it needs to be created.
      attributes = NONEMPTY_TEXT_ATTRIBUTES;
    }
    if (attributesKey != null) b.textAttributes(attributesKey);
    if (attributes != null) b.textAttributes(attributes);
    if (problemDescriptor.isAfterEndOfLine()) b.endOfLine();
    if (isFileLevel) b.fileLevelAnnotation();
    if (problemDescriptor.getProblemGroup() != null) b.problemGroup(problemDescriptor.getProblemGroup());

    return b;
  }

  private final InjectedLanguageManager myInjectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
  private final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
  private final Set<Pair<TextRange, String>> emptyActionRegistered = Collections.synchronizedSet(new HashSet<>());

  private void createHighlightsForDescriptor(@NotNull ProblemDescriptor descriptor,
                                             @NotNull PsiElement psiElement,
                                             @NotNull LocalInspectionToolWrapper tool,
                                             @NotNull Consumer<? super HighlightInfo> infoProcessor) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (myIgnoreSuppressed) {
      LocalInspectionToolWrapper toolWrapper = tool;
      if (descriptor instanceof ProblemDescriptorWithReporterName name) {
        String reportingToolName = name.getReportingToolName();
        toolWrapper = (LocalInspectionToolWrapper)myProfileWrapper.getInspectionTool(reportingToolName, psiElement);
      }
      if (toolWrapper.getTool().isSuppressedFor(psiElement)) {
        registerSuppressedElements(psiElement, toolWrapper.getID(), toolWrapper.getAlternativeID(), mySuppressedElements);
        return;
      }
    }

    PsiFile file = psiElement.getContainingFile();

    HighlightDisplayKey displayKey = tool.getDisplayKey();
    if (displayKey == null) {
      LOG.error("getDisplayKey() is null for " + tool + " (" + tool.getTool() + " ; " + tool.getTool().getClass() + ")");
      return;
    }
    HighlightSeverity severity = myProfileWrapper.getErrorLevel(displayKey, file).getSeverity();

    createHighlightsForDescriptor(emptyActionRegistered, file, tool, severity, descriptor, psiElement, infoProcessor);
  }

  @Override
  protected void applyInformationWithProgress() {
    ((HighlightingSessionImpl)getHighlightingSession()).applyFileLevelHighlightsRequests();
  }

  private void createHighlightsForDescriptor(@NotNull Set<? super Pair<TextRange, String>> emptyActionRegistered,
                                             @NotNull PsiFile file,
                                             @NotNull LocalInspectionToolWrapper toolWrapper,
                                             @NotNull HighlightSeverity severity,
                                             @NotNull ProblemDescriptor descriptor,
                                             @NotNull PsiElement element,
                                             @NotNull Consumer<? super HighlightInfo> outInfos) {
    SeverityRegistrar severityRegistrar = myProfileWrapper.getProfileManager().getSeverityRegistrar();
    HighlightInfoType level = ProblemDescriptorUtil.highlightTypeFromDescriptor(descriptor, severity, severityRegistrar);
    ProblemPresentation presentation = ProblemDescriptorUtil.renderDescriptor(descriptor, element, ProblemDescriptorUtil.NONE);
    String message = presentation.getDescription();

    ProblemGroup problemGroup = descriptor.getProblemGroup();
    String problemName = problemGroup != null ? problemGroup.getProblemName() : null;
    String shortName = problemName != null ? problemName : toolWrapper.getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    InspectionProfile inspectionProfile = myProfileWrapper.getInspectionProfile();
    if (!inspectionProfile.isToolEnabled(key, file)) {
      return;
    }

    HighlightInfoType type = new InspectionHighlightInfoType(level, element);
    String plainMessage = message.startsWith("<html>")
                          ? StringUtil.unescapeXmlEntities(XmlStringUtil.stripHtml(message).replaceAll("<[^>]*>", ""))
                            .replaceAll("&nbsp;|&#32;", " ")
                          : message;

    @NlsSafe String tooltip = null;
    if (descriptor.showTooltip()) {
      String rendered = presentation.getTooltip();
      tooltip = tooltips.intern(DaemonTooltipsUtil.getWrappedTooltip(rendered, shortName,
                                                                     showToolDescription(toolWrapper)));
    }
    List<IntentionAction> fixes = getQuickFixes(key, descriptor, emptyActionRegistered);
    HighlightInfo.Builder builder = highlightInfoFromDescriptor(descriptor, type, plainMessage, tooltip, element, fixes, key, getColorsScheme(), severityRegistrar);
    if (builder == null) {
      return;
    }
    registerQuickFixes(builder, fixes, shortName);

    PsiFile context = getTopLevelFileInBaseLanguage(element, file.getProject());
    PsiFile myContext = getTopLevelFileInBaseLanguage(file, file.getProject());
    if (context != myContext) {
      String errorMessage = "Reported element " + element + " ("+element.getClass()+")"+
                            " is not from the file the inspection '" + shortName +
                            "' (" + toolWrapper.getTool().getClass() +
                            ") was invoked for. Message: '" + descriptor + "'.\nElement containing file: " +
                            PsiUtilCore.getVirtualFile(context) + "\nInspection invoked for the file: " + PsiUtilCore.getVirtualFile(myContext) + "\n";
      PluginException.logPluginError(LOG, errorMessage, null, toolWrapper.getTool().getClass());
    }
    boolean isInInjected = myInspectInjectedPsi && file.getViewProvider() instanceof InjectedFileViewProvider;
    HighlightInfo info = builder.create();

    if (info == null || !UpdateHighlightersUtil.HighlightInfoPostFilters.accept(myProject, info)) {
      return;
    }
    info.toolId = toolWrapper.getShortName();
    if (isInInjected) {
      Document documentRange = documentManager.getDocument(file);
      if (documentRange != null) {
        injectToHost(file, documentRange, element, fixes, info, shortName, outInfos);
      }
    }
    else {
      outInfos.accept(info);
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

  private void injectToHost(@NotNull PsiFile file,
                            @NotNull Document documentRange,
                            @NotNull PsiElement element,
                            @NotNull List<? extends IntentionAction> fixes,
                            @NotNull HighlightInfo info,
                            @NotNull String shortName,
                            @NotNull Consumer<? super HighlightInfo> outInfos) {
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
      if (start != end || info.startOffset == info.endOffset) {
        registerQuickFixes(builder, fixes, shortName);
        HighlightInfo patched = builder.createUnconditionally();
        patched.markFromInjection();
        patched.toolId = info.toolId;
        outInfos.accept(patched);
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

  private static void registerQuickFixes(@NotNull HighlightInfo.Builder builder,
                                         @NotNull List<? extends IntentionAction> quickFixes,
                                         @NotNull String shortName) {
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    for (IntentionAction quickFix : quickFixes) {
      builder.registerFix(quickFix, null, HighlightDisplayKey.getDisplayNameByKey(key), null, key);
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
    HintAction hintAction = descriptor instanceof ProblemDescriptorImpl impl ? impl.getHintAction() : null;
    if (hintAction != null) {
      result.add(hintAction);
      needEmptyAction = false;
    }
    if (((ProblemDescriptorBase)descriptor).getEnforcedTextAttributes() != null) {
      needEmptyAction = false;
    }
    if (needEmptyAction && emptyActionRegistered.add(Pair.create(((ProblemDescriptorBase)descriptor).getTextRange(), key.getShortName()))) {
      String displayNameByKey = HighlightDisplayKey.getDisplayNameByKey(key);
      LOG.assertTrue(displayNameByKey != null, key.toString());

      result.add(Registry.is("llm.empty.intention.generation")
                 ? new EmptyIntentionGeneratorIntention(displayNameByKey, descriptor.getDescriptionTemplate())
                 : new EmptyIntentionAction(displayNameByKey));
    }
    return result;
  }

  private @NotNull List<LocalInspectionToolWrapper> getInspectionTools(@NotNull InspectionProfileWrapper profile) {
    List<InspectionToolWrapper<?, ?>> toolWrappers = profile.getInspectionProfile().getInspectionTools(getFile());

    if (LOG.isDebugEnabled()) {
      // this triggers heavy class loading of all inspections, do not run if DEBUG not enabled
      InspectionProfileWrapper.checkInspectionsDuplicates(toolWrappers);
    }

    List<LocalInspectionToolWrapper> enabled = new ArrayList<>();
    Set<String> projectTypes = ProjectTypeService.getProjectTypeIds(myProject);
    boolean isTests = ApplicationManager.getApplication().isUnitTestMode();

    for (InspectionToolWrapper<?, ?> toolWrapper : toolWrappers) {
      ProgressManager.checkCanceled();

      if (!isTests && !toolWrapper.isApplicable(projectTypes)) continue;

      HighlightDisplayKey key = toolWrapper.getDisplayKey();
      if (!profile.isToolEnabled(key, getFile())) continue;
      if (HighlightDisplayLevel.DO_NOT_SHOW.equals(profile.getErrorLevel(key, getFile()))) continue;
      LocalInspectionToolWrapper wrapper;
      if (toolWrapper instanceof LocalInspectionToolWrapper local) {
        wrapper = local;
      }
      else {
        wrapper = ((GlobalInspectionToolWrapper)toolWrapper).getSharedLocalInspectionToolWrapper();
        if (wrapper == null) continue;
      }
      String language = wrapper.getLanguage();
      if (language != null && Language.findLanguageByID(language) == null) {
        continue; // filter out at least unknown languages
      }

      if (myIgnoreSuppressed
          && wrapper.isApplicable(getFile().getLanguage())
          && wrapper.getTool().isSuppressedFor(getFile())) {
        // inspections that do not match file language are excluded later in InspectionRunner.inspect
        continue;
      }

      enabled.add(wrapper);
    }
    return enabled;
  }

  @Override
  public @NotNull List<HighlightInfo> getInfos() {
    return Collections.unmodifiableList(myInfos);
  }

  @Override
  public boolean isDumbAware() {
    return Registry.is("ide.dumb.aware.inspections");
  }

  private static final class InspectionHighlightInfoType extends HighlightInfoType.HighlightInfoTypeImpl {
    InspectionHighlightInfoType(@NotNull HighlightInfoType level, @NotNull PsiElement element) {
      super(level.getSeverity(element), level.getAttributesKey());
    }

    @Override
    public boolean isInspectionHighlightInfoType() {
      return true;
    }
  }
}
