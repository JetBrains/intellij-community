// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.injected.editor.InjectionEditService;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.*;
import com.intellij.modcommand.ModShowConflicts.Conflict;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.intellij.modcommand.ModCommand.error;
import static com.intellij.modcommand.ModCommand.nop;
import static java.util.Objects.requireNonNull;

final class PsiUpdateImpl {
  private static final Key<PsiFile> ORIGINAL_FILE_FOR_INJECTION = Key.create("ORIGINAL_FILE_FOR_INJECTION");

  static @NotNull ModCommand psiUpdate(@NotNull ActionContext context,
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
    private final @Nullable PsiLanguageInjectionHost myInjectionHost;
    private final @NotNull PsiFile myTargetFile;
    private final @NotNull Document myPositionDocument;
    private final @NotNull List<ModUpdateFileText.Fragment> myFragments = new ArrayList<>();
    private final @NotNull Document myDocument;
    private final @NotNull String myOrigText;
    private final @NotNull PsiFile myOrigFile;
    private final @NotNull PsiFile myCopyFile;
    private final @NotNull PsiDocumentManager myManager;
    private boolean myDeleted;
    private boolean myGuardModification;

    FileTracker(@NotNull PsiFile origFile, @NotNull Map<PsiFile, FileTracker> changedFiles) {
      Project project = origFile.getProject();
      myCopyFile = copyFile(project, origFile);
      PsiFileImplUtil.setNonPhysicalFileDeleteHandler(myCopyFile, f -> myDeleted = true);
      myDocument = myCopyFile.getViewProvider().getDocument();
      assert !myCopyFile.getViewProvider().isEventSystemEnabled() : "Event system for " + myCopyFile.getName();
      InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(project);
      boolean injected = injectionManager.isInjectedFragment(origFile);
      if (injected) {
        PsiLanguageInjectionHost host = requireNonNull(injectionManager.getInjectionHost(origFile));
        myInjectionHost = host;
        PsiFile hostFile = host.getContainingFile();
        FileTracker hostTracker = changedFiles.get(hostFile);
        PsiFile hostFileCopy = hostTracker != null ? hostTracker.myTargetFile : (PsiFile)hostFile.copy();
        PsiFile injectedFileCopy = getInjectedFileCopy(host, hostFileCopy, origFile.getLanguage());
        Disposable disposable = ApplicationManager.getApplication().getService(InjectionEditService.class)
          .synchronizeWithFragment(injectedFileCopy, myDocument);
        myDocument.addDocumentListener(new DocumentListener() {
          @Override
          public void beforeDocumentChange(@NotNull DocumentEvent event) {
            RangeMarker guard = myDocument.getRangeGuard(event.getOffset(), event.getOffset() + event.getOldLength());
            if (guard != null) {
              myGuardModification = true;
            }
          }
        }, this);
        Disposer.register(this, disposable);
        myTargetFile = hostFileCopy;
        origFile = hostFile;
        myPositionDocument = hostFileCopy.getViewProvider().getDocument();
      }
      else {
        myInjectionHost = null;
        myTargetFile = myCopyFile;
        myPositionDocument = myDocument;
      }
      myPositionDocument.addDocumentListener(this, this);
      myOrigText = myTargetFile.getText();
      myOrigFile = origFile;
      myManager = PsiDocumentManager.getInstance(project);
      PostprocessReformattingAspect.getInstance(project).forcePostprocessFormat(myCopyFile, this);
    }

    @Nullable PsiLanguageInjectionHost getHostCopy() {
      // Assume that PSI structure of target host file is the same during manipulations inside the injections
      return PsiTreeUtil.findSameElementInCopy(myInjectionHost, myTargetFile);
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
      String newText = myTargetFile.getFileDocument().getText();
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
      if (myDeleted) {
        throw new IllegalStateException("The file was deleted.");
      }
      if (orig == this.myOrigFile) {
        @SuppressWarnings("unchecked") E file = (E)this.myCopyFile;
        return file;
      }
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
    return requireNonNull(visitor.injectedFileCopy);
  }

  private static @NotNull PsiFile copyFile(Project project, PsiFile origFile) {
    PsiFile file;
    var manager = InjectedLanguageManager.getInstance(project);
    boolean injectedFragment = manager.isInjectedFragment(origFile);
    if (!injectedFragment) {
      PsiElement navigationElement = origFile.getNavigationElement();
      if (navigationElement != origFile && navigationElement instanceof PsiFile) {
        file = (PsiFile)navigationElement.copy();
      }
      else {
        file = (PsiFile)origFile.copy();
      }
    }
    else {
      file = PsiFileFactory.getInstance(project).createFileFromText(
        origFile.getName(), origFile.getLanguage(), manager.getUnescapedText(origFile),
        false, true, true, origFile.getVirtualFile());
      file.putUserData(ORIGINAL_FILE_FOR_INJECTION, origFile);
    }
    file.putUserData(PsiFileFactory.ORIGINAL_FILE, origFile);
    return file;
  }

  private static class ModPsiUpdaterImpl implements ModPsiUpdater, DocumentListener, Disposable {
    private final @NotNull ActionContext myActionContext;
    private @Nullable FileTracker myTracker;
    private final @NotNull Map<PsiFile, FileTracker> myChangedFiles = new LinkedHashMap<>();
    private final @NotNull Map<VirtualFile, ModPsiUpdaterImpl.ChangedDirectoryInfo> myChangedDirectories = new LinkedHashMap<>();
    private @Nullable VirtualFile myNavigationFile;
    private int myCaretOffset;
    private int myCaretVirtualEnd;
    private @NotNull TextRange mySelection;
    private final List<ModHighlight.HighlightInfo> myHighlightInfos = new ArrayList<>();
    private final List<ModStartTemplate.TemplateField> myTemplateFields = new ArrayList<>();
    private @Nullable ModStartRename myRenameSymbol;
    private final List<ModUpdateReferences> myTrackedDeclarations = new ArrayList<>();
    private boolean myPositionUpdated = false;
    private @NlsContexts.Tooltip String myErrorMessage;
    private @NlsContexts.Tooltip String myInfoMessage;
    private final @NotNull Map<@NotNull PsiElement, ModShowConflicts.@NotNull Conflict> myConflictMap = new LinkedHashMap<>();

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
        ChangedVirtualDirectory root = directory();
        Map<LightVirtualFile, VirtualFile> mapping = new HashMap<>();
        mapping.put(root, root.getOriginalFile());
        return StreamEx.<LightVirtualFile, ChangedVirtualDirectory>ofTree(
            root, ChangedVirtualDirectory.class, vf -> StreamEx.ofValues(vf.getAddedChildren()))
          .skip(1) // existing root
          .map(vf -> {
            if (vf.isDirectory()) {
              FutureVirtualFile file = new FutureVirtualFile(mapping.get(vf.getParent()), vf.getName(), null);
              mapping.put(vf, file);
              return new ModCreateFile(file, new ModCreateFile.Directory());
            }
            PsiFile psiFile = manager.findFile(vf);
            if (psiFile == null) return nop();
            Document document = psiFile.getViewProvider().getDocument();
            documentManager.commitDocument(document);
            documentManager.doPostponedOperationsAndUnblockDocument(document);
            return new ModCreateFile(new FutureVirtualFile(mapping.get(vf.getParent()), vf.getName(), vf.getFileType()),
                                     new ModCreateFile.Text(psiFile.getText()));
          });
      }
    }

