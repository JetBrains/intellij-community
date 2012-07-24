/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
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
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
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
import java.util.List;

public class CtrlMouseHandler extends AbstractProjectComponent {

  public static final DataKey<Pair<PsiElement /* documentation anchor */, PsiElement /* element under mouse */>>
    ELEMENT_UNDER_MOUSE_INFO_KEY = DataKey.create("ElementUnderMouseInfo");

  private static final AnAction[] ourTooltipActions = {
    new ShowQuickDocFromTooltipAction(), new ShowQuickDocAtPinnedWindowFromTooltipAction()
  };

  private final TextAttributes  ourReferenceAttributes;
  private       HighlightersSet myHighlighter;
  @JdkConstants.InputEventMask private int             myStoredModifiers = 0;
  private                              TooltipProvider myTooltipProvider = null;
  private final FileEditorManager    myFileEditorManager;
  private final DocumentationManager myDocumentationManager;
  private final IdeTooltipManager    myTooltipManager;
  @Nullable private Point myPrevMouseLocation;

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

      if (isMouseOverTooltip(mouseEvent.getLocationOnScreen()) || isMouseMovedTowardTooltip(mouseEvent.getLocationOnScreen())) {
        myPrevMouseLocation = mouseEvent.getLocationOnScreen();
        return;
      }
      myPrevMouseLocation = mouseEvent.getLocationOnScreen();

