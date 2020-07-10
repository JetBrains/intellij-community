// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class SealClassAction extends BaseElementAtCaretIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.family.name.make.sealed");
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (aClass == null) return false;
    return isAvailable(aClass, editor);
  }

  private static boolean isAvailable(@NotNull PsiClass aClass, Editor editor) {
    if (!HighlightingFeature.SEALED_CLASSES.isAvailable(aClass)) return false;
    int offset = editor.getCaretModel().getOffset();
    PsiElement lBrace = aClass.getLBrace();
    if (lBrace == null) return false;
    if (offset >= lBrace.getTextRange().getStartOffset()) return false;
    if (aClass.hasModifierProperty(PsiModifier.SEALED)) return false;
    if (aClass.getPermitsList() != null) return false;
    if (aClass.getModifierList() == null) return false;
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) return false;
    if (PsiUtil.isLocalOrAnonymousClass(aClass)) return false;
    if (!(aClass.getContainingFile() instanceof PsiJavaFile)) return false;
    return !aClass.hasAnnotation(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (aClass == null) return;
    if (!isAvailable(aClass, editor)) return;
    PsiJavaFile parentFile = (PsiJavaFile)aClass.getContainingFile();
    if (aClass.isInterface()) {
      if (FunctionalExpressionSearch.search(aClass).findFirst() != null) {
        showError(project, editor, "intention.error.make.sealed.class.is.used.in.functional.expression");
        return;
      }
    }

    PsiJavaModule module = JavaModuleGraphUtil.findDescriptorByElement(aClass);

    List<PsiClass> inheritors = new ArrayList<>();
    Ref<String> message = new Ref<>();
    ClassInheritorsSearch.search(aClass, false).forEach(inheritor -> {
      if (PsiUtil.isLocalOrAnonymousClass(inheritor)) {
        message.set("intention.error.make.sealed.class.has.anonymous.or.local.inheritors");
        return false;
      }

      if (module == null) {
        PsiJavaFile file = tryCast(inheritor.getContainingFile(), PsiJavaFile.class);
        if (file == null) {
          message.set("intention.error.make.sealed.class.inheritors.not.in.java.file");
          return false;
        }
        if (!parentFile.getPackageName().equals(file.getPackageName())) {
          message.set("intention.error.make.sealed.class.different.packages");
          return false;
        }
      }
      else {
        if (JavaModuleGraphUtil.findDescriptorByElement(inheritor) != module) {
          message.set("intention.error.make.sealed.class.different.modules");
          return false;
        }
      }

      inheritors.add(inheritor);
      return true;
    });
    if (!message.isNull()) {
      showError(project, editor, message.get());
      return;
    }
    Set<VirtualFile> filesWithInheritors = new HashSet<>();
    filesWithInheritors.add(parentFile.getVirtualFile());
    for (PsiClass inheritor : inheritors) {
      filesWithInheritors.add(inheritor.getContainingFile().getVirtualFile());
    }
    FileModificationService.getInstance().prepareVirtualFilesForWrite(project, filesWithInheritors);
    List<String> names = ContainerUtil.map(inheritors, PsiClass::getQualifiedName);
    @PsiModifier.ModifierConstant String modifier;
    if (!names.isEmpty()) {
      modifier = PsiModifier.SEALED;
      if (shouldCreatePermitsList(inheritors, parentFile)) {
        addPermitsClause(project, aClass, names);
      }
      setInheritorsModifiers(project, inheritors);
    }
    else {
      if (aClass.isInterface()) {
        showError(project, editor, "intention.error.make.sealed.class.interface.has.no.inheritors");
        return;
      }
      else {
        modifier = PsiModifier.FINAL;
      }
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiModifierList modifierList = Objects.requireNonNull(aClass.getModifierList());
      modifierList.setModifierProperty(modifier, true);
    });
  }

  private static void showError(@NotNull Project project, Editor editor, @PropertyKey(resourceBundle = JavaBundle.BUNDLE) String message) {
    CommonRefactoringUtil.showErrorHint(project, editor, JavaBundle.message(message), getErrorTitle(), null);
  }

  public boolean shouldCreatePermitsList(List<PsiClass> inheritors, PsiFile parentFile) {
    return !inheritors.stream().allMatch(psiClass -> psiClass.getContainingFile() == parentFile);
  }

  public void setParentModifier(PsiClass aClass) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiModifierList modifierList = Objects.requireNonNull(aClass.getModifierList());
      if (modifierList.hasModifierProperty(PsiModifier.NON_SEALED)) {
        modifierList.setModifierProperty(PsiModifier.NON_SEALED, false);
      }
      modifierList.setModifierProperty(PsiModifier.SEALED, true);
    });
  }

  public void setInheritorsModifiers(@NotNull Project project, List<PsiClass> inheritors) {
    String title = JavaBundle.message("intention.make.sealed.class.task.title.set.inheritors.modifiers");
    SequentialModalProgressTask task = new SequentialModalProgressTask(project, title, true);
    task.setTask(new SequentialTask() {
      private int current = 0;
      private final int size = inheritors.size();

      @Override
      public boolean isDone() {
        return current >= size;
      }

      @Override
      public boolean iteration() {
        task.getIndicator().setFraction(((double)current) / size);
        PsiClass inheritor = inheritors.get(current);
        current++;
        PsiModifierList modifierList = inheritor.getModifierList();
        assert modifierList != null; // ensured by absence of anonymous classes
        if (modifierList.hasModifierProperty(PsiModifier.SEALED) ||
            modifierList.hasModifierProperty(PsiModifier.NON_SEALED) ||
            modifierList.hasModifierProperty(PsiModifier.FINAL)) {
          return isDone();
        }
        ApplicationManager.getApplication().runWriteAction(() -> {
          modifierList.setModifierProperty(PsiModifier.NON_SEALED, true);
        });
        return isDone();
      }
    });
    ProgressManager.getInstance().run(task);
  }

  public static void addPermitsClause(@NotNull Project project, PsiClass aClass, List<String> nonNullNames) {
    String permitsClause = StreamEx.of(nonNullNames).sorted().joining(",", "permits ", "");
    PsiReferenceList permitsList = createPermitsClause(project, permitsClause);
    PsiReferenceList implementsList = Objects.requireNonNull(aClass.getImplementsList());
    ApplicationManager.getApplication().runWriteAction(() -> {
      aClass.addAfter(permitsList, implementsList);
    });
  }

  @NotNull
  private static PsiReferenceList createPermitsClause(@NotNull Project project, String permitsClause) {
    PsiFileFactory factory = PsiFileFactory.getInstance(project);
    PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText(JavaLanguage.INSTANCE, "class __Dummy " + permitsClause + "{}");
    PsiClass newClass = javaFile.getClasses()[0];
    return Objects.requireNonNull(newClass.getPermitsList());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static String getErrorTitle() {
    return JavaBundle.message("intention.make.sealed.class.hint.title");
  }
}
