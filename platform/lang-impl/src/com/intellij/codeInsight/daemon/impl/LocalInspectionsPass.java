/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.concurrency.JobUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
public class LocalInspectionsPass extends ProgressableTextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LocalInspectionsPass");
  private final int myStartOffset;
  private final int myEndOffset;
  private final TextRange myPriorityRange;
  private final ConcurrentMap<PsiFile, List<InspectionResult>> result = new ConcurrentHashMap<PsiFile, List<InspectionResult>>();
  static final String PRESENTABLE_NAME = DaemonBundle.message("pass.inspection");
  private volatile List<HighlightInfo> myInfos = Collections.emptyList();
  static final Icon IN_PROGRESS_ICON = IconLoader.getIcon("/general/inspectionInProgress.png");
  private final String myShortcutText;
  private final SeverityRegistrar mySeverityRegistrar;
  private final InspectionProfileWrapper myProfileWrapper;
  private boolean myFailFastOnAcquireReadAction;

  public LocalInspectionsPass(@NotNull PsiFile file, @Nullable Document document, int startOffset, int endOffset) {
    this(file, document, startOffset, endOffset, new TextRange(0, 0));
  }
  public LocalInspectionsPass(@NotNull PsiFile file, @Nullable Document document, int startOffset, int endOffset, @NotNull TextRange priorityRange) {
    super(file.getProject(), document, IN_PROGRESS_ICON, PRESENTABLE_NAME, file, true);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPriorityRange = priorityRange;
    setId(Pass.LOCAL_INSPECTIONS);

    final KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null) {
      final Keymap keymap = keymapManager.getActiveKeymap();
      myShortcutText = keymap == null ? "" : "(" + KeymapUtil.getShortcutsText(keymap.getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")";
    }
    else {
      myShortcutText = "";
    }
    mySeverityRegistrar = SeverityRegistrar.getInstance(myProject);
    InspectionProfileWrapper customProfile = file.getUserData(InspectionProfileWrapper.KEY);
    myProfileWrapper = customProfile == null ? InspectionProjectProfileManager.getInstance(myProject).getProfileWrapper() : customProfile;
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    if (!HighlightLevelUtil.shouldInspect(myFile)) return;
    final InspectionManagerEx iManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    final InspectionProfileWrapper profile = myProfileWrapper;
    final List<LocalInspectionTool> tools = DumbService.getInstance(myProject).filterByDumbAwareness(getInspectionTools(profile));
    checkInspectionsDuplicates(tools);
    inspect(tools, iManager, true, true, true, progress);
  }

  // check whether some inspection got registered twice by accident. Bit only once.
  private static boolean alreadyChecked;
  private static void checkInspectionsDuplicates(List<LocalInspectionTool> tools) {
    if (alreadyChecked) return;
    alreadyChecked = true;
    Set<LocalInspectionTool> uniqTools = new THashSet<LocalInspectionTool>(tools.size());
    for (LocalInspectionTool tool : tools) {
      if (!uniqTools.add(tool)) {
        LOG.error("Inspection " + tool.getDisplayName() + " (" + tool.getClass() + ") already registered");
      }
    }
  }

  public void doInspectInBatch(final InspectionManagerEx iManager, List<InspectionProfileEntry> toolWrappers, boolean ignoreSuppressed) {
    Map<LocalInspectionTool, LocalInspectionToolWrapper> tool2Wrapper = new THashMap<LocalInspectionTool, LocalInspectionToolWrapper>(toolWrappers.size());
    for (InspectionProfileEntry toolWrapper : toolWrappers) {
      tool2Wrapper.put(((LocalInspectionToolWrapper)toolWrapper).getTool(), (LocalInspectionToolWrapper)toolWrapper);
    }
    List<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>(tool2Wrapper.keySet());

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    inspect(tools, iManager, false, ignoreSuppressed, false, progress);
    addDescriptorsFromInjectedResults(tool2Wrapper, iManager);
    List<InspectionResult> resultList = result.get(myFile);
    if (resultList == null) return;
    for (InspectionResult inspectionResult : resultList) {
      LocalInspectionTool tool = inspectionResult.tool;
      LocalInspectionToolWrapper toolWrapper = tool2Wrapper.get(tool);
      for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
        toolWrapper.addProblemDescriptors(Collections.singletonList(descriptor), ignoreSuppressed);
      }
    }
  }

  private void addDescriptorsFromInjectedResults(Map<LocalInspectionTool, LocalInspectionToolWrapper> tool2Wrapper, InspectionManagerEx iManager) {
    Set<TextRange> emptyActionRegistered = new THashSet<TextRange>();
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);

    for (Map.Entry<PsiFile, List<InspectionResult>> entry : result.entrySet()) {
      PsiFile file = entry.getKey();
      if (file == myFile) continue; // not injected
      DocumentWindow documentRange = (DocumentWindow)documentManager.getDocument(file);
      List<InspectionResult> resultList = entry.getValue();
      for (InspectionResult inspectionResult : resultList) {
        LocalInspectionTool tool = inspectionResult.tool;
        HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), myFile).getSeverity();
        for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {

          PsiElement psiElement = descriptor.getPsiElement();
          if (InspectionManagerEx.inspectionResultSuppressed(psiElement, tool)) continue;
          HighlightInfoType level = highlightTypeFromDescriptor(descriptor, severity);
          HighlightInfo info = createHighlightInfo(descriptor, tool, level,emptyActionRegistered);
          if (info == null) continue;
          List<TextRange> editables = ilManager.intersectWithAllEditableFragments(file, new TextRange(info.startOffset, info.endOffset));
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
            ProblemDescriptor patchedDescriptor = iManager.createProblemDescriptor(myFile, hostRange, descriptor.getDescriptionTemplate(),
                                                                                   descriptor.getHighlightType(), true, localFixes);
            LocalInspectionToolWrapper toolWrapper = tool2Wrapper.get(tool);
            toolWrapper.addProblemDescriptors(Collections.singletonList(patchedDescriptor), true);
          }
        }
      }
    }
  }

  private void inspect(final List<LocalInspectionTool> tools,
                       final InspectionManagerEx iManager,
                       final boolean isOnTheFly,
                       final boolean ignoreSuppressed,
                       boolean failFastOnAcquireReadAction,
                       @NotNull final ProgressIndicator indicator) {
    myFailFastOnAcquireReadAction = failFastOnAcquireReadAction;
    if (tools.isEmpty()) return;

    ArrayList<PsiElement> inside = new ArrayList<PsiElement>();
    ArrayList<PsiElement> outside = new ArrayList<PsiElement>();
    Divider.getInsideAndOutside(myFile, myStartOffset, myEndOffset, myPriorityRange, inside, outside, HighlightLevelUtil.AnalysisLevel.HIGHLIGHT_AND_INSPECT);

    setProgressLimit(1L * tools.size() * (inside.size() + outside.size()));
    final LocalInspectionToolSession session = new LocalInspectionToolSession(myFile, myStartOffset, myEndOffset);

    List<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>> init = new ArrayList<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>>();
    visitPriorityElementsAndInit(tools, iManager, isOnTheFly, ignoreSuppressed, indicator, inside, session, init);
    visitRestElementsAndCleanup(tools,isOnTheFly,ignoreSuppressed, indicator, outside, session, init);

    indicator.checkCanceled();

    myInfos = new ArrayList<HighlightInfo>();
    addHighlightsFromResults(myInfos);
  }

  private void visitPriorityElementsAndInit(List<LocalInspectionTool> tools,
                                final InspectionManagerEx iManager,
                                final boolean isOnTheFly,
                                final boolean ignoreSuppressed,
                                final ProgressIndicator indicator,
                                final List<PsiElement> elements,
                                final LocalInspectionToolSession session,
                                final List<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>> init) {
    boolean result = JobUtil.invokeConcurrentlyUnderMyProgress(tools, new Processor<LocalInspectionTool>() {
      public boolean process(final LocalInspectionTool tool) {
        final ProgressManager progressManager = ProgressManager.getInstance();
        indicator.checkCanceled();
        ProgressIndicator localIndicator = progressManager.getProgressIndicator();

        ProgressIndicator original = ((ProgressWrapper)localIndicator).getOriginalProgressIndicator();
        LOG.assertTrue(original == indicator, original);

        ApplicationManager.getApplication().assertReadAccessAllowed();

        ProblemsHolder holder = new ProblemsHolder(iManager, myFile, isOnTheFly) {
          @Override
          public void registerProblem(@NotNull ProblemDescriptor descriptor) {
            super.registerProblem(descriptor);
            if (isOnTheFly) {
              addDescriptorIncrementally(descriptor, tool, ignoreSuppressed, indicator);
            }
          }
        };
        PsiElementVisitor elementVisitor = tool.buildVisitor(holder, isOnTheFly, session);
        synchronized (init) {
          init.add(Trinity.create(tool, holder, elementVisitor));
        }
        //noinspection ConstantConditions
        if(elementVisitor == null) {
          LOG.error("Tool " + tool + " must not return null from the buildVisitor() method");
        }
        tool.inspectionStarted(session);
        for (PsiElement element : elements) {
          indicator.checkCanceled();
          element.accept(elementVisitor);
        }

        advanceProgress(elements.size());

        if (holder.hasResults()) {
          appendDescriptors(myFile, holder.getResults(), tool);
        }
        return true;
      }
    }, myFailFastOnAcquireReadAction);
    if (!result) throw new ProcessCanceledException();
    inspectInjectedPsi(elements, tools, isOnTheFly, ignoreSuppressed, indicator, session);
  }

  private void visitRestElementsAndCleanup(
                            List<LocalInspectionTool> tools,
                            final boolean isOnTheFly,
                            final boolean ignoreSuppressed,
                            final ProgressIndicator indicator,
                            final List<PsiElement> elements,
                            final LocalInspectionToolSession session,
                            List<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>> init) {
    boolean result = JobUtil.invokeConcurrentlyUnderMyProgress(init, new Processor<Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor>>() {
      @Override
      public boolean process(Trinity<LocalInspectionTool, ProblemsHolder, PsiElementVisitor> i) {
        LocalInspectionTool tool = i.first;
        final ProgressManager progressManager = ProgressManager.getInstance();
        indicator.checkCanceled();
        ProgressIndicator localIndicator = progressManager.getProgressIndicator();

        ProgressIndicator original = ((ProgressWrapper)localIndicator).getOriginalProgressIndicator();
        LOG.assertTrue(original == indicator, original);

        ApplicationManager.getApplication().assertReadAccessAllowed();

        ProblemsHolder holder = i.second;
        PsiElementVisitor elementVisitor = i.third;
        for (PsiElement element : elements) {
          indicator.checkCanceled();
          element.accept(elementVisitor);
        }

        advanceProgress(elements.size());

        tool.inspectionFinished(session);

        if (holder.hasResults()) {
          appendDescriptors(myFile, holder.getResults(), tool);
        }
        return true;
      }
    }, myFailFastOnAcquireReadAction);
    if (!result) {
      throw new ProcessCanceledException();
    }
    inspectInjectedPsi(elements, tools, isOnTheFly, ignoreSuppressed, indicator, session);

  }

  private void inspectInjectedPsi(final List<PsiElement> elements,
                                  final List<LocalInspectionTool> tools,
                                  final boolean onTheFly,
                                  final boolean ignoreSuppressed, final ProgressIndicator indicator,
                                  final LocalInspectionToolSession session) {
    final Set<PsiFile> injected = new THashSet<PsiFile>();
    for (PsiElement element : elements) {
      InjectedLanguageUtil.enumerate(element, myFile, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          injected.add(injectedPsi);
        }
      }, false);
    }
    if (!JobUtil.invokeConcurrentlyUnderMyProgress(new ArrayList<PsiFile>(injected), new Processor<PsiFile>() {
      public boolean process(final PsiFile injectedPsi) {
        doInspectInjectedPsi(injectedPsi, tools, onTheFly, ignoreSuppressed, indicator, session);
        return true;
      }
    }, myFailFastOnAcquireReadAction)) throw new ProcessCanceledException();
  }

  public Collection<HighlightInfo> getHighlights() {
    List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();

    addHighlightsFromResults(highlights);
    return highlights;
  }

  private static HighlightInfo highlightInfoFromDescriptor(final ProblemDescriptor problemDescriptor,
                                                           final HighlightInfoType highlightInfoType,
                                                           final String message,
                                                           final String toolTip) {
    TextRange textRange = ((ProblemDescriptorImpl)problemDescriptor).getTextRange();
    PsiElement element = problemDescriptor.getPsiElement();
    boolean isFileLevel = element instanceof PsiFile && textRange.equals(element.getTextRange());

    return new HighlightInfo(null, highlightInfoType, textRange.getStartOffset(), textRange.getEndOffset(), message, toolTip,
                             highlightInfoType.getSeverity(element), problemDescriptor.isAfterEndOfLine(), null, isFileLevel);
  }

  private final AtomicBoolean haveInfosToProcess = new AtomicBoolean();
  private final ConcurrentLinkedQueue<Pair<ProblemDescriptor, LocalInspectionTool>> infosToAdd = new ConcurrentLinkedQueue<Pair<ProblemDescriptor, LocalInspectionTool>>();
  private final Set<TextRange> emptyActionRegistered = Collections.synchronizedSet(new HashSet<TextRange>());

  private void addDescriptorIncrementally(@NotNull final ProblemDescriptor descriptor,
                                          @NotNull final LocalInspectionTool tool,
                                          boolean ignoreSuppressed,
                                          @NotNull final ProgressIndicator indicator) {
    if (ignoreSuppressed && InspectionManagerEx.inspectionResultSuppressed(descriptor.getPsiElement(), tool)) {
      return;
    }

    infosToAdd.offer(Pair.create(descriptor, tool));
    if (haveInfosToProcess.getAndSet(true)) return;
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    // extra invoke later is harmless, missing invoke is not
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
        InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
        List<HighlightInfo> infos = new ArrayList<HighlightInfo>(2);
        while (haveInfosToProcess.compareAndSet(true, false)) {
          for (Pair<ProblemDescriptor, LocalInspectionTool> pair = infosToAdd.poll(); pair != null; pair = infosToAdd.poll()) {
            if (indicator.isCanceled()) {
              infosToAdd.clear();
              return;
            }

            ProblemDescriptor descriptor = pair.first;
            LocalInspectionTool tool = pair.second;
            PsiElement psiElement = descriptor.getPsiElement();
            if (psiElement == null) continue;
            PsiFile file = psiElement.getContainingFile();
            Document thisDocument = documentManager.getDocument(file);

            HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), file).getSeverity();

            infos.clear();
            createHighlightsForDescriptor(infos, emptyActionRegistered, ilManager, file, thisDocument, tool, severity, descriptor);
            for (HighlightInfo info : infos) {
              UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, myDocument, myFile, myStartOffset, myEndOffset, info, getId());
            }
          }
        }
      }
    });
  }

  private void appendDescriptors(PsiFile file, List<ProblemDescriptor> descriptors, LocalInspectionTool tool) {
    InspectionResult res = new InspectionResult(tool, descriptors);
    appendResult(file, res);
  }

  private void appendResult(PsiFile file, InspectionResult res) {
    List<InspectionResult> resultList = result.get(file);
    if (resultList == null) {
      resultList = ConcurrencyUtil.cacheOrGet(result, file, new ArrayList<InspectionResult>());
    }
    synchronized (resultList) {
      resultList.add(res);
    }
  }

  @NotNull
  private HighlightInfoType highlightTypeFromDescriptor(final ProblemDescriptor problemDescriptor, final HighlightSeverity severity) {
    final ProblemHighlightType highlightType = problemDescriptor.getHighlightType();
    switch (highlightType) {
      case GENERIC_ERROR_OR_WARNING:
        return mySeverityRegistrar.getHighlightInfoTypeBySeverity(severity);
      case LIKE_DEPRECATED:
        return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.DEPRECATED.getAttributesKey());
      case LIKE_UNKNOWN_SYMBOL:
        if (severity == HighlightSeverity.ERROR) {
          return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.WRONG_REF.getAttributesKey());
        }
        else if (severity == HighlightSeverity.WARNING) {
          return new HighlightInfoType.HighlightInfoTypeImpl(severity, CodeInsightColors.INFO_ATTRIBUTES);
        }
        else {
          return mySeverityRegistrar.getHighlightInfoTypeBySeverity(severity);
        }
      case LIKE_UNUSED_SYMBOL:
        return new HighlightInfoType.HighlightInfoTypeImpl(severity, HighlightInfoType.UNUSED_SYMBOL.getAttributesKey());
      case INFO:
        return HighlightInfoType.INFO;
      case ERROR:
        return HighlightInfoType.WRONG_REF;
      case GENERIC_ERROR:
        return HighlightInfoType.ERROR;
      case INFORMATION:
        final TextAttributesKey attributes = ((ProblemDescriptorImpl)problemDescriptor).getEnforcedTextAttributes();
        if (attributes != null) {
          return new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, attributes);
        }
        return HighlightInfoType.INFORMATION;
    }
    throw new RuntimeException("Cannot map " + highlightType);
  }

  protected void applyInformationWithProgress() {
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myInfos, getId());
  }

  private void addHighlightsFromResults(final List<HighlightInfo> outInfos) {
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    Set<TextRange> emptyActionRegistered = new THashSet<TextRange>();

    for (Map.Entry<PsiFile, List<InspectionResult>> entry : result.entrySet()) {
      PsiFile file = entry.getKey();
      Document documentRange = documentManager.getDocument(file);
      if (documentRange == null) continue;
      List<InspectionResult> resultList = entry.getValue();
      for (InspectionResult inspectionResult : resultList) {
        LocalInspectionTool tool = inspectionResult.tool;
        HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), file).getSeverity();
        for (ProblemDescriptor descriptor : inspectionResult.foundProblems) {
          createHighlightsForDescriptor(outInfos, emptyActionRegistered, ilManager, file, documentRange, tool, severity, descriptor);
        }
      }
    }
  }

  private void createHighlightsForDescriptor(List<HighlightInfo> outInfos,
                                             Set<TextRange> emptyActionRegistered,
                                             InjectedLanguageManager ilManager,
                                             PsiFile file,
                                             Document documentRange,
                                             LocalInspectionTool tool,
                                             HighlightSeverity severity,
                                             ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (InspectionManagerEx.inspectionResultSuppressed(psiElement, tool)) return;
    HighlightInfoType level = highlightTypeFromDescriptor(descriptor, severity);
    HighlightInfo info = createHighlightInfo(descriptor, tool, level, emptyActionRegistered);
    if (info == null) return;

    if (file == myFile) {
      // not injected
      outInfos.add(info);
      return;
    }
    // todo we got to separate our "internal" prefixes/suffixes from user-defined ones
    // todo in the latter case the erors should be highlighted, otherwise not
    List<TextRange> editables = ilManager.intersectWithAllEditableFragments(file, new TextRange(info.startOffset, info.endOffset));
    for (TextRange editable : editables) {
      TextRange hostRange = ((DocumentWindow)documentRange).injectedToHost(editable);
      HighlightInfo patched = HighlightInfo.createHighlightInfo(info.type, psiElement, hostRange.getStartOffset(), hostRange.getEndOffset(), info.description, info.toolTip);
      if (patched != null) {
        registerQuickFixes(tool, descriptor, patched, emptyActionRegistered);
        outInfos.add(patched);
      }
    }
  }

  @Nullable
  private HighlightInfo createHighlightInfo(final ProblemDescriptor descriptor,
                                            final LocalInspectionTool tool,
                                            final HighlightInfoType level,
                                            final Set<TextRange> emptyActionRegistered) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return null;
    @NonNls String message = ProblemDescriptionNode.renderDescriptionMessage(descriptor);

    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    final InspectionProfile inspectionProfile = myProfileWrapper.getInspectionProfile();
    if (!inspectionProfile.isToolEnabled(key, myFile)) return null;

    HighlightInfoType type = new HighlightInfoType.HighlightInfoTypeImpl(level.getSeverity(psiElement), level.getAttributesKey());
    final String plainMessage = message.startsWith("<html>") ? StringUtil.unescapeXml(message.replaceAll("<[^>]*>", "")) : message;
    @NonNls final String link = "<a href=\"#inspection/" + tool.getShortName() + "\"> " + DaemonBundle.message("inspection.extended.description") +
                                "</a>" + myShortcutText;

    @NonNls String tooltip = null;
    if (descriptor.showTooltip()) {
      if (message.startsWith("<html>")) {
        tooltip = message.contains("</body>") ? message.replace("</body>", link + "</body>") : message.replace("</html>", link + "</html>");
      }
      else {
        tooltip = "<html><body>" + XmlStringUtil.escapeString(message) + link + "</body></html>";
      }
    }
    HighlightInfo highlightInfo = highlightInfoFromDescriptor(descriptor, type, plainMessage, tooltip);
    registerQuickFixes(tool, descriptor, highlightInfo, emptyActionRegistered);
    return highlightInfo;
  }

  private static void registerQuickFixes(final LocalInspectionTool tool, final ProblemDescriptor descriptor,
                                         final HighlightInfo highlightInfo, final Set<TextRange> emptyActionRegistered) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    boolean needEmptyAction = true;
    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length > 0) {
      for (int k = 0; k < fixes.length; k++) {
        if (fixes[k] != null) { // prevent null fixes from var args
          QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixWrapper.wrap(descriptor, k), key);
          needEmptyAction = false;
        }
      }
    }
    HintAction hintAction = ((ProblemDescriptorImpl)descriptor).getHintAction();
    if (hintAction != null) {
      QuickFixAction.registerQuickFixAction(highlightInfo, hintAction, key);
      needEmptyAction = false;
    }
    if (((ProblemDescriptorImpl)descriptor).getEnforcedTextAttributes() != null) {
      needEmptyAction = false;      
    }
    if (needEmptyAction && emptyActionRegistered.add(new TextRange(highlightInfo.fixStartOffset, highlightInfo.fixEndOffset))) {
      EmptyIntentionAction emptyIntentionAction = new EmptyIntentionAction(tool.getDisplayName());
      QuickFixAction.registerQuickFixAction(highlightInfo, emptyIntentionAction, key);
    }
  }

  public static PsiElement[] getElementsIntersectingRange(PsiFile file, final int startOffset, final int endOffset) {
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<PsiElement> result = new LinkedHashSet<PsiElement>();
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile psiRoot = viewProvider.getPsi(language);
      if (HighlightLevelUtil.shouldInspect(psiRoot)) {
        result.addAll(CollectHighlightsUtil.getElementsInRange(psiRoot, startOffset, endOffset, true));
      }
    }
    return result.toArray(new PsiElement[result.size()]);
  }

  List<LocalInspectionTool> getInspectionTools(InspectionProfileWrapper profile) {
    return profile.getHighlightingLocalInspectionTools(myFile);
  }

  private void doInspectInjectedPsi(@NotNull PsiFile injectedPsi,
                                    @NotNull List<LocalInspectionTool> tools,
                                    final boolean isOnTheFly,
                                    final boolean ignoreSuppressed,
                                    final ProgressIndicator indicator,
                                    LocalInspectionToolSession session) {
    InspectionManager iManager  = InspectionManager.getInstance(injectedPsi.getProject());
    final PsiElement host = injectedPsi.getContext();

    final PsiElement[] elements = getElementsIntersectingRange(injectedPsi, 0, injectedPsi.getTextLength());
    if (elements.length == 0) {
      return;
    }
    for (final LocalInspectionTool tool : tools) {
      indicator.checkCanceled();
      if (host != null && InspectionManagerEx.inspectionResultSuppressed(host, tool)) {
        continue;
      }
      ProblemsHolder holder = new ProblemsHolder(iManager, injectedPsi, isOnTheFly) {
        @Override
        public void registerProblem(@NotNull ProblemDescriptor descriptor) {
          super.registerProblem(descriptor);
          if (isOnTheFly) {
            addDescriptorIncrementally(descriptor, tool, ignoreSuppressed, indicator);
          }
        }
      };

      final PsiElementVisitor visitor = tool.buildVisitor(holder, true, session);
      assert !(visitor instanceof PsiRecursiveElementVisitor) : "The visitor returned from LocalInspectionTool.buildVisitor() must not be recursive. "+tool;
      for (PsiElement element : elements) {
        element.accept(visitor);
      }
      List<ProblemDescriptor> problems = holder.getResults();
      if (problems != null && !problems.isEmpty()) {
        InspectionResult res = new InspectionResult(tool, problems);
        appendResult(injectedPsi, res);
      }
    }
  }

  public List<HighlightInfo> getInfos() {
    return myInfos;
  }

  private static class InspectionResult {
    public final LocalInspectionTool tool;
    public final List<ProblemDescriptor> foundProblems;

    private InspectionResult(final LocalInspectionTool tool, final List<ProblemDescriptor> foundProblems) {
      this.tool = tool;
      this.foundProblems = foundProblems;
    }
  }
}
