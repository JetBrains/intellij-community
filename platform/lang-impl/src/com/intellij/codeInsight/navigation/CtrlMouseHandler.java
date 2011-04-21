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

package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.searches.DefinitionsSearch;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.LightweightHint;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CtrlMouseHandler extends AbstractProjectComponent {
  private final TextAttributes ourReferenceAttributes;
  private HighlightersSet myHighlighter;
  private int myStoredModifiers = 0;
  private TooltipProvider myTooltipProvider = null;
  private final FileEditorManager myFileEditorManager;

  private enum BrowseMode {None, Declaration, TypeDeclaration, Implementation}
  private final KeyListener myEditorKeyListener = new KeyAdapter() {
    public void keyPressed(final KeyEvent e) {
      handleKey(e);
    }

    public void keyReleased(final KeyEvent e) {
      handleKey(e);
    }

    private void handleKey(final KeyEvent e) {
      int modifiers = e.getModifiers();
      if (modifiers == myStoredModifiers) {
        return;
      }

      BrowseMode browseMode = getBrowseMode(modifiers);

      if (browseMode != BrowseMode.None) {
        if (myTooltipProvider != null) {
          if (browseMode != myTooltipProvider.getBrowseMode()) {
            disposeHighlighter();
          }
          myStoredModifiers = modifiers;
          myTooltipProvider.execute(browseMode);
        }
      }
      else {
        disposeHighlighter();
        myTooltipProvider = null;
      }
    }
  };

  private final FileEditorManagerListener myFileEditorManagerListener = new FileEditorManagerAdapter() {
    public void selectionChanged(FileEditorManagerEvent e) {
      disposeHighlighter();
      myTooltipProvider = null;
    }
  };

  private final VisibleAreaListener myVisibleAreaListener = new VisibleAreaListener() {
    public void visibleAreaChanged(VisibleAreaEvent e) {
      disposeHighlighter();
      myTooltipProvider = null;
    }
  };

  private final EditorMouseAdapter myEditorMouseAdapter = new EditorMouseAdapter() {
    public void mouseReleased(EditorMouseEvent e) {
      disposeHighlighter();
      myTooltipProvider = null;
    }
  };

  private final EditorMouseMotionListener myEditorMouseMotionListener = new EditorMouseMotionAdapter() {
    public void mouseMoved(final EditorMouseEvent e) {
      if (e.isConsumed() || !myProject.isInitialized()) {
        return;
      }
      MouseEvent mouseEvent = e.getMouseEvent();

      Editor editor = e.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      Point point = new Point(mouseEvent.getPoint());
      if (!PsiDocumentManager.getInstance(myProject).isUncommited(editor.getDocument())) {
        // when document is committed, try to check injected stuff - it's fast
        editor = InjectedLanguageUtil
          .getEditorForInjectedLanguageNoCommit(editor, psiFile, editor.logicalPositionToOffset(editor.xyToLogicalPosition(point)));
      }

      LogicalPosition pos = editor.xyToLogicalPosition(point);
      int offset = editor.logicalPositionToOffset(pos);
      int selStart = editor.getSelectionModel().getSelectionStart();
      int selEnd = editor.getSelectionModel().getSelectionEnd();

      myStoredModifiers = mouseEvent.getModifiers();
      BrowseMode browseMode = getBrowseMode(myStoredModifiers);

      if (myTooltipProvider != null) {
        myTooltipProvider.dispose();
      }

      if (browseMode == BrowseMode.None || offset >= selStart && offset < selEnd) {
        disposeHighlighter();
        myTooltipProvider = null;
        return;
      }

      myTooltipProvider = new TooltipProvider(editor, pos);
      myTooltipProvider.execute(browseMode);
    }
  };

  private static final TextAttributesKey CTRL_CLICKABLE_ATTRIBUTES_KEY =
    TextAttributesKey
      .createTextAttributesKey("CTRL_CLICKABLE", new TextAttributes(Color.blue, null, Color.blue, EffectType.LINE_UNDERSCORE, 0));

  public CtrlMouseHandler(final Project project, StartupManager startupManager, EditorColorsManager colorsManager,
                          FileEditorManager fileEditorManager) {
    super(project);
    startupManager.registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
        eventMulticaster.addEditorMouseListener(myEditorMouseAdapter, project);
        eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener, project);
      }
    });
    ourReferenceAttributes = colorsManager.getGlobalScheme().getAttributes(CTRL_CLICKABLE_ATTRIBUTES_KEY);
    myFileEditorManager = fileEditorManager;
  }

  @NotNull
  public String getComponentName() {
    return "CtrlMouseHandler";
  }

  private static BrowseMode getBrowseMode(final int modifiers) {
    if (modifiers != 0) {
      final Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
      if (matchMouseShortcut(activeKeymap, modifiers, IdeActions.ACTION_GOTO_DECLARATION)) return BrowseMode.Declaration;
      if (matchMouseShortcut(activeKeymap, modifiers, IdeActions.ACTION_GOTO_TYPE_DECLARATION)) return BrowseMode.TypeDeclaration;
      if (matchMouseShortcut(activeKeymap, modifiers, IdeActions.ACTION_GOTO_IMPLEMENTATION)) return BrowseMode.Implementation;
      if (modifiers == InputEvent.CTRL_MASK) return BrowseMode.Declaration;
    }
    return BrowseMode.None;
  }

  private static boolean matchMouseShortcut(final Keymap activeKeymap, final int modifiers, final String actionId) {
    final MouseShortcut syntheticShortcut = new MouseShortcut(MouseEvent.BUTTON1, modifiers, 1);
    for (Shortcut shortcut : activeKeymap.getShortcuts(actionId)) {
      if (shortcut instanceof MouseShortcut) {
        final MouseShortcut mouseShortcut = (MouseShortcut)shortcut;
        if (mouseShortcut.getModifiers() == syntheticShortcut.getModifiers()) {
          return true;
        }
      }
    }
    return false;
  }

  @TestOnly
  public static String getInfo(PsiElement element, PsiElement atPointer) {
    return generateInfo(element, atPointer);
  }

  @Nullable
  private static String generateInfo(PsiElement element, PsiElement atPointer) {
    final DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(element, atPointer);

    String info = documentationProvider.getQuickNavigateInfo(element, atPointer);
    if (info != null) {
      return info;
    }

    if (element instanceof PsiFile) {
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null) {
        return virtualFile.getPresentableUrl();
      }
    }

    info = getQuickNavigateInfo(element);
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
    private final List<TextRange> myRanges;

    public Info(@NotNull PsiElement elementAtPointer, List<TextRange> ranges) {
      myElementAtPointer = elementAtPointer;
      myRanges = ranges;
    }

    public Info(@NotNull PsiElement elementAtPointer) {
      this(elementAtPointer, Collections.singletonList(new TextRange(elementAtPointer.getTextOffset(),
                                                                     elementAtPointer.getTextOffset() + elementAtPointer.getTextLength())));
    }

    boolean isSimilarTo(final Info that) {
      return Comparing.equal(myElementAtPointer, that.myElementAtPointer) && myRanges.equals(that.myRanges);
    }

    public List<TextRange> getRanges() {
      return myRanges;
    }

    @Nullable
    public abstract String getInfo();

    public abstract boolean isValid(Document document);

    protected boolean rangesAreCorrect(Document document) {
      final TextRange docRange = new TextRange(0, document.getTextLength());
      for (TextRange range : getRanges()) {
        if (!docRange.contains(range)) return false;
      }

      return true;
    }
  }

  private static void showDumbModeNotification(final Project project) {
    DumbService.getInstance(project).showDumbModeNotification("Element information is not available during index update");
  }

  private static class InfoSingle extends Info {
    @NotNull private final PsiElement myTargetElement;

    public InfoSingle(@NotNull PsiElement elementAtPointer, @NotNull PsiElement targetElement) {
      super(elementAtPointer);
      myTargetElement = targetElement;
    }

    public InfoSingle(final PsiReference ref, @NotNull final PsiElement targetElement) {
      super(ref.getElement(), ReferenceRange.getAbsoluteRanges(ref));
      myTargetElement = targetElement;
    }

    @Nullable
    public String getInfo() {
      try {
        return generateInfo(myTargetElement, myElementAtPointer);
      }
      catch (IndexNotReadyException e) {
        showDumbModeNotification(myTargetElement.getProject());
        return null;
      }
    }

    public boolean isValid(Document document) {
      if (!myTargetElement.isValid()) return false;
      if (myTargetElement == myElementAtPointer || myTargetElement == myElementAtPointer.getParent()) return false;

      return rangesAreCorrect(document);
    }
  }

  private static class InfoMultiple extends Info {

    public InfoMultiple(@NotNull final PsiElement elementAtPointer) {
      super(elementAtPointer);
    }

    public String getInfo() {
      return CodeInsightBundle.message("multiple.implementations.tooltip");
    }

    public boolean isValid(Document document) {
      return rangesAreCorrect(document);
    }
  }

  @Nullable
  private Info getInfoAt(final Editor editor, PsiFile file, int offset, BrowseMode browseMode) {
    PsiElement targetElement = null;

    if (browseMode == BrowseMode.TypeDeclaration) {
      try {
        targetElement = GotoTypeDeclarationAction.findSymbolType(editor, offset);
      }
      catch (IndexNotReadyException e) {
        showDumbModeNotification(myProject);
      }
    }
    else if (browseMode == BrowseMode.Declaration) {
      final PsiReference ref = TargetElementUtilBase.findReference(editor, offset);
      final PsiElement resolvedElement = ref != null ? resolve(ref) : null;

      final PsiElement[] targetElements = GotoDeclarationAction.findTargetElementsNoVS(myProject, editor, offset);
      final PsiElement elementAtPointer = file.findElementAt(offset);

      if (targetElements != null) {
        if (targetElements.length == 0) {
          return null;
        }
        else if (targetElements.length == 1) {
          if (targetElements[0] != resolvedElement && elementAtPointer != null && targetElements[0].isPhysical()) {
            return new InfoSingle(elementAtPointer, targetElements[0]);
          }
        }
        else {
          return elementAtPointer != null ? new InfoMultiple(elementAtPointer) : null;
        }
      }

      if (resolvedElement != null) {
        return new InfoSingle(ref, resolvedElement);
      }
    }
    else if (browseMode == BrowseMode.Implementation) {
      final PsiElement element = TargetElementUtilBase.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
      PsiElement[] targetElements = new ImplementationSearcher() {
        @NotNull
        protected PsiElement[] searchDefinitions(final PsiElement element) {
          final List<PsiElement> found = new ArrayList<PsiElement>(2);
          DefinitionsSearch.search(element).forEach(new Processor<PsiElement>() {
            public boolean process(final PsiElement psiElement) {
              found.add(psiElement);
              return found.size() != 2;
            }
          });
          return PsiUtilBase.toPsiElementArray(found);
        }
      }.searchImplementations(editor, element, offset);
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

    return null;
  }

  @Nullable
  private static PsiElement resolve(final PsiReference ref) {
    // IDEA-56727 try resolve first as in GotoDeclarationAction
    PsiElement resolvedElement = ref.resolve();

    if (resolvedElement == null && ref instanceof PsiPolyVariantReference) {
      final ResolveResult[] psiElements = ((PsiPolyVariantReference)ref).multiResolve(false);
      if (psiElements.length > 0) {
        final ResolveResult resolveResult = psiElements[0];
        if (resolveResult != null) {
          resolvedElement = resolveResult.getElement();
        }
      }
    }
    return resolvedElement;
  }

  private void disposeHighlighter() {
    if (myHighlighter != null) {
      myHighlighter.uninstall();
      HintManager.getInstance().hideAllHints();
      myHighlighter = null;
    }
  }

  private class TooltipProvider {
    private final Editor myEditor;
    private final LogicalPosition myPosition;
    private BrowseMode myBrowseMode;
    private boolean myDisposed;

    public TooltipProvider(Editor editor, LogicalPosition pos) {
      myEditor = editor;
      myPosition = pos;
    }

    public void dispose() {
      myDisposed = true;
    }

    public BrowseMode getBrowseMode() {
      return myBrowseMode;
    }

    public void execute(BrowseMode browseMode) {
      myBrowseMode = browseMode;

      Document document = myEditor.getDocument();
      final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      if (file == null) return;
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      if (TargetElementUtilBase.inVirtualSpace(myEditor, myPosition)) {
        return;
      }

      final int offset = myEditor.logicalPositionToOffset(myPosition);

      int selStart = myEditor.getSelectionModel().getSelectionStart();
      int selEnd = myEditor.getSelectionModel().getSelectionEnd();

      if (offset >= selStart && offset < selEnd) return;

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          final ProgressIndicator progressIndicator = new ProgressIndicatorBase();
          final ApplicationAdapter listener = new ApplicationAdapter() {
            @Override
            public void beforeWriteActionStart(Object action) {
              progressIndicator.cancel();
            }
          };
          final Application application = ApplicationManager.getApplication();
          try {
            application.addApplicationListener(listener);
            ProgressManager.getInstance().runProcess(new Runnable(){
                @Override
                public void run() {
                  // This read action can possibe last for a long time, we want it to stop immediately on the first write access.
                  // For this purpose we launch it under empty progress and invoke progressIndicator#cancel on write access to avoid possible write lock delays.
                  application.runReadAction(new Runnable() {
                    public void run() {
                      doExecute(file, offset);
                    }
                  });
                }
              }, progressIndicator);
          }
          finally {
            application.removeApplicationListener(listener);
          }
        }
      });
    }

    private void doExecute(PsiFile file, int offset) {
      final Info info;
      try {
        info = getInfoAt(myEditor, file, offset, myBrowseMode);
      }
      catch (IndexNotReadyException e) {
        showDumbModeNotification(myProject);
        return;
      }
      if (info == null) return;

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myDisposed || myEditor.isDisposed() || !myEditor.getComponent().isShowing()) return;
          showHint(info);
        }
      });
    }

    private void showHint(Info info) {
      if (myDisposed) return;
      Component internalComponent = myEditor.getContentComponent();
      if (myHighlighter != null) {
        if (!info.isSimilarTo(myHighlighter.getStoredInfo())) {
          disposeHighlighter();
        }
        else {
          // highlighter already set
          internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          return;
        }
      }

      if (info.isValid(myEditor.getDocument())) {
        myHighlighter = installHightlighterSet(info, myEditor);

        String text = info.getInfo();

        if (text == null) return;

        JComponent label = HintUtil.createInformationLabel(text);
        final LightweightHint hint = new LightweightHint(label);
        final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
        Point p = HintManagerImpl.getHintPosition(hint, myEditor, myPosition, HintManager.ABOVE);
        hintManager.showEditorHint(hint, myEditor, p,
                                   HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
                                   0, false, HintManagerImpl.createHintHint(myEditor, p,  hint, HintManager.ABOVE).setContentActive(false));
      }
    }

  }

  private HighlightersSet installHightlighterSet(Info info, Editor editor) {
    final JComponent internalComponent = editor.getContentComponent();
    internalComponent.addKeyListener(myEditorKeyListener);
    editor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    final Cursor cursor = internalComponent.getCursor();
    internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myFileEditorManager.addFileEditorManagerListener(myFileEditorManagerListener);

    List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    for (TextRange range : info.getRanges()) {
      final RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(range.getStartOffset(), range.getEndOffset(),
                                                                                       HighlighterLayer.SELECTION + 1,
                                                                                       ourReferenceAttributes,
                                                                                       HighlighterTargetArea.EXACT_RANGE);
      highlighters.add(highlighter);
    }

    return new HighlightersSet(highlighters, editor, cursor, info);
  }

  private class HighlightersSet {
    private final List<RangeHighlighter> myHighlighters;
    private final Editor myHighlighterView;
    private final Cursor myStoredCursor;
    private final Info myStoredInfo;

    private HighlightersSet(List<RangeHighlighter> highlighters, Editor highlighterView, Cursor storedCursor, Info storedInfo) {
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

    public Info getStoredInfo() {
      return myStoredInfo;
    }
  }
}
