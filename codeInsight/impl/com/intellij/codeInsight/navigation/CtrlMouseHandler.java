package com.intellij.codeInsight.navigation;

import com.intellij.ant.PsiAntElement;
import com.intellij.ant.impl.dom.impl.PsiAntTarget;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.Language;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CtrlMouseHandler implements ProjectComponent {
  private final Project myProject;
  private static TextAttributes ourReferenceAttributes;
  private RangeHighlighter myHighlighter;
  private Editor myHighlighterView;
  private Cursor myStoredCursor;
  private Info myStoredInfo;
  private TooltipProvider myTooltipProvider = null;

  private final KeyListener myEditorKeyListener = new KeyAdapter() {
    public void keyPressed(final KeyEvent e) {
      handleKey(e);
    }

    public void keyReleased(final KeyEvent e) {
      handleKey(e);
    }

    private void handleKey(final KeyEvent e) {
      int modifiers = e.getModifiers();

      if (isControlShiftMask(modifiers)) {
        if (myTooltipProvider != null) {
          if (!myTooltipProvider.isTypeBrowsed()) {
            disposeHighlighter();
          }
          myTooltipProvider.execute(true);
        }
      } else if (isControlMask(modifiers)) {
        if (myTooltipProvider != null) {
          if (myTooltipProvider.isTypeBrowsed()) {
            disposeHighlighter();
          }
          myTooltipProvider.execute(false);
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
      if (e.isConsumed()) {
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

      int modifiers = mouseEvent.getModifiers();

      if (!isControlMask(modifiers) && !isControlShiftMask(modifiers)
          || offset >= selStart && offset < selEnd) {
        disposeHighlighter();
        myTooltipProvider = null;
        return;
      }

      myTooltipProvider = new TooltipProvider(editor, pos);
      myTooltipProvider.execute(isControlShiftMask(modifiers));
    }
  };

  static {
    ourReferenceAttributes = new TextAttributes();
    ourReferenceAttributes.setForegroundColor(Color.blue);
    ourReferenceAttributes.setEffectColor(Color.blue);
    ourReferenceAttributes.setEffectType(EffectType.LINE_UNDERSCORE);
  }

  public CtrlMouseHandler(Project project, StartupManager startupManager) {
    myProject = project;
    startupManager.registerPostStartupActivity(new Runnable(){
      public void run() {
        EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
        eventMulticaster.addEditorMouseListener(myEditorMouseAdapter);
        eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener);
      }
    });
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
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.removeEditorMouseListener(myEditorMouseAdapter);
    eventMulticaster.removeEditorMouseMotionListener(myEditorMouseMotionListener);
  }

  private static boolean isControlMask(int modifiers) {
    int mask = SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK;
    return modifiers == mask;
  }

  private static boolean isControlShiftMask(int modifiers) {
    int mask = (SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK) | InputEvent.SHIFT_MASK;
    return modifiers == mask;
  }

  private static class JavaInfoGenerator {

    @Nullable
    private static String generateAttributeValueInfo(PsiAntElement antElement) {
      if (antElement instanceof PsiAntTarget) return null;

      PsiElement navigationElement = antElement.getNavigationElement();
      if (navigationElement instanceof XmlAttributeValue) {
        return PsiTreeUtil.getParentOfType(navigationElement, XmlTag.class).getText();
      }

      return null;
    }

    private static String generateFileInfo(PsiFile file) {
      return file.getVirtualFile().getPresentableUrl();
    }

    @Nullable
    public static String generateInfo(PsiElement element) {
      final Language language = element.getLanguage();
      final DocumentationProvider documentationProvider = language.getDocumentationProvider();
      if(documentationProvider != null) {
        String info = documentationProvider.getQuickNavigateInfo(element);
        if (info != null) {
          return info;
        }
      }

      if (element instanceof PsiFile) {
        return generateFileInfo((PsiFile) element);
      } else if (element instanceof PsiAntElement) {
        return generateAttributeValueInfo((PsiAntElement)element);
      } else {
        return null;
      }
    }
  }

  private static class Info {
    public final PsiElement myTargetElement;
    public final PsiElement myElementAtPointer;
    public final int myStartOffset;
    public final int myEndOffset;

    public Info(PsiElement targetElement, PsiElement elementAtPointer) {
      myTargetElement = targetElement;
      myElementAtPointer = elementAtPointer;
      myStartOffset = elementAtPointer.getTextOffset();
      myEndOffset = myStartOffset + elementAtPointer.getTextLength();
    }

    public Info(PsiElement targetElement, PsiElement elementAtPointer, int startOffset, int endOffset) {
      myTargetElement = targetElement;
      myElementAtPointer = elementAtPointer;
      myStartOffset = startOffset;
      myEndOffset = endOffset;
    }
  }

  @Nullable
  private Info getInfoAt(final Editor editor, LogicalPosition pos, boolean browseType) {
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file == null) return null;
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final int offset = editor.logicalPositionToOffset(pos);

    int selStart = editor.getSelectionModel().getSelectionStart();
    int selEnd = editor.getSelectionModel().getSelectionEnd();

    if (offset >= selStart && offset < selEnd) return null;


    PsiElement targetElement;
    if (browseType) {
      targetElement = GotoTypeDeclarationAction.findSymbolType(editor, offset);
    }
    else {
      PsiReference ref = TargetElementUtil.findReference(editor, offset);
      if (ref != null) {
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

        if (resolvedElement != null) {
          PsiElement e = ref.getElement();
          return new Info(resolvedElement, e, e.getTextRange().getStartOffset() + ref.getRangeInElement().getStartOffset(),
                          e.getTextRange().getStartOffset() + ref.getRangeInElement().getEndOffset());
        }
      }
      targetElement = GotoDeclarationAction.findTargetElement(myProject, editor, offset);
    }
    if (targetElement != null && targetElement.isPhysical()) {
      PsiElement elementAtPointer = file.findElementAt(offset);
      if (elementAtPointer instanceof PsiIdentifier
          || elementAtPointer instanceof PsiKeyword
          || elementAtPointer instanceof PsiDocToken
          || elementAtPointer instanceof XmlToken) {
        return new Info(targetElement, elementAtPointer);
      }
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
    private boolean myBrowseType;

    public TooltipProvider(Editor editor, LogicalPosition pos) {
      myEditor = editor;
      myPosition = pos;
    }

    public boolean isTypeBrowsed() {
      return myBrowseType;
    }

    public void execute(boolean browseType) {
      myBrowseType = browseType;
      Info info = getInfoAt(myEditor, myPosition, myBrowseType);
      if (info == null) return;

      Component internalComponent = myEditor.getContentComponent();
      if (myHighlighter != null) {
        if (!Comparing.equal(info.myElementAtPointer, myStoredInfo.myElementAtPointer) ||
            info.myStartOffset != myStoredInfo.myStartOffset ||
            info.myEndOffset != myStoredInfo.myEndOffset) {
          disposeHighlighter();
        } else {
          // highlighter already set
          internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          return;
        }
      }

      if (info.myTargetElement != null &&
          info.myElementAtPointer != null &&
          info.myTargetElement != info.myElementAtPointer &&
          info.myTargetElement != info.myElementAtPointer.getParent() &&
          targetNavigateable(info.myTargetElement)
        ) {

        installLinkHighlighter(info);

        internalComponent.addKeyListener(myEditorKeyListener);
        myEditor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
        myStoredCursor = internalComponent.getCursor();
        myStoredInfo = info;
        internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        FileEditorManager.getInstance(myProject).addFileEditorManagerListener(myFileEditorManagerListener);

        String text = JavaInfoGenerator.generateInfo(info.myTargetElement);

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
        Point p = hintManager.getHintPosition(hint, myEditor, myPosition, HintManager.ABOVE);
        hintManager.showEditorHint(hint, myEditor, p,
                                   HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE |
                                   HintManager.HIDE_BY_SCROLLING,
                                   0, false);
      }
    }

    private boolean targetNavigateable(final PsiElement targetElement) {
      PsiElement navElement = targetElement.getNavigationElement();
      return navElement instanceof Navigatable && ((Navigatable)navElement).canNavigate();
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