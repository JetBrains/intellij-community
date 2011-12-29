/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.*;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.ui.components.JBList;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.*;

/**
 * @author ven
 */
public class VariableInplaceRenamer {
  public static final Key<VariableInplaceRenamer> INPLACE_RENAMER = Key.create("EditorInplaceRenamer");
  protected static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.inplace.VariableInplaceRenamer");
  public static final LanguageExtension<ResolveSnapshotProvider> INSTANCE = new LanguageExtension<ResolveSnapshotProvider>(
    "com.intellij.rename.inplace.resolveSnapshotProvider"
  );
  static final String RENAME_TITLE = RefactoringBundle.message("rename.title");

  protected PsiNamedElement myElementToRename;
  @NonNls protected static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OtherVariable";
  private ArrayList<RangeHighlighter> myHighlighters;
  protected final Editor myEditor;
  protected final Project myProject;
  private RangeMarker myRenameOffset;
  private String myInitialName;
  protected final String myOldName;
  protected RangeMarker myBeforeRevert = null;

  public void setAdvertisementText(String advertisementText) {
    myAdvertisementText = advertisementText;
  }

  private String myAdvertisementText;

  private static final Stack<VariableInplaceRenamer> ourRenamersStack = new Stack<VariableInplaceRenamer>();

  public VariableInplaceRenamer(@NotNull PsiNamedElement elementToRename, Editor editor) {
    this(elementToRename, editor, elementToRename.getProject());
  }

  public VariableInplaceRenamer(PsiNamedElement elementToRename,
                                Editor editor,
                                Project project) {
    this(elementToRename, editor, project, elementToRename != null ? elementToRename.getName() : null, elementToRename != null ? elementToRename.getName() : null);
  }

  public VariableInplaceRenamer(PsiNamedElement elementToRename,
                                Editor editor,
                                Project project,
                                final String initialName,
                                final String oldName) {
    myElementToRename = elementToRename;
    myEditor = /*(editor instanceof EditorWindow)? ((EditorWindow)editor).getDelegate() : */editor;
    myProject = project;
    myOldName = oldName;
    if (myElementToRename != null) {
      myInitialName = initialName;
      final PsiFile containingFile = myElementToRename.getContainingFile();
      if (!notSameFile(containingFile.getVirtualFile(), containingFile)) {
        myRenameOffset = myElementToRename != null && myElementToRename.getTextRange() != null ? myEditor.getDocument().createRangeMarker(myElementToRename.getTextRange()) : null;
      }
    }
  }

  public boolean performInplaceRename() {
    return performInplaceRename(true, null);
  }

  public boolean performInplaceRename(boolean processTextOccurrences, LinkedHashSet<String> nameSuggestions) {
    if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(myElementToRename)) {
      return false;
    }

    final FileViewProvider fileViewProvider = myElementToRename.getContainingFile().getViewProvider();
    VirtualFile file = getTopLevelVirtualFile(fileViewProvider);

    SearchScope referencesSearchScope = getReferencesSearchScope(file);

    final Collection<PsiReference> refs = collectRefs(referencesSearchScope);

    addReferenceAtCaret(refs);

    for (PsiReference ref : refs) {
      final PsiFile containingFile = ref.getElement().getContainingFile();

      if (notSameFile(file, containingFile)) {
        return false;
      }
    }

    PsiElement scope = checkLocalScope();

    if (scope == null) {
      return false; // Should have valid local search scope for inplace rename
    }

