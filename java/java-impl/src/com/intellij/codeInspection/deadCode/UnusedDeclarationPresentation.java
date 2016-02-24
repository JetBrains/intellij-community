/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.*;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

public class UnusedDeclarationPresentation extends DefaultInspectionToolPresentation {
  private final Map<String, Set<RefEntity>> myPackageContents = Collections.synchronizedMap(new HashMap<String, Set<RefEntity>>());

  private Map<String, Set<RefEntity>> myOldPackageContents = null;

  private final Set<RefEntity> myIgnoreElements = new HashSet<RefEntity>();
  private WeakUnreferencedFilter myFilter;
  private DeadHTMLComposer myComposer;
  @NonNls private static final String DELETE = "delete";
  @NonNls private static final String COMMENT = "comment";
  @NonNls private static final String [] HINTS = {COMMENT, DELETE};

  public UnusedDeclarationPresentation(@NotNull InspectionToolWrapper toolWrapper, @NotNull GlobalInspectionContextImpl context) {
    super(toolWrapper, context);
    myQuickFixActions = createQuickFixes(toolWrapper);
    ((EntryPointsManagerBase)getEntryPointsManager()).setAddNonJavaEntries(getTool().ADD_NONJAVA_TO_ENTRIES);
  }

  public RefFilter getFilter() {
    if (myFilter == null) {
      myFilter = new WeakUnreferencedFilter(getTool(), getContext());
    }
    return myFilter;
  }
  private static class WeakUnreferencedFilter extends UnreferencedFilter {
    private WeakUnreferencedFilter(@NotNull UnusedDeclarationInspectionBase tool, @NotNull GlobalInspectionContextImpl context) {
      super(tool, context);
    }

    @Override
    public int getElementProblemCount(@NotNull final RefJavaElement refElement) {
      final int problemCount = super.getElementProblemCount(refElement);
      if (problemCount > - 1) return problemCount;
      if (!((RefElementImpl)refElement).hasSuspiciousCallers() || ((RefJavaElementImpl)refElement).isSuspiciousRecursive()) return 1;
      return 0;
    }
  }

  @NotNull
  private UnusedDeclarationInspectionBase getTool() {
    return (UnusedDeclarationInspectionBase)getToolWrapper().getTool();
  }


  @Override
  @NotNull
  public HTMLComposerImpl getComposer() {
    if (myComposer == null) {
      myComposer = new DeadHTMLComposer(this);
    }
    return myComposer;
  }

  @Override
  public void exportResults(@NotNull final Element parentNode, @NotNull RefEntity refEntity) {
    if (!(refEntity instanceof RefJavaElement)) return;
    final RefFilter filter = getFilter();
    if (!getIgnoredRefElements().contains(refEntity) && filter.accepts((RefJavaElement)refEntity)) {
      refEntity = getRefManager().getRefinedElement(refEntity);
      Element element = refEntity.getRefManager().export(refEntity, parentNode, -1);
      if (element == null) return;
      @NonNls Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));

