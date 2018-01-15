// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.ElementLocationUtil;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.BaseNavigateToSourceAction;
import com.intellij.ide.actions.ExternalJavaDocAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SideBorder;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import java.util.HashMap;
import com.intellij.util.ui.FontInfo;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;

public class DocumentationComponent extends JPanel implements Disposable, DataProvider {

  private static final Logger LOG = Logger.getInstance(DocumentationComponent.class);

  private static final Color DOCUMENTATION_COLOR = new JBColor(new Color(0xf6f6f6), new Color(0x4d4f51));
  public static final ColorKey COLOR_KEY = ColorKey.createColorKey("DOCUMENTATION_COLOR", DOCUMENTATION_COLOR);

  private static final Highlighter.HighlightPainter LINK_HIGHLIGHTER = new LinkHighlighter();
  @NonNls private static final String DOCUMENTATION_TOPIC_ID = "reference.toolWindows.Documentation";

  private static final int PREFERRED_WIDTH_EM = 37;
  private static final int PREFERRED_HEIGHT_MIN_EM = 7;
  private static final int PREFERRED_HEIGHT_MAX_EM = 20;

  private DocumentationManager myManager;
  private SmartPsiElementPointer myElement;
  private long myModificationCount;

  public static final String QUICK_DOC_FONT_SIZE_PROPERTY = "quick.doc.font.size";

  private final Stack<Context> myBackStack = new Stack<>();
  private final Stack<Context> myForwardStack = new Stack<>();
  private final ActionToolbar myToolBar;
  private volatile boolean myIsEmpty;
  private boolean myIsShown;
  private final JLabel myElementLabel;
  private JSlider myFontSizeSlider;
  private final JComponent mySettingsPanel;
  private boolean myIgnoreFontSizeSliderChange;
  private String myEffectiveExternalUrl;
  private final MyDictionary<String, Image> myImageProvider = new MyDictionary<String, Image>() {
    @Override
    public Image get(Object key) {
      if (myManager == null || key == null) return null;
      PsiElement element = getElement();
      if (element == null) return null;
      URL url = (URL)key;
      Image inMemory = myManager.getElementImage(element, url.toExternalForm());
      if (inMemory != null) {
        return inMemory;
      }

      Url parsedUrl = Urls.parseEncoded(url.toExternalForm());
      BuiltInServerManager builtInServerManager = BuiltInServerManager.getInstance();
      if (parsedUrl != null && builtInServerManager.isOnBuiltInWebServer(parsedUrl)) {
        try {
          url = new URL(builtInServerManager.addAuthToken(parsedUrl).toExternalForm());
        }
        catch (MalformedURLException e) {
          LOG.warn(e);
        }
      }
      return Toolkit.getDefaultToolkit().createImage(url);
    }
  };
  private Runnable myToolwindowCallback;


  public AnAction[] getActions() {
    return myToolBar.getActions().stream().filter((action -> !(action instanceof Separator))).toArray(AnAction[]::new);
  }

  public AnAction getFontSizeAction() {
    return new MyShowSettingsAction();
  }

  public void removeCornerMenu() {
    myScrollPane.resetCorners();
  }

  public void setToolwindowCallback(Runnable callback) {
    myToolwindowCallback = callback;
  }

  private static class Context {
    private final SmartPsiElementPointer element;
    private final String text;
    private final String externalUrl;
    private final Rectangle viewRect;
    private final int highlightedLink;

    public Context(SmartPsiElementPointer element, String text, String externalUrl, Rectangle viewRect, int highlightedLink) {
      this.element = element;
      this.text = text;
      this.externalUrl = externalUrl;
      this.viewRect = viewRect;
      this.highlightedLink = highlightedLink;
    }
  }

  private final MyScrollPane myScrollPane;
  private final JEditorPane myEditorPane;
  private String myText; // myEditorPane.getText() surprisingly crashes.., let's cache the text
  private final JPanel myControlPanel;
  private boolean myControlPanelVisible;
  private int myHighlightedLink = -1;
  private Object myHighlightingTag;

