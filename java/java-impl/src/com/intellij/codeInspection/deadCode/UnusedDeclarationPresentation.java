/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.*;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UnusedDeclarationPresentation extends DefaultInspectionToolPresentation {
  private final Map<RefEntity, UnusedDeclarationHint> myFixedElements =
    ConcurrentCollectionFactory.createMap(ContainerUtil.identityStrategy());
  private final Set<RefEntity> myExcludedElements = ConcurrentCollectionFactory.createConcurrentSet(ContainerUtil.identityStrategy());

  private WeakUnreferencedFilter myFilter;
  private DeadHTMLComposer myComposer;
  @NonNls private static final String DELETE = "delete";
  @NonNls private static final String COMMENT = "comment";

  private enum UnusedDeclarationHint {
    COMMENT("Commented out"),
    DELETE("Deleted");

    private final String myDescription;

    UnusedDeclarationHint(String description) {
      myDescription = description;
    }

    public String getDescription() {
      return myDescription;
    }
  }

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

      for (RefElement element : refElement.getInReferences()) {
        if (refElement instanceof RefFile) return 1;
        if (((UnusedDeclarationInspectionBase)myTool).isEntryPoint(element)) return 1;
      }

      return 0;
    }
  }

  @NotNull
  private UnusedDeclarationInspectionBase getTool() {
    return (UnusedDeclarationInspectionBase)getToolWrapper().getTool();
  }


  @Override
  @NotNull
  public DeadHTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new DeadHTMLComposer(this);
    }
    return myComposer;
  }

  @Override
  public boolean isExcluded(@NotNull RefEntity entity) {
    return myExcludedElements.contains(entity);
  }


  @Override
  public void amnesty(@NotNull RefEntity element) {
    myExcludedElements.remove(element);
  }

  @Override
  public void exclude(@NotNull RefEntity element) {
    myExcludedElements.add(element);
  }

  @Override
  public void exportResults(@NotNull final Element parentNode,
                            @NotNull RefEntity refEntity,
                            @NotNull Predicate<CommonProblemDescriptor> excludedDescriptions) {
    if (!(refEntity instanceof RefJavaElement)) return;
    final RefFilter filter = getFilter();
    if (!myFixedElements.containsKey(refEntity) && filter.accepts((RefJavaElement)refEntity)) {
      refEntity = getRefManager().getRefinedElement(refEntity);
      if (!refEntity.isValid()) return;
      RefJavaElement refElement = (RefJavaElement)refEntity;
      if (!compareVisibilities(refElement, getTool().getSharedLocalInspectionTool())) return;
      if (skipEntryPoints(refElement)) return;

      Element element = refEntity.getRefManager().export(refEntity, parentNode, -1);
      if (element == null) return;
      @NonNls Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));

      final HighlightSeverity severity = getSeverity(refElement);
      final String attributeKey =
        getTextAttributeKey(refElement.getRefManager().getProject(), severity, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
      problemClassElement.setAttribute("severity", severity.myName);
      problemClassElement.setAttribute("attribute_key", attributeKey);

      problemClassElement.addContent(InspectionsBundle.message("inspection.export.results.dead.code"));
      element.addContent(problemClassElement);

      @NonNls Element hintsElement = new Element("hints");

      for (UnusedDeclarationHint hint : UnusedDeclarationHint.values()) {
        @NonNls Element hintElement = new Element("hint");
        hintElement.setAttribute("value", hint.toString().toLowerCase());
        hintsElement.addContent(hintElement);
      }
      element.addContent(hintsElement);


      Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
      StringBuffer buf = new StringBuffer();
      DeadHTMLComposer.appendProblemSynopsis((RefElement)refEntity, buf);
      descriptionElement.addContent(buf.toString());
      element.addContent(descriptionElement);
    }
    super.exportResults(parentNode, refEntity, excludedDescriptions);
  }

  @NotNull
  @Override
  public QuickFixAction[] getQuickFixes(@NotNull RefEntity... refElements) {
    return Arrays.stream(refElements).anyMatch(element -> element instanceof RefJavaElement && getFilter().accepts((RefJavaElement)element) && !myFixedElements.containsKey(element) && element.isValid())
           ? myQuickFixActions
           : QuickFixAction.EMPTY;
  }

  final QuickFixAction[] myQuickFixActions;

  @NotNull
  private QuickFixAction[] createQuickFixes(@NotNull InspectionToolWrapper toolWrapper) {
    return new QuickFixAction[]{new PermanentDeleteAction(toolWrapper), new CommentOutBin(toolWrapper), new MoveToEntries(toolWrapper)};
  }
  private static final String DELETE_QUICK_FIX = InspectionsBundle.message("inspection.dead.code.safe.delete.quickfix");

  class PermanentDeleteAction extends QuickFixAction {
    PermanentDeleteAction(@NotNull InspectionToolWrapper toolWrapper) {
      super(DELETE_QUICK_FIX, AllIcons.Actions.Cancel, null, toolWrapper);
      copyShortcutFrom(ActionManager.getInstance().getAction("SafeDelete"));
    }

    @Override
    protected boolean applyFix(@NotNull final RefEntity[] refElements) {
      if (!super.applyFix(refElements)) return false;

      //filter only elements applicable to be deleted (exclude entry points)
      RefElement[] filteredRefElements = Arrays.stream(refElements)
        .filter(entry -> entry instanceof RefJavaElement && getFilter().accepts((RefJavaElement)entry))
        .toArray(RefElement[]::new);

      ApplicationManager.getApplication().invokeLater(() -> {
        final Project project = getContext().getProject();
        if (isDisposed() || project.isDisposed()) return;
        Set<RefEntity> classes = Arrays.stream(filteredRefElements)
          .filter(refElement -> refElement instanceof RefClass)
          .collect(Collectors.toSet());

        //filter out elements inside classes to be deleted
        PsiElement[] elements = Arrays.stream(filteredRefElements).filter(e -> {
          RefEntity owner = e.getOwner();
          if (owner != null && classes.contains(owner)) {
            return false;
          }
          return true;
        }).map(e -> e.getElement())
          .filter(e -> e != null)
          .toArray(PsiElement[]::new);
        SafeDeleteHandler.invoke(project, elements, false,
                                 () -> {
                                   removeElements(filteredRefElements, project, myToolWrapper);
                                   for (RefEntity ref : filteredRefElements) {
                                     myFixedElements.put(ref, UnusedDeclarationHint.DELETE);
                                   }
                                 });
      });

      return false; //refresh after safe delete dialog is closed
    }
  }

  private EntryPointsManager getEntryPointsManager() {
    return getContext().getExtension(GlobalJavaInspectionContext.CONTEXT).getEntryPointsManager(getContext().getRefManager());
  }

  class MoveToEntries extends QuickFixAction {
    MoveToEntries(@NotNull InspectionToolWrapper toolWrapper) {
      super(InspectionsBundle.message("inspection.dead.code.entry.point.quickfix"), null, null, toolWrapper);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabledAndVisible()) {
        final RefEntity[] elements = getInvoker(e).getTree().getSelectedElements();
        for (RefEntity element : elements) {
          if (!((RefElement) element).isEntry()) {
            return;
          }
        }
        e.getPresentation().setEnabled(false);
      }
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
      List<RefElement> deletedRefs = new ArrayList<>(1);
      final RefFilter filter = getFilter();
      for (RefEntity refElement : refElements) {
        PsiElement psiElement = refElement instanceof RefElement ? ((RefElement)refElement).getElement() : null;
        if (psiElement == null) continue;
        if (filter.getElementProblemCount((RefJavaElement)refElement) == 0) continue;

        final RefEntity owner = refElement.getOwner();
        if (!(owner instanceof RefJavaElement) || filter.getElementProblemCount((RefJavaElement)owner) == 0 || !(ArrayUtil.find(refElements, owner) > -1)) {
          commentOutDead(psiElement);
        }

        refElement.getRefManager().removeRefElement((RefElement)refElement, deletedRefs);
      }

      EntryPointsManager entryPointsManager = getEntryPointsManager();
      for (RefElement refElement : deletedRefs) {
        entryPointsManager.removeEntryPoint(refElement);
      }

      for (RefElement ref : deletedRefs) {
        myFixedElements.put(ref, UnusedDeclarationHint.COMMENT);
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

  @Override
  public void createToolNode(@NotNull GlobalInspectionContextImpl context,
                             @NotNull InspectionNode node,
                             @NotNull InspectionRVContentProvider provider,
                             @NotNull InspectionTreeNode parentNode,
                             boolean showStructure,
                             boolean groupByStructure) {
    final EntryPointsNode entryPointsNode = new EntryPointsNode(context);
    InspectionToolWrapper dummyToolWrapper = entryPointsNode.getToolWrapper();
    InspectionToolPresentation presentation = context.getPresentation(dummyToolWrapper);
    presentation.updateContent();
    provider.appendToolNodeContent(context, entryPointsNode, node, showStructure, groupByStructure);
    myToolNode = entryPointsNode;
  }

  @NotNull
  @Override
  public RefElementNode createRefNode(@Nullable RefEntity entity) {
    return new RefElementNode(entity, this) {
      @Nullable
      @Override
      public String getTailText() {
        final UnusedDeclarationHint hint = myFixedElements.get(getElement());
        if (hint != null) {
          return hint.getDescription();
        }
        return super.getTailText();
      }

      @Override
      public boolean isQuickFixAppliedFromView() {
        return myFixedElements.containsKey(getElement());
      }
    };
  }

  public boolean isProblemResolved(@Nullable RefEntity entity) {
    return myFixedElements.containsKey(entity);
  }

  @Override
  public synchronized void updateContent() {
    getTool().checkForReachableRefs(getContext());
    myContents.clear();
    final UnusedSymbolLocalInspectionBase localInspectionTool = getTool().getSharedLocalInspectionTool();
    getContext().getRefManager().iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (!(refEntity instanceof RefJavaElement)) return;//dead code doesn't work with refModule | refPackage
        RefJavaElement refElement = (RefJavaElement)refEntity;
        if (!compareVisibilities(refElement, localInspectionTool)) return;
        if (!(getContext().getUIOptions().FILTER_RESOLVED_ITEMS &&
              (myFixedElements.containsKey(refElement) ||
              isExcluded(refEntity) ||
              isSuppressed(refElement))) && refElement.isValid() && getFilter().accepts(refElement)) {
          if (skipEntryPoints(refElement)) return;
          registerContentEntry(refEntity, RefJavaUtil.getInstance().getPackageName(refEntity));
        }
      }
    });
    updateProblemElements();
  }

  protected boolean skipEntryPoints(RefJavaElement refElement) {
    return getTool().isEntryPoint(refElement);
  }

  @PsiModifier.ModifierConstant
  private static String getAcceptedVisibility(UnusedSymbolLocalInspectionBase tool, RefJavaElement element) {
    if (element instanceof RefImplicitConstructor) {
      element = ((RefImplicitConstructor)element).getOwnerClass();
    }
    if (element instanceof RefClass) {
      return element.getOwner() instanceof RefClass ? tool.getInnerClassVisibility() : tool.getClassVisibility();
    }
    if (element instanceof RefField) {
      return tool.getFieldVisibility();
    }
    if (element instanceof RefMethod) {
      final String methodVisibility = tool.getMethodVisibility();
      if (methodVisibility != null &&
          //todo store in the graph
          tool.isIgnoreAccessors()) {
        final PsiModifierListOwner listOwner = ((RefMethod)element).getElement();
        if (listOwner instanceof PsiMethod && PropertyUtilBase.isSimplePropertyAccessor((PsiMethod)listOwner)) {
          return null;
        }
      }
      return methodVisibility;
    }
    if (element instanceof RefParameter) {
      return tool.getParameterVisibility();
    }
    return PsiModifier.PUBLIC;
  }

  protected static boolean compareVisibilities(RefJavaElement listOwner,
                                               UnusedSymbolLocalInspectionBase localInspectionTool) {
    return compareVisibilities(listOwner, getAcceptedVisibility(localInspectionTool, listOwner));
  }

  protected static boolean compareVisibilities(RefJavaElement listOwner, final String acceptedVisibility) {
    if (acceptedVisibility != null) {
      while (listOwner != null) {
        if (VisibilityUtil.compare(listOwner.getAccessModifier(), acceptedVisibility) >= 0) {
          return true;
        }
        final RefEntity parent = listOwner.getOwner();
        if (parent instanceof RefJavaElement) {
          listOwner = (RefJavaElement)parent;
        }
        else {
          break;
        }
      }
    }
    return false;
  }

  @Override
  public void ignoreElement(@NotNull RefEntity refEntity) {
    if (refEntity instanceof RefElement) {
      final CommonProblemDescriptor[] descriptors = getProblemElements().get(refEntity);
      if (descriptors != null) {
        final PsiElement psiElement = ReadAction.compute(() -> ((RefElement)refEntity).getElement());
        List<CommonProblemDescriptor> foreignDescriptors = new ArrayList<>();
        for (CommonProblemDescriptor descriptor : descriptors) {
          if (descriptor instanceof ProblemDescriptor) {
            PsiElement problemElement = ReadAction.compute(() -> {
              PsiElement element = ((ProblemDescriptor)descriptor).getPsiElement();
              if (element instanceof PsiIdentifier) element = element.getParent();
              return element;
            });
            if (problemElement == psiElement ||
                problemElement instanceof PsiParameter && ((PsiParameter)problemElement).getDeclarationScope() == psiElement) continue;
          }
          foreignDescriptors.add(descriptor);
        }
        if (foreignDescriptors.size() == descriptors.length) return;
      }
    }
    super.ignoreElement(refEntity);
  }

  @Override
  public void cleanup() {
    super.cleanup();
    myFixedElements.clear();
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
      return super.findQuickFixes(descriptor, hint);
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
        SafeDeleteHandler.invoke(myElement.getProject(), new PsiElement[]{PsiTreeUtil.getParentOfType(myElement, PsiModifierListOwner.class)}, false);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  @NotNull
  @Override
  public JComponent getCustomPreviewPanel(@NotNull RefEntity entity) {
    final Project project = entity.getRefManager().getProject();
    JEditorPane htmlView = new JEditorPane() {
      @Override
      public String getToolTipText(MouseEvent evt) {
        int pos = viewToModel(evt.getPoint());
        if (pos >= 0) {
          HTMLDocument hdoc = (HTMLDocument) getDocument();
          javax.swing.text.Element e = hdoc.getCharacterElement(pos);
          AttributeSet a = e.getAttributes();

          SimpleAttributeSet value = (SimpleAttributeSet) a.getAttribute(HTML.Tag.A);
          if (value != null) {
            String objectPackage = (String) value.getAttribute("qualifiedname");
            if (objectPackage != null) {
              return objectPackage;
            }
          }
        }
        return null;
      }
    };
    htmlView.setContentType(UIUtil.HTML_MIME);
    htmlView.setEditable(false);
    htmlView.setOpaque(false);
    htmlView.setBackground(UIUtil.getLabelBackground());
    htmlView.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        URL url = e.getURL();
        if (url == null) {
          return;
        }
        @NonNls String ref = url.getRef();

        int offset = Integer.parseInt(ref);
        String fileURL = url.toExternalForm();
        fileURL = fileURL.substring(0, fileURL.indexOf('#'));
        VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(fileURL);
        if (vFile == null) {
          vFile = VfsUtil.findFileByURL(url);
        }
        if (vFile != null) {
          final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vFile, offset);
          FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
        }
      }
    });
    final StyleSheet css = ((HTMLEditorKit)htmlView.getEditorKit()).getStyleSheet();
    css.addRule("p.problem-description-group {text-indent: " + JBUI.scale(9) + "px;font-weight:bold;}");
    css.addRule("div.problem-description {margin-left: " + JBUI.scale(9) + "px;}");
    css.addRule("ul {margin-left:" + JBUI.scale(10) + "px;text-indent: 0}");
    css.addRule("code {font-family:" + UIUtil.getLabelFont().getFamily()  +  "}");
    final StringBuffer buf = new StringBuffer();
    getComposer().compose(buf, entity, false);
    final String text = buf.toString();
    SingleInspectionProfilePanel.readHTML(htmlView, SingleInspectionProfilePanel.toHTML(htmlView, text, false));
    return ScrollPaneFactory.createScrollPane(htmlView, true);
  }

  @Override
  public int getProblemsCount(@NotNull InspectionTree tree) {
    return 0;
  }
}
