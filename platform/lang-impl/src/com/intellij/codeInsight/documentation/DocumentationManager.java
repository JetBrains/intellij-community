// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.BaseNavigateToSourceAction;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.ide.util.gotoByName.QuickSearchComponent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.preview.PreviewManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.*;

public class DocumentationManager extends DockablePopupManager<DocumentationComponent> {
  public static final String JAVADOC_LOCATION_AND_SIZE = "javadoc.popup";
  public static final String NEW_JAVADOC_LOCATION_AND_SIZE = "javadoc.popup.new";
  public static final DataKey<String> SELECTED_QUICK_DOC_TEXT = DataKey.create("QUICK_DOC.SELECTED_TEXT");

  private static final Logger LOG = Logger.getInstance(DocumentationManager.class);
  private static final String SHOW_DOCUMENTATION_IN_TOOL_WINDOW = "ShowDocumentationInToolWindow";
  private static final String DOCUMENTATION_AUTO_UPDATE_ENABLED = "DocumentationAutoUpdateEnabled";

  private static final long DOC_GENERATION_TIMEOUT_MILLISECONDS = 60000;
  private static final long DOC_GENERATION_PAUSE_MILLISECONDS = 100;

  private Editor myEditor;
  private final Alarm myUpdateDocAlarm;
  private WeakReference<JBPopup> myDocInfoHintRef;
  private WeakReference<Component> myFocusedBeforePopup;
  public static final Key<SmartPsiElementPointer> ORIGINAL_ELEMENT_KEY = Key.create("Original element");

  private final ActionManager myActionManager;

  private final TargetElementUtil myTargetElementUtil;

  private boolean myCloseOnSneeze;
  private String myPrecalculatedDocumentation;

  private ActionCallback myLastAction;
  private DocumentationComponent myTestDocumentationComponent;

  private AnAction myRestorePopupAction;

  @Override
  protected String getToolwindowId() {
    return ToolWindowId.DOCUMENTATION;
  }

  @Override
  protected DocumentationComponent createComponent() {
    return new DocumentationComponent(this);
  }

  @Override
  protected String getRestorePopupDescription() {
    return "Restore popup view mode";
  }

  @Override
  protected String getAutoUpdateDescription() {
    return "Refresh documentation on selection change automatically";
  }

  @Override
  protected String getAutoUpdateTitle() {
    return "Auto-update from Source";
  }

  @Override
  protected boolean getAutoUpdateDefault() {
    return true;
  }

  @NotNull
  @Override
  protected AnAction createRestorePopupAction() {
    myRestorePopupAction = super.createRestorePopupAction();
    return myRestorePopupAction;
  }

  @Override
  public void restorePopupBehavior() {
    super.restorePopupBehavior();
    Component previouslyFocused = SoftReference.dereference(myFocusedBeforePopup);
    if (previouslyFocused != null && previouslyFocused.isShowing()) {
      IdeFocusManager.getInstance(myProject).requestFocus(previouslyFocused, true);
      updateComponent(true);
    }
  }

