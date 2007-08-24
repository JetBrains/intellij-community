package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobScheduler;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.injected.DocumentRange;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedPsiInspectionUtil;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlStringUtil;
import com.intellij.xml.util.XmlUtil;
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
public class LocalInspectionsPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LocalInspectionsPass");
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  @NotNull private List<ProblemDescriptor> myDescriptors = Collections.emptyList();
  @NotNull private List<HighlightInfoType> myLevels = Collections.emptyList();
  @NotNull private List<LocalInspectionTool> myTools = Collections.emptyList();
  @NotNull private List<InjectedPsiInspectionUtil.InjectedPsiInspectionResult> myInjectedPsiInspectionResults = Collections.emptyList();
  static final String PRESENTABLE_NAME = DaemonBundle.message("pass.inspection");
  private List<HighlightInfo> myInfos = Collections.emptyList();
  static final Icon IN_PROGRESS_ICON = IconLoader.getIcon("/general/inspectionInProgress.png");

  public LocalInspectionsPass(@NotNull PsiFile file, @Nullable Document document, int startOffset, int endOffset) {
    super(file.getProject(), document, IN_PROGRESS_ICON, PRESENTABLE_NAME);
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    setId(Pass.LOCAL_INSPECTIONS);
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    myDescriptors = new ArrayList<ProblemDescriptor>();
    myLevels = new ArrayList<HighlightInfoType>();
    myTools = new ArrayList<LocalInspectionTool>();
    inspectRoot(progress);
  }

  private void inspectRoot(final ProgressIndicator progress) {
    if (!shouldInspect()) return;
    final InspectionManagerEx iManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    final InspectionProfileWrapper profile = InspectionProjectProfileManager.getInstance(myProject).getProfileWrapper(myFile);
    final LocalInspectionTool[] tools = profile.getHighlightingLocalInspectionTools();

    inspect(tools, progress, iManager, true);
  }

  protected boolean shouldInspect() {
    return HighlightUtil.shouldInspect(myFile);
  }

  public void doInspectInBatch(final InspectionManagerEx iManager, InspectionTool[] toolWrappers, final ProgressIndicator progress) {
    myDescriptors = new ArrayList<ProblemDescriptor>();
    myLevels = new ArrayList<HighlightInfoType>();
    myTools = new ArrayList<LocalInspectionTool>();
    
    Map<LocalInspectionTool, LocalInspectionToolWrapper> tool2Wrapper = new THashMap<LocalInspectionTool, LocalInspectionToolWrapper>(toolWrappers.length);
    for (InspectionTool toolWrapper : toolWrappers) {
      tool2Wrapper.put(((LocalInspectionToolWrapper)toolWrapper).getTool(), (LocalInspectionToolWrapper)toolWrapper);
    }
    LocalInspectionTool[] tools = tool2Wrapper.keySet().toArray(new LocalInspectionTool[tool2Wrapper.size()]);
    inspect(tools, progress, iManager, false);
    for (int i = 0; i < myTools.size(); i++) {
      final LocalInspectionTool tool = myTools.get(i);
      ProblemDescriptor descriptor = myDescriptors.get(i);
      LocalInspectionToolWrapper toolWrapper = tool2Wrapper.get(tool);

      toolWrapper.addProblemDescriptors(Collections.singletonList(descriptor), true);
    }
  }

  public void inspect(final LocalInspectionTool[] tools, final ProgressIndicator progress, final InspectionManagerEx iManager, final boolean isOnTheFly) {
    final PsiElement[] elements = getElementsIntersectingRange(myFile, myStartOffset, myEndOffset);

    final Job<?> job = JobScheduler.getInstance().createJob("Inspection tools", Job.DEFAULT_PRIORITY); // TODO: Better name, handle priority

    for (final LocalInspectionTool tool : tools) {
      job.addTask(new Runnable() {
        public void run() {
          if (progress != null) {
            if (progress.isCanceled()) {
              job.cancel();
              return;
            }
          }

          final ProgressManager progressManager = ProgressManager.getInstance();
          ((ProgressManagerImpl)progressManager).executeProcessUnderProgress(new Runnable(){
            public void run() {
              ApplicationManager.getApplication().assertReadAccessAllowed();

              ProblemsHolder holder = new ProblemsHolder(iManager);
              try {
                progressManager.checkCanceled();
                PsiElementVisitor elementVisitor = tool.buildVisitor(holder, isOnTheFly);
                if(elementVisitor == null) {
                  LOG.error("Tool " + tool + " must not return null from the buildVisitor() method");
                }
                for (PsiElement element : elements) {
                  progressManager.checkCanceled();
                  element.accept(elementVisitor);
                }
                advanceProgress(elements.length);

                if (holder.hasResults()) {
                  appendDescriptors(holder.getResults(), tool);
                }
              }
              catch (ProcessCanceledException e) {
                job.cancel();
              }
            }
          },progress);
        }
      });
    }

    setProgressLimit(1L * tools.length * elements.length);

    try {
      job.scheduleAndWaitForResults();
      if (job.isCanceled()) return;
    }
    catch (ProcessCanceledException e) {
      return;
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    inspectInjectedPsi(elements);

    myInfos = new ArrayList<HighlightInfo>(myDescriptors.size());
    addHighlightsFromDescriptors(myInfos);
    addHighlightsFromInjectedPsiProblems(myInfos);
  }

  private void inspectInjectedPsi(final PsiElement[] elements) {
    myInjectedPsiInspectionResults = new SmartList<InjectedPsiInspectionUtil.InjectedPsiInspectionResult>();
    for (PsiElement element : elements) {
      if (element instanceof PsiLanguageInjectionHost) {
        final PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)element;
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            myInjectedPsiInspectionResults.addAll(InjectedPsiInspectionUtil.inspectInjectedPsi(host));
          }
        });
      }
    }
  }

  //for tests only
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
    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(highlightInfoType, element, textRange.getStartOffset(), textRange.getEndOffset(), message, toolTip);
    highlightInfo.isAfterEndOfLine = problemDescriptor.isAfterEndOfLine();

    if (element instanceof PsiFile && textRange.equals(element.getTextRange())) {
      highlightInfo.isFileLevelAnnotation = true;
    }

    return highlightInfo;
  }

  private synchronized void appendDescriptors(List<ProblemDescriptor> problemDescriptors, LocalInspectionTool tool) {
    if (problemDescriptors == null) return;
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(myFile);
    final HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName())).getSeverity();
    ProgressManager progressManager = ProgressManager.getInstance();
    for (ProblemDescriptor problemDescriptor : problemDescriptors) {
      progressManager.checkCanceled();
      if (!InspectionManagerEx.inspectionResultSuppressed(problemDescriptor.getPsiElement(), tool)) {
        myDescriptors.add(problemDescriptor);
        HighlightInfoType type = highlightTypeFromDescriptor(problemDescriptor, severity);
        myLevels.add(type);
        myTools.add(tool);
      }
    }
  }

  @Nullable
  private HighlightInfoType highlightTypeFromDescriptor(final ProblemDescriptor problemDescriptor, final HighlightSeverity severity) {
    ProblemHighlightType highlightType = problemDescriptor.getHighlightType();
    HighlightInfoType type = null;
    if (highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING || highlightType == ProblemHighlightType.J2EE_PROBLEM) {
      type = SeverityRegistrar.getInstance(myProject).getHighlightInfoTypeBySeverity(severity);
    }
    else if (highlightType == ProblemHighlightType.LIKE_DEPRECATED) {
      type = HighlightInfoType.DEPRECATED;
    }
    else if (highlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) {
      if (severity == HighlightSeverity.ERROR) {
        type = HighlightInfoType.WRONG_REF;
      }
      else {
        type = SeverityRegistrar.getInstance(myProject).getHighlightInfoTypeBySeverity(severity);
      }
    }
    else if (highlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
      type = HighlightInfoType.UNUSED_SYMBOL;
    }
    return type;
  }

  protected void applyInformationWithProgress() {
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myInfos, Pass.LOCAL_INSPECTIONS);
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
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(myFile);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myInjectedPsiInspectionResults.size(); i++) {
      InjectedPsiInspectionUtil.InjectedPsiInspectionResult result = myInjectedPsiInspectionResults.get(i);
      LocalInspectionTool tool = result.tool;
      HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName())).getSeverity();

      PsiElement injectedPsi = result.injectedPsi;
      DocumentRange documentRange = (DocumentRange)documentManager.getDocument((PsiFile)injectedPsi);
      if (documentRange == null) continue;
      //noinspection ForLoopReplaceableByForEach
      for (int j = 0; j < result.foundProblems.size(); j++) {
        ProblemDescriptor descriptor = result.foundProblems.get(j);
        PsiElement psiElement = descriptor.getPsiElement();
        if (InspectionManagerEx.inspectionResultSuppressed(psiElement, tool)) continue;
        HighlightInfoType level = highlightTypeFromDescriptor(descriptor, severity);
        HighlightInfo info = createHighlightInfo(descriptor, tool, level,emptyActionRegistered);
        if (info == null) continue;
        TextRange editable = documentRange.intersectWithEditable(new TextRange(info.startOffset, info.endOffset));
        if (editable == null) continue;
        HighlightInfo patched = HighlightInfo.createHighlightInfo(info.type, psiElement, documentRange.injectedToHost(editable.getStartOffset()), documentRange.injectedToHost(editable.getEndOffset()), info.description, info.toolTip);
        if (patched != null) {
          registerQuickFixes(tool, descriptor, patched,emptyActionRegistered);
          infos.add(patched);
        }
      }
    }
  }

  @Nullable
  private HighlightInfo createHighlightInfo(final ProblemDescriptor descriptor, final LocalInspectionTool tool, final HighlightInfoType level,
                                            final Set<TextRange> emptyActionRegistered) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return null;
    @NonNls String message = renderDescriptionMessage(descriptor);

    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(myFile);
    if (!inspectionProfile.isToolEnabled(key)) return null;

    HighlightInfoType type = new HighlightInfoType.HighlightInfoTypeImpl(inspectionProfile.getErrorLevel(key).getSeverity(), level.getAttributesKey());
    final String plainMessage = message.startsWith("<html>") ? XmlUtil.unescape(message.replaceAll("<[^>]*>", "")) : message;
    @NonNls final String link = "<a href=\"#inspection/" + tool.getShortName() + "\"> " + DaemonBundle.message("inspection.extended.description") + "</a>";
    @NonNls String tooltip;
    if (message.startsWith("<html>")) {
      tooltip = message.contains("</body>") ? message.replace("</body>", link + "</body>") : message.replace("</html>", link + "</html>");
    }
    else {
      tooltip = "<html><body>" + XmlStringUtil.escapeString(message) + link + "</body></html>";
    }
    HighlightInfo highlightInfo = highlightInfoFromDescriptor(descriptor, type, plainMessage, tooltip);
    registerQuickFixes(tool, descriptor, highlightInfo, emptyActionRegistered);
    return highlightInfo;
  }

  private static void registerQuickFixes(final LocalInspectionTool tool, final ProblemDescriptor descriptor,
                                         final HighlightInfo highlightInfo, final Set<TextRange> emptyActionRegistered) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length > 0) {
      for (int k = 0; k < fixes.length; k++) {
        if (fixes[k] != null) { // prevent null fixes from var args
          QuickFixAction.registerQuickFixAction(highlightInfo, new QuickFixWrapper(descriptor, k), key);
        }
      }
    }
    else if (emptyActionRegistered.add(new TextRange(highlightInfo.fixStartOffset, highlightInfo.fixEndOffset))) {
      EmptyIntentionAction emptyIntentionAction = new EmptyIntentionAction(tool.getDisplayName());
      QuickFixAction.registerQuickFixAction(highlightInfo, emptyIntentionAction, key);
    }
  }

  private static String renderDescriptionMessage(ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    String message = descriptor.getDescriptionTemplate();

    // no message. Should not be the case if inspection correctly implemented.
    // noinspection ConstantConditions
    if (message == null) return "";

    message = StringUtil.replace(message, "<code>", "'");
    message = StringUtil.replace(message, "</code>", "'");
    //message = message.replaceAll("<[^>]*>", "");
    String text = psiElement == null ? "" : psiElement.getText();
    message = StringUtil.replace(message, "#ref", text);
    message = StringUtil.replace(message, "#loc", "");

    message = XmlUtil.unescape(message).trim();
    return message;
  }

  public static PsiElement[] getElementsIntersectingRange(PsiFile file, final int startOffset, final int endOffset) {
    final FileViewProvider viewProvider = file.getViewProvider();
    final Set<PsiElement> result = new LinkedHashSet<PsiElement>();
    for (Language language : viewProvider.getPrimaryLanguages()) {
      final PsiFile psiRoot = viewProvider.getPsi(language);
      if (HighlightUtil.shouldInspect(psiRoot)) {
        ApplicationManager.getApplication().runReadAction(new Runnable(){
          public void run() {
            result.addAll(CodeInsightUtil.getElementsInRange(psiRoot, startOffset, endOffset, true));
          }
        });
      }
    }
    return result.toArray(new PsiElement[result.size()]);
  }
}
