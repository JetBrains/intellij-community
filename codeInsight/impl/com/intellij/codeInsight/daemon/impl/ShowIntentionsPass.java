package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ShowIntentionsPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.ShowIntentionsPass");
  private final Editor myEditor;
  @Nullable private IntentionAction[] myIntentionActions; //null means all actions in IntentionManager

  private final PsiFile myFile;

  private int myStartOffset;
  private int myEndOffset;
  private final int myPassIdToShowIntentionsFor;

  ShowIntentionsPass(Project project, Editor editor, int passId, @Nullable IntentionAction[] intentionActions) {
    super(project, editor.getDocument());
    myPassIdToShowIntentionsFor = passId;
    ApplicationManager.getApplication().assertIsDispatchThread();

    myEditor = editor;
    myIntentionActions = intentionActions;

    TextRange range = getVisibleRange(myEditor);
    myStartOffset = range.getStartOffset();
    myEndOffset = range.getEndOffset();

    myFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    LOG.assertTrue(myFile != null);
  }

  private static TextRange getVisibleRange(Editor editor) {
    Rectangle visibleRect = editor.getScrollingModel().getVisibleArea();

    LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(visibleRect.x, visibleRect.y));
    int myStartOffset = editor.logicalPositionToOffset(startPosition);

    LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(visibleRect.x + visibleRect.width, visibleRect.y + visibleRect.height));
    int myEndOffset = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));
    return new TextRange(myStartOffset, myEndOffset);
  }

  public void doCollectInformation(ProgressIndicator progress) {
  }

  public void doApplyInformationToEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!myEditor.getContentComponent().hasFocus()) return;

    HighlightInfo[] visibleHighlights = getVisibleHighlights(myStartOffset, myEndOffset, myProject, myEditor);

    PsiElement[] elements = new PsiElement[visibleHighlights.length];
    for (int i = 0; i < visibleHighlights.length; i++) {
      ProgressManager.getInstance().checkCanceled();

      HighlightInfo highlight = visibleHighlights[i];
      final PsiElement elementAt = myFile.findElementAt(highlight.startOffset);
      elements[i] = elementAt;
    }

    int caretOffset = myEditor.getCaretModel().getOffset();
    for (int i = visibleHighlights.length - 1; i >= 0; i--) {
      HighlightInfo info = visibleHighlights[i];
      if (elements[i] != null && info.startOffset <= caretOffset && showAddImportHint(info, elements[i])) return;
    }

    for (int i = 0; i < visibleHighlights.length; i++) {
      HighlightInfo info = visibleHighlights[i];
      if (elements[i] != null && info.startOffset > caretOffset && showAddImportHint(info, elements[i])) return;
    }

    TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
    if (state == null || state.isFinished()) {
      showIntentionActions();
    }
  }

  private void showIntentionActions() {
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    if (LookupManager.getInstance(myProject).getActiveLookup() != null) return;

    // do not show intentions if caret is outside visible area
    LogicalPosition caretPos = myEditor.getCaretModel().getLogicalPosition();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    Point xy = myEditor.logicalPositionToXY(caretPos);
    if (!visibleArea.contains(xy)) return;
    List<HighlightInfo.IntentionActionDescriptor> intentionsToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    List<HighlightInfo.IntentionActionDescriptor> errorFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    List<HighlightInfo.IntentionActionDescriptor> inspectionFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    if (myIntentionActions == null) {
      myIntentionActions = IntentionManager.getInstance().getIntentionActions();
    }
    getActionsToShow(myEditor, myFile, intentionsToShow, errorFixesToShow, inspectionFixesToShow, myIntentionActions, myPassIdToShowIntentionsFor);
    if (myFile instanceof PsiCodeFragment) {
      final PsiCodeFragment.IntentionActionsFilter actionsFilter = ((PsiCodeFragment)myFile).getIntentionActionsFilter();
      if (actionsFilter == null) return;
      if (actionsFilter != PsiCodeFragment.IntentionActionsFilter.EVERYTHING_AVAILABLE) {
        filterIntentionActions(actionsFilter, intentionsToShow);
        filterIntentionActions(actionsFilter, errorFixesToShow);
        filterIntentionActions(actionsFilter, inspectionFixesToShow);
      }
    }

    if (!intentionsToShow.isEmpty() || !errorFixesToShow.isEmpty() || !inspectionFixesToShow.isEmpty()) {
      boolean showBulb = false;
      for (HighlightInfo.IntentionActionDescriptor action : ContainerUtil.concat(errorFixesToShow, inspectionFixesToShow)) {
        if (IntentionManagerSettings.getInstance().isShowLightBulb(action.getAction())) {
          showBulb = true;
          break;
        }
      }
      if (!showBulb) {
        for (HighlightInfo.IntentionActionDescriptor descriptor : intentionsToShow) {
          final IntentionAction action = descriptor.getAction();
          if (IntentionManagerSettings.getInstance().isShowLightBulb(action) && action.isAvailable(myProject, myEditor, myFile)) {
            showBulb = true;
            break;
          }
        }
      }

      if (showBulb) {
        IntentionHintComponent hintComponent = codeAnalyzer.getLastIntentionHint();

        if (hintComponent != null) {
          if (hintComponent.updateActions(intentionsToShow, errorFixesToShow, inspectionFixesToShow)) {
            return;
          }
          codeAnalyzer.setLastIntentionHint(null);
        }
        if (!HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) {
          hintComponent = IntentionHintComponent.showIntentionHint(myProject, myFile, myEditor, intentionsToShow, errorFixesToShow, inspectionFixesToShow, false);
          codeAnalyzer.setLastIntentionHint(hintComponent);
        }
      }
    }
  }

  private static void filterIntentionActions(final PsiCodeFragment.IntentionActionsFilter actionsFilter, final List<HighlightInfo.IntentionActionDescriptor> intentionActionDescriptors) {
    for (Iterator<HighlightInfo.IntentionActionDescriptor> it = intentionActionDescriptors.iterator(); it.hasNext();) {
        HighlightInfo.IntentionActionDescriptor actionDescriptor = it.next();
        if (!actionsFilter.isAvailable(actionDescriptor.getAction())) it.remove();
      }
  }

  public static void getActionsToShow(final Editor editor, final PsiFile psiFile,
                                final List<HighlightInfo.IntentionActionDescriptor> intentionsToShow,
                                final List<HighlightInfo.IntentionActionDescriptor> errorFixesToShow,
                                final List<HighlightInfo.IntentionActionDescriptor> inspectionFixesToShow,
                                IntentionAction[] allIntentionActions, int passIdToShowIntentionsFor) {
    final PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    LOG.assertTrue(psiElement == null || psiElement.isValid(), psiElement);
    final boolean isInProject = psiFile.getManager().isInProject(psiFile);

    int offset = editor.getCaretModel().getOffset();
    Project project = psiFile.getProject();
    final DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    HighlightInfo infoAtCursor = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(editor.getDocument(), offset, true);
    for (IntentionAction action : allIntentionActions) {
      if (action instanceof PsiElementBaseIntentionAction && isInProject && ((PsiElementBaseIntentionAction)action).isAvailable(project, editor, psiElement)
          || action.isAvailable(project, editor, psiFile)) {
        List<IntentionAction> enableDisableIntentionAction = new ArrayList<IntentionAction>();
        enableDisableIntentionAction.add(new IntentionHintComponent.EnableDisableIntentionAction(action));
        intentionsToShow.add(new HighlightInfo.IntentionActionDescriptor(action, enableDisableIntentionAction, null));
      }
    }

    QuickFixAction quickFixAction = new QuickFixAction();
    List<HighlightInfo.IntentionActionDescriptor> actions = quickFixAction.getAvailableActions(editor, psiFile, passIdToShowIntentionsFor);
    if (infoAtCursor == null || infoAtCursor.getSeverity() == HighlightSeverity.ERROR) {
      errorFixesToShow.addAll(actions);
    }
    else {
      inspectionFixesToShow.addAll(actions);
    }
  }

  @NotNull
  private static HighlightInfo[] getVisibleHighlights(int startOffset, int endOffset, Project project, Editor editor) {
    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), project);
    if (highlights == null) return HighlightInfo.EMPTY_ARRAY;

    List<HighlightInfo> array = new ArrayList<HighlightInfo>();
    for (HighlightInfo info : highlights) {
      if (isWrongRef(info.type)
          && startOffset <= info.startOffset && info.endOffset <= endOffset
          && !editor.getFoldingModel().isOffsetCollapsed(info.startOffset)) {
        array.add(info);
      }
    }
    return array.toArray(new HighlightInfo[array.size()]);
  }

  private boolean showAddImportHint(HighlightInfo info, PsiElement element) {
    if (!DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled()) return false;
    if (!DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(myFile)) return false;
    if (!element.isValid() || !element.isWritable()) return false;

    element = element.getParent();
    if (!(element instanceof PsiJavaCodeReferenceElement)) return false;
                                       
    final HighlightInfoType infoType = info.type;
    return isWrongRef(infoType) && handleWrongRefInfo(myEditor, (PsiJavaCodeReferenceElement)element, true);
  }

  private static boolean isWrongRef(final HighlightInfoType infoType) {
    return infoType.getAttributesKey() == HighlightInfoType.WRONG_REF.getAttributesKey();
  }

  public static void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file) {
    int caretOffset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(caretOffset);
    int startOffset = document.getLineStartOffset(lineNumber);
    int endOffset = document.getLineEndOffset(lineNumber);

    List<PsiElement> elements = CodeInsightUtil.getElementsInRange(file, startOffset, endOffset);
    for (PsiElement element : elements) {
      if (element instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)element;
        if (ref.resolve() == null) {
          handleWrongRefInfo(editor, ref, false);
        }
      }
    }
  }

  private static boolean handleWrongRefInfo(Editor editor, PsiJavaCodeReferenceElement ref, final boolean showAddImportHint) {
    if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) return false;

    PsiManager manager = ref.getManager();
    if (manager == null) return false;
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiShortNamesCache cache = manager.getShortNamesCache();
    PsiElement refname = ref.getReferenceNameElement();
    if (!(refname instanceof PsiIdentifier)) return false;
    PsiElement refElement = ref.resolve();
    if (refElement != null) return false;
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
      //ignore
    }

    List<PsiClass> availableClasses = new ArrayList<PsiClass>();
    boolean isAnnotationReference = ref.getParent() instanceof PsiAnnotation;
    for (PsiClass aClass : classes) {
      if (!isFromNonDefaultPackage(aClass)) continue;
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
    final QuestionAction action = new AddImportAction(manager.getProject(), ref, editor, classes);

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(manager.getProject());

    if (classes.length == 1 && CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY && !isCaretNearRef(editor, ref) &&
        !PsiUtil.isInJspFile(psiFile) && codeAnalyzer.canChangeFileSilently(psiFile) &&
        !hasUnresolvedImportWhichCanImport(psiFile, classes[0].getName())) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        public void run() {
          action.execute();
        }
      });
      return false;
    }
    if (showAddImportHint) {
      HintManager hintManager = HintManager.getInstance();
      hintManager.showQuestionHint(editor, hintText, offset1, offset2, action);
    }
    return true;
  }

  private static boolean isFromNonDefaultPackage(PsiClass aClass) {
    //inner classes from default package _cannot_ be imported
    PsiClass containingClass = aClass.getContainingClass();
    while(containingClass != null) {
      aClass = containingClass;
      containingClass = containingClass.getContainingClass();
    }
    final String qName = aClass.getQualifiedName();
    //default package
    return qName != null && qName.indexOf('.') > 0;
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
      if (qualifiedName != null && StringUtil.split(qualifiedName, ".").contains(name)) return true;
    }
    return false;
  }

  private static void reduceSuggestedClassesBasedOnDependencyRuleViolation(PsiFile file, List<PsiClass> availableClasses) {
    final Project project = file.getProject();
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(project);
    for (int i = availableClasses.size() - 1; i >= 0; i--) {
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
}
