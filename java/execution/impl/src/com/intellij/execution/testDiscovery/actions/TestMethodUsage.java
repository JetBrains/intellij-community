// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.ide.SelectInEditorManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
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
import java.util.Collections;

class TestMethodUsage implements Usage, UsageInFile, UsageInModule, PsiElementUsage, DataProvider {
  private final DiscoveredTestsTreeModel.Node.Method myNode;

  TestMethodUsage(DiscoveredTestsTreeModel.Node.Method node) {myNode = node;}

  @Override
  public VirtualFile getFile() {
    return myNode.getPointer().getVirtualFile();
  }

  @Override
  public Module getModule() {
    return ModuleUtilCore.findModuleForFile(myNode.getPointer().getContainingFile());
  }

  @NotNull
  @Override
  public UsagePresentation getPresentation() {
    return new UsagePresentation() {
      @NotNull
      @Override
      public TextChunk[] getText() {
        return new TextChunk[]{new TextChunk(SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes(), getPlainText())};
      }

      @NotNull
      @Override
      public String getPlainText() {
        return myNode.getName();
      }

      @Override
      public Icon getIcon() {
        return myNode.getIcon();
      }

      @Override
      public String getTooltipText() {
        return getPlainText();
      }
    };
  }

  @Override
  public boolean isValid() {
    return myNode.getPointer().getElement() != null;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Nullable
  @Override
  public FileEditorLocation getLocation() {
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;
    Project project = myNode.getPointer().getProject();
    FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
    if (!(editor instanceof TextEditor)) return null;

    Segment segment = myNode.getPointer().getPsiRange();
    if (segment == null) return null;
    return new TextEditorLocation(segment.getStartOffset(), (TextEditor)editor);
  }

  @Override
  public void selectInEditor() {
    navigate(true);
  }

  @Override
  public void highlightInEditor() {
    PsiMethod element = getElement();
    if (element != null) {
      Project project = myNode.getPointer().getProject();
      TextRange range = element.getTextRange();
      SelectInEditorManager.getInstance(project).selectInEditor(getFile(), range.getStartOffset(), range.getEndOffset(), false, false);
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    PsiMethod element = getElement();
    if (element != null) {
      element.navigate(requestFocus);
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

  @Nullable
  @Override
  public PsiMethod getElement() {
    return myNode.getPointer().getElement();
  }

  @Override
  public boolean isNonCodeUsage() {
    return false;
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (!UsageView.USAGE_INFO_LIST_KEY.is(dataId)) return null;
    PsiMethod method = getElement();
    return method == null ? null : Collections.singletonList(new UsageInfo(method));
  }
}
