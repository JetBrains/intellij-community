// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class InvokeTemplateAction extends DefaultActionGroup {
  private final TemplateImpl myTemplate;
  private final Editor myEditor;
  private final Project myProject;
  private final @Nullable Runnable myCallback;

  public InvokeTemplateAction(TemplateImpl template,
                              Editor editor,
                              Project project,
                              Set<? super Character> usedMnemonicsSet) {
    this(template, editor, project, usedMnemonicsSet, null);
  }

  public InvokeTemplateAction(TemplateImpl template,
                              Editor editor,
                              Project project,
                              Set<? super Character> usedMnemonicsSet,
                              @Nullable Runnable afterInvocationCallback) {
    super(extractMnemonic(template.getKey(), usedMnemonicsSet) +
          (StringUtil.isEmptyOrSpaces(template.getDescription()) ? "" : ". " + template.getDescription()),
          List.of(new EditTemplateSettingsAction(project, template),
                  new DisableTemplateSettingsAction(template)));
    myTemplate = template;
    myProject = project;
    myEditor = editor;
    myCallback = afterInvocationCallback;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setPopupGroup(true);
    e.getPresentation().setPerformGroup(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  public static @ActionText String extractMnemonic(@ActionText String caption, Set<? super Character> usedMnemonics) {
    if (StringUtil.isEmpty(caption)) return "";

    for (int i = 0; i < caption.length(); i++) {
      char c = caption.charAt(i);
      if (usedMnemonics.add(Character.toUpperCase(c))) {
        return caption.substring(0, i) + UIUtil.MNEMONIC + caption.substring(i);
      }
    }

    return caption + " ";
  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform();
  }

  public void perform() {
    final Document document = myEditor.getDocument();
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null &&
        !IntentionPreviewUtils.isIntentionPreviewActive() &&
        ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(Collections.singletonList(file)).hasReadonlyFiles()) {
      return;
    }

    CommandProcessor.getInstance().executeCommand(myProject, this::performInCommand,
                                                  CodeInsightBundle.message("command.wrap.with.template"),
                                                  "Wrap with template " + myTemplate.getKey());
  }

  public void performInCommand() {
    final Document document = myEditor.getDocument();
    myEditor.getCaretModel().runForEachCaret(__ -> {
      // adjust the selection so that it starts with a non-whitespace character (to make sure that the template is inserted
      // at a meaningful position rather than at indent 0)
      if (myEditor.getSelectionModel().hasSelection() && myTemplate.isToReformat()) {
        int offset = myEditor.getSelectionModel().getSelectionStart();
        int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();
        int lineEnd = document.getLineEndOffset(document.getLineNumber(offset));
        while (offset < lineEnd && offset < selectionEnd &&
               (document.getCharsSequence().charAt(offset) == ' ' || document.getCharsSequence().charAt(offset) == '\t')) {
          offset++;
        }
        // avoid extra line break after $SELECTION$ in case when selection ends with a complete line
        if (selectionEnd == document.getLineStartOffset(document.getLineNumber(selectionEnd))) {
          selectionEnd--;
        }
        if (offset < lineEnd && offset < selectionEnd) {  // found non-WS character in first line of selection
          myEditor.getSelectionModel().setSelection(offset, selectionEnd);
        }
      }
      String selectionString = myEditor.getSelectionModel().getSelectedText();
      TemplateManager.getInstance(myProject).startTemplate(myEditor, selectionString, myTemplate);
    });
    if (myCallback != null) {
      myCallback.run();
    }
  }

  private static final class EditTemplateSettingsAction extends AnAction {
    private final Project myProject;
    private final TemplateImpl myTemplate;

    private EditTemplateSettingsAction(Project project, TemplateImpl template) {
      //noinspection DialogTitleCapitalization
      super(CodeInsightBundle.message("action.text.edit.live.template.settings"), null, PlatformIcons.EDIT);
      myProject = project;
      myTemplate = template;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final LiveTemplatesConfigurable configurable = new LiveTemplatesConfigurable();
      ShowSettingsUtil.getInstance().editConfigurable(myProject, configurable, () -> configurable.getTemplateListPanel().editTemplate(
        myTemplate));
    }
  }

  private static final class DisableTemplateSettingsAction extends AnAction {
    private final TemplateImpl myTemplate;

    private DisableTemplateSettingsAction(TemplateImpl template) {
      //noinspection DialogTitleCapitalization
      super(CodeInsightBundle.message("action.text.disable.live.template", template.getKey()), null, AllIcons.Actions.Cancel);
      myTemplate = template;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myTemplate.setDeactivated(true);
    }
  }
}
