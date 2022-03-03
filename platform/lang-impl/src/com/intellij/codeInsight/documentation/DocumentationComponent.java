// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.BaseNavigateToSourceAction;
import com.intellij.ide.actions.ExternalJavaDocAction;
import com.intellij.ide.actions.WindowAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ide.DocumentationUtil;
import com.intellij.lang.documentation.impl.DocumentationRequest;
import com.intellij.lang.documentation.impl.ImplKt;
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget;
import com.intellij.model.Pointer;
import com.intellij.navigation.TargetPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.reference.SoftReference;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.MathUtil;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.documentation.QuickDocUtil.isDocumentationV2Enabled;

public class DocumentationComponent extends JPanel implements Disposable, DataProvider, WidthBasedLayout {
  private static final Logger LOG = Logger.getInstance(DocumentationComponent.class);
  static final DataProvider HELP_DATA_PROVIDER =
    dataId -> PlatformCoreDataKeys.HELP_ID.is(dataId)
              ? "reference.toolWindows.Documentation"
              : null;

  public static final ColorKey COLOR_KEY = EditorColors.DOCUMENTATION_COLOR;
  public static final Color SECTION_COLOR = Gray.get(0x90);

  private static final int PREFERRED_HEIGHT_MAX_EM = 10;
  static final JBDimension MIN_DEFAULT = new JBDimension(300, 36);
  static final JBDimension MAX_DEFAULT = new JBDimension(950, 500);

  private final ExternalDocAction myExternalDocAction;

  private DocumentationManager myManager;
  private SmartPsiElementPointer<PsiElement> myElement;
  private long myModificationCount;

  private static final String QUICK_DOC_FONT_SIZE_V1_PROPERTY = "quick.doc.font.size"; // 2019.3 or earlier versions
  private static final String QUICK_DOC_FONT_SIZE_V2_PROPERTY = "quick.doc.font.size.v2"; // 2020.1 EAP
  private static final String QUICK_DOC_FONT_SIZE_V3_PROPERTY = "quick.doc.font.size.v3"; // 2020.1 or later versions

  private final Stack<Context> myBackStack = new Stack<>();
  private final Stack<Context> myForwardStack = new Stack<>();
  private final List<? extends AnAction> myNavigationActions;
  private final ActionToolbarImpl myToolBar;
  private volatile boolean myIsEmpty;
  private boolean mySizeTrackerRegistered;
  private String myExternalUrl;
  private DocumentationProvider myProvider;
  private Reference<Component> myReferenceComponent;

  private Runnable myToolwindowCallback;
  private final ActionButton myCorner;

  private final JScrollPane myScrollPane;
  private final DocumentationHintEditorPane myEditorPane;
  private final JComponent myControlPanel;
  private boolean myControlPanelVisible;
  private final DocumentationLinkHandler myLinkHandler;
  private final boolean myStoreSize;
  private boolean myManuallyResized;

  private AbstractPopup myHint;

  /**
   * @deprecated This method is executed on the EDT, but at the same time it works with PSI.
   * To migrate: compute pointer and presentation on a BG thread, then transfer to the EDT
   * and call {@link DocumentationUtil#documentationComponent(Project, Pointer, TargetPresentation, Disposable)} with the prepared data.
   */
  @Deprecated
  public static @NotNull JComponent createAndFetch(
    @NotNull Project project,
    @NotNull PsiElement element,
    @NotNull Disposable disposable
  ) {
    if (isDocumentationV2Enabled() && Registry.is("documentation.v2.component")) {
      DocumentationRequest request;
      try (AccessToken ignored = SlowOperations.allowSlowOperations("old API fallback")) {
        request = ImplKt.documentationRequest(new PsiElementDocumentationTarget(project, element));
      }
      return DocumentationUtil.documentationComponent(project, request, disposable);
    }
    DocumentationManager manager = DocumentationManager.getInstance(project);
    DocumentationComponent component = new DocumentationComponent(manager);
    Disposer.register(disposable, component);
    manager.fetchDocInfo(element, component);
    return component;
  }

