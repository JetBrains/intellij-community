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

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.*;

public class CodeStyleStatusBarWidget extends EditorBasedStatusBarPopup implements CodeStyleSettingsListener {

  public CodeStyleStatusBarWidget(@NotNull Project project) {
    super(project);
  }

  @NotNull
  @Override
  protected WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) return WidgetState.HIDDEN;
    PsiFile psiFile = getPsiFile();
    if (psiFile == null) return WidgetState.HIDDEN;
    IndentOptions indentOptions = CodeStyle.getIndentOptions(psiFile);
    IndentOptions projectIndentOptions = CodeStyle.getIndentOptionsByFileType(psiFile);
    FileIndentOptionsProvider provider = findProvider(file, indentOptions);
    if (projectIndentOptions.equals(indentOptions)) {
      if (provider == null || !provider.areActionsAvailable(file, indentOptions)) {
        return WidgetState.HIDDEN;
      }
    }
    return createWidgetState(file, indentOptions, provider);
  }

  @Nullable
  private static FileIndentOptionsProvider findProvider(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    FileIndentOptionsProvider optionsProvider = indentOptions.getFileIndentOptionsProvider();
    if (optionsProvider != null) return optionsProvider;
    for (FileIndentOptionsProvider provider : FileIndentOptionsProvider.EP_NAME.getExtensions()) {
      if (provider.areActionsAvailable(file, indentOptions)) {
        return provider;
      }
    }
    return null;
  }

  private static WidgetState createWidgetState(@NotNull VirtualFile file,
                                               @NotNull IndentOptions indentOptions,
                                               @Nullable FileIndentOptionsProvider provider) {
    String indentInfo = (provider != null
                         ? provider.getTooltip(indentOptions)
                         : FileIndentOptionsProvider.getDefaultTooltip(indentOptions));
    String tooltip = "Current indent: " + indentInfo;
    return new MyWidgetState(tooltip, indentInfo, file, indentOptions, provider);
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
        AnAction[] actions = provider.getActions(psiFile, state.getIndentOptions());
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

    private final IndentOptions myIndentOptions;
    private final FileIndentOptionsProvider myProvider;

    protected MyWidgetState(String toolTip,
                            String text,
                            @NotNull VirtualFile file,
                            @NotNull IndentOptions indentOptions,
                            @Nullable FileIndentOptionsProvider provider) {
      super(toolTip, text, provider != null && provider.areActionsAvailable(file, indentOptions));
      myIndentOptions = indentOptions;
      myProvider = provider;
    }

    public FileIndentOptionsProvider getProvider() {
      return myProvider;
    }

    public IndentOptions getIndentOptions() {
      return myIndentOptions;
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
