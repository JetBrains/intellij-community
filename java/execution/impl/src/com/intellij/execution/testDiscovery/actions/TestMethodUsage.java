// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.ide.SelectInEditorManager;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsagePresentation;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.usages.rules.UsageInModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

class TestMethodUsage implements Usage, UsageInFile, UsageInModule, PsiElementUsage, UiDataProvider {
  private final @NotNull SmartPsiElementPointer<? extends PsiMethod> myTestMethodPointer;
  private final @Nullable SmartPsiElementPointer<? extends PsiClass> myTestClassPointer;

  TestMethodUsage(@NotNull SmartPsiElementPointer<? extends PsiMethod> testMethod,
                  @NotNull SmartPsiElementPointer<? extends PsiClass> testClass,
                  @NotNull Collection<String> parameters) {
    myTestMethodPointer = testMethod;
    myTestClassPointer = parameters.isEmpty() ? null : testClass;
  }

  public @Nullable Location<PsiMethod> calculateLocation() {
    PsiMethod m = myTestMethodPointer.getElement();
    if (m == null) return null;
    PsiClass c = myTestClassPointer == null ? m.getContainingClass() : myTestClassPointer.getElement();
    if (c == null) return null;
    return MethodLocation.elementInClass(m, c);
  }

  @Override
  public VirtualFile getFile() {
    return getPointer().getVirtualFile();
  }

  @Override
  public Module getModule() {
    return ModuleUtilCore.findModuleForFile(getPointer().getContainingFile());
  }

  @Override
  public @NotNull UsagePresentation getPresentation() {
    return new UsagePresentation() {
      @Override
      public TextChunk @NotNull [] getText() {
        return new TextChunk[]{new TextChunk(SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes(), getPlainText())};
      }

      @Override
      public @NotNull @NlsSafe String getPlainText() {
        PsiMember element = getElement();
        if (element == null) return "";
        return StringUtil.notNullize(element.getName());
      }

      @Override
      public Icon getIcon() {
        PsiMember element = getElement();
        if (element == null) return null;
        return element.getIcon(0);
      }

      @Override
      public String getTooltipText() {
        return getPlainText();
      }
    };
  }

  @Override
  public boolean isValid() {
    return getElement() != null;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public @Nullable FileEditorLocation getLocation() {
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;
    Project project = getPointer().getProject();
    FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
    if (!(editor instanceof TextEditor)) return null;

    Segment segment = getPointer().getPsiRange();
    if (segment == null) return null;
    return new TextEditorLocation(segment.getStartOffset(), (TextEditor)editor);
  }

  @Override
  public void selectInEditor() {
    navigate(true);
  }

  @Override
  public void highlightInEditor() {
    PsiElement element = getElement();
    if (element != null) {
      Project project = getPointer().getProject();
      TextRange range = element.getTextRange();
      SelectInEditorManager.getInstance(project).selectInEditor(getFile(), range.getStartOffset(), range.getEndOffset(), false, false);
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    PsiElement element = getElement();
    if (element != null) {
      ((Navigatable)element).navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return true;
  }

  @Override
  public @Nullable PsiMember getElement() {
    return getPointer().getElement();
  }

  private @NotNull SmartPsiElementPointer<? extends PsiMember> getPointer() {
    return myTestClassPointer != null ? myTestClassPointer : myTestMethodPointer;
  }

  @Override
  public int getNavigationOffset() {
    Segment segment = getPointer().getPsiRange();
    if (segment == null) return -1;
    return segment.getStartOffset();
  }

  @Override
  public boolean isNonCodeUsage() {
    return false;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.lazy(UsageView.USAGE_INFO_LIST_KEY, () -> {
      PsiElement psi = getElement();
      return psi == null ? null : Collections.singletonList(new UsageInfo(psi));
    });
  }
}
