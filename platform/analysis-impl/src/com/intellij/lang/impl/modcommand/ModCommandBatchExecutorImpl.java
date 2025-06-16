// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.options.*;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.*;
import com.intellij.modcommand.ModUpdateFileText.Fragment;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static com.intellij.openapi.util.text.HtmlChunk.tag;
import static com.intellij.openapi.util.text.HtmlChunk.text;

/**
 * An {@link ModCommandExecutor} service implementation that supports batch execution only. 
 * Interactive execution request executes like in batch. 
 */
@ApiStatus.Internal
public class ModCommandBatchExecutorImpl implements ModCommandExecutor {
  /**
   * {@inheritDoc}
   * 
   * @implNote this implementation executes the action like in batch, without displaying any UI.
   * 
   * @param context current context
   * @param command a command to execute
   * @param editor ignored by this implementation, can be null
   */
  @Override
  public void executeInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor) {
    BatchExecutionResult result = executeInBatch(context, command);
    if (result == Result.INTERACTIVE || result == Result.CONFLICTS) {
      throw new UnsupportedOperationException(result.getMessage());
    }
    if (result instanceof Error error) {
      throw new RuntimeException(error.getMessage());
    }
  }

  @Override
  public @NotNull BatchExecutionResult executeInBatch(@NotNull ActionContext context, @NotNull ModCommand command) {
    if (!ensureWritable(context.project(), command)) {
      return new Error(AnalysisBundle.message("modcommand.executor.error.files.are.marked.as.readonly"));
    }
    return doExecuteInBatch(context, command);
  }

  private BatchExecutionResult doExecuteInBatch(@NotNull ActionContext context, @NotNull ModCommand command) {
    Project project = context.project();
    if (command.isEmpty()) {
      return Result.NOTHING;
    }
    if (command instanceof ModUpdateFileText upd) {
      return executeUpdate(project, upd) ? Result.SUCCESS : Result.ABORT;
    }
    if (command instanceof ModCreateFile create) {
      String message = executeCreate(project, create);
      return message == null ? Result.SUCCESS : new Error(message);
    }
    if (command instanceof ModDeleteFile deleteFile) {
      String message = executeDelete(deleteFile);
      return message == null ? Result.SUCCESS : new Error(message);
    }
    if (command instanceof ModMoveFile moveFile) {
      String message = executeMove(moveFile);
      return message == null ? Result.SUCCESS : new Error(message);
    }
    if (command instanceof ModCompositeCommand cmp) {
      BatchExecutionResult result = Result.NOTHING;
      for (ModCommand subCommand : cmp.commands()) {
        result = result.compose(doExecuteInBatch(context, subCommand));
        if (result == Result.ABORT || result instanceof Error) break;
      }
      return result;
    }
    if (command instanceof ModChooseAction chooser) {
      return executeChooseInBatch(context, chooser);
    }
    if (command instanceof ModNavigate || command instanceof ModHighlight ||
        command instanceof ModCopyToClipboard || command instanceof ModStartRename ||
        command instanceof ModStartTemplate || command instanceof ModUpdateSystemOptions ||
        command instanceof ModUpdateReferences || command instanceof ModOpenUrl) {
      return Result.INTERACTIVE;
    }
    if (command instanceof ModShowConflicts) {
      return Result.CONFLICTS;
    }
    if (command instanceof ModEditOptions<?> editOptions) {
      return bypassEditOptions(editOptions, context);
    }
    if (command instanceof ModDisplayMessage message) {
      if (message.kind() == ModDisplayMessage.MessageKind.ERROR) {
        return new Error(message.messageText());
      }
      return Result.INTERACTIVE;
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  private <T extends OptionContainer> BatchExecutionResult bypassEditOptions(@NotNull ModEditOptions<T> options, @NotNull ActionContext context) {
    if (!options.canUseDefaults()) return Result.INTERACTIVE;
    return doExecuteInBatch(context, options.nextCommand().apply(options.containerSupplier().get()));
  }

  private BatchExecutionResult executeChooseInBatch(@NotNull ActionContext context, ModChooseAction chooser) {
    ModCommandAction action = StreamEx.of(chooser.actions()).filter(act -> act.getPresentation(context) != null)
      .findFirst().orElse(null);
    if (action == null) return Result.NOTHING;

    String name = chooser.title();
    ModCommand next = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.nonBlocking(() -> {
        if (action.getPresentation(context) == null) return null;
        return action.perform(context);
      }).executeSynchronously(), name, true, context.project());
    if (next == null) return Result.ABORT;
    return executeInBatch(context, next);
  }

  protected @Nls String executeDelete(@NotNull ModDeleteFile file) {
    try {
      WriteAction.run(() -> file.file().delete(this));
      return null;
    }
    catch (IOException e) {
      return e.getLocalizedMessage();
    }
  }

  protected @Nls String executeMove(@NotNull ModMoveFile file) {
    VirtualFile source = actualize(file.file());
    FutureVirtualFile target = file.targetFile();
    VirtualFile parent = actualize(target.getParent());
    return WriteAction.compute(() -> {
      VirtualFile origParent = source.getParent();
      if (parent != null && !parent.equals(origParent)) {
        try {
          source.move(this, parent);
        }
        catch (IOException e) {
          return AnalysisBundle.message("modcommand.executor.cannot.move.file",
                                        source.getPath(), parent.getPath(), e.getLocalizedMessage());
        }
      }
      if (!target.getName().equals(source.getName())) {
        try {
          source.rename(this, target.getName());
        }
        catch (IOException e) {
          if (origParent != null && !origParent.equals(parent)) {
            try {
              // Try to rollback 'move' in case if rename failed
              source.move(this, origParent);
            }
            catch (IOException ignored) {
              // Ignore move exception 
            }
          }
          return AnalysisBundle.message("modcommand.executor.cannot.rename.file",
                                        source.getPath(), target.getName(), e.getLocalizedMessage());
        }
      }
      return null;
    });
  }

  protected @Nls String executeCreate(@NotNull Project project, @NotNull ModCreateFile create) {
    FutureVirtualFile file = create.file();
    VirtualFile parent = actualize(file.getParent());
    try {
      return WriteAction.compute(() -> {
        if (create.content() instanceof ModCreateFile.Directory) {
          parent.createChildDirectory(this, file.getName());
          return null;
        }
        VirtualFile newFile = parent.createChildData(this, file.getName());
        if (create.content() instanceof ModCreateFile.Text text) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(newFile);
          if (psiFile == null) return AnalysisBundle.message("modcommand.executor.unable.to.find.the.new.file", file.getName());
          Document document = psiFile.getViewProvider().getDocument();
          document.setText(text.text());
          PsiDocumentManager.getInstance(project).commitDocument(document);
        }
        else if (create.content() instanceof ModCreateFile.Binary binary) {
          newFile.setBinaryContent(binary.bytes());
        }
        return null;
      });
    }
    catch (IOException e) {
      return e.getLocalizedMessage();
    }
  }

  @Override
  public void executeForFileCopy(@NotNull ModCommand command, @NotNull PsiFile file) {
    for (ModCommand cmd : command.unpack()) {
      if (cmd instanceof ModUpdateFileText updateFileText) {
        if (!updateFileText.file().equals(file.getOriginalFile().getVirtualFile())) {
          throw new UnsupportedOperationException("The command updates non-current file");
        }
        updateText(file.getProject(), file.getViewProvider().getDocument(), updateFileText);
      }
      else if (!(cmd instanceof ModNavigate) && !(cmd instanceof ModHighlight)) {
        throw new UnsupportedOperationException("Unexpected command: " + command);
      }
    }
  }

  protected static boolean ensureWritable(@NotNull Project project, @NotNull ModCommand command) {
    Collection<VirtualFile> files = ContainerUtil.filter(command.modifiedFiles(), f -> !(f instanceof LightVirtualFile));
    return files.isEmpty() || ReadonlyStatusHandler.ensureFilesWritable(project, files.toArray(VirtualFile.EMPTY_ARRAY));
  }

  protected static VirtualFile actualize(@NotNull VirtualFile file) {
    return file instanceof FutureVirtualFile future
           ? actualize(future.getParent()).findChild(future.getName())
           : file;
  }

  private void updateText(@NotNull Project project, @NotNull Document document, @NotNull ModUpdateFileText upd)
    throws IllegalStateException {
    String oldText = upd.oldText();
    if (!document.getText().equals(oldText)) {
      throw new IllegalStateException("Old text doesn't match");
    }
    List<@NotNull Fragment> ranges = calculateRanges(upd);
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    applyRanges(document, ranges, upd.newText());
    manager.commitDocument(document);
  }

  protected boolean executeUpdate(@NotNull Project project, @NotNull ModUpdateFileText upd) {
    VirtualFile file = upd.file();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return false;
    String oldText = upd.oldText();
    if (!document.getText().equals(oldText)) return false;
    List<@NotNull Fragment> ranges = calculateRanges(upd);
    return WriteAction.compute(() -> {
      applyRanges(document, ranges, upd.newText());
      PsiDocumentManager.getInstance(project).commitDocument(document);
      return true;
    });
  }

  private static void applyRanges(@NotNull Document document, List<@NotNull Fragment> ranges, String newText) {
    for (Fragment range : ranges) {
      document.replaceString(range.offset(), range.offset() + range.oldLength(),
                             newText.substring(range.offset(), range.offset() + range.newLength()));
    }
  }

  protected @Unmodifiable @NotNull List<@NotNull Fragment> calculateRanges(@NotNull ModUpdateFileText upd) {
    return List.of(new Fragment(0, upd.oldText().length(), upd.newText().length()));
  }

  @Override
  public @NotNull IntentionPreviewInfo getPreview(@NotNull ModCommand modCommand, @NotNull ActionContext context) {
    Project project = context.project();
    PsiFile file = context.file();
    List<IntentionPreviewInfo.CustomDiff> customDiffList = new ArrayList<>();
    IntentionPreviewInfo navigateInfo = IntentionPreviewInfo.EMPTY;
    List<@NlsSafe String> createdDirs = new ArrayList<>();
    List<HtmlChunk> fsActions = new ArrayList<>();
    for (ModCommand command : modCommand.unpack()) {
      if (command instanceof ModUpdateFileText modFile) {
        VirtualFile vFile = modFile.file();
        var currentFile =
          vFile.equals(file.getOriginalFile().getVirtualFile()) ||
          vFile.equals(InjectedLanguageManager.getInstance(project).getTopLevelFile(file).getOriginalFile().getVirtualFile());
        customDiffList.add(new IntentionPreviewInfo.CustomDiff(vFile.getFileType(),
                                                               currentFile ? null : getFileNamePresentation(project, vFile),
                                                               modFile.oldText(),
                                                               modFile.newText(),
                                                               true));
      }
      else if (command instanceof ModCreateFile createFile) {
        VirtualFile vFile = createFile.file();
        if (createFile.content() instanceof ModCreateFile.Directory) {
          createdDirs.add(getFileNamePresentation(project, vFile));
        } else {
          String content =
            createFile.content() instanceof ModCreateFile.Text text ? text.text() : AnalysisBundle.message("preview.binary.content");
          customDiffList.add(new IntentionPreviewInfo.CustomDiff(vFile.getFileType(),
                                                                 getFileNamePresentation(project, vFile),
                                                                 "",
                                                                 content,
                                                                 true));
        }
      }
      else if (command instanceof ModNavigate navigate && navigate.caret() != -1) {
        VirtualFile virtualFile = navigate.file();
        if (virtualFile.isValid()) {
          PsiFile target = PsiManager.getInstance(project).findFile(virtualFile);
          if (target != null) {
            navigateInfo = IntentionPreviewInfo.navigate(target, navigate.caret());
          }
        }
      }
      else if (command instanceof ModChooseAction target) {
        return getChoosePreview(context, target);
      }
      else if (command instanceof ModEditOptions<?> target) {
        return getEditOptionsPreview(context, target);
      }
      else if (command instanceof ModDisplayMessage message) {
        if (message.kind() == ModDisplayMessage.MessageKind.ERROR) {
          return new IntentionPreviewInfo.Html(new HtmlBuilder().append(
            AnalysisBundle.message("preview.cannot.perform.action")).br().append(message.messageText()).toFragment(),
                                               IntentionPreviewInfo.InfoKind.ERROR);
        }
        else if (navigateInfo == IntentionPreviewInfo.EMPTY) {
          navigateInfo = new IntentionPreviewInfo.Html(message.messageText());
        }
      }
      else if (command instanceof ModCopyToClipboard copy) {
        navigateInfo = new IntentionPreviewInfo.Html(text(
          AnalysisBundle.message("preview.copy.to.clipboard", StringUtil.shortenTextWithEllipsis(copy.content(), 50, 10))));
      }
      else if (command instanceof ModOpenUrl openUrl) {
        navigateInfo = new IntentionPreviewInfo.Html(text(
          AnalysisBundle.message("preview.open.url", StringUtil.shortenTextWithEllipsis(openUrl.url(), 50, 10))));
      }
      else if (command instanceof ModMoveFile moveFile) {
        FutureVirtualFile targetFile = moveFile.targetFile();
        IntentionPreviewInfo.Html html;
        if (targetFile.getName().equals(moveFile.file().getName())) {
          html = (IntentionPreviewInfo.Html)IntentionPreviewInfo.moveToDirectory(moveFile.file(), targetFile.getParent());
        } else {
          html = (IntentionPreviewInfo.Html)IntentionPreviewInfo.rename(moveFile.file(), targetFile.getName());
        }
        fsActions.add(html.content());
      }
      else if (command instanceof ModUpdateSystemOptions options) {
        HtmlChunk preview = createOptionsPreview(context, options);
        navigateInfo = preview.isEmpty() ? IntentionPreviewInfo.EMPTY : new IntentionPreviewInfo.Html(preview);
      }
    }
    customDiffList.sort(Comparator.comparing(diff -> diff.fileName() != null));
    if (customDiffList.isEmpty()) {
      HtmlBuilder builder = new HtmlBuilder();
      if (!createdDirs.isEmpty()) {
        if (createdDirs.size() == 1) {
          builder.append(AnalysisBundle.message("preview.create.directory", createdDirs.get(0))).br();
        } else {
          builder.append(tag("p").addText(AnalysisBundle.message("preview.create.directories")).children(
              ContainerUtil.map(createdDirs, text -> new HtmlBuilder().br()
                .appendRaw("&bull; ") //NON-NLS
                .append(text)
                .toFragment()))
          );
        }
      }
      if (!fsActions.isEmpty()) {
        if (!builder.isEmpty()) builder.br();
        fsActions.forEach(builder::append);
      }
      if (!builder.isEmpty()) {
        return new IntentionPreviewInfo.Html(builder.toFragment());
      }
      return navigateInfo;
    }
    return customDiffList.size() == 1 ? customDiffList.get(0) :
           new IntentionPreviewInfo.MultiFileDiff(customDiffList);
  }

  private @NotNull <T extends OptionContainer> IntentionPreviewInfo getEditOptionsPreview(@NotNull ActionContext context,
                                                                                          @NotNull ModEditOptions<T> target) {
    return getPreview(target.nextCommand().apply(target.containerSupplier().get()), context);
  }

  protected @NotNull String getFileNamePresentation(Project project, VirtualFile file) {
    StringBuilder presentation = new StringBuilder(file.getName());
    while (file.getParent() instanceof FutureVirtualFile parent) {
      presentation.insert(0, parent.getName() + "/");
      file = parent;
    }
    return presentation.toString();
  }

  private static @NotNull IntentionPreviewInfo getChoosePreview(@NotNull ActionContext context, @NotNull ModChooseAction target) {
    return target.actions().stream()
      .filter(action -> action.getPresentation(context) != null)
      .findFirst()
      .map(action -> action.generatePreview(context))
      .orElse(IntentionPreviewInfo.EMPTY);
  }

  private static @NotNull HtmlChunk createOptionsPreview(@NotNull ActionContext context, @NotNull ModUpdateSystemOptions options) {
    HtmlBuilder builder = new HtmlBuilder();
    for (var option : options.options()) {
      builder.append(createOptionPreview(context.file(), option));
    }
    return builder.toFragment();
  }

  private static @NotNull HtmlChunk createOptionPreview(@NotNull PsiFile file, ModUpdateSystemOptions.@NotNull ModifiedOption option) {
    OptionController controller = OptionControllerProvider.rootController(file);
    OptionController.OptionControlInfo controlInfo = controller.findControl(option.bindId());
    if (controlInfo == null) return HtmlChunk.empty();
    OptControl control = controlInfo.control();
    Object newValue = option.newValue();
    if (newValue instanceof Boolean value) {
      OptCheckbox optCheckBox = ObjectUtils.tryCast(control, OptCheckbox.class);
      if (optCheckBox == null) return HtmlChunk.empty();
      HtmlChunk label = text(optCheckBox.label().label());
      HtmlChunk.Element checkbox = tag("input").attr("type", "checkbox").attr("readonly", "true");
      if (value) {
        checkbox = checkbox.attr("checked", "true");
      }
      HtmlChunk info = tag("table")
        .child(tag("tr").children(
          tag("td").child(checkbox),
          tag("td").child(label)
        ));
      return new HtmlBuilder().append(value ? AnalysisBundle.message("set.option.description.check")
                                            : AnalysisBundle.message("set.option.description.uncheck"))
        .br().br().append(info).toFragment();
    }
    if (newValue instanceof Integer value) {
      OptNumber optNumber = ObjectUtils.tryCast(control, OptNumber.class);
      if (optNumber == null) return HtmlChunk.empty();
      LocMessage.PrefixSuffix prefixSuffix = optNumber.splitLabel().splitLabel();
      HtmlChunk info = getValueChunk(value, prefixSuffix);
      return new HtmlBuilder().append(AnalysisBundle.message("set.option.description.input"))
        .br().br().append(info).br().toFragment();
    }
    if (newValue instanceof String value) {
      OptString optString = ObjectUtils.tryCast(control, OptString.class);
      if (optString == null) return HtmlChunk.empty();
      LocMessage.PrefixSuffix prefixSuffix = optString.splitLabel().splitLabel();
      HtmlChunk info = getValueChunk(value, prefixSuffix);
      return new HtmlBuilder().append(AnalysisBundle.message("set.option.description.string"))
        .br().br().append(info).br().toFragment();
    }
    if (newValue instanceof List<?> list) {
      OptStringList optList = ObjectUtils.tryCast(control, OptStringList.class);
      if (optList == null) return HtmlChunk.empty();
      List<?> oldList = (List<?>)option.oldValue();
      //noinspection unchecked
      return IntentionPreviewInfo.addListOption((List<String>)list, optList.label().label(), value -> !oldList.contains(value)).content();
    }
    if (newValue == null) {
      throw new IllegalStateException("Null value is not supported");
    }
    throw new IllegalStateException("Value of type " + newValue.getClass() + " is not supported");
  }

  private static @NotNull HtmlChunk getValueChunk(Object value, LocMessage.PrefixSuffix prefixSuffix) {
    HtmlChunk.Element input = tag("input").attr("type", "text").attr("value", String.valueOf(value))
      .attr("size", value.toString().length() + 1).attr("readonly", "true");
    return tag("table").child(tag("tr").children(
      tag("td").child(text(prefixSuffix.prefix())),
      tag("td").child(input),
      tag("td").child(text(prefixSuffix.suffix()))
    ));
  }
}