  @Override
  public void createToolWindow(PsiElement element, PsiElement originalElement) {
    super.createToolWindow(element, originalElement);

    if (myToolWindow != null) {
      myToolWindow.getComponent().putClientProperty(ChooseByNameBase.TEMPORARILY_FOCUSABLE_COMPONENT_KEY, Boolean.TRUE);

      if (myRestorePopupAction != null) {
        ShortcutSet quickDocShortcut = ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC).getShortcutSet();
        myRestorePopupAction.registerCustomShortcutSet(quickDocShortcut, myToolWindow.getComponent());
        myRestorePopupAction = null;
      }
    }
  }

  /**
   * @return {@code true} if quick doc control is configured to not prevent user-IDE interaction (e.g. should be closed if
   * the user presses a key);
   * {@code false} otherwise
   */
  public boolean isCloseOnSneeze() {
    return myCloseOnSneeze;
  }

  @Override
  protected void installComponentActions(ToolWindow toolWindow, DocumentationComponent component) {
    ((ToolWindowEx)toolWindow).setTitleActions(component.getActions());
    DefaultActionGroup group = new DefaultActionGroup(createActions());
    group.add(component.getFontSizeAction());
    ((ToolWindowEx)toolWindow).setAdditionalGearActions(group);
    component.removeCornerMenu();
  }

  @Override
  protected void setToolwindowDefaultState() {
    Rectangle rectangle = WindowManager.getInstance().getIdeFrame(myProject).suggestChildFrameBounds();
    myToolWindow.setDefaultState(ToolWindowAnchor.RIGHT, ToolWindowType.DOCKED, new Rectangle(rectangle.width / 4, rectangle.height));
    myToolWindow.setType(ToolWindowType.DOCKED, null);
    myToolWindow.setSplitMode(true, null);
    myToolWindow.setAutoHide(false);
  }

  public static DocumentationManager getInstance(Project project) {
    return ServiceManager.getService(project, DocumentationManager.class);
  }

  public DocumentationManager(Project project, ActionManager manager, TargetElementUtil targetElementUtil) {
    super(project);
    myActionManager = manager;
    AnActionListener actionListener = new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
        JBPopup hint = getDocInfoHint();
        if (hint != null) {
          if (LookupManager.getActiveLookup(myEditor) != null) return; // let the lookup manage all the actions
          if (action instanceof HintManagerImpl.ActionToIgnore) return;
          if (action instanceof ScrollingUtil.ScrollingAction) return;
          if (action == myActionManager.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)) return;
          if (action == myActionManager.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)) return;
          if (action == myActionManager.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN)) return;
          if (action == myActionManager.getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP)) return;
          if (action == ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_ESCAPE)) return;
          if (ActionPlaces.JAVADOC_INPLACE_SETTINGS.equals(event.getPlace())) return;
          if (ActionPlaces.JAVADOC_TOOLBAR.equals(event.getPlace())) return;
          if (action instanceof BaseNavigateToSourceAction) return;
          closeDocHint();
        }
      }

      @Override
      public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
        JBPopup hint = getDocInfoHint();
        if (hint != null && LookupManager.getActiveLookup(myEditor) == null) {
          hint.cancel();
        }
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect(project).subscribe(AnActionListener.TOPIC, actionListener);
    myUpdateDocAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myProject);
    myTargetElementUtil = targetElementUtil;
  }

  private void closeDocHint() {
    JBPopup hint = getDocInfoHint();
    if (hint == null) {
      return;
    }
    myCloseOnSneeze = false;
    hint.cancel();
    Component toFocus = SoftReference.dereference(myFocusedBeforePopup);
    hint.cancel();
    if (toFocus != null) {
      IdeFocusManager.getInstance(myProject).requestFocus(toFocus, true);
    }
  }

  public void setAllowContentUpdateFromContext(boolean allow) {
    if (hasActiveDockedDocWindow()) {
      restartAutoUpdate(allow);
    }
  }

  public void updateToolwindowContext() {
    if (hasActiveDockedDocWindow()) {
      updateComponent();
    }
  }

  public void showJavaDocInfoAtToolWindow(@NotNull PsiElement element, @NotNull PsiElement original) {
    Content content = recreateToolWindow(element, original);
    if (content == null) return;
    DocumentationComponent component = (DocumentationComponent)content.getComponent();
    myUpdateDocAlarm.cancelAllRequests();
    doFetchDocInfo(component, new MyCollector(myProject, element, original, null))
      .doWhenDone(() -> component.clearHistory());
  }

  public void showJavaDocInfo(@NotNull PsiElement element, PsiElement original) {
    showJavaDocInfo(element, original, null);
  }

  /**
   * Asks to show quick doc for the target element.
   *
   * @param editor        editor with an element for which quick do should be shown
   * @param element       target element which documentation should be shown
   * @param original      element that was used as a quick doc anchor. Example: consider a code like {@code Runnable task;}.
   *                      A user wants to see javadoc for the {@code Runnable}, so, original element is a class name from the variable
   *                      declaration but {@code 'element'} argument is a {@code Runnable} descriptor
   * @param closeCallback callback to be notified on target hint close (if any)
   * @param documentation precalculated documentation
   * @param closeOnSneeze flag that defines whether quick doc control should be as non-obtrusive as possible. E.g. there are at least
   *                      two possible situations - the quick doc is shown automatically on mouse over element; the quick doc is shown
   *                      on explicit action call (Ctrl+Q). We want to close the doc on, say, editor viewport position change
   *                      at the first situation but don't want to do that at the second
   */
  public void showJavaDocInfo(@NotNull Editor editor,
                              @NotNull PsiElement element,
                              @NotNull PsiElement original,
                              @Nullable Runnable closeCallback,
                              @Nullable String documentation,
                              boolean closeOnSneeze) {
    myEditor = editor;
    myCloseOnSneeze = closeOnSneeze;
    showJavaDocInfo(element, original, false, closeCallback, documentation);
  }

  public void showJavaDocInfo(@NotNull PsiElement element,
                              PsiElement original,
                              @Nullable Runnable closeCallback) {
    showJavaDocInfo(element, original, false, closeCallback);
  }

  public void showJavaDocInfo(@NotNull PsiElement element,
                              PsiElement original,
                              boolean requestFocus,
                              @Nullable Runnable closeCallback) {
    showJavaDocInfo(element, original, requestFocus, closeCallback, null);
  }

  public void showJavaDocInfo(@NotNull PsiElement element,
                              PsiElement original,
                              boolean requestFocus,
                              @Nullable Runnable closeCallback,
                              @Nullable String documentation) {
    if (!element.isValid()) {
      return;
    }

    PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(element.getProject()) {
      @Override
      public void updatePopup(Object lookupItemObject) {
        if (lookupItemObject instanceof PsiElement) {
          doShowJavaDocInfo((PsiElement)lookupItemObject, requestFocus, this, original, null, null);
        }
      }
    };

    doShowJavaDocInfo(element, requestFocus, updateProcessor, original, closeCallback, documentation);
  }

  public void showJavaDocInfo(Editor editor, @Nullable PsiFile file, boolean requestFocus) {
    showJavaDocInfo(editor, file, requestFocus, null);
  }

  public void showJavaDocInfo(Editor editor,
                              @Nullable PsiFile file,
                              boolean requestFocus,
                              @Nullable Runnable closeCallback) {
    myEditor = editor;
    Project project = getProject(file);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement list = ParameterInfoController.findArgumentList(file, editor.getCaretModel().getOffset(), -1);
    PsiElement expressionList = null;
    if (list != null) {
      LookupEx lookup = LookupManager.getInstance(myProject).getActiveLookup();
      if (lookup != null) {
        expressionList = null; // take completion variants for documentation then
      }
      else {
        expressionList = list;
      }
    }

    PsiElement originalElement = getContextElement(editor, file);
    PsiElement element = assertSameProject(findTargetElement(editor, file));

    if (element == null && expressionList != null) {
      element = expressionList;
    }

    if (element == null && file == null) return; //file == null for text field editor

    if (element == null) { // look if we are within a javadoc comment
      element = assertSameProject(originalElement);
      if (element == null) return;

      PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class);
      if (comment == null) return;

      element = comment instanceof PsiDocCommentBase ? ((PsiDocCommentBase)comment).getOwner() : comment.getParent();
      if (element == null) return;
      //if (!(element instanceof PsiDocCommentOwner)) return null;
    }

    PsiElement finalElement = element;
    PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(project) {
      @Override
      public void updatePopup(Object lookupIteObject) {
        if (lookupIteObject == null) {
          doShowJavaDocInfo(finalElement, false, this, originalElement, closeCallback, CodeInsightBundle.message("no.documentation.found"));
          return;
        }
        if (lookupIteObject instanceof PsiElement) {
          doShowJavaDocInfo((PsiElement)lookupIteObject, false, this, originalElement, closeCallback, null);
          return;
        }

        DocumentationProvider documentationProvider = getProviderFromElement(file);

        PsiElement element = documentationProvider.getDocumentationElementForLookupItem(
          PsiManager.getInstance(myProject),
          lookupIteObject,
          originalElement
        );

        if (element == null) {
          doShowJavaDocInfo(finalElement, false, this, originalElement, closeCallback, CodeInsightBundle.message("no.documentation.found"));
          return;
        }

        if (myEditor != null) {
          PsiFile file = element.getContainingFile();
          if (file != null) {
            Editor editor = myEditor;
            showJavaDocInfo(myEditor, file, false);
            myEditor = editor;
          }
        }
        else {
          doShowJavaDocInfo(element, false, this, originalElement, closeCallback, null);
        }
      }
    };

    doShowJavaDocInfo(element, requestFocus, updateProcessor, originalElement, closeCallback, null);
  }

  public PsiElement findTargetElement(Editor editor, PsiFile file) {
    return findTargetElement(editor, file, getContextElement(editor, file));
  }

  private static PsiElement getContextElement(Editor editor, PsiFile file) {
    return file != null ? file.findElementAt(editor.getCaretModel().getOffset()) : null;
  }

  private void doShowJavaDocInfo(@NotNull PsiElement element,
                                 boolean requestFocus,
                                 PopupUpdateProcessor updateProcessor,
                                 PsiElement originalElement,
                                 @Nullable Runnable closeCallback,
                                 @Nullable String documentation) {
    Project project = getProject(element);
    if (!project.isOpen()) return;

    storeOriginalElement(project, originalElement, element);

    JBPopup prevHint = getDocInfoHint();
    if (PreviewManager.SERVICE.preview(myProject, DocumentationPreviewPanelProvider.ID, Couple.of(element, originalElement), requestFocus) != null) {
      return;
    }

    myPrecalculatedDocumentation = documentation;
    if (myToolWindow == null && PropertiesComponent.getInstance().isTrueValue(SHOW_DOCUMENTATION_IN_TOOL_WINDOW)) {
      createToolWindow(element, originalElement);
    }
    else if (myToolWindow != null) {
      Content content = myToolWindow.getContentManager().getSelectedContent();
      if (content != null) {
        DocumentationComponent component = (DocumentationComponent)content.getComponent();
        boolean sameElement = element.getManager().areElementsEquivalent(component.getElement(), element);
        if (sameElement) {
          JComponent preferredFocusableComponent = content.getPreferredFocusableComponent();
          // focus toolwindow on the second actionPerformed
          boolean focus = requestFocus || CommandProcessor.getInstance().getCurrentCommand() != null;
          if (preferredFocusableComponent != null && focus) {
            IdeFocusManager.getInstance(myProject).requestFocus(preferredFocusableComponent, true);
          }
        }
        if (!sameElement || !component.isUpToDate()) {
          cancelAndFetchDocInfo(component, new MyCollector(myProject, element, originalElement, null))
            .doWhenDone(() -> component.clearHistory());
        }
      }

      if (!myToolWindow.isVisible()) {
        myToolWindow.show(null);
      }
    }
    else if (prevHint != null && prevHint.isVisible() && prevHint instanceof AbstractPopup) {
      DocumentationComponent component = (DocumentationComponent)((AbstractPopup)prevHint).getComponent();
      ActionCallback result = cancelAndFetchDocInfo(component, new MyCollector(myProject, element, originalElement, null));
      if (requestFocus) {
        result.doWhenDone(() -> {
          JBPopup hint = getDocInfoHint();
          if (hint != null) ((AbstractPopup)hint).focusPreferredComponent();
        });
      }
    }
    else {
      showInPopup(element, requestFocus, updateProcessor, originalElement, closeCallback);
    }
  }

  private void showInPopup(@NotNull PsiElement element,
                           boolean requestFocus,
                           PopupUpdateProcessor updateProcessor,
                           PsiElement originalElement,
                           @Nullable Runnable closeCallback) {
    Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
    myFocusedBeforePopup = new WeakReference<>(focusedComponent);

    DocumentationComponent component = myTestDocumentationComponent == null ? new DocumentationComponent(this) :
                                       myTestDocumentationComponent;
    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        createToolWindow(element, originalElement);
        JBPopup hint = getDocInfoHint();
        if (hint != null && hint.isVisible()) hint.cancel();
      }
    };
    List<Pair<ActionListener, KeyStroke>> actions = ContainerUtil.newSmartList();
    AnAction quickDocAction = ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC);
    for (Shortcut shortcut : quickDocAction.getShortcutSet().getShortcuts()) {
      if (!(shortcut instanceof KeyboardShortcut)) continue;
      actions.add(Pair.create(actionListener, ((KeyboardShortcut)shortcut).getFirstKeyStroke()));
    }

    boolean hasLookup = LookupManager.getActiveLookup(myEditor) != null;
    AbstractPopup hint = (AbstractPopup)JBPopupFactory
      .getInstance().createComponentPopupBuilder(component, component)
      .setProject(element.getProject())
      .addListener(updateProcessor)
      .addUserData(updateProcessor)
      .setKeyboardActions(actions)
      .setResizable(true)
      .setMovable(true)
      .setFocusable(true)
      .setRequestFocus(requestFocus)
      .setCancelOnClickOutside(!hasLookup) // otherwise selecting lookup items by mouse would close the doc
      .setModalContext(false)
      .setCancelCallback(() -> {
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
          return false;
        }
        myCloseOnSneeze = false;

        if (closeCallback != null) {
          closeCallback.run();
        }
        findQuickSearchComponent().ifPresent(QuickSearchComponent::unregisterHint);

        Disposer.dispose(component);
        myEditor = null;
        return Boolean.TRUE;
      })
      .setKeyEventHandler(e -> {
        if (myCloseOnSneeze) {
          closeDocHint();
        }
        if (AbstractPopup.isCloseRequest(e) && getDocInfoHint() != null) {
          closeDocHint();
          return true;
        }
        return false;
      })
      .createPopup();

    component.setHint(hint);
    component.setToolwindowCallback(() -> {
      createToolWindow(element, originalElement);
      myToolWindow.setAutoHide(false);
      hint.cancel();
    });

    if (DimensionService.getInstance().getSize(NEW_JAVADOC_LOCATION_AND_SIZE, myProject) != null) {
      hint.setDimensionServiceKey(NEW_JAVADOC_LOCATION_AND_SIZE);
    }

    if (myEditor == null) {
      // subsequent invocation of javadoc popup from completion will have myEditor == null because of cancel invoked,
      // so reevaluate the editor for proper popup placement
      Lookup lookup = LookupManager.getInstance(myProject).getActiveLookup();
      myEditor = lookup != null ? lookup.getEditor() : null;
    }
    cancelAndFetchDocInfo(component, new MyCollector(myProject, element, originalElement, null));

    myDocInfoHintRef = new WeakReference<>(hint);

    findQuickSearchComponent().ifPresent(quickSearch -> quickSearch.registerHint(hint));

    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getSource() == hint.getPopupWindow()) {
        myCloseOnSneeze = false;
      }
      return false;
    }, component);
  }

  static String getTitle(@NotNull PsiElement element, boolean isShort) {
    String title = SymbolPresentationUtil.getSymbolPresentableText(element);
    return isShort ? title != null ? title : element.getText()
                   : CodeInsightBundle.message("javadoc.info.title", title != null ? title : element.getText());
  }

  public static void storeOriginalElement(Project project, PsiElement originalElement, PsiElement element) {
    if (element == null) return;
    try {
      element.putUserData(
        ORIGINAL_ELEMENT_KEY,
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(originalElement)
      );
    }
    catch (RuntimeException ex) {
      // PsiPackage does not allow putUserData
    }
  }

  @Nullable
  public PsiElement findTargetElement(@NotNull Editor editor, @Nullable PsiFile file, PsiElement contextElement) {
    return findTargetElement(editor, editor.getCaretModel().getOffset(), file, contextElement);
  }

  @Nullable
  public PsiElement findTargetElement(Editor editor, int offset, @Nullable PsiFile file, PsiElement contextElement) {
    try {
      return findTargetElementUnsafe(editor, offset, file, contextElement);
    }
    catch (IndexNotReadyException ex) {
      LOG.warn("Index not ready");
      LOG.debug(ex);
      return null;
    }
  }

  /**
   * in case index is not ready will throw IndexNotReadyException
   */
  @Nullable
  private PsiElement findTargetElementUnsafe(Editor editor, int offset, @Nullable PsiFile file, PsiElement contextElement) {
    if (LookupManager.getInstance(myProject).getActiveLookup() != null) {
      return assertSameProject(getElementFromLookup(editor, file));
    }

    TargetElementUtil util = TargetElementUtil.getInstance();
    PsiElement element = null;
    if (file != null) {
      DocumentationProvider documentationProvider = getProviderFromElement(file);
      if (documentationProvider instanceof DocumentationProviderEx) {
        element = assertSameProject(((DocumentationProviderEx)documentationProvider).getCustomDocumentationElement(editor, file, contextElement));
      }
    }

    if (element == null) {
      element = assertSameProject(util.findTargetElement(editor, myTargetElementUtil.getAllAccepted(), offset));

      // Allow context doc over xml tag content
      if (element != null || contextElement != null) {
        PsiElement adjusted = assertSameProject(util.adjustElement(editor, myTargetElementUtil.getAllAccepted(), element, contextElement));
        if (adjusted != null) {
          element = adjusted;
        }
      }
    }

    if (element == null) {
      PsiReference ref = TargetElementUtil.findReference(editor, offset);
      if (ref != null) {
        element = assertSameProject(util.adjustReference(ref));
        if (ref instanceof PsiPolyVariantReference) {
          element = assertSameProject(ref.getElement());
        }
      }
    }

    storeOriginalElement(myProject, contextElement, element);

    return element;
  }

  @Nullable
  public PsiElement getElementFromLookup(Editor editor, @Nullable PsiFile file) {
    Lookup activeLookup = LookupManager.getInstance(myProject).getActiveLookup();

    if (activeLookup != null) {
      LookupElement item = activeLookup.getCurrentItem();
      if (item != null) {
        int offset = editor.getCaretModel().getOffset();
        if (offset > 0 && offset == editor.getDocument().getTextLength()) offset--;
        PsiReference ref = TargetElementUtil.findReference(editor, offset);
        PsiElement contextElement = file == null ? null : file.findElementAt(offset);
        PsiElement targetElement = ref != null ? ref.getElement() : contextElement;
        if (targetElement != null) {
          PsiUtilCore.ensureValid(targetElement);
        }

        DocumentationProvider documentationProvider = getProviderFromElement(file);
        PsiManager psiManager = PsiManager.getInstance(myProject);
        PsiElement fromProvider = targetElement == null ? null :
                                  documentationProvider.getDocumentationElementForLookupItem(psiManager, item.getObject(), targetElement);
        return fromProvider != null ? fromProvider : CompletionUtil.getTargetElement(item);
      }
    }
    return null;
  }

  public String generateDocumentation(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    return new MyCollector(myProject, element, originalElement, null).getDocumentation();
  }

  @Nullable
  public JBPopup getDocInfoHint() {
    if (myDocInfoHintRef == null) return null;
    JBPopup hint = myDocInfoHintRef.get();
    if (hint == null || !hint.isVisible() && !ApplicationManager.getApplication().isUnitTestMode()) {
      if (hint != null) {
        // hint's window might've been hidden by AWT without notifying us
        // dispose to remove the popup from IDE hierarchy and avoid leaking components
        hint.cancel();
      }
      myDocInfoHintRef = null;
      return null;
    }
    return hint;
  }

  public void fetchDocInfo(@NotNull PsiElement element, @NotNull DocumentationComponent component) {
    cancelAndFetchDocInfo(component, new MyCollector(myProject, element, null, null));
  }

  public ActionCallback queueFetchDocInfo(@NotNull PsiElement element, @NotNull DocumentationComponent component) {
    return doFetchDocInfo(component, new MyCollector(myProject, element, null, null));
  }

  private ActionCallback cancelAndFetchDocInfo(@NotNull DocumentationComponent component, @NotNull DocumentationCollector provider) {
    updateToolWindowTabName(provider.element);
    myUpdateDocAlarm.cancelAllRequests();
    return doFetchDocInfo(component, provider);
  }

  void updateToolWindowTabName(@NotNull PsiElement element) {
    if (myToolWindow != null) {
      Content content = myToolWindow.getContentManager().getSelectedContent();
      if (content != null) content.setDisplayName(getTitle(element, true));
    }
  }

  private ActionCallback doFetchDocInfo(@NotNull DocumentationComponent component,
                                        @NotNull DocumentationCollector collector) {
    ActionCallback callback = new ActionCallback();
    myLastAction = callback;
    if (myPrecalculatedDocumentation != null) {
      LOG.debug("Setting precalculated documentation");
      PsiElement element = collector.element;
      PsiElement originalElement = collector instanceof MyCollector ? ((MyCollector)collector).originalElement : element;
      DocumentationProvider provider = ReadAction.compute(() -> getProviderFromElement(element, originalElement));
      component.setData(element, myPrecalculatedDocumentation,
                        collector.effectiveUrl, collector.ref, provider);
      callback.setDone();
      myPrecalculatedDocumentation = null;
      return callback;
    }
    boolean wasEmpty = component.isEmpty();
    component.startWait();
    if (wasEmpty) {
      component.setText(CodeInsightBundle.message("javadoc.fetching.progress"), collector.element, collector.provider);
      AbstractPopup jbPopup = (AbstractPopup)getDocInfoHint();
      if (jbPopup != null) {
        jbPopup.setDimensionServiceKey(null);
      }
    }

    ModalityState modality = ModalityState.defaultModalityState();

    myUpdateDocAlarm.addRequest(() -> {
      if (myProject.isDisposed()) return;
      LOG.debug("Started fetching documentation...");

      PsiElement element = ReadAction.compute(() -> collector.element.isValid() ? collector.element : null);
      if (element == null) {
        LOG.debug("Element for which documentation was requested is not available anymore");
        return;
      }

      Throwable fail = null;
      String text = null;
      try {
        text = collector.getDocumentation();
      }
      catch (Throwable e) {
        LOG.info(e);
        fail = e;
      }

      if (fail != null) {
        Throwable finalFail = fail;
        GuiUtils.invokeLaterIfNeeded(() -> {
          String message = finalFail instanceof IndexNotReadyException
                           ? "Documentation is not available until indices are built."
                           : CodeInsightBundle.message("javadoc.external.fetch.error.message");
          component.setText(message, null, collector.provider);
          component.clearHistory();
          callback.setDone();
        }, ModalityState.any());
        return;
      }

      LOG.debug("Documentation fetched successfully:\n", text);

      String finalText = text;
      PsiDocumentManager.getInstance(myProject).performLaterWhenAllCommitted(() -> {
        if (!element.isValid()) {
          LOG.debug("Element for which documentation was requested is not valid");
          callback.setDone();
          return;
        }

        String currentText = component.getText();
        PsiElement currentElement = component.getElement();
        if (finalText == null) {
          component.setText(CodeInsightBundle.message("no.documentation.found"), element, collector.provider);
        }
        else if (finalText.isEmpty()) {
          component.setText(currentText, element, collector.provider);
        }
        else if (currentElement != null && currentElement.getManager().areElementsEquivalent(currentElement, element)) {
          Diff.Change change = null;
          try {
            change = Diff.buildChanges(currentText, finalText);
          }
          catch (FilesTooBigForDiffException ignore) { }
          if (change == null || change.line0 > 10) {
            component.replaceText(finalText, element);
          }
          else {
            component.setData(element, finalText, collector.effectiveUrl, collector.ref, collector.provider);
          }
        }
        else {
          component.setData(element, finalText, collector.effectiveUrl, collector.ref, collector.provider);
        }
        if (wasEmpty) {
          component.clearHistory();
        }
        callback.setDone();
      }, modality);
    }, 10);
    return callback;
  }

  @NotNull
  public static DocumentationProvider getProviderFromElement(PsiElement element) {
    return getProviderFromElement(element, null);
  }

  @NotNull
  public static DocumentationProvider getProviderFromElement(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
    if (element != null && !element.isValid()) {
      element = null;
    }
    if (originalElement != null && !originalElement.isValid()) {
      originalElement = null;
    }

    if (originalElement == null) {
      originalElement = getOriginalElement(element);
    }

    PsiFile containingFile =
      originalElement != null ? originalElement.getContainingFile() : element != null ? element.getContainingFile() : null;
    Set<DocumentationProvider> result = new LinkedHashSet<>();

    Language containingFileLanguage = containingFile != null ? containingFile.getLanguage() : null;
    DocumentationProvider originalProvider =
      containingFile != null ? LanguageDocumentation.INSTANCE.forLanguage(containingFileLanguage) : null;

    Language elementLanguage = element != null ? element.getLanguage() : null;
    DocumentationProvider elementProvider =
      element == null || elementLanguage.is(containingFileLanguage) ? null : LanguageDocumentation.INSTANCE.forLanguage(elementLanguage);

    ContainerUtil.addIfNotNull(result, elementProvider);
    ContainerUtil.addIfNotNull(result, originalProvider);

    if (containingFile != null) {
      Language baseLanguage = containingFile.getViewProvider().getBaseLanguage();
      if (!baseLanguage.is(containingFileLanguage)) {
        ContainerUtil.addIfNotNull(result, LanguageDocumentation.INSTANCE.forLanguage(baseLanguage));
      }
    }
    else if (element instanceof PsiDirectory) {
      Set<Language> set = new HashSet<>();

      for (PsiFile file : ((PsiDirectory)element).getFiles()) {
        Language baseLanguage = file.getViewProvider().getBaseLanguage();
        if (!set.contains(baseLanguage)) {
          set.add(baseLanguage);
          ContainerUtil.addIfNotNull(result, LanguageDocumentation.INSTANCE.forLanguage(baseLanguage));
        }
      }
    }
    return CompositeDocumentationProvider.wrapProviders(result);
  }

  @Nullable
  public static PsiElement getOriginalElement(PsiElement element) {
    SmartPsiElementPointer originalElementPointer = element != null ? element.getUserData(ORIGINAL_ELEMENT_KEY) : null;
    return originalElementPointer != null ? originalElementPointer.getElement() : null;
  }

  void navigateByLink(DocumentationComponent component, String url) {
    component.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    PsiElement psiElement = component.getElement();
    if (psiElement == null) {
      return;
    }
    PsiManager manager = PsiManager.getInstance(getProject(psiElement));
    if (url.equals("external_doc")) {
      component.showExternalDoc();
      return;
    }
    if (url.startsWith("open")) {
      PsiFile containingFile = psiElement.getContainingFile();
      OrderEntry libraryEntry = null;
      if (containingFile != null) {
        VirtualFile virtualFile = containingFile.getVirtualFile();
        libraryEntry = LibraryUtil.findLibraryEntry(virtualFile, myProject);
      }
      else if (psiElement instanceof PsiDirectoryContainer) {
        PsiDirectory[] directories = ((PsiDirectoryContainer)psiElement).getDirectories();
        for (PsiDirectory directory : directories) {
          VirtualFile virtualFile = directory.getVirtualFile();
          libraryEntry = LibraryUtil.findLibraryEntry(virtualFile, myProject);
          if (libraryEntry != null) {
            break;
          }
        }
      }
      if (libraryEntry != null) {
        ProjectSettingsService.getInstance(myProject).openLibraryOrSdkSettings(libraryEntry);
      }
    }
    else if (url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
      String refText = url.substring(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL.length());
      int separatorPos = refText.lastIndexOf(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL_REF_SEPARATOR);
      String ref = null;
      if (separatorPos >= 0) {
        ref = refText.substring(separatorPos + DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL_REF_SEPARATOR.length());
        refText = refText.substring(0, separatorPos);
      }
      DocumentationProvider provider = getProviderFromElement(psiElement);
      PsiElement targetElement = provider.getDocumentationElementForLink(manager, refText, psiElement);
      if (targetElement == null) {
        for (DocumentationProvider documentationProvider : DocumentationProvider.EP_NAME.getExtensionList()) {
          targetElement = documentationProvider.getDocumentationElementForLink(manager, refText, psiElement);
          if (targetElement != null) {
            break;
          }
        }
      }
      if (targetElement == null) {
        for (Language language : Language.getRegisteredLanguages()) {
          DocumentationProvider documentationProvider = LanguageDocumentation.INSTANCE.forLanguage(language);
          if (documentationProvider != null) {
            targetElement = documentationProvider.getDocumentationElementForLink(manager, refText, psiElement);
            if (targetElement != null) {
              break;
            }
          }
        }
      }
      if (targetElement != null) {
        cancelAndFetchDocInfo(component, new MyCollector(myProject, targetElement, null, ref));
      }
    }
    else {
      DocumentationProvider provider = getProviderFromElement(psiElement);
      boolean processed = false;
      if (provider instanceof CompositeDocumentationProvider) {
        for (DocumentationProvider p : ((CompositeDocumentationProvider)provider).getAllProviders()) {
          if (!(p instanceof ExternalDocumentationHandler)) continue;

          ExternalDocumentationHandler externalHandler = (ExternalDocumentationHandler)p;
          if (externalHandler.canFetchDocumentationLink(url)) {
            String ref = externalHandler.extractRefFromLink(url);
            cancelAndFetchDocInfo(component, new DocumentationCollector(psiElement, url, ref, p) {
              @Override
              public String getDocumentation() {
                return externalHandler.fetchExternalDocumentation(url, psiElement);
              }
            });
            processed = true;
          }
          else if (externalHandler.handleExternalLink(manager, url, psiElement)) {
            processed = true;
            break;
          }
        }
      }

      if (!processed) {
        cancelAndFetchDocInfo(component, new DocumentationCollector(psiElement, url, null, provider) {
          @Override
          public String getDocumentation() {
            if (BrowserUtil.isAbsoluteURL(url)) {
              BrowserUtil.browse(url);
              return "";
            }
            else {
              return CodeInsightBundle.message("javadoc.error.resolving.url", url);
            }
          }
        });
      }
    }

    component.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }

  public Project getProject(@Nullable PsiElement element) {
    assertSameProject(element);
    return myProject;
  }

  private PsiElement assertSameProject(@Nullable PsiElement element) {
    if (element != null && element.isValid() && myProject != element.getProject()) {
      throw new AssertionError(myProject + "!=" + element.getProject() + "; element=" + element);
    }
    return element;
  }

  public static void createHyperlink(StringBuilder buffer, String refText, String label, boolean plainLink) {
    DocumentationManagerUtil.createHyperlink(buffer, refText, label, plainLink);
  }

  @Override
  public String getShowInToolWindowProperty() {
    return SHOW_DOCUMENTATION_IN_TOOL_WINDOW;
  }

  @Override
  public String getAutoUpdateEnabledProperty() {
    return DOCUMENTATION_AUTO_UPDATE_ENABLED;
  }

  @Override
  protected void doUpdateComponent(PsiElement element, PsiElement originalElement, DocumentationComponent component) {
    cancelAndFetchDocInfo(component, new MyCollector(myProject, element, originalElement, null));
  }

  @Override
  protected void doUpdateComponent(Editor editor, PsiFile psiFile, boolean requestFocus) {
    showJavaDocInfo(editor, psiFile, requestFocus, null);
  }

  @Override
  protected void doUpdateComponent(Editor editor, PsiFile psiFile) {
    doUpdateComponent(editor, psiFile, false);
  }

  @Override
  protected void doUpdateComponent(@NotNull PsiElement element) {
    showJavaDocInfo(element, element, null);
  }

  @Override
  protected String getTitle(PsiElement element) {
    return getTitle(element, true);
  }

  @Nullable
  public Image getElementImage(@NotNull PsiElement element, @NotNull String imageSpec) {
    DocumentationProvider provider = getProviderFromElement(element);
    if (provider instanceof CompositeDocumentationProvider) {
      for (DocumentationProvider p : ((CompositeDocumentationProvider)provider).getAllProviders()) {
        if (p instanceof DocumentationProviderEx) {
          Image image = ((DocumentationProviderEx)p).getLocalImageForElement(element, imageSpec);
          if (image != null) return image;
        }
      }
    }
    return null;
  }

  Editor getEditor() {
    return myEditor;
  }

  @TestOnly
  public ActionCallback getLastAction() {
    return myLastAction;
  }

  @TestOnly
  public void setDocumentationComponent(DocumentationComponent documentationComponent) {
    myTestDocumentationComponent = documentationComponent;
  }

  private abstract static class DocumentationCollector {
    final PsiElement element;
    final String ref;

    volatile DocumentationProvider provider;
    String effectiveUrl;

    DocumentationCollector(PsiElement element, String effectiveUrl, String ref, DocumentationProvider provider) {
      this.element = element;
      this.ref = ref;
      this.effectiveUrl = effectiveUrl;
      this.provider = provider;
    }

    @Nullable
    abstract String getDocumentation() throws Exception;
  }

  private static class MyCollector extends DocumentationCollector {

    final Project project;
    final PsiElement originalElement;

    MyCollector(@NotNull Project project, @NotNull PsiElement element, PsiElement originalElement, String ref) {
      super(element, null, ref, null);
      this.project = project;
      this.originalElement = originalElement;
    }

    @Override
    @Nullable
    public String getDocumentation() {
      provider = ReadAction.compute(() -> getProviderFromElement(element, originalElement));
      LOG.debug("Using provider ", provider);

      if (provider instanceof ExternalDocumentationProvider) {
        List<String> urls = ReadAction.compute(
          () -> {
            SmartPsiElementPointer originalElementPtr = element.getUserData(ORIGINAL_ELEMENT_KEY);
            PsiElement originalElement = originalElementPtr != null ? originalElementPtr.getElement() : null;
            return provider.getUrlFor(element, originalElement);
          }
        );
        LOG.debug("External documentation URLs: ", urls);
        if (urls != null) {
          for (String url : urls) {
            String doc = ((ExternalDocumentationProvider)provider).fetchExternalDocumentation(
              project, element, Collections.singletonList(url));
            if (doc != null) {
              LOG.debug("Fetched documentation from ", url);
              effectiveUrl = url;
              return doc;
            }
          }
        }
      }

      Ref<String> result = new Ref<>();
      QuickDocUtil.runInReadActionWithWriteActionPriorityWithRetries(() -> {
        if (!element.isValid()) return;
        SmartPsiElementPointer originalPointer = element.getUserData(ORIGINAL_ELEMENT_KEY);
        PsiElement originalPsi = originalPointer != null ? originalPointer.getElement() : null;
        String doc = provider.generateDoc(element, originalPsi);
        if (element instanceof PsiFile) {
          String fileDoc = generateFileDoc((PsiFile)element, doc == null);
          if (fileDoc != null) {
            doc = doc == null ? fileDoc : doc + fileDoc;
          }
        }
        result.set(doc);
      }, DOC_GENERATION_TIMEOUT_MILLISECONDS, DOC_GENERATION_PAUSE_MILLISECONDS, null);
      return result.get();
    }
  }

  @Nullable
  private static String generateFileDoc(@NotNull PsiFile psiFile, boolean withUrl) {
    VirtualFile file = PsiUtilCore.getVirtualFile(psiFile);
    File ioFile = file == null || !file.isInLocalFileSystem() ? null : VfsUtilCore.virtualToIoFile(file);
    BasicFileAttributes attr = null;
    try {
      attr = ioFile == null ? null : Files.readAttributes(Paths.get(ioFile.toURI()), BasicFileAttributes.class);
    }
    catch (Exception ignored) { }
    if (attr == null) return null;
    FileType type = file.getFileType();
    String typeName = type == UnknownFileType.INSTANCE ? "Unknown" :
                      type == PlainTextFileType.INSTANCE ? "Text" :
                      type == ArchiveFileType.INSTANCE ? "Archive" :
                      type.getName();
    String languageName = type.isBinary() ? "" : psiFile.getLanguage().getDisplayName();
    return (withUrl ? DocumentationMarkup.DEFINITION_START +
                      file.getPresentableUrl() +
                      DocumentationMarkup.DEFINITION_END +
                      DocumentationMarkup.CONTENT_START : "") +
           "<p><span class='grayed'>Size:</span> " + StringUtil.formatFileSize(attr.size()) +
           "<p><span class='grayed'>Type:</span> " + typeName + (type.isBinary() || typeName.equals(languageName) ? "" : " (" + languageName + ")") +
           "<p><span class='grayed'>Modified:</span> " + DateFormatUtil.formatDateTime(attr.lastModifiedTime().toMillis()) +
           "<p><span class='grayed'>Created:</span> " + DateFormatUtil.formatDateTime(attr.creationTime().toMillis()) + (withUrl ? DocumentationMarkup.CONTENT_END : "");
  }

  private Optional<QuickSearchComponent> findQuickSearchComponent() {
    Component c = SoftReference.dereference(myFocusedBeforePopup);
    while (c != null) {
      if (c instanceof QuickSearchComponent) {
        return Optional.of((QuickSearchComponent)c);
      }
      c = c.getParent();
    }
    return Optional.empty();
  }
}
