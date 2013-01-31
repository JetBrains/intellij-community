/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.packageDependencies.DependencyRule;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author peter
 */
public abstract class ImportClassFixBase<T extends PsiElement, R extends PsiReference> implements HintAction, HighPriorityAction {
  private final T myElement;
  private final R myRef;

  protected ImportClassFixBase(@NotNull T elem, R ref) {
    myElement = elem;
    myRef = ref;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiFile file) {
    if (!myElement.isValid()) {
      return false;
    }

    PsiElement parent = myElement.getParent();
    if (parent instanceof PsiNewExpression && ((PsiNewExpression)parent).getQualifier() != null) {
      return false;
    }

    PsiManager manager = file.getManager();
    return manager.isInProject(file) && !getClassesToImport().isEmpty();
  }

  @Nullable
  protected abstract String getReferenceName(@NotNull R reference);
  protected abstract PsiElement getReferenceNameElement(@NotNull R reference);
  protected abstract boolean hasTypeParameters(@NotNull R reference);

  @NotNull
  public List<PsiClass> getClassesToImport() {
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myElement.getProject());
    String name = getReferenceName(myRef);
    GlobalSearchScope scope = myElement.getResolveScope();
    if (name == null) {
      return Collections.emptyList();
    }
    boolean referenceHasTypeParameters = hasTypeParameters(myRef);
    PsiClass[] classes = cache.getClassesByName(name, scope);
    if (classes.length == 0) return Collections.emptyList();
    List<PsiClass> classList = new ArrayList<PsiClass>(classes.length);
    boolean isAnnotationReference = myElement.getParent() instanceof PsiAnnotation;
    for (PsiClass aClass : classes) {
      if (isAnnotationReference && !aClass.isAnnotationType()) continue;
      if (JavaCompletionUtil.isInExcludedPackage(aClass, false)) continue;
      if (referenceHasTypeParameters && !aClass.hasTypeParameters()) continue;
      String qName = aClass.getQualifiedName();
      if (qName != null) { //filter local classes
        if (qName.indexOf('.') == -1) continue; //do not show classes from default package)
        if (qName.endsWith(name)) {
          if (isAccessible(aClass, myElement)) {
            classList.add(aClass);
          }
        }
      }
    }

    final String memberName = getRequiredMemberName(myElement);
    if (memberName != null) {
      List<PsiClass> filtered = ContainerUtil.findAll(classList, new Condition<PsiClass>() {
        @Override
        public boolean value(PsiClass psiClass) {
          PsiField field = psiClass.findFieldByName(memberName, true);
          if (field != null && field.hasModifierProperty(PsiModifier.STATIC) && isAccessible(field, myElement)) return true;

          PsiClass inner = psiClass.findInnerClassByName(memberName, true);
          if (inner != null && isAccessible(inner, myElement)) return true;

          for (PsiMethod method : psiClass.findMethodsByName(memberName, true)) {
            if (method.hasModifierProperty(PsiModifier.STATIC) && isAccessible(method, myElement)) return true;
          }
          return false;
        }
      });
      if (!filtered.isEmpty()) {
        classList = filtered;
      }
    }

    List<PsiClass> filtered = filterByContext(classList, myElement);
    if (!filtered.isEmpty()) {
      classList = filtered;
    }

