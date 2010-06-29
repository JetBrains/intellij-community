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

package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.ElementLocationUtil;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.actions.ExternalJavaDocAction;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.EdgeBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Stack;

public class DocumentationComponent extends JPanel implements Disposable {

  private static final int MAX_WIDTH = 500;
  private static final int MAX_HEIGHT = 300;
  private static final int MIN_HEIGHT = 45;

  private DocumentationManager myManager;
  private SmartPsiElementPointer myElement;

  private final Stack<Context> myBackStack = new Stack<Context>();
  private final Stack<Context> myForwardStack = new Stack<Context>();
  private ActionToolbar myToolBar;
  private boolean myIsEmpty;
  private boolean myIsShown;
  private final JLabel myElementLabel;

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

  private final JBScrollPane myScrollPane;
  private final JEditorPane myEditorPane;
  private String myText; // myEditorPane.getText() surprisingly crashes.., let's cache the text
  private final JPanel myControlPanel;
  private boolean myControlPanelVisible;
  private final ExternalDocAction myExternalDocAction;

  private JBPopup myHint;

  private final HashMap<KeyStroke, ActionListener> myKeyboardActions = new HashMap<KeyStroke, ActionListener>();
    // KeyStroke --> ActionListener

  public boolean requestFocusInWindow() {
    return myScrollPane.requestFocusInWindow();
  }


  public void requestFocus() {
    myScrollPane.requestFocus();
  }

  public DocumentationComponent(final DocumentationManager manager, final AnAction[] additionalActions) {
    myManager = manager;
    myIsEmpty = true;
    myIsShown = false;

    myEditorPane = new JEditorPane(UIUtil.HTML_MIME, "") {
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
        enableEvents(KeyEvent.KEY_EVENT_MASK);
      }

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
    };
    myText = "";
    myEditorPane.setEditable(false);
    myEditorPane.setBackground(HintUtil.INFORMATION_COLOR);
    myEditorPane.setEditorKit(UIUtil.getHTMLEditorKit());
    myScrollPane = new JBScrollPane(myEditorPane);
    myScrollPane.setBorder(null);