    final PsiFile containingFile = scope.getContainingFile();
    if (containingFile == null){
      return false; // Should have valid local search scope for inplace rename
    }
    //no need to process further when file is read-only
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, containingFile)) return true;

    myEditor.putUserData(INPLACE_RENAMER, this);
    ourRenamersStack.push(this);

    final List<Pair<PsiElement, TextRange>> stringUsages = new ArrayList<Pair<PsiElement, TextRange>>();
    collectAdditionalElementsToRename(processTextOccurrences, stringUsages);
    if (appendAdditionalElement(stringUsages)) {
      return runRenameTemplate(nameSuggestions, refs, stringUsages, scope, containingFile);
    } else {
      new RenameChooser(myEditor).showChooser(refs, stringUsages, nameSuggestions, scope, containingFile);
    }


    return true;
  }

  protected Collection<PsiReference> collectRefs(SearchScope referencesSearchScope) {
    return ReferencesSearch.search(myElementToRename, referencesSearchScope, false).findAll();
  }

  protected boolean notSameFile(@Nullable VirtualFile file, PsiFile containingFile) {
    return getTopLevelVirtualFile(containingFile.getViewProvider()) != file;
  }

  protected SearchScope getReferencesSearchScope(VirtualFile file) {
    return file == null || ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(file)
      ? ProjectScope.getProjectScope(myElementToRename.getProject())
      : new LocalSearchScope(myElementToRename.getContainingFile());
  }

  @Nullable
  protected PsiElement checkLocalScope() {
    final SearchScope searchScope = PsiSearchHelper.SERVICE.getInstance(myElementToRename.getProject()).getUseScope(myElementToRename);
    if (searchScope instanceof LocalSearchScope) {
      final PsiElement[] elements = ((LocalSearchScope)searchScope).getScope();
      return PsiTreeUtil.findCommonParent(elements);
    }

    return null;
  }

  protected boolean appendAdditionalElement(List<Pair<PsiElement, TextRange>> stringUsages) {
    return stringUsages.isEmpty();
  }

  protected void collectAdditionalElementsToRename(boolean processTextOccurrences, final List<Pair<PsiElement, TextRange>> stringUsages) {
    final String stringToSearch = myElementToRename.getName();
    final PsiFile currentFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    if (processTextOccurrences && stringToSearch != null) {
      TextOccurrencesUtil
        .processUsagesInStringsAndComments(myElementToRename, stringToSearch, true, new PairProcessor<PsiElement, TextRange>() {
          public boolean process(PsiElement psiElement, TextRange textRange) {
            if (psiElement.getContainingFile() == currentFile) {
              stringUsages.add(Pair.create(psiElement, textRange));
            }
            return true;
          }
        });
    }
  }

  private boolean runRenameTemplate(final LinkedHashSet<String> nameSuggestions,
                                    Collection<PsiReference> refs,
                                    final Collection<Pair<PsiElement, TextRange>> stringUsages,
                                    PsiElement scope,
                                    final PsiFile containingFile) {
    final PsiElement context = containingFile.getContext();
    if (context != null) {
      scope = context.getContainingFile();
    }

    final HighlightManager highlightManager = HighlightManager.getInstance(myProject);
    ResolveSnapshotProvider resolveSnapshotProvider = INSTANCE.forLanguage(scope.getLanguage());
    final ResolveSnapshotProvider.ResolveSnapshot snapshot = resolveSnapshotProvider != null ?
      resolveSnapshotProvider.createSnapshot(scope):null;
    final TemplateBuilderImpl builder = new TemplateBuilderImpl(scope);

    PsiElement nameIdentifier = getNameIdentifier();
    int offset = myEditor.getCaretModel().getOffset();
    PsiElement selectedElement = getSelectedInEditorElement(nameIdentifier, refs, stringUsages, offset);

    if (nameIdentifier != null) addVariable(nameIdentifier, selectedElement, builder, nameSuggestions);
    for (PsiReference ref : refs) {
      addVariable(ref, selectedElement, builder, offset, nameSuggestions);
    }
    for (Pair<PsiElement, TextRange> usage : stringUsages) {
      addVariable(usage.first, usage.second, selectedElement, builder, nameSuggestions);
    }
    addAdditionalVariables(builder);
    final StartMarkAction markAction;
    try {
      markAction = startRename();
    }
    catch (final StartMarkAction.AlreadyStartedException e) {
      final Document oldDocument = e.getDocument();
      if (oldDocument != myEditor.getDocument()) {
        final int exitCode = Messages.showOkCancelDialog(myProject, e.getMessage(), RENAME_TITLE,
                                                         "Navigate to continue rename", "Cancel started rename", Messages.getErrorIcon());
        if (exitCode == -1) return true;
        navigateToAlreadyStarted(oldDocument, exitCode);
        return true;
      }
      else {
        restoreStateBeforeDialogWouldBeShown();
      }
      return false;
    }

    final PsiElement scope1 = scope;
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final RangeMarker rangeMarker = myEditor.getDocument().createRangeMarker(new TextRange(myEditor.getCaretModel().getOffset(), myEditor.getCaretModel().getOffset()));
            final int offset = rangeMarker.getStartOffset();
            final SelectionModel selectionModel = myEditor.getSelectionModel();
            final TextRange selectedRange = preserveSelectedRange(selectionModel);
            Template template = builder.buildInlineTemplate();
            template.setToShortenLongNames(false);
            TextRange range = scope1.getTextRange();
            assert range != null;
            myHighlighters = new ArrayList<RangeHighlighter>();
            Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myEditor);
            topLevelEditor.getCaretModel().moveToOffset(range.getStartOffset());
            final DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
            final boolean previousUpdate;
            if (daemonCodeAnalyzer != null) {
              previousUpdate = ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).isUpdateByTimerEnabled();
              daemonCodeAnalyzer.setUpdateByTimerEnabled(false);
            }
            else {
              previousUpdate = false;
            }
            TemplateManager.getInstance(myProject).startTemplate(topLevelEditor, template, new TemplateEditingAdapter() {
              private String myNewName = null;
              public void beforeTemplateFinished(final TemplateState templateState, Template template) {
                if (daemonCodeAnalyzer != null) {
                  daemonCodeAnalyzer.setUpdateByTimerEnabled(previousUpdate);
                }
                finish();

                TextResult value = templateState.getVariableValue(PRIMARY_VARIABLE_NAME);
                myNewName = getNewName(value != null ? value.toString() : null, snapshot);
                if (myNewName != null && !LanguageNamesValidation.INSTANCE.forLanguage(scope1.getLanguage()).isIdentifier(myNewName, myProject)) {
                  performOnInvalidIdentifier(myNewName, nameSuggestions);
                  return;
                }
                if (myNewName != null && snapshot != null && performAutomaticRename()) {
                  if (LanguageNamesValidation.INSTANCE.forLanguage(scope1.getLanguage()).isIdentifier(myNewName, myProject)) {
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                      public void run() {
                        snapshot.apply(myNewName);
                      }
                    });
                  }
                }
                final int currentOffset = myEditor.getCaretModel().getOffset();
                myBeforeRevert = myRenameOffset != null && myRenameOffset.getEndOffset() >= currentOffset && myRenameOffset.getStartOffset() <= currentOffset 
                                 ? myEditor.getDocument().createRangeMarker(myRenameOffset.getStartOffset(), currentOffset) 
                                 : null;
                if (myBeforeRevert != null) {
                  myBeforeRevert.setGreedyToRight(true);
                }
                restoreStateBeforeTemplateIsFinished();
              }

              @Override
              public void templateFinished(Template template, final boolean brokenOff) {
                boolean bind = false;
                try {
                  super.templateFinished(template, brokenOff);
                  moveOffsetAfter(!brokenOff);
                  if (myNewName != null && !brokenOff) {
                    bind = true;
                    final Runnable runnable = new Runnable() {
                      public void run() {
                        performRefactoringRename(myNewName, context, markAction);
                        if (myBeforeRevert != null) {
                          myBeforeRevert.dispose();
                        }
                      }
                    };
                    if (ApplicationManager.getApplication().isUnitTestMode()){
                      runnable.run();
                    } else {
                      ApplicationManager.getApplication().invokeLater(runnable);
                    }
                  }
                }
                finally {
                  if (!bind) {
                    FinishMarkAction.finish(myProject, myEditor, markAction);
                    if (myBeforeRevert != null) {
                      myBeforeRevert.dispose();
                    }
                  }
                }
              }

              public void templateCancelled(Template template) {
                if (daemonCodeAnalyzer != null) {
                  daemonCodeAnalyzer.setUpdateByTimerEnabled(previousUpdate);
                }
                try {
                  final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
                  documentManager.commitAllDocuments();
                  finish();
                  moveOffsetAfter(false);
                  ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    public void run() {
                      documentManager.doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
                    }
                  });
                }
                finally {
                  FinishMarkAction.finish(myProject, myEditor, markAction);
                }
              }
            });

            //move to old offset
            Runnable runnable = new Runnable() {
              public void run() {
                myEditor.getCaretModel().moveToOffset(getOffsetForCaret(rangeMarker, offset));
                if (selectedRange != null){
                  myEditor.getSelectionModel().setSelection(selectedRange.getStartOffset(), selectedRange.getEndOffset());
                } else if (!shouldSelectAll()){
                  myEditor.getSelectionModel().removeSelection();
                }
              }
            };

            final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
            if (lookup != null && lookup.getLookupStart() <= (getOffsetForCaret(rangeMarker, offset))) {
              lookup.setFocused(false);
              lookup.performGuardedChange(runnable);
            } else {
              runnable.run();
            }

            //add highlights
            if (myHighlighters != null) { // can be null if finish is called during testing
              Map<TextRange, TextAttributes> rangesToHighlight = new HashMap<TextRange, TextAttributes>();
              final TemplateState templateState = TemplateManagerImpl.getTemplateState(topLevelEditor);
              if (templateState != null) {
                EditorColorsManager colorsManager = EditorColorsManager.getInstance();
                for (int i = 0; i < templateState.getSegmentsCount(); i++) {
                  final TextRange segmentOffset = templateState.getSegmentRange(i);
                  final String name = template.getSegmentName(i);
                  TextAttributes attributes = null;
                  if (name.equals(PRIMARY_VARIABLE_NAME)) {
                    attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);
                  } else if (name.equals(OTHER_VARIABLE_NAME)) {
                    attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
                  }
                  if (attributes == null) continue;
                  rangesToHighlight.put(segmentOffset, attributes);
                }
              }
              addHighlights(rangesToHighlight, topLevelEditor, myHighlighters, highlightManager);
            }
          }
        });
      }
    }, RENAME_TITLE, null);
    return true;
  }

  protected int getOffsetForCaret(RangeMarker rangeMarker, int offset) {
    return offset;
  }

  protected boolean shouldSelectAll() {
    if (Registry.is("rename.preselect")) return true;
    final Boolean selectAll = myEditor.getUserData(RenameHandlerRegistry.SELECT_ALL);
    return selectAll != null && selectAll.booleanValue();
  }

  protected void navigateToAlreadyStarted(Document oldDocument, int exitCode) {
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(oldDocument);
    if (file != null) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final FileEditor[] editors = FileEditorManager.getInstance(myProject).getEditors(virtualFile);
        for (FileEditor editor : editors) {
          if (editor instanceof TextEditor) {
            final Editor textEditor = ((TextEditor)editor).getEditor();
            final TemplateState templateState = TemplateManagerImpl.getTemplateState(textEditor);
            if (templateState != null) {
              if (exitCode == DialogWrapper.OK_EXIT_CODE) {
                final TextRange range = templateState.getVariableRange(PRIMARY_VARIABLE_NAME);
                if (range != null) {
                  new OpenFileDescriptor(myProject, virtualFile, range.getStartOffset()).navigate(true);
                  return;
                }
              } else {
                templateState.gotoEnd();
                return;
              }
            }
          }
        }
      }
    }
  }
  
  protected VariableInplaceRenamer createInplaceRenamerToRestart(PsiNamedElement variable, Editor editor, String initialName) {
    return new VariableInplaceRenamer(variable, editor, myProject, initialName, myOldName);
  }

  protected void performOnInvalidIdentifier(final String newName, final LinkedHashSet<String> nameSuggestions) {
    revertState();
    JBPopupFactory.getInstance()
      .createConfirmation("Inserted identifier is not valid", "Continue editing", "Cancel", new Runnable() {
        @Override
        public void run() {
          createInplaceRenamerToRestart(getVariable(), myEditor, newName).performInplaceRename(true, nameSuggestions);
        }
      }, 0).showInBestPositionFor(myEditor);
  }

  protected void restoreStateBeforeTemplateIsFinished(){}


  @Nullable
  protected PsiElement getNameIdentifier() {
    return myElementToRename instanceof PsiNameIdentifierOwner ? ((PsiNameIdentifierOwner)myElementToRename).getNameIdentifier() : null;
  }

  protected void restoreStateBeforeDialogWouldBeShown() {
    PsiNamedElement variable = getVariable();
    final TemplateState state = TemplateManagerImpl.getTemplateState(InjectedLanguageUtil.getTopLevelEditor(myEditor));
    assert state != null;
    final String commandName = RefactoringBundle
      .message("renaming.0.1.to.2", UsageViewUtil.getType(variable), UsageViewUtil.getDescriptiveName(variable),
               variable.getName());
    Runnable runnable = new Runnable() {
      public void run() {
        state.gotoEnd(true);
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable, commandName, null);
  }

  @Nullable
  protected String getNewName(String newName, ResolveSnapshotProvider.ResolveSnapshot snapshot) {
    if (!performAutomaticRename()) return null;
    return snapshot != null ? newName : null;
  }

  @Nullable
  protected StartMarkAction startRename() throws StartMarkAction.AlreadyStartedException {
    final StartMarkAction[] markAction = new StartMarkAction[1];
    final StartMarkAction.AlreadyStartedException[] ex = new StartMarkAction.AlreadyStartedException[1];
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        try {
          markAction[0] = StartMarkAction.start(myEditor, myProject, RENAME_TITLE);
        }
        catch (StartMarkAction.AlreadyStartedException e) {
          ex[0] = e;
        }
      }
    }, RENAME_TITLE, null);
    if (ex[0] != null) throw ex[0];
    return markAction[0];
  }

  @Nullable
  protected TextRange preserveSelectedRange(SelectionModel selectionModel) {
    if (selectionModel.hasSelection()) {
      return new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    }
    return null;
  }

  @Nullable
  protected PsiNamedElement getVariable() {
    if (myElementToRename != null && myElementToRename.isValid()) return myElementToRename;
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    if (psiFile != null) {
      return PsiTreeUtil.getParentOfType(psiFile.findElementAt(myRenameOffset.getStartOffset()), PsiNameIdentifierOwner.class);
    }
    return myElementToRename;
  }

  protected boolean performAutomaticRename() {
    return true;
  }

  /**
   * Called after the completion of the refactoring, either a successful or a failed one.
   *
   * @param success true if the refactoring was accepted, false if it was cancelled (by undo or Esc)
   */
  protected void moveOffsetAfter(boolean success) {
  }

  protected void addAdditionalVariables(TemplateBuilderImpl builder) {
  }

  protected void addReferenceAtCaret(Collection<PsiReference> refs) {
    PsiFile myEditorFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    // Note, that myEditorFile can be different from myElement.getContainingFile() e.g. in injections: myElement declaration in one
    // file / usage in another !
    final PsiReference reference = (myEditorFile != null ?
      myEditorFile:myElementToRename.getContainingFile()).findReferenceAt(myEditor.getCaretModel().getOffset());
    if (reference != null && !refs.contains(reference)) {
      refs.add(reference);
    }
  }
  protected void performRefactoringRename(final String newName,
                                            PsiElement context,
                                            final StartMarkAction markAction) {
    try {
      PsiNamedElement elementToRename = getVariable();
      for (AutomaticRenamerFactory renamerFactory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
        if (renamerFactory.isApplicable(elementToRename)) {
          final List<UsageInfo> usages = new ArrayList<UsageInfo>();
          final AutomaticRenamer renamer =
            renamerFactory.createRenamer(elementToRename, newName, new ArrayList<UsageInfo>());
          if (renamer.hasAnythingToRename()) {
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              final AutomaticRenamingDialog renamingDialog = new AutomaticRenamingDialog(myProject, renamer);
              renamingDialog.show();
              if (!renamingDialog.isOK()) return;
            }

            final Runnable runnable = new Runnable() {
              public void run() {
                renamer.findUsages(usages, false, false);
              }
            };

            if (!ProgressManager.getInstance()
              .runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("searching.for.variables"), true, myProject)) {
              return;
            }

            if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, PsiUtilBase.toPsiElementArray(renamer.getElements()))) return;
            final UsageInfo[] usageInfos = usages.toArray(new UsageInfo[usages.size()]);
            final MultiMap<PsiElement,UsageInfo> classified = RenameProcessor.classifyUsages(renamer.getElements(), usageInfos);
            for (final PsiNamedElement element : renamer.getElements()) {
              new WriteCommandAction(myProject, RENAME_TITLE) {
                @Override
                protected void run(com.intellij.openapi.application.Result result) throws Throwable {
                  final String newElementName = renamer.getNewName(element);
                  if (newElementName != null) {
                    final Collection<UsageInfo> infos = classified.get(element);
                    RenameUtil.doRenameGenericNamedElement(element, newElementName, infos.toArray(new UsageInfo[infos.size()]), null);
                  }
                }
              }.execute();
            }
          }
        }
      }
    }
    finally {
      FinishMarkAction.finish(myProject, myEditor, markAction);
    }
  }

  public void setElementToRename(PsiNamedElement elementToRename) {
    myElementToRename = elementToRename;
  }

  protected void showDialogAdvertisement(final String actionId) {
    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    final Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    if (shortcuts.length > 0) {
      setAdvertisementText("Press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to show dialog");
    }
  }

  public String getInitialName() {
    if (myInitialName == null) {
      final PsiNamedElement variable = getVariable();
      if (variable != null) {
        return variable.getName();
      }
    }
    return myInitialName;
  }

  protected void revertState() {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        final Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myEditor);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final TemplateState state = TemplateManagerImpl.getTemplateState(topLevelEditor);
            assert state != null;
            final int segmentsCount = state.getSegmentsCount();
            final Document document = topLevelEditor.getDocument();
            for (int i = 0; i < segmentsCount; i++) {
              final TextRange segmentRange = state.getSegmentRange(i);
              document.replaceString(segmentRange.getStartOffset(), segmentRange.getEndOffset(), myOldName);
            }
          }
        });
        PsiDocumentManager.getInstance(myProject).commitDocument(topLevelEditor.getDocument());
      }
    }, RENAME_TITLE, null);
  }

  private static VirtualFile getTopLevelVirtualFile(final FileViewProvider fileViewProvider) {
    VirtualFile file = fileViewProvider.getVirtualFile();
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    return file;
  }

  @TestOnly
  public static void checkCleared(){
    try {
      assert ourRenamersStack.isEmpty() : ourRenamersStack;
    }
    finally {
      ourRenamersStack.clear();
    }
  }

  public void finish() {
    if (!ourRenamersStack.isEmpty() && ourRenamersStack.peek() == this) {
      ourRenamersStack.pop();
    }
    if (myHighlighters != null) {
      if (!myProject.isDisposed()) {
        final HighlightManager highlightManager = HighlightManager.getInstance(myProject);
        for (RangeHighlighter highlighter : myHighlighters) {
          highlightManager.removeSegmentHighlighter(myEditor, highlighter);
        }
      }

      myHighlighters = null;
      myEditor.putUserData(INPLACE_RENAMER, null);
    }
  }

  protected void addHighlights(@NotNull Map<TextRange, TextAttributes> ranges, @NotNull Editor editor, @NotNull Collection<RangeHighlighter> highlighters, @NotNull HighlightManager highlightManager) {
    for (Map.Entry<TextRange,TextAttributes> entry : ranges.entrySet()) {
      TextRange range = entry.getKey();
      TextAttributes attributes = entry.getValue();
      highlightManager.addOccurrenceHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, 0, highlighters, null);
    }

    for (RangeHighlighter highlighter : highlighters) {
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
  }

  private static PsiElement getSelectedInEditorElement(@Nullable PsiElement nameIdentifier,
                                                       final Collection<PsiReference> refs,
                                                       Collection<Pair<PsiElement, TextRange>> stringUsages,
                                                       final int offset) {
    if (nameIdentifier != null) {
      final TextRange range = nameIdentifier.getTextRange()/*.shiftRight(PsiUtilBase.findInjectedElementOffsetInRealDocument(nameIdentifier))*/;
      if (contains(range, offset)) return nameIdentifier;
    }

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (contains(ref.getRangeInElement().shiftRight(element.getTextRange().getStartOffset()), offset)) return element;
    }

    for (Pair<PsiElement, TextRange> stringUsage : stringUsages) {
      final PsiElement element = stringUsage.first;
      if (contains(stringUsage.second.shiftRight(element.getTextRange().getStartOffset()), offset)) return element;
    }

    LOG.assertTrue(false);
    return null;
  }

  private static boolean contains(final TextRange range, final int offset) {
    return range.getStartOffset() <= offset && offset <= range.getEndOffset();
  }

  private void addVariable(final PsiReference reference,
                           final PsiElement selectedElement,
                           final TemplateBuilderImpl builder,
                           int offset,
                           final LinkedHashSet<String> names) {
    if (reference.getElement() == selectedElement &&
        contains(reference.getRangeInElement().shiftRight(selectedElement.getTextRange().getStartOffset()), offset)) {
      Expression expression = new MyExpression(getInitialName(), names);
      builder.replaceElement(reference, PRIMARY_VARIABLE_NAME, expression, true);
    }
    else {
      builder.replaceElement(reference, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }

  private void addVariable(final PsiElement element,
                           final PsiElement selectedElement,
                           final TemplateBuilderImpl builder,
                           final LinkedHashSet<String> names) {
    addVariable(element, null, selectedElement, builder, names);
  }

  private void addVariable(final PsiElement element,
                           @Nullable final TextRange textRange,
                           final PsiElement selectedElement,
                           final TemplateBuilderImpl builder,
                           final LinkedHashSet<String> names) {
    if (element == selectedElement) {
      Expression expression = new MyExpression(getInitialName(), names);
      builder.replaceElement(element, PRIMARY_VARIABLE_NAME, expression, true);
    } else if (textRange != null) {
      builder.replaceElement(element, textRange, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
    else {
      builder.replaceElement(element, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }

  protected LookupElement[] createLookupItems(final LookupElement[] lookupItems, final String name) {
    return lookupItems;
  }

  private class MyExpression extends Expression {
    private final String myName;
    private final LookupElement[] myLookupItems;

    private MyExpression(String name, LinkedHashSet<String> names) {
      myName = name;
      if (names == null) {
        names = new LinkedHashSet<String>();
        for(NameSuggestionProvider provider: Extensions.getExtensions(NameSuggestionProvider.EP_NAME)) {
          provider.getSuggestedNames(myElementToRename, myElementToRename, names);
        }
      }
      myLookupItems = new LookupElement[names.size()];
      final Iterator<String> iterator = names.iterator();
      for (int i = 0; i < myLookupItems.length; i++) {
        final String suggestion = iterator.next();
        myLookupItems[i] = LookupElementBuilder.create(suggestion).setInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            if (shouldSelectAll()) return;
            final Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myEditor);
            final TemplateState templateState = TemplateManagerImpl.getTemplateState(topLevelEditor);
            if (templateState != null) {
              final TextRange range = templateState.getCurrentVariableRange();
              if (range != null) {
                topLevelEditor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), suggestion);
              }
            }
          }
        });
      }
    }

    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      return createLookupItems(myLookupItems, myName);
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return calculateResult(context);
    }

    public Result calculateResult(ExpressionContext context) {
      TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
      final TextResult insertedValue = templateState != null ? templateState.getVariableValue(PRIMARY_VARIABLE_NAME) : null;
      if (insertedValue != null) {
        if (!insertedValue.getText().isEmpty()) return insertedValue;
      }
      return new TextResult(myName);
    }

    @Override
    public String getAdvertisingText() {
      return myAdvertisementText;
    }
  }

  private class RenameChooser {
    @NonNls private static final String CODE_OCCURRENCES = "Rename code occurrences";
    @NonNls private static final String ALL_OCCURRENCES = "Rename all occurrences";
    private final Set<RangeHighlighter> myRangeHighlighters = new HashSet<RangeHighlighter>();
    private final Editor myEditor;
    private final TextAttributes myAttributes;

    public RenameChooser(Editor editor) {
      myEditor = editor;
      myAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    }

    public void showChooser(final Collection<PsiReference> refs,
                            final List<Pair<PsiElement, TextRange>> stringUsages,
                            final LinkedHashSet<String> nameSuggestions,
                            final PsiElement scope,
                            final PsiFile containingFile) {

      final DefaultListModel model = new DefaultListModel();
      model.addElement(CODE_OCCURRENCES);
      model.addElement(ALL_OCCURRENCES);
      final JList list = new JBList(model);

      list.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(final ListSelectionEvent e) {
          final String selectedValue = (String)list.getSelectedValue();
          if (selectedValue == null) return;
          dropHighlighters();
          final MarkupModel markupModel = myEditor.getMarkupModel();

          if (selectedValue == ALL_OCCURRENCES) {
            for (Pair<PsiElement, TextRange> pair : stringUsages) {
              final TextRange textRange = pair.second.shiftRight(pair.first.getTextOffset());
              final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
                textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1, myAttributes,
                HighlighterTargetArea.EXACT_RANGE);
              myRangeHighlighters.add(rangeHighlighter);
            }
          }

          for (PsiReference reference : refs) {
            final PsiElement element = reference.getElement();
            if (element == null) continue;
            final TextRange textRange = element.getTextRange();
            final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
              textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1, myAttributes,
              HighlighterTargetArea.EXACT_RANGE);
            myRangeHighlighters.add(rangeHighlighter);
          }
        }
      });

      JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("String occurrences found")
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(new Runnable() {
          public void run() {
            runRenameTemplate(nameSuggestions, refs, list.getSelectedValue() == ALL_OCCURRENCES ? stringUsages : new ArrayList<Pair<PsiElement, TextRange>>(), scope, containingFile);
          }
        })
        .addListener(new JBPopupAdapter() {
          @Override
          public void onClosed(LightweightWindowEvent event) {
            dropHighlighters();
          }
        })
        .createPopup().showInBestPositionFor(myEditor);
    }



    private void dropHighlighters() {
      for (RangeHighlighter highlight : myRangeHighlighters) {
        highlight.dispose();
      }
      myRangeHighlighters.clear();
    }
  }
}