    private ModPsiUpdaterImpl(@NotNull ActionContext actionContext) {
      myActionContext = actionContext;
      myCaretOffset = myCaretVirtualEnd = actionContext.offset();
      mySelection = actionContext.selection();
    }
    
    private @NotNull FileTracker tracker() {
      return myTracker == null ? tracker(myActionContext.file()) : myTracker;
    }
    
    private @NotNull VirtualFile navigationFile() {
      if (myNavigationFile == null) {
        myNavigationFile = tracker().myOrigFile.getViewProvider().getVirtualFile();
      }
      return myNavigationFile;
    } 

    private @NotNull FileTracker tracker(@NotNull PsiFile file) {
      FileTracker result = myChangedFiles.computeIfAbsent(file, origFile -> {
        var tracker = new FileTracker(origFile, myChangedFiles);
        Disposer.register(this, tracker);
        return tracker;
      });
      if (myTracker == null && myActionContext.file() == file) {
        myTracker = result;
        myTracker.myPositionDocument.addDocumentListener(this, this);
      }
      return result;
    }

    @Override
    public @NotNull PsiFile getOriginalFile(@NotNull PsiFile copyFile) throws IllegalArgumentException {
      Map.Entry<PsiFile, FileTracker> entry = ContainerUtil.find(myChangedFiles.entrySet(), e -> e.getValue().myCopyFile == copyFile);
      if (entry == null) {
        throw new IllegalArgumentException("Supplied file is not a writable copy tracked by this tracker: " + copyFile);
      }
      return entry.getKey();
    }

