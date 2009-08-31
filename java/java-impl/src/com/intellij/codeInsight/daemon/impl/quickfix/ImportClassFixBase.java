package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.HintAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.DependencyRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

/**
 * @author peter
 */
public abstract class ImportClassFixBase<T extends PsiElement & PsiReference> implements HintAction {
  private final T myRef;

  protected ImportClassFixBase(T ref) {
    myRef = ref;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myRef.isValid() && file.getManager().isInProject(file) && !getClassesToImport().isEmpty();
  }

  @Nullable
  protected abstract String getReferenceName(T reference);

  protected abstract boolean hasTypeParameters(T reference);

  public List<PsiClass> getClassesToImport() {
    PsiManager manager = PsiManager.getInstance(myRef.getProject());
    PsiShortNamesCache cache = JavaPsiFacade.getInstance(manager.getProject()).getShortNamesCache();
    String name = getReferenceName(myRef);
    GlobalSearchScope scope = myRef.getResolveScope();
    if (name == null) {
      return Collections.emptyList();
    }
    boolean referenceHasTypeParameters = hasTypeParameters(myRef);
    PsiClass[] classes = cache.getClassesByName(name, scope);
    if (classes.length == 0) return Collections.emptyList();
    ArrayList<PsiClass> classList = new ArrayList<PsiClass>(classes.length);
    boolean isAnnotationReference = myRef.getParent() instanceof PsiAnnotation;
    for (PsiClass aClass : classes) {
      if (isAnnotationReference && !aClass.isAnnotationType()) continue;
      if (JavaCompletionUtil.isInExcludedPackage(aClass)) continue;
      if (referenceHasTypeParameters && !aClass.hasTypeParameters()) continue;
      PsiFile file = aClass.getContainingFile();
      if (file instanceof PsiClassOwner && ((PsiClassOwner)file).getPackageName().length() == 0) { //do not show classes from default package
        continue;
      }
      String qName = aClass.getQualifiedName();
      if (qName != null) { //filter local classes
        if (qName.endsWith(name)) {
          if (isAccessible(aClass, myRef)) {
            classList.add(aClass);
          }
        }
      }
    }
    return classList;
  }

  protected abstract boolean isAccessible(PsiClass aClass, T reference);

  protected abstract String getQualifiedName(T reference);

  public boolean doFix(@NotNull final Editor editor, boolean doShow, final boolean allowCaretNearRef) {
    List<PsiClass> classesToImport = getClassesToImport();
    if (classesToImport.isEmpty()) return false;

    try {
      String name = getQualifiedName(myRef);
      if (name != null) {
        Pattern pattern = Pattern.compile(DaemonCodeAnalyzerSettings.getInstance().NO_AUTO_IMPORT_PATTERN);
        Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
          return false;
        }
      }
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

    final QuestionAction action = createAddImportAction(classes, project, editor, myRef);

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);

    if (classes.length == 1
        && CodeStyleSettingsManager.getSettings(project).ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY
        && (allowCaretNearRef || !isCaretNearRef(editor, myRef))
        && !JspPsiUtil.isInJspFile(psiFile)
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

  protected abstract boolean isQualified(T reference);

  public boolean showHint(final Editor editor) {
    if (isQualified(myRef)) return false;
    return doFix(editor, true, false);
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("import.class.fix");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("import.class.fix");
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected abstract boolean hasUnresolvedImportWhichCanImport(PsiFile psiFile, String name);

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

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        List<PsiClass> classesToImport = getClassesToImport();
        PsiClass[] classes = classesToImport.toArray(new PsiClass[classesToImport.size()]);
        CodeInsightUtil.sortIdenticalShortNameClasses(classes, file);
        if (classes.length == 0) return;

        AddImportAction action = createAddImportAction(classes, project, editor, myRef);
        action.execute();
      }
    });
  }

  protected void bindReference(T reference, PsiClass targetClass) {
    reference.bindToElement(targetClass);
  }

  protected AddImportAction createAddImportAction(PsiClass[] classes, Project project, Editor editor, T reference) {
    return new AddImportAction(project, myRef, editor, classes) {
      @Override
      protected void bindReference(PsiReference ref, PsiClass targetClass) {
        ImportClassFixBase.this.bindReference((T)ref, targetClass);
      }
    };
  }

}