  private JBPopup myHint;

  private final Map<KeyStroke, ActionListener> myKeyboardActions = new HashMap<>();

  @Override
  public boolean requestFocusInWindow() {
    // With a screen reader active, set the focus directly to the editor because
    // it makes it easier for users to read/navigate the documentation contents.
    if (ScreenReader.isActive())
      return myEditorPane.requestFocusInWindow();
    else
      return myScrollPane.requestFocusInWindow();
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

  public DocumentationComponent(final DocumentationManager manager) {
    myManager = manager;
    myIsEmpty = true;
    myIsShown = false;

    myEditorPane = new JEditorPane(UIUtil.HTML_MIME, "") {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
        int em = myEditorPane.getFont().getSize();
        int prefWidth = PREFERRED_WIDTH_EM * em;
        int prefHeightMin = PREFERRED_HEIGHT_MIN_EM * em;
        int prefHeightMax = PREFERRED_HEIGHT_MAX_EM * em;

        if (getWidth() == 0 || getHeight() == 0) {
          setSize(prefWidth, prefHeightMax);
        }

        Insets ins = myEditorPane.getInsets();
        View rootView = myEditorPane.getUI().getRootView(myEditorPane);
        rootView.setSize(prefWidth, prefHeightMax);  // Necessary! Without this line, the size won't increase when the content does

        int prefHeight = (int)rootView.getPreferredSpan(View.Y_AXIS) + ins.bottom + ins.top +
                         myScrollPane.getHorizontalScrollBar().getMaximumSize().height;
        prefHeight = Math.max(prefHeightMin, Math.min(prefHeightMax, prefHeight));
        return new Dimension(prefWidth, prefHeight);
      }

      {
        enableEvents(AWTEvent.KEY_EVENT_MASK);
      }

      @Override
      protected void processKeyEvent(KeyEvent e) {
        KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
        ActionListener listener = myKeyboardActions.get(keyStroke);
        if (listener != null) {
          listener.actionPerformed(new ActionEvent(DocumentationComponent.this, 0, ""));
          e.consume();
          return;
        }
        super.processKeyEvent(e);
      }

      @Override
      protected void paintComponent(Graphics g) {
        GraphicsUtil.setupAntialiasing(g);
        super.paintComponent(g);
      }

      @Override
      public void setDocument(Document doc) {
        super.setDocument(doc);
        doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
        if (doc instanceof StyledDocument) {
          doc.putProperty("imageCache", myImageProvider);
        }
      }
    };
    DataProvider helpDataProvider = new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        return PlatformDataKeys.HELP_ID.is(dataId) ? DOCUMENTATION_TOPIC_ID : null;
      }
    };
    myEditorPane.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, helpDataProvider);
    myText = "";
    myEditorPane.setEditable(false);
    if (ScreenReader.isActive()) {
      // Note: Making the caret visible is merely for convenience
      myEditorPane.getCaret().setVisible(true);
    }
    myEditorPane.setBackground(EditorColorsUtil.getGlobalOrDefaultColor(COLOR_KEY));
    HTMLEditorKit editorKit = UIUtil.getHTMLEditorKit(false);
    String editorFontName = StringUtil.escapeQuotes(EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName());
    if (isMonospacedFont(editorFontName)) {
      editorKit.getStyleSheet().addRule("code {font-family:\"" + editorFontName + "\"}");
      editorKit.getStyleSheet().addRule("pre {font-family:\"" + editorFontName + "\"}");
    }
    myEditorPane.setEditorKit(editorKit);
    myScrollPane = new MyScrollPane();
    myScrollPane.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, helpDataProvider);

    final FocusListener focusAdapter = new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        Component previouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(manager.getProject(getElement()));

        if (previouslyFocused != myEditorPane) {
          if (myHint != null && !myHint.isDisposed()) myHint.cancel();
        }
      }
    };
    myEditorPane.addFocusListener(focusAdapter);

    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myEditorPane.removeFocusListener(focusAdapter);
      }
    });

    setLayout(new BorderLayout());

    mySettingsPanel = createSettingsPanel();
    add(myScrollPane, BorderLayout.CENTER);
    setOpaque(true);
    myScrollPane.setViewportBorder(JBScrollPane.createIndentBorder());

    final DefaultActionGroup actions = new DefaultActionGroup();
    final BackAction back = new BackAction();
    final ForwardAction forward = new ForwardAction();
    EditDocumentationSourceAction edit = new EditDocumentationSourceAction();
    ExternalDocAction externalDoc = new ExternalDocAction();
    actions.add(back);
    actions.add(forward);
    actions.add(edit);

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
    
    externalDoc.registerCustomShortcutSet(CustomShortcutSet.fromString("UP"), this);
    externalDoc.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet(), myEditorPane);
    edit.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    ActionPopupMenu contextMenu = ((ActionManagerImpl)ActionManager.getInstance()).createActionPopupMenu(ActionPlaces.JAVADOC_TOOLBAR, actions,
                                                                                                         new MenuItemPresentationFactory(true));
    PopupHandler popupHandler = new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        contextMenu.getComponent().show(comp, x, y);
      }
    };
    myEditorPane.addMouseListener(popupHandler);
    Disposer.register(this, () -> myEditorPane.removeMouseListener(popupHandler));

    new NextLinkAction().registerCustomShortcutSet(CustomShortcutSet.fromString("TAB"), this);
    new PreviousLinkAction().registerCustomShortcutSet(CustomShortcutSet.fromString("shift TAB"), this);
    new ActivateLinkAction().registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), this);

    myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.JAVADOC_TOOLBAR, actions, true);

    myControlPanel = new JPanel(new BorderLayout(5, 5));
    myControlPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    myElementLabel = new JLabel();
    myElementLabel.setMinimumSize(new Dimension(100, 0)); // do not recalculate size according to the text

    myControlPanel.add(myToolBar.getComponent(), BorderLayout.WEST);
    myControlPanel.add(myElementLabel, BorderLayout.CENTER);
    myControlPanel.add(new MyShowSettingsButton(), BorderLayout.EAST);
    myControlPanelVisible = false;

    final HyperlinkListener hyperlinkListener = new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        HyperlinkEvent.EventType type = e.getEventType();
        if (type == HyperlinkEvent.EventType.ACTIVATED) {
          manager.navigateByLink(DocumentationComponent.this, e.getDescription());
        }
      }
    };
    myEditorPane.addHyperlinkListener(hyperlinkListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myEditorPane.removeHyperlinkListener(hyperlinkListener);
      }
    });

    registerActions();

    updateControlState();
  }

  private static boolean isMonospacedFont(String fontName) {
    return FontInfo.isMonospaced(new Font(fontName, Font.PLAIN, 12));
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (DocumentationManager.SELECTED_QUICK_DOC_TEXT.getName().equals(dataId)) {
      // Javadocs often contain &nbsp; symbols (non-breakable white space). We don't want to copy them as is and replace
      // with raw white spaces. See IDEA-86633 for more details.
      String selectedText = myEditorPane.getSelectedText();
      return selectedText == null? null : selectedText.replace((char)160, ' ');
    }

    return null;
  }

  private JComponent createSettingsPanel() {
    JPanel result = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
    result.add(new JLabel(ApplicationBundle.message("label.font.size")));
    myFontSizeSlider = new JSlider(SwingConstants.HORIZONTAL, 0, FontSize.values().length - 1, 3);
    myFontSizeSlider.setMinorTickSpacing(1);
    myFontSizeSlider.setPaintTicks(true);
    myFontSizeSlider.setPaintTrack(true);
    myFontSizeSlider.setSnapToTicks(true);
    UIUtil.setSliderIsFilled(myFontSizeSlider, true);
    result.add(myFontSizeSlider);
    result.setBorder(BorderFactory.createLineBorder(JBColor.border(), 1));

    myFontSizeSlider.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (myIgnoreFontSizeSliderChange) {
          return;
        }
        setQuickDocFontSize(FontSize.values()[myFontSizeSlider.getValue()]);
        applyFontProps();
      }
    });

    String tooltipText = ApplicationBundle.message("quickdoc.tooltip.font.size.by.wheel");
    result.setToolTipText(tooltipText);
    myFontSizeSlider.setToolTipText(tooltipText);
    result.setVisible(false);
    result.setOpaque(true);
    myFontSizeSlider.setOpaque(true);
    return result;
  }

  @NotNull
  public static FontSize getQuickDocFontSize() {
    String strValue = PropertiesComponent.getInstance().getValue(QUICK_DOC_FONT_SIZE_PROPERTY);
    if (strValue != null) {
      try {
        return FontSize.valueOf(strValue);
      }
      catch (IllegalArgumentException iae) {
        // ignore, fall back to default font.
      }
    }
    return FontSize.SMALL;
  }

  public void setQuickDocFontSize(@NotNull FontSize fontSize) {
    PropertiesComponent.getInstance().setValue(QUICK_DOC_FONT_SIZE_PROPERTY, fontSize.toString());
  }

  private void setFontSizeSliderSize(FontSize fontSize) {
    myIgnoreFontSizeSliderChange = true;
    try {
      FontSize[] sizes = FontSize.values();
      for (int i = 0; i < sizes.length; i++) {
        if (fontSize == sizes[i]) {
          myFontSizeSlider.setValue(i);
          break;
        }
      }
    }
    finally {
      myIgnoreFontSizeSliderChange = false;
    }
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
    myHint = hint;
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

  private void setElement(SmartPsiElementPointer element) {
    myElement = element;
    myModificationCount = getCurrentModificationCount();
  }

  public boolean isUpToDate() {
    return getElement() != null && myModificationCount == getCurrentModificationCount();
  }

  private long getCurrentModificationCount() {
    return myElement != null ? PsiModificationTracker.SERVICE.getInstance(myElement.getProject()).getModificationCount() : -1;
  }

  public void setText(String text, @Nullable PsiElement element, boolean clearHistory) {
    setText(text, element, false, clearHistory);
  }

  public void setText(String text, PsiElement element, boolean clean, boolean clearHistory) {
    if (clean && myElement != null) {
      myBackStack.push(saveContext());
      myForwardStack.clear();
    }
    updateControlState();
    setData(element, text, clearHistory, null);
    if (clean) {
      myIsEmpty = false;
    }

    if (clearHistory) clearHistory();
  }

  public void replaceText(String text, PsiElement element) {
    PsiElement current = getElement();
    if (current == null || !current.getManager().areElementsEquivalent(current, element)) return;
    setText(text, element, false);
    if (!myBackStack.empty()) myBackStack.pop();
  }

  private void clearHistory() {
    myForwardStack.clear();
    myBackStack.clear();
  }

  public void setData(PsiElement _element, String text, final boolean clearHistory, String effectiveExternalUrl) {
    setData(_element, text, clearHistory, effectiveExternalUrl, null);
  }
  
  public void setData(PsiElement _element, String text, final boolean clearHistory, String effectiveExternalUrl, String ref) {
    if (myElement != null) {
      myBackStack.push(saveContext());
      myForwardStack.clear();
    }
    myEffectiveExternalUrl = effectiveExternalUrl;

    final SmartPsiElementPointer element = _element != null && _element.isValid()
                                           ? SmartPointerManager.getInstance(_element.getProject()).createSmartPsiElementPointer(_element)
                                           : null;

    if (element != null) {
      setElement(element);
    }

    myIsEmpty = false;
    updateControlState();
    setDataInternal(element, text, new Rectangle(0, 0), ref);

    if (clearHistory) clearHistory();
  }

  private void setDataInternal(SmartPsiElementPointer element, String text, final Rectangle viewRect, final String ref) {
    setElement(element);
    
    highlightLink(-1);

    myEditorPane.setText(text);
    applyFontProps();

    if (!myIsShown && myHint != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      myManager.showHint(myHint);
      myIsShown = true;
    }

    myText = text;

    PsiElement e = element == null ? null : element.getElement();
    if (e != null) {
      String title = DocumentationManager.getTitle(e, false);
      if (myHint instanceof AbstractPopup) ((AbstractPopup)myHint).setCaption(title);
      // Set panel name so that it is announced by readers when it gets the focus
      AccessibleContextUtil.setName(this, title);
    }

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      myEditorPane.scrollRectToVisible(viewRect); // if ref is defined but is not found in document, this provides a default location
      if (ref != null) {
        myEditorPane.scrollToReference(ref);
      } else if (ScreenReader.isActive()) {
        myEditorPane.setCaretPosition(0);
      }});
  }

  private void applyFontProps() {
    Document document = myEditorPane.getDocument();
    if (!(document instanceof StyledDocument)) {
      return;
    }
    String fontName = Registry.is("documentation.component.editor.font") ?
                      EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName() :
                      myEditorPane.getFont().getFontName();

    // changing font will change the doc's CSS as myEditorPane has JEditorPane.HONOR_DISPLAY_PROPERTIES via UIUtil.getHTMLEditorKit
    myEditorPane.setFont(UIUtil.getFontWithFallback(fontName, Font.PLAIN, JBUI.scale(getQuickDocFontSize().getSize())));
  }

  private void goBack() {
    if (myBackStack.isEmpty()) return;
    Context context = myBackStack.pop();
    myForwardStack.push(saveContext());
    restoreContext(context);
    updateControlState();
  }

  private void goForward() {
    if (myForwardStack.isEmpty()) return;
    Context context = myForwardStack.pop();
    myBackStack.push(saveContext());
    restoreContext(context);
    updateControlState();
  }

  private Context saveContext() {
    Rectangle rect = myScrollPane.getViewport().getViewRect();
    return new Context(myElement, myText, myEffectiveExternalUrl, rect, myHighlightedLink);
  }

  private void restoreContext(Context context) {
    setDataInternal(context.element, context.text, context.viewRect, null);
    myEffectiveExternalUrl = context.externalUrl;
    highlightLink(context.highlightedLink);
  }

  private void updateControlState() {
    ElementLocationUtil.customizeElementLabel(myElement != null ? myElement.getElement() : null, myElementLabel);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    if (Registry.is("documentation.show.toolbar")) {
      myToolBar.updateActionsImmediately(); // update faster
      setControlPanelVisible();
      removeCornerMenu();
    } else {
      myControlPanelVisible = false;
      remove(myControlPanel);
      if (myHint == null) return;
      final DefaultActionGroup gearActions = new MyGearActionGroup();
      gearActions.add(new ShowAsToolwindowAction());
      gearActions.add(new MyShowSettingsAction());
      gearActions.add(new ShowToolbarAction());
      gearActions.addSeparator();
      gearActions.addAll(getActions());
      Presentation presentation = new Presentation();
      presentation.setIcon(AllIcons.General.GearPlain);
      ActionButton corner = new ActionButton(gearActions, presentation, ActionPlaces.UNKNOWN, new Dimension(22, 22)) {
        @Override
        public void paintComponent(Graphics g) {
          g.setColor(getBackground());
          g.fillRect(0, 0, getSize().width, getSize().height);
          paintButtonLook(g);
        }
      };
      corner.setOpaque(true);
      corner.setNoIconsInPopup(true);
      myScrollPane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, corner);
    }
  }

  private static class MyGearActionGroup extends DefaultActionGroup implements HintManagerImpl.ActionToIgnore {
    public MyGearActionGroup(@NotNull AnAction... actions) {
      super(actions);
      setPopup(true);
    }
  }

  private class BackAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public BackAction() {
      super(CodeInsightBundle.message("javadoc.action.back"), null, AllIcons.Actions.Back);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      goBack();
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(!myBackStack.isEmpty());
      if (!isToolbar(e)) {
        presentation.setVisible(presentation.isEnabled());
        presentation.setIcon(AllIcons.Actions.Left);
      }
    }
  }

  private class ForwardAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public ForwardAction() {
      super(CodeInsightBundle.message("javadoc.action.forward"), null, AllIcons.Actions.Forward);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      goForward();
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(!myForwardStack.isEmpty());
      if (!isToolbar(e)) {
        presentation.setVisible(presentation.isEnabled());
        presentation.setIcon(AllIcons.Actions.Right);
      }
    }
  }

  private class EditDocumentationSourceAction extends BaseNavigateToSourceAction {

    private EditDocumentationSourceAction() {
      super(true);
      getTemplatePresentation().setIcon(AllIcons.Actions.EditSource);
      getTemplatePresentation().setText("Edit Source");
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      if (!isToolbar(e)) {
        e.getPresentation().setIcon(AllIcons.General.Inline_edit);
        e.getPresentation().setHoveredIcon(AllIcons.General.Inline_edit_hovered);
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      super.actionPerformed(e);
      final JBPopup hint = myHint;
      if (hint != null && hint.isVisible()) {
        hint.cancel();
      }
    }

    @Nullable
    @Override
    protected Navigatable[] getNavigatables(DataContext dataContext) {
      SmartPsiElementPointer element = myElement;
      if (element != null) {
        PsiElement psiElement = element.getElement();
        return psiElement instanceof Navigatable ? new Navigatable[] {(Navigatable)psiElement} : null;
      }
      return null;
    }
  }

  private static boolean isToolbar(AnActionEvent e) {
    return ActionPlaces.JAVADOC_TOOLBAR.equals(e.getPlace());
  }


  private class ExternalDocAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    private ExternalDocAction() {
      super(CodeInsightBundle.message("javadoc.action.view.external"), null, AllIcons.Actions.Browser_externalJavaDoc);
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet(), null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myElement == null) {
        return;
      }

      final PsiElement element = myElement.getElement();
      final PsiElement originalElement = DocumentationManager.getOriginalElement(element);
      
      ExternalJavaDocAction.showExternalJavadoc(element, originalElement, myEffectiveExternalUrl, e.getDataContext());
    }

    @Override
    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      if (myElement != null) {
        final PsiElement element = myElement.getElement();
        final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
        final PsiElement originalElement = DocumentationManager.getOriginalElement(element);
        if (provider instanceof ExternalDocumentationProvider) {
          presentation.setEnabled(element != null && ((ExternalDocumentationProvider)provider).hasDocumentationFor(element, originalElement));
        }
        else {
          final List<String> urls = provider.getUrlFor(element, originalElement);
          presentation.setEnabled(element != null && urls != null && !urls.isEmpty());
        }
      }
    }
  }

  private void registerActions() {
    // With screen readers, we want the default keyboard behavior inside
    // the document text editor, i.e. the caret moves with cursor keys, etc.
    if (!ScreenReader.isActive()) {
      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
          int value = scrollBar.getValue() - scrollBar.getUnitIncrement(-1);
          value = Math.max(value, 0);
          scrollBar.setValue(value);
        }
      });

      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
          int value = scrollBar.getValue() + scrollBar.getUnitIncrement(+1);
          value = Math.min(value, scrollBar.getMaximum());
          scrollBar.setValue(value);
        }
      });

      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
          int value = scrollBar.getValue() - scrollBar.getUnitIncrement(-1);
          value = Math.max(value, 0);
          scrollBar.setValue(value);
        }
      });

      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
          int value = scrollBar.getValue() + scrollBar.getUnitIncrement(+1);
          value = Math.min(value, scrollBar.getMaximum());
          scrollBar.setValue(value);
        }
      });

      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
          int value = scrollBar.getValue() - scrollBar.getBlockIncrement(-1);
          value = Math.max(value, 0);
          scrollBar.setValue(value);
        }
      });

      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
          int value = scrollBar.getValue() + scrollBar.getBlockIncrement(+1);
          value = Math.min(value, scrollBar.getMaximum());
          scrollBar.setValue(value);
        }
      });

      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
          scrollBar.setValue(0);
        }
      });

      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
          scrollBar.setValue(scrollBar.getMaximum());
        }
      });

      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.CTRL_MASK), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
          scrollBar.setValue(0);
        }
      });

      myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.CTRL_MASK), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
          scrollBar.setValue(scrollBar.getMaximum());
        }
      });
    }
  }

  public String getText() {
    return myText;
  }

  @Override
  public void dispose() {
    myBackStack.clear();
    myForwardStack.clear();
    myKeyboardActions.clear();
    myElement = null;
    myManager = null;
    myHint = null;
  }

  private int getLinkCount() {
    HTMLDocument document = (HTMLDocument)myEditorPane.getDocument();
    int linkCount = 0;
    for (HTMLDocument.Iterator it = document.getIterator(HTML.Tag.A); it.isValid(); it.next()) {
      if (it.getAttributes().isDefined(HTML.Attribute.HREF)) linkCount++;
    }
    return linkCount;
  }

  @Nullable
  private HTMLDocument.Iterator getLink(int n) {
    if (n >= 0) {
      HTMLDocument document = (HTMLDocument)myEditorPane.getDocument();
      int linkCount = 0;
      for (HTMLDocument.Iterator it = document.getIterator(HTML.Tag.A); it.isValid(); it.next()) {
        if (it.getAttributes().isDefined(HTML.Attribute.HREF) && linkCount++ == n) return it;
      }
    }
    return null;
  }
  
  private void highlightLink(int n) {
    myHighlightedLink = n;
    Highlighter highlighter = myEditorPane.getHighlighter();
    HTMLDocument.Iterator link = getLink(n);
    if (link != null) {
      int startOffset = link.getStartOffset();
      int endOffset = link.getEndOffset();
      try {
        if (myHighlightingTag == null) {
          myHighlightingTag = highlighter.addHighlight(startOffset, endOffset, LINK_HIGHLIGHTER);
        }
        else {
          highlighter.changeHighlight(myHighlightingTag, startOffset, endOffset);
        }
        myEditorPane.setCaretPosition(startOffset);
      }
      catch (BadLocationException e) {
        LOG.warn("Error highlighting link", e);
      }
    }
    else if (myHighlightingTag != null) {
      highlighter.removeHighlight(myHighlightingTag);
      myHighlightingTag = null;
    }
  }

  private void activateLink(int n) {
    HTMLDocument.Iterator link = getLink(n);
    if (link != null) {
      String href = (String)link.getAttributes().getAttribute(HTML.Attribute.HREF);
      myManager.navigateByLink(this, href);
    }
  }

  private class MyShowSettingsButton extends ActionButton {

    private MyShowSettingsButton() {
      this(new MyGearActionGroup(new ShowAsToolwindowAction(), new MyShowSettingsAction(), new ShowToolbarAction()), new Presentation(), ActionPlaces.JAVADOC_INPLACE_SETTINGS, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    }

    private MyShowSettingsButton(AnAction action, Presentation presentation, String place, @NotNull Dimension minimumSize) {
      super(action, presentation, place, minimumSize);
      myPresentation.setIcon(AllIcons.General.SecondaryGroup);
    }
  }

  private class MyShowSettingsAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public MyShowSettingsAction() {
      super("Adjust font size...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(mySettingsPanel, myFontSizeSlider).createPopup();
      setFontSizeSliderSize(getQuickDocFontSize());
      mySettingsPanel.setVisible(true);
      Point location = MouseInfo.getPointerInfo().getLocation();
      popup.show(new RelativePoint(new Point(location.x - mySettingsPanel.getPreferredSize().width / 2,
                                             location.y - mySettingsPanel.getPreferredSize().height / 2)));
    }
  }

  private abstract static class MyDictionary<K, V> extends Dictionary<K, V> {
    @Override
    public int size() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<K> keys() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<V> elements() {
      throw new UnsupportedOperationException();
    }

    @Override
    public V put(K key, V value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
      throw new UnsupportedOperationException();
    }
  }

  private class PreviousLinkAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    @Override
    public void actionPerformed(AnActionEvent e) {
      int linkCount = getLinkCount();
      if (linkCount <= 0) return;
      highlightLink(myHighlightedLink < 0 ? (linkCount - 1) : (myHighlightedLink + linkCount - 1) % linkCount);
    }
  }

  private class NextLinkAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    @Override
    public void actionPerformed(AnActionEvent e) {
      int linkCount = getLinkCount();
      if (linkCount <= 0) return;
      highlightLink((myHighlightedLink + 1) % linkCount);
    }
  }

  private class ActivateLinkAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    @Override
    public void actionPerformed(AnActionEvent e) {
      activateLink(myHighlightedLink);
    }
  }
  
  private static class LinkHighlighter implements Highlighter.HighlightPainter {
    private static final Stroke STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{1}, 0);
    
    @Override
    public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
      try {
        Rectangle target = c.getUI().getRootView(c).modelToView(p0, Position.Bias.Forward, p1, Position.Bias.Backward, bounds).getBounds();
        Graphics2D g2d = (Graphics2D)g.create();
        try {
          g2d.setStroke(STROKE);
          g2d.setColor(c.getSelectionColor());
          g2d.drawRect(target.x, target.y, target.width - 1, target.height - 1);
        }
        finally {
          g2d.dispose();
        }
      }
      catch (Exception e) {
        LOG.warn("Error painting link highlight", e);
      }
    }
  }

  private class ShowToolbarAction extends ToggleAction implements HintManagerImpl.ActionToIgnore {
    public ShowToolbarAction() {
      super("Show Toolbar");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return Registry.get("documentation.show.toolbar").asBoolean();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      Registry.get("documentation.show.toolbar").setValue(state);
      updateControlState();
      revalidate();
      repaint();
    }
  }

  private class MyScrollPane extends JBScrollPane {
    public MyScrollPane() {
      super(myEditorPane, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_ALWAYS);
      setLayout(new Layout() {
        @Override
        public void layoutContainer(Container parent) {
          super.layoutContainer(parent);
          if (!(lowerRight instanceof ActionButton)) return;
          if (vsb != null) {
            Rectangle bounds = vsb.getBounds();
            vsb.setBounds(bounds.x, bounds.y, bounds.width, bounds.height - lowerRight.getPreferredSize().height - 6);
          }
          if (hsb != null) {
            Rectangle bounds = hsb.getBounds();
            int vsbOffset = vsb != null ? vsb.getBounds().width : 0;
            hsb.setBounds(bounds.x, bounds.y, bounds.width - lowerRight.getPreferredSize().width - 6 + vsbOffset, bounds.height);
          }
          Rectangle bounds = lowerRight.getBounds();
          lowerRight.setBounds(bounds.x - lowerRight.getPreferredSize().width - 6,
                               bounds.y - lowerRight.getPreferredSize().height - 6,
                               lowerRight.getPreferredSize().width,
                               lowerRight.getPreferredSize().height);
        }
      });
    }

    public void resetCorners() {
      setupCorners();
    }

    @Override
    protected void setupCorners() {
      super.setupCorners();
      setBorder(JBUI.Borders.empty());
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e) {
      if (!EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled() || !EditorUtil.isChangeFontSize(e)) {
        super.processMouseWheelEvent(e);
        return;
      }

      int rotation = e.getWheelRotation();
      if (rotation == 0) return;
      int change = Math.abs(rotation);
      boolean increase = rotation <= 0;
      FontSize newFontSize = getQuickDocFontSize();
      for (; change > 0; change--) {
        if (increase) {
          newFontSize = newFontSize.larger();
        }
        else {
          newFontSize = newFontSize.smaller();
        }
      }

      if (newFontSize == getQuickDocFontSize()) {
        return;
      }

      setQuickDocFontSize(newFontSize);
      applyFontProps();
      setFontSizeSliderSize(newFontSize);
    }
  }

  private class ShowAsToolwindowAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public ShowAsToolwindowAction() {
      super("Open as Tool Window");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myToolwindowCallback.run();
    }
  }
}