    @Override
    public <E extends PsiElement> E getWritable(E element) {
      if (element == null) return null;
      if (element instanceof PsiDirectory dir) {
        VirtualFile file = dir.getVirtualFile();
        if (file instanceof ChangedVirtualDirectory) return element;
        ChangedDirectoryInfo directory = myChangedDirectories.computeIfAbsent(file, f -> ChangedDirectoryInfo.create(dir));
        @SuppressWarnings("unchecked") E result = (E)directory.psiDirectory;
        return result;
      }
      PsiFile file = element.getContainingFile();
      if (file.getViewProvider().getVirtualFile() instanceof ChangedVirtualDirectory.AddedVirtualFile) {
        return element;
      }
      PsiFile originalFile = ObjectUtils.coalesce(file.getUserData(ORIGINAL_FILE_FOR_INJECTION), file.getOriginalFile());
      if (originalFile != file) {
        FileTracker tracker = tracker(originalFile);
        if (tracker.myCopyFile == file) {
          return element;
        }
      }
      return tracker(file).getCopy(element);
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
      if (!element.isValid()) throw new IllegalArgumentException("Element " + element + " is not valid");
      if (myTracker == null || !PsiTreeUtil.isAncestor(myTracker.myCopyFile, element, false)) {
        PsiFile file = element.getContainingFile();
        // allow navigating to the beginning of files
        if (file.getViewProvider().getVirtualFile() instanceof LightVirtualFile lvf &&
            lvf.getParent() instanceof ChangedVirtualDirectory cvd) {
          myNavigationFile = new FutureVirtualFile(cvd.getOriginalFile(), lvf.getName(), lvf.getFileType());
        }
        else {
          myNavigationFile = file.getOriginalFile().getVirtualFile();
        }
        if (myTracker != null) {
          myTracker.myPositionDocument.removeDocumentListener(this);
        }
        myTracker = tracker(file.getOriginalFile());
        myTracker.myPositionDocument.addDocumentListener(this, this);
        return element.getTextRange();
      }
      SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(element);
      myTracker.unblock();
      Segment range = pointer.getRange();
      if (range == null) return null;
      return TextRange.create(range);
    }

