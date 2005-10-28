package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.QuickFixWrapper;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

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
  private List<ProblemDescriptor> myDescriptors = Collections.EMPTY_LIST;
  private List<HighlightInfoType> myLevels = Collections.EMPTY_LIST;
  private List<LocalInspectionTool> myTools = Collections.EMPTY_LIST;

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

    PsiElement[] psiRoots = myFile.getPsiRoots();
    for (final PsiElement psiRoot : psiRoots) {
      if(!HighlightUtil.isRootInspected(psiRoot)) continue;
      PsiElement[] elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      final Set<PsiElement> workSet = new THashSet<PsiElement>();
      for (PsiElement element1 : elements) {
        ProgressManager.getInstance().checkCanceled();

        PsiElement element = PsiTreeUtil.getNonStrictParentOfType(element1, PsiMethod.class, PsiField.class, PsiClass.class);
        while (element != null) {
          if (!workSet.add(element)) break;
          element = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiField.class, PsiClass.class);
        }
      }
      workSet.add(myFile);

      myDescriptors = new ArrayList<ProblemDescriptor>();
      myLevels = new ArrayList<HighlightInfoType>();
      myTools = new ArrayList<LocalInspectionTool>();

      final LocalInspectionTool[] tools = DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(myFile).getHighlightingLocalInspectionTools();
      PsiManager.getInstance(myProject).performActionWithFormatterDisabled(new Runnable() {
        public void run() {
          for (PsiElement element : workSet) {
            ProgressManager.getInstance().checkCanceled();
            LocalInspectionTool currentTool = null;
            try {
              if (element instanceof PsiMethod) {
                PsiMethod psiMethod = (PsiMethod)element;
                for (LocalInspectionTool tool : tools) {
                  currentTool = tool;
                  if (InspectionManagerEx.isToCheckMember(psiMethod, currentTool.getID())) {
                    appendDescriptors(currentTool.checkMethod(psiMethod, iManager, true), currentTool);
                  }
                }
              }
              else if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
                PsiClass psiClass = (PsiClass)element;
                for (LocalInspectionTool tool : tools) {
                  currentTool = tool;
                  if (InspectionManagerEx.isToCheckMember(psiClass, currentTool.getID())) {
                    appendDescriptors(currentTool.checkClass(psiClass, iManager, true), currentTool);
                  }
                }
              }
              else if (element instanceof PsiField) {
                PsiField psiField = (PsiField)element;
                for (LocalInspectionTool tool : tools) {
                  currentTool = tool;
                  if (InspectionManagerEx.isToCheckMember(psiField, currentTool.getID())) {
                    appendDescriptors(currentTool.checkField(psiField, iManager, true), currentTool);
                  }
                }
              }
              else if (element instanceof PsiFile){
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
        }
      });
    }
  }

  //for tests only
  public Collection<HighlightInfo> getHighlights() {
    ArrayList<HighlightInfo> highlights = new ArrayList<HighlightInfo>();
    for (int i = 0; i < myDescriptors.size(); i++) {
      ProblemDescriptor problemDescriptor = myDescriptors.get(i);
      String message = renderDescriptionMessage(problemDescriptor);
      PsiElement psiElement = problemDescriptor.getPsiElement();
      HighlightInfo highlightInfo =
        HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING, psiElement, message, message);
      highlights.add(highlightInfo);
      LocalInspectionTool tool = myTools.get(i);
      List<IntentionAction> options = new ArrayList<IntentionAction>();
      options.add(new EditInspectionToolsSettingsAction(tool));
      options.add(new AddNoInspectionCommentAction(tool, psiElement));
      options.add(new AddNoInspectionDocTagAction(tool, psiElement));
      options.add(new AddNoInspectionForClassAction(tool, psiElement));
      options.add(new AddSuppressWarningsAnnotationAction(tool, psiElement));
      options.add(new AddSuppressWarningsAnnotationForClassAction(tool, psiElement));
      options.add(new AddSuppressWarningsAnnotationForAllAction(psiElement));
      final LocalQuickFix[] fixes = problemDescriptor.getFixes();
      if (fixes != null && fixes.length > 0) {
        for (int k = 0; k < problemDescriptor.getFixes().length; k++) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new QuickFixWrapper(problemDescriptor, k), options);
        }
      } else {
        QuickFixAction.registerQuickFixAction(highlightInfo, new EmptyIntentionAction(tool.getDisplayName(), options), options);
      }
    }
    return highlights;
  }

  private void appendDescriptors(ProblemDescriptor[] problemDescriptors, LocalInspectionTool tool) {
    ProgressManager.getInstance().checkCanceled();

    if (problemDescriptors != null) {
      boolean isError = DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(myFile).getErrorLevel(
        HighlightDisplayKey.find(tool.getShortName())) ==
                                                       HighlightDisplayLevel.ERROR;
      for (ProblemDescriptor problemDescriptor : problemDescriptors) {
        if (!InspectionManagerEx.inspectionResultSuppressed(problemDescriptor.getPsiElement(), tool.getID())) {
          myDescriptors.add(problemDescriptor);
          ProblemHighlightType highlightType = problemDescriptor.getHighlightType();
          HighlightInfoType type = null;
          if (highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING) {
            type = isError ? HighlightInfoType.ERROR : HighlightInfoType.WARNING;
          }
          else if (highlightType == ProblemHighlightType.LIKE_DEPRECATED) {
            type = HighlightInfoType.DEPRECATED;
          }
          else if (highlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) {
            type = HighlightInfoType.WRONG_REF;
          }
          else if (highlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
            type = HighlightInfoType.UNUSED_SYMBOL;
          }


          myLevels.add(type);
          myTools.add(tool);
        }
      }
    }
  }

  public void doApplyInformationToEditor() {
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>(myDescriptors.size());
    for (int i = 0; i < myDescriptors.size(); i++) {
      ProblemDescriptor descriptor = myDescriptors.get(i);
      LocalInspectionTool tool = myTools.get(i);
      //TODO
      PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement == null) continue;
      String message = renderDescriptionMessage(descriptor);
      final HighlightInfoType level = myLevels.get(i);

      HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
      InspectionProfileImpl inspectionProfile = DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(myFile);
      if (!inspectionProfile.isToolEnabled(key)) continue;
      final boolean isError = inspectionProfile.getErrorLevel(key) == HighlightDisplayLevel.ERROR;


      HighlightInfoType type = new HighlightInfoType() {
        public HighlightSeverity getSeverity(final PsiElement psiElement) {
          return isError ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
        }

        public TextAttributesKey getAttributesKey() {
          return level.getAttributesKey();
        }
      };
      String plainMessage = XmlUtil.unescape(message.replaceAll("<[^>]*>", ""));
      @NonNls String tooltip = "<html><body>" + XmlUtil.escapeString(message) + "</body></html>";
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(type, psiElement, plainMessage, tooltip);
      infos.add(highlightInfo);
      List<IntentionAction> options = new ArrayList<IntentionAction>();
      options.add(new EditInspectionToolsSettingsAction(tool));
      options.add(new AddNoInspectionCommentAction(tool, psiElement));
      options.add(new AddNoInspectionDocTagAction(tool, psiElement));
      options.add(new AddNoInspectionForClassAction(tool, psiElement));
      options.add(new AddNoInspectionAllForClassAction(psiElement));
      options.add(new AddSuppressWarningsAnnotationAction(tool, psiElement));
      options.add(new AddSuppressWarningsAnnotationForClassAction(tool, psiElement));
      options.add(new AddSuppressWarningsAnnotationForAllAction(psiElement));
      final LocalQuickFix[] fixes = descriptor.getFixes();
      if (fixes != null && fixes.length > 0) {
        for (int k = 0; k < fixes.length; k++) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new QuickFixWrapper(descriptor, k), options);
        }
      } else {
        QuickFixAction.registerQuickFixAction(highlightInfo, new EmptyIntentionAction(tool.getDisplayName(), options), options);
      }
    }

    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, infos,
                                                   UpdateHighlightersUtil.INSPECTION_HIGHLIGHTERS_GROUP);
    myDescriptors = Collections.EMPTY_LIST;
    myLevels = Collections.EMPTY_LIST;
    myTools = Collections.EMPTY_LIST;

    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(myDocument, FileStatusMap.LOCAL_INSPECTIONS);

    ErrorStripeRenderer renderer = new RefreshStatusRenderer(myProject, daemonCodeAnalyzer, myDocument, myFile);
    Editor[] editors = EditorFactory.getInstance().getEditors(myDocument, myProject);
    for (Editor editor : editors) {
      ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(renderer);
    }
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
