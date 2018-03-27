/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormatTextRanges;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiTreeDebugBuilder;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.codeStyle.IndentHelperImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.TextRangeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class PostprocessReformattingAspect implements PomModelAspect {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PostprocessReformattingAspect");
  private final Project myProject;
  private final PsiManager myPsiManager;
  private final TreeAspect myTreeAspect;
  private static final Key<Throwable> REFORMAT_ORIGINATOR = Key.create("REFORMAT_ORIGINATOR");
  private static final Key<Boolean> REPARSE_PENDING = Key.create("REPARSE_PENDING");

  private static class Holder {
    private static final boolean STORE_REFORMAT_ORIGINATOR_STACKTRACE = ApplicationManager.getApplication().isInternal();
  }

  private final ThreadLocal<Context> myContext = ThreadLocal.withInitial(Context::new);

  public PostprocessReformattingAspect(Project project, PsiManager psiManager, TreeAspect treeAspect,final CommandProcessor processor) {
    myProject = project;
    myPsiManager = psiManager;
    myTreeAspect = treeAspect;
    PomManager.getModel(psiManager.getProject())
      .registerAspect(PostprocessReformattingAspect.class, this, Collections.singleton(treeAspect));

    ApplicationListener applicationListener = new ApplicationAdapter() {
      @Override
      public void writeActionStarted(@NotNull final Object action) {
        if (processor != null) {
          final Project project = processor.getCurrentCommandProject();
          if (project == myProject) {
            incrementPostponedCounter();
          }
        }
      }

      @Override
      public void writeActionFinished(@NotNull final Object action) {
        if (processor != null) {
          final Project project = processor.getCurrentCommandProject();
          if (project == myProject) {
            decrementPostponedCounter();
          }
        }
      }
    };
    ApplicationManager.getApplication().addApplicationListener(applicationListener, project);
  }

  public void disablePostprocessFormattingInside(@NotNull final Runnable runnable) {
    disablePostprocessFormattingInside((NullableComputable<Object>)() -> {
      runnable.run();
      return null;
    });
  }

  public <T> T disablePostprocessFormattingInside(@NotNull Computable<T> computable) {
    try {
      getContext().myDisabledCounter++;
      return computable.compute();
    }
    finally {
      getContext().myDisabledCounter--;
      LOG.assertTrue(getContext().myDisabledCounter > 0 || !isDisabled());
    }
  }

  public void postponeFormattingInside(@NotNull final Runnable runnable) {
    postponeFormattingInside((NullableComputable<Object>)() -> {
      runnable.run();
      return null;
    });
  }

  public <T> T postponeFormattingInside(@NotNull Computable<T> computable) {
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    try {
      incrementPostponedCounter();
      return computable.compute();
    }
    finally {
      decrementPostponedCounter();
    }
  }

  private void incrementPostponedCounter() {
    getContext().myPostponedCounter++;
  }

  private void decrementPostponedCounter() {
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    if (--getContext().myPostponedCounter == 0) {
      if (application.isWriteAccessAllowed()) {
        doPostponedFormatting();
      }
      else {
        application.runWriteAction((Runnable)this::doPostponedFormatting);
      }
    }
  }

  private static void atomic(@NotNull Runnable r) {
    ProgressManager.getInstance().executeNonCancelableSection(r);
  }

  @Override
  public void update(@NotNull final PomModelEvent event) {
    atomic(new Runnable() {
      @Override
      public void run() {
        if (isDisabled() || getContext().myPostponedCounter == 0 && !ApplicationManager.getApplication().isUnitTestMode()) return;
        final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myTreeAspect);
        if (changeSet == null) return;
        final PsiElement psiElement = changeSet.getRootElement().getPsi();
        if (psiElement == null) return;
        PsiFile containingFile = InjectedLanguageManager.getInstance(psiElement.getProject()).getTopLevelFile(psiElement);
        final FileViewProvider viewProvider = containingFile.getViewProvider();

        if (!viewProvider.isEventSystemEnabled()) return;
        getContext().myUpdatedProviders.add(viewProvider);
        for (final ASTNode node : changeSet.getChangedElements()) {
          final TreeChange treeChange = changeSet.getChangesByElement(node);
          for (final ASTNode affectedChild : treeChange.getAffectedChildren()) {
            checkForOuters(containingFile, (TreeElement)affectedChild);

            final ChangeInfo childChange = treeChange.getChangeByChild(affectedChild);
            switch (childChange.getChangeType()) {
              case ChangeInfo.ADD:
              case ChangeInfo.REPLACE:
                postponeFormatting(viewProvider, affectedChild);
                break;
              case ChangeInfo.CONTENTS_CHANGED:
                if (!CodeEditUtil.isNodeGenerated(affectedChild)) {
                  ((TreeElement)affectedChild).acceptTree(new RecursiveTreeElementWalkingVisitor() {
                    @Override
                    protected void visitNode(TreeElement element) {
                      if (CodeEditUtil.isNodeGenerated(element) && CodeEditUtil.isSuspendedNodesReformattingAllowed()) {
                        postponeFormatting(viewProvider, element);
                        return;
                      }
                      super.visitNode(element);
                    }
                  });
                }
                break;
            }
          }
        }
      }

      private void checkForOuters(PsiFile containingFile, TreeElement affectedChild) {
        if (TreeUtil.containsOuterLanguageElements(affectedChild)) {
          containingFile.putUserData(REPARSE_PENDING, true);
        }
      }
    });
  }

  public void doPostponedFormatting() {
    atomic(() -> {
      if (isDisabled()) return;
      try {
        FileViewProvider[] viewProviders = getContext().myUpdatedProviders.toArray(new FileViewProvider[getContext().myUpdatedProviders.size()]);
        for (final FileViewProvider viewProvider : viewProviders) {
          doPostponedFormatting(viewProvider);
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        LOG.assertTrue(getContext().myReformatElements.isEmpty(), getContext().myReformatElements);
      }
    });
  }

  public void doPostponedFormatting(@NotNull FileViewProvider viewProvider) {
    postponedFormattingImpl(viewProvider);
  }

  private void postponedFormattingImpl(@NotNull final FileViewProvider viewProvider) {
    atomic(() -> {
      if (isDisabled()) return;

      try {
        disablePostprocessFormattingInside(() -> doPostponedFormattingInner(viewProvider));
      }
      finally {
        getContext().myUpdatedProviders.remove(viewProvider);
        getContext().myReformatElements.remove(viewProvider);
        viewProvider.putUserData(REFORMAT_ORIGINATOR, null);
      }
    });
  }

  public boolean isViewProviderLocked(@NotNull FileViewProvider fileViewProvider) {
    return getContext().myReformatElements.containsKey(fileViewProvider);
  }

  /**
   * Checks that view provider doesn't contain any PSI modifications which will be used in postponed formatting and may conflict with
   * changes made to the document.
   *
   * @param viewProvider The view provider to validate.
   * @throws RuntimeException If the assertion fails.
   */
  public void assertDocumentChangeIsAllowed(@NotNull FileViewProvider viewProvider) {
    if (isViewProviderLocked(viewProvider)) {
      Throwable cause = viewProvider.getUserData(REFORMAT_ORIGINATOR);
      @NonNls String message = "Document is locked by write PSI operations. " +
                               "Use PsiDocumentManager.doPostponedOperationsAndUnblockDocument() to commit PSI changes to the document." +
                               "\nUnprocessed elements: " + dumpUnprocessedElements(viewProvider) +
                               (cause == null ? "" : " \nSee cause stacktrace for the reason to lock.");
      throw cause == null ? new RuntimeException(message): new RuntimeException(message, cause);
    }
  }

  private String dumpUnprocessedElements(@NotNull FileViewProvider provider) {
    StringBuilder sb = new StringBuilder();
    int count = 0;
    List<ASTNode> nodes = myContext.get().myReformatElements.get(provider);
    for (ASTNode node : nodes) {
      if (count >= 5) {
        sb.append(" and ").append(nodes.size() - count).append(" more.");
        break;
      }
      if (sb.length() > 0) sb.append(", ");
      sb.append(node.getElementType()).append(node.getTextRange());
      count ++;
    }
    return sb.toString();
  }

  public static PostprocessReformattingAspect getInstance(Project project) {
    return project.getComponent(PostprocessReformattingAspect.class);
  }

  private void postponeFormatting(@NotNull FileViewProvider viewProvider, @NotNull ASTNode child) {
    if (!CodeEditUtil.isNodeGenerated(child) && child.getElementType() != TokenType.WHITE_SPACE) {
      final int oldIndent = CodeEditUtil.getOldIndentation(child);
      LOG.assertTrue(oldIndent >= 0,
                     "for not generated items old indentation must be defined: element=" + child + ", text=" + child.getText());
    }
    List<ASTNode> list = getContext().myReformatElements.get(viewProvider);
    if (list == null) {
      list = new ArrayList<>();
      getContext().myReformatElements.put(viewProvider, list);
      if (Holder.STORE_REFORMAT_ORIGINATOR_STACKTRACE) {
        viewProvider.putUserData(REFORMAT_ORIGINATOR, new Throwable());
      }
    }
    list.add(child);
  }

  private void doPostponedFormattingInner(@NotNull FileViewProvider key) {
    List<ASTNode> astNodes = getContext().myReformatElements.remove(key);
    final Document document = key.getDocument();
    // Sort ranges by end offsets so that we won't need any offset adjustment after reformat or reindent
    if (document == null) return;

    final VirtualFile virtualFile = key.getVirtualFile();
    if (!virtualFile.isValid()) return;

    PsiManager manager = key.getManager();
    if (manager instanceof PsiManagerEx) {
      FileManager fileManager = ((PsiManagerEx)manager).getFileManager();
      FileViewProvider viewProvider = fileManager.findCachedViewProvider(virtualFile);
      if (viewProvider != key) { // viewProvider was invalidated e.g. due to language level change
        if (viewProvider == null) viewProvider = fileManager.findViewProvider(virtualFile);
        if (viewProvider != null) {
          key = viewProvider;
          astNodes = getContext().myReformatElements.remove(key);
        }
      }
    }

    reparseByTextIfNeeded(key, document);

    Collection<Disposable> toDispose = Collections.emptyList();
    try {
      // process all roots in viewProvider to find marked for reformat before elements and create appropriate range markers
      Set<PostprocessFormattingTask> postProcessTasks = new TreeSet<>();
      handleReformatMarkers(key, postProcessTasks);
      toDispose = new ArrayList<>(postProcessTasks);

      // then we create ranges by changed nodes. One per node. There ranges can intersect. Ranges are sorted by end offset.
      if (astNodes != null) createActionsMap(astNodes, key, postProcessTasks);

      if (Boolean.getBoolean("check.psi.is.valid") && ApplicationManager.getApplication().isUnitTestMode()) {
        checkPsiIsCorrect(key);
      }

      while (!postProcessTasks.isEmpty()) {
        // now we have to normalize actions so that they not intersect and ordered in most appropriate way
        // (free reformatting -> reindent -> formatting under reindent)
        final List<PostponedAction> normalizedActions = normalizeAndReorderPostponedActions(postProcessTasks, document);
        toDispose.addAll(normalizedActions);

        // only in following loop real changes in document are made
        final FileViewProvider viewProvider = key;
        for (final PostponedAction normalizedAction : normalizedActions) {
          CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myPsiManager.getProject());
          codeStyleManager.runWithDocCommentFormattingDisabled(
            viewProvider.getPsi(viewProvider.getBaseLanguage()), () -> normalizedAction.execute(viewProvider));
        }
      }
    }
    finally {
      for (Disposable disposable : toDispose) {
        //noinspection SSBasedInspection
        disposable.dispose();
      }
    }
  }

  private void reparseByTextIfNeeded(@NotNull FileViewProvider viewProvider, @NotNull Document document) {
    if (PsiDocumentManager.getInstance(myProject).isCommitted(document)) {
      for (PsiFile file : viewProvider.getAllFiles()) {
        if (file.getUserData(REPARSE_PENDING) != null) {
          ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject)).reparseFileFromText((PsiFileImpl)file);
          file.putUserData(REPARSE_PENDING, null);
        }
      }
    }
  }

  private void checkPsiIsCorrect(@NotNull FileViewProvider key) {
    PsiFile actualPsi = key.getPsi(key.getBaseLanguage());

    PsiTreeDebugBuilder treeDebugBuilder = new PsiTreeDebugBuilder().setShowErrorElements(false).setShowWhiteSpaces(false);

    String actualPsiTree = treeDebugBuilder.psiToString(actualPsi);

    String fileName = key.getVirtualFile().getName();
    PsiFile psi = PsiFileFactory.getInstance(myProject)
      .createFileFromText(fileName, FileTypeManager.getInstance().getFileTypeByFileName(fileName), actualPsi.getNode().getText(),
                          LocalTimeCounter.currentTime(), false);

    if (actualPsi.getClass().equals(psi.getClass())) {
      String expectedPsi = treeDebugBuilder.psiToString(psi);

      if (!expectedPsi.equals(actualPsiTree)) {
        getContext().myReformatElements.clear();
        assert expectedPsi.equals(actualPsiTree) : "Refactored psi should be the same as result of parsing";
      }
    }
  }

  @NotNull
  private List<PostponedAction> normalizeAndReorderPostponedActions(@NotNull Set<PostprocessFormattingTask> rangesToProcess, @NotNull Document document) {
    final List<PostprocessFormattingTask> freeFormattingActions = new ArrayList<>();
    final List<ReindentTask> indentActions = new ArrayList<>();

    PostprocessFormattingTask accumulatedTask = null;
    Iterator<PostprocessFormattingTask> iterator = rangesToProcess.iterator();
    while (iterator.hasNext()) {
      final PostprocessFormattingTask currentTask = iterator.next();
      if (accumulatedTask == null) {
        accumulatedTask = currentTask;
        iterator.remove();
      }
      else if (accumulatedTask.getStartOffset() > currentTask.getEndOffset() ||
               accumulatedTask.getStartOffset() == currentTask.getEndOffset() &&
                !canStickActionsTogether(accumulatedTask, currentTask)) {
        // action can be pushed
        if (accumulatedTask instanceof ReindentTask) {
          indentActions.add((ReindentTask) accumulatedTask);
        }
        else {
          freeFormattingActions.add(accumulatedTask);
        }

        accumulatedTask = currentTask;
        iterator.remove();
      }
      else if (accumulatedTask instanceof ReformatTask && currentTask instanceof ReindentTask) {
        // split accumulated reformat range into two
        if (accumulatedTask.getStartOffset() < currentTask.getStartOffset()) {
          final RangeMarker endOfRange = document.createRangeMarker(accumulatedTask.getStartOffset(), currentTask.getStartOffset());
          // add heading reformat part
          rangesToProcess.add(new ReformatTask(endOfRange));
          // and manage heading whitespace because formatter does not edit it in previous action
          iterator = rangesToProcess.iterator();
          //noinspection StatementWithEmptyBody
          while (iterator.next().getRange() != currentTask.getRange()) ;
        }
        final RangeMarker rangeToProcess = document.createRangeMarker(currentTask.getEndOffset(), accumulatedTask.getEndOffset());
        freeFormattingActions.add(new ReformatWithHeadingWhitespaceTask(rangeToProcess));
        accumulatedTask = currentTask;
        iterator.remove();
      }
      else {
        if (!(accumulatedTask instanceof ReindentTask)) {
          iterator.remove();

          boolean withLeadingWhitespace = accumulatedTask instanceof ReformatWithHeadingWhitespaceTask;
          if (accumulatedTask instanceof ReformatTask &&
              currentTask instanceof ReformatWithHeadingWhitespaceTask &&
              accumulatedTask.getStartOffset() == currentTask.getStartOffset()) {
            withLeadingWhitespace = true;
          }
          else if (accumulatedTask instanceof ReformatWithHeadingWhitespaceTask &&
              currentTask instanceof ReformatTask &&
              accumulatedTask.getStartOffset() < currentTask.getStartOffset()) {
            withLeadingWhitespace = false;
          }
          int newStart = Math.min(accumulatedTask.getStartOffset(), currentTask.getStartOffset());
          int newEnd = Math.max(accumulatedTask.getEndOffset(), currentTask.getEndOffset());
          RangeMarker rangeMarker;

          if (accumulatedTask.getStartOffset() == newStart && accumulatedTask.getEndOffset() == newEnd) {
            rangeMarker = accumulatedTask.getRange();
          }
          else if (currentTask.getStartOffset() == newStart && currentTask.getEndOffset() == newEnd) {
            rangeMarker = currentTask.getRange();
          }
          else {
            rangeMarker = document.createRangeMarker(newStart, newEnd);
          }

          accumulatedTask = withLeadingWhitespace ? new ReformatWithHeadingWhitespaceTask(rangeMarker) : new ReformatTask(rangeMarker);
        }
        else if (currentTask instanceof ReindentTask) {
          iterator.remove();
        } // TODO[ik]: need to be fixed to correctly process indent inside indent
      }
    }
    if (accumulatedTask != null) {
      if (accumulatedTask instanceof ReindentTask) {
        indentActions.add((ReindentTask) accumulatedTask);
      }
      else {
        freeFormattingActions.add(accumulatedTask);
      }
    }

    Collections.reverse(freeFormattingActions);
    Collections.reverse(indentActions);

    List<PostponedAction> result = new ArrayList<>();
    if (!freeFormattingActions.isEmpty()) {
      FormatTextRanges ranges = new FormatTextRanges();
      for (PostprocessFormattingTask action : freeFormattingActions) {
        TextRange range = TextRange.create(action);
        ranges.add(range, action instanceof ReformatWithHeadingWhitespaceTask);
      }
      result.add(new ReformatRangesAction(ranges));
    }

    if (!indentActions.isEmpty()) {
      ReindentRangesAction reindentRangesAction = new ReindentRangesAction();
      for (ReindentTask action : indentActions) {
        reindentRangesAction.add(action.getRange(), action.getOldIndent());
      }
      result.add(reindentRangesAction);
    }

    return result;
  }

  private static boolean canStickActionsTogether(final PostprocessFormattingTask currentTask,
                                                 final PostprocessFormattingTask nextTask) {
    // empty reformat markers can't be stuck together with any action
    if (nextTask instanceof ReformatWithHeadingWhitespaceTask && nextTask.getStartOffset() == nextTask.getEndOffset()) return false;
    if (currentTask instanceof ReformatWithHeadingWhitespaceTask && currentTask.getStartOffset() == currentTask.getEndOffset()) {
      return false;
    }
    // reindent actions can't be be stuck at all
    return !(currentTask instanceof ReindentTask);
  }

  private static void createActionsMap(@NotNull List<ASTNode> astNodes,
                                       @NotNull FileViewProvider provider,
                                       @NotNull Collection<PostprocessFormattingTask> rangesToProcess) {
    final Set<ASTNode> nodesToProcess = new HashSet<>(astNodes);
    final Document document = provider.getDocument();
    if (document == null) {
      return;
    }
    for (final ASTNode node : astNodes) {
      nodesToProcess.remove(node);
      final FileElement fileElement = TreeUtil.getFileElement((TreeElement)node);
      if (fileElement == null || ((PsiFile)fileElement.getPsi()).getViewProvider() != provider) continue;
      final boolean isGenerated = CodeEditUtil.isNodeGenerated(node);

      ((TreeElement)node).acceptTree(new RecursiveTreeElementVisitor() {
        private boolean inGeneratedContext = !isGenerated;

        @Override
        protected boolean visitNode(TreeElement element) {
          if (nodesToProcess.contains(element)) return false;

          final boolean currentNodeGenerated = CodeEditUtil.isNodeGenerated(element);
          CodeEditUtil.setNodeGenerated(element, false);
          if (currentNodeGenerated && !inGeneratedContext) {
            rangesToProcess.add(new ReformatTask(document.createRangeMarker(element.getTextRange())));
            inGeneratedContext = true;
          }
          if (!currentNodeGenerated && inGeneratedContext) {
            if (element.getElementType() == TokenType.WHITE_SPACE) return false;
            int oldIndent = CodeEditUtil.getOldIndentation(element);
            if (oldIndent < 0) {
              LOG.warn("For not generated items old indentation must be defined: element " + element);
              oldIndent = 0;
            }
            CodeEditUtil.setOldIndentation(element, -1);
            for (TextRange indentRange : getEnabledRanges(element.getPsi())) {
              rangesToProcess.add(new ReindentTask(document.createRangeMarker(indentRange), oldIndent));
            }
            inGeneratedContext = false;
          }
          return true;
        }

        private Iterable<TextRange> getEnabledRanges(@NotNull PsiElement element) {
          List<TextRange> disabledRanges = new ArrayList<>();
          for (DisabledIndentRangesProvider rangesProvider : DisabledIndentRangesProvider.EP_NAME.getExtensions()) {
            Collection<TextRange> providedDisabledRanges = rangesProvider.getDisabledIndentRanges(element);
            if (providedDisabledRanges != null) {
              disabledRanges.addAll(providedDisabledRanges);
            }
          }
          return TextRangeUtil.excludeRanges(element.getTextRange(), disabledRanges);
        }

        @Override
        public void visitComposite(CompositeElement composite) {
          boolean oldGeneratedContext = inGeneratedContext;
          super.visitComposite(composite);
          inGeneratedContext = oldGeneratedContext;
        }

        @Override
        public void visitLeaf(LeafElement leaf) {
          boolean oldGeneratedContext = inGeneratedContext;
          super.visitLeaf(leaf);
          inGeneratedContext = oldGeneratedContext;
        }
      });
    }
  }

  private static void handleReformatMarkers(@NotNull final FileViewProvider key, @NotNull final Set<PostprocessFormattingTask> rangesToProcess) {
    final Document document = key.getDocument();
    if (document == null) {
      return;
    }
    for (final FileElement fileElement : ((AbstractFileViewProvider)key).getKnownTreeRoots()) {
      fileElement.acceptTree(new RecursiveTreeElementWalkingVisitor() {
        @Override
        protected void visitNode(TreeElement element) {
          if (CodeEditUtil.isMarkedToReformatBefore(element)) {
            CodeEditUtil.markToReformatBefore(element, false);
            rangesToProcess.add(new ReformatWithHeadingWhitespaceTask(
              document.createRangeMarker(element.getStartOffset(), element.getStartOffset()))
            );
          }
          else if (CodeEditUtil.isMarkedToReformat(element)) {
            CodeEditUtil.markToReformat(element, false);
            rangesToProcess.add(new ReformatWithHeadingWhitespaceTask(
              document.createRangeMarker(element.getStartOffset(), element.getStartOffset() + element.getTextLength()))
            );
          }
          super.visitNode(element);
        }
      });
    }
  }

  private static void adjustIndentationInRange(@NotNull PsiFile file,
                                               @NotNull Document document,
                                               @NotNull TextRange[] indents,
                                               final int indentAdjustment) {
    final CharSequence charsSequence = document.getCharsSequence();
    for (final TextRange indent : indents) {
      final String oldIndentStr = charsSequence.subSequence(indent.getStartOffset() + 1, indent.getEndOffset()).toString();
      final int oldIndent = IndentHelperImpl.getIndent(file.getProject(), file.getFileType(), oldIndentStr, true);
      final String newIndentStr = IndentHelperImpl
        .fillIndent(CodeStyle.getIndentOptions(file), Math.max(oldIndent + indentAdjustment, 0));
      document.replaceString(indent.getStartOffset() + 1, indent.getEndOffset(), newIndentStr);
    }
  }

  private static int getNewIndent(@NotNull PsiFile psiFile, final int firstWhitespace) {
    final Document document = psiFile.getViewProvider().getDocument();
    assert document != null;
    final int startOffset = document.getLineStartOffset(document.getLineNumber(firstWhitespace));
    int endOffset = startOffset;
    final CharSequence charsSequence = document.getCharsSequence();
    //noinspection StatementWithEmptyBody
    while (Character.isWhitespace(charsSequence.charAt(endOffset++))) ;
    final String newIndentStr = charsSequence.subSequence(startOffset, endOffset - 1).toString();
    return IndentHelperImpl.getIndent(psiFile.getProject(), psiFile.getFileType(), newIndentStr, true);
  }

  public boolean isDisabled() {
    return getContext().myDisabledCounter > 0;
  }

  @NotNull
  private CodeFormatterFacade getFormatterFacade(@NotNull FileViewProvider viewProvider) {
    final CodeStyleSettings styleSettings = CodeStyle.getSettings(viewProvider.getPsi(viewProvider.getBaseLanguage()));
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myPsiManager.getProject());
    final Document document = viewProvider.getDocument();
    assert document != null;
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(styleSettings, viewProvider.getBaseLanguage());

    documentManager.commitDocument(document);
    return codeFormatter;
  }

  private abstract static class PostprocessFormattingTask implements Comparable<PostprocessFormattingTask>, Segment, Disposable {
    @NotNull private final RangeMarker myRange;

    PostprocessFormattingTask(@NotNull RangeMarker rangeMarker) {
      myRange = rangeMarker;
    }

    @Override
    public int compareTo(@NotNull PostprocessFormattingTask o) {
      RangeMarker o1 = myRange;
      RangeMarker o2 = o.myRange;
      if (o1.equals(o2)) return 0;
      final int diff = o2.getEndOffset() - o1.getEndOffset();
      if (diff == 0) {
        if (o1.getStartOffset() == o2.getStartOffset()) return 0;
        if (o1.getStartOffset() == o1.getEndOffset()) return -1; // empty ranges first
        if (o2.getStartOffset() == o2.getEndOffset()) return 1; // empty ranges first
        return o1.getStartOffset() - o2.getStartOffset();
      }
      return diff;
    }

    @NotNull
    public RangeMarker getRange() {
      return myRange;
    }

    @Override
    public int getStartOffset() {
      return myRange.getStartOffset();
    }

    @Override
    public int getEndOffset() {
      return myRange.getEndOffset();
    }

    @Override
    public void dispose() {
      if (myRange.isValid()) {
        myRange.dispose();
      }
    }
  }

  private static class ReformatTask extends PostprocessFormattingTask {
    ReformatTask(@NotNull RangeMarker rangeMarker) {
      super(rangeMarker);
    }
  }

  private static class ReformatWithHeadingWhitespaceTask extends PostprocessFormattingTask {
    ReformatWithHeadingWhitespaceTask(@NotNull RangeMarker rangeMarker) {
      super(rangeMarker);
    }
  }

  private static class ReindentTask extends PostprocessFormattingTask {
    private final int myOldIndent;

    ReindentTask(@NotNull RangeMarker rangeMarker, int oldIndent) {
      super(rangeMarker);
      myOldIndent = oldIndent;
    }

    int getOldIndent() {
      return myOldIndent;
    }
  }

  private interface PostponedAction extends Disposable {
    void execute(@NotNull FileViewProvider viewProvider);
  }

  private class ReformatRangesAction implements PostponedAction {
    private final FormatTextRanges myRanges;

    ReformatRangesAction(@NotNull FormatTextRanges ranges) {
      myRanges = ranges;
    }

    @Override
    public void execute(@NotNull FileViewProvider viewProvider) {
      final CodeFormatterFacade codeFormatter = getFormatterFacade(viewProvider);
      codeFormatter.setReformatContext(true);
      codeFormatter.processText(viewProvider.getPsi(viewProvider.getBaseLanguage()), myRanges.ensureNonEmpty(), false);
    }

    @Override
    public void dispose() {
    }
  }

  private static class ReindentRangesAction implements PostponedAction {
    private final List<Pair<Integer, RangeMarker>> myRangesToReindent = new ArrayList<>();

    public void add(@NotNull RangeMarker rangeMarker, int oldIndent) {
      myRangesToReindent.add(new Pair<>(oldIndent, rangeMarker));
    }

    @Override
    public void execute(@NotNull FileViewProvider viewProvider) {
      final Document document = viewProvider.getDocument();
      assert document != null;
      final PsiFile psiFile = viewProvider.getPsi(viewProvider.getBaseLanguage());
      for (Pair<Integer, RangeMarker> integerRangeMarkerPair : myRangesToReindent) {
        RangeMarker marker = integerRangeMarkerPair.second;
        final CharSequence charsSequence = document.getCharsSequence().subSequence(marker.getStartOffset(), marker.getEndOffset());
        final int oldIndent = integerRangeMarkerPair.first;
        final TextRange[] whitespaces = CharArrayUtil.getIndents(charsSequence, marker.getStartOffset());
        final int indentAdjustment = getNewIndent(psiFile, marker.getStartOffset()) - oldIndent;
        if (indentAdjustment != 0) adjustIndentationInRange(psiFile, document, whitespaces, indentAdjustment);
      }
    }

    @Override
    public void dispose() {
      for (Pair<Integer, RangeMarker> pair : myRangesToReindent) {
        RangeMarker marker = pair.second;
        if (marker.isValid()) {
          marker.dispose();
        }
      }
    }
  }

  @TestOnly
  public void clear() {
    getContext().myReformatElements.clear();
  }

  private Context getContext() {
    return myContext.get();
  }

  private static class Context {
    private int myPostponedCounter;
    private int myDisabledCounter;
    private final Set<FileViewProvider> myUpdatedProviders = new HashSet<>();
    private final Map<FileViewProvider, List<ASTNode>> myReformatElements = new HashMap<>();
  }
}