    @Override
    public void select(@NotNull TextRange range) {
      myPositionUpdated = true;
      range = mapRange(range);
      mySelection = range;
      myCaretOffset = range.getStartOffset();
      myCaretVirtualEnd = range.getEndOffset();
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
    public @NotNull ModTemplateBuilder templateBuilder() {
      if (!myTemplateFields.isEmpty()) {
        throw new IllegalStateException("Template was already created");
      }
      return new ModTemplateBuilder() {
        @Override
        public @NotNull ModTemplateBuilder field(@NotNull PsiElement element, @NotNull Expression expression) {
          return createField(element, null, expression);
        }

        @Override
        public @NotNull ModTemplateBuilder field(@NotNull PsiElement element, @NotNull String varName, @NotNull Expression expression) {
          return createField(element, varName, expression);
        }

        private @NotNull ModTemplateBuilder createField(@NotNull PsiElement element, @Nullable String varName, @NotNull Expression expression) {
          TextRange elementRange = getRange(element);
          if (elementRange == null) {
            throw new IllegalStateException("Unable to restore element for template");
          }
          TextRange range = mapRange(elementRange);
          Result result = expression.calculateResult(new DummyContext(range, element));
          myTemplateFields.add(new ModStartTemplate.ExpressionField(range, varName, expression));
          if (result != null) {
            FileTracker tracker = requireNonNull(myTracker); // guarded by getRange call
            tracker.myDocument.replaceString(elementRange.getStartOffset(), elementRange.getEndOffset(), result.toString());
          }
          return this;
        }

        @Override
        public @NotNull ModTemplateBuilder field(@NotNull PsiElement element,
                                                 @NotNull String varName,
                                                 @NotNull String dependantVariableName,
                                                 boolean alwaysStopAt) {
          TextRange elementRange = getRange(element);
          if (elementRange == null) {
            throw new IllegalStateException("Unable to restore element for template");
          }
          TextRange range = mapRange(elementRange);
          myTemplateFields.add(new ModStartTemplate.DependantVariableField(range, varName, dependantVariableName, alwaysStopAt));
          return this;
        }

        @Override
        public @NotNull ModTemplateBuilder finishAt(int offset) {
          TextRange range = mapRange(TextRange.create(offset, offset));
          myTemplateFields.add(new ModStartTemplate.EndField(range));
          return this;
        }
      };
    }

    @Override
    public void moveCaretTo(int offset) {
      myPositionUpdated = true;
      PsiLanguageInjectionHost host = tracker().getHostCopy();
      if (host != null) {
        InjectedLanguageManager instance = InjectedLanguageManager.getInstance(myActionContext.project());
        PsiFile file = findInjectedFile(instance, host);
        offset = instance.mapUnescapedOffsetToInjected(file, offset);
        offset = instance.injectedToHost(file, offset);
      }
      myCaretOffset = myCaretVirtualEnd = offset;
      if (!mySelection.containsOffset(offset)) {
        mySelection = TextRange.create(offset, offset);
      }
    }

    @Override
    public void moveCaretTo(@NotNull PsiElement element) {
      TextRange range = getRange(element);
      if (range != null) {
        range = mapRange(range);
        myPositionUpdated = true;
        myCaretOffset = range.getStartOffset();
        myCaretVirtualEnd = range.getEndOffset();
      }
    }

    @Override
    public void rename(@NotNull PsiNameIdentifierOwner element, @NotNull List<@NotNull String> suggestedNames) {
      rename(element, element.getNameIdentifier(), suggestedNames);
    }

    @Override
    public void rename(@NotNull PsiNamedElement element, @Nullable PsiElement nameIdentifier, @NotNull List<@NotNull String> suggestedNames) {
      if (myRenameSymbol != null) {
        throw new IllegalStateException("One element is already registered for rename");
      }
      TextRange range = getRange(element);
      if (range == null) {
        throw new IllegalArgumentException("Element disappeared after postponed operations: " + element);
      }
      range = mapRange(range);
      TextRange identifierRange = nameIdentifier != null ? getRange(nameIdentifier) : null;
      identifierRange = identifierRange == null ? null : mapRange(identifierRange);
      myRenameSymbol = new ModStartRename(navigationFile(), new ModStartRename.RenameSymbolRange(range, identifierRange), suggestedNames);
    }

    @Override
    public void trackDeclaration(@NotNull PsiElement declaration) {
      TextRange range = getRange(declaration);
      if (range == null) {
        throw new IllegalArgumentException("Element disappeared after postponed operations: " + declaration);
      }
      range = mapRange(range);
      String oldText = requireNonNull(myTracker).myCopyFile.getText();
      myTrackedDeclarations.add(new ModUpdateReferences(navigationFile(), oldText, range, range));
    }

    @Override
    public void cancel(@NotNull @NlsContexts.Tooltip String errorMessage) {
      if (myErrorMessage != null) {
        throw new IllegalStateException("Update is already cancelled");
      }
      myErrorMessage = errorMessage;
    }

    @Override
    public void message(@NotNull String message) {
      if (myInfoMessage != null) {
        throw new IllegalStateException("Message display was already requested; cannot show two messages");
      }
      myInfoMessage = message;
    }

    @Override
    public int getCaretOffset() {
      return myCaretOffset;
    }

    @Override
    public void showConflicts(@NotNull Map<@NotNull PsiElement, @NotNull Conflict> conflicts) {
      conflicts.forEach((e, c) -> {
        if (!e.isPhysical()) {
          PsiFile file = e.getContainingFile().getOriginalFile();
          FileTracker tracker = myChangedFiles.get(file);
          if (tracker != null) {
            if (!tracker.myFragments.isEmpty()) {
              throw new IllegalArgumentException("Supplied element belongs to a changed file");
            }
            e = PsiTreeUtil.findSameElementInCopy(e, file);
          }
        }
        myConflictMap.merge(e, c, Conflict::merge);
      });
    }

    private TextRange mapRange(@NotNull TextRange range) {
      PsiLanguageInjectionHost host = tracker().getHostCopy();
      if (host != null) {
        InjectedLanguageManager instance = InjectedLanguageManager.getInstance(myActionContext.project());
        PsiFile file = findInjectedFile(instance, host);
        int start = instance.mapUnescapedOffsetToInjected(file, range.getStartOffset());
        int end = instance.mapUnescapedOffsetToInjected(file, range.getEndOffset());
        range = instance.injectedToHost(file, TextRange.create(start, end));
      }
      return range;
    }

    private @NotNull PsiFile findInjectedFile(InjectedLanguageManager instance, PsiLanguageInjectionHost host) {
      Language language = tracker().myCopyFile.getLanguage();
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
      return requireNonNull(visitor.myFile);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      if (myCaretVirtualEnd > myCaretOffset) {
        myCaretOffset = updateOffset(event, myCaretOffset, true);
        myCaretVirtualEnd = updateOffset(event, myCaretVirtualEnd, false);
      } else {
        myCaretOffset = myCaretVirtualEnd = updateOffset(event, myCaretOffset, false);
      }
      mySelection = updateRange(event, mySelection);
      myHighlightInfos.replaceAll(info -> info.withRange(updateRange(event, info.range())));
      myTemplateFields.replaceAll(info -> info.withRange(updateRange(event, info.range())));
      myTrackedDeclarations.replaceAll(range -> range.withNewRange(updateRange(event, range.newRange())));
      if (myRenameSymbol != null) {
        ModStartRename.RenameSymbolRange renameSymbolRange = myRenameSymbol.symbolRange();

        myRenameSymbol = myRenameSymbol.withRange(updateRange(event, renameSymbolRange));
      }
    }

    private static @NotNull ModStartRename.RenameSymbolRange updateRange(@NotNull DocumentEvent event,
                                                                         @NotNull ModStartRename.RenameSymbolRange range) {
      TextRange idRange = range.nameIdentifierRange();
      return new ModStartRename.RenameSymbolRange(
        updateRange(event, range.range()), idRange != null ? updateRange(event, idRange) : null);
    }

    private static @NotNull TextRange updateRange(@NotNull DocumentEvent event, @NotNull TextRange range) {
      if (range.isEmpty()) {
        int newPos = updateOffset(event, range.getEndOffset(), false);
        return newPos == range.getEndOffset() ? range : TextRange.from(newPos, 0);
      }
      int newStart = updateOffset(event, range.getStartOffset(), true);
      int newEnd = updateOffset(event, range.getEndOffset(), false);
      if (range.getStartOffset() == newStart && range.getEndOffset() == newEnd) return range;
      return TextRange.create(newStart, newEnd);
    }

    private static int updateOffset(DocumentEvent event, int pos, boolean leanRight) {
      int offset = event.getOffset();
      int oldLength = event.getOldLength();
      int newLength = event.getNewLength();
      if (pos < offset || (pos == offset && (!leanRight || oldLength > 0))) return pos;
      if (pos >= offset + oldLength) return pos + newLength - oldLength;
      if (newLength > oldLength) {
        String newContent = event.getNewFragment().toString();
        String oldContent = event.getOldFragment().toString();
        int index = newContent.indexOf(oldContent);
        if (index >= 0) {
          return pos + index;
        }
      }
      return offset + newLength;
    }

    private @NotNull ModCommand getCommand() {
      if (myRenameSymbol != null && !myTemplateFields.isEmpty()) {
        throw new IllegalStateException("Cannot have both rename and template commands");
      }
      if (myErrorMessage != null) {
        return error(myErrorMessage);
      }
      if (ContainerUtil.exists(myChangedFiles.values(), ft -> ft.myGuardModification)) {
        return error(AnalysisBundle.message("modcommand.executor.modification.of.guarded.region"));
      }
      return ModCommand.showConflicts(myConflictMap)
        .andThen(myChangedFiles.values().stream()
          .map(fileTracker -> fileTracker.getUpdateCommand()).reduce(nop(), ModCommand::andThen))
        .andThen(myChangedDirectories.values().stream()
                   .flatMap(info -> info.createFileCommands(myActionContext.project()))
                   .reduce(nop(), ModCommand::andThen))
        .andThen(getNavigateCommand()).andThen(getHighlightCommand()).andThen(getTemplateCommand())
        .andThen(myTrackedDeclarations.stream().<ModCommand>map(c -> c).reduce(nop(), ModCommand::andThen))
        .andThen(myRenameSymbol == null ? nop() : myRenameSymbol)
        .andThen(myInfoMessage == null ? nop() : ModCommand.info(myInfoMessage));
    }

    private @NotNull ModCommand getNavigateCommand() {
      if (!myPositionUpdated || myRenameSymbol != null || myTracker == null) return nop();
      int length = myTracker.myTargetFile.getFileDocument().getTextLength();
      int start = -1, end = -1, caret = -1;
      if (mySelection.getEndOffset() <= length) {
        start = mySelection.getStartOffset();
        end = mySelection.getEndOffset();
      }
      if (this.myCaretOffset <= length) {
        caret = this.myCaretOffset;
      }
      if (start == -1 && end == -1 && caret == -1) return nop();
      return new ModNavigate(navigationFile(), start, end, caret);
    }

    private @NotNull ModCommand getHighlightCommand() {
      if (myHighlightInfos.isEmpty()) return nop();
      return new ModHighlight(navigationFile(), myHighlightInfos);
    }

    private @NotNull ModCommand getTemplateCommand() {
      if (myTemplateFields.isEmpty()) return nop();
      return new ModStartTemplate(navigationFile(), myTemplateFields, f -> nop());
    }

    private class DummyContext implements ExpressionContext {
      private final TextRange myRange;
      private final @NotNull PsiElement myElement;

      private DummyContext(TextRange range, @NotNull PsiElement element) {
        myRange = range;
        myElement = element;
      }

      @Override
      public Project getProject() { return myActionContext.project(); }

      @Override
      public @Nullable Editor getEditor() { return null; }

      @Override
      public int getStartOffset() { return myRange.getStartOffset(); }

      @Override
      public int getTemplateStartOffset() { return myRange.getStartOffset(); }

      @Override
      public int getTemplateEndOffset() { return myRange.getEndOffset(); }

      @Override
      public <T> T getProperty(Key<T> key) { return null; }

      @Override
      public @Nullable PsiElement getPsiElementAtStartOffset() { return myElement.isValid() ? myElement : null; }

      @Override
      public @Nullable TextResult getVariableValue(String variableName) { return null; }
    }
  }
}
