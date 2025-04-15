// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HighlightOverridingMethodsHandler extends HighlightUsagesHandlerBase<PsiClass> {
  private final PsiElement myTarget;
  private final PsiClass myClass;

  public HighlightOverridingMethodsHandler(final Editor editor, final PsiFile file, final PsiElement target, final PsiClass psiClass) {
    super(editor, file);
    myTarget = target;
    myClass = psiClass;
  }

  @Override
  public @NotNull List<PsiClass> getTargets() {
    PsiReferenceList list = JavaKeywords.EXTENDS.equals(myTarget.getText()) ? myClass.getExtendsList() : myClass.getImplementsList();
    if (list == null) return Collections.emptyList();
    final PsiClassType[] classTypes = list.getReferencedTypes();
    return ChooseClassAndDoHighlightRunnable.resolveClasses(classTypes);
  }

  @Override
  protected void selectTargets(final @NotNull List<? extends PsiClass> targets, final @NotNull Consumer<? super List<? extends PsiClass>> selectionConsumer) {
    new ChooseClassAndDoHighlightRunnable(targets, myEditor, JavaBundle.message("highlight.overridden.classes.chooser.title")) {
      @Override
      protected void selected(PsiClass @NotNull ... classes) {
        selectionConsumer.consume(Arrays.asList(classes));
      }
    }.run();
  }

  @Override
  public void computeUsages(final @NotNull List<? extends PsiClass> classes) {
    for (PsiMethod method : myClass.getMethods()) {
      List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
      for (HierarchicalMethodSignature superSignature : superSignatures) {
        PsiClass containingClass = superSignature.getMethod().getContainingClass();
        if (containingClass == null) continue;
        for (PsiClass classToAnalyze : classes) {
          if (InheritanceUtil.isInheritorOrSelf(classToAnalyze, containingClass, true)) {
            PsiIdentifier identifier = method.getNameIdentifier();
            if (identifier != null) {
              addOccurrence(identifier);
              break;
            }
          }
        }
      }
    }
    if (myReadUsages.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      String name;
      if (classes.size() == 1) {
        final ItemPresentation presentation = classes.get(0).getPresentation();
        name = presentation != null ? presentation.getPresentableText() : "";
      }
      else {
        name = "";
      }
      myHintText = JavaBundle.message("no.methods.overriding.0.are.found", classes.size(), name);
    }
    else {
      addOccurrence(myTarget);
      final int methodCount = myReadUsages.size()-1;  // exclude 'target' keyword
      myStatusText = JavaBundle.message("status.bar.overridden.methods.highlighted.message", methodCount,
                                                                        HighlightUsagesHandler.getShortcutText());
    }
  }

  @Override
  public @Nullable String getFeatureId() {
    return "codeassists.highlight.implements";
  }
}
