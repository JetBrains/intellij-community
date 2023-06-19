// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.InjectionEditService;
import com.intellij.modcommand.*;
import com.intellij.modcommand.ModUpdateFileText.Fragment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utility methods to create commands
 *
 * @see ModCommand
 */
@ApiStatus.Experimental
public final class ModCommands {
  /**
   * @return a command that does nothing
   */
  public static @NotNull ModCommand nop() {
    return ModNothing.NOTHING;
  }

  /**
   * @param message error message to display
   * @return a command that displays the specified error message in the editor
   */
  public static @NotNull ModCommand error(@NotNull @NlsContexts.Tooltip String message) {
    return new ModDisplayError(message);
  }
  
  /**
   * @param target element to select
   * @return a command that selects given element in the editor, assuming that it's opened in the editor
   */
  public static @NotNull ModCommand select(@NotNull PsiElement target) {
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
   * @param elements list of elements. If list contains no elements, nothing will be executed.
   *                 If list contains only one element, the subsequent step will be executed
   *                 right away assuming that the only element is selected.
   * @param nextStep next step generator that accepts the selected element
   * @param title    user-visible title for the element selection list
   * @param <T>      type of elements
   * @return a command that displays UI, so user can select one of the PSI elements,
   * and subsequent step will be invoked after that.
   */
  public static <T extends PsiElement> @NotNull ModCommand chooser(@NotNull List<ModChooseTarget.@NotNull ListItem<@NotNull T>> elements,
                                                                   @NotNull Function<? super @NotNull T, ? extends @NotNull ModCommand> nextStep,
                                                                   @NotNull @NlsContexts.PopupTitle String title) {
    if (elements.isEmpty()) return nop();
    if (elements.size() == 1) {
      return nextStep.apply(elements.get(0).element());
    }
    return new ModChooseTarget<>(elements, nextStep, title, 0);
  }

  /**
   * @param context a context of the original action
   * @param updater a function that accepts an updater, so it can query writable copies from it and perform modifications;
   *                also additional editor operation like caret positioning could be performed
   * @return a command that will perform the corresponding update to the original elements and the editor
   */
  public static @NotNull ModCommand psiUpdate(@NotNull ModCommandAction.ActionContext context,
                                              @NotNull Consumer<@NotNull EditorUpdater> updater) {
    var runnable = new Runnable() {
      private EditorUpdaterImpl myUpdater;

      @Override
      public void run() {
        myUpdater = new EditorUpdaterImpl(context);
        updater.accept(myUpdater);
      }

      void dispose() {
        if (myUpdater != null) {
          Disposer.dispose(myUpdater);
        }
      }
    };
    try {
      PostprocessReformattingAspect.getInstance(context.project()).postponeFormattingInside(runnable);
      return runnable.myUpdater.getCommand();
    }
    finally {
      runnable.dispose();
    }
  }

  /**
   * @param orig    PsiElement to update
   * @param updater a function that accepts a non-physical copy of the supplied orig element and performs
   *                PSI write operations in background to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  public static <E extends PsiElement> @NotNull ModCommand psiUpdate(@NotNull E orig, @NotNull Consumer<@NotNull E> updater) {
    return psiUpdate(orig, (e, ctx) -> updater.accept(e));
  }
  
  /**
   * @param orig    PsiElement to update
   * @param updater a function that accepts a non-physical copy of the supplied orig element and a context to
   *                perform additional editor operations if necessary; and performs PSI write operations in background
   *                to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  public static <E extends PsiElement> @NotNull ModCommand psiUpdate(@NotNull E orig,
                                                                     @NotNull BiConsumer<@NotNull E, @NotNull EditorUpdater> updater) {
    return psiUpdate(ModCommandAction.ActionContext.from(null, orig.getContainingFile()), eu -> updater.accept(eu.getWritable(orig), eu));
  }

  private static class FileTracker implements DocumentListener, Disposable {
    private final @Nullable PsiLanguageInjectionHost myHostCopy;
    private final @NotNull PsiFile myTargetFile;
    private final @NotNull Document myPositionDocument;
    private final @NotNull List<Fragment> myFragments = new ArrayList<>();
    private final @NotNull Document myDocument;
    private final @NotNull Project myProject;
    private final @NotNull String myOrigText;
    private final @NotNull PsiFile myOrigFile;
    private final @NotNull PsiFile myCopyFile;
    private final @NotNull PsiDocumentManager myManager;

    FileTracker(@NotNull PsiFile origFile) {
      myProject = origFile.getProject();
      myCopyFile = copyFile(myProject, origFile);
      myDocument = myCopyFile.getViewProvider().getDocument();
      InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(myProject);
      boolean injected = injectionManager.isInjectedFragment(origFile);
      if (injected) {
        PsiLanguageInjectionHost host = Objects.requireNonNull(injectionManager.getInjectionHost(origFile));
        PsiFile hostFile = host.getContainingFile();
        PsiFile hostFileCopy = (PsiFile)hostFile.copy();
        PsiFile injectedFileCopy = getInjectedFileCopy(host, hostFileCopy, origFile.getLanguage());
        myHostCopy = injectionManager.getInjectionHost(injectedFileCopy);
        Disposable disposable = ApplicationManager.getApplication().getService(InjectionEditService.class)
          .synchronizeWithFragment(injectedFileCopy, myDocument);
        Disposer.register(this, disposable);
        myTargetFile = hostFileCopy;
        origFile = hostFile;
        myPositionDocument = hostFileCopy.getViewProvider().getDocument();
      } else {
        myHostCopy = null;
        myTargetFile = myCopyFile;
        myPositionDocument = myDocument;
      }
      myPositionDocument.addDocumentListener(this, this);
      myOrigText = myTargetFile.getText();
      myOrigFile = origFile;
      myManager = PsiDocumentManager.getInstance(myProject);
      PostprocessReformattingAspect.getInstance(myProject).forcePostprocessFormat(myCopyFile, this);
    }

    void unblock() {
      myManager.doPostponedOperationsAndUnblockDocument(myDocument);
    }

    ModCommand getUpdateCommand() {
      myManager.commitDocument(myDocument);
      unblock();
      VirtualFile origVirtualFile = myOrigFile.getOriginalFile().getVirtualFile();
      if (origVirtualFile == null) return new ModNothing();
      String newText = myTargetFile.getText();
      return myOrigText.equals(newText) ? new ModNothing() :
             new ModUpdateFileText(origVirtualFile, myOrigText, newText, myFragments);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      recordFragment(new Fragment(event.getOffset(), event.getOldLength(), event.getNewLength()));
    }

    private void recordFragment(@NotNull Fragment newFragment) {
      int insertionPoint = 0;
      int intersect = 0;
      for (int i = myFragments.size() - 1; i >= 0; i--) {
        Fragment fragment = myFragments.get(i);
        if (fragment.offset() > newFragment.offset() + newFragment.oldLength()) {
          myFragments.set(i, fragment.shift(newFragment.newLength() - newFragment.oldLength()));
        } else if (fragment.intersects(newFragment)) {
          intersect++;
        } else {
          insertionPoint = i + 1;
          break;
        }
      }
      List<Fragment> intersected = myFragments.subList(insertionPoint, insertionPoint + intersect);
      if (!intersected.isEmpty()) {
        Fragment first = intersected.get(0);
        Fragment last = intersected.get(intersected.size() - 1);
        int diff = intersected.stream().mapToInt(f -> f.newLength() - f.oldLength()).sum();
        Fragment mergedFragment = new Fragment(first.offset(), last.offset() + last.newLength() - diff - first.offset(),
                                               last.offset() + last.newLength() - first.offset());
        newFragment = mergedFragment.mergeWithNext(newFragment);
      }
      intersected.clear();
      intersected.add(newFragment);
    }

    @Override
    public void dispose() {
    }

    <E extends PsiElement> @NotNull E getCopy(@NotNull E orig) {
      if (!myFragments.isEmpty()) {
        throw new IllegalStateException("File is already modified. Elements to update must be requested before any modifications");
      }
      return PsiTreeUtil.findSameElementInCopy(orig, this.myCopyFile);
    }
  }

  private static @NotNull PsiFile getInjectedFileCopy(@NotNull PsiLanguageInjectionHost host,
                                                      @NotNull PsiFile hostFileCopy,
                                                      @NotNull Language injectedLanguage) {
    InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(hostFileCopy.getProject());
    PsiLanguageInjectionHost hostCopy = PsiTreeUtil.findSameElementInCopy(host, hostFileCopy);
    var visitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      private final Language origLanguage = injectedLanguage;
      PsiFile injectedFileCopy = null;

      @Override
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<? extends PsiLanguageInjectionHost.Shred> places) {
        if (injectedPsi.getLanguage() == origLanguage) {
          injectedFileCopy = injectedPsi;
        }
      }
    };
    injectionManager.enumerate(hostCopy, visitor);
    return Objects.requireNonNull(visitor.injectedFileCopy);
  }

  private static @NotNull PsiFile copyFile(Project project, PsiFile origFile) {
    var manager = InjectedLanguageManager.getInstance(project);
    boolean injectedFragment = manager.isInjectedFragment(origFile);
    if (!injectedFragment) {
      return (PsiFile)origFile.copy();
    } else {
      return PsiFileFactory.getInstance(project).createFileFromText(
          origFile.getName(), origFile.getFileType(), manager.getUnescapedText(origFile),
          LocalTimeCounter.currentTime(), false);
    }
  }

  private static class EditorUpdaterImpl implements EditorUpdater, DocumentListener, Disposable {
    private final @NotNull FileTracker myTracker;
    private final @NotNull Map<PsiFile, FileTracker> myChangedFiles = new LinkedHashMap<>();
    private final @NotNull VirtualFile myOrigVirtualFile;
    private int myCaretOffset;
    private @NotNull TextRange mySelection;
    private final List<ModHighlight.HighlightInfo> myHighlightInfos = new ArrayList<>();
    private @Nullable ModRenameSymbol myRenameSymbol;
    private boolean myPositionUpdated = false;

    private EditorUpdaterImpl(@NotNull ModCommandAction.ActionContext actionContext) {
      myCaretOffset = actionContext.offset();
      mySelection = actionContext.selection();
      // TODO: lazily get the tracker for the current file
      myTracker = tracker(actionContext.file());
      myTracker.myPositionDocument.addDocumentListener(this, this);
      myOrigVirtualFile = Objects.requireNonNull(myTracker.myOrigFile.getOriginalFile().getVirtualFile());
    }
    
    private @NotNull FileTracker tracker(@NotNull PsiFile file) {
      return myChangedFiles.computeIfAbsent(file, origFile -> {
        var tracker = new FileTracker(origFile);
        Disposer.register(this, tracker);
        return tracker;
      });
    }

    @Override
    public <E extends PsiElement> E getWritable(E e) {
      if (e == null) return null;
      PsiFile file = e.getContainingFile();
      PsiFile originalFile = file.getOriginalFile();
      if (originalFile != file) {
        FileTracker tracker = tracker(originalFile);
        if (tracker.myCopyFile == file) {
          return e;
        }
      }
      return tracker(file).getCopy(e);
    }

    @Override
    public void dispose() {
    }

    @Override
    public void select(@NotNull PsiElement element) {
      TextRange range = getRange(element);
      if (range != null) {
        select(range);
      }
    }

    private @Nullable TextRange getRange(@NotNull PsiElement element) {
      validate(element);
      SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(element);
      myTracker.unblock();
      Segment range = pointer.getRange();
      return range == null ? null : TextRange.create(range);
    }

    @Override
    public void select(@NotNull TextRange range) {
      myPositionUpdated = true;
      range = mapRange(range);
      mySelection = range;
      myCaretOffset = range.getStartOffset();
    }

    @Override
    public void highlight(@NotNull PsiElement element, @NotNull TextAttributesKey attributesKey) {
      TextRange range = getRange(element);
      if (range != null) {
        highlight(range, attributesKey);
      }
    }

    @Override
    public void highlight(@NotNull TextRange range, @NotNull TextAttributesKey attributesKey) {
      range = mapRange(range);
      myHighlightInfos.add(new ModHighlight.HighlightInfo(range, attributesKey, true));
    }

    @Override
    public void moveTo(int offset) {
      myPositionUpdated = true;
      PsiLanguageInjectionHost host = myTracker.myHostCopy;
      if (host != null) {
        InjectedLanguageManager instance = InjectedLanguageManager.getInstance(myTracker.myProject);
        PsiFile file = findInjectedFile(instance, host);
        offset = instance.mapUnescapedOffsetToInjected(file, offset);
        offset = instance.injectedToHost(file, offset);
      }
      myCaretOffset = offset;
    }

    @Override
    public void moveTo(@NotNull PsiElement element) {
      TextRange range = getRange(element);
      if (range != null) {
        moveTo(range.getStartOffset());
      }
    }

    @Override
    public void moveToPrevious(char ch) {
      myPositionUpdated = true;
      myTracker.unblock();
      String text = myTracker.myPositionDocument.getText();
      int idx = text.lastIndexOf(ch, myCaretOffset);
      if (idx == -1) return;
      myCaretOffset = idx;
    }

    @Override
    public void rename(@NotNull PsiNameIdentifierOwner element, @NotNull List<@NotNull String> suggestedNames) {
      if (myRenameSymbol != null) {
        throw new IllegalStateException("One element is already registered for rename");
      }
      myRenameSymbol = new ModRenameSymbol(myOrigVirtualFile, element.getTextRange(), suggestedNames);
    }

    private TextRange mapRange(@NotNull TextRange range) {
      PsiLanguageInjectionHost host = myTracker.myHostCopy;
      if (host != null) {
        InjectedLanguageManager instance = InjectedLanguageManager.getInstance(myTracker.myProject);
        PsiFile file = findInjectedFile(instance, host);
        int start = instance.mapUnescapedOffsetToInjected(file, range.getStartOffset());
        int end = instance.mapUnescapedOffsetToInjected(file, range.getEndOffset());
        range = instance.injectedToHost(file, TextRange.create(start, end));
      }
      return range;
    }

    private PsiFile findInjectedFile(InjectedLanguageManager instance, PsiLanguageInjectionHost host) {
      Language language = myTracker.myCopyFile.getLanguage();
      var visitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        PsiFile myFile = null;

        @Override
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<? extends PsiLanguageInjectionHost.Shred> places) {
          if (injectedPsi.getLanguage() == language) {
            myFile = injectedPsi;
          }
        }
      };
      instance.enumerate(host, visitor);
      return Objects.requireNonNull(visitor.myFile);
    }

    private void validate(@NotNull PsiElement element) {
      if (!element.isValid()) throw new IllegalArgumentException();
      if (!PsiTreeUtil.isAncestor(myTracker.myCopyFile, element, false)) throw new IllegalArgumentException();
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      myCaretOffset = updateOffset(event, myCaretOffset);
      mySelection = updateRange(event, mySelection);
      myHighlightInfos.replaceAll(info -> info.withRange(updateRange(event, info.range())));
      if (myRenameSymbol != null) {
        myRenameSymbol = myRenameSymbol.withRange(updateRange(event, myRenameSymbol.symbolRange()));
      }
    }

    private static @NotNull TextRange updateRange(@NotNull DocumentEvent event, @NotNull TextRange range) {
      int newStart = updateOffset(event, range.getStartOffset());
      int newEnd = updateOffset(event, range.getEndOffset());
      if (range.getStartOffset() == newStart && range.getEndOffset() == newEnd) return range;
      return TextRange.create(newStart, newEnd);
    }

    private static int updateOffset(DocumentEvent event, int pos) {
      int offset = event.getOffset();
      int oldLength = event.getOldLength();
      int newLength = event.getNewLength();
      if (pos <= offset) return pos;
      if (pos >= offset + oldLength) return pos + newLength - oldLength;
      return offset + newLength;
    }
    
    private @NotNull ModCommand getCommand() {
      return myChangedFiles.values().stream().map(FileTracker::getUpdateCommand).reduce(nop(), ModCommand::andThen)
        .andThen(getNavigateCommand()).andThen(getHighlightCommand()).andThen(myRenameSymbol == null ? nop() : myRenameSymbol);
    }

    @NotNull
    private ModCommand getNavigateCommand() {
      if (!myPositionUpdated || myRenameSymbol != null) return nop();
      int length = myTracker.myTargetFile.getTextLength();
      int start = -1, end = -1, caret = -1;
      if (mySelection.getEndOffset() <= length) {
        start = mySelection.getStartOffset();
        end = mySelection.getEndOffset();
      }
      if (this.myCaretOffset <= length) {
        caret = this.myCaretOffset;
      }
      if (start == -1 && end == -1 && caret == -1) return nop();
      return new ModNavigate(myOrigVirtualFile, start, end, caret);
    }
    
    @NotNull
    private ModCommand getHighlightCommand() {
      if (myHighlightInfos.isEmpty()) return nop();
      return new ModHighlight(myOrigVirtualFile, myHighlightInfos);
    }
  }
}
