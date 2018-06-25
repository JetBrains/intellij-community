// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.statusbar;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleStatusBarWidget extends EditorBasedStatusBarPopup implements CodeStyleSettingsListener {

  public CodeStyleStatusBarWidget(@NotNull Project project) {
    super(project);
  }

  @NotNull
  @Override
  protected WidgetState getWidgetState(@Nullable VirtualFile file) {
    PsiFile psiFile = getPsiFile();
    if (psiFile == null) return WidgetState.HIDDEN;
    CodeStyleSettings.IndentOptions indentOptions = CodeStyle.getIndentOptions(psiFile);
    CodeStyleSettings.IndentOptions projectIndentOptions = CodeStyle.getIndentOptionsByFileType(psiFile);
    if (projectIndentOptions.equals(indentOptions)) {
      return WidgetState.HIDDEN;
    }
    return createWidgetState(indentOptions);
  }

  private static WidgetState createWidgetState(@NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    String tooltip = "Current indent options: " + indentOptions.INDENT_SIZE;
    StringBuilder messageBuilder = new StringBuilder();
    boolean areActionsAvailable = false;
    messageBuilder.append("Indent: ");
    if (indentOptions.USE_TAB_CHARACTER) {
      messageBuilder.append("Tab");
    }
    else {
      messageBuilder.append(indentOptions.INDENT_SIZE);
    }
    String hint = indentOptions.getHint();
    if (hint != null) {
      messageBuilder.append(" (").append(hint).append(")");
      areActionsAvailable = true;
    }
    return new MyWidgetState(tooltip, messageBuilder.toString(), areActionsAvailable, indentOptions.getFileIndentOptionsProvider());
  }

  @Nullable
  private PsiFile getPsiFile()  {
    Editor editor = getEditor();
    Project project = getProject();
    if (editor != null && project != null) {
      Document document = editor.getDocument();
      return PsiDocumentManager.getInstance(project).getPsiFile(document);
    }
    return null;
  }

  @Nullable
  @Override
  protected ListPopup createPopup(DataContext context)
  {
    MyWidgetState state = (MyWidgetState)getWidgetState(context.getData(CommonDataKeys.VIRTUAL_FILE));
    Editor editor = getEditor();
    PsiFile psiFile = getPsiFile();
    if (state != WidgetState.HIDDEN && editor != null && psiFile != null) {
      FileIndentOptionsProvider provider = state.getProvider();
      if (provider != null) {
        AnAction[] actions = provider.getActions(psiFile);
        if (actions != null) {
          ActionGroup actionGroup = new ActionGroup() {
            @NotNull
            @Override
            public AnAction[] getChildren(@Nullable AnActionEvent e) {
              return actions;
            }
          };
          return JBPopupFactory.getInstance().createActionGroupPopup(
            "Indent", actionGroup, context,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
        }
      }
    }
    return null;
  }

  @Override
  protected void registerCustomListeners() {
    CodeStyleSettingsManager.getInstance(getProject()).addListener(this);
  }

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    update();
  }

  @NotNull
  @Override
  protected StatusBarWidget createInstance(Project project) {
    return new CodeStyleStatusBarWidget(project);
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    //myIndentOptions = null;
    update();
  }


  @NotNull
  @Override
  public String ID() {
    return CodeStyleStatusBarWidget.class.getName();
  }

  private static class MyWidgetState extends WidgetState {

    private final FileIndentOptionsProvider myProvider;

    protected MyWidgetState(String toolTip, String text, boolean actionEnabled, @Nullable FileIndentOptionsProvider provider) {
      super(toolTip, text, actionEnabled);
      myProvider = provider;
    }

    public FileIndentOptionsProvider getProvider() {
      return myProvider;
    }
  }

  @Override
  public void dispose() {
    if (!myProject.isDisposed()) {
      CodeStyleSettingsManager.getInstance(myProject).removeListener(this);
    }
    super.dispose();
  }
}
