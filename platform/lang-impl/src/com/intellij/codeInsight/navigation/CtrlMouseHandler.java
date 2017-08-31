/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;

public class CtrlMouseHandler extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.navigation.CtrlMouseHandler");
  private static final AbstractDocumentationTooltipAction[] ourTooltipActions = {new ShowQuickDocAtPinnedWindowFromTooltipAction()};
  private final EditorColorsManager myEditorColorsManager;

  private HighlightersSet myHighlighter;
  @JdkConstants.InputEventMask private int myStoredModifiers = 0;
  private TooltipProvider myTooltipProvider = null;
  private final FileEditorManager myFileEditorManager;
  private final DocumentationManager myDocumentationManager;
  @Nullable private Point myPrevMouseLocation;
  private LightweightHint myHint;

  public enum BrowseMode {None, Declaration, TypeDeclaration, Implementation}

  private final KeyListener myEditorKeyListener = new KeyAdapter() {
    @Override
    public void keyPressed(final KeyEvent e) {
      handleKey(e);
    }

    @Override
    public void keyReleased(final KeyEvent e) {
      handleKey(e);
    }

    private void handleKey(final KeyEvent e) {
      int modifiers = e.getModifiers();
      if (modifiers == myStoredModifiers) {
        return;
      }

      BrowseMode browseMode = getBrowseMode(modifiers);

      if (browseMode == BrowseMode.None) {
        disposeHighlighter();
        cancelPreviousTooltip();
      }
      else {
        TooltipProvider tooltipProvider = myTooltipProvider;
        if (tooltipProvider != null) {
          if (browseMode != tooltipProvider.getBrowseMode()) {
            disposeHighlighter();
          }
          myStoredModifiers = modifiers;
          cancelPreviousTooltip();
          myTooltipProvider = new TooltipProvider(tooltipProvider.myEditor, tooltipProvider.myPosition);
          myTooltipProvider.execute(browseMode);
        }
      }
    }
  };

  private final FileEditorManagerListener myFileEditorManagerListener = new FileEditorManagerListener() {
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent e) {
      disposeHighlighter();
      cancelPreviousTooltip();
    }
  };

  private final VisibleAreaListener myVisibleAreaListener = new VisibleAreaListener() {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      disposeHighlighter();
      cancelPreviousTooltip();
    }
  };

  private final EditorMouseAdapter myEditorMouseAdapter = new EditorMouseAdapter() {
    @Override
    public void mouseReleased(EditorMouseEvent e) {
      disposeHighlighter();
      cancelPreviousTooltip();
    }
  };

  private final EditorMouseMotionListener myEditorMouseMotionListener = new EditorMouseMotionAdapter() {
    @Override
    public void mouseMoved(final EditorMouseEvent e) {
      if (e.isConsumed() || !myProject.isInitialized() || myProject.isDisposed()) {
        return;
      }
      MouseEvent mouseEvent = e.getMouseEvent();

      Point prevLocation = myPrevMouseLocation;
      myPrevMouseLocation = mouseEvent.getLocationOnScreen();
      if (isMouseOverTooltip(mouseEvent.getLocationOnScreen())
          || ScreenUtil.isMovementTowards(prevLocation, mouseEvent.getLocationOnScreen(), getHintBounds())) {
        return;
      }
      cancelPreviousTooltip();

      myStoredModifiers = mouseEvent.getModifiers();
      BrowseMode browseMode = getBrowseMode(myStoredModifiers);

      if (browseMode == BrowseMode.None || e.getArea() != EditorMouseEventArea.EDITING_AREA) {
        disposeHighlighter();
        return;
      }

      Editor editor = e.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      PsiFile psiFile = documentManager.getPsiFile(editor.getDocument());
      Point point = new Point(mouseEvent.getPoint());
      if (documentManager.isCommitted(editor.getDocument())) {
        // when document is committed, try to check injected stuff - it's fast
        int offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(point));
        editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, psiFile, offset);
      }

      LogicalPosition pos = editor.xyToLogicalPosition(point);
      int offset = editor.logicalPositionToOffset(pos);
      int selStart = editor.getSelectionModel().getSelectionStart();
      int selEnd = editor.getSelectionModel().getSelectionEnd();

      if (offset >= selStart && offset < selEnd) {
        disposeHighlighter();
        return;
      }

      myTooltipProvider = new TooltipProvider(editor, pos);
      myTooltipProvider.execute(browseMode);
    }
  };

  private void cancelPreviousTooltip() {
    if (myTooltipProvider != null) {
      myTooltipProvider.dispose();
      myTooltipProvider = null;
    }
  }

  @NotNull private final Alarm myDocAlarm;

  public CtrlMouseHandler(final Project project,
                          StartupManager startupManager,
                          EditorColorsManager colorsManager,
                          FileEditorManager fileEditorManager,
                          @NotNull DocumentationManager documentationManager,
                          @NotNull final EditorFactory editorFactory) {
    super(project);
    myEditorColorsManager = colorsManager;
    startupManager.registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        EditorEventMulticaster eventMulticaster = editorFactory.getEventMulticaster();
        eventMulticaster.addEditorMouseListener(myEditorMouseAdapter, project);
        eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener, project);
        eventMulticaster.addCaretListener(new CaretListener() {
          @Override
          public void caretPositionChanged(CaretEvent e) {
            if (myHint != null) {
              myDocumentationManager.updateToolwindowContext();
            }
          }
        }, project);
      }
    });
    myFileEditorManager = fileEditorManager;
    myDocumentationManager = documentationManager;
    myDocAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myProject);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "CtrlMouseHandler";
  }

  private boolean isMouseOverTooltip(@NotNull Point mouseLocationOnScreen) {
    Rectangle bounds = getHintBounds();
    return bounds != null && bounds.contains(mouseLocationOnScreen);
  }

  @Nullable
  private Rectangle getHintBounds() {
    LightweightHint hint = myHint;
    if (hint == null) {
      return null;
    }
    JComponent hintComponent = hint.getComponent();
    if (!hintComponent.isShowing()) {
      return null;
    }
    return new Rectangle(hintComponent.getLocationOnScreen(), hintComponent.getSize());
  }

  @NotNull
  private static BrowseMode getBrowseMode(@JdkConstants.InputEventMask int modifiers) {
    if (modifiers != 0) {
      final Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
      if (KeymapUtil.matchActionMouseShortcutsModifiers(activeKeymap, modifiers, IdeActions.ACTION_GOTO_DECLARATION)) return BrowseMode.Declaration;
      if (KeymapUtil.matchActionMouseShortcutsModifiers(activeKeymap, modifiers, IdeActions.ACTION_GOTO_TYPE_DECLARATION)) return BrowseMode.TypeDeclaration;
      if (KeymapUtil.matchActionMouseShortcutsModifiers(activeKeymap, modifiers, IdeActions.ACTION_GOTO_IMPLEMENTATION)) return BrowseMode.Implementation;
    }
    return BrowseMode.None;
  }

  @Nullable
  @TestOnly
  public static String getInfo(PsiElement element, PsiElement atPointer) {
    return generateInfo(element, atPointer, true).text;
  }

  @Nullable
  @TestOnly
  public static String getInfo(@NotNull Editor editor, BrowseMode browseMode) {
    Project project = editor.getProject();
    if (project == null) return null; 
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;
    Info info = getInfoAt(project, editor, file, editor.getCaretModel().getOffset(), browseMode);
    return info == null ? null : info.getInfo().text;
  }

  @NotNull
  private static DocInfo generateInfo(PsiElement element, PsiElement atPointer, boolean fallbackToBasicInfo) {
    final DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(element, atPointer);
    String result = documentationProvider.getQuickNavigateInfo(element, atPointer);
    if (result == null && fallbackToBasicInfo) {
      result = doGenerateInfo(element);
    }
    return result == null ? DocInfo.EMPTY : new DocInfo(result, documentationProvider, element);
  }

  @Nullable
  private static String doGenerateInfo(@NotNull PsiElement element) {
    if (element instanceof PsiFile) {
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null) {
        return virtualFile.getPresentableUrl();
      }
    }

    String info = getQuickNavigateInfo(element);
    if (info != null) {
      return info;
    }

    if (element instanceof NavigationItem) {
      final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
      if (presentation != null) {
        return presentation.getPresentableText();
      }
    }

    return null;
  }

  @Nullable
  private static String getQuickNavigateInfo(PsiElement element) {
    final String name = ElementDescriptionUtil.getElementDescription(element, UsageViewShortNameLocation.INSTANCE);
    if (StringUtil.isEmpty(name)) return null;
    final String typeName = ElementDescriptionUtil.getElementDescription(element, UsageViewTypeLocation.INSTANCE);
    final PsiFile file = element.getContainingFile();
    final StringBuilder sb = new StringBuilder();
    if (StringUtil.isNotEmpty(typeName)) sb.append(typeName).append(" ");
    sb.append("\"").append(name).append("\"");
    if (file != null && file.isPhysical()) {
      sb.append(" [").append(file.getName()).append("]");
    }
    return sb.toString();
  }

  private abstract static class Info {
    @NotNull protected final PsiElement myElementAtPointer;
    @NotNull private final List<TextRange> myRanges;

    public Info(@NotNull PsiElement elementAtPointer, @NotNull List<TextRange> ranges) {
      myElementAtPointer = elementAtPointer;
      myRanges = ranges;
    }

    public Info(@NotNull PsiElement elementAtPointer) {
      this(elementAtPointer, getReferenceRanges(elementAtPointer));
    }

    @NotNull
    private static List<TextRange> getReferenceRanges(@NotNull PsiElement elementAtPointer) {
      if (!elementAtPointer.isPhysical()) return Collections.emptyList();
      int textOffset = elementAtPointer.getTextOffset();
      final TextRange range = elementAtPointer.getTextRange();
      if (range == null) {
        throw new AssertionError("Null range for " + elementAtPointer + " of " + elementAtPointer.getClass());
      }
      if (textOffset < range.getStartOffset() || textOffset < 0) {
        LOG.error("Invalid text offset " + textOffset + " of element " + elementAtPointer + " of " + elementAtPointer.getClass());
        textOffset = range.getStartOffset();
      }
      return Collections.singletonList(new TextRange(textOffset, range.getEndOffset()));
    }

    boolean isSimilarTo(@NotNull Info that) {
      return Comparing.equal(myElementAtPointer, that.myElementAtPointer) && myRanges.equals(that.myRanges);
    }

    @NotNull
    public List<TextRange> getRanges() {
      return myRanges;
    }

    @NotNull
    public abstract DocInfo getInfo();

    public abstract boolean isValid(@NotNull Document document);
    
    public abstract boolean isNavigatable();

    public abstract void showDocInfo(@NotNull DocumentationManager docManager);

    protected boolean rangesAreCorrect(@NotNull Document document) {
      final TextRange docRange = new TextRange(0, document.getTextLength());
      for (TextRange range : getRanges()) {
        if (!docRange.contains(range)) return false;
      }

      return true;
    }
  }

  private static void showDumbModeNotification(@NotNull Project project) {
    DumbService.getInstance(project).showDumbModeNotification("Element information is not available during index update");
  }

  private static class InfoSingle extends Info {
    @NotNull private final PsiElement myTargetElement;

    public InfoSingle(@NotNull PsiElement elementAtPointer, @NotNull PsiElement targetElement) {
      super(elementAtPointer);
      myTargetElement = targetElement;
    }

    public InfoSingle(@NotNull PsiReference ref, @NotNull final PsiElement targetElement) {
      super(ref.getElement(), ReferenceRange.getAbsoluteRanges(ref));
      myTargetElement = targetElement;
    }

    @Override
    @NotNull
    public DocInfo getInfo() {
      return areElementsValid() ? generateInfo(myTargetElement, myElementAtPointer, isNavigatable()) : DocInfo.EMPTY;
    }

    private boolean areElementsValid() {
      return myTargetElement.isValid() && myElementAtPointer.isValid();
    }

    @Override
    public boolean isValid(@NotNull Document document) {
      return areElementsValid() && rangesAreCorrect(document);
    }

    @Override
    public boolean isNavigatable() {
      return myTargetElement != myElementAtPointer && myTargetElement != myElementAtPointer.getParent();
    }

    @Override
    public void showDocInfo(@NotNull DocumentationManager docManager) {
      docManager.showJavaDocInfo(myTargetElement, myElementAtPointer, null);
      docManager.setAllowContentUpdateFromContext(false);
    }
  }

  private static class InfoMultiple extends Info {
    public InfoMultiple(@NotNull final PsiElement elementAtPointer) {
      super(elementAtPointer);
    }

    public InfoMultiple(@NotNull final PsiElement elementAtPointer, @NotNull PsiReference ref) {
      super(elementAtPointer, ReferenceRange.getAbsoluteRanges(ref));
    }

    @Override
    @NotNull
    public DocInfo getInfo() {
      return new DocInfo(CodeInsightBundle.message("multiple.implementations.tooltip"), null, null);
    }

    @Override
    public boolean isValid(@NotNull Document document) {
      return rangesAreCorrect(document);
    }

    @Override
    public boolean isNavigatable() {
      return true;
    }

    @Override
    public void showDocInfo(@NotNull DocumentationManager docManager) {
      // Do nothing
    }
  }

  @Nullable
  private Info getInfoAt(@NotNull final Editor editor, @NotNull PsiFile file, int offset, @NotNull BrowseMode browseMode) {
    return getInfoAt(myProject, editor, file, offset, browseMode);
  }
  
  @Nullable
  private static Info getInfoAt(@NotNull Project project, @NotNull final Editor editor, @NotNull PsiFile file, int offset, 
                                @NotNull BrowseMode browseMode) {
    PsiElement targetElement = null;

    if (browseMode == BrowseMode.TypeDeclaration) {
      try {
        targetElement = GotoTypeDeclarationAction.findSymbolType(editor, offset);
      }
      catch (IndexNotReadyException e) {
        showDumbModeNotification(project);
      }
    }
    else if (browseMode == BrowseMode.Declaration) {
      final PsiReference ref = TargetElementUtil.findReference(editor, offset);
      final List<PsiElement> resolvedElements = ref == null ? Collections.emptyList() : resolve(ref);
      final PsiElement resolvedElement = resolvedElements.size() == 1 ? resolvedElements.get(0) : null;

      final PsiElement[] targetElements = GotoDeclarationAction.findTargetElementsNoVS(project, editor, offset, false);
      final PsiElement elementAtPointer = file.findElementAt(TargetElementUtil.adjustOffset(file, editor.getDocument(), offset));

      if (targetElements != null) {
        if (targetElements.length == 0) {
          return null;
        }
        else if (targetElements.length == 1) {
          if (targetElements[0] != resolvedElement && elementAtPointer != null && targetElements[0].isPhysical()) {
            return ref != null ? new InfoSingle(ref, targetElements[0]) : new InfoSingle(elementAtPointer, targetElements[0]);
          }
        }
        else {
          return elementAtPointer != null ? new InfoMultiple(elementAtPointer) : null;
        }
      }

      if (resolvedElements.size() == 1) {
        return new InfoSingle(ref, resolvedElements.get(0));
      }
      if (resolvedElements.size() > 1) {
        return elementAtPointer != null ? new InfoMultiple(elementAtPointer, ref) : null;
      }
    }
    else if (browseMode == BrowseMode.Implementation) {
      final PsiElement element = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
      PsiElement[] targetElements = new ImplementationSearcher() {
        @Override
        @NotNull
        protected PsiElement[] searchDefinitions(final PsiElement element, Editor editor) {
          final List<PsiElement> found = new ArrayList<>(2);
          DefinitionsScopedSearch.search(element, getSearchScope(element, editor)).forEach(psiElement -> {
            found.add(psiElement);
            return found.size() != 2;
          });
          return PsiUtilCore.toPsiElementArray(found);
        }
      }.searchImplementations(editor, element, offset);
      if (targetElements == null) {
        return null;
      }
      if (targetElements.length > 1) {
        PsiElement elementAtPointer = file.findElementAt(offset);
        if (elementAtPointer != null) {
          return new InfoMultiple(elementAtPointer);
        }
        return null;
      }
      if (targetElements.length == 1) {
        Navigatable descriptor = EditSourceUtil.getDescriptor(targetElements[0]);
        if (descriptor == null || !descriptor.canNavigate()) {
          return null;
        }
        targetElement = targetElements[0];
      }
    }

    if (targetElement != null && targetElement.isPhysical()) {
      PsiElement elementAtPointer = file.findElementAt(offset);
      if (elementAtPointer != null) {
        return new InfoSingle(elementAtPointer, targetElement);
      }
    }

    final PsiElement element = GotoDeclarationAction.findElementToShowUsagesOf(editor, offset);
    if (element instanceof PsiNameIdentifierOwner) {
      PsiElement identifier = ((PsiNameIdentifierOwner)element).getNameIdentifier();
      if (identifier != null && identifier.isValid()) {
        return new Info(identifier){
          @Override
          public void showDocInfo(@NotNull DocumentationManager docManager) {
          }
  
          @NotNull
          @Override
          public DocInfo getInfo() {
            String name = UsageViewUtil.getType(element) + " '"+ UsageViewUtil.getShortName(element)+"'";
            return new DocInfo("Show usages of "+name, null, element);
          }
  
          @Override
          public boolean isValid(@NotNull Document document) {
            return element.isValid();
          }

          @Override
          public boolean isNavigatable() {
            return true;
          }
        };
      }
    }
    return null;
  }

  @NotNull
  private static List<PsiElement> resolve(@NotNull PsiReference ref) {
    // IDEA-56727 try resolve first as in GotoDeclarationAction
    PsiElement resolvedElement = ref.resolve();

    if (resolvedElement == null && ref instanceof PsiPolyVariantReference) {
      List<PsiElement> result = new ArrayList<>();
      final ResolveResult[] psiElements = ((PsiPolyVariantReference)ref).multiResolve(false);
      for (ResolveResult resolveResult : psiElements) {
        if (resolveResult.getElement() != null) {
          result.add(resolveResult.getElement());
        }
      }
      return result;
    }
    return resolvedElement == null ? Collections.emptyList() : Collections.singletonList(resolvedElement);
  }

  private void disposeHighlighter() {
    if (myHighlighter != null) {
      HighlightersSet highlighter = myHighlighter;
      myHighlighter = null;
      highlighter.uninstall();
      HintManager.getInstance().hideAllHints();
    }
  }

  private void fulfillDocInfo(@NotNull final String header,
                              @NotNull final DocumentationProvider provider,
                              @NotNull final PsiElement originalElement,
                              @NotNull final PsiElement anchorElement,
                              @NotNull final Consumer<String> newTextConsumer,
                              @NotNull final LightweightHint hint)
  {
    myDocAlarm.cancelAllRequests();
    myDocAlarm.addRequest(() -> {
      final Ref<String> fullTextRef = new Ref<>();
      final Ref<String> qualifiedNameRef = new Ref<>();
      ApplicationManager.getApplication().runReadAction(() -> {
        if (anchorElement.isValid() && originalElement.isValid()) {
          try {
            fullTextRef.set(provider.generateDoc(anchorElement, originalElement));
          }
          catch (IndexNotReadyException e) {
            fullTextRef.set("Documentation is not available while indexing is in progress");
          }
          if (anchorElement instanceof PsiQualifiedNamedElement) {
            qualifiedNameRef.set(((PsiQualifiedNamedElement)anchorElement).getQualifiedName());
          }
        }
      });
      String fullText = fullTextRef.get();
      if (fullText == null) {
        return;
      }
      final String updatedText = DocPreviewUtil.buildPreview(header, qualifiedNameRef.get(), fullText);
      final String newHtml = HintUtil.prepareHintText(updatedText, HintUtil.getInformationHint());
      UIUtil.invokeLaterIfNeeded(() -> {

        // There is a possible case that quick doc control width is changed, e.g. it contained text
        // like 'public final class String implements java.io.Serializable, java.lang.Comparable<java.lang.String>' and
        // new text replaces fully-qualified class names by hyperlinks with short name.
        // That's why we might need to update the control size. We assume that the hint component is located at the
        // layered pane, so, the algorithm is to find an ancestor layered pane and apply new size for the target component.

        JComponent component = hint.getComponent();
        Dimension oldSize = component.getPreferredSize();
        newTextConsumer.consume(newHtml);

        final int widthIncrease;
        if (component instanceof QuickDocInfoPane) {
          int buttonWidth = ((QuickDocInfoPane)component).getButtonWidth();
          widthIncrease = calculateWidthIncrease(buttonWidth, updatedText);
        }
        else {
          widthIncrease = 0;
        }

        if (oldSize == null) {
          return;
        }

        Dimension newSize = component.getPreferredSize();
        if (newSize.width + widthIncrease == oldSize.width) {
          return;
        }
        component.setPreferredSize(new Dimension(newSize.width + widthIncrease, newSize.height));

        // We're assuming here that there are two possible hint representation modes: popup and layered pane.
        if (hint.isRealPopup()) {

          TooltipProvider tooltipProvider = myTooltipProvider;
          if (tooltipProvider != null) {
            // There is a possible case that 'raw' control was rather wide but the 'rich' one is narrower. That's why we try to
            // re-show the hint here. Benefits: there is a possible case that we'll be able to show nice layered pane-based balloon;
            // the popup will be re-positioned according to the new width.
            hint.hide();
            tooltipProvider.showHint(new LightweightHint(component));
          }
          else {
            component.setPreferredSize(new Dimension(newSize.width + widthIncrease, oldSize.height));
            hint.pack();
          }
          return;
        }

        Container topLevelLayeredPaneChild = null;
        boolean adjustBounds = false;
        for (Container current = component.getParent(); current != null; current = current.getParent()) {
          if (current instanceof JLayeredPane) {
            adjustBounds = true;
            break;
          }
          else {
            topLevelLayeredPaneChild = current;
          }
        }

        if (adjustBounds && topLevelLayeredPaneChild != null) {
          Rectangle bounds = topLevelLayeredPaneChild.getBounds();
          topLevelLayeredPaneChild.setBounds(bounds.x, bounds.y, bounds.width + newSize.width + widthIncrease - oldSize.width, bounds.height);
        }
      });
    }, 0);
  }

  /**
   * It's possible that we need to expand quick doc control's width in order to provide better visual representation
   * (see https://youtrack.jetbrains.com/issue/IDEA-101425). This method calculates that width expand.
   *
   * @param buttonWidth  icon button's width
   * @param updatedText  text which will be should at the quick doc control
   * @return             width increase to apply to the target quick doc control (zero if no additional width increase is required)
   */
  private static int calculateWidthIncrease(int buttonWidth, String updatedText) {
    int maxLineWidth = 0;
    TIntArrayList lineWidths = new TIntArrayList();
    for (String lineText : StringUtil.split(updatedText, "<br/>")) {
      String html = HintUtil.prepareHintText(lineText, HintUtil.getInformationHint());
      int width = new JLabel(html).getPreferredSize().width;
      maxLineWidth = Math.max(maxLineWidth, width);
      lineWidths.add(width);
    }

    if (!lineWidths.isEmpty()) {
      int firstLineAvailableTrailingWidth = maxLineWidth - lineWidths.get(0);
      if (firstLineAvailableTrailingWidth >= buttonWidth) {
        return 0;
      }
      else {
        return buttonWidth - firstLineAvailableTrailingWidth;
      }
    }
    return 0;
  }

  private class TooltipProvider {
    @NotNull private final Editor myEditor;
    @NotNull private final LogicalPosition myPosition;
    private BrowseMode myBrowseMode;
    private boolean myDisposed;
    private final ProgressIndicator myProgress = new ProgressIndicatorBase();

    TooltipProvider(@NotNull Editor editor, @NotNull LogicalPosition pos) {
      myEditor = editor;
      myPosition = pos;
    }

    void dispose() {
      myDisposed = true;
      myProgress.cancel();
    }

    public BrowseMode getBrowseMode() {
      return myBrowseMode;
    }

    void execute(@NotNull BrowseMode browseMode) {
      myBrowseMode = browseMode;

      if (PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument()) == null) return;

      if (EditorUtil.inVirtualSpace(myEditor, myPosition)) {
        disposeHighlighter();
        return;
      }

      final int offset = myEditor.logicalPositionToOffset(myPosition);

      int selStart = myEditor.getSelectionModel().getSelectionStart();
      int selEnd = myEditor.getSelectionModel().getSelectionEnd();

      if (offset >= selStart && offset < selEnd) return;

      PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(
        () -> ProgressIndicatorUtils.scheduleWithWriteActionPriority(myProgress, new ReadTask() {
          @Nullable
          @Override
          public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
            return doExecute(offset);
          }

          @Override
          public void onCanceled(@NotNull ProgressIndicator indicator) {
            LOG.debug("Highlighting was cancelled");
          }
        }));
    }

    @Nullable
    private ReadTask.Continuation doExecute(int offset) {
      if (isTaskOutdated()) return null;

      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
      if (file == null) return null;

      final Info info;
      final DocInfo docInfo;
      try {
        info = getInfoAt(myEditor, file, offset, myBrowseMode);
        if (info == null) return null;
        docInfo = info.getInfo();
      }
      catch (IndexNotReadyException e) {
        showDumbModeNotification(myProject);
        return null;
      }

      LOG.debug("Obtained info about element under cursor");
      return new ReadTask.Continuation(() -> {
        if (myDisposed || myEditor.isDisposed() || !myEditor.getComponent().isShowing()) return;
        showHint(info, docInfo);
      });
    }

    private boolean isTaskOutdated() {
      return myDisposed || myProject.isDisposed() || myEditor.isDisposed() || !myEditor.getComponent().isShowing();
    }

    private void showHint(@NotNull Info info, @NotNull DocInfo docInfo) {
      if (myDisposed || myEditor.isDisposed()) return;
      Component internalComponent = myEditor.getContentComponent();
      if (myHighlighter != null) {
        if (!info.isSimilarTo(myHighlighter.getStoredInfo())) {
          disposeHighlighter();
        }
        else {
          // highlighter already set
          if (info.isNavigatable()) {
            internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          }
          return;
        }
      }

      if (!info.isValid(myEditor.getDocument()) || !info.isNavigatable() && docInfo.text == null) {
        return;
      }

      myHighlighter = installHighlighterSet(info, myEditor);

      if (docInfo.text == null) return;

      if (myDocumentationManager.hasActiveDockedDocWindow()) {
        info.showDocInfo(myDocumentationManager);
      }

      HyperlinkListener hyperlinkListener = docInfo.docProvider == null
                                   ? null
                                   : new QuickDocHyperlinkListener(docInfo.docProvider, info.myElementAtPointer);
      final Ref<QuickDocInfoPane> quickDocPaneRef = new Ref<>();
      MouseListener mouseListener = new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          QuickDocInfoPane pane = quickDocPaneRef.get();
          if (pane != null) {
            pane.mouseEntered(e);
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          QuickDocInfoPane pane = quickDocPaneRef.get();
          if (pane != null) {
            pane.mouseExited(e);
          }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
        }
      };
      Ref<Consumer<String>> newTextConsumerRef = new Ref<>();
      JComponent label = HintUtil.createInformationLabel(docInfo.text, hyperlinkListener, mouseListener, newTextConsumerRef);
      Consumer<String> newTextConsumer = newTextConsumerRef.get();
      QuickDocInfoPane quickDocPane = null;
      if (docInfo.documentationAnchor != null) {
        quickDocPane = new QuickDocInfoPane(docInfo.documentationAnchor, info.myElementAtPointer, label);
        quickDocPaneRef.set(quickDocPane);
      }

      JComponent hintContent = quickDocPane == null ? label : quickDocPane;

      final LightweightHint hint = new LightweightHint(hintContent);
      myHint = hint;
      hint.addHintListener(new HintListener() {
        @Override
        public void hintHidden(EventObject event) {
          myHint = null;
        }
      });
      myDocAlarm.cancelAllRequests();
      if (newTextConsumer != null && docInfo.docProvider != null && docInfo.documentationAnchor != null) {
        fulfillDocInfo(docInfo.text, docInfo.docProvider, info.myElementAtPointer, docInfo.documentationAnchor, newTextConsumer, hint);
      }

      showHint(hint);
    }

    public void showHint(@NotNull LightweightHint hint) {
      if (myEditor.isDisposed()) return;
      final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
      short constraint = HintManager.ABOVE;
      Point p = HintManagerImpl.getHintPosition(hint, myEditor, myPosition, constraint);
      if (p.y - hint.getComponent().getPreferredSize().height < 0) {
        constraint = HintManager.UNDER;
        p = HintManagerImpl.getHintPosition(hint, myEditor, myPosition, constraint);
      }
      hintManager.showEditorHint(hint, myEditor, p,
                                 HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
                                 0, false, HintManagerImpl.createHintHint(myEditor, p,  hint, constraint).setContentActive(false));
    }
  }

  @NotNull
  private HighlightersSet installHighlighterSet(@NotNull Info info, @NotNull Editor editor) {
    final JComponent internalComponent = editor.getContentComponent();
    internalComponent.addKeyListener(myEditorKeyListener);
    editor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    final Cursor cursor = internalComponent.getCursor();
    if (info.isNavigatable()) {
      internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    myFileEditorManager.addFileEditorManagerListener(myFileEditorManagerListener);

    List<RangeHighlighter> highlighters = new ArrayList<>();
    TextAttributes attributes = info.isNavigatable() 
                                ? myEditorColorsManager.getGlobalScheme().getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR) 
                                : new TextAttributes(null, HintUtil.getInformationColor(), null, null, Font.PLAIN);
    for (TextRange range : info.getRanges()) {
      TextAttributes attr = NavigationUtil.patchAttributesColor(attributes, range, editor);
      final RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(range.getStartOffset(), range.getEndOffset(),
                                                                                       HighlighterLayer.HYPERLINK, 
                                                                                       attr,
                                                                                       HighlighterTargetArea.EXACT_RANGE);
      highlighters.add(highlighter);
    }

    return new HighlightersSet(highlighters, editor, cursor, info);
  }


  private class HighlightersSet {
    @NotNull private final List<RangeHighlighter> myHighlighters;
    @NotNull private final Editor myHighlighterView;
    @NotNull private final Cursor myStoredCursor;
    @NotNull private final Info myStoredInfo;

    private HighlightersSet(@NotNull List<RangeHighlighter> highlighters,
                            @NotNull Editor highlighterView,
                            @NotNull Cursor storedCursor,
                            @NotNull Info storedInfo) {
      myHighlighters = highlighters;
      myHighlighterView = highlighterView;
      myStoredCursor = storedCursor;
      myStoredInfo = storedInfo;
    }

    public void uninstall() {
      for (RangeHighlighter highlighter : myHighlighters) {
        highlighter.dispose();
      }

      Component internalComponent = myHighlighterView.getContentComponent();
      internalComponent.setCursor(myStoredCursor);
      internalComponent.removeKeyListener(myEditorKeyListener);
      myHighlighterView.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
      myFileEditorManager.removeFileEditorManagerListener(myFileEditorManagerListener);
    }

    @NotNull
    public Info getStoredInfo() {
      return myStoredInfo;
    }
  }

  private static class DocInfo {
    public static final DocInfo EMPTY = new DocInfo(null, null, null);

    @Nullable public final String text;
    @Nullable public final DocumentationProvider docProvider;
    @Nullable public final PsiElement documentationAnchor;

    DocInfo(@Nullable String text, @Nullable DocumentationProvider provider, @Nullable PsiElement documentationAnchor) {
      this.text = text;
      docProvider = provider;
      this.documentationAnchor = documentationAnchor;
    }
  }

  private class QuickDocInfoPane extends JBLayeredPane {
    private static final int BUTTON_HGAP = 5;

    @NotNull private final List<JComponent> myButtons = new ArrayList<>();

    @NotNull private final JComponent myBaseDocControl;

    private final int myMinWidth;
    private final int myMinHeight;
    private final int myButtonWidth;

    QuickDocInfoPane(@NotNull PsiElement documentationAnchor,
                     @NotNull PsiElement elementUnderMouse,
                     @NotNull JComponent baseDocControl) {
      myBaseDocControl = baseDocControl;

      PresentationFactory presentationFactory = new PresentationFactory();
      for (AbstractDocumentationTooltipAction action : ourTooltipActions) {
        Icon icon = action.getTemplatePresentation().getIcon();
        Dimension minSize = new Dimension(icon.getIconWidth(), icon.getIconHeight());
        myButtons.add(new ActionButton(action, presentationFactory.getPresentation(action), IdeTooltipManager.IDE_TOOLTIP_PLACE, minSize));
        action.setDocInfo(documentationAnchor, elementUnderMouse);
      }
      Collections.reverse(myButtons);

      setPreferredSize(baseDocControl.getPreferredSize());
      setMaximumSize(baseDocControl.getMaximumSize());
      setMinimumSize(baseDocControl.getMinimumSize());
      setBackground(baseDocControl.getBackground());

      add(baseDocControl, Integer.valueOf(0));
      int minWidth = 0;
      int minHeight = 0;
      int buttonWidth = 0;
      for (JComponent button : myButtons) {
        button.setBorder(null);
        button.setBackground(baseDocControl.getBackground());
        add(button, Integer.valueOf(1));
        button.setVisible(false);
        Dimension preferredSize = button.getPreferredSize();
        minWidth += preferredSize.width;
        minHeight = Math.max(minHeight, preferredSize.height);
        buttonWidth = Math.max(buttonWidth, preferredSize.width);
      }
      myButtonWidth = buttonWidth;

      int margin = 2;
      myMinWidth = minWidth + margin * 2 + (myButtons.size() - 1) * BUTTON_HGAP;
      myMinHeight = minHeight + margin * 2;
    }

    public int getButtonWidth() {
      return myButtonWidth;
    }

    @Override
    public Dimension getPreferredSize() {
      return expandIfNecessary(myBaseDocControl.getPreferredSize());
    }

    @Override
    public void setPreferredSize(Dimension preferredSize) {
      super.setPreferredSize(preferredSize);
      myBaseDocControl.setPreferredSize(preferredSize);
    }

    @Override
    public Dimension getMinimumSize() {
      return expandIfNecessary(myBaseDocControl.getMinimumSize());
    }

    @Override
    public Dimension getMaximumSize() {
      return expandIfNecessary(myBaseDocControl.getMaximumSize());
    }

    @NotNull
    private Dimension expandIfNecessary(@NotNull Dimension base) {
      if (base.width >= myMinWidth && base.height >= myMinHeight) {
        return base;
      }
      return new Dimension(Math.max(myMinWidth, base.width), Math.max(myMinHeight, base.height));
    }

    @Override
    public void doLayout() {
      Rectangle bounds = getBounds();
      myBaseDocControl.setBounds(new Rectangle(0, 0, bounds.width, bounds.height));

      int x = bounds.width;
      for (JComponent button : myButtons) {
        Dimension buttonSize = button.getPreferredSize();
        x -= buttonSize.width;
        button.setBounds(x, 0, buttonSize.width, buttonSize.height);
        x -= BUTTON_HGAP;
      }
    }

    public void mouseEntered(@NotNull MouseEvent e) {
      processStateChangeIfNecessary(e.getLocationOnScreen(), true);
    }

    public void mouseExited(@NotNull MouseEvent e) {
      processStateChangeIfNecessary(e.getLocationOnScreen(), false);
    }

    private void processStateChangeIfNecessary(@NotNull Point mouseScreenLocation, boolean mouseEntered) {
      // Don't show 'view quick doc' buttons if docked quick doc control is already active.
      if (myDocumentationManager.hasActiveDockedDocWindow()) {
        return;
      }

      // Skip event triggered when mouse leaves action button area.
      if (!mouseEntered && new Rectangle(getLocationOnScreen(), getSize()).contains(mouseScreenLocation)) {
        return;
      }
      for (JComponent button : myButtons) {
        button.setVisible(mouseEntered);
      }
    }
  }

  private class QuickDocHyperlinkListener implements HyperlinkListener {
    @NotNull private final DocumentationProvider myProvider;
    @NotNull private final PsiElement myContext;

    QuickDocHyperlinkListener(@NotNull DocumentationProvider provider, @NotNull PsiElement context) {
      myProvider = provider;
      myContext = context;
    }

    @Override
    public void hyperlinkUpdate(@NotNull HyperlinkEvent e) {
      if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
        return;
      }

      String description = e.getDescription();
      if (StringUtil.isEmpty(description) || !description.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
        return;
      }

      String elementName = e.getDescription().substring(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL.length());

      DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> {
        PsiElement targetElement = myProvider.getDocumentationElementForLink(PsiManager.getInstance(myProject), elementName, myContext);
        if (targetElement != null) {
          LightweightHint hint = myHint;
          if (hint != null) {
            hint.hide(true);
          }
          myDocumentationManager.showJavaDocInfo(targetElement, myContext, null);
        }
      });
    }
  }
}
