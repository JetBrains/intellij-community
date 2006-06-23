package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.daemon.impl.quickfix.PostIntentionsQuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionActionComposite;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ShowIntentionsPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.ShowIntentionsPass");
  private final Project myProject;
  private final Editor myEditor;
  private final IntentionAction[] myIntentionActions;

  private final PsiFile myFile;

  private boolean myIsSecondPass;
  private int myStartOffset;
  private int myEndOffset;


  ShowIntentionsPass(Project project, Editor editor, IntentionAction[] intentionActions, boolean isSecondPass) {
    super(editor.getDocument());
    ApplicationManager.getApplication().assertIsDispatchThread();

    myIsSecondPass = isSecondPass;
    myProject = project;
    myEditor = editor;
    myIntentionActions = intentionActions;

    Rectangle visibleRect = myEditor.getScrollingModel().getVisibleArea();

    LogicalPosition startPosition = myEditor.xyToLogicalPosition(new Point(visibleRect.x, visibleRect.y));
    myStartOffset = myEditor.logicalPositionToOffset(startPosition);

    LogicalPosition endPosition = myEditor.xyToLogicalPosition(
      new Point(visibleRect.x + visibleRect.width, visibleRect.y + visibleRect.height));
    myEndOffset = myEditor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));

    myFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    LOG.assertTrue(myFile != null);
  }

  public void doCollectInformation(ProgressIndicator progress) {
  }

  public void doApplyInformationToEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!myEditor.getContentComponent().hasFocus()) return;

    HighlightInfo[] visibleHighlights = getVisibleHighlights(myStartOffset, myEndOffset);
    if (visibleHighlights == null) return;

    PsiElement[] elements = new PsiElement[visibleHighlights.length];
    for (int i = 0; i < visibleHighlights.length; i++) {
      ProgressManager.getInstance().checkCanceled();

      HighlightInfo highlight = visibleHighlights[i];
      elements[i] = myFile.findElementAt(highlight.startOffset);
    }

    int caretOffset = myEditor.getCaretModel().getOffset();
    for (int i = visibleHighlights.length - 1; i >= 0; i--) {
      HighlightInfo info = visibleHighlights[i];
      if (elements[i] == null) continue;
      if (info.startOffset <= caretOffset) {
        if (showAddImportHint(info, elements[i])) return;
      }
    }

    for (int i = 0; i < visibleHighlights.length; i++) {
      HighlightInfo info = visibleHighlights[i];
      if (elements[i] == null) continue;
      if (info.startOffset > caretOffset) {
        if (showAddImportHint(info, elements[i])) return;
      }
    }

    if (!(myFile instanceof PsiCodeFragment)) {
      TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
      if (state == null || state.isFinished()) {
        showIntentionActions();
      }
    }
  }

  public int getPassId() {
    return myIsSecondPass ? Pass.POPUP_HINTS2 : Pass.POPUP_HINTS;
  }

  private void showIntentionActions() {
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    if (myIsSecondPass) codeAnalyzer.setShowPostIntentions(true);
    if (LookupManager.getInstance(myProject).getActiveLookup() != null) return;

    // do not show intentions if caret is outside visible area
    LogicalPosition caretPos = myEditor.getCaretModel().getLogicalPosition();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    Point xy = myEditor.logicalPositionToXY(caretPos);
    if (!visibleArea.contains(xy)) return;

    ArrayList<HighlightInfo.IntentionActionDescriptor> intentionsToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    ArrayList<HighlightInfo.IntentionActionDescriptor> fixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    for (IntentionAction action : myIntentionActions) {
      if (action instanceof IntentionActionComposite) {
        if (action instanceof QuickFixAction ||
            action instanceof PostIntentionsQuickFixAction && codeAnalyzer.showPostIntentions()) {
          List<HighlightInfo.IntentionActionDescriptor> availableActions = ((IntentionActionComposite)action).getAvailableActions(myEditor, myFile);

          int offset = myEditor.getCaretModel().getOffset();
          HighlightInfo info = codeAnalyzer.findHighlightByOffset(myEditor.getDocument(), offset, true);
          if (info == null || info.getSeverity() == HighlightSeverity.ERROR) {
            fixesToShow.addAll(availableActions);
          }
          else {
            intentionsToShow.addAll(availableActions);
          }
        }
      }
      else if (action.isAvailable(myProject, myEditor, myFile)) {
        List<IntentionAction> enableDisableIntentionAction = new ArrayList<IntentionAction>();
        enableDisableIntentionAction.add(new IntentionHintComponent.EnableDisableIntentionAction(action));
        intentionsToShow.add(new HighlightInfo.IntentionActionDescriptor(action, enableDisableIntentionAction, null));
      }
    }

    if (!intentionsToShow.isEmpty() || !fixesToShow.isEmpty()) {
      boolean showBulb = false;
      for (HighlightInfo.IntentionActionDescriptor action : fixesToShow) {
        if (IntentionManagerSettings.getInstance().isShowLightBulb(action.getAction())) {
          showBulb = true;
          break;
        }
      }
      if (!showBulb) {
        for (HighlightInfo.IntentionActionDescriptor action : intentionsToShow) {
          if (IntentionManagerSettings.getInstance().isShowLightBulb(action.getAction())) {
            showBulb = true;
            break;
          }
        }
      }

      if (showBulb) {
        if (myIsSecondPass) {
          IntentionHintComponent hintComponent = codeAnalyzer.getLastIntentionHint();
          if (hintComponent != null) {
            hintComponent.updateIfNotShowingPopup(fixesToShow, intentionsToShow);
          }
        }

        if (!HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) {
          IntentionHintComponent hintComponent = IntentionHintComponent.showIntentionHint(myProject, myEditor, intentionsToShow,
                                                                                          fixesToShow, false);
          if (!myIsSecondPass) {
            codeAnalyzer.setLastIntentionHint(hintComponent);
          }
        }
      }
    }
  }

  private HighlightInfo[] getVisibleHighlights(int startOffset, int endOffset) {
    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(myEditor.getDocument(), myProject);
    if (highlights == null) return null;

    ArrayList<HighlightInfo> array = new ArrayList<HighlightInfo>();
    for (HighlightInfo info : highlights) {
      if (!canBeHint(info.type)) continue;
      if (startOffset <= info.startOffset && info.endOffset <= endOffset) {
        if (myEditor.getFoldingModel().isOffsetCollapsed(info.startOffset)) continue;
        array.add(info);
      }
    }
    return array.toArray(new HighlightInfo[array.size()]);
  }

  private boolean showAddImportHint(HighlightInfo info, PsiElement element) {
    if (!element.isWritable()) return false;
    if (!DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled()) return false;
    if (!DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(myFile)) return false;

    element = element.getParent();
    if (!(element instanceof PsiJavaCodeReferenceElement)) return false;

    if (info.type == HighlightInfoType.WRONG_REF) {
      return showAddImportHint(myEditor, (PsiJavaCodeReferenceElement)element);
    }
    else if (info.type == HighlightInfoType.JAVADOC_WRONG_REF) {
      HighlightDisplayKey javadocKey = HighlightDisplayKey.find(JavaDocLocalInspection.SHORT_NAME);
      if (javadocKey == null){
        HighlightDisplayKey.register(JavaDocReferenceInspection.SHORT_NAME, JavaDocReferenceInspection.DISPLAY_NAME);
      }
      if (InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(myFile).getErrorLevel(javadocKey) ==
          HighlightDisplayLevel.ERROR) {
        return showAddImportHint(myEditor, (PsiJavaCodeReferenceElement)element);
      }
    }

    return false;
  }

  private static boolean showAddImportHint(Editor editor, PsiJavaCodeReferenceElement ref) {
    if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) return false;

    PsiManager manager = ref.getManager();
    if (manager == null) return false;
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiShortNamesCache cache = manager.getShortNamesCache();
    PsiElement refname = ref.getReferenceNameElement();
    if (!(refname instanceof PsiIdentifier)) {
      return false;
    }
    PsiElement refElement = ref.resolve();
    if (refElement != null) {
      return false;
    }
    String name = ref.getQualifiedName();
    if (manager.getResolveHelper().resolveReferencedClass(name, ref) != null) return false;

    GlobalSearchScope scope = ref.getResolveScope();
    PsiClass[] classes = cache.getClassesByName(name, scope);
    if (classes.length == 0) return false;

    try {
      Pattern pattern = Pattern.compile(DaemonCodeAnalyzerSettings.getInstance().NO_AUTO_IMPORT_PATTERN);
      Matcher matcher = pattern.matcher(name);
      if (matcher.matches()) return false;
    }
    catch (PatternSyntaxException e) {
    }

    List<PsiClass> availableClasses = new ArrayList<PsiClass>();
    boolean isAnnotationReference = ref.getParent() instanceof PsiAnnotation;
    for (PsiClass aClass : classes) {
      if (aClass.getParent() instanceof PsiDeclarationStatement) continue;
      PsiFile file = aClass.getContainingFile();
      if (!(file instanceof PsiJavaFile) || ((PsiJavaFile)file).getPackageName().length() == 0) continue;
      if (isAnnotationReference && !aClass.isAnnotationType()) continue;
      if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) continue;
      if (CompletionUtil.isInExcludedPackage(aClass)) continue;
      availableClasses.add(aClass);
    }
    if (availableClasses.isEmpty()) return false;

    PsiReferenceParameterList parameterList = ref.getParameterList();
    int refTypeArgsLength = parameterList == null ? 0 : parameterList.getTypeArguments().length;
    if (refTypeArgsLength != 0) {
      List<PsiClass> typeArgMatched = new ArrayList<PsiClass>(availableClasses);
      // try to reduce suggestions based on type argument list
      for (int i = typeArgMatched.size() - 1; i >= 0; i--) {
        PsiClass aClass = typeArgMatched.get(i);
        PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
        if (refTypeArgsLength != typeParameters.length) {
          typeArgMatched.remove(i);
        }
      }
      if (!typeArgMatched.isEmpty()) {
        availableClasses = typeArgMatched;
      }
    }
    PsiFile psiFile = ref.getContainingFile();
    if (availableClasses.size() > 1) {
      reduceSuggestedClassesBasedOnDependencyRuleViolation(psiFile, availableClasses);
    }
    classes = availableClasses.toArray(new PsiClass[availableClasses.size()]);
    CodeInsightUtil.sortIdenticalShortNameClasses(classes);
    @NonNls String messageKey = classes.length > 1 ? "import.popup.multiple" : "import.popup.text";

    String hintText = QuickFixBundle.message(messageKey, classes[0].getQualifiedName());

    hintText += " " + KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));

    int offset1 = ref.getTextOffset();
    int offset2 = ref.getTextRange().getEndOffset();
    QuestionAction action = new AddImportAction(manager.getProject(), ref, classes, editor);

    if (classes.length == 1
        && CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY
        && !isCaretNearRef(editor,ref)
        && !PsiUtil.isInJspFile(psiFile)
        && DaemonCodeAnalyzerImpl.canChangeFileSilently(psiFile)
        && !hasUnresolvedImportWhichCanImport(psiFile, classes[0].getName())
      ) {
      action.execute();
      return false;
    }
    HintManager hintManager = HintManager.getInstance();
    hintManager.showQuestionHint(editor, hintText, offset1, offset2, action);
    return true;
  }

  private static boolean hasUnresolvedImportWhichCanImport(final PsiFile psiFile, final String name) {
    if (!(psiFile instanceof PsiJavaFile)) return false;
    PsiImportList importList = ((PsiJavaFile)psiFile).getImportList();
    if (importList == null) return false;
    PsiImportStatement[] importStatements = importList.getImportStatements();
    for (PsiImportStatement importStatement : importStatements) {
      if (importStatement.resolve() != null) continue;
      if (importStatement.isOnDemand()) return true;
      String qualifiedName = importStatement.getQualifiedName();
      String className = qualifiedName == null ? null : ClassUtil.extractClassName(qualifiedName);
      if (Comparing.strEqual(className, name)) return true;
    }
    PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    for (PsiImportStaticStatement importStaticStatement : importStaticStatements) {
      if (importStaticStatement.resolve() != null) continue;
      if (importStaticStatement.isOnDemand()) return true;
      String qualifiedName = importStaticStatement.getReferenceName();
      // rough heuristic, since there is no API to get class name refrence from static import
      if (qualifiedName != null && StringUtil.split(qualifiedName,".").contains(name)) return true;
    }
    return false;
  }

  private static void reduceSuggestedClassesBasedOnDependencyRuleViolation(PsiFile file, List<PsiClass> availableClasses) {
    final Project project = file.getProject();
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(project);
    for (int i = availableClasses.size()-1; i>=0;i--) {
      PsiClass psiClass = availableClasses.get(i);
      PsiFile targetFile = psiClass.getContainingFile();
      if (targetFile == null) continue;
      final DependencyRule[] violated = validationManager.getViolatorDependencyRules(file, targetFile);
      if (violated.length != 0) {
        availableClasses.remove(i);
        if (availableClasses.size() == 1) break;
      }
    }
  }
  private static boolean isCaretNearRef(Editor editor, PsiJavaCodeReferenceElement ref) {
    TextRange range = ref.getTextRange();
    int offset = editor.getCaretModel().getOffset();

    return range.grown(1).contains(offset);
  }

  private static boolean canBeHint(HighlightInfoType type) {
    return type == HighlightInfoType.WRONG_REF;
  }
}
