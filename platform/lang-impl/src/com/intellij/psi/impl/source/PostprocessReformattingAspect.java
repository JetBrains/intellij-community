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

package com.intellij.psi.impl.source;

import com.intellij.formatting.FormatTextRanges;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.Disposable;
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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.PsiTreeDebugBuilder;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.codeStyle.HelperFactory;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class PostprocessReformattingAspect implements PomModelAspect, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PostprocessReformatingAspect");
  private final Project myProject;
  private final PsiManager myPsiManager;
  private final TreeAspect myTreeAspect;
  private final Map<FileViewProvider, List<ASTNode>> myReformatElements = new HashMap<FileViewProvider, List<ASTNode>>();
  private volatile int myDisabledCounter = 0;
  private final Set<FileViewProvider> myUpdatedProviders = new HashSet<FileViewProvider>();

  private final ApplicationListener myApplicationListener = new ApplicationAdapter() {
    public void writeActionStarted(final Object action) {
      final CommandProcessor processor = CommandProcessor.getInstance();
      if (processor != null) {
        final Project project = processor.getCurrentCommandProject();
        if (project == myProject) {
          myPostponedCounter++;
        }
      }
    }

    public void writeActionFinished(final Object action) {
      final CommandProcessor processor = CommandProcessor.getInstance();
      if (processor != null) {
        final Project project = processor.getCurrentCommandProject();
        if (project == myProject) {
          decrementPostponedCounter();
        }
      }
    }
  };

  public PostprocessReformattingAspect(Project project, PsiManager psiManager, TreeAspect treeAspect) {
    myProject = project;
    myPsiManager = psiManager;
    myTreeAspect = treeAspect;
    PomManager.getModel(psiManager.getProject())
      .registerAspect(PostprocessReformattingAspect.class, this, Collections.singleton((PomModelAspect)treeAspect));

    ApplicationManager.getApplication().addApplicationListener(myApplicationListener);
    Disposer.register(project, this);
  }

  public void dispose() {
    ApplicationManager.getApplication().removeApplicationListener(myApplicationListener);
  }

  public void disablePostprocessFormattingInside(final Runnable runnable) {
    disablePostprocessFormattingInside(new NullableComputable<Object>() {
      public Object compute() {
        runnable.run();
        return null;
      }
    });
  }

  public <T> T disablePostprocessFormattingInside(Computable<T> computable) {
    try {
      myDisabledCounter++;
      return computable.compute();
    }
    finally {
      myDisabledCounter--;
      LOG.assertTrue(myDisabledCounter > 0 || !isDisabled());
    }
  }

  private int myPostponedCounter = 0;

  public void postponeFormattingInside(final Runnable runnable) {
    postponeFormattingInside(new NullableComputable<Object>() {
      public Object compute() {
        runnable.run();
        return null;
      }
    });
  }

  public <T> T postponeFormattingInside(Computable<T> computable) {
    try {
      //if(myPostponedCounter == 0) myDisabled = false;
      myPostponedCounter++;
      return computable.compute();
    }
    finally {
      decrementPostponedCounter();
    }
  }

  private void decrementPostponedCounter() {
    if (--myPostponedCounter == 0) {
      if (!ApplicationManager.getApplication().isWriteAccessAllowed()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            doPostponedFormatting();
          }
        });
      }
      else {
        doPostponedFormatting();
      }
      //myDisabled = true;
    }
  }

  private final Object LOCK = new Object();

  private void atomic(Runnable r) {
    synchronized (LOCK) {
      ProgressManager.getInstance().executeNonCancelableSection(r);
    }
  }

  public void update(final PomModelEvent event) {
    atomic(new Runnable() {
      public void run() {
        if (isDisabled() || myPostponedCounter == 0 && !ApplicationManager.getApplication().isUnitTestMode()) return;
        final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myTreeAspect);
        if (changeSet == null) return;
        final PsiElement psiElement = changeSet.getRootElement().getPsi();
        if (psiElement == null) return;
        PsiFile containingFile = InjectedLanguageUtil.getTopLevelFile(psiElement);
        final FileViewProvider viewProvider = containingFile.getViewProvider();

        if (!viewProvider.isEventSystemEnabled()) return;
        myUpdatedProviders.add(viewProvider);
        for (final ASTNode node : changeSet.getChangedElements()) {
          final TreeChange treeChange = changeSet.getChangesByElement(node);
          for (final ASTNode affectedChild : treeChange.getAffectedChildren()) {
            final ChangeInfo childChange = treeChange.getChangeByChild(affectedChild);
            switch (childChange.getChangeType()) {
              case ChangeInfo.ADD:
              case ChangeInfo.REPLACE:
                postponeFormatting(viewProvider, affectedChild);
                break;
              case ChangeInfo.CONTENTS_CHANGED:
                if (!CodeEditUtil.isNodeGenerated(affectedChild)) {
                  ((TreeElement)affectedChild).acceptTree(new RecursiveTreeElementWalkingVisitor() {
                    protected void visitNode(TreeElement element) {
                      if (CodeEditUtil.isNodeGenerated(element)) {
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
    });
  }

  public void doPostponedFormatting() {
    atomic(new Runnable() {
      public void run() {
        if (isDisabled()) return;
        try {
          FileViewProvider[] viewProviders = myUpdatedProviders.toArray(new FileViewProvider[myUpdatedProviders.size()]);
          for (final FileViewProvider viewProvider : viewProviders) {
            doPostponedFormatting(viewProvider);
          }
        }
        finally {
          LOG.assertTrue(myReformatElements.isEmpty());
        }
      }
    });
  }

  public void postponedFormatting(final FileViewProvider viewProvider) {
    postponedFormattingImpl(viewProvider, true);
  }

  public void doPostponedFormatting(final FileViewProvider viewProvider) {
    postponedFormattingImpl(viewProvider, false);
  }

  private void postponedFormattingImpl(final FileViewProvider viewProvider, final boolean check) {
    atomic(new Runnable() {
      public void run() {
        if (isDisabled() || check && !myUpdatedProviders.contains(viewProvider)) return;

        try {
          disablePostprocessFormattingInside(new Runnable() {
            public void run() {
              doPostponedFormattingInner(viewProvider);
            }
          });
        }
        finally {
          myUpdatedProviders.remove(viewProvider);
          myReformatElements.remove(viewProvider);
        }
      }
    });
  }

  public boolean isViewProviderLocked(final FileViewProvider fileViewProvider) {
    return myReformatElements.containsKey(fileViewProvider);
  }

  public static PostprocessReformattingAspect getInstance(Project project) {
    return project.getComponent(PostprocessReformattingAspect.class);
  }

  private void postponeFormatting(final FileViewProvider viewProvider, final ASTNode child) {
    if (!CodeEditUtil.isNodeGenerated(child) && child.getElementType() != TokenType.WHITE_SPACE) {
      final int oldIndent = CodeEditUtil.getOldIndentation(child);
      LOG.assertTrue(oldIndent >= 0,
                     "for not generated items old indentation must be defined: element=" + child + ", text=" + child.getText());
    }
    List<ASTNode> list = myReformatElements.get(viewProvider);
    if (list == null) {
      list = new ArrayList<ASTNode>();
      myReformatElements.put(viewProvider, list);
    }
    list.add(child);
  }

  private void doPostponedFormattingInner(final FileViewProvider key) {


    final List<ASTNode> astNodes = myReformatElements.remove(key);
    final Document document = key.getDocument();
    // Sort ranges by end offsets so that we won't need any offset adjustment after reformat or reindent
    if (document == null /*|| documentManager.isUncommited(document) TODO */) return;

    final VirtualFile virtualFile = key.getVirtualFile();
    if (!virtualFile.isValid()) return;

    final TreeSet<PostprocessFormattingTask> postprocessTasks = new TreeSet<PostprocessFormattingTask>();
    // process all roots in viewProvider to find marked for reformat before elements and create appropriate ragge markers
    handleReformatMarkers(key, postprocessTasks);

    // then we create ranges by changed nodes. One per node. There ranges can instersect. Ranges are sorted by end offset.
    if (astNodes != null) createActionsMap(astNodes, key, postprocessTasks);

    if ("true".equals(System.getProperty("check.psi.is.valid")) && ApplicationManager.getApplication().isUnitTestMode()) {
      checkPsiIsCorrect(key);
    }

    while (!postprocessTasks.isEmpty()) {
      // now we have to normalize actions so that they not intersect and ordered in most appropriate way
      // (free reformating -> reindent -> formating under reindent)

      final List<PostponedAction> normalizedActions = normalizeAndReorderPostponedActions(postprocessTasks, document);

      // only in following loop real changes in document are made
      for (final PostponedAction normalizedAction : normalizedActions) {
        CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myPsiManager.getProject());
        boolean old = settings.ENABLE_JAVADOC_FORMATTING;
        settings.ENABLE_JAVADOC_FORMATTING = false;
        try {
          normalizedAction.execute(key);
        }
        finally {
          settings.ENABLE_JAVADOC_FORMATTING = old;
        }
      }
    }
  }

  private void checkPsiIsCorrect(final FileViewProvider key) {
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
        myReformatElements.clear();
        assert expectedPsi.equals(actualPsiTree) : "Refactored psi should be the same as result of parsing";
      }
    }


  }

  private List<PostponedAction> normalizeAndReorderPostponedActions(final TreeSet<PostprocessFormattingTask> rangesToProcess,
                                                                            Document document) {
    final List<PostprocessFormattingTask> freeFormatingActions = new ArrayList<PostprocessFormattingTask>();
    final List<ReindentTask> indentActions = new ArrayList<ReindentTask>();

    PostprocessFormattingTask accumulatedTask = null;
    Iterator<PostprocessFormattingTask> iterator = rangesToProcess.iterator();
    while (iterator.hasNext()) {
      final PostprocessFormattingTask currentTask = iterator.next();
      if (accumulatedTask == null) {
        accumulatedTask = currentTask;
        iterator.remove();
      }
      else if (accumulatedTask.getStartOffset() > currentTask.getEndOffset() ||
               (accumulatedTask.getStartOffset() == currentTask.getEndOffset() &&
                !canStickActionsTogether(accumulatedTask, currentTask))) {
        // action can be pushed
        if (accumulatedTask instanceof ReindentTask) {
          indentActions.add((ReindentTask) accumulatedTask);
        }
        else {
          freeFormatingActions.add(accumulatedTask);
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
        freeFormatingActions.add(new ReformatWithHeadingWhitespaceTask(rangeToProcess));
        accumulatedTask = currentTask;
        iterator.remove();
      }
      else {
        if (!(accumulatedTask instanceof ReindentTask)) {
          iterator.remove();
          
          boolean withLeadingWhitespace = (accumulatedTask instanceof ReformatWithHeadingWhitespaceTask);
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
          RangeMarker rangeMarker = document.createRangeMarker(Math.min(accumulatedTask.getStartOffset(), currentTask.getStartOffset()),
                                                               Math.max(accumulatedTask.getEndOffset(), currentTask.getEndOffset()));
          if (withLeadingWhitespace) {
            accumulatedTask = new ReformatWithHeadingWhitespaceTask(rangeMarker);
          }
          else {
            accumulatedTask = new ReformatTask(rangeMarker);

          }
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
        freeFormatingActions.add(accumulatedTask);
      }
    }

    final List<PostponedAction> result = new ArrayList<PostponedAction>();
    Collections.reverse(freeFormatingActions);
    Collections.reverse(indentActions);

    if (!freeFormatingActions.isEmpty()) {
      FormatTextRanges ranges = new FormatTextRanges();
      for (PostprocessFormattingTask action : freeFormatingActions) {
        TextRange range = new TextRange(action.getStartOffset(), action.getEndOffset());
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
    // empty reformat markers can't sticked together with any action
    if (nextTask instanceof ReformatWithHeadingWhitespaceTask && nextTask.getStartOffset() == nextTask.getEndOffset()) return false;
    if (currentTask instanceof ReformatWithHeadingWhitespaceTask && currentTask.getStartOffset() == currentTask.getEndOffset()) {
      return false;
    }
    // reindent actions can't be sticked at all
    return !(currentTask instanceof ReindentTask);
  }

  private static void createActionsMap(final List<ASTNode> astNodes,
                                       final FileViewProvider provider,
                                       final TreeSet<PostprocessFormattingTask> rangesToProcess) {
    final Set<ASTNode> nodesToProcess = new HashSet<ASTNode>(astNodes);
    final Document document = provider.getDocument();
    for (final ASTNode node : astNodes) {
      nodesToProcess.remove(node);
      final FileElement fileElement = TreeUtil.getFileElement((TreeElement)node);
      if (fileElement == null || ((PsiFile)fileElement.getPsi()).getViewProvider() != provider) continue;
      final boolean isGenerated = CodeEditUtil.isNodeGenerated(node);

      ((TreeElement)node).acceptTree(new RecursiveTreeElementVisitor() {
        boolean inGeneratedContext = !isGenerated;

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
            final int oldIndent = CodeEditUtil.getOldIndentation(element);
            LOG.assertTrue(oldIndent >= 0, "for not generated items old indentation must be defined");
            rangesToProcess.add(new ReindentTask(document.createRangeMarker(element.getTextRange()), oldIndent));
            inGeneratedContext = false;
          }
          return true;
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

  private static void handleReformatMarkers(final FileViewProvider key, final TreeSet<PostprocessFormattingTask> rangesToProcess) {
    final Document document = key.getDocument();
    for (final FileElement fileElement : ((SingleRootFileViewProvider)key).getKnownTreeRoots()) {
      fileElement.acceptTree(new RecursiveTreeElementWalkingVisitor() {
        protected void visitNode(TreeElement element) {
          if (CodeEditUtil.isMarkedToReformatBefore(element)) {
            CodeEditUtil.markToReformatBefore(element, false);
            rangesToProcess.add(new ReformatWithHeadingWhitespaceTask(document.createRangeMarker(element.getStartOffset(), element.getStartOffset())));
          }
          super.visitNode(element);
        }
      });
    }
  }

  private static void adjustIndentationInRange(final PsiFile file,
                                               final Document document,
                                               final TextRange[] indents,
                                               final int indentAdjustment) {
    final Helper formatHelper = HelperFactory.createHelper(file.getFileType(), file.getProject());
    final CharSequence charsSequence = document.getCharsSequence();
    for (final TextRange indent : indents) {
      final String oldIndentStr = charsSequence.subSequence(indent.getStartOffset() + 1, indent.getEndOffset()).toString();
      final int oldIndent = formatHelper.getIndent(oldIndentStr, true);
      final String newIndentStr = formatHelper.fillIndent(Math.max(oldIndent + indentAdjustment, 0));
      document.replaceString(indent.getStartOffset() + 1, indent.getEndOffset(), newIndentStr);
    }
  }

  private static int getNewIndent(final PsiFile psiFile, final int firstWhitespace) {
    final Helper formatHelper = HelperFactory.createHelper(psiFile.getFileType(), psiFile.getProject());
    final Document document = psiFile.getViewProvider().getDocument();
    final int startOffset = document.getLineStartOffset(document.getLineNumber(firstWhitespace));
    int endOffset = startOffset;
    final CharSequence charsSequence = document.getCharsSequence();
    while (Character.isWhitespace(charsSequence.charAt(endOffset++))) ;
    final String newIndentStr = charsSequence.subSequence(startOffset, endOffset - 1).toString();
    return formatHelper.getIndent(newIndentStr, true);
  }

  public boolean isDisabled() {
    return myDisabledCounter > 0;
  }

  private CodeFormatterFacade getFormatterFacade(final FileViewProvider viewProvider) {
    final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myPsiManager.getProject());
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myPsiManager.getProject());
    final Document document = viewProvider.getDocument();
    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(styleSettings);

    documentManager.commitDocument(document);
    return codeFormatter;
  }

  private abstract static class PostprocessFormattingTask implements Comparable<PostprocessFormattingTask> {
    private final RangeMarker myRange;

    public PostprocessFormattingTask(RangeMarker rangeMarker) {
      myRange = rangeMarker;
    }

    public int compareTo(PostprocessFormattingTask o) {
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

    public RangeMarker getRange() {
      return myRange;
    }

    public int getStartOffset() {
      return myRange.getStartOffset();
    }

    public int getEndOffset() {
      return myRange.getEndOffset();
    }
  }

  private static class ReformatTask extends PostprocessFormattingTask {
    public ReformatTask(RangeMarker rangeMarker) {
      super(rangeMarker);
    }
  }

  private static class ReformatWithHeadingWhitespaceTask extends PostprocessFormattingTask {
    public ReformatWithHeadingWhitespaceTask(RangeMarker rangeMarker) {
      super(rangeMarker);
    }
  }

  private static class ReindentTask extends PostprocessFormattingTask {
    private final int myOldIndent;

    public ReindentTask(RangeMarker rangeMarker, int oldIndent) {
      super(rangeMarker);
      myOldIndent = oldIndent;
    }

    public int getOldIndent() {
      return myOldIndent;
    }
  }

  private interface PostponedAction {
    void execute(FileViewProvider viewProvider);
  }

  private class ReformatRangesAction implements PostponedAction {
    private final FormatTextRanges myRanges;

    public ReformatRangesAction(FormatTextRanges ranges) {
      myRanges = ranges;
    }

    public void execute(FileViewProvider viewProvider) {
      final CodeFormatterFacade codeFormatter = getFormatterFacade(viewProvider);
      codeFormatter.processText(viewProvider.getPsi(viewProvider.getBaseLanguage()), myRanges.ensureNonEmpty());
    }
  }

  private static class ReindentRangesAction implements PostponedAction {
    private final List<Pair<Integer, RangeMarker>> myRangesToReindent = new ArrayList<Pair<Integer, RangeMarker>>();

    public void add(RangeMarker rangeMarker, int oldIndent) {
      myRangesToReindent.add(new Pair<Integer, RangeMarker>(oldIndent, rangeMarker));
    }

    public void execute(FileViewProvider viewProvider) {
      final Document document = viewProvider.getDocument();
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
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "Postponed reformatting model";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @TestOnly
  public void clear() {
    myReformatElements.clear();
  }
}
