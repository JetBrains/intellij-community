// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A transparent command, which modifies the project/workspace state (writes file, changes setting, moves editor caret, etc.),
 * or produces a user interaction (displays question, launches browser, etc.).
 * <p>
 * All inheritors are records, so the whole state is declarative and readable.
 */
public sealed interface ModCommand
  permits ModChooseAction, ModChooseMember, ModCompositeCommand, ModCopyToClipboard, ModCreateFile, ModDeleteFile, ModDisplayMessage,
          ModHighlight, ModNavigate, ModNothing, ModRenameSymbol, ModShowConflicts, ModStartTemplate, ModUpdateFileText,
          ModUpdateInspectionOptions {

  /**
   * @return true if the command does nothing
   */
  default boolean isEmpty() {
    return false;
  }

  /**
   * @return set of files that are potentially modified by this command
   */
  default @NotNull Set<@NotNull VirtualFile> modifiedFiles() {
    return Set.of();
  }

  /**
   * @param next command to be executed right after current
   * @return the composite command that executes both current and the next command
   */
  default @NotNull ModCommand andThen(@NotNull ModCommand next) {
    if (isEmpty()) return next;
    if (next.isEmpty()) return this;
    List<ModCommand> commands = new ArrayList<>(unpack());
    commands.addAll(next.unpack());
    return commands.size() == 1 ? commands.get(0) : new ModCompositeCommand(commands); 
  }

  /**
   * @return list of individual commands this command consists of
   */
  default @NotNull List<@NotNull ModCommand> unpack() {
    return List.of(this);
  }

  /**
   * @return a command that does nothing
   */
  static @NotNull ModCommand nop() {
    return ModNothing.NOTHING;
  }

  /**
   * @param content content to put into clipboard
   * @return a command that copies the content to the clipboard
   */
  static @NotNull ModCommand copyToClipboard(@NotNull String content) {
    return new ModCopyToClipboard(content);
  }

  /**
   * @param message error message to display
   * @return a command that displays the specified error message in the editor
   */
  static @NotNull ModCommand error(@NotNull @NlsContexts.Tooltip String message) {
    return new ModDisplayMessage(message, ModDisplayMessage.MessageKind.ERROR);
  }

  /**
   * @param message informational message to display
   * @return a command that displays the specified informational message in the editor
   */
  static @NotNull ModCommand info(@NotNull @NlsContexts.Tooltip String message) {
    return new ModDisplayMessage(message, ModDisplayMessage.MessageKind.INFORMATION);
  }

  /**
   * @param target element to select
   * @return a command that selects given element in the editor, assuming that it's opened in the editor
   */
  static @NotNull ModCommand select(@NotNull PsiElement target) {
    PsiFile psiFile = target.getContainingFile();
    TextRange range = target.getTextRange();
    Document document = psiFile.getViewProvider().getDocument();
    if (document instanceof DocumentWindow window) {
      range = window.injectedToHost(range);
      psiFile = InjectedLanguageManager.getInstance(psiFile.getProject()).getTopLevelFile(psiFile);
    }
    VirtualFile file = psiFile.getVirtualFile();
    return new ModNavigate(file, range.getStartOffset(), range.getEndOffset(), range.getStartOffset());
  }

  /**
   * @param attributes attributes to use for highlighting 
   * @param elements elements to highlight
   * @return a command to highlight the elements, assuming that nothing will be changed in the file
   */
  static @NotNull ModCommand highlight(@NotNull TextAttributesKey attributes, @NotNull PsiElement @NotNull ... elements) {
    if (elements.length == 0) return nop();
    VirtualFile file = elements[0].getContainingFile().getVirtualFile();
    var highlights = ContainerUtil.map(
      elements, e -> new ModHighlight.HighlightInfo(e.getTextRange(), attributes, true));
    return new ModHighlight(file, highlights);
  }

  /**
   * @param elements elements to highlight
   * @return a command to highlight the elements, assuming that nothing will be changed in the file
   */
  static @NotNull ModCommand highlight(@NotNull PsiElement @NotNull ... elements) {
    return highlight(EditorColors.SEARCH_RESULT_ATTRIBUTES, elements);
  }

  /**
   * @param context context PSI element to retrieve proper copy of the tool 
   * @param inspection inspection instance to update (used as template, should not be changed)
   * @param updater updater function that receives a separate inspection instance and can change its options.
   *                Only options accessible via {@link InspectionProfileEntry#getOptionsPane()} will be tracked.
   * @param <T> inspection class
   * @return a command to update an inspection option
   */
  static <T extends InspectionProfileEntry> @NotNull ModCommand updateOption(
    @NotNull PsiElement context, @NotNull T inspection, @NotNull Consumer<@NotNull T> updater) {
    return ModCommandService.getInstance().updateOption(context, inspection, updater);
  }

  /**
   * @param context a context of the original action
   * @param updater a function that accepts an updater, so it can query writable copies from it and perform modifications;
   *                also additional editor operation like caret positioning could be performed
   * @return a command that will perform the corresponding update to the original elements and the editor
   */
  static @NotNull ModCommand psiUpdate(@NotNull ActionContext context,
                                       @NotNull Consumer<@NotNull ModPsiUpdater> updater) {
    return ModCommandService.getInstance().psiUpdate(context, updater);
  }

  /**
   * @param orig    PsiElement to update
   * @param updater a function that accepts a non-physical copy of the supplied orig element and performs
   *                PSI write operations in background to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  static <E extends PsiElement> @NotNull ModCommand psiUpdate(@NotNull E orig, @NotNull Consumer<@NotNull E> updater) {
    return psiUpdate(orig, (e, ctx) -> updater.accept(e));
  }

  /**
   * @param orig    PsiElement to update
   * @param updater a function that accepts a non-physical copy of the supplied orig element and a context to
   *                perform additional editor operations if necessary; and performs PSI write operations in background
   *                to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  static <E extends PsiElement> @NotNull ModCommand psiUpdate(@NotNull E orig,
                                                              @NotNull BiConsumer<@NotNull E, @NotNull ModPsiUpdater> updater) {
    return psiUpdate(ActionContext.from(null, orig.getContainingFile()), eu -> updater.accept(eu.getWritable(orig), eu));
  }

  /**
   * Create an action that depends on a PSI element in current file
   *
   * @param element element
   * @param title action title
   * @param function factory to create a final command
   * @param range range to select
   * @param <T> type of the element
   * @return an action suitable to store inside {@link ModChooseAction}
   */
  static @NotNull <T extends PsiElement> ModCommandAction psiBasedStep(
    @NotNull T element,
    @NotNull @IntentionName final String title,
    @NotNull Function<@NotNull T, @NotNull ModCommand> function,
    @NotNull Function<@NotNull T, @NotNull TextRange> range) {
    return new PsiBasedModCommandAction<T>(element) {
      @Override
      protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull T element) {
        return function.apply(element);
      }

      @Override
      protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull T section) {
        return Presentation.of(getFamilyName()).withHighlighting(range.apply(section));
      }

      @Override
      public @NotNull String getFamilyName() {
        return title;
      }
    };
  }

  /**
   * Create an action that depends on a PSI element in current file and performs PSI update
   *
   * @param element element
   * @param title action title
   * @param action action to perform on non-physical element copy
   * @param range function to extract a range to select
   * @param <T> type of the element
   * @return an action suitable to store inside {@link ModChooseAction}
   */
  static @NotNull <T extends PsiElement> ModCommandAction psiUpdateStep(
    @NotNull T element,
    @NotNull @IntentionName final String title,
    @NotNull BiConsumer<@NotNull T, @NotNull ModPsiUpdater> action,
    @NotNull Function<@NotNull T, @NotNull TextRange> range) {
    return new PsiUpdateModCommandAction<T>(element) {
      @Override
      protected void invoke(@NotNull ActionContext context, @NotNull T element, @NotNull ModPsiUpdater updater) {
        action.accept(element, updater);
      }

      @Override
      protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull T section) {
        return Presentation.of(getFamilyName()).withHighlighting(range.apply(section));
      }

      @Override
      public @NotNull String getFamilyName() {
        return title;
      }

      @Override
      public String toString() {
        return "Step: [" + title + "] " + element.getText();
      }
    };
  }

  /**
   * Create an action that depends on a PSI element in current file and performs PSI update
   *
   * @param element element
   * @param title action title
   * @param action action to perform on non-physical element copy
   * @param <T> type of the element
   * @return an action suitable to store inside {@link ModChooseAction}
   */
  static @NotNull <T extends PsiElement> ModCommandAction psiUpdateStep(
    @NotNull T element,
    @NotNull @IntentionName final String title,
    @NotNull BiConsumer<@NotNull T, @NotNull ModPsiUpdater> action) {
    return psiUpdateStep(element, title, action, PsiElement::getTextRange);
  }
}
