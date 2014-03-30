/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.ElementLocationUtil;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ExternalJavaDocAction;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationHandler;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class DocumentationComponent extends JPanel implements Disposable, DataProvider {

  @NonNls private static final String DOCUMENTATION_TOPIC_ID = "reference.toolWindows.Documentation";

  private static final DataContext EMPTY_DATA_CONTEXT = new DataContext() {
    @Override
    public Object getData(@NonNls String dataId) {
      return null;
    }
  };

  private static final int MAX_WIDTH = 500;
  private static final int MAX_HEIGHT = 300;
  private static final int MIN_HEIGHT = 45;

  private DocumentationManager myManager;
  private SmartPsiElementPointer myElement;

  private final Stack<Context> myBackStack = new Stack<Context>();
  private final Stack<Context> myForwardStack = new Stack<Context>();
  private final ActionToolbar myToolBar;
  private boolean myIsEmpty;
  private boolean myIsShown;
  private final JLabel myElementLabel;
  private Style myFontSizeStyle;
  private JSlider myFontSizeSlider;
  private final JComponent mySettingsPanel;
  private final MyShowSettingsButton myShowSettingsButton;
  private boolean myIgnoreFontSizeSliderChange;

  private static class Context {
    final SmartPsiElementPointer element;
    final String text;
    final Rectangle viewRect;

    public Context(SmartPsiElementPointer element, String text, Rectangle viewRect) {
      this.element = element;
      this.text = text;
      this.viewRect = viewRect;
    }
  }

  private final JScrollPane myScrollPane;
  private final JEditorPane myEditorPane;
  private String myText; // myEditorPane.getText() surprisingly crashes.., let's cache the text
  private final JPanel myControlPanel;
  private boolean myControlPanelVisible;
  private final ExternalDocAction myExternalDocAction;
  private Consumer<PsiElement> myNavigateCallback;

  private JBPopup myHint;

  private final Map<KeyStroke, ActionListener> myKeyboardActions = new HashMap<KeyStroke, ActionListener>();

  @Override
  public boolean requestFocusInWindow() {
    return myScrollPane.requestFocusInWindow();
  }


  @Override
  public void requestFocus() {
    myScrollPane.requestFocus();
  }

  public DocumentationComponent(final DocumentationManager manager, final AnAction[] additionalActions) {
    myManager = manager;
    myIsEmpty = true;
    myIsShown = false;

    myEditorPane = new JEditorPane(UIUtil.HTML_MIME, "") {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
        if (getWidth() == 0 || getHeight() == 0) {
          setSize(MAX_WIDTH, MAX_HEIGHT);
        }
        Insets ins = myEditorPane.getInsets();
        View rootView = myEditorPane.getUI().getRootView(myEditorPane);
        rootView.setSize(MAX_WIDTH,
                         MAX_HEIGHT);  // Necessary! Without this line, size will not increase then you go from small page to bigger one
        int prefHeight = (int)rootView.getPreferredSpan(View.Y_AXIS);
        prefHeight += ins.bottom + ins.top + myScrollPane.getHorizontalScrollBar().getMaximumSize().height;
        return new Dimension(MAX_WIDTH, Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, prefHeight)));
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
    myEditorPane.setBackground(HintUtil.INFORMATION_COLOR);
    myEditorPane.setEditorKit(UIUtil.getHTMLEditorKit());
    myScrollPane = new JBScrollPane(myEditorPane) {
      @Override
      protected void processMouseWheelEvent(MouseWheelEvent e) {
        if (!EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled() || !EditorUtil.isChangeFontSize(e)) {
          super.processMouseWheelEvent(e);
          return;
        }

        int change = Math.abs(e.getWheelRotation());
        boolean increase = e.getWheelRotation() <= 0;
        EditorColorsManager colorsManager = EditorColorsManager.getInstance();
        EditorColorsScheme scheme = colorsManager.getGlobalScheme();
        FontSize newFontSize = scheme.getQuickDocFontSize();
        for (; change > 0; change--) {
          if (increase) {
            newFontSize = newFontSize.larger();
          }
          else {
            newFontSize = newFontSize.smaller();
          }
        }

        if (newFontSize == scheme.getQuickDocFontSize()) {
          return;
        }

        scheme.setQuickDocFontSize(newFontSize);
        applyFontSize();
        setFontSizeSliderSize(newFontSize);
      }
    };
    myScrollPane.setBorder(null);
    myScrollPane.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, helpDataProvider);

    final MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myManager.requestFocus();
        myShowSettingsButton.hideSettings();
      }
    };
    myEditorPane.addMouseListener(mouseAdapter);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myEditorPane.removeMouseListener(mouseAdapter);
      }
    });

    final FocusAdapter focusAdapter = new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        Component previouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(manager.getProject(getElement()));

        if (!(previouslyFocused == myEditorPane)) {
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
    JLayeredPane layeredPane = new JBLayeredPane() {
      @Override
      public void doLayout() {
        final Rectangle r = getBounds();
        for (Component component : getComponents()) {
          if (component instanceof JScrollPane) {
            component.setBounds(0, 0, r.width, r.height);
          }
          else {
            int insets = 2;
            Dimension d = component.getPreferredSize();
            component.setBounds(r.width - d.width - insets, insets, d.width, d.height);
          }
        }
      }

      @Override
      public Dimension getPreferredSize() {
        Dimension editorPaneSize = myEditorPane.getPreferredScrollableViewportSize();
        Dimension controlPanelSize = myControlPanel.getPreferredSize();
        return new Dimension(Math.max(editorPaneSize.width, controlPanelSize.width), editorPaneSize.height + controlPanelSize.height);
      }
    };
    layeredPane.add(myScrollPane);
    layeredPane.setLayer(myScrollPane, 0);

    mySettingsPanel = createSettingsPanel();
    layeredPane.add(mySettingsPanel);
    layeredPane.setLayer(mySettingsPanel, JLayeredPane.POPUP_LAYER);
    add(layeredPane, BorderLayout.CENTER);
    setOpaque(true);
    myScrollPane.setViewportBorder(JBScrollPane.createIndentBorder());

    final DefaultActionGroup actions = new DefaultActionGroup();
    final BackAction back = new BackAction();
    final ForwardAction forward = new ForwardAction();
    actions.add(back);
    actions.add(forward);
    actions.add(myExternalDocAction = new ExternalDocAction());
    back.registerCustomShortcutSet(CustomShortcutSet.fromString("LEFT"), this);
    forward.registerCustomShortcutSet(CustomShortcutSet.fromString("RIGHT"), this);
    myExternalDocAction.registerCustomShortcutSet(CustomShortcutSet.fromString("UP"), this);
    if (additionalActions != null) {
      for (final AnAction action : additionalActions) {
        actions.add(action);
      }
    }

    myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.JAVADOC_TOOLBAR, actions, true);

    myControlPanel = new JPanel();
    myControlPanel.setLayout(new BorderLayout());
    myControlPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    JPanel dummyPanel = new JPanel();

    myElementLabel = new JLabel();

    dummyPanel.setLayout(new BorderLayout());
    dummyPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

    dummyPanel.add(myElementLabel, BorderLayout.EAST);

    myControlPanel.add(myToolBar.getComponent(), BorderLayout.WEST);
    myControlPanel.add(dummyPanel, BorderLayout.CENTER);
    myControlPanel.add(myShowSettingsButton = new MyShowSettingsButton(), BorderLayout.EAST);
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

  public DocumentationComponent(final DocumentationManager manager) {
    this(manager, null);
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
    result.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor(), 1));

    myFontSizeSlider.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (myIgnoreFontSizeSliderChange) {
          return;
        }
        EditorColorsManager colorsManager = EditorColorsManager.getInstance();
        EditorColorsScheme scheme = colorsManager.getGlobalScheme();
        scheme.setQuickDocFontSize(FontSize.values()[myFontSizeSlider.getValue()]);
        applyFontSize();
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

  public synchronized boolean isEmpty() {
    return myIsEmpty;
  }

  public synchronized void startWait() {
    myIsEmpty = true;
  }

  private void setControlPanelVisible(boolean visible) {
    if (visible == myControlPanelVisible) return;
    if (visible) {
      add(myControlPanel, BorderLayout.NORTH);
    }
    else {
      remove(myControlPanel);
    }
    myControlPanelVisible = visible;
  }

  public void setHint(JBPopup hint) {
    myHint = hint;
  }

  public JComponent getComponent() {
    return myEditorPane;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement != null ? myElement.getElement() : null;
  }

  public void setNavigateCallback(Consumer<PsiElement> navigateCallback) {
    myNavigateCallback = navigateCallback;
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
    setData(element, text, clearHistory);
    if (clean) {
      myIsEmpty = false;
    }

    if (clearHistory) clearHistory();
  }

  public void replaceText(String text, PsiElement element) {
    if (element == null || getElement() != element) return;
    setText(text, element, false);
    if (!myBackStack.empty()) myBackStack.pop();
  }

  private void clearHistory() {
    myForwardStack.clear();
    myBackStack.clear();
  }

  public void setData(PsiElement _element, String text, final boolean clearHistory) {
    if (myElement != null) {
      myBackStack.push(saveContext());
      myForwardStack.clear();
    }

    final SmartPsiElementPointer element = _element != null && _element.isValid()
                                           ? SmartPointerManager.getInstance(_element.getProject()).createSmartPsiElementPointer(_element)
                                           : null;

    if (element != null) {
      myElement = element;
    }

    myIsEmpty = false;
    updateControlState();
    setDataInternal(element, text, new Rectangle(0, 0));

    if (clearHistory) clearHistory();
  }

  private void setDataInternal(SmartPsiElementPointer element, String text, final Rectangle viewRect) {
    setDataInternal(element, text, viewRect, false);
  }

  private void setDataInternal(SmartPsiElementPointer element, String text, final Rectangle viewRect, boolean skip) {

    myElement = element;

    boolean justShown = false;
    if (!myIsShown && myHint != null) {
      myEditorPane.setText(text);
      applyFontSize();
      myManager.showHint(myHint);
      myIsShown = justShown = true;
    }

    if (!justShown) {
      myEditorPane.setText(text);
      applyFontSize();
    }

    if (!skip) {
      myText = text;
    }

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        myEditorPane.scrollRectToVisible(viewRect);
      }
    });
  }

  private void applyFontSize() {
    Document document = myEditorPane.getDocument();
    if (!(document instanceof StyledDocument)) {
      return;
    }

    StyledDocument styledDocument = (StyledDocument)document;
    if (myFontSizeStyle == null) {
      myFontSizeStyle = styledDocument.addStyle("active", null);
    }

    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = colorsManager.getGlobalScheme();
    StyleConstants.setFontSize(myFontSizeStyle, scheme.getQuickDocFontSize().getSize());
    if (Registry.is("documentation.component.editor.font")) {
      StyleConstants.setFontFamily(myFontSizeStyle, scheme.getEditorFontName());
    }
    styledDocument.setCharacterAttributes(0, document.getLength(), myFontSizeStyle, false);
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
    return new Context(myElement, myText, rect);
  }

  private void restoreContext(Context context) {
    setDataInternal(context.element, context.text, context.viewRect);
    if (myNavigateCallback != null) {
      final PsiElement element = context.element.getElement();
      if (element != null) {
        myNavigateCallback.consume(element);
      }
    }
  }

  private void updateControlState() {
    ElementLocationUtil.customizeElementLabel(myElement != null ? myElement.getElement() : null, myElementLabel);
    myToolBar.updateActionsImmediately(); // update faster
    setControlPanelVisible(true);//(!myBackStack.isEmpty() || !myForwardStack.isEmpty());
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
    }
  }

  private class ExternalDocAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public ExternalDocAction() {
      super(CodeInsightBundle.message("javadoc.action.view.external"), null, AllIcons.Actions.Browser_externalJavaDoc);
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet(), null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myElement != null) {
        final PsiElement element = myElement.getElement();
        final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
        final PsiElement originalElement = DocumentationManager.getOriginalElement(element);
        boolean processed = false;
        if (provider instanceof CompositeDocumentationProvider) {
          for (final DocumentationProvider documentationProvider : ((CompositeDocumentationProvider)provider).getProviders()) {
            if (documentationProvider instanceof ExternalDocumentationHandler && ((ExternalDocumentationHandler)documentationProvider).handleExternal(element, originalElement)) {
              processed = true;
              break;
            }
          }
        }

        if (!processed) {
          final List<String> urls = provider.getUrlFor(element, originalElement);
          assert urls != null : provider;
          assert !urls.isEmpty() : provider;
          ExternalJavaDocAction.showExternalJavadoc(urls);
        }
      }
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
    myExternalDocAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet(), myEditorPane);

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
    myNavigateCallback = null;
  }

  private class MyShowSettingsButton extends ActionButton {

    MyShowSettingsButton() {
      this(new MyShowSettingsAction(), new Presentation(), ActionPlaces.JAVADOC_INPLACE_SETTINGS, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    }

    MyShowSettingsButton(AnAction action, Presentation presentation, String place, @NotNull Dimension minimumSize) {
      super(action, presentation, place, minimumSize);
      myPresentation.setIcon(AllIcons.General.SecondaryGroup);
    }

    public void hideSettings() {
      if (!mySettingsPanel.isVisible()) {
        return;
      }
      AnActionEvent event = new AnActionEvent(
        null, EMPTY_DATA_CONTEXT, ActionPlaces.JAVADOC_INPLACE_SETTINGS, myPresentation, ActionManager.getInstance(), 0
      );
      myAction.actionPerformed(event);
    }
  }

  private class MyShowSettingsAction extends ToggleAction {

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySettingsPanel.isVisible();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (!state) {
        mySettingsPanel.setVisible(false);
        return;
      }

      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      EditorColorsScheme scheme = colorsManager.getGlobalScheme();
      setFontSizeSliderSize(scheme.getQuickDocFontSize());
      mySettingsPanel.setVisible(true);
    }
  }
}
