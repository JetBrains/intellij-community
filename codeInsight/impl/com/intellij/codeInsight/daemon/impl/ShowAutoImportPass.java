package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ShowAutoImportPass extends TextEditorHighlightingPass {
  protected final Editor myEditor;

  protected final PsiFile myFile;

  private final int myStartOffset;
  private final int myEndOffset;

  public ShowAutoImportPass(@NotNull Project project, @NotNull Editor editor) {
    super(project, editor.getDocument());
    ApplicationManager.getApplication().assertIsDispatchThread();

    myEditor = editor;

    TextRange range = getVisibleRange(myEditor);
    myStartOffset = range.getStartOffset();
    myEndOffset = range.getEndOffset();

    myFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    assert myFile != null : FileDocumentManager.getInstance().getFile(myEditor.getDocument());
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
    doMyJob();
  }

  protected boolean doMyJob() {
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
      if (elements[i] != null && info.startOffset <= caretOffset && showAddImportHint(info, elements[i])) return true;
    }

    for (int i = 0; i < visibleHighlights.length; i++) {
      HighlightInfo info = visibleHighlights[i];
      if (elements[i] != null && info.startOffset > caretOffset && showAddImportHint(info, elements[i])) return true;
    }
    return false;
  }

  @NotNull
  private HighlightInfo[] getVisibleHighlights(int startOffset, int endOffset, Project project, Editor editor) {
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

  protected boolean showAddImportHint(HighlightInfo info, PsiElement element) {
    if (!DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled()) return false;
    if (!DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(myFile)) return false;
    if (!element.isValid()) return false;

    element = element.getParent();
    if (!(element instanceof PsiJavaCodeReferenceElement)) return false;
    final Pair<String, ? extends QuestionAction> descriptor = info.getQuestionAction(element, myEditor, myFile);
    if (descriptor == null) {
      return false;
    }
    final int offset1 = element.getTextOffset();
    final int offset2 = element.getTextRange().getEndOffset();
    HintManager.getInstance().showQuestionHint(myEditor, descriptor.getFirst(), offset1, offset2, descriptor.getSecond());
    return true;
  }

  protected boolean isWrongRef(final HighlightInfoType infoType) {
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
          getDescriptor(editor, ref);
        }
      }
    }
  }
  
  public static Pair<String,QuestionAction> getDescriptor(Editor editor, PsiJavaCodeReferenceElement ref) {

    if (!ApplicationManager.getApplication().isUnitTestMode() && HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) return null;
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiManager manager = ref.getManager();
    if (manager == null) return null;
    PsiElement refname = ref.getReferenceNameElement();
    if (!(refname instanceof PsiIdentifier)) return null;
    PsiElement refElement = ref.resolve();
    if (refElement != null) return null;
    String name = ref.getQualifiedName();

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    if (facade.getResolveHelper().resolveReferencedClass(name, ref) != null) return null;

    PsiShortNamesCache cache = facade.getShortNamesCache();
    GlobalSearchScope scope = ref.getResolveScope();
    PsiClass[] classes = cache.getClassesByName(name, scope);
    if (classes.length == 0) return null;

    try {
      Pattern pattern = Pattern.compile(DaemonCodeAnalyzerSettings.getInstance().NO_AUTO_IMPORT_PATTERN);
      Matcher matcher = pattern.matcher(name);
      if (matcher.matches()) return null;
    }
    catch (PatternSyntaxException e) {
      //ignore
    }

    List<PsiClass> availableClasses = new ArrayList<PsiClass>();
    boolean isAnnotationReference = ref.getParent() instanceof PsiAnnotation;
    for (PsiClass aClass : classes) {
      if (isFromDefaultPackage(aClass)) continue;
      if (isAnnotationReference && !aClass.isAnnotationType()) continue;
      if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) continue;
      if (CompletionUtil.isInExcludedPackage(aClass)) continue;
      availableClasses.add(aClass);
    }
    if (availableClasses.isEmpty()) return null;

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
    CodeInsightUtil.sortIdenticalShortNameClasses(classes, psiFile);

    final QuestionAction action = new AddImportAction(manager.getProject(), ref, editor, classes);

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(manager.getProject());

    if (classes.length == 1
        && CodeStyleSettingsManager.getSettings(manager.getProject()).ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY
        && !isCaretNearRef(editor, ref)
        && !PsiUtil.isInJspFile(psiFile)
        && codeAnalyzer.canChangeFileSilently(psiFile)
        && !hasUnresolvedImportWhichCanImport(psiFile, classes[0].getName())) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        public void run() {
          action.execute();
        }
      });
      return null;
    }
    String hintText = getMessage(classes.length > 1, classes[0].getQualifiedName());
    return Pair.create(hintText, action);
  }

  public static String getMessage(final boolean multiple, final String name) {
    final @NonNls String messageKey = multiple ? "import.popup.multiple" : "import.popup.text";
    String hintText = QuickFixBundle.message(messageKey, name);
    hintText += " " + KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    return hintText;
  }

  private static boolean isFromDefaultPackage(PsiClass aClass) {
    //inner classes from default package _cannot_ be imported
    PsiClass containingClass = aClass.getContainingClass();
    while(containingClass != null) {
      aClass = containingClass;
      containingClass = containingClass.getContainingClass();
    }
    final String qName = aClass.getQualifiedName();
    //default package
    return qName == null || qName.indexOf('.') <= 0;
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

  protected static boolean isCaretNearRef(Editor editor, PsiElement ref) {
    TextRange range = ref.getTextRange();
    int offset = editor.getCaretModel().getOffset();

    return range.grown(1).contains(offset);
  }
}