// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.OptionControllerProvider;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A transparent command that modifies the project/workspace state (writes file, changes setting, moves editor caret, etc.).
 * <p>
 * There are commands that don't modify state, but produce a user interaction (e.g., ask questions, display chooser, launch browser),
 * but they should be followed by commands that <i>do modify</i> state.
 * Otherwise, there's no purpose in such a command.
 * And to some extent, showing UI could also be considered a modification (of things visible on the screen).
 * <p>
 * All inheritors are records, so the whole state is declarative and readable.
 * Instead of creating the commands directly, it's preferred to use static methods in this class to create individual commands.
 * Especially take a look at {@link #psiUpdate} methods which are helpful in most of the cases.
 */
public sealed interface ModCommand
  permits ModChooseAction, ModChooseMember, ModCompositeCommand, ModCopyToClipboard, ModCreateFile, ModDeleteFile, ModDisplayMessage,
          ModEditOptions, ModHighlight, ModMoveFile, ModNavigate, ModNothing, ModOpenUrl, ModShowConflicts, ModStartRename,
          ModStartTemplate, ModUpdateFileText, ModUpdateReferences, ModUpdateSystemOptions {

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
   * @param url the URL to open
   * @return a ModCommand instance representing the action of opening the URL
   */
  static @NotNull ModCommand openUrl(@NotNull String url) {
    return new ModOpenUrl(url);
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
   * @return a command that selects the given element in the editor, assuming that it's opened in the editor
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
   * @param target element to move to
   * @return a command that navigates to a given element in the editor, assuming that it's opened in the editor
   */
  static @NotNull ModCommand moveTo(@NotNull PsiElement target) {
    PsiFile psiFile = target.getContainingFile();
    TextRange range = target.getTextRange();
    Document document = psiFile.getViewProvider().getDocument();
    if (document instanceof DocumentWindow window) {
      range = window.injectedToHost(range);
      psiFile = InjectedLanguageManager.getInstance(psiFile.getProject()).getTopLevelFile(psiFile);
    }
    VirtualFile file = psiFile.getVirtualFile();
    return new ModNavigate(file, range.getStartOffset(), range.getStartOffset(), range.getStartOffset());
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
   * @param context context PSI element to retrieve a proper copy of the tool
   * @param inspection inspection instance to update (used as template, should not be changed)
   * @param updater updater function that receives a separate inspection instance and can change its options.
   *                Only options accessible via {@link InspectionProfileEntry#getOptionsPane()} will be tracked.
   * @param <T> inspection class
   * @return a command to update an inspection option
   */
  static <T extends InspectionProfileEntry> @NotNull ModCommand updateInspectionOption(
    @NotNull PsiElement context, @NotNull T inspection, @NotNull Consumer<@NotNull T> updater) {
    return ModCommandService.getInstance().updateOption(context, inspection, updater);
  }

  /**
   * @param context context PSI element
   * @param bindId global option locator
   * @param newValue a new value of the option
   * @return a command that updates the given option
   * @see OptionControllerProvider for details
   */
  @ApiStatus.Experimental
  static @NotNull ModCommand updateOption(
    @NotNull PsiElement context, @NotNull @NonNls String bindId, @NotNull Object newValue) {
    Object oldValue = OptionControllerProvider.getOption(context, bindId);
    return new ModUpdateSystemOptions(List.of(new ModUpdateSystemOptions.ModifiedOption(bindId, oldValue, newValue)));
  }

  /**
   * @param context context PSI element
   * @param bindId global option locator
   * @param listUpdater function that accepts a mutable list (an old value) and updates it
   * @return a command that updates the given option
   * @see OptionControllerProvider for details
   */
  @ApiStatus.Experimental
  static @NotNull ModCommand updateOptionList(
    @NotNull PsiElement context, @NotNull @NonNls String bindId, @NotNull Consumer<@NotNull List<@NotNull String>> listUpdater) {
    @SuppressWarnings("unchecked") 
    List<String> oldValue = (List<String>)Objects.requireNonNull(OptionControllerProvider.getOption(context, bindId));
    List<String> newValue = new ArrayList<>(oldValue);
    listUpdater.accept(newValue);
    if (oldValue.equals(newValue)) {
      return nop();
    }
    return new ModUpdateSystemOptions(List.of(new ModUpdateSystemOptions.ModifiedOption(bindId, oldValue, newValue)));
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
   *                PSI write operations in the background to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  static <E extends PsiElement> @NotNull ModCommand psiUpdate(@NotNull E orig, @NotNull Consumer<@NotNull E> updater) {
    return psiUpdate(orig, (e, ctx) -> updater.accept(e));
  }

  /**
   * @param orig    PsiElement to update
   * @param updater a function that accepts a non-physical copy of the supplied orig element and a context to
   *                perform additional editor operations if necessary; and performs PSI write operations in the background
   *                to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  static <E extends PsiElement> @NotNull ModCommand psiUpdate(@NotNull E orig,
                                                              @NotNull BiConsumer<@NotNull E, @NotNull ModPsiUpdater> updater) {
    return psiUpdate(ActionContext.from(null, orig.getContainingFile()), eu -> updater.accept(eu.getWritable(orig), eu));
  }

  /**
   * Create an action that depends on a PSI element in the current file.
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
    final @NotNull @IntentionName String title,
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
   * Create an action that depends on a PSI element in the current file and performs PSI update.
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
    final @NotNull @IntentionName String title,
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
   * Create an action that depends on a PSI element in the current file and performs PSI update.
   *
   * @param element element
   * @param title action title
   * @param action action to perform on non-physical element copy
   * @param <T> type of the element
   * @return an action suitable to store inside {@link ModChooseAction}
   */
  static @NotNull <T extends PsiElement> ModCommandAction psiUpdateStep(
    @NotNull T element,
    final @NotNull @IntentionName String title,
    @NotNull BiConsumer<@NotNull T, @NotNull ModPsiUpdater> action) {
    return psiUpdateStep(element, title, action, PsiElement::getTextRange);
  }

  /**
   * @param command a command to tune
   * @param file a file where we want to navigate
   * @param offset an offset in the file before the command is executed 
   * @param leanRight if true, lean to the right side when the text was inserted right at the caret position
   * @return an updated command which tries to navigate inside the specified file, taking into account the modifications inside that file
   */
  @ApiStatus.Experimental
  static @NotNull ModCommand moveCaretAfter(@NotNull ModCommand command, @NotNull PsiFile file, int offset, boolean leanRight) {
    VirtualFile virtualFile = file.getVirtualFile();
    ModCommand finalCommand = nop();
    for (ModCommand sub : command.unpack()) {
      if (sub instanceof ModUpdateFileText updateFileText && updateFileText.file().equals(virtualFile)) {
        offset = updateFileText.translateOffset(offset, leanRight);
      }
      if (sub instanceof ModDeleteFile deleteFile && deleteFile.file().equals(virtualFile)) {
        // Navigation is useless: we are removing the target file
        return command;
      }
      if (sub instanceof ModMoveFile moveFile && moveFile.file().equals(virtualFile)) {
        virtualFile = moveFile.targetFile();
      }
      if (!(sub instanceof ModNavigate)) {
        finalCommand = finalCommand.andThen(sub);
      }
    }
    return finalCommand.andThen(new ModNavigate(virtualFile, offset, offset, offset));
  }

  /**
   * Creates a command that allows user to select arbitrary number of members (but at least one).
   * Initially, all the elements are selected. In batch mode, it's assumed that all the elements are selected.
   *
   * @param title user-readable title to display in UI
   * @param elements all elements to select from
   * @param nextCommand a function to compute the subsequent command based on the selection; will be executed in read-action
   */
  @ApiStatus.Experimental
  static @NotNull ModCommand chooseMultipleMembers(@NotNull @NlsContexts.PopupTitle String title,
                                                   @NotNull List<? extends OptMultiSelector.@NotNull OptElement> elements,
                                                   @NotNull Function<@NotNull List<? extends OptMultiSelector.@NotNull OptElement>, ? extends @NotNull ModCommand> nextCommand) {
    return chooseMultipleMembers(title, elements, elements, nextCommand);
  }

  /**
   * Creates a command that allows user to select arbitrary number of members (but at least one). 
   * In batch mode, it's assumed that the default selection is selected.
   *
   * @param title user-readable title to display in UI
   * @param elements all elements to select from
   * @param defaultSelection default selection
   * @param nextCommand a function to compute the subsequent command based on the selection; will be executed in read-action
   */
  @ApiStatus.Experimental
  static @NotNull ModCommand chooseMultipleMembers(@NotNull @NlsContexts.PopupTitle String title,
                                                   @NotNull List<? extends OptMultiSelector.@NotNull OptElement> elements,
                                                   @NotNull List<? extends OptMultiSelector.@NotNull OptElement> defaultSelection,
                                                   @NotNull Function<@NotNull List<? extends OptMultiSelector.@NotNull OptElement>, ? extends @NotNull ModCommand> nextCommand) {
    return new ModChooseMember(title, elements, defaultSelection, ModChooseMember.SelectionMode.MULTIPLE, nextCommand);
  }

  /**
   * A replacement for chooseMultipleMembers; not working yet
   */
  @ApiStatus.Internal
  static @NotNull ModCommand chooseMultipleMembersNew(@NotNull @NlsContexts.PopupTitle String title,
                                                   @NotNull List<? extends OptMultiSelector.@NotNull OptElement> elements,
                                                   @NotNull List<? extends OptMultiSelector.@NotNull OptElement> defaultSelection,
                                                   @NotNull Function<@NotNull List<? extends OptMultiSelector.@NotNull OptElement>, ? extends @NotNull ModCommand> nextCommand) {
    class MemberHolder implements OptionContainer {
      @SuppressWarnings("FieldMayBeFinal") 
      @NotNull List<? extends OptMultiSelector.@NotNull OptElement> myElements = new ArrayList<>(defaultSelection);
      
      @Override
      public @NotNull OptPane getOptionsPane() {
        return OptPane.pane(
          new OptMultiSelector("myElements", elements, OptMultiSelector.SelectionMode.MULTIPLE)
        );
      }
    }
    return new ModEditOptions<>(title, MemberHolder::new, true, mh -> nextCommand.apply(mh.myElements));
  }

  /**
   * Creates a command that displays conflicts during interactive execution and requires user confirmation to proceed to the next step.
   * Not executed in batch; skipped in preview.
   *
   * @param conflicts conflicts to show
   */
  static @NotNull ModCommand showConflicts(@NotNull Map<@NotNull PsiElement, ModShowConflicts.@NotNull Conflict> conflicts) {
    return conflicts.isEmpty() ? nop() : new ModShowConflicts(conflicts);
  }

  /**
   * Creates a command that displays a UI and allows users to select a subsequent action from the list.
   * Intention preview assumes that the first available action is selected by default.
   * In batch mode, the first option is also selected automatically.
   *
   * @param title title to display to the user
   * @param actions actions to select from. If there's only one action, then it could be executed right away without asking the user.
   * @see #psiUpdateStep(PsiElement, String, BiConsumer) could be useful as a subsequent step
   */
  static @NotNull ModCommand chooseAction(@NotNull @NlsContexts.PopupTitle String title,
                                          @NotNull List<? extends @NotNull ModCommandAction> actions) {
    return new ModChooseAction(title, actions);
  }

  /**
   * Creates a command that displays a UI and allows users to select a subsequent action from the list.
   * Intention preview assumes that the first available action is selected by default.
   * In batch mode, the first option is also selected automatically.
   *
   * @param title title to display to the user
   * @param actions actions to select from. If there's only one action, then it could be executed right away without asking the user. 
   * @see #psiUpdateStep(PsiElement, String, BiConsumer) could be useful as a subsequent step
   */
  static @NotNull ModCommand chooseAction(@NotNull @NlsContexts.PopupTitle String title,
                                          @NotNull ModCommandAction @NotNull ... actions) {
    return new ModChooseAction(title, List.of(actions));
  }

  /**
   * Creates a command to move a file to a specified directory
   * 
   * @param file file to move
   * @param target target directory
   * @return a command that moves the file to a specified directory
   */
  static @NotNull ModCommand moveFile(@NotNull VirtualFile file, @NotNull VirtualFile target) {
    return new ModMoveFile(file, new FutureVirtualFile(target, file.getName(), file.getFileType()));
  }

}
