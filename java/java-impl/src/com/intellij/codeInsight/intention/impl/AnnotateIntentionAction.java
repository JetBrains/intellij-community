// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ClassUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AnnotateIntentionAction extends BaseIntentionAction implements LowPriorityAction {
  private static final AnnotationProvider[] PROVIDERS = {
    new DeprecationAnnotationProvider(),
    new NullableAnnotationProvider(),
    new NotNullAnnotationProvider(),
    new UnmodifiableAnnotationProvider(),
    new UnmodifiableViewAnnotationProvider()
  };
  private AnnotationProvider myAnnotationProvider;
  private boolean mySingleMode;

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.annotation.family");
  }

  private static StreamEx<AnnotationProvider> availableAnnotations(PsiModifierListOwner owner, Project project) {
    return StreamEx.of(PROVIDERS)
                   .filter(p -> p.isAvailable(owner))
                   .filter(p -> !alreadyAnnotated(owner, p, project));
  }

  /**
   * Configure the intention to annotate an element at caret in the editor with concrete annotation
   *
   * @param editor an editor
   * @param file a file
   * @param annotationShortName a short name of the annotation to add
   * @return true if specified annotation is provided and could be added to the element under caret, false otherwise
   */
  @TestOnly
  public boolean selectSingle(Editor editor, PsiFile file, String annotationShortName) {
    if (mySingleMode) {
      throw new IllegalStateException();
    }
    mySingleMode = true;
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner == null || owner.getModifierList() == null || !ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      return false;
    }
    Optional<AnnotationProvider> provider = availableAnnotations(owner, file.getProject())
      .filter(p -> StringUtil.getShortName(p.getName(file.getProject())).equals(annotationShortName))
      .collect(MoreCollectors.onlyOne());
    myAnnotationProvider = provider.orElse(null);
    return provider.isPresent();
  }

  private static boolean alreadyAnnotated(PsiModifierListOwner owner, AnnotationProvider p, Project project) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, ArrayUtil
      .prepend(p.getName(owner.getProject()), p.getAnnotationsToRemove(project)));
    return annotation != null && !AnnotationUtil.isInferredAnnotation(annotation);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner == null || owner.getModifierList() == null || !ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      return false;
    }
    if (mySingleMode) {
      return myAnnotationProvider != null && availableAnnotations(owner, project).has(myAnnotationProvider);
    }
    List<AnnotationProvider> annotations = availableAnnotations(owner, project).limit(2).collect(Collectors.toList());
    if (annotations.isEmpty()) return false;
    if (annotations.size() == 1) {
      myAnnotationProvider = annotations.get(0);
      setText(AddAnnotationPsiFix.calcText(owner, myAnnotationProvider.getName(project)));
    }
    else {
      myAnnotationProvider = null;
      setText(AddAnnotationPsiFix.calcText(owner, null));
    }
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    assert owner != null;
    if (myAnnotationProvider != null) {
      if (alreadyAnnotated(owner, myAnnotationProvider, project)) return;
      AddAnnotationFix fix =
        new AddAnnotationFix(myAnnotationProvider.getName(project), owner, myAnnotationProvider.getAnnotationsToRemove(project));
      fix.invoke(project, editor, file);
    }
    else {
      List<AnnotationProvider> annotations = availableAnnotations(owner, project).collect(Collectors.toList());
      if (annotations.isEmpty()) return;
      JBPopupFactory.getInstance().createListPopup(
        new BaseListPopupStep<AnnotationProvider>(CodeInsightBundle.message("annotate.intention.chooser.title"), annotations) {
          @Override
          public PopupStep onChosen(final AnnotationProvider selectedValue, final boolean finalChoice) {
            return doFinalStep(() -> {
              new AddAnnotationFix(selectedValue.getName(project), owner, selectedValue.getAnnotationsToRemove(project)).invoke(project, editor, file);
            });
          }

          @Override
          @NotNull
          public String getTextFor(final AnnotationProvider value) {
            return value.getName(project);
          }
        }).showInBestPositionFor(editor);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  interface AnnotationProvider {
    @NotNull
    String getName(Project project);

    boolean isAvailable(PsiModifierListOwner owner);

    @NotNull
    default String[] getAnnotationsToRemove(Project project) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  static class DeprecationAnnotationProvider implements AnnotationProvider {
    @NotNull
    @Override
    public String getName(Project project) {
      return CommonClassNames.JAVA_LANG_DEPRECATED;
    }

    @Override
    public boolean isAvailable(PsiModifierListOwner owner) {
      return true;
    }
  }

  static class NullableAnnotationProvider implements AnnotationProvider {
    @NotNull
    @Override
    public String getName(Project project) {
      return NullableNotNullManager.getInstance(project).getDefaultNullable();
    }

    @Override
    public boolean isAvailable(PsiModifierListOwner owner) {
      return AddAnnotationPsiFix.isNullabilityAnnotationApplicable(owner);
    }

    @NotNull
    @Override
    public String[] getAnnotationsToRemove(Project project) {
      return NullableNotNullManager.getInstance(project).getNotNulls().toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    }
  }

  static class NotNullAnnotationProvider implements AnnotationProvider {
    @NotNull
    @Override
    public String getName(Project project) {
      return NullableNotNullManager.getInstance(project).getDefaultNotNull();
    }

    @Override
    public boolean isAvailable(PsiModifierListOwner owner) {
      return AddAnnotationPsiFix.isNullabilityAnnotationApplicable(owner);
    }

    @NotNull
    @Override
    public String[] getAnnotationsToRemove(Project project) {
      return NullableNotNullManager.getInstance(project).getNullables().toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    }
  }

  static class UnmodifiableAnnotationProvider implements AnnotationProvider {

    @NotNull
    @Override
    public String getName(Project project) {
      return Mutability.UNMODIFIABLE_ANNOTATION;
    }

    @Override
    public boolean isAvailable(PsiModifierListOwner owner) {
      return ApplicationManagerEx.getApplicationEx().isInternal() &&
             owner instanceof PsiMethod &&
             !ClassUtils.isImmutable(((PsiMethod)owner).getReturnType());
    }

    @NotNull
    @Override
    public String[] getAnnotationsToRemove(Project project) {
      return new String[]{Mutability.UNMODIFIABLE_VIEW_ANNOTATION};
    }
  }

  static class UnmodifiableViewAnnotationProvider implements AnnotationProvider {

    @NotNull
    @Override
    public String getName(Project project) {
      return Mutability.UNMODIFIABLE_VIEW_ANNOTATION;
    }

    @Override
    public boolean isAvailable(PsiModifierListOwner owner) {
      return ApplicationManagerEx.getApplicationEx().isInternal() &&
             owner instanceof PsiMethod &&
             !ClassUtils.isImmutable(((PsiMethod)owner).getReturnType());
    }

    @NotNull
    @Override
    public String[] getAnnotationsToRemove(Project project) {
      return new String[]{Mutability.UNMODIFIABLE_ANNOTATION};
    }
  }
}