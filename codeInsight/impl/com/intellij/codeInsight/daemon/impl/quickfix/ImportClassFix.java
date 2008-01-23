/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 8:49:24 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ImportClassFix implements HintAction {

  private final PsiJavaCodeReferenceElement myRef;

  public ImportClassFix(PsiJavaCodeReferenceElement element) {
    myRef = element;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("import.class.fix");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("import.class.fix");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (myRef == null || !myRef.isValid()) return false;
    if (!file.getManager().isInProject(file)) return false;
    List<PsiClass> classesToImport = getClassesToImport();
    return !classesToImport.isEmpty();
  }

  private List<PsiClass> getClassesToImport() {
    PsiManager manager = PsiManager.getInstance(myRef.getProject());
    PsiShortNamesCache cache = JavaPsiFacade.getInstance(manager.getProject()).getShortNamesCache();
    String name = myRef.getReferenceName();
    GlobalSearchScope scope = myRef.getResolveScope();
    if (name == null) {
      return Collections.emptyList();
    }
    PsiClass[] classes = cache.getClassesByName(name, scope);
    ArrayList<PsiClass> classList = new ArrayList<PsiClass>();
    boolean isAnnotationReference = myRef.getParent() instanceof PsiAnnotation;
    for (PsiClass aClass : classes) {
      if (isAnnotationReference && !aClass.isAnnotationType()) continue;
      if (CompletionUtil.isInExcludedPackage(aClass)) continue;
      PsiFile file = aClass.getContainingFile();
      if (file instanceof PsiJavaFile && ((PsiJavaFile)file).getPackageName().length() == 0) { //do not show classes from default package
        continue;
      }
      String qName = aClass.getQualifiedName();
      if (qName != null) { //filter local classes
        if (qName.endsWith(name)) {
          if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            classList.add(aClass);
          }
        }
      }
    }
    return classList;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        List<PsiClass> classesToImport = getClassesToImport();
        PsiClass[] classes = classesToImport.toArray(new PsiClass[classesToImport.size()]);
        CodeInsightUtil.sortIdenticalShortNameClasses(classes, file);
        if (classes.length == 0) return;

        AddImportAction action = new AddImportAction(project, myRef, editor, classes);
        action.execute();
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

  public boolean showHint(final Editor editor) {

    return doFix(editor, true, false);
  }

  public boolean doFix(@NotNull final Editor editor, boolean doShow, final boolean allowCaretNearRef) {
    List<PsiClass> classesToImport = getClassesToImport();
    if (classesToImport.isEmpty()) {
      return false;
    }
    try {
      String name = myRef.getQualifiedName();
      Pattern pattern = Pattern.compile(DaemonCodeAnalyzerSettings.getInstance().NO_AUTO_IMPORT_PATTERN);
      Matcher matcher = pattern.matcher(name);
      if (matcher.matches()) return false;
    }
    catch (PatternSyntaxException e) {
      //ignore
    }
    final PsiFile psiFile = myRef.getContainingFile();
    if (classesToImport.size() > 1) {
      reduceSuggestedClassesBasedOnDependencyRuleViolation(psiFile, classesToImport);
    }
    PsiClass[] classes = classesToImport.toArray(new PsiClass[classesToImport.size()]);
    final Project project = myRef.getProject();
    CodeInsightUtil.sortIdenticalShortNameClasses(classes, psiFile);

    final QuestionAction action = new AddImportAction(project, myRef, editor, classes);

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);

    if (classes.length == 1
        && CodeStyleSettingsManager.getSettings(project).ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY
        && (allowCaretNearRef || !isCaretNearRef(editor, myRef)) 
        && !PsiUtil.isInJspFile(psiFile)
        && codeAnalyzer.canChangeFileSilently(psiFile)
        && !hasUnresolvedImportWhichCanImport(psiFile, classes[0].getName())) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        public void run() {
          action.execute();
        }
      });
      return false;
    }
    if (doShow) {
      String hintText = ShowAutoImportPass.getMessage(classes.length > 1, classes[0].getQualifiedName());
      HintManager.getInstance().showQuestionHint(editor, hintText, myRef.getTextOffset(), myRef.getTextRange().getEndOffset(), action);
    }
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

  private static boolean isCaretNearRef(Editor editor, PsiElement ref) {
    TextRange range = ref.getTextRange();
    int offset = editor.getCaretModel().getOffset();

    return range.grown(1).contains(offset);
  }

}
