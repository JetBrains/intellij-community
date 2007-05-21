package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.components.ProjectComponent;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.search.searches.DefinitionsSearch;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlToken;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class CtrlMouseHandler implements ProjectComponent {
  private final Project myProject;
  private final TextAttributes ourReferenceAttributes;
  private RangeHighlighter myHighlighter;
  private Editor myHighlighterView;
  private Cursor myStoredCursor;
  private Info myStoredInfo;
  private int myStoredModifiers = 0;
  private TooltipProvider myTooltipProvider = null;

  enum BrowseMode { None, Declaration, TypeDeclaration, Implementation }

  private final KeyListener myEditorKeyListener = new KeyAdapter() {
    public void keyPressed(final KeyEvent e) {
      handleKey(e);
    }

    public void keyReleased(final KeyEvent e) {
      handleKey(e);
    }

    private void handleKey(final KeyEvent e) {
      int modifiers = e.getModifiers();
      if ( modifiers == myStoredModifiers) {
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
      } else {
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
      if (e.isConsumed() || myProject.isDisposed()) {
        return;
      }


      MouseEvent mouseEvent = e.getMouseEvent();

      Editor editor = e.getEditor();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      Point point = new Point(mouseEvent.getPoint());
      editor = InjectedLanguageUtil.getEditorForInjectedLanguage(editor, psiFile, editor.logicalPositionToOffset(editor.xyToLogicalPosition(point)));
      LogicalPosition pos = editor.xyToLogicalPosition(point);
      int offset = editor.logicalPositionToOffset(pos);
      int selStart = editor.getSelectionModel().getSelectionStart();
      int selEnd = editor.getSelectionModel().getSelectionEnd();

      myStoredModifiers = mouseEvent.getModifiers();
      BrowseMode browseMode = getBrowseMode(myStoredModifiers);

      if (browseMode == BrowseMode.None || offset >= selStart && offset < selEnd) {
        disposeHighlighter();
        myTooltipProvider = null;
        return;
      }

      myTooltipProvider = new TooltipProvider(editor, pos);
      myTooltipProvider.execute(browseMode);
    }
  };

  public static final TextAttributesKey CTRL_CLICKABLE_ATTRIBUTES_KEY =
    TextAttributesKey.createTextAttributesKey("CTRL_CLICKABLE", new TextAttributes(Color.blue, null, Color.blue, EffectType.LINE_UNDERSCORE, 0));

  public CtrlMouseHandler(final Project project, StartupManager startupManager, EditorColorsManager colorsManager) {
    myProject = project;
    startupManager.registerPostStartupActivity(new Runnable(){
      public void run() {
        EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
        eventMulticaster.addEditorMouseListener(myEditorMouseAdapter, project);
        eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener, project);
      }
    });
    ourReferenceAttributes = colorsManager.getGlobalScheme().getAttributes(CTRL_CLICKABLE_ATTRIBUTES_KEY);
  }

  @NotNull
  public String getComponentName() {
    return "CtrlMouseHandler";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  private static BrowseMode getBrowseMode(final int modifiers) {
    if ( modifiers != 0 ) {
      final Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
      if (matchMouseShourtcut(activeKeymap, modifiers, IdeActions.ACTION_GOTO_DECLARATION)) return BrowseMode.Declaration;
      if (matchMouseShourtcut(activeKeymap, modifiers, IdeActions.ACTION_GOTO_TYPE_DECLARATION)) return BrowseMode.TypeDeclaration;
      if (matchMouseShourtcut(activeKeymap, modifiers, IdeActions.ACTION_GOTO_IMPLEMENTATION)) return BrowseMode.Implementation;
    }
    return BrowseMode.None;
  }

  private static boolean matchMouseShourtcut(final Keymap activeKeymap, final int modifiers, final String actionId) {
    final MouseShortcut syntheticShortcat = new MouseShortcut(MouseEvent.BUTTON1, modifiers, 1);
    for ( Shortcut shortcut : activeKeymap.getShortcuts(actionId)) {
      if ( shortcut instanceof MouseShortcut) {
        final MouseShortcut mouseShortcut = ((MouseShortcut)shortcut);
        if ( mouseShortcut.getModifiers() == syntheticShortcat.getModifiers() ) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static String generateInfo(PsiElement element) {
    final PsiFile file = element.getContainingFile();
    final Language language = (file != null ? file : element).getLanguage();
    final DocumentationProvider documentationProvider = language.getDocumentationProvider();
    if (documentationProvider != null) {
      String info = documentationProvider.getQuickNavigateInfo(element);
      if (info != null) {
        return info;
      }
    }

    if (element instanceof PsiFile) {
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null) {
        return virtualFile.getPresentableUrl();
      }
    }
    return null;
  }

  private abstract static class Info {
    @NotNull protected final PsiElement myElementAtPointer;
    public final int myStartOffset;
    public final int myEndOffset;

    public Info(@NotNull PsiElement elementAtPointer, int startOffset, int endOffset ) {
      myElementAtPointer = elementAtPointer;
      myStartOffset = startOffset;
      myEndOffset = endOffset;
    }

    public Info(@NotNull PsiElement elementAtPointer ) {
      myElementAtPointer = elementAtPointer;
      myStartOffset = elementAtPointer.getTextOffset();
      myEndOffset = myStartOffset + elementAtPointer.getTextLength();
    }

    boolean isSimilarTo(final Info that) {
      return Comparing.equal(myElementAtPointer, that.myElementAtPointer) &&
             myStartOffset == that.myStartOffset &&
             myEndOffset == that.myEndOffset;
    }

    @Nullable
    abstract public String getInfo();

    abstract public boolean isValid();
  }

  private static class InfoSingle extends Info {
    @NotNull private final PsiElement myTargetElement;

    public InfoSingle(@NotNull PsiElement elementAtPointer, @NotNull PsiElement targetElement) {
      super ( elementAtPointer );
      myTargetElement = targetElement;
    }

    public InfoSingle(final PsiReference ref, @NotNull final PsiElement targetElement) {
      super ( ref.getElement(),
              ref.getElement().getTextRange().getStartOffset() + ref.getRangeInElement().getStartOffset(),
              ref.getElement().getTextRange().getStartOffset() + ref.getRangeInElement().getEndOffset());
      myTargetElement = targetElement;
    }

    @Nullable
    public String getInfo() {
      return generateInfo(myTargetElement);
    }

    public boolean isValid() {
      return  myTargetElement != myElementAtPointer &&
              myTargetElement != myElementAtPointer.getParent() &&
              targetNavigateable(myTargetElement);
    }

    private static boolean targetNavigateable(final PsiElement targetElement) {
      PsiElement navElement = targetElement.getNavigationElement();
      return navElement instanceof Navigatable && ((Navigatable)navElement).canNavigate();
    }
  }

  private static class InfoMultiple extends Info {

    public InfoMultiple(@NotNull final PsiElement elementAtPointer) {
      super ( elementAtPointer );
    }

    public String getInfo() {
      return CodeInsightBundle.message("multiple.implementations.tooltip");
    }

    public boolean isValid() {
      return true;
    }
  }

  @Nullable
  private Info getInfoAt(final Editor editor, LogicalPosition pos, BrowseMode browseMode) {
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file == null) return null;
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final int offset = editor.logicalPositionToOffset(pos);

    int selStart = editor.getSelectionModel().getSelectionStart();
    int selEnd = editor.getSelectionModel().getSelectionEnd();

    if (offset >= selStart && offset < selEnd) return null;

    PsiElement targetElement = null;

    if (browseMode == BrowseMode.TypeDeclaration) {
      targetElement = GotoTypeDeclarationAction.findSymbolType(editor, offset);
    }
    else if (browseMode == BrowseMode.Declaration) {
      PsiReference ref = TargetElementUtil.findReference(editor, offset);
      if (ref != null) {
        PsiElement resolvedElement = resolve(ref);
        if (resolvedElement != null) {
          return new InfoSingle (ref, resolvedElement);
        }
      }
      targetElement = GotoDeclarationAction.findTargetElement(myProject, editor, offset);
    } else if ( browseMode == BrowseMode.Implementation ) {
      final PsiElement element = TargetElementUtil.findTargetElement(editor, GotoImplementationHandler.FLAGS, offset);
      PsiElement[] targetElements = new GotoImplementationHandler() {
        @NotNull
        protected PsiElement[] searchDefinitions(final PsiElement element) {
          final List<PsiElement> found = new ArrayList<PsiElement>(2);
          DefinitionsSearch.search(element).forEach( new Processor<PsiElement>() {
            public boolean process(final PsiElement psiElement) {
              found.add ( psiElement );
              return found.size() != 2;
            }
          });
          return found.toArray(PsiElement.EMPTY_ARRAY);
        }
      }.searchImplementations(editor, file, element, offset);
      if ( targetElements.length > 1) {
        PsiElement elementAtPointer = findElementAtPointer(file, offset);
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
        targetElement = targetElements [ 0 ];
      }
    }

    if (targetElement != null && targetElement.isPhysical() ) {
      PsiElement elementAtPointer = findElementAtPointer(file, offset);
      if ( elementAtPointer != null ) {
         return new InfoSingle(elementAtPointer, targetElement);
      }
    }

    return null;
  }

  @Nullable
  private static PsiElement resolve(final PsiReference ref) {
    PsiElement resolvedElement;

    if (ref instanceof PsiPolyVariantReference) {
      final ResolveResult[] psiElements = ((PsiPolyVariantReference)ref).multiResolve(false);
      resolvedElement = psiElements.length > 0 ? psiElements[0].getElement() : null;

      if (resolvedElement instanceof XmlAttributeValue &&
          ref.getElement() == resolvedElement &&     // guard for id references
          psiElements.length > 1
         ) {
        resolvedElement = psiElements[1].getElement();
      }
    }
    else {
      resolvedElement = ref.resolve();
    }
    return resolvedElement;
  }

  @Nullable
  private static PsiElement findElementAtPointer(final PsiFile file, final int offset) {
    PsiElement elementAtPointer = file.findElementAt(offset);
    if (elementAtPointer instanceof PsiIdentifier ||
        elementAtPointer instanceof PsiKeyword ||
        elementAtPointer instanceof PsiDocToken ||
        elementAtPointer instanceof XmlToken) {
      return elementAtPointer;
    }
    return null;
  }

  private void disposeHighlighter() {
    if (myHighlighter != null) {
      myHighlighterView.getMarkupModel().removeHighlighter(myHighlighter);
      Component internalComponent = myHighlighterView.getContentComponent();
      internalComponent.setCursor(myStoredCursor);
      internalComponent.removeKeyListener(myEditorKeyListener);
      myHighlighterView.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
      FileEditorManager.getInstance(myProject).removeFileEditorManagerListener(myFileEditorManagerListener);
      HintManager hintManager = HintManager.getInstance();
      hintManager.hideAllHints();
      myHighlighter = null;
      myHighlighterView = null;
      myStoredCursor = null;
    }
    myStoredInfo = null;
  }

  private class TooltipProvider {
    private final Editor myEditor;
    private final LogicalPosition myPosition;
    private BrowseMode myBrowseMode;

    public TooltipProvider(Editor editor, LogicalPosition pos) {
      myEditor = editor;
      myPosition = pos;
    }

    public BrowseMode getBrowseMode() {
      return myBrowseMode;
    }

    public void execute(BrowseMode browseMode) {
      myBrowseMode = browseMode;
      Info info = getInfoAt(myEditor, myPosition, myBrowseMode);
      if (info == null) return;

      Component internalComponent = myEditor.getContentComponent();
      if (myHighlighter != null) {
        if (!info.isSimilarTo(myStoredInfo)) {
          disposeHighlighter();
        } else {
          // highlighter already set
          internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          return;
        }
      }

      if (info.isValid()) {

        installLinkHighlighter(info);

        internalComponent.addKeyListener(myEditorKeyListener);
        myEditor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
        myStoredCursor = internalComponent.getCursor();
        myStoredInfo = info;
        internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        FileEditorManager.getInstance(myProject).addFileEditorManagerListener(myFileEditorManagerListener);

        String text = info.getInfo();

        if (text == null) return;

        JLabel label = HintUtil.createInformationLabel(text);
        label.setUI(new MultiLineLabelUI());
        Font FONT = UIUtil.getLabelFont();
        label.setFont(FONT);
        final LightweightHint hint = new LightweightHint(label);
        final HintManager hintManager = HintManager.getInstance();
        label.addMouseMotionListener(new MouseMotionAdapter() {
          public void mouseMoved(MouseEvent e) {
            hintManager.hideAllHints();
          }
        });
        Point p = HintManager.getHintPosition(hint, myEditor, myPosition, HintManager.ABOVE);
        hintManager.showEditorHint(hint, myEditor, p,
                                   HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE |
                                   HintManager.HIDE_BY_SCROLLING,
                                   0, false);
      }
    }

    private void installLinkHighlighter(Info info) {
      int startOffset = info.myStartOffset;
      int endOffset = info.myEndOffset;
      myHighlighter =
      myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, HighlighterLayer.SELECTION + 1,
                                                    ourReferenceAttributes, HighlighterTargetArea.EXACT_RANGE);
      myHighlighterView = myEditor;
    }
  }
}