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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public class LocalInspectionsPass extends ProgressableTextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LocalInspectionsPass");
  private final int myStartOffset;
  private final int myEndOffset;
  @NotNull private List<ProblemDescriptor> myDescriptors = Collections.emptyList();
  @NotNull private List<HighlightInfoType> myLevels = Collections.emptyList();
  @NotNull private List<LocalInspectionTool> myTools = Collections.emptyList();
  @NotNull private List<InjectedPsiInspectionResult> myInjectedPsiInspectionResults = Collections.emptyList();
  static final String PRESENTABLE_NAME = DaemonBundle.message("pass.inspection");
  private volatile List<HighlightInfo> myInfos = Collections.emptyList();
  static final Icon IN_PROGRESS_ICON = IconLoader.getIcon("/general/inspectionInProgress.png");
  private final String myShortcutText;
  private final SeverityRegistrar mySeverityRegistrar;
  private boolean myFailFastOnAcquireReadAction;

  public LocalInspectionsPass(@NotNull PsiFile file, @Nullable Document document, int startOffset, int endOffset) {
    super(file.getProject(), document, IN_PROGRESS_ICON, PRESENTABLE_NAME, file, true);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
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
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    myDescriptors = new ArrayList<ProblemDescriptor>();
    myLevels = new ArrayList<HighlightInfoType>();
    myTools = new ArrayList<LocalInspectionTool>();
    if (!HighlightLevelUtil.shouldInspect(myFile)) return;
    final InspectionManagerEx iManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    final InspectionProfileWrapper profile = InspectionProjectProfileManager.getInstance(myProject).getProfileWrapper();
    final List<LocalInspectionTool> tools = DumbService.getInstance(myProject).filterByDumbAwareness(getInspectionTools(profile));

    inspect(tools, iManager, true, true, true);
  }

  public void doInspectInBatch(final InspectionManagerEx iManager, List<InspectionProfileEntry> toolWrappers, boolean ignoreSuppressed) {
    myDescriptors = new ArrayList<ProblemDescriptor>();
    myLevels = new ArrayList<HighlightInfoType>();
    myTools = new ArrayList<LocalInspectionTool>();

    Map<LocalInspectionTool, LocalInspectionToolWrapper> tool2Wrapper = new THashMap<LocalInspectionTool, LocalInspectionToolWrapper>(toolWrappers.size());
    for (InspectionProfileEntry toolWrapper : toolWrappers) {
      tool2Wrapper.put(((LocalInspectionToolWrapper)toolWrapper).getTool(), (LocalInspectionToolWrapper)toolWrapper);
    }
    List<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>(tool2Wrapper.keySet());
    inspect(tools, iManager, false, ignoreSuppressed, false);
    addDescriptorsFromInjectedResults(tool2Wrapper, iManager);
    for (int i = 0; i < myTools.size(); i++) {
      final LocalInspectionTool tool = myTools.get(i);
      ProblemDescriptor descriptor = myDescriptors.get(i);
      LocalInspectionToolWrapper toolWrapper = tool2Wrapper.get(tool);

      toolWrapper.addProblemDescriptors(Collections.singletonList(descriptor), true);
    }
  }

  private void addDescriptorsFromInjectedResults(Map<LocalInspectionTool, LocalInspectionToolWrapper> tool2Wrapper,
                                                 InspectionManagerEx iManager) {
    Set<TextRange> emptyActionRegistered = new THashSet<TextRange>();
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myInjectedPsiInspectionResults.size(); i++) {
      InjectedPsiInspectionResult result = myInjectedPsiInspectionResults.get(i);
      LocalInspectionTool tool = result.tool;
      HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), myFile).getSeverity();

      PsiElement injectedPsi = result.injectedPsi;
      DocumentWindow documentRange = (DocumentWindow)documentManager.getDocument((PsiFile)injectedPsi);
      if (documentRange == null) continue;
      //noinspection ForLoopReplaceableByForEach
      for (int j = 0; j < result.foundProblems.size(); j++) {
        ProblemDescriptor descriptor = result.foundProblems.get(j);
        PsiElement psiElement = descriptor.getPsiElement();
        if (InspectionManagerEx.inspectionResultSuppressed(psiElement, tool)) continue;
        HighlightInfoType level = highlightTypeFromDescriptor(descriptor, severity);
        HighlightInfo info = createHighlightInfo(descriptor, tool, level,emptyActionRegistered);
        if (info == null) continue;
        List<TextRange> editables = ilManager.intersectWithAllEditableFragments((PsiFile)injectedPsi, new TextRange(info.startOffset, info.endOffset));
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

  private void inspect(final List<LocalInspectionTool> tools,
                       final InspectionManagerEx iManager,
                       final boolean isOnTheFly,
                       final boolean ignoreSuppressed,
                       boolean failFastOnAcquireReadAction) {
    myFailFastOnAcquireReadAction = failFastOnAcquireReadAction;
    if (tools.isEmpty()) return;
    final PsiElement[] elements = getElementsIntersectingRange(myFile, myStartOffset, myEndOffset);

    setProgressLimit(1L * tools.size() * elements.length);
    final LocalInspectionToolSession session = new LocalInspectionToolSession(myFile, myStartOffset, myEndOffset);
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    LOG.assertTrue(indicator != null);

    boolean result = JobUtil.invokeConcurrentlyUnderMyProgress(tools, new Processor<LocalInspectionTool>() {
      public boolean process(final LocalInspectionTool tool) {
        final ProgressManager progressManager = ProgressManager.getInstance();
        indicator.checkCanceled();
        ProgressIndicator localIndicator = progressManager.getProgressIndicator();

        ProgressIndicator original = ((ProgressWrapper)localIndicator).getOriginalProgressIndicator();
        LOG.assertTrue(original == indicator, original);

        ApplicationManager.getApplication().assertReadAccessAllowed();

        ProblemsHolder holder = new ProblemsHolder(iManager, myFile, isOnTheFly);
        PsiElementVisitor elementVisitor = tool.buildVisitor(holder, isOnTheFly);
        //noinspection ConstantConditions
        if(elementVisitor == null) {
          LOG.error("Tool " + tool + " must not return null from the buildVisitor() method");
        }
        tool.inspectionStarted(session);
        for (PsiElement element : elements) {
          indicator.checkCanceled();
          element.accept(elementVisitor);
        }
        tool.inspectionFinished(session);
        advanceProgress(elements.length);

        if (holder.hasResults()) {
          appendDescriptors(holder.getResults(), tool, ignoreSuppressed, indicator);
        }
        return true;
      }
    }, myFailFastOnAcquireReadAction);
    if (!result) throw new ProcessCanceledException();

    indicator.checkCanceled();
    inspectInjectedPsi(elements, tools);

    myInfos = new ArrayList<HighlightInfo>(myDescriptors.size());
    addHighlightsFromDescriptors(myInfos);
    addHighlightsFromInjectedPsiProblems(myInfos);
  }

  void inspectInjectedPsi(final PsiElement[] elements, final List<LocalInspectionTool> tools) {
    myInjectedPsiInspectionResults = ContainerUtil.createEmptyCOWList();
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
        inspectInjectedPsi(injectedPsi, myInjectedPsiInspectionResults, tools);
        return true;
      }
    }, myFailFastOnAcquireReadAction)) throw new ProcessCanceledException();
  }

  public Collection<HighlightInfo> getHighlights() {
    ArrayList<HighlightInfo> highlights = new ArrayList<HighlightInfo>(myDescriptors.size());
    addHighlightsFromDescriptors(highlights);
    addHighlightsFromInjectedPsiProblems(highlights);
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

  private synchronized void appendDescriptors(@NotNull List<ProblemDescriptor> problemDescriptors,
                                              @NotNull LocalInspectionTool tool,
                                              boolean ignoreSuppressed,
                                              @NotNull ProgressIndicator progress) {
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    final HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), myFile).getSeverity();
    for (ProblemDescriptor problemDescriptor : problemDescriptors) {
      progress.checkCanceled();
      if (!(ignoreSuppressed && InspectionManagerEx.inspectionResultSuppressed(problemDescriptor.getPsiElement(), tool))) {
        myDescriptors.add(problemDescriptor);
        HighlightInfoType type = highlightTypeFromDescriptor(problemDescriptor, severity);
        myLevels.add(type);
        myTools.add(tool);
      }
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

  private void addHighlightsFromDescriptors(final List<HighlightInfo> toInfos) {
    Set<TextRange> emptyActionRegistered = new THashSet<TextRange>();
    for (int i = 0; i < myDescriptors.size(); i++) {
      ProblemDescriptor descriptor = myDescriptors.get(i);
      LocalInspectionTool tool = myTools.get(i);
      final HighlightInfoType level = myLevels.get(i);
      HighlightInfo highlightInfo = createHighlightInfo(descriptor, tool, level, emptyActionRegistered);
      if (highlightInfo != null) {
        toInfos.add(highlightInfo);
      }
    }
  }

  private void addHighlightsFromInjectedPsiProblems(final List<HighlightInfo> infos) {
    Set<TextRange> emptyActionRegistered = new THashSet<TextRange>();
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    InjectedLanguageManager ilManager = InjectedLanguageManager.getInstance(myProject);
    for (InjectedPsiInspectionResult result : myInjectedPsiInspectionResults) {
      LocalInspectionTool tool = result.tool;
      HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName()), myFile).getSeverity();

      PsiElement injectedPsi = result.injectedPsi;
      DocumentWindow documentRange = (DocumentWindow)documentManager.getDocument((PsiFile)injectedPsi);
      if (documentRange == null) continue;
      //noinspection ForLoopReplaceableByForEach
      for (int j = 0; j < result.foundProblems.size(); j++) {
        ProblemDescriptor descriptor = result.foundProblems.get(j);
        PsiElement psiElement = descriptor.getPsiElement();
        if (InspectionManagerEx.inspectionResultSuppressed(psiElement, tool)) continue;
        HighlightInfoType level = highlightTypeFromDescriptor(descriptor, severity);
        HighlightInfo info = createHighlightInfo(descriptor, tool, level, emptyActionRegistered);
        if (info == null) continue;

        // todo we got to separate our "internal" prefixes/suffixes from user-defined ones
        // todo in the latter case the erors should be highlighted, otherwise not
        List<TextRange> editables =
            ilManager.intersectWithAllEditableFragments((PsiFile)injectedPsi, new TextRange(info.startOffset, info.endOffset));
        for (TextRange editable : editables) {
          TextRange hostRange = documentRange.injectedToHost(editable);
          HighlightInfo patched = HighlightInfo.createHighlightInfo(info.type, psiElement, hostRange.getStartOffset(), hostRange.getEndOffset(), info.description, info.toolTip);
          if (patched != null) {
            registerQuickFixes(tool, descriptor, patched, emptyActionRegistered);
            infos.add(patched);
          }
        }
      }
    }
  }

  @Nullable
  private HighlightInfo createHighlightInfo(final ProblemDescriptor descriptor, final LocalInspectionTool tool, final HighlightInfoType level,
                                            final Set<TextRange> emptyActionRegistered) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return null;
    @NonNls String message = ProblemDescriptionNode.renderDescriptionMessage(descriptor);

    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
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

  private static void inspectInjectedPsi(PsiFile injectedPsi, List<InjectedPsiInspectionResult> result, List<LocalInspectionTool> tools) {
    InspectionManager inspectionManager = InspectionManager.getInstance(injectedPsi.getProject());
    final ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, injectedPsi, true);
    final PsiElement host = injectedPsi.getContext();

    final PsiElement[] elements = getElementsIntersectingRange(injectedPsi, 0, injectedPsi.getTextLength());
    if (elements.length != 0) {
      for (LocalInspectionTool tool : tools) {
        if (host != null && InspectionManagerEx.inspectionResultSuppressed(host, tool)) {
            continue;
        }
        final PsiElementVisitor visitor = tool.buildVisitor(problemsHolder, true);
        assert !(visitor instanceof PsiRecursiveElementVisitor) : "The visitor returned from LocalInspectionTool.buildVisitor() must not be recursive. "+tool;
        for (PsiElement element : elements) {
          element.accept(visitor);
        }
        List<ProblemDescriptor> problems = problemsHolder.getResults();
        if (problems != null && !problems.isEmpty()) {
          InjectedPsiInspectionResult res = new InjectedPsiInspectionResult(tool, injectedPsi, new SmartList<ProblemDescriptor>(problems));
          result.add(res);
        }
      }
    }
  }

  public List<HighlightInfo> getInfos() {
    return myInfos;
  }

  private static class InjectedPsiInspectionResult {
    public final LocalInspectionTool tool;
    public final PsiElement injectedPsi;
    public final List<ProblemDescriptor> foundProblems;

    private InjectedPsiInspectionResult(final LocalInspectionTool tool, final PsiElement injectedPsi, final List<ProblemDescriptor> foundProblems) {
      this.tool = tool;
      this.injectedPsi = injectedPsi;
      this.foundProblems = foundProblems;
    }
  }
}