      Editor editor = e.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      Point point = new Point(mouseEvent.getPoint());
      if (PsiDocumentManager.getInstance(myProject).isCommitted(editor.getDocument())) {
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

  @NotNull private final Alarm myDocAlarm;

  public CtrlMouseHandler(final Project project, StartupManager startupManager, EditorColorsManager colorsManager,
                          FileEditorManager fileEditorManager, @NotNull DocumentationManager documentationManager,
                          @NotNull final EditorFactory editorFactory, @NotNull IdeTooltipManager tooltipManager)
  {
    super(project);
    startupManager.registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        EditorEventMulticaster eventMulticaster = editorFactory.getEventMulticaster();
        eventMulticaster.addEditorMouseListener(myEditorMouseAdapter, project);
        eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener, project);
        eventMulticaster.addCaretListener(new CaretListener() {
          @Override
          public void caretPositionChanged(CaretEvent e) {
            myDocumentationManager.setAllowContentUpdateFromContext(true);
          }
        }, project);
      }
    });
    ourReferenceAttributes = colorsManager.getGlobalScheme().getAttributes(CTRL_CLICKABLE_ATTRIBUTES_KEY);
    myFileEditorManager = fileEditorManager;
    myDocumentationManager = documentationManager;
    myTooltipManager = tooltipManager;
    myDocAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, myProject);
  }

  @NotNull
  public String getComponentName() {
    return "CtrlMouseHandler";
  }

  private boolean isMouseOverTooltip(@NotNull Point mouseLocationOnScreen) {
    Rectangle bounds = getHintBounds(myTooltipManager.getCurrentTooltip());
    return bounds != null && bounds.contains(mouseLocationOnScreen);
  }

  private boolean isMouseMovedTowardTooltip(@NotNull Point mouseLocationOnScreen) {
    Rectangle bounds = getHintBounds(myTooltipManager.getCurrentTooltip());
    if (bounds == null) {
      return false;
    }

    Point prevLocation = myPrevMouseLocation;
    if (prevLocation == null) {
      myPrevMouseLocation = mouseLocationOnScreen;
      return true;
    }
    else if (prevLocation.equals(mouseLocationOnScreen)) {
      return true;
    }

    int dx = prevLocation.x - mouseLocationOnScreen.x;
    if (dx == 0) {
      return mouseLocationOnScreen.x > bounds.x && mouseLocationOnScreen.x < bounds.x + bounds.width;
    }

    // Check if the mouse goes out of the control.
    if (mouseLocationOnScreen.x < prevLocation.x && bounds.x > prevLocation.x) return false;
    if (mouseLocationOnScreen.y < prevLocation.y && bounds.y > prevLocation.y) return false;
    
    // Calculate line equation parameters - y = a * x + b
    float dy = prevLocation.y - mouseLocationOnScreen.y;
    float a = dy / dx;
    float b = mouseLocationOnScreen.y - a * mouseLocationOnScreen.x;

    // Check if crossing point with any tooltip border line is within bounds. Don't bother with floating point inaccuracy here.
    
    // Left border.
    float crossY = a * bounds.x + b;
    if (crossY >= bounds.y && crossY < bounds.y + bounds.height) return true;
    
    // Right border.
    crossY = a * (bounds.x + bounds.width) + b;
    if (crossY >= bounds.y && crossY < bounds.y + bounds.height) return true;
    
    // Top border.
    float crossX = (bounds.y - b) / a;
    if (crossX >= bounds.x && crossX < bounds.x + bounds.width) return true;
    
    // Bottom border
    crossX = (bounds.y + bounds.height - b) / a;
    return crossX >= bounds.x && crossX < bounds.x + bounds.width;
  }

  @Nullable
  private static Rectangle getHintBounds(@Nullable IdeTooltip tooltip) {
    if (tooltip == null) {
      return null;
    }
    JComponent component = tooltip.getTipComponent();
    if (component == null || !component.isShowing()) {
      return null;
    }

    return new Rectangle(component.getLocationOnScreen(), component.getBounds().getSize());
  }
  
  private static BrowseMode getBrowseMode(@JdkConstants.InputEventMask int modifiers) {
    if (modifiers != 0) {
      final Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
      if (matchMouseShortcut(activeKeymap, modifiers, IdeActions.ACTION_GOTO_DECLARATION)) return BrowseMode.Declaration;
      if (matchMouseShortcut(activeKeymap, modifiers, IdeActions.ACTION_GOTO_TYPE_DECLARATION)) return BrowseMode.TypeDeclaration;
      if (matchMouseShortcut(activeKeymap, modifiers, IdeActions.ACTION_GOTO_IMPLEMENTATION)) return BrowseMode.Implementation;
      if (modifiers == InputEvent.CTRL_MASK || modifiers == InputEvent.META_MASK) return BrowseMode.Declaration;
    }
    return BrowseMode.None;
  }

  private static boolean matchMouseShortcut(final Keymap activeKeymap, @JdkConstants.InputEventMask int modifiers, final String actionId) {
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

  @Nullable
  @TestOnly
  public static String getInfo(PsiElement element, PsiElement atPointer) {
    return generateInfo(element, atPointer).text;
  }

  @NotNull
  private static DocInfo generateInfo(PsiElement element, PsiElement atPointer) {
    final DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(element, atPointer);
    String result = doGenerateInfo(element, atPointer, documentationProvider);
    return result == null ? DocInfo.EMPTY : new DocInfo(result, documentationProvider, element);
  }

  @Nullable
  private static String doGenerateInfo(@NotNull PsiElement element,
                                       @NotNull PsiElement atPointer,
                                       @NotNull DocumentationProvider documentationProvider)
  {
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

    @NotNull
    public abstract DocInfo getInfo();

    public abstract boolean isValid(Document document);

    public abstract void showDocInfo(@NotNull DocumentationManager docManager);

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

    @NotNull
    public DocInfo getInfo() {
      AccessToken token = ReadAction.start();
      try {
        return generateInfo(myTargetElement, myElementAtPointer);
      }
      catch (IndexNotReadyException e) {
        showDumbModeNotification(myTargetElement.getProject());
        return null;
      }
      finally {
        token.finish();
      }
    }

    public boolean isValid(Document document) {
      if (!myTargetElement.isValid()) return false;
      if (!myElementAtPointer.isValid()) return false;
      if (myTargetElement == myElementAtPointer || myTargetElement == myElementAtPointer.getParent()) return false;

      return rangesAreCorrect(document);
    }

    @Override
    public void showDocInfo(@NotNull DocumentationManager docManager) {
      docManager.showJavaDocInfo(myTargetElement, myElementAtPointer, true, null);
      docManager.setAllowContentUpdateFromContext(false);
    }
  }

  private static class InfoMultiple extends Info {

    public InfoMultiple(@NotNull final PsiElement elementAtPointer) {
      super(elementAtPointer);
    }
    
    @NotNull
    public DocInfo getInfo() {
      return new DocInfo(CodeInsightBundle.message("multiple.implementations.tooltip"), null, null);
    }

    public boolean isValid(Document document) {
      return rangesAreCorrect(document);
    }

    @Override
    public void showDocInfo(@NotNull DocumentationManager docManager) {
      // Do nothing
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
      final List<PsiElement> resolvedElements = ref != null ? resolve(ref) : Collections.<PsiElement>emptyList();
      final PsiElement resolvedElement = resolvedElements.size() == 1 ? resolvedElements.get(0) : null;

      final PsiElement[] targetElements = GotoDeclarationAction.findTargetElementsNoVS(myProject, editor, offset, false);
      final PsiElement elementAtPointer = file.findElementAt(offset);

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
      else if (resolvedElements.size() > 1) {
        return elementAtPointer != null ? new InfoMultiple(elementAtPointer) : null;
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

  private static List<PsiElement> resolve(final PsiReference ref) {
    // IDEA-56727 try resolve first as in GotoDeclarationAction
    PsiElement resolvedElement = ref.resolve();

    if (resolvedElement == null && ref instanceof PsiPolyVariantReference) {
      List<PsiElement> result = new ArrayList<PsiElement>();
      final ResolveResult[] psiElements = ((PsiPolyVariantReference)ref).multiResolve(false);
      for (ResolveResult resolveResult : psiElements) {
        if (resolveResult.getElement() != null) {
          result.add(resolveResult.getElement());
        }
      }
      return result;
    }
    return resolvedElement == null ? Collections.<PsiElement>emptyList() : Collections.singletonList(resolvedElement);
  }

  private void disposeHighlighter() {
    if (myHighlighter != null) {
      myHighlighter.uninstall();
      HintManager.getInstance().hideAllHints();
      myHighlighter = null;
    }
  }

  private void fulfillDocInfo(@NotNull final String header,
                              @NotNull final DocumentationProvider provider,
                              @NotNull final PsiElement originalElement,
                              @NotNull final PsiElement anchorElement,
                              @NotNull final Consumer<String> newTextConsumer)
  {
    myDocAlarm.cancelAllRequests();
    myDocAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        final Ref<String> fullTextRef = new Ref<String>();
        final Ref<String> qualifiedNameRef = new Ref<String>();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            fullTextRef.set(provider.generateDoc(anchorElement, originalElement));
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
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            newTextConsumer.consume(updatedText); 
          }
        });
      }
    }, 0);
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

      if (EditorUtil.inVirtualSpace(myEditor, myPosition)) {
        return;
      }

      final int offset = myEditor.logicalPositionToOffset(myPosition);

      int selStart = myEditor.getSelectionModel().getSelectionStart();
      int selEnd = myEditor.getSelectionModel().getSelectionEnd();

      if (offset >= selStart && offset < selEnd) return;

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          ProgressIndicatorUtils.runWithWriteActionPriority(new Runnable() {
            public void run() {
              doExecute(file, offset);
            }
          });
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
      if (myDisposed || myEditor.isDisposed()) return;
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

      if (!info.isValid(myEditor.getDocument())) {
        return;
      }
      
      myHighlighter = installHighlighterSet(info, myEditor);

      DocInfo docInfo = info.getInfo();

      if (docInfo.text == null) return;

      if (myDocumentationManager.hasActiveDockedDocWindow()) {
        info.showDocInfo(myDocumentationManager);
      }
      
      HyperlinkListener hyperlinkListener = docInfo.docProvider == null
                                   ? null
                                   : new QuickDocHyperlinkListener(docInfo.docProvider, info.myElementAtPointer);
      final Ref<QuickDocInfoPane> quickDocPaneRef = new Ref<QuickDocInfoPane>();
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
      };
      Ref<Consumer<String>> newTextConsumerRef = new Ref<Consumer<String>>();
      JComponent label = HintUtil.createInformationLabel(docInfo.text, hyperlinkListener, mouseListener, newTextConsumerRef);
      Consumer<String> newTextConsumer = newTextConsumerRef.get();
      myDocAlarm.cancelAllRequests();
      if (newTextConsumer != null && docInfo.docProvider != null && docInfo.documentationAnchor != null) {
        fulfillDocInfo(docInfo.text, docInfo.docProvider, info.myElementAtPointer, docInfo.documentationAnchor, newTextConsumer);
      }
      QuickDocInfoPane quickDocPane = null;
      if (docInfo.documentationAnchor != null) {
        quickDocPane = new QuickDocInfoPane(docInfo.documentationAnchor, info.myElementAtPointer, label);
        quickDocPaneRef.set(quickDocPane);
      }
      
      JComponent hintContent = quickDocPane == null ? label : quickDocPane;
      final LightweightHint hint = new LightweightHint(hintContent);
      final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
      Point p = HintManagerImpl.getHintPosition(hint, myEditor, myPosition, HintManager.ABOVE);
      hintManager.showEditorHint(hint, myEditor, p,
                                 HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
                                 0, false, HintManagerImpl.createHintHint(myEditor, p,  hint, HintManager.ABOVE).setContentActive(false));
    }
  }
  
  private HighlightersSet installHighlighterSet(Info info, Editor editor) {
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
  
  private static class DocInfo {

    public static final DocInfo EMPTY = new DocInfo(null, null, null);

    @Nullable public final String                text;
    @Nullable public final DocumentationProvider docProvider;
    @Nullable public final PsiElement            documentationAnchor;

    DocInfo(@Nullable String text, @Nullable DocumentationProvider provider, @Nullable PsiElement documentationAnchor) {
      this.text = text;
      docProvider = provider;
      this.documentationAnchor = documentationAnchor;
    }
  }

  private class QuickDocInfoPane extends JLayeredPane implements DataProvider {

    @NotNull private final List<JComponent> myButtons = new ArrayList<JComponent>();
    @NotNull private final Pair<PsiElement, PsiElement> myElementUnderMouseInfo;
    @NotNull private final JComponent                   myBaseDocControl;

    QuickDocInfoPane(@NotNull PsiElement documentationAnchor, @NotNull PsiElement elementUnderMouse, @NotNull JComponent baseDocControl) {
      myElementUnderMouseInfo = Pair.create(documentationAnchor, elementUnderMouse);
      myBaseDocControl = baseDocControl;

      PresentationFactory presentationFactory = new PresentationFactory();
      for (AnAction action : ourTooltipActions) {
        Icon icon = action.getTemplatePresentation().getIcon();
        Dimension minSize = new Dimension(icon.getIconWidth(), icon.getIconHeight());
        myButtons.add(new ActionButton(action, presentationFactory.getPresentation(action), IdeTooltipManager.IDE_TOOLTIP_PLACE, minSize));
      }
      Collections.reverse(myButtons);

      setPreferredSize(baseDocControl.getPreferredSize());
      setMaximumSize(baseDocControl.getMaximumSize());
      setMinimumSize(baseDocControl.getMinimumSize());
      setBackground(baseDocControl.getBackground());

      add(baseDocControl, Integer.valueOf(0));
      for (JComponent button : myButtons) {
        button.setBorder(null);
        button.setBackground(baseDocControl.getBackground());
        add(button, Integer.valueOf(1));
        button.setVisible(false);
      }
    }

    @Override
    public Object getData(@NonNls String dataId) {
      return ELEMENT_UNDER_MOUSE_INFO_KEY.is(dataId) ? myElementUnderMouseInfo : null;
    }

    @Override
    public void doLayout() {
      Rectangle bounds = getBounds();
      myBaseDocControl.setBounds(bounds);

      final int buttonsHGap = 5;
      int x = bounds.width;
      for (JComponent button : myButtons) {
        Dimension buttonSize = button.getPreferredSize();
        x -= buttonSize.width;
        button.setBounds(x, 0, buttonSize.width, buttonSize.height);
        x -= buttonsHGap;
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
    @NotNull private final PsiElement            myContext;

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
      if (StringUtil.isEmpty(description) || !description.startsWith(DocumentationManager.PSI_ELEMENT_PROTOCOL)) {
        return;
      }

      String elementName = e.getDescription().substring(DocumentationManager.PSI_ELEMENT_PROTOCOL.length());

      final PsiElement targetElement = myProvider.getDocumentationElementForLink(PsiManager.getInstance(myProject), elementName, myContext);
      if (targetElement != null) {
        myTooltipManager.hideCurrentNow(false);
        myDocumentationManager.showJavaDocInfo(targetElement, myContext, true, null);
      }
    }
  }
}