    final MouseAdapter mouseAdapter = new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        myManager.requestFocus();
      }
    };
    myEditorPane.addMouseListener(mouseAdapter);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        myEditorPane.removeMouseListener(mouseAdapter);
      }
    });

    final FocusAdapter focusAdapter = new FocusAdapter() {
      public void focusLost(FocusEvent e) {
        Component previouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(manager.getProject(getElement()));

        if (!(previouslyFocused == myEditorPane)) {
          if (myHint != null && !myHint.isDisposed()) myHint.cancel();
        }
      }
    };
    myEditorPane.addFocusListener(focusAdapter);

    Disposer.register(this, new Disposable() {
      public void dispose() {
        myEditorPane.removeFocusListener(focusAdapter);
      }
    });

    setLayout(new BorderLayout());
    add(myScrollPane, BorderLayout.CENTER);
    myScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 2));

    final DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new BackAction());
    actions.add(new ForwardAction());
    actions.add(myExternalDocAction = new ExternalDocAction());

    if (additionalActions != null) {
      for (final AnAction action : additionalActions) {
        actions.add(action);
      }
    }

    myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.JAVADOC_TOOLBAR, actions, true);

    myControlPanel = new JPanel();
    myControlPanel.setLayout(new BorderLayout());
    myControlPanel.setBorder(new EdgeBorder(EdgeBorder.EDGE_BOTTOM));
    JPanel dummyPanel = new JPanel();

    myElementLabel = new JLabel();

    dummyPanel.setLayout(new BorderLayout());
    dummyPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

    dummyPanel.add(myElementLabel, BorderLayout.EAST);

    myControlPanel.add(myToolBar.getComponent(), BorderLayout.WEST);
    myControlPanel.add(dummyPanel, BorderLayout.CENTER);
    myControlPanelVisible = false;

    final HyperlinkListener hyperlinkListener = new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        HyperlinkEvent.EventType type = e.getEventType();
        if (type == HyperlinkEvent.EventType.ACTIVATED) {
          manager.navigateByLink(DocumentationComponent.this, e.getDescription());
        }
        else if (type == HyperlinkEvent.EventType.ENTERED) {
          myEditorPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        else if (type == HyperlinkEvent.EventType.EXITED) {
          myEditorPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
      }
    };
    myEditorPane.addHyperlinkListener(hyperlinkListener);
    Disposer.register(this, new Disposable() {
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

  public void setText(String text, PsiElement element, boolean clearHistory) {
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
    boolean justShown = false;

    myElement = element;

    if (!myIsShown && myHint != null) {
      myEditorPane.setText(text);
      myManager.showHint(myHint);
      myIsShown = justShown = true;
    }

    if (!justShown) {
      myEditorPane.setText(text);
    }

    if (!skip) {
      myText = text;
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myEditorPane.scrollRectToVisible(viewRect);
      }
    });
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
  }

  private void updateControlState() {
    ElementLocationUtil.customizeElementLabel(myElement != null ? myElement.getElement() : null, myElementLabel);
    myToolBar.updateActionsImmediately(); // update faster
    setControlPanelVisible(true);//(!myBackStack.isEmpty() || !myForwardStack.isEmpty());
  }

  private class BackAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public BackAction() {
      super(CodeInsightBundle.message("javadoc.action.back"), null, IconLoader.getIcon("/actions/back.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      goBack();
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(!myBackStack.isEmpty());
    }
  }

  private class ForwardAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public ForwardAction() {
      super(CodeInsightBundle.message("javadoc.action.forward"), null, IconLoader.getIcon("/actions/forward.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      goForward();
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(!myForwardStack.isEmpty());
    }
  }

  private class ExternalDocAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public ExternalDocAction() {
      super(CodeInsightBundle.message("javadoc.action.view.external"), null, IconLoader.getIcon("/actions/browser-externalJavaDoc.png"));
      registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet(), null);
    }

    public void actionPerformed(AnActionEvent e) {
      if (myElement != null) {
        final PsiElement element = myElement.getElement();
        final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
        final List<String> urls = provider.getUrlFor(element, DocumentationManager.getOriginalElement(element));
        assert urls != null;
        assert !urls.isEmpty();
        ExternalJavaDocAction.showExternalJavadoc(urls);
      }
    }

    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      if (myElement != null) {
        final PsiElement element = myElement.getElement();
        final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
        final List<String> urls = provider.getUrlFor(element, DocumentationManager.getOriginalElement(element));
        presentation.setEnabled(element != null && urls != null && !urls.isEmpty());
      }
    }
  }

  private void registerActions() {
    myExternalDocAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet(), myEditorPane);

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
        int value = scrollBar.getValue() - scrollBar.getUnitIncrement(-1);
        value = Math.max(value, 0);
        scrollBar.setValue(value);
      }
    });

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
        int value = scrollBar.getValue() + scrollBar.getUnitIncrement(+1);
        value = Math.min(value, scrollBar.getMaximum());
        scrollBar.setValue(value);
      }
    });

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
        int value = scrollBar.getValue() - scrollBar.getUnitIncrement(-1);
        value = Math.max(value, 0);
        scrollBar.setValue(value);
      }
    });

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
        int value = scrollBar.getValue() + scrollBar.getUnitIncrement(+1);
        value = Math.min(value, scrollBar.getMaximum());
        scrollBar.setValue(value);
      }
    });

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
        int value = scrollBar.getValue() - scrollBar.getBlockIncrement(-1);
        value = Math.max(value, 0);
        scrollBar.setValue(value);
      }
    });

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
        int value = scrollBar.getValue() + scrollBar.getBlockIncrement(+1);
        value = Math.min(value, scrollBar.getMaximum());
        scrollBar.setValue(value);
      }
    });

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
        scrollBar.setValue(0);
      }
    });

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
        scrollBar.setValue(scrollBar.getMaximum());
      }
    });

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, KeyEvent.CTRL_MASK), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
        scrollBar.setValue(0);
      }
    });

    myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, KeyEvent.CTRL_MASK), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
        scrollBar.setValue(scrollBar.getMaximum());
      }
    });
  }

  public String getText() {
    return myText;
  }

  public void dispose() {
    myBackStack.clear();
    myForwardStack.clear();
    myKeyboardActions.clear();
    myElement = null;
    myManager = null;
    myHint = null;
  }

}
