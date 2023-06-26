// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.InjectionEditService;
import com.intellij.modcommand.*;
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
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.intellij.codeInspection.ModCommands.error;
import static com.intellij.codeInspection.ModCommands.nop;

final class PsiUpdateImpl {
  static @NotNull ModCommand psiUpdate(@NotNull ModCommandAction.ActionContext context,
                                       @NotNull Consumer<@NotNull ModPsiUpdater> updater) {
    var runnable = new Runnable() {
      private ModPsiUpdaterImpl myUpdater;

      @Override
      public void run() {
        myUpdater = new ModPsiUpdaterImpl(context);
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

  private static class FileTracker implements DocumentListener, Disposable {
    private final @Nullable PsiLanguageInjectionHost myHostCopy;
    private final @NotNull PsiFile myTargetFile;
    private final @NotNull Document myPositionDocument;
    private final @NotNull List<ModUpdateFileText.Fragment> myFragments = new ArrayList<>();
    private final @NotNull Document myDocument;
    private final @NotNull Project myProject;
    private final @NotNull String myOrigText;
    private final @NotNull PsiFile myOrigFile;
    private final @NotNull PsiFile myCopyFile;
    private final @NotNull PsiDocumentManager myManager;
    private boolean myDeleted;

    FileTracker(@NotNull PsiFile origFile) {
      myProject = origFile.getProject();
      myCopyFile = copyFile(myProject, origFile);
      PsiFileImplUtil.setNonPhysicalFileDeleteHandler(myCopyFile, f -> myDeleted = true);
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
      }
      else {
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
      VirtualFile origVirtualFile = myOrigFile.getOriginalFile().getVirtualFile();
      if (origVirtualFile == null) return new ModNothing();
      if (myDeleted) {
        return new ModDeleteFile(origVirtualFile);
      }
      myManager.commitDocument(myDocument);
      unblock();
      String newText = myTargetFile.getText();
      return myOrigText.equals(newText) ? new ModNothing() :
             new ModUpdateFileText(origVirtualFile, myOrigText, newText, myFragments);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      recordFragment(new ModUpdateFileText.Fragment(event.getOffset(), event.getOldLength(), event.getNewLength()));
    }

    private void recordFragment(@NotNull ModUpdateFileText.Fragment newFragment) {
      int insertionPoint = 0;
      int intersect = 0;
      for (int i = myFragments.size() - 1; i >= 0; i--) {
        ModUpdateFileText.Fragment fragment = myFragments.get(i);
        if (fragment.offset() > newFragment.offset() + newFragment.oldLength()) {
          myFragments.set(i, fragment.shift(newFragment.newLength() - newFragment.oldLength()));
        }
        else if (fragment.intersects(newFragment)) {
          intersect++;
        }
        else {
          insertionPoint = i + 1;
          break;
        }
      }
      List<ModUpdateFileText.Fragment> intersected = myFragments.subList(insertionPoint, insertionPoint + intersect);
      if (!intersected.isEmpty()) {
        ModUpdateFileText.Fragment first = intersected.get(0);
        ModUpdateFileText.Fragment last = intersected.get(intersected.size() - 1);
        int diff = intersected.stream().mapToInt(f -> f.newLength() - f.oldLength()).sum();
        ModUpdateFileText.Fragment
          mergedFragment = new ModUpdateFileText.Fragment(first.offset(), last.offset() + last.newLength() - diff - first.offset(),
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
    }
    else {
      return PsiFileFactory.getInstance(project).createFileFromText(
        origFile.getName(), origFile.getFileType(), manager.getUnescapedText(origFile),
        LocalTimeCounter.currentTime(), false);
    }
  }

  private static class ModPsiUpdaterImpl implements ModPsiUpdater, DocumentListener, Disposable {
    private final @NotNull FileTracker myTracker;
    private final @NotNull Map<PsiFile, FileTracker> myChangedFiles = new LinkedHashMap<>();
    private final @NotNull Map<VirtualFile, ModPsiUpdaterImpl.ChangedDirectoryInfo> myChangedDirectories = new LinkedHashMap<>();
    private @NotNull VirtualFile myNavigationFile;
    private int myCaretOffset;
    private @NotNull TextRange mySelection;
    private final List<ModHighlight.HighlightInfo> myHighlightInfos = new ArrayList<>();
    private @Nullable ModRenameSymbol myRenameSymbol;
    private boolean myPositionUpdated = false;
    private @NlsContexts.Tooltip String myMessage;

    private record ChangedDirectoryInfo(@NotNull ChangedVirtualDirectory directory, @NotNull PsiDirectory psiDirectory) {
      static @NotNull ModPsiUpdaterImpl.ChangedDirectoryInfo create(@NotNull PsiDirectory directory) {
        VirtualFile origDirectory = directory.getVirtualFile();
        ChangedVirtualDirectory changedDirectory = new ChangedVirtualDirectory(origDirectory);
        return new ModPsiUpdaterImpl.ChangedDirectoryInfo(changedDirectory, PsiDirectoryFactory.getInstance(directory.getProject())
          .createDirectory(changedDirectory));
      }

      @NotNull Stream<ModCommand> createFileCommands(@NotNull Project project) {
        PsiManager manager = PsiManager.getInstance(project);
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        return directory().getAddedChildren().values().stream()
          .map(vf -> {
            PsiFile psiFile = manager.findFile(vf);
            if (psiFile == null) return nop();
            Document document = psiFile.getViewProvider().getDocument();
            documentManager.commitDocument(document);
            documentManager.doPostponedOperationsAndUnblockDocument(document);
            return new ModCreateFile(new FutureVirtualFile(directory().getOriginalFile(),
                                                           vf.getName(), vf.getFileType()), psiFile.getText());
          });
      }
    }

    private ModPsiUpdaterImpl(@NotNull ModCommandAction.ActionContext actionContext) {
      myCaretOffset = actionContext.offset();
      mySelection = actionContext.selection();
      // TODO: lazily get the tracker for the current file
      myTracker = tracker(actionContext.file());
      myTracker.myPositionDocument.addDocumentListener(this, this);
      myNavigationFile = Objects.requireNonNull(myTracker.myOrigFile.getOriginalFile().getVirtualFile());
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
      if (e instanceof PsiDirectory dir) {
        VirtualFile file = dir.getVirtualFile();
        if (file instanceof ChangedVirtualDirectory) return e;
        ChangedDirectoryInfo directory = myChangedDirectories.computeIfAbsent(file, f -> ChangedDirectoryInfo.create(dir));
        @SuppressWarnings("unchecked") E result = (E)directory.psiDirectory;
        return result;
      }
      PsiFile file = e.getContainingFile();
      if (file.getViewProvider().getVirtualFile() instanceof ChangedVirtualDirectory.AddedVirtualFile) {
        return e;
      }
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
      if (!element.isValid()) throw new IllegalArgumentException();
      if (!PsiTreeUtil.isAncestor(myTracker.myCopyFile, element, false)) {
        if (element instanceof PsiFile file) {
          // allow navigating to the beginning of files
          if (file.getViewProvider().getVirtualFile() instanceof LightVirtualFile lvf &&
              lvf.getParent() instanceof ChangedVirtualDirectory cvd) {
            myNavigationFile = new FutureVirtualFile(cvd.getOriginalFile(), lvf.getName(), lvf.getFileType());
          }
          else {
            myNavigationFile = file.getOriginalFile().getVirtualFile();
          }
          return TextRange.create(0, 0);
        }
        throw new IllegalArgumentException();
      }
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
      TextRange range = getRange(element);
      if (range == null) {
        throw new IllegalArgumentException("Element disappeared after postponed operations: " + element);
      }
      myRenameSymbol = new ModRenameSymbol(myNavigationFile, range, suggestedNames);
    }

    @Override
    public void cancel(@NotNull @NlsContexts.Tooltip String errorMessage) {
      if (myMessage != null) {
        throw new IllegalStateException("Update is already cancelled");
      }
      myMessage = errorMessage;
    }

    @Override
    public int getCaretOffset() {
      return myCaretOffset;
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
      if (myMessage != null) {
        return error(myMessage);
      }
      return myChangedFiles.values().stream().map(FileTracker::getUpdateCommand).reduce(nop(), ModCommand::andThen)
        .andThen(myChangedDirectories.values().stream()
                   .flatMap(info -> info.createFileCommands(myTracker.myProject))
                   .reduce(nop(), ModCommand::andThen))
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
      return new ModNavigate(myNavigationFile, start, end, caret);
    }

    @NotNull
    private ModCommand getHighlightCommand() {
      if (myHighlightInfos.isEmpty()) return nop();
      return new ModHighlight(myNavigationFile, myHighlightInfos);
    }
  }
}
