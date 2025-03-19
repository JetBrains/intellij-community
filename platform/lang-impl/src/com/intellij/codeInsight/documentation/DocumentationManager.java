// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.BaseNavigateToSourceAction;
import com.intellij.ide.actions.WindowAction;
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSetBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.ui.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.ui.tabs.FileColorManagerImpl;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.intellij.lang.documentation.DocumentationMarkup.*;

/**
 * Replaced by {@link com.intellij.lang.documentation.ide.impl.DocumentationManager}
 *
 * @deprecated Unused in v2 implementation. Unsupported: use at own risk.
 */
@SuppressWarnings("removal")
@Deprecated(forRemoval = true)
public class DocumentationManager extends DockablePopupManager<DocumentationComponent> {
  public static final String JAVADOC_LOCATION_AND_SIZE = "javadoc.popup";
  public static final String NEW_JAVADOC_LOCATION_AND_SIZE = "javadoc.popup.new";
  public static final DataKey<String> SELECTED_QUICK_DOC_TEXT = DataKey.create("QUICK_DOC.SELECTED_TEXT");

  static final Logger LOG = Logger.getInstance(DocumentationManager.class);
  private static final String SHOW_DOCUMENTATION_IN_TOOL_WINDOW = "ShowDocumentationInToolWindow";
  private static final String DOCUMENTATION_AUTO_UPDATE_ENABLED = "DocumentationAutoUpdateEnabled";

  private static final Class<?>[] ACTION_CLASSES_TO_IGNORE = {
    HintManagerImpl.ActionToIgnore.class,
    ScrollingUtil.ScrollingAction.class,
    SwingActionDelegate.class,
    BaseNavigateToSourceAction.class,
    WindowAction.class
  };
  private static final String[] ACTION_IDS_TO_IGNORE = {
    IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN,
    IdeActions.ACTION_EDITOR_MOVE_CARET_UP,
    IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN,
    IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP,
    IdeActions.ACTION_EDITOR_ESCAPE
  };
  private static final String[] ACTION_PLACES_TO_IGNORE = {
    ActionPlaces.JAVADOC_INPLACE_SETTINGS,
    ActionPlaces.JAVADOC_TOOLBAR
  };

  private Editor myEditor;
  private final Alarm myUpdateDocAlarm;
  private WeakReference<JBPopup> myDocInfoHintRef;
  private WeakReference<Component> myFocusedBeforePopup;
  public static final Key<SmartPsiElementPointer<?>> ORIGINAL_ELEMENT_KEY = Key.create("Original element");
  public static final Key<Boolean> IS_FROM_LOOKUP = Key.create("IS FROM LOOKUP");

  private boolean myCloseOnSneeze;
  private @Nls String myPrecalculatedDocumentation;

  private ActionCallback myLastAction;
  private DocumentationComponent myTestDocumentationComponent;

  private AnAction myRestorePopupAction;

  private ToolWindow myDefaultDocToolWindow;
  private final Map<String, ToolWindow> myLangToolWindows = new HashMap<>();

  @Override
  protected String getToolwindowId() {
    return ToolWindowId.DOCUMENTATION;
  }

  @Override
  protected String getToolwindowTitle() {
    return CodeInsightBundle.message("documentation.tool.window.title");
  }

  @Override
  protected DocumentationComponent createComponent() {
    return new DocumentationComponent(this);
  }

  @Override
  public String getRestorePopupDescription() {
    return CodeInsightBundle.message("action.description.restore.popup.view.mode");
  }

  @Override
  public String getAutoUpdateDescription() {
    return CodeInsightBundle.message("action.description.refresh.documentation.on.selection.change.automatically");
  }

  @Override
  public String getAutoUpdateTitle() {
    return CodeInsightBundle.message("popup.title.auto.update.from.source");
  }

  @Override
  protected boolean getAutoUpdateDefault() {
    return true;
  }

  @Override
  protected @NotNull AnAction createRestorePopupAction() {
    myRestorePopupAction = super.createRestorePopupAction();
    return myRestorePopupAction;
  }

  @Override
  public void restorePopupBehavior() {
    ToolWindow defaultToolWindow = myDefaultDocToolWindow;
    if (defaultToolWindow == null && myLangToolWindows.isEmpty()) {
      return;
    }

    myToolWindow = null;
    myDefaultDocToolWindow = null;
    PropertiesComponent.getInstance().setValue(getShowInToolWindowProperty(), Boolean.FALSE.toString());

    if (defaultToolWindow != null) {
      defaultToolWindow.remove();
      Disposer.dispose(defaultToolWindow.getContentManager());
    }

    for (Map.Entry<String, ToolWindow> entry : myLangToolWindows.entrySet()) {
      Language language = Language.findLanguageByID(entry.getKey());
      if (language == null) continue;
      DocToolWindowManager toolWindowManager = DocToolWindowManager.LANGUAGE_MANAGER.forLanguage(language);
      if (toolWindowManager != null) {
        toolWindowManager.disposeToolWindow(entry.getValue(), this);
      }
    }
    myLangToolWindows.clear();

    restartAutoUpdate(false);
    Component previouslyFocused = SoftReference.dereference(myFocusedBeforePopup);
    if (previouslyFocused != null && previouslyFocused.isShowing()) {
      UIUtil.runWhenFocused(previouslyFocused, () -> updateComponent(true));
      IdeFocusManager.getInstance(myProject).requestFocus(previouslyFocused, true);
    }
  }

  @Override
  public Content recreateToolWindow(PsiElement element, PsiElement originalElement) {
    Language language = element.getLanguage();
    DocToolWindowManager toolWindowManager = DocToolWindowManager.LANGUAGE_MANAGER.forLanguage(language);
    ToolWindow toolWindow;
    if (toolWindowManager == null) {
      toolWindow = myDefaultDocToolWindow;
    }
    else {
      toolWindow = myLangToolWindows.get(language.getID());
    }

    if (toolWindow == null) {
      createToolWindow(element, originalElement);
      return null;
    }

    final Content content;
    if (toolWindowManager != null) {
      content = toolWindowManager.getDocumentationContent(toolWindow, this);
    }
    else {
      content = toolWindow.getContentManager().getSelectedContent();
    }
    if (content == null || !toolWindow.isVisible()) {
      restorePopupBehavior();
      createToolWindow(element, originalElement);
      return null;
    }
    return content;
  }

