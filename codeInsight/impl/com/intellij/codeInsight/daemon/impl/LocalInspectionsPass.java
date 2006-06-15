package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedPsiInspectionUtil;
import com.intellij.xml.util.XmlUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author max
 */
public class LocalInspectionsPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LocalInspectionsPass");

  private Project myProject;
  private PsiFile myFile;
  private Document myDocument;
  private int myStartOffset;
  private int myEndOffset;
  @NotNull private List<ProblemDescriptor> myDescriptors = Collections.emptyList();
  @NotNull private List<HighlightInfoType> myLevels = Collections.emptyList();
  @NotNull private List<LocalInspectionTool> myTools = Collections.emptyList();
  @NotNull private List<InjectedPsiInspectionUtil.InjectedPsiInspectionResult> myInjectedPsiInspectionResults = Collections.emptyList();

  public LocalInspectionsPass(Project project,
                              PsiFile file,
                              Document document,
                              int startOffset,
                              int endOffset) {
    super(document);
    myProject = project;
    myFile = file;
    myDocument = document;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    final InspectionManagerEx iManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);

    myDescriptors = new ArrayList<ProblemDescriptor>();
    myLevels = new ArrayList<HighlightInfoType>();
    myTools = new ArrayList<LocalInspectionTool>();
    final FileViewProvider fileViewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = fileViewProvider.getRelevantLanguages();
    for (Language language : relevantLanguages) {
      final PsiFile psiRoot = fileViewProvider.getPsi(language);
      if (HighlightUtil.shouldInspect(psiRoot)) {
        inspectRoot(psiRoot, iManager);
      }
    }
  }

  private void inspectRoot(final PsiElement psiRoot, final InspectionManagerEx iManager) {
    final TextRange targetRange = new TextRange(myStartOffset, myEndOffset);
    final Set<PsiElement> workSet = new THashSet<PsiElement>();
    psiRoot.accept(new PsiRecursiveElementVisitor() {
      public void visitMethod(PsiMethod method) {
        processTarget(method);
      }

      public void visitClass(PsiClass aClass) {
        processTarget(aClass);
      }

      public void visitField(PsiField field) {
        processTarget(field);
      }

      private void processTarget(PsiMember member) {
        final TextRange range = member.getTextRange();
        if (targetRange.intersects(range)) {
          workSet.add(member);
          member.acceptChildren(this);
        }
      }
    });

    workSet.add(psiRoot);

    final InspectionProfileWrapper profile = InspectionProjectProfileManager.getInstance(myProject).getProfileWrapper(myFile);
    final LocalInspectionTool[] tools = profile.getHighlightingLocalInspectionTools();

    final ProblemsHolder holder = new ProblemsHolder(iManager);
    final List<Pair<LocalInspectionTool, PsiElementVisitor>> visitors = new ArrayList<Pair<LocalInspectionTool, PsiElementVisitor>>();
    for (LocalInspectionTool tool : tools) {
      final PsiElementVisitor visitor = tool.buildVisitor(holder, true);
      if (visitor != null) visitors.add(new Pair<LocalInspectionTool, PsiElementVisitor>(tool, visitor));
    }

    for (PsiElement element : workSet) {
      ProgressManager.getInstance().checkCanceled();
      LocalInspectionTool currentTool = null;
      try {
        if (element instanceof PsiMethod) {
          PsiMethod psiMethod = (PsiMethod)element;
          for (LocalInspectionTool tool : tools) {
            currentTool = tool;
            if (GlobalInspectionContextImpl.isToCheckMember(psiMethod, currentTool.getID())) {
              appendDescriptors(currentTool.checkMethod(psiMethod, iManager, true), currentTool);
            }
          }
        }
        else if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
          PsiClass psiClass = (PsiClass)element;
          for (LocalInspectionTool tool : tools) {
            currentTool = tool;
            if (GlobalInspectionContextImpl.isToCheckMember(psiClass, currentTool.getID())) {
              appendDescriptors(currentTool.checkClass(psiClass, iManager, true), currentTool);
            }
          }
        }
        else if (element instanceof PsiField) {
          PsiField psiField = (PsiField)element;
          for (LocalInspectionTool tool : tools) {
            currentTool = tool;
            if (GlobalInspectionContextImpl.isToCheckMember(psiField, currentTool.getID())) {
              appendDescriptors(currentTool.checkField(psiField, iManager, true), currentTool);
            }
          }
        }
        else if (element instanceof PsiFile) {
          PsiFile psiFile = (PsiFile)element;
          for (LocalInspectionTool tool : tools) {
            currentTool = tool;
            appendDescriptors(currentTool.checkFile(psiFile, iManager, true), currentTool);
          }
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        if (currentTool != null) {
          LOG.error("Exception happened in local inspection tool: " + currentTool.getDisplayName(), e);
        }
        else {
          LOG.error(e);
        }
      }
    }

    final List<PsiElement> elements = CodeInsightUtil.getElementsIntersectingRange(psiRoot, myStartOffset, myEndOffset);
    if (!visitors.isEmpty()) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < elements.size(); i++) {
        PsiElement element = elements.get(i);
        //noinspection ForLoopReplaceableByForEach
        for (int j = 0; j < visitors.size(); j++) {
          Pair<LocalInspectionTool, PsiElementVisitor> visitor = visitors.get(j);
          element.accept(visitor.getSecond());
          appendDescriptors(holder.getResults(), visitor.getFirst());
        }
      }
    }
    inspectInjectedPsi(elements);
  }

  private void inspectInjectedPsi(final List<PsiElement> elements) {
    myInjectedPsiInspectionResults = new SmartList<InjectedPsiInspectionUtil.InjectedPsiInspectionResult>();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      if (element instanceof PsiLanguageInjectionHost) {
        PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)element;
        myInjectedPsiInspectionResults.addAll(InjectedPsiInspectionUtil.inspectInjectedPsi(host));
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
    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(highlightInfoType, textRange, message, toolTip);
    highlightInfo.isAfterEndOfLine = problemDescriptor.isAfterEndOfLine();

    if (problemDescriptor.getPsiElement() instanceof PsiFile) {
      highlightInfo.isFileLevelAnnotation = true;
    }

    return highlightInfo;
  }

  private void appendDescriptors(ProblemDescriptor[] problemDescriptors, LocalInspectionTool tool) {
    if (problemDescriptors == null) return;
    appendDescriptors(Arrays.asList(problemDescriptors), tool);
  }

  private void appendDescriptors(List<ProblemDescriptor> problemDescriptors, LocalInspectionTool tool) {
    if (problemDescriptors == null) return;
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(myFile);
    final HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName())).getSeverity();
    for (ProblemDescriptor problemDescriptor : problemDescriptors) {
      ProgressManager.getInstance().checkCanceled();
      if (!InspectionManagerEx.inspectionResultSuppressed(problemDescriptor.getPsiElement(), tool.getID())) {
        myDescriptors.add(problemDescriptor);
        HighlightInfoType type = highlightTypeFromDescriptor(problemDescriptor, tool, severity);

        myLevels.add(type);
        myTools.add(tool);
      }
    }
  }

  private static HighlightInfoType highlightTypeFromDescriptor(final ProblemDescriptor problemDescriptor, final LocalInspectionTool tool,
                                                    final HighlightSeverity severity) {
    ProblemHighlightType highlightType = problemDescriptor.getHighlightType();
    HighlightInfoType type = null;
    if (highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING) {
      type = SeverityRegistrar.getHighlightInfoTypeBySeverity(severity);
    }
    else if (highlightType == ProblemHighlightType.LIKE_DEPRECATED) {
      type = HighlightInfoType.DEPRECATED;
    }
    else if (highlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) {
      if (JavaDocReferenceInspection.SHORT_NAME.equals(tool.getShortName())){
        type = HighlightInfoType.JAVADOC_WRONG_REF;
      } else {
        type = HighlightInfoType.WRONG_REF;
      }
    }
    else if (highlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
      type = HighlightInfoType.UNUSED_SYMBOL;
    }
    return type;
  }

  public void doApplyInformationToEditor() {
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>(myDescriptors.size());
    addHighlightsFromDescriptors(infos);
    addHighlightsFromInjectedPsiProblems(infos);

    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, infos,
                                                   UpdateHighlightersUtil.INSPECTION_HIGHLIGHTERS_GROUP);
    myDescriptors = Collections.emptyList();
    myLevels = Collections.emptyList();
    myTools = Collections.emptyList();

    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(myDocument, FileStatusMap.LOCAL_INSPECTIONS);

    ErrorStripeRenderer renderer = new RefreshStatusRenderer(myProject, daemonCodeAnalyzer, myDocument, myFile);
    Editor[] editors = EditorFactory.getInstance().getEditors(myDocument, myProject);
    for (Editor editor : editors) {
      ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(renderer);
    }
  }

  private void addHighlightsFromDescriptors(final List<HighlightInfo> infos) {
    for (int i = 0; i < myDescriptors.size(); i++) {
      ProblemDescriptor descriptor = myDescriptors.get(i);
      LocalInspectionTool tool = myTools.get(i);
      final HighlightInfoType level = myLevels.get(i);
      HighlightInfo highlightInfo = createHighlightInfo(descriptor, tool, level);
      infos.add(highlightInfo);
    }
  }

  private void addHighlightsFromInjectedPsiProblems(final List<HighlightInfo> infos) {
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(myFile);
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myInjectedPsiInspectionResults.size(); i++) {
      InjectedPsiInspectionUtil.InjectedPsiInspectionResult result = myInjectedPsiInspectionResults.get(i);
      LocalInspectionTool tool = result.tool;
      HighlightSeverity severity = inspectionProfile.getErrorLevel(HighlightDisplayKey.find(tool.getShortName())).getSeverity();

      int injectedPsiOffset = result.injectedPsi.getContext().getTextRange().getStartOffset() + result.rangeInsideHost.getStartOffset();

      //noinspection ForLoopReplaceableByForEach
      for (int j = 0; j < result.foundProblems.size(); j++) {
        ProblemDescriptor descriptor = result.foundProblems.get(j);
        HighlightInfoType level = highlightTypeFromDescriptor(descriptor, tool, severity);
        HighlightInfo info = createHighlightInfo(descriptor, tool, level);
        if (info == null) continue;
        HighlightInfo patched = HighlightInfo.createHighlightInfo(info.type, info.startOffset + injectedPsiOffset, info.endOffset + injectedPsiOffset, info.description, info.toolTip);
        infos.add(patched);
      }
    }
  }

  @Nullable
  private HighlightInfo createHighlightInfo(final ProblemDescriptor descriptor, final LocalInspectionTool tool, final HighlightInfoType level) {
    //TODO
    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return null;
    @NonNls String message = renderDescriptionMessage(descriptor);

    final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(myFile);
    if (!inspectionProfile.isToolEnabled(key)) return null;


    HighlightInfoType type = new HighlightInfoType() {
      public HighlightSeverity getSeverity(final PsiElement psiElement) {
        return inspectionProfile.getErrorLevel(key).getSeverity();
      }

      public TextAttributesKey getAttributesKey() {
        return level.getAttributesKey();
      }
    };
    String plainMessage = XmlUtil.unescape(message.replaceAll("<[^>]*>", ""));
    @NonNls String tooltip = message.startsWith("<html>") ? message : "<html><body>" + XmlUtil.escapeString(message) + "</body></html>";
    HighlightInfo highlightInfo = highlightInfoFromDescriptor(descriptor, type, plainMessage, tooltip);
    List<IntentionAction> options = getStandardIntentionOptions(tool, psiElement);
    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length > 0) {
      for (int k = 0; k < fixes.length; k++) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new QuickFixWrapper(descriptor, k), options, tool.getDisplayName());
      }
    } else {
      QuickFixAction.registerQuickFixAction(highlightInfo, new EmptyIntentionAction(tool.getDisplayName(), options), options, tool.getDisplayName());
    }
    return highlightInfo;
  }

  private static List<IntentionAction> getStandardIntentionOptions(final LocalInspectionTool tool, final PsiElement psiElement) {
    List<IntentionAction> options = new ArrayList<IntentionAction>();
    options.add(new EditInspectionToolsSettingsAction(tool));
    options.add(new RunInspectionIntention(tool));
    options.add(new AddNoInspectionCommentAction(tool, psiElement));
    options.add(new AddNoInspectionDocTagAction(tool, psiElement));
    options.add(new AddNoInspectionForClassAction(tool, psiElement));
    options.add(new AddNoInspectionAllForClassAction(psiElement));
    options.add(new AddSuppressWarningsAnnotationAction(tool, psiElement));
    options.add(new AddSuppressWarningsAnnotationForClassAction(tool, psiElement));
    options.add(new AddSuppressWarningsAnnotationForAllAction(psiElement));
    options.add(new DisableInspectionToolAction(tool));
    return options;
  }

  public int getPassId() {
    return Pass.LOCAL_INSPECTIONS;
  }

  private static String renderDescriptionMessage(ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    String message = descriptor.getDescriptionTemplate();
    if (message == null) return ""; // no message. Should not be the case if inspection correctly implemented.
    message = StringUtil.replace(message, "<code>", "'");
    message = StringUtil.replace(message, "</code>", "'");
    //message = message.replaceAll("<[^>]*>", "");
    String text = psiElement == null ? "" : psiElement.getText();
    message = StringUtil.replace(message, "#ref", text);
    message = StringUtil.replace(message, "#loc", "");

    message = XmlUtil.unescape(message);
    return message;
  }

}