  public DocumentationComponent(DocumentationManager manager) {
    this(manager, true);
  }

  public DocumentationComponent(DocumentationManager manager, boolean storeSize) {
    myManager = manager;
    myIsEmpty = true;
    myStoreSize = storeSize;

    myScrollPane = new DocumentationScrollPane();
    myEditorPane = new DocumentationHintEditorPane(
      manager.getProject(),
      DocumentationScrollPane.keyboardActions(myScrollPane),
      this::getElementImage
    );
    myScrollPane.setViewportView(myEditorPane);
    myScrollPane.addMouseWheelListener(new FontSizeMouseWheelListener(myEditorPane::applyFontProps));

    setLayout(new BorderLayout());

    //add(myScrollPane, BorderLayout.CENTER);
    setOpaque(true);

    BackAction back = new BackAction();
    ForwardAction forward = new ForwardAction();
    EditDocumentationSourceAction edit = new EditDocumentationSourceAction();
    myNavigationActions = List.of(back, forward, edit);

    List<AnAction> navigationAndAdditionalActions = new ArrayList<>(myNavigationActions);
    for (DocumentationActionProvider provider: DocumentationActionProvider.EP_NAME.getExtensions()) {
      navigationAndAdditionalActions.addAll(provider.additionalActions(this));
    }

    try {
      String backKey = ScreenReader.isActive() ? "alt LEFT" : "LEFT";
      CustomShortcutSet backShortcutSet = new CustomShortcutSet(KeyboardShortcut.fromString(backKey),
                                                                KeymapUtil.parseMouseShortcut("button4"));

      String forwardKey = ScreenReader.isActive() ? "alt RIGHT" : "RIGHT";
      CustomShortcutSet forwardShortcutSet = new CustomShortcutSet(KeyboardShortcut.fromString(forwardKey),
                                                                   KeymapUtil.parseMouseShortcut("button5"));
      back.registerCustomShortcutSet(backShortcutSet, this);
      forward.registerCustomShortcutSet(forwardShortcutSet, this);
      // mouse actions are checked only for exact component over which click was performed,
      // so we need to register shortcuts for myEditorPane as well
      back.registerCustomShortcutSet(backShortcutSet, myEditorPane);
      forward.registerCustomShortcutSet(forwardShortcutSet, myEditorPane);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }

    myExternalDocAction = new ExternalDocAction();
    myExternalDocAction.registerCustomShortcutSet(CustomShortcutSet.fromString("UP"), this);
    myExternalDocAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet(), myEditorPane);
    edit.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    PopupHandler popupHandler = new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu contextMenu = ((ActionManagerImpl)ActionManager.getInstance()).createActionPopupMenu(
          ActionPlaces.JAVADOC_TOOLBAR,
          new DefaultActionGroup(navigationAndAdditionalActions),
          new MenuItemPresentationFactory(true)
        );
        contextMenu.getComponent().show(comp, x, y);
      }
    };
    myEditorPane.addMouseListener(popupHandler);
    Disposer.register(this, () -> myEditorPane.removeMouseListener(popupHandler));

    myLinkHandler = DocumentationLinkHandler.createAndRegister(
      myEditorPane, this,
      href -> manager.navigateByLink(this, null, href)
    );
    for (AnAction action : myLinkHandler.createLinkActions()) {
      action.registerCustomShortcutSet(this, this);
    }

    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.addAll(navigationAndAdditionalActions);
    toolbarActions.addAction(new ShowAsToolwindowAction()).setAsSecondary(true);
    toolbarActions.addAction(new ToggleShowDocsOnHoverAction()).setAsSecondary(true);
    toolbarActions.addAction(new MyShowSettingsAction()).setAsSecondary(true);
    toolbarActions.addAction(new ShowToolbarAction()).setAsSecondary(true);
    toolbarActions.addAction(new ShowPopupAutomaticallyAction()).setAsSecondary(true);
    toolbarActions.addAction(new RestoreDefaultSizeAction()).setAsSecondary(true);
    myToolBar = new ActionToolbarImpl(ActionPlaces.JAVADOC_TOOLBAR, toolbarActions, true);
    myToolBar.setSecondaryActionsIcon(AllIcons.Actions.More, true);
    myToolBar.setTargetComponent(this);

    JLayeredPane layeredPane = new JBLayeredPane() {
      @Override
      public void doLayout() {
        Rectangle r = getBounds();
        for (Component component : getComponents()) {
          if (component instanceof JScrollPane) {
            component.setBounds(0, 0, r.width, r.height);
          }
          else {
            Dimension d = component.getPreferredSize();
            component.setBounds(r.width - d.width - 2, r.height - d.height - 7, d.width, d.height);
          }
        }
      }

      @Override
      public Dimension getPreferredSize() {
        Dimension size = myScrollPane.getPreferredSize();
        if (myHint == null && myManager != null && myManager.myToolWindow == null) {
          int em = myEditorPane.getFont().getSize();
          int prefHeightMax = PREFERRED_HEIGHT_MAX_EM * em;
          return new Dimension(size.width, Math.min(prefHeightMax,
                                                    size.height + (needsToolbar() ? myControlPanel.getPreferredSize().height : 0)));
        }
        return size;
      }
    };
    layeredPane.add(myScrollPane);
    layeredPane.setLayer(myScrollPane, 0);

    DefaultActionGroup gearActions = new MyGearActionGroup();
    ShowAsToolwindowAction showAsToolwindowAction = new ShowAsToolwindowAction();
    gearActions.add(showAsToolwindowAction);
    gearActions.add(new ToggleShowDocsOnHoverAction());
    gearActions.add(new MyShowSettingsAction());
    gearActions.add(new ShowToolbarAction());
    gearActions.add(new ShowPopupAutomaticallyAction());
    gearActions.add(new RestoreDefaultSizeAction());
    gearActions.addSeparator();
    gearActions.addAll(navigationAndAdditionalActions);
    Presentation presentation = new Presentation();
    presentation.setIcon(AllIcons.Actions.More);
    presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);
    myCorner = new ActionButton(gearActions, presentation, ActionPlaces.UNKNOWN, new Dimension(20, 20)) {
      @Override
      protected DataContext getDataContext() {
        return DataManager.getInstance().getDataContext(myCorner);
      }
    };
    myCorner.setNoIconsInPopup(true);
    myScrollPane.setLayout(new CornerAwareScrollPaneLayout(myCorner));
    showAsToolwindowAction.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("QuickJavaDoc"), myCorner);
    layeredPane.add(myCorner);
    layeredPane.setLayer(myCorner, JLayeredPane.POPUP_LAYER);
    add(layeredPane, BorderLayout.CENTER);

    myControlPanel = myToolBar.getComponent();
    myControlPanel.setBorder(IdeBorderFactory.createBorder(UIUtil.getTooltipSeparatorColor(), SideBorder.BOTTOM));
    myControlPanelVisible = false;

    if (myHint != null) {
      Disposer.register(myHint, this);
    }
    else if (myManager.myToolWindow != null) {
      Disposer.register(myManager.myToolWindow.getContentManager(), this);
    }
    myEditorPane.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, HELP_DATA_PROVIDER);
    myScrollPane.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, HELP_DATA_PROVIDER);

    updateControlState();
  }

  @Override
  public void setBackground(Color color) {
    super.setBackground(color);
    if (myEditorPane != null) myEditorPane.setBackground(color);
    if (myControlPanel != null) myControlPanel.setBackground(color);
  }

  public List<? extends AnAction> getNavigationActions() {
    return myNavigationActions;
  }

  public AnAction getFontSizeAction() {
    return new MyShowSettingsAction();
  }

  public void removeCornerMenu() {
    myCorner.setVisible(false);
  }

  public void setToolwindowCallback(Runnable callback) {
    myToolwindowCallback = callback;
  }

  public void showExternalDoc() {
    DataContext dataContext = DataManager.getInstance().getDataContext(this);
    myExternalDocAction.actionPerformed(AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext));
  }

  @Override
  public boolean requestFocusInWindow() {
    // With a screen reader active, set the focus directly to the editor because
    // it makes it easier for users to read/navigate the documentation contents.
    if (ScreenReader.isActive()) {
      return myEditorPane.requestFocusInWindow();
    }
    else {
      return myScrollPane.requestFocusInWindow();
    }
  }

  @Override
  public void requestFocus() {
    // With a screen reader active, set the focus directly to the editor because
    // it makes it easier for users to read/navigate the documentation contents.
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      if (ScreenReader.isActive()) {
        IdeFocusManager.getGlobalInstance().requestFocus(myEditorPane, true);
      }
      else {
        IdeFocusManager.getGlobalInstance().requestFocus(myScrollPane, true);
      }
    });
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (DocumentationManager.SELECTED_QUICK_DOC_TEXT.is(dataId)) {
      // Javadocs often contain &nbsp; symbols (non-breakable white space). We don't want to copy them as is and replace
      // with raw white spaces. See IDEA-86633 for more details.
      String selectedText = myEditorPane.getSelectedText();
      return selectedText == null ? null : selectedText.replace((char)160, ' ');
    }

    return null;
  }

  @NotNull
  public static FontSize getQuickDocFontSize() {
    FontSize v1 = readFontSizeFromSettings(QUICK_DOC_FONT_SIZE_V1_PROPERTY, true);
    FontSize v2 = readFontSizeFromSettings(QUICK_DOC_FONT_SIZE_V2_PROPERTY, true);
    FontSize v3 = readFontSizeFromSettings(QUICK_DOC_FONT_SIZE_V3_PROPERTY, false);
    if (v3 != null) {
      return v3;
    }
    if (v2 != null) {
      v3 = migrateV2ToV3(v2);
      setQuickDocFontSize(v3);
      return v3;
    }
    if (v1 != null) {
      v3 = migrateV2ToV3(migrateV1ToV2(v1));
      setQuickDocFontSize(v3);
      return v3;
    }
    return FontSize.SMALL;
  }

  private static @NotNull FontSize migrateV1ToV2(@NotNull FontSize size) {
    return size == FontSize.X_LARGE ? FontSize.XX_LARGE : size == FontSize.LARGE ? FontSize.X_LARGE : size;
  }

  private static @NotNull FontSize migrateV2ToV3(@NotNull FontSize size) {
    return size == FontSize.X_SMALL ? FontSize.XX_SMALL : size == FontSize.SMALL ? FontSize.X_SMALL : size;
  }

  @Nullable
  private static FontSize readFontSizeFromSettings(@NotNull String propertyName, boolean unsetAfterReading) {
    String strValue = PropertiesComponent.getInstance().getValue(propertyName);
    if (strValue != null) {
      if (unsetAfterReading) PropertiesComponent.getInstance().unsetValue(propertyName);
      try {
        return FontSize.valueOf(strValue);
      }
      catch (IllegalArgumentException ignored) {}
    }
    return null;
  }

  public static void setQuickDocFontSize(@NotNull FontSize fontSize) {
    PropertiesComponent.getInstance().setValue(QUICK_DOC_FONT_SIZE_V3_PROPERTY, fontSize.toString());
  }

  public boolean isEmpty() {
    return myIsEmpty;
  }

  public void startWait() {
    myIsEmpty = true;
  }

  private void setControlPanelVisible() {
    if (myControlPanelVisible) return;
    add(myControlPanel, BorderLayout.NORTH);
    myControlPanelVisible = true;
  }

  public void setHint(JBPopup hint) {
    myHint = (AbstractPopup)hint;
    PopupDragListener.dragPopupByComponent(hint, myControlPanel);
    myEditorPane.setHint(hint);
  }

  public JBPopup getHint() {
    return myHint;
  }

  public JComponent getComponent() {
    return myEditorPane;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement != null ? myElement.getElement() : null;
  }

  private void setElement(SmartPsiElementPointer<PsiElement> element) {
    myElement = element;
    myModificationCount = getCurrentModificationCount();
  }

  public boolean isUpToDate() {
    return getElement() != null && myModificationCount == getCurrentModificationCount();
  }

  private long getCurrentModificationCount() {
    return myElement != null ? PsiModificationTracker.SERVICE.getInstance(myElement.getProject()).getModificationCount() : -1;
  }

  public void setText(@NotNull @Nls String text, @Nullable PsiElement element, @Nullable DocumentationProvider provider) {
    setData(element, text, null, null, provider);
  }

  public void replaceText(@NotNull @Nls String text, @Nullable PsiElement element) {
    PsiElement current = getElement();
    if (current == null || !current.getManager().areElementsEquivalent(current, element)) return;
    restoreContext(saveContext().withText(text));
  }

  public void clearHistory() {
    myForwardStack.clear();
    myBackStack.clear();
  }

  private void pushHistory() {
    if (myElement != null) {
      myBackStack.push(saveContext());
      myForwardStack.clear();
    }
  }

  public void setData(@Nullable PsiElement element,
                      @NotNull @Nls String text,
                      @Nullable String effectiveExternalUrl,
                      @Nullable String ref,
                      @Nullable DocumentationProvider provider) {
    pushHistory();
    myExternalUrl = effectiveExternalUrl;
    myProvider = provider;

    SmartPsiElementPointer<PsiElement> pointer = null;
    if (element != null && element.isValid()) {
      pointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    }
    setDataInternal(pointer, text, new Rectangle(0, 0), ref);
  }

  private void setDataInternal(@Nullable SmartPsiElementPointer<PsiElement> element,
                               @NotNull @Nls String text,
                               @NotNull Rectangle viewRect,
                               @Nullable String ref) {
    myIsEmpty = false;
    if (myManager == null) return;

    myEditorPane.setText(text);
    setElement(element);
    if (element != null && element.getElement() != null) {
      myManager.updateToolWindowTabName(element.getElement());
    }

    showHint(viewRect, ref);

    if (myManager != null) {
      myManager.getProject().getMessageBus().syncPublisher(DocumentationComponentListener.TOPIC).onComponentDataChanged();
    }
  }

  protected void showHint(@NotNull Rectangle viewRect, @Nullable String ref) {
    String refToUse;
    Rectangle viewRectToUse;
    if (DocumentationManagerProtocol.KEEP_SCROLLING_POSITION_REF.equals(ref)) {
      refToUse = null;
      viewRectToUse = myScrollPane.getViewport().getViewRect();
    }
    else {
      refToUse = ref;
      viewRectToUse = viewRect;
    }

    updateControlState();

    myLinkHandler.highlightLink(-1);

    myEditorPane.applyFontProps(getQuickDocFontSize());

    showHint();

    SwingUtilities.invokeLater(() -> {
      myEditorPane.scrollRectToVisible(viewRectToUse); // if ref is defined but is not found in document, this provides a default location
      if (refToUse != null) {
        UIUtil.scrollToReference(myEditorPane, refToUse);
      }
      else if (ScreenReader.isActive()) {
        myEditorPane.setCaretPosition(0);
      }
    });
  }

  protected void showHint() {
    if (myHint == null) return;

    setHintSize();

    DataContext dataContext = getDataContext();
    PopupPositionManager.positionPopupInBestPosition(myHint, myManager.getEditor(), dataContext,
                                                     PopupPositionManager.Position.RIGHT, PopupPositionManager.Position.LEFT);

    Window window = myHint.getPopupWindow();
    if (window != null) window.setFocusableWindowState(true);

    registerSizeTracker();
  }

  private DataContext getDataContext() {
    Component referenceComponent;
    if (myReferenceComponent == null) {
      referenceComponent = IdeFocusManager.getInstance(myManager.myProject).getFocusOwner();
      myReferenceComponent = new WeakReference<>(referenceComponent);
    }
    else {
      referenceComponent = SoftReference.dereference(myReferenceComponent);
      if (referenceComponent == null || ! referenceComponent.isShowing()) referenceComponent = myHint.getComponent();
    }
    return DataManager.getInstance().getDataContext(referenceComponent);
  }

  private void setHintSize() {
    Dimension hintSize = myManuallyResized ? ensureMinimum(myHint.getContentSize())
                                           : getDefaultHintSize();
    myHint.setSize(hintSize);
  }

  private @NotNull Dimension getDefaultHintSize() {
    if (myHint.getDimensionServiceKey() == null) {
      return getOptimalSize();
    }
    Dimension storedSize = DimensionService.getInstance().getSize(DocumentationManager.NEW_JAVADOC_LOCATION_AND_SIZE, myManager.myProject);
    if (storedSize != null) {
      return ensureMinimum(storedSize);
    }
    return new Dimension(MIN_DEFAULT);
  }

  private static @NotNull Dimension ensureMinimum(@NotNull Dimension hintSize) {
    return new Dimension(
      Math.max(hintSize.width, MIN_DEFAULT.width),
      Math.max(hintSize.height, MIN_DEFAULT.height)
    );
  }

  private @NotNull Dimension getOptimalSize() {
    int width = getPreferredWidth();
    int height = getPreferredHeight(width);
    return new Dimension(width, height);
  }

  @Override
  public int getPreferredWidth() {
    int minWidth = JBUIScale.scale(300);
    int maxWidth = getPopupAnchor() != null ? JBUIScale.scale(435) : JBUIScale.scale(MAX_DEFAULT.width);

    int width = myEditorPane.getPreferredWidth();
    Insets insets = getInsets();
    return MathUtil.clamp(width, minWidth, maxWidth) + insets.left + insets.right;
  }

  @Override
  public int getPreferredHeight(int width) {
    myEditorPane.setBounds(0, 0, width, MAX_DEFAULT.height);
    Dimension preferredSize = myEditorPane.getPreferredSize();

    int height = preferredSize.height + (needsToolbar() ? myControlPanel.getPreferredSize().height : 0);
    JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
    int reservedForScrollBar = width < preferredSize.width && scrollBar.isOpaque() ? scrollBar.getPreferredSize().height : 0;
    Insets insets = getInsets();
    return MathUtil.clamp(height, MIN_DEFAULT.height, MAX_DEFAULT.height) + insets.top + insets.bottom + reservedForScrollBar;
  }

  private Component getPopupAnchor() {
    LookupEx lookup = myManager == null ? null : LookupManager.getActiveLookup(myManager.getEditor());

    if (lookup != null && lookup.getCurrentItem() != null && lookup.getComponent().isShowing()) {
      return lookup.getComponent();
    }
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    JBPopup popup = PopupUtil.getPopupContainerFor(focusOwner);
    if (popup != null && popup != myHint && !popup.isDisposed()) {
      return popup.getContent();
    }
    return null;
  }

  private void registerSizeTracker() {
    AbstractPopup hint = myHint;
    if (hint == null || mySizeTrackerRegistered) return;
    mySizeTrackerRegistered = true;
    hint.addResizeListener(this::onManualResizing, this);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
        if (action instanceof WindowAction) onManualResizing();
      }
    });
  }

  private void onManualResizing() {
    myManuallyResized = true;
    if (myStoreSize && myHint != null) {
      myHint.setDimensionServiceKey(DocumentationManager.NEW_JAVADOC_LOCATION_AND_SIZE);
      myHint.storeDimensionSize();
    }
  }

  private void goBack() {
    if (myBackStack.isEmpty()) return;
    Context context = myBackStack.pop();
    myForwardStack.push(saveContext());
    restoreContext(context);
  }

  private void goForward() {
    if (myForwardStack.isEmpty()) return;
    Context context = myForwardStack.pop();
    myBackStack.push(saveContext());
    restoreContext(context);
  }

  private Context saveContext() {
    Rectangle rect = myScrollPane.getViewport().getViewRect();
    return new Context(myElement, myEditorPane.getText(), myExternalUrl, myProvider, rect, myLinkHandler.getHighlightedLink());
  }

  private void restoreContext(@NotNull Context context) {
    myExternalUrl = context.externalUrl;
    myProvider = context.provider;
    setDataInternal(context.element, context.text, context.viewRect, null);
    myLinkHandler.highlightLink(context.highlightedLink);

    if (myManager != null) {
      PsiElement element  = context.element.getElement();
      if (element != null) {
        myManager.updateToolWindowTabName(element);
      }
    }
  }

  private void updateControlState() {
    if (needsToolbar()) {
      myToolBar.updateActionsImmediately(); // update faster
      setControlPanelVisible();
      removeCornerMenu();
    }
    else {
      myControlPanelVisible = false;
      remove(myControlPanel);
      if (myManager.myToolWindow != null) return;
      myCorner.setVisible(true);
    }
  }

  public boolean needsToolbar() {
    return myManager.myToolWindow == null && Registry.is("documentation.show.toolbar");
  }

  private @Nullable Image getElementImage(@NotNull String imageSpec) {
    PsiElement element = getElement();
    return element == null ? null : DocumentationManager.getElementImage(element, imageSpec);
  }

  private static class MyGearActionGroup extends DefaultActionGroup implements HintManagerImpl.ActionToIgnore {
    MyGearActionGroup(AnAction @NotNull ... actions) {
      super(actions);
      setPopup(true);
    }
  }

  protected class BackAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    BackAction() {
      super(CodeInsightBundle.messagePointer("javadoc.action.back"), AllIcons.Actions.Back);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      goBack();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(!myBackStack.isEmpty());
      if (!isToolbar(e)) {
        presentation.setVisible(presentation.isEnabled());
      }
    }
  }

  protected class ForwardAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    ForwardAction() {
      super(CodeInsightBundle.messagePointer("javadoc.action.forward"), AllIcons.Actions.Forward);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      goForward();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(!myForwardStack.isEmpty());
      if (!isToolbar(e)) {
        presentation.setVisible(presentation.isEnabled());
      }
    }
  }

  protected final class EditDocumentationSourceAction extends BaseNavigateToSourceAction {

    private EditDocumentationSourceAction() {
      super(true);
      getTemplatePresentation().setIcon(AllIcons.Actions.EditSource);
      getTemplatePresentation().setText(CodeInsightBundle.messagePointer("action.presentation.DocumentationComponent.text"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      super.actionPerformed(e);
      JBPopup hint = myHint;
      if (hint != null && hint.isVisible()) {
        hint.cancel();
      }
    }

    @Override
    protected Navigatable @Nullable [] getNavigatables(DataContext dataContext) {
      SmartPsiElementPointer<PsiElement> element = myElement;
      if (element != null) {
        PsiElement psiElement = element.getElement();
        return psiElement instanceof Navigatable ? new Navigatable[]{(Navigatable)psiElement} : null;
      }
      return null;
    }
  }

  private static boolean isToolbar(@NotNull AnActionEvent e) {
    return ActionPlaces.JAVADOC_TOOLBAR.equals(e.getPlace());
  }


  private final class ExternalDocAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    private ExternalDocAction() {
      super(CodeInsightBundle.message("javadoc.action.view.external"), null, AllIcons.Actions.PreviousOccurence);
      setShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myElement == null) {
        return;
      }

      PsiElement element = myElement.getElement();
      PsiElement originalElement = DocumentationManager.getOriginalElement(element);

      ExternalJavaDocAction.showExternalJavadoc(element, originalElement, myExternalUrl, e.getDataContext());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(hasExternalDoc());
    }
  }

  private boolean hasExternalDoc() {
    boolean enabled = false;
    if (myElement != null && myProvider != null) {
      PsiElement element = myElement.getElement();
      PsiElement originalElement = DocumentationManager.getOriginalElement(element);
      enabled = element != null && CompositeDocumentationProvider.hasUrlsFor(myProvider, element, originalElement);
    }
    return enabled;
  }

  public @Nls String getText() {
    return myEditorPane.getText();
  }

  public @Nls String getDecoratedText() {
    return myEditorPane.getText();
  }

  @Override
  public void dispose() {
    myEditorPane.getCaret().setVisible(false); // Caret, if blinking, has to be deactivated.
    myBackStack.clear();
    myForwardStack.clear();
    myElement = null;
    myManager = null;
    myHint = null;
  }

  private static class Context {
    final SmartPsiElementPointer<PsiElement> element;
    final @Nls String text;
    final String externalUrl;
    final DocumentationProvider provider;
    final Rectangle viewRect;
    final int highlightedLink;

    Context(SmartPsiElementPointer<PsiElement> element,
            @Nls String text,
            String externalUrl,
            DocumentationProvider provider,
            Rectangle viewRect,
            int highlightedLink) {
      this.element = element;
      this.text = text;
      this.externalUrl = externalUrl;
      this.provider = provider;
      this.viewRect = viewRect;
      this.highlightedLink = highlightedLink;
    }

    @NotNull
    Context withText(@NotNull @Nls String text) {
      return new Context(element, text, externalUrl, provider, viewRect, highlightedLink);
    }
  }

  private class MyShowSettingsAction extends AnAction implements HintManagerImpl.ActionToIgnore {

    MyShowSettingsAction() {
      super(CodeInsightBundle.message("javadoc.adjust.font.size"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DocFontSizePopup.show(DocumentationComponent.this, size -> {
        myEditorPane.applyFontProps(size);
        // resize popup according to new font size, if user didn't set popup size manually
        if (!myManuallyResized && myHint != null && myHint.getDimensionServiceKey() == null) showHint();
      });
    }
  }

  protected class ShowToolbarAction extends ToggleAction implements HintManagerImpl.ActionToIgnore {
    ShowToolbarAction() {
      super(CodeInsightBundle.messagePointer("javadoc.show.toolbar"));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return Registry.get("documentation.show.toolbar").asBoolean();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      Registry.get("documentation.show.toolbar").setValue(state);
      updateControlState();
      showHint();
    }
  }

  protected static class ShowPopupAutomaticallyAction extends ToggleAction implements HintManagerImpl.ActionToIgnore {
    ShowPopupAutomaticallyAction() {
      super(CodeInsightBundle.messagePointer("javadoc.show.popup.automatically"));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      var project = e.getProject();
      e.getPresentation().setEnabledAndVisible(project != null && LookupManager.getInstance(project).getActiveLookup() != null);
      super.update(e);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO = state;
    }
  }

  protected class ShowAsToolwindowAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    ShowAsToolwindowAction() {
      super(CodeInsightBundle.messagePointer("javadoc.open.as.tool.window"));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      if (myManager == null) {
        presentation.setEnabledAndVisible(false);
      }
      else {
        presentation.setIcon(ToolWindowManager.getInstance(myManager.myProject).getLocationIcon(ToolWindowId.DOCUMENTATION, EmptyIcon.ICON_16));
        presentation.setEnabledAndVisible(myToolwindowCallback != null);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myToolwindowCallback.run();
    }
  }

  protected class RestoreDefaultSizeAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    RestoreDefaultSizeAction() {
      super(CodeInsightBundle.messagePointer("javadoc.restore.size"));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myHint != null && (myManuallyResized || myHint.getDimensionServiceKey() != null));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myManuallyResized = false;
      if (myStoreSize) {
        DimensionService.getInstance().setSize(DocumentationManager.NEW_JAVADOC_LOCATION_AND_SIZE, null, myManager.myProject);
        myHint.setDimensionServiceKey(null);
      }
      showHint();
    }
  }
}