      final RefElement refElement = (RefElement)refEntity;
      final HighlightSeverity severity = getSeverity(refElement);
      final String attributeKey =
        getTextAttributeKey(refElement.getRefManager().getProject(), severity, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
      problemClassElement.setAttribute("severity", severity.myName);
      problemClassElement.setAttribute("attribute_key", attributeKey);

      problemClassElement.addContent(InspectionsBundle.message("inspection.export.results.dead.code"));
      element.addContent(problemClassElement);

      @NonNls Element hintsElement = new Element("hints");

      for (String hint : HINTS) {
        @NonNls Element hintElement = new Element("hint");
        hintElement.setAttribute("value", hint);
        hintsElement.addContent(hintElement);
      }
      element.addContent(hintsElement);


      Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
      StringBuffer buf = new StringBuffer();
      DeadHTMLComposer.appendProblemSynopsis((RefElement)refEntity, buf);
      descriptionElement.addContent(buf.toString());
      element.addContent(descriptionElement);
    }
  }

  @Override
  public QuickFixAction[] getQuickFixes(@NotNull final RefEntity[] refElements, CommonProblemDescriptor[] allowedDescriptors) {
    return myQuickFixActions;
  }

  final QuickFixAction[] myQuickFixActions;

  @NotNull
  private QuickFixAction[] createQuickFixes(@NotNull InspectionToolWrapper toolWrapper) {
    return new QuickFixAction[]{new PermanentDeleteAction(toolWrapper), new CommentOutBin(toolWrapper), new MoveToEntries(toolWrapper)};
  }
  private static final String DELETE_QUICK_FIX = InspectionsBundle.message("inspection.dead.code.safe.delete.quickfix");

  class PermanentDeleteAction extends QuickFixAction {
    PermanentDeleteAction(@NotNull InspectionToolWrapper toolWrapper) {
      super(DELETE_QUICK_FIX, AllIcons.Actions.Cancel, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), toolWrapper);
    }

    @Override
    protected boolean applyFix(@NotNull final RefEntity[] refElements) {
      if (!super.applyFix(refElements)) return false;
      final ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();
      for (RefEntity refElement : refElements) {
        PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getElement() : null;
        if (psiElement == null) continue;
        if (getFilter().getElementProblemCount((RefJavaElement)refElement) == 0) continue;
        psiElements.add(psiElement);
      }

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          final Project project = getContext().getProject();
          if (isDisposed() || project.isDisposed()) return;
          SafeDeleteHandler.invoke(project, PsiUtilCore.toPsiElementArray(psiElements), false, new Runnable() {
            @Override
            public void run() {
              removeElements(refElements, project, myToolWrapper);
            }
          });
        }
      });

      return false; //refresh after safe delete dialog is closed
    }
  }

  private EntryPointsManager getEntryPointsManager() {
    return getContext().getExtension(GlobalJavaInspectionContext.CONTEXT).getEntryPointsManager(getContext().getRefManager());
  }

  class MoveToEntries extends QuickFixAction {
    MoveToEntries(@NotNull InspectionToolWrapper toolWrapper) {
      super(InspectionsBundle.message("inspection.dead.code.entry.point.quickfix"), null, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), toolWrapper);
    }

    @Override
    protected boolean applyFix(@NotNull RefEntity[] refElements) {
      final EntryPointsManager entryPointsManager = getEntryPointsManager();
      for (RefEntity refElement : refElements) {
        if (refElement instanceof RefElement) {
          entryPointsManager.addEntryPoint((RefElement)refElement, true);
        }
      }

      return true;
    }
  }

  class CommentOutBin extends QuickFixAction {
    CommentOutBin(@NotNull InspectionToolWrapper toolWrapper) {
      super(COMMENT_OUT_QUICK_FIX, null, KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK),
            toolWrapper);
    }

    @Override
    protected boolean applyFix(@NotNull RefEntity[] refElements) {
      if (!super.applyFix(refElements)) return false;
      List<RefElement> deletedRefs = new ArrayList<RefElement>(1);
      for (RefEntity refElement : refElements) {
        PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getElement() : null;
        if (psiElement == null) continue;
        if (getFilter().getElementProblemCount((RefJavaElement)refElement) == 0) continue;
        commentOutDead(psiElement);
        refElement.getRefManager().removeRefElement((RefElement)refElement, deletedRefs);
      }

      EntryPointsManager entryPointsManager = getEntryPointsManager();
      for (RefElement refElement : deletedRefs) {
        entryPointsManager.removeEntryPoint(refElement);
      }

      return true;
    }
  }

  private static final String COMMENT_OUT_QUICK_FIX = InspectionsBundle.message("inspection.dead.code.comment.quickfix");
  private static class CommentOutFix implements IntentionAction {
    private final PsiElement myElement;

    private CommentOutFix(final PsiElement element) {
      myElement = element;
    }

    @Override
    @NotNull
    public String getText() {
      return COMMENT_OUT_QUICK_FIX;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (myElement != null && myElement.isValid()) {
        commentOutDead(PsiTreeUtil.getParentOfType(myElement, PsiModifierListOwner.class));
      }
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
  private static void commentOutDead(PsiElement psiElement) {
    PsiFile psiFile = psiElement.getContainingFile();

    if (psiFile != null) {
      Document doc = PsiDocumentManager.getInstance(psiElement.getProject()).getDocument(psiFile);
      if (doc != null) {
        TextRange textRange = psiElement.getTextRange();
        String date = DateFormatUtil.formatDateTime(new Date());

        int startOffset = textRange.getStartOffset();
        CharSequence chars = doc.getCharsSequence();
        while (CharArrayUtil.regionMatches(chars, startOffset, InspectionsBundle.message("inspection.dead.code.comment"))) {
          int line = doc.getLineNumber(startOffset) + 1;
          if (line < doc.getLineCount()) {
            startOffset = doc.getLineStartOffset(line);
            startOffset = CharArrayUtil.shiftForward(chars, startOffset, " \t");
          }
        }

        int endOffset = textRange.getEndOffset();

        int line1 = doc.getLineNumber(startOffset);
        int line2 = doc.getLineNumber(endOffset - 1);

        if (line1 == line2) {
          doc.insertString(startOffset, InspectionsBundle.message("inspection.dead.code.date.comment", date));
        }
        else {
          for (int i = line1; i <= line2; i++) {
            doc.insertString(doc.getLineStartOffset(i), "//");
          }

          doc.insertString(doc.getLineStartOffset(Math.min(line2 + 1, doc.getLineCount() - 1)),
                           InspectionsBundle.message("inspection.dead.code.stop.comment", date));
          doc.insertString(doc.getLineStartOffset(line1), InspectionsBundle.message("inspection.dead.code.start.comment", date));
        }
      }
    }
  }

  @NotNull
  @Override
  public InspectionNode createToolNode(@NotNull GlobalInspectionContextImpl context,
                                       @NotNull InspectionNode node,
                                       @NotNull InspectionRVContentProvider provider,
                                       @NotNull InspectionTreeNode parentNode,
                                       boolean showStructure) {
    final EntryPointsNode entryPointsNode = new EntryPointsNode(context);
    InspectionToolWrapper dummyToolWrapper = entryPointsNode.getToolWrapper();
    InspectionToolPresentation presentation = context.getPresentation(dummyToolWrapper);
    presentation.updateContent();
    provider.appendToolNodeContent(context, entryPointsNode, node, showStructure);
    return entryPointsNode;
  }

  @Override
  public void updateContent() {
    getTool().checkForReachables(getContext());
    myPackageContents.clear();
    getContext().getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (!(refEntity instanceof RefJavaElement)) return;//dead code doesn't work with refModule | refPackage
        RefJavaElement refElement = (RefJavaElement)refEntity;
        if (!(getContext().getUIOptions().FILTER_RESOLVED_ITEMS && getIgnoredRefElements().contains(refElement)) && refElement.isValid() && getFilter().accepts(refElement)) {
          String packageName = RefJavaUtil.getInstance().getPackageName(refEntity);
          Set<RefEntity> content = myPackageContents.get(packageName);
          if (content == null) {
            content = new HashSet<RefEntity>();
            myPackageContents.put(packageName, content);
          }
          content.add(refEntity);
        }
      }
    });
  }

  @Override
  public boolean hasReportedProblems() {
    final GlobalInspectionContextImpl context = getContext();
    if (!isDisposed() && context.getUIOptions().SHOW_ONLY_DIFF){
      return containsOnlyDiff(myPackageContents) ||
             myOldPackageContents != null && containsOnlyDiff(myOldPackageContents);
    }
    if (!myPackageContents.isEmpty()) return true;
    return isOldProblemsIncluded() && !myOldPackageContents.isEmpty();
  }

  private boolean containsOnlyDiff(@NotNull Map<String, Set<RefEntity>> packageContents) {
    for (String packageName : packageContents.keySet()) {
      final Set<RefEntity> refElements = packageContents.get(packageName);
      if (refElements != null){
        for (RefEntity refElement : refElements) {
          if (getElementStatus(refElement) != FileStatus.NOT_CHANGED){
            return true;
          }
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Map<String, Set<RefEntity>> getContent() {
    return myPackageContents;
  }

  @Override
  public Map<String, Set<RefEntity>> getOldContent() {
    return myOldPackageContents;
  }

  @Override
  public void ignoreCurrentElement(RefEntity refEntity) {
    if (refEntity == null) return;
    myIgnoreElements.add(refEntity);
  }

  @Override
  public void amnesty(RefEntity refEntity) {
    myIgnoreElements.remove(refEntity);
  }

  @Override
  public void cleanup() {
    super.cleanup();
    myOldPackageContents = null;
    myPackageContents.clear();
    myIgnoreElements.clear();
  }


  @Override
  public void finalCleanup() {
    super.finalCleanup();
    myOldPackageContents = null;
  }

  @Override
  public boolean isGraphNeeded() {
    return true;
  }

  @Override
  public boolean isElementIgnored(final RefEntity element) {
    for (RefEntity entity : myIgnoreElements) {
      if (Comparing.equal(entity, element)) {
        return true;
      }
    }
    return false;
  }


  @NotNull
  @Override
  public FileStatus getElementStatus(final RefEntity element) {
    final GlobalInspectionContextImpl context = getContext();
    if (!isDisposed() && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldPackageContents != null){
        final boolean old = RefUtil.contains(element, collectRefElements(myOldPackageContents));
        final boolean current = RefUtil.contains(element, collectRefElements(myPackageContents));
        return calcStatus(old, current);
      }
      return FileStatus.ADDED;
    }
    return FileStatus.NOT_CHANGED;
  }

  @Override
  @NotNull
  public Collection<RefEntity> getIgnoredRefElements() {
    return myIgnoreElements;
  }

  private static Set<RefEntity> collectRefElements(Map<String, Set<RefEntity>> packageContents) {
    Set<RefEntity> allAvailable = new HashSet<RefEntity>();
    for (Set<RefEntity> elements : packageContents.values()) {
      allAvailable.addAll(elements);
    }
    return allAvailable;
  }

  @Override
  @Nullable
  public IntentionAction findQuickFixes(@NotNull final CommonProblemDescriptor descriptor, final String hint) {
    if (descriptor instanceof ProblemDescriptor) {
      if (DELETE.equals(hint)) {
        return new PermanentDeleteFix(((ProblemDescriptor)descriptor).getPsiElement());
      }
      if (COMMENT.equals(hint)) {
        return new CommentOutFix(((ProblemDescriptor)descriptor).getPsiElement());
      }
    }
    return null;
  }


  private static class PermanentDeleteFix implements IntentionAction {
    private final PsiElement myElement;

    private PermanentDeleteFix(final PsiElement element) {
      myElement = element;
    }

    @Override
    @NotNull
    public String getText() {
      return DELETE_QUICK_FIX;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (myElement != null && myElement.isValid()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            SafeDeleteHandler
              .invoke(myElement.getProject(), new PsiElement[]{PsiTreeUtil.getParentOfType(myElement, PsiModifierListOwner.class)}, false);
          }
        });
      }
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
}