    return classList;
  }

  @Nullable
  protected String getRequiredMemberName(T reference) {
    return null;
  }

  protected List<PsiClass> filterByContext(List<PsiClass> candidates, T ref) {
    return candidates;
  }

  protected abstract boolean isAccessible(PsiMember member, T reference);

  protected abstract String getQualifiedName(T reference);

  protected static List<PsiClass> filterAssignableFrom(PsiType type, List<PsiClass> candidates) {
    final PsiClass actualClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (actualClass != null) {
      return ContainerUtil.findAll(candidates, new Condition<PsiClass>() {
        @Override
        public boolean value(PsiClass psiClass) {
          return InheritanceUtil.isInheritorOrSelf(psiClass, actualClass, true);
        }
      });
    }
    return candidates;
  }

  public enum Result {
    POPUP_SHOWN,
    CLASS_AUTO_IMPORTED,
    POPUP_NOT_SHOWN
  }

  public Result doFix(@NotNull final Editor editor, boolean allowPopup, final boolean allowCaretNearRef) {
    List<PsiClass> classesToImport = getClassesToImport();
    if (classesToImport.isEmpty()) return Result.POPUP_NOT_SHOWN;

    try {
      String name = getQualifiedName(myElement);
      if (name != null) {
        Pattern pattern = Pattern.compile(DaemonCodeAnalyzerSettings.getInstance().NO_AUTO_IMPORT_PATTERN);
        Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
          return Result.POPUP_NOT_SHOWN;
        }
      }
    }
    catch (PatternSyntaxException e) {
      //ignore
    }
    final PsiFile psiFile = myElement.getContainingFile();
    if (classesToImport.size() > 1) {
      reduceSuggestedClassesBasedOnDependencyRuleViolation(psiFile, classesToImport);
    }
    PsiClass[] classes = classesToImport.toArray(new PsiClass[classesToImport.size()]);
    final Project project = myElement.getProject();
    CodeInsightUtil.sortIdenticalShortNameClasses(classes, myRef);

    final QuestionAction action = createAddImportAction(classes, project, editor);

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);

    boolean canImportHere = true;

    if (classes.length == 1
        && (canImportHere = canImportHere(allowCaretNearRef, editor, psiFile, classes[0].getName()))
        && (JspPsiUtil.isInJspFile(psiFile) ?
            CodeInsightSettings.getInstance().JSP_ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY :
            CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY)
        && (ApplicationManager.getApplication().isUnitTestMode() || codeAnalyzer.canChangeFileSilently(psiFile))
        && !autoImportWillInsertUnexpectedCharacters(classes[0])
        && !LaterInvocator.isInModalContext()
      ) {
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override
        public void run() {
          action.execute();
        }
      });
      return Result.CLASS_AUTO_IMPORTED;
    }

    if (allowPopup && canImportHere) {
      String hintText = ShowAutoImportPass.getMessage(classes.length > 1, classes[0].getQualifiedName());
      if (!ApplicationManager.getApplication().isUnitTestMode() && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) {
        HintManager.getInstance().showQuestionHint(editor, hintText, getStartOffset(myElement, myRef),
                                                   getEndOffset(myElement, myRef), action);
      }
      return Result.POPUP_SHOWN;
    }
    return Result.POPUP_NOT_SHOWN;
  }

  protected int getStartOffset(T element, R ref) {
    return element.getTextOffset();
  }

  protected int getEndOffset(T element, R ref) {
    return element.getTextRange().getEndOffset();
  }

  private static boolean autoImportWillInsertUnexpectedCharacters(PsiClass aClass) {
    PsiClass containingClass = aClass.getContainingClass();
    // when importing inner class, the reference might be qualified with outer class name and it can be confusing
    return containingClass != null;
  }

  private boolean canImportHere(boolean allowCaretNearRef, Editor editor, PsiFile psiFile, String exampleClassName) {
    return (allowCaretNearRef || !isCaretNearRef(editor, myRef)) &&
           !hasUnresolvedImportWhichCanImport(psiFile, exampleClassName);
  }

  protected abstract boolean isQualified(R reference);

  @Override
  public boolean showHint(final Editor editor) {
    if (isQualified(myRef)) {
      return false;
    }
    Result result = doFix(editor, true, false);
    return result == Result.POPUP_SHOWN || result == Result.CLASS_AUTO_IMPORTED;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("import.class.fix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("import.class.fix");
  }

  @Override
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

  private boolean isCaretNearRef(@NotNull Editor editor, @NotNull R ref) {
    PsiElement nameElement = getReferenceNameElement(ref);
    if (nameElement == null) return false;
    TextRange range = nameElement.getTextRange();
    int offset = editor.getCaretModel().getOffset();

    return offset == range.getEndOffset();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        List<PsiClass> classesToImport = getClassesToImport();
        PsiClass[] classes = classesToImport.toArray(new PsiClass[classesToImport.size()]);
        if (classes.length == 0) return;

        AddImportAction action = createAddImportAction(classes, project, editor);
        action.execute();
      }
    });
  }

  protected void bindReference(PsiReference reference, PsiClass targetClass) {
    reference.bindToElement(targetClass);
  }

  protected AddImportAction createAddImportAction(PsiClass[] classes, Project project, Editor editor) {
    return new AddImportAction(project, myRef, editor, classes) {
      @Override
      protected void bindReference(PsiReference ref, PsiClass targetClass) {
        ImportClassFixBase.this.bindReference(ref, targetClass);
      }
    };
  }
}