  public void registerQuickDocShortcutSet(JComponent component, AnAction restorePopupAction) {
    ShortcutSet quickDocShortcut = ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC).getShortcutSet();
    restorePopupAction.registerCustomShortcutSet(quickDocShortcut, component);
  }

  @Override
  public void createToolWindow(@NotNull PsiElement element, PsiElement originalElement) {
    createToolWindow(element, originalElement, false);
  }

  protected void createToolWindow(@NotNull PsiElement element, PsiElement originalElement, boolean onAutoUpdate) {
    doCreateToolWindow(element, originalElement, onAutoUpdate);
    if (myToolWindow != null) {
      myToolWindow.getComponent().putClientProperty(ChooseByNameBase.TEMPORARILY_FOCUSABLE_COMPONENT_KEY, Boolean.TRUE);
    }
  }

  private void doCreateDefaultToolWindow(@NotNull PsiElement element, PsiElement originalElement) {
    myToolWindow = null;
    super.createToolWindow(element, originalElement);
    myDefaultDocToolWindow = myToolWindow;
    if (myRestorePopupAction != null) {
      registerQuickDocShortcutSet(myToolWindow.getComponent(), myRestorePopupAction);
      myRestorePopupAction = null;
    }
  }

  private void doCreateToolWindow(@NotNull PsiElement element, PsiElement originalElement, boolean onAutoUpdate) {
    Language language = element.getLanguage();
    assert myLangToolWindows.get(language.getID()) == null;

    DocToolWindowManager toolWindowManager = DocToolWindowManager.LANGUAGE_MANAGER.forLanguage(language);
    if (toolWindowManager == null) {
      doCreateDefaultToolWindow(element, originalElement);
      return;
    }
    else if (onAutoUpdate && !toolWindowManager.isAutoUpdateAvailable()) {
      return;
    }

    ToolWindow toolWindow = toolWindowManager.createToolWindow(element, originalElement, this);
    DocumentationComponent component = toolWindowManager.getDocumentationComponent(toolWindow, this);
    if (component == null) {
      // If failed create language toolwindow - create default
      doCreateDefaultToolWindow(element, originalElement);
      return;
    }
    myToolWindow = toolWindow;
    myLangToolWindows.put(language.getID(), toolWindow);

    toolWindow.setAvailable(true);
    toolWindow.setToHideOnEmptyContent(false);
    toolWindow.show(null);

    toolWindowManager.installToolWindowActions(toolWindow, this);
    toolWindowManager.setToolWindowDefaultState(toolWindow, this);
    toolWindowManager.prepareForShowDocumentation(toolWindow, this);
    toolWindowManager.updateToolWindowDocumentationTabName(toolWindow, element, this);

    ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentRemoved(@NotNull ContentManagerEvent event) {
        if (event.getContent().getComponent() == component) {
          restorePopupBehavior();
        }
      }
    });

    UiNotifyConnector.installOn(component, new Activatable() {
      @Override
      public void showNotify() {
        restartAutoUpdate(PropertiesComponent.getInstance().getBoolean(getAutoUpdateEnabledProperty(), getAutoUpdateDefault()));
      }

      @Override
      public void hideNotify() {
        restartAutoUpdate(false);
      }
    });

    PropertiesComponent.getInstance().setValue(getShowInToolWindowProperty(), Boolean.TRUE.toString());
    restartAutoUpdate(PropertiesComponent.getInstance().getBoolean(getAutoUpdateEnabledProperty(), true));
    doUpdateComponent(element, originalElement, component, onAutoUpdate);
  }

  @Override
  protected void installComponentActions(@NotNull ToolWindow toolWindow, DocumentationComponent component) {
    toolWindow.setTitleActions(component.getNavigationActions());
    DefaultActionGroup group = new DefaultActionGroup(createActions());
    group.add(component.getFontSizeAction());
    toolWindow.setAdditionalGearActions(group);
    component.removeCornerMenu();
  }

  @Override
  protected void setToolwindowDefaultState(@NotNull ToolWindow toolWindow) {
    Rectangle rectangle = Objects.requireNonNull(WindowManager.getInstance().getIdeFrame(myProject)).suggestChildFrameBounds();
    toolWindow.setDefaultState(ToolWindowAnchor.RIGHT, ToolWindowType.DOCKED, new Rectangle(rectangle.width / 4, rectangle.height));
    toolWindow.setType(ToolWindowType.DOCKED, null);
    toolWindow.setSplitMode(true, null);
    toolWindow.setAutoHide(false);
  }

  public static DocumentationManager getInstance(@NotNull Project project) {
    return project.getService(DocumentationManager.class);
  }

  public DocumentationManager(@NotNull Project project) {
    super(project);
    AnActionListener actionListener = new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
        JBPopup hint = getDocInfoHint();
        if (hint != null &&
            LookupManager.getActiveLookup(myEditor) == null && // let the lookup manage all the actions
            !Conditions.instanceOf(ACTION_CLASSES_TO_IGNORE).value(action) &&
            !ArrayUtil.contains(event.getPlace(), ACTION_PLACES_TO_IGNORE) &&
            !ContainerUtil.exists(ACTION_IDS_TO_IGNORE, id -> ActionManager.getInstance().getAction(id) == action) &&
            clientOwns(hint)) {
          closeDocHint();
        }
      }

      @Override
      public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
        JBPopup hint = getDocInfoHint();
        if (hint != null && LookupManager.getActiveLookup(myEditor) == null && clientOwns(hint)) {
          hint.cancel();
        }
      }

      private static boolean clientOwns(@NotNull JBPopup hint) {
        ClientId ownerId = hint.getUserData(ClientId.class);
        return ownerId == null || ownerId.equals(ClientId.getCurrent());
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect(project).subscribe(AnActionListener.TOPIC, actionListener);
    myUpdateDocAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myProject);

    DocToolWindowManager.DocToolWindowLanguageManager.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull KeyedLazyInstance<DocToolWindowManager> extension, @NotNull PluginDescriptor pluginDescriptor) {
        String language = extension.getKey();
        ToolWindow toolWindow = myLangToolWindows.remove(language);
        if (toolWindow == null) return;

        if (myToolWindow == toolWindow) {
          myToolWindow = myDefaultDocToolWindow;
          if (myToolWindow == null) {
            myToolWindow = myLangToolWindows.values().stream().findFirst().orElse(null);
          }
        }
        toolWindow.remove();
        Disposer.dispose(toolWindow.getContentManager());
      }
    }, project);
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

  @SuppressWarnings("unused") // used by plugin
  public void showJavaDocInfoAtToolWindow(@NotNull PsiElement element, @NotNull PsiElement original) {
    Content content = recreateToolWindow(element, original);
    if (content == null) return;
    DocumentationComponent component = (DocumentationComponent)content.getComponent();
    myUpdateDocAlarm.cancelAllRequests();
    doFetchDocInfo(component, new MyCollector(myProject, element, original, null, false, false))
      .doWhenDone(() -> component.clearHistory());
  }

  public void showJavaDocInfo(@NotNull PsiElement element, PsiElement original) {
    showJavaDocInfo(element, original, null);
  }

  public void showJavaDocInfo(@NotNull PsiElement element,
                              PsiElement original,
                              @Nullable Runnable closeCallback) {
    showJavaDocInfo(element, original, closeCallback, false);
  }

  protected void showJavaDocInfo(@NotNull PsiElement element,
                                 PsiElement original,
                                 @Nullable Runnable closeCallback,
                                 boolean onAutoUpdate) {
    showJavaDocInfo(element, original, false, closeCallback, null, true, onAutoUpdate);
  }

  public void showJavaDocInfo(@NotNull PsiElement element,
                              PsiElement original,
                              boolean requestFocus,
                              @Nullable Runnable closeCallback) {
    showJavaDocInfo(element, original, requestFocus, closeCallback, null, true);
  }

  public void showJavaDocInfo(@NotNull PsiElement element,
                              PsiElement original,
                              boolean requestFocus,
                              @Nullable Runnable closeCallback,
                              @Nullable @Nls String documentation,
                              boolean useStoredPopupSize) {
    showJavaDocInfo(element, original, requestFocus, closeCallback, documentation, useStoredPopupSize, false);
  }

  protected void showJavaDocInfo(@NotNull PsiElement element,
                                 PsiElement original,
                                 boolean requestFocus,
                                 @Nullable Runnable closeCallback,
                                 @Nullable @Nls String documentation,
                                 boolean useStoredPopupSize,
                                 boolean onAutoUpdate) {
    if (!element.isValid()) {
      return;
    }

    PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(element.getProject()) {
      @Override
      public void updatePopup(Object lookupItemObject) {
        PsiElement psiElement = PSIPresentationBgRendererWrapper.toPsi(lookupItemObject);
        if (psiElement != null) {
          doShowJavaDocInfo(psiElement, requestFocus, this, original, null, null,
                            useStoredPopupSize, onAutoUpdate);
        }
      }
    };

    doShowJavaDocInfo(element, requestFocus, updateProcessor, original, closeCallback, documentation, useStoredPopupSize, onAutoUpdate);
  }

  public void showJavaDocInfo(Editor editor, @Nullable PsiFile file, boolean requestFocus) {
    showJavaDocInfo(editor, file, requestFocus, null);
  }

  public void showJavaDocInfo(Editor editor,
                              @Nullable PsiFile file,
                              boolean requestFocus,
                              @Nullable Runnable closeCallback) {
    showJavaDocInfo(editor, file, requestFocus, closeCallback, false);
  }

  protected void showJavaDocInfo(Editor editor,
                                 @Nullable PsiFile file,
                                 boolean requestFocus,
                                 @Nullable Runnable closeCallback,
                                 boolean onAutoUpdate) {
    myEditor = editor;
    Project project = getProject(file);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (file != null && !file.isValid()) {
      file = null; // commit could invalidate the file
    }
    PsiFile finalFile = file;

    PsiElement originalElement = getContextElement(editor, file);

    int offset = editor.getCaretModel().getOffset();
    CancellablePromise<PsiElement> elementPromise = ReadAction.nonBlocking(
      () -> findTargetElementFromContext(editor, offset, finalFile)
    ).coalesceBy(this).submit(AppExecutorUtil.getAppExecutorService());
    CompletableFuture<PsiElement> elementFuture = Promises.asCompletableFuture(elementPromise);

    PopupUpdateProcessor updateProcessor = new PopupUpdateProcessor(project) {
      @Override
      public void updatePopup(Object lookupItemObject) {
        if (lookupItemObject == null) {
          doShowJavaDocInfo(elementFuture, false, this, originalElement, closeCallback,
                            CodeInsightBundle.message("no.documentation.found"),
                            true, onAutoUpdate);
          return;
        }
        PsiElement psiElement = PSIPresentationBgRendererWrapper.toPsi(lookupItemObject);
        if (psiElement != null) {
          doShowJavaDocInfo(psiElement, false, this, originalElement, closeCallback,
                            null, true, onAutoUpdate);
          return;
        }

        DocumentationProvider documentationProvider = getProviderFromElement(finalFile);

        PsiElement element = documentationProvider.getDocumentationElementForLookupItem(
          PsiManager.getInstance(myProject),
          lookupItemObject,
          originalElement
        );

        if (element == null) {
          doShowJavaDocInfo(elementFuture, false, this, originalElement, closeCallback,
                            CodeInsightBundle.message("no.documentation.found"),
                            true, onAutoUpdate);
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
          doShowJavaDocInfo(element, false, this, originalElement, closeCallback, null, true, onAutoUpdate);
        }
      }
    };

    doShowJavaDocInfo(elementFuture, requestFocus, updateProcessor, originalElement, closeCallback, null, true, onAutoUpdate);
  }

  public PsiElement findTargetElement(Editor editor, PsiFile file) {
    return findTargetElement(editor, file, getContextElement(editor, file));
  }

  @Internal
  public static @Nullable PsiElement getContextElement(Editor editor, PsiFile file) {
    return getContextElement(file, editor.getCaretModel().getOffset());
  }

  private static @Nullable PsiElement getContextElement(@Nullable PsiFile file, int offset) {
    if (file == null) return null;
    if (offset == file.getTextLength()) {
      offset = Math.max(0, offset - 1);
    }
    return file.findElementAt(offset);
  }

  protected void doShowJavaDocInfo(@NotNull PsiElement element,
                                   boolean requestFocus,
                                   @NotNull PopupUpdateProcessor updateProcessor,
                                   PsiElement originalElement,
                                   @Nullable Runnable closeCallback,
                                   @Nullable @Nls String documentation,
                                   boolean useStoredPopupSize,
                                   boolean onAutoUpdate) {
    doShowJavaDocInfo(element, requestFocus, updateProcessor, originalElement, closeCallback, null,
                      documentation, useStoredPopupSize, onAutoUpdate);
  }

  protected void doShowJavaDocInfo(@NotNull PsiElement element,
                                   boolean requestFocus,
                                   @NotNull PopupUpdateProcessor updateProcessor,
                                   PsiElement originalElement,
                                   @Nullable Runnable closeCallback,
                                   @Nullable ActionCallback actionCallback,
                                   @Nullable @Nls String documentation,
                                   boolean useStoredPopupSize,
                                   boolean onAutoUpdate) {
    if (!myProject.isOpen()) return;

    ReadAction.run(() -> {
      assertSameProject(element);
      storeOriginalElement(myProject, originalElement, element);
    });

    JBPopup prevHint = getDocInfoHint();

    Language language = element.getLanguage();
    ToolWindow newToolWindow;
    DocToolWindowManager toolWindowManager = DocToolWindowManager.LANGUAGE_MANAGER.forLanguage(language);
    if (toolWindowManager == null) {
      newToolWindow = myDefaultDocToolWindow;
    }
    else {
      newToolWindow = myLangToolWindows.get(language.getID());
    }

    myPrecalculatedDocumentation = documentation;
    if (newToolWindow == null && PropertiesComponent.getInstance().isTrueValue(SHOW_DOCUMENTATION_IN_TOOL_WINDOW)) {
      createToolWindow(element, originalElement, onAutoUpdate);
    }
    else if (newToolWindow != null) {
      myToolWindow = newToolWindow;
      Content content;
      if (toolWindowManager != null) {
        content = toolWindowManager.getDocumentationContent(myToolWindow, this);
      }
      else {
        content = myToolWindow.getContentManager().getSelectedContent();
      }
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
          cancelAndFetchDocInfo(component, new MyCollector(myProject, element, originalElement, null, actionCallback, false, onAutoUpdate))
            .doWhenDone(() -> component.clearHistory());
        }
      }

      if (!myToolWindow.isVisible()) {
        myToolWindow.show(null);
      }
    }
    else if (prevHint != null && prevHint.isVisible() && prevHint instanceof AbstractPopup) {
      DocumentationComponent component = (DocumentationComponent)((AbstractPopup)prevHint).getComponent();
      ActionCallback result =
        cancelAndFetchDocInfo(component, new MyCollector(myProject, element, originalElement, null, actionCallback, false, false));
      if (requestFocus) {
        result.doWhenDone(() -> {
          JBPopup hint = getDocInfoHint();
          if (hint != null) ((AbstractPopup)hint).focusPreferredComponent();
        });
      }
    }
    else {
      showInPopup(element, requestFocus, updateProcessor, originalElement, closeCallback, actionCallback, useStoredPopupSize);
    }
  }

  protected void doShowJavaDocInfo(@NotNull CompletableFuture<? extends PsiElement> elementFuture,
                                   boolean requestFocus,
                                   @NotNull PopupUpdateProcessor updateProcessor,
                                   PsiElement originalElement,
                                   @Nullable Runnable closeCallback,
                                   @Nullable @Nls String documentation,
                                   boolean useStoredPopupSize,
                                   boolean onAutoUpdate) {
    if (!myProject.isOpen()) return;

    PsiElement targetElement = null;
    try {
      //try to get target element if possible (in case when element can be resolved fast)
      targetElement = elementFuture.get(50, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      LOG.debug("Failed to calculate targetElement in 50ms", e);
    }

    if (targetElement != null) {
      doShowJavaDocInfo(targetElement, requestFocus, updateProcessor, originalElement, closeCallback,
                        documentation, useStoredPopupSize, onAutoUpdate);
    }
    else {
      ActionCallback actionCallback = createActionCallback();
      elementFuture.thenAccept(element -> {
        if (element != null) {
          AppUIUtil.invokeOnEdt(() -> {
            doShowJavaDocInfo(element, requestFocus, updateProcessor, originalElement, closeCallback, actionCallback,
                              documentation, useStoredPopupSize, onAutoUpdate);
          });
        }
      });
    }
  }

  private void showInPopup(@NotNull PsiElement element,
                           boolean requestFocus,
                           PopupUpdateProcessor updateProcessor,
                           PsiElement originalElement,
                           @Nullable Runnable closeCallback,
                           @Nullable ActionCallback actionCallback,
                           boolean useStoredPopupSize) {
    Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
    myFocusedBeforePopup = new WeakReference<>(focusedComponent);

    DocumentationComponent component = myTestDocumentationComponent == null ? new DocumentationComponent(this, useStoredPopupSize) :
                                       myTestDocumentationComponent;
    ActionListener actionListener = __ -> {
      createToolWindow(element, originalElement);
      JBPopup hint = getDocInfoHint();
      if (hint != null && hint.isVisible()) hint.cancel();
    };
    List<Pair<ActionListener, KeyStroke>> actions = new SmartList<>();
    AnAction quickDocAction = ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC);
    for (Shortcut shortcut : quickDocAction.getShortcutSet().getShortcuts()) {
      if (!(shortcut instanceof KeyboardShortcut)) continue;
      actions.add(Pair.create(actionListener, ((KeyboardShortcut)shortcut).getFirstKeyStroke()));
    }

    boolean hasLookup = LookupManager.getActiveLookup(myEditor) != null;
    AbstractPopup hint = (AbstractPopup)JBPopupFactory
      .getInstance().createComponentPopupBuilder(component, component)
      .setProject(myProject)
      .addListener(updateProcessor)
      .addUserData(updateProcessor)
      .addUserData(ClientId.getCurrent())
      .setKeyboardActions(actions)
      .setResizable(true)
      .setMovable(true)
      .setFocusable(true)
      .setRequestFocus(requestFocus)
      .setCancelOnClickOutside(!hasLookup) // otherwise, selecting lookup items by mouse would close the doc
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

    if (useStoredPopupSize && DimensionService.getInstance().getSize(NEW_JAVADOC_LOCATION_AND_SIZE, myProject) != null) {
      hint.setDimensionServiceKey(NEW_JAVADOC_LOCATION_AND_SIZE);
    }

    if (myEditor == null) {
      // subsequent invocation of javadoc popup from completion will have myEditor == null because of cancel invoked,
      // so reevaluate the editor for proper popup placement
      Lookup lookup = LookupManager.getInstance(myProject).getActiveLookup();
      myEditor = lookup != null ? lookup.getEditor() : null;
    }
    cancelAndFetchDocInfo(component, new MyCollector(myProject, element, originalElement, null, actionCallback, false, false));

    myDocInfoHintRef = new WeakReference<>(hint);

    findQuickSearchComponent().ifPresent(quickSearch -> quickSearch.registerHint(hint));

    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getSource() == hint.getPopupWindow()) {
        myCloseOnSneeze = false;
      }
      return false;
    }, component);
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

  private @Nullable PsiElement findTargetElementFromContext(@NotNull Editor editor, int offset, @Nullable PsiFile file) {
    if (LookupManager.getInstance(myProject).getActiveLookup() != null) {
      try {
        return assertSameProject(getElementFromLookup(editor, file));
      }
      catch (IndexNotReadyException e) {
        return null;
      }
    }
    var elementAndContext = findTargetElementAndContext(editor, offset, file);
    return elementAndContext == null ? null : elementAndContext.first;
  }

  @Internal
  public @Nullable Pair<@NotNull PsiElement, @Nullable PsiElement> findTargetElementAndContext(
    @NotNull Editor editor,
    int offset,
    @Nullable PsiFile file
  ) {
    PsiElement originalElement = getContextElement(file, offset);
    PsiElement element = findTargetElementAtOffset(editor, offset, file, originalElement);
    if (element == null) {
      PsiElement list = ParameterInfoControllerBase.findArgumentList(file, offset, -1);
      if (list != null) {
        element = list;
      }
    }
    if (element == null && file == null) return null; //file == null for text field editor

    if (element == null) { // look if we are within a javadoc comment
      element = assertSameProject(originalElement);
      if (element == null) return null;

      PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class);
      if (comment == null) return null;

      element = comment instanceof PsiDocCommentBase ? ((PsiDocCommentBase)comment).getOwner() : comment.getParent();
      if (element == null) return null;
      //if (!(element instanceof PsiDocCommentOwner)) return null;
    }
    return Pair.create(element, originalElement);
  }

  public @Nullable PsiElement findTargetElement(@NotNull Editor editor, @Nullable PsiFile file, PsiElement contextElement) {
    return findTargetElement(editor, editor.getCaretModel().getOffset(), file, contextElement);
  }

  public @Nullable PsiElement findTargetElement(Editor editor, int offset, @Nullable PsiFile file, PsiElement contextElement) {
    try {
      return findTargetElementUnsafe(editor, offset, file, contextElement);
    }
    catch (IndexNotReadyException ex) {
      LOG.debug(ex);
      return null;
    }
  }

  /**
   * in case index is not ready will throw IndexNotReadyException
   */
  private @Nullable PsiElement findTargetElementUnsafe(Editor editor, int offset, @Nullable PsiFile file, PsiElement contextElement) {
    if (LookupManager.getInstance(myProject).getActiveLookup() != null) {
      return assertSameProject(getElementFromLookup(editor, file));
    }

    return findTargetElementAtOffset(editor, offset, file, contextElement);
  }

  @Internal
  public @Nullable PsiElement findTargetElementAtOffset(
    @NotNull Editor editor,
    int offset,
    @Nullable PsiFile file,
    @Nullable PsiElement contextElement
  ) {
    PsiElement element = assertSameProject(doFindTargetElementAtOffset(editor, offset, file, contextElement));
    storeOriginalElement(myProject, contextElement, element);
    storeIsFromLookup(element, false);
    return element;
  }

  private static @Nullable PsiElement doFindTargetElementAtOffset(
    @NotNull Editor editor,
    int offset,
    @Nullable PsiFile file,
    @Nullable PsiElement contextElement
  ) {
    PsiElement element;

    element = customElement(editor, file, offset, contextElement);
    if (element != null) {
      return element;
    }

    element = fromTargetUtil(editor, offset, contextElement);
    if (element != null) {
      return element;
    }

    return fromReference(editor, offset);
  }

  private static @Nullable PsiElement customElement(
    @NotNull Editor editor,
    @Nullable PsiFile file,
    int offset,
    @Nullable PsiElement contextElement
  ) {
    if (file == null) {
      return null;
    }
    return getProviderFromElement(file).getCustomDocumentationElement(editor, file, contextElement, offset);
  }

  private static @Nullable PsiElement fromTargetUtil(
    @NotNull Editor editor,
    int offset,
    @Nullable PsiElement contextElement
  ) {
    TargetElementUtil util = TargetElementUtil.getInstance();
    PsiElement element = util.findTargetElement(editor, util.getAllAccepted(), offset);
    if (element == null && contextElement == null) {
      return null;
    }
    // Allow context doc over xml tag content
    PsiElement adjusted = util.adjustElement(editor, util.getAllAccepted(), element, contextElement);
    return adjusted != null ? adjusted : element;
  }

  private static @Nullable PsiElement fromReference(@NotNull Editor editor, int offset) {
    PsiReference ref = TargetElementUtil.findReference(editor, offset);
    if (ref == null) {
      return null;
    }
    if (ref instanceof PsiPolyVariantReference) {
      return ref.getElement();
    }
    return TargetElementUtil.getInstance().adjustReference(ref);
  }

  private static void storeIsFromLookup(@Nullable PsiElement element, boolean value) {
    if (element == null) return;
    element.putUserData(IS_FROM_LOOKUP, value ? true : null);
  }

  public @Nullable PsiElement getElementFromLookup(Editor editor, @Nullable PsiFile file) {
    Lookup activeLookup = LookupManager.getInstance(myProject).getActiveLookup();

    if (activeLookup != null) {
      LookupElement item = activeLookup.getCurrentItem();
      if (item != null) {
        return getElementFromLookup(myProject, editor, file, item);
      }
    }
    return null;
  }

  @Internal
  public static @Nullable PsiElement getElementFromLookup(
    @NotNull Project project,
    @NotNull Editor editor,
    @Nullable PsiFile file,
    @NotNull LookupElement item
  ) {
    int offset = editor.getCaretModel().getOffset();
    if (offset > 0 && offset == editor.getDocument().getTextLength()) offset--;
    PsiReference ref = TargetElementUtil.findReference(editor, offset);
    PsiElement contextElement = file == null ? null : ObjectUtils.coalesce(file.findElementAt(offset), file);
    PsiElement targetElement = ref != null ? ref.getElement() : contextElement;
    if (targetElement != null) {
      PsiUtilCore.ensureValid(targetElement);
    }

    DocumentationProvider documentationProvider = getProviderFromElement(file);
    PsiManager psiManager = PsiManager.getInstance(project);
    PsiElement fromProvider = targetElement == null ? null :
                              documentationProvider.getDocumentationElementForLookupItem(psiManager, item.getObject(), targetElement);
    if (fromProvider == null) {
      return CompletionUtil.getTargetElement(item);
    }
    storeIsFromLookup(fromProvider, true);
    return fromProvider;
  }

  public @NlsSafe String generateDocumentation(@NotNull PsiElement element, @Nullable PsiElement originalElement, boolean onHover) {
    return new MyCollector(myProject, element, originalElement, null, onHover, false).getDocumentation();
  }

  public @Nullable JBPopup getDocInfoHint() {
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
    cancelAndFetchDocInfo(component, new MyCollector(myProject, element, null, null, false, false));
  }

  public ActionCallback queueFetchDocInfo(@NotNull PsiElement element, @NotNull DocumentationComponent component) {
    return doFetchDocInfo(component, new MyCollector(myProject, element, null, null, false, false));
  }

  private ActionCallback cancelAndFetchDocInfo(@NotNull DocumentationComponent component, @NotNull DocumentationCollector provider) {
    myUpdateDocAlarm.cancelAllRequests();
    return doFetchDocInfo(component, provider);
  }

  void updateToolWindowTabName(@NotNull PsiElement element) {
    if (myToolWindow != null) {
      DocToolWindowManager toolWindowManager = DocToolWindowManager.LANGUAGE_MANAGER.forLanguage(element.getLanguage());
      if (toolWindowManager != null) {
        toolWindowManager.updateToolWindowDocumentationTabName(myToolWindow, element, this);
      }
      else {
        Content content = myToolWindow.getContentManager().getSelectedContent();
        if (content != null) content.setDisplayName(getTitle(element));
      }
    }
  }

  private ActionCallback doFetchDocInfo(@NotNull DocumentationComponent component,
                                        @NotNull DocumentationCollector collector) {
    ActionCallback callback = collector.actionCallback != null ? collector.actionCallback : createActionCallback();

    boolean wasEmpty = component.isEmpty();
    if (wasEmpty) {
      component.setText(CodeInsightBundle.message("javadoc.fetching.progress"), null, collector.provider);
    }

    ModalityState modality = ModalityState.defaultModalityState();

    String precalculatedDocumentation = myPrecalculatedDocumentation;
    myPrecalculatedDocumentation = null;
    myUpdateDocAlarm.addRequest(() -> {
      if (myProject.isDisposed()) return;
      LOG.debug("Started fetching documentation...");

      PsiElement element = collector.getElement(true);
      if (element == null || !ReadAction.compute(() -> element.isValid())) {
        LOG.debug("Element for which documentation was requested is not available anymore");
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
          component.setText(CodeInsightBundle.message("no.documentation.found"), null, collector.provider);
        });
        callback.setDone();
        return;
      }

      Language elementLanguage = ReadAction.compute(() -> element.getLanguage());
      DocToolWindowManager toolWindowManager = DocToolWindowManager.LANGUAGE_MANAGER.forLanguage(elementLanguage);
      if (toolWindowManager != null) {
        if (collector.onAutoUpdate && !toolWindowManager.isAutoUpdateAvailable()) {
          callback.setDone();
          return;
        }
        if (myToolWindow != null) {
          toolWindowManager.prepareForShowDocumentation(myToolWindow, this);
        }
      }
      component.startWait();

      final @Nls String text;
      final DocumentationProvider provider;
      try {
        if (precalculatedDocumentation != null) {
          LOG.debug("Setting precalculated documentation:\n", precalculatedDocumentation);
          text = precalculatedDocumentation;
          PsiElement originalElement = getOriginalElement(collector, element);
          provider = ReadAction.compute(() -> getProviderFromElement(element, originalElement));
        }
        else {
          text = collector.getDocumentation();
          provider = collector.provider;
        }
      }
      catch (Throwable e) {
        LOG.info(e);
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
          //noinspection InstanceofCatchParameter
          String message = e instanceof IndexNotReadyException
                           ? CodeInsightBundle.message("documentation.message.documentation.is.not.available")
                           : CodeInsightBundle.message("javadoc.external.fetch.error.message");
          component.setText(message, null, collector.provider);
          component.clearHistory();
          callback.setDone();
        });
        return;
      }

      LOG.debug("Documentation fetched successfully:\n", text);

      final @Nls String decoratedText = ReadAction.compute(() -> {
        if (text == null) {
          return decorate(element, CodeInsightBundle.message("no.documentation.found"), null, provider);
        }
        else if (text.isEmpty()) {
          return null;
        }
        else if (precalculatedDocumentation != null) {
          return text; // text == precalculatedDocumentation in this case; don't decorate it
        }
        else {
          return decorate(element, text, collector.effectiveUrl, provider);
        }
      });

      PsiDocumentManager.getInstance(myProject).performLaterWhenAllCommitted(modality, () -> {
        if (!element.isValid()) {
          LOG.debug("Element for which documentation was requested is not valid");
          callback.setDone();
          return;
        }
        if (text == null) {
          component.setText(decoratedText, element, provider);
        }
        else if (text.isEmpty()) {
          component.setText(component.getDecoratedText(), element, provider);
        }
        else {
          component.setData(element, decoratedText, collector.effectiveUrl, collector.ref, provider);
        }
        if (wasEmpty) {
          component.clearHistory();
        }
        callback.setDone();
      });
    }, 10);
    return callback;
  }

  public static @NotNull DocumentationProvider getProviderFromElement(PsiElement element) {
    return getProviderFromElement(element, null);
  }

  public static @NotNull DocumentationProvider getProviderFromElement(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
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

  public static @Nullable PsiElement getOriginalElement(PsiElement element) {
    SmartPsiElementPointer<?> originalElementPointer = element != null ? element.getUserData(ORIGINAL_ELEMENT_KEY) : null;
    return originalElementPointer != null ? originalElementPointer.getElement() : null;
  }

  public @Nullable PsiElement getTargetElement(@Nullable PsiElement context, @Nullable String url) {
    Pair<@NotNull PsiElement, @Nullable String> target = getTarget(context, url);
    return target == null ? null : target.first;
  }

  private @Nullable Pair<@NotNull PsiElement, @Nullable String> getTarget(@Nullable PsiElement context, @Nullable String url) {
    if (context == null || url == null) {
      return null;
    }
    return targetAndRef(getProject(context), url, context);
  }

  @Internal
  public static @Nullable Pair<@NotNull PsiElement, @Nullable String> targetAndRef(
    @NotNull Project project,
    @NotNull String url,
    @Nullable PsiElement context
  ) {
    Pair<String, String> linkAndRef = parseUrl(url);
    if (linkAndRef == null) {
      return null;
    }
    PsiElement targetElement = targetElement(project, linkAndRef.first, context);
    if (targetElement != null) {
      return Pair.create(targetElement, linkAndRef.second);
    }
    return null;
  }

  private static @Nullable Pair<@NotNull String, @Nullable String> parseUrl(@NotNull String url) {
    if (!url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
      return null;
    }
    String withoutProtocol = url.substring(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL.length());
    int separatorPos = withoutProtocol.lastIndexOf(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL_REF_SEPARATOR);
    if (separatorPos >= 0) {
      return Pair.create(
        withoutProtocol.substring(0, separatorPos),
        withoutProtocol.substring(separatorPos + DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL_REF_SEPARATOR.length())
      );
    }
    else {
      return Pair.create(withoutProtocol, null);
    }
  }

  private static @Nullable PsiElement targetElement(
    @NotNull Project project,
    @NotNull String link,
    @Nullable PsiElement context
  ) {
    PsiManager manager = PsiManager.getInstance(project);
    DocumentationProvider provider = getProviderFromElement(context);
    PsiElement targetElement = provider.getDocumentationElementForLink(manager, link, context);
    if (targetElement != null) {
      return targetElement;
    }
    return targetFromLanguageProviders(manager, link, context);
  }

  private static @Nullable PsiElement targetFromLanguageProviders(
    @NotNull PsiManager manager,
    @NotNull String link,
    @Nullable PsiElement context
  ) {
    for (Language language : Language.getRegisteredLanguages()) {
      DocumentationProvider documentationProvider = LanguageDocumentation.INSTANCE.forLanguage(language);
      if (documentationProvider != null) {
        PsiElement targetElement = documentationProvider.getDocumentationElementForLink(manager, link, context);
        if (targetElement != null) {
          return targetElement;
        }
      }
    }
    return null;
  }

  private static PsiElement getOriginalElement(@NotNull DocumentationCollector collector, PsiElement targetElement) {
    return collector instanceof MyCollector ? ((MyCollector)collector).originalElement : targetElement;
  }

  public void navigateByLink(@NotNull DocumentationComponent component, @Nullable PsiElement context, @NotNull String url) {
    myPrecalculatedDocumentation = null;
    component.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    PsiElement psiElement = context != null ? context : component.getElement();
    if (psiElement == null) {
      return;
    }
    PsiManager manager = PsiManager.getInstance(getProject(psiElement));
    if (url.equals("external_doc")) {
      component.showExternalDoc();
      return;
    }
    if (url.startsWith("open")) {
      OrderEntry libraryEntry = libraryEntry(myProject, psiElement);
      if (libraryEntry != null) {
        ProjectSettingsService.getInstance(myProject).openLibraryOrSdkSettings(libraryEntry);
      }
    }
    else if (url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
      ActionCallback callback = createActionCallback();
      callback.doWhenProcessed(() -> component.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)));
      ReadAction.nonBlocking(
        () -> getTarget(psiElement, url)
      ).finishOnUiThread(ModalityState.defaultModalityState(), target -> {
        if (target == null) {
          callback.setDone();
          return;
        }
        cancelAndFetchDocInfoByLink(component, new MyCollector(myProject, target.first, null, target.second, callback, false, false));
      }).submit(AppExecutorUtil.getAppExecutorService());
      return;
    }
    else {
      DocumentationProvider provider = getProviderFromElement(psiElement);
      boolean processed = false;
      if (provider instanceof CompositeDocumentationProvider) {
        for (DocumentationProvider p : ((CompositeDocumentationProvider)provider).getAllProviders()) {
          if (!(p instanceof ExternalDocumentationHandler externalHandler)) continue;

          if (externalHandler.canFetchDocumentationLink(url)) {
            String ref = externalHandler.extractRefFromLink(url);
            cancelAndFetchDocInfoByLink(component, new DocumentationCollector(psiElement, url, ref, p, false) {
              @Override
              public @Nls String getDocumentation() {
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
        cancelAndFetchDocInfoByLink(component, new DocumentationCollector(psiElement, url, null, provider, false) {
          @Override
          public @Nls String getDocumentation() {
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

  @Internal
  public static @Nullable OrderEntry libraryEntry(@NotNull Project project, @NotNull PsiElement psiElement) {
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null) {
      return libraryEntry(project, containingFile);
    }
    else if (psiElement instanceof PsiDirectoryContainer) {
      PsiDirectory[] directories = ((PsiDirectoryContainer)psiElement).getDirectories();
      for (PsiDirectory directory : directories) {
        OrderEntry libraryEntry = libraryEntry(project, directory);
        if (libraryEntry != null) {
          return libraryEntry;
        }
      }
      return null;
    }
    else {
      return null;
    }
  }

  private static @Nullable OrderEntry libraryEntry(@NotNull Project project, @NotNull PsiFileSystemItem directory) {
    VirtualFile virtualFile = directory.getVirtualFile();
    return LibraryUtil.findLibraryEntry(virtualFile, project);
  }

  protected ActionCallback cancelAndFetchDocInfoByLink(@NotNull DocumentationComponent component,
                                                       @NotNull DocumentationCollector provider) {
    return cancelAndFetchDocInfo(component, provider);
  }

  public Project getProject() {
    return myProject;
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
  protected void doUpdateComponent(@NotNull CompletableFuture<? extends PsiElement> elementFuture,
                                   PsiElement originalElement,
                                   DocumentationComponent component) {
    doUpdateComponent(elementFuture, originalElement, component, false);
  }

  @Override
  protected void doUpdateComponent(@NotNull CompletableFuture<? extends PsiElement> elementFuture,
                                   PsiElement originalElement,
                                   DocumentationComponent component,
                                   boolean onAutoUpdate) {
    cancelAndFetchDocInfo(component, new MyCollector(myProject, elementFuture, originalElement, null, null, false, onAutoUpdate));
  }

  @Override
  protected void doUpdateComponent(@NotNull PsiElement element,
                                   PsiElement originalElement,
                                   DocumentationComponent component) {
    doUpdateComponent(element, originalElement, component, false);
  }

  @Override
  protected void doUpdateComponent(@NotNull PsiElement element,
                                   PsiElement originalElement,
                                   DocumentationComponent component,
                                   boolean onAutoUpdate) {
    cancelAndFetchDocInfo(component, new MyCollector(myProject, element, originalElement, null, false, onAutoUpdate));
  }

  @Override
  protected void doUpdateComponent(Editor editor, PsiFile psiFile, boolean requestFocus) {
    doUpdateComponent(editor, psiFile, requestFocus, false);
  }

  @Override
  protected void doUpdateComponent(Editor editor, PsiFile psiFile, boolean requestFocus, boolean onAutoUpdate) {
    showJavaDocInfo(editor, psiFile, requestFocus, null, onAutoUpdate);
  }

  @Override
  protected void doUpdateComponent(Editor editor, PsiFile psiFile) {
    doUpdateComponent(editor, psiFile, false);
  }

  @Override
  protected void doUpdateComponent(@NotNull PsiElement element) {
    doUpdateComponent(element, false);
  }

  @Override
  protected void doUpdateComponent(@NotNull PsiElement element, boolean onAutoUpdate) {
    showJavaDocInfo(element, element, null, onAutoUpdate);
  }

  @Override
  protected String getTitle(PsiElement element) {
    String title = SymbolPresentationUtil.getSymbolPresentableText(element);
    return title != null ? title : element.getText();
  }

  @Internal
  public static @Nullable Image getElementImage(@NotNull PsiElement element, @NotNull String imageSpec) {
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

  protected Editor getEditor() {
    return myEditor;
  }

  private @NotNull ActionCallback createActionCallback() {
    ActionCallback callback = new ActionCallback();
    myLastAction = callback;
    return callback;
  }

  @TestOnly
  public ActionCallback getLastAction() {
    return myLastAction;
  }

  @TestOnly
  public void setDocumentationComponent(DocumentationComponent documentationComponent) {
    myTestDocumentationComponent = documentationComponent;
  }

  protected abstract static class DocumentationCollector {
    private final CompletableFuture<? extends PsiElement> myElementFuture;
    final String ref;
    final boolean onAutoUpdate;
    final ActionCallback actionCallback;

    volatile DocumentationProvider provider;
    String effectiveUrl;

    DocumentationCollector(PsiElement element,
                           String effectiveUrl,
                           String ref,
                           DocumentationProvider provider,
                           boolean onAutoUpdate) {
      this(element, effectiveUrl, ref, null, provider, onAutoUpdate);
    }

    DocumentationCollector(PsiElement element,
                           String effectiveUrl,
                           String ref,
                           ActionCallback actionCallback,
                           DocumentationProvider provider,
                           boolean onAutoUpdate) {
      this(CompletableFuture.completedFuture(element), effectiveUrl, ref, actionCallback, provider, onAutoUpdate);
    }

    DocumentationCollector(@NotNull CompletableFuture<? extends PsiElement> elementFuture,
                           String effectiveUrl,
                           String ref,
                           ActionCallback actionCallback,
                           DocumentationProvider provider,
                           boolean onAutoUpdate) {
      myElementFuture = elementFuture;
      this.actionCallback = actionCallback;
      this.ref = ref;
      this.effectiveUrl = effectiveUrl;
      this.provider = provider;
      this.onAutoUpdate = onAutoUpdate;
    }

    public @Nullable PsiElement getElement(boolean wait) {
      try {
        return wait ? myElementFuture.get() : myElementFuture.getNow(null);
      }
      catch (Exception e) {
        LOG.debug("Cannot get target element", e);
        return null;
      }
    }

    abstract @Nullable @Nls String getDocumentation() throws Exception;
  }

  private static final class MyCollector extends DocumentationCollector {
    final Project project;
    final PsiElement originalElement;
    final boolean onHover;

    MyCollector(@NotNull Project project,
                @NotNull PsiElement element,
                PsiElement originalElement,
                String ref,
                boolean onHover,
                boolean onAutoUpdate) {
      this(project, element, originalElement, ref, null, onHover, onAutoUpdate);
    }

    MyCollector(@NotNull Project project,
                @NotNull PsiElement element,
                PsiElement originalElement,
                String ref,
                ActionCallback actionCallback,
                boolean onHover,
                boolean onAutoUpdate) {
      this(project, CompletableFuture.completedFuture(element), originalElement, ref, actionCallback, onHover, onAutoUpdate);
    }

    MyCollector(@NotNull Project project,
                @NotNull CompletableFuture<? extends PsiElement> elementSupplier,
                PsiElement originalElement,
                String ref,
                ActionCallback actionCallback,
                boolean onHover,
                boolean onAutoUpdate) {
      super(elementSupplier, null, ref, actionCallback, null, onAutoUpdate);
      this.project = project;
      this.originalElement = originalElement;
      this.onHover = onHover;
    }

    @Override
    public @Nullable @Nls String getDocumentation() {
      PsiElement element = getElement(true);
      if (element == null) {
        return null;
      }
      provider = ReadAction.compute(() -> getProviderFromElement(element, originalElement));
      LOG.debug("Using provider ", provider);

      if (provider instanceof ExternalDocumentationProvider) {
        List<String> urls = ReadAction.nonBlocking(
          () -> {
            SmartPsiElementPointer<?> originalElementPtr = element.getUserData(ORIGINAL_ELEMENT_KEY);
            PsiElement originalElement = originalElementPtr != null ? originalElementPtr.getElement() : null;
            return provider.getUrlFor(element, originalElement);
          }
        ).executeSynchronously();
        LOG.debug("External documentation URLs: ", urls);
        if (urls != null) {
          for (String url : urls) {
            String doc = ((ExternalDocumentationProvider)provider).fetchExternalDocumentation(
              project, element, Collections.singletonList(url), onHover);
            if (doc != null) {
              LOG.debug("Fetched documentation from ", url);
              effectiveUrl = url;
              return doc;
            }
          }
        }
      }

      //noinspection HardCodedStringLiteral T should be inferred to `@Nls String`
      return ReadAction.nonBlocking(() -> doGetDocumentation(element)).executeSynchronously();
    }

    private @Nullable @Nls String doGetDocumentation(@NotNull PsiElement element) {
      if (!element.isValid()) return null;
      SmartPsiElementPointer<?> originalPointer = element.getUserData(ORIGINAL_ELEMENT_KEY);
      PsiElement originalPsi = originalPointer != null ? originalPointer.getElement() : null;
      @Nls String doc = onHover ? provider.generateHoverDoc(element, originalPsi)
                                : provider.generateDoc(element, originalPsi);
      if (element instanceof PsiFileSystemItem) {
        @Nls String fileDoc = generateFileDoc((PsiFileSystemItem)element, doc == null);
        if (fileDoc != null) {
          return doc == null ? fileDoc : doc + fileDoc;
        }
      }
      return doc;
    }
  }

  @Internal
  public static @Nls @Nullable String generateFileDoc(@NotNull PsiFileSystemItem psiFile, boolean withUrl) {
    VirtualFile fileOrArchiveRoot = PsiUtilCore.getVirtualFile(psiFile);
    VirtualFile file;
    if (psiFile instanceof PsiDirectory) {
      if (fileOrArchiveRoot != null && fileOrArchiveRoot.getFileSystem() instanceof ArchiveFileSystem fileSystem
          && fileOrArchiveRoot.equals(fileSystem.getRootByEntry(fileOrArchiveRoot))) {
        file = fileSystem.getLocalByEntry(fileOrArchiveRoot);
      }
      else {
        //we don't show meaningful information for real directories
        return null;
      }
    }
    else {
      file = fileOrArchiveRoot;
    }
    File ioFile = file == null || !file.isInLocalFileSystem() ? null : VfsUtilCore.virtualToIoFile(file);
    BasicFileAttributes attr = null;
    try {
      attr = ioFile == null ? null : Files.readAttributes(Paths.get(ioFile.toURI()), BasicFileAttributes.class);
    }
    catch (Exception ignored) {
    }
    if (attr == null) return null;
    FileType type = file.getFileType();
    @Nls String typeName = type.getDisplayName();
    @Nls String languageName = type.isBinary() ? "" : psiFile.getLanguage().getDisplayName();
    var content = List.of(
      getVcsStatus(psiFile.getProject(), file),
      getScope(psiFile.getProject(), fileOrArchiveRoot),
      HtmlChunk.p().children(
        GRAYED_ELEMENT.addText(CodeInsightBundle.message("documentation.file.size.label")),
        HtmlChunk.nbsp(),
        HtmlChunk.text(StringUtil.formatFileSize(attr.size()))
      ),
      HtmlChunk.p().children(
        GRAYED_ELEMENT.addText(CodeInsightBundle.message("documentation.file.type.label")),
        HtmlChunk.nbsp(),
        HtmlChunk.text(typeName + (type.isBinary() || typeName.equals(languageName) ? "" : " (" + languageName + ")"))
      ),
      HtmlChunk.p().children(
        GRAYED_ELEMENT.addText(CodeInsightBundle.message("documentation.file.modification.datetime.label")),
        HtmlChunk.nbsp(),
        HtmlChunk.text(DateFormatUtil.formatDateTime(attr.lastModifiedTime().toMillis()))
      ),
      HtmlChunk.p().children(
        GRAYED_ELEMENT.addText(CodeInsightBundle.message("documentation.file.creation.datetime.label")),
        HtmlChunk.nbsp(),
        HtmlChunk.text(DateFormatUtil.formatDateTime(attr.creationTime().toMillis()))
      )
    );
    var result = !withUrl
                 ? List.of(CONTENT_ELEMENT.children(content))
                 : List.of(
                   HtmlChunk.text(file.getPresentableUrl()).wrapWith(PRE_ELEMENT).wrapWith(DEFINITION_ELEMENT),
                   CONTENT_ELEMENT.children(content)
                 );
    @Nls StringBuilder sb = new StringBuilder();
    for (HtmlChunk chunk : result) {
      chunk.appendTo(sb);
    }
    return sb.toString();
  }

  private static @Nls @NotNull HtmlChunk getScope(@NotNull Project project, @NotNull VirtualFile file) {
    FileColorManagerImpl colorManager = (FileColorManagerImpl)FileColorManager.getInstance(project);
    Color color = colorManager.getRendererBackground(file);
    if (color == null) {
      return HtmlChunk.empty();
    }
    for (NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(project)) {
      for (NamedScope scope : holder.getScopes()) {
        PackageSet packageSet = scope.getValue();
        String name = scope.getScopeId();
        if (packageSet instanceof PackageSetBase && ((PackageSetBase)packageSet).contains(file, project, holder) &&
            colorManager.getScopeColor(name) == color) {
          return HtmlChunk.p().children(
            GRAYED_ELEMENT.addText(CodeInsightBundle.message("documentation.file.scope.label")),
            HtmlChunk.nbsp(),
            HtmlChunk.span().attr("bgcolor", "#" + ColorUtil.toHex(color)).addText(scope.getPresentableName())
          );
        }
      }
    }
    return HtmlChunk.empty();
  }

  private static @NotNull HtmlChunk getVcsStatus(@NotNull Project project, @NotNull VirtualFile file) {
    FileStatus status = FileStatusManager.getInstance(project).getStatus(file);
    if (status == FileStatus.NOT_CHANGED || status == FileStatus.SUPPRESSED) {
      return HtmlChunk.empty();
    }
    HtmlChunk vcsText = HtmlChunk.text(status.getText());
    Color color = status.getColor();
    return HtmlChunk.p().children(
      GRAYED_ELEMENT.addText(CodeInsightBundle.message("documentation.file.vcs.status.label")),
      HtmlChunk.nbsp(),
      color == null ? vcsText : vcsText.wrapWith(HtmlChunk.span().attr("color", ColorUtil.toHex(color)))
    );
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

  @Internal
  @RequiresReadLock
  @RequiresBackgroundThread
  @Contract(pure = true)
  public final @Nls String decorate(
    @Nullable PsiElement element,
    @Nls @NotNull String text,
    @NlsSafe @Nullable String externalUrl,
    @Nullable DocumentationProvider provider
  ) {
    HtmlChunk locationInfo = getDefaultLocationInfo(element);
    return decorate(text, locationInfo, getExternalText(element, externalUrl, provider), null);
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  private static @Nullable HtmlChunk getDefaultLocationInfo(@Nullable PsiElement element) {
    if (element == null) return null;

    PsiFile file = element.getContainingFile();
    VirtualFile vfile = file == null ? null : file.getVirtualFile();
    if (vfile == null) return null;

    if (element.getUseScope() instanceof LocalSearchScope) return null;

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    Module module = fileIndex.getModuleForFile(vfile);

    if (module != null) {
      if (ModuleManager.getInstance(element.getProject()).getModules().length == 1) return null;
      return HtmlChunk.fragment(
        HtmlChunk.tag("icon").attr("src", "AllIcons.Nodes.Module"),
        HtmlChunk.nbsp(),
        HtmlChunk.text(module.getName())
      );
    }
    else {
      return fileIndex.getOrderEntriesForFile(vfile).stream()
        .filter(it -> it instanceof LibraryOrderEntry || it instanceof JdkOrderEntry)
        .findFirst()
        .map(it -> HtmlChunk.fragment(
          HtmlChunk.tag("icon").attr("src", "AllIcons.Nodes.PpLibFolder"),
          HtmlChunk.nbsp(),
          HtmlChunk.text(it.getPresentableName())
        ))
        .orElse(null);
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @Internal
  @Contract(pure = true)
  public static @Nls String decorate(@Nls @NotNull String text, @Nullable HtmlChunk location, @Nullable HtmlChunk links,
                                     @Nullable String downloadDocumentationActionLink) {
    text = StringUtil.replaceIgnoreCase(text, "</html>", "");
    text = StringUtil.replaceIgnoreCase(text, "</body>", "");

    var document = Jsoup.parse(text);
    if (document.select("." + CLASS_DEFINITION + ", ." + CLASS_CONTENT + ", ." + CLASS_SECTIONS).isEmpty()) {
      int bodyStart = findContentStart(text);
      if (bodyStart > 0) {
        text = text.substring(0, bodyStart) +
               CONTENT_START +
               text.substring(bodyStart) +
               CONTENT_END;
      }
      else {
        text = CONTENT_START + text + CONTENT_END;
      }
      // reparse the document
      document = Jsoup.parse(text);
    }

    DocumentationHtmlUtil.removeEmptySections$intellij_platform_lang_impl(document);

    if (downloadDocumentationActionLink != null) {
      document.body().appendChild(
        new Element("div")
          .addClass(CLASS_DOWNLOAD_DOCUMENTATION)
          .appendChildren(Arrays.asList(
            new Element("icon").attr("src", "AllIcons.Plugins.Downloads"),
            new TextNode(" "),
            new Element("a").attr("href", downloadDocumentationActionLink)
              .text(CodeInsightBundle.message("documentation.download.button.label"))
          )));
    }
    if (location != null) {
      document.body().append(getBottom().child(location).toString());
    }
    if (links != null) {
      document.body().append(getBottom().child(links).toString());
    }

    document.select("." + CLASS_DEFINITION + ", ." + CLASS_CONTENT + ", ." + CLASS_SECTIONS).forEach(
      div -> {
        var nextSibling = div.nextElementSibling();
        if (nextSibling == null) {
          return;
        }
        if (nextSibling.hasClass(CLASS_DEFINITION)
            || (nextSibling.hasClass(CLASS_CONTENT) && !div.hasClass(CLASS_SECTIONS))
            || (div.hasClass(CLASS_DEFINITION)
                && (
                  nextSibling.hasClass(CLASS_SECTIONS)
                  || nextSibling.hasClass(CLASS_BOTTOM)
                  || nextSibling.hasClass(CLASS_DOWNLOAD_DOCUMENTATION)
                ))) {
          div.after(new Element("hr"));
        }
      }
    );
    DocumentationHtmlUtil.addParagraphsIfNeeded$intellij_platform_lang_impl(
      document, "." + CLASS_CONTENT + ", table." + CLASS_SECTIONS + " td[valign=top]");
    DocumentationHtmlUtil.addExternalLinkIcons$intellij_platform_lang_impl(document);
    document.outputSettings().prettyPrint(false);
    return document.html();
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  private @Nullable HtmlChunk getExternalText(
    @Nullable PsiElement element,
    @NlsSafe @Nullable String externalUrl,
    @Nullable DocumentationProvider provider
  ) {
    if (element == null || provider == null) return null;

    PsiElement originalElement = getOriginalElement(element);
    if (!shouldShowExternalDocumentationLink(provider, element, originalElement)) {
      return null;
    }

    String title = getTitle(element);
    if (externalUrl == null) {
      List<String> urls = provider.getUrlFor(element, originalElement);
      if (urls == null) {
        return null;
      }
      HtmlChunk links = getExternalLinks(title, urls);
      if (links != null) {
        return links;
      }
    }
    else {
      HtmlChunk link = getLink(title, externalUrl);
      if (link != null) return link;
    }

    return getGenericExternalDocumentationLink(title);
  }

  public static @Nullable HtmlChunk getExternalLinks(@Nls String title, @NotNull List<String> urls) {
    List<HtmlChunk> result = new SmartList<>();
    for (String url : urls) {
      HtmlChunk link = getLink(title, url);
      if (link == null) {
        return null;
      }
      else {
        result.add(link);
      }
    }
    HtmlBuilder builder = new HtmlBuilder();
    builder.appendWithSeparators(HtmlChunk.p(), result);
    return builder.toFragment();
  }

  public static @NotNull HtmlChunk getGenericExternalDocumentationLink(@Nullable String title) {
    String linkText = CodeInsightBundle.message("html.external.documentation.component.header", title, title == null ? 0 : 1);
    return HtmlChunk.link("external_doc", linkText).child(EXTERNAL_LINK_ICON);
  }

  @Internal
  public static @Nullable HtmlChunk getLink(@Nls String title, @NlsSafe String url) {
    String hostname = getHostname(url);
    if (hostname == null) {
      return null;
    }
    String linkText;
    if (title == null) {
      linkText = CodeInsightBundle.message("link.text.documentation.on", hostname);
    }
    else {
      linkText = CodeInsightBundle.message("link.text.element.documentation.on.url", title, hostname);
    }
    return HtmlChunk.link(url, linkText);
  }

  static boolean shouldShowExternalDocumentationLink(DocumentationProvider provider,
                                                     PsiElement element,
                                                     PsiElement originalElement) {
    if (provider instanceof CompositeDocumentationProvider) {
      List<DocumentationProvider> providers = ((CompositeDocumentationProvider)provider).getProviders();
      for (DocumentationProvider p : providers) {
        if (p instanceof ExternalDocumentationHandler) {
          return ((ExternalDocumentationHandler)p).canHandleExternal(element, originalElement);
        }
      }
    }
    else if (provider instanceof ExternalDocumentationHandler) {
      return ((ExternalDocumentationHandler)provider).canHandleExternal(element, originalElement);
    }
    return true;
  }

  private static String getHostname(String url) {
    try {
      return new URL(url).toURI().getHost();
    }
    catch (URISyntaxException | MalformedURLException ignored) {
    }
    return null;
  }

  private static int findContentStart(String text) {
    int index = StringUtil.indexOfIgnoreCase(text, "<body>", 0);
    if (index >= 0) return index + 6;
    index = StringUtil.indexOfIgnoreCase(text, "</head>", 0);
    if (index >= 0) return index + 7;
    index = StringUtil.indexOfIgnoreCase(text, "</style>", 0);
    if (index >= 0) return index + 8;
    index = StringUtil.indexOfIgnoreCase(text, "<html>", 0);
    if (index >= 0) return index + 6;
    return -1;
  }

  private static @NotNull HtmlChunk.Element getBottom() {
    return HtmlChunk.div().setClass(CLASS_BOTTOM);
  }
}
