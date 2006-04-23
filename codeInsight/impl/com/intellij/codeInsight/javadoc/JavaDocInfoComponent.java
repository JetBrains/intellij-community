package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.*;
import com.intellij.ui.EdgeBorder;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Stack;

public class JavaDocInfoComponent extends JPanel {
    private static final Icon LIB_ICON_CLOSED = IconLoader.getIcon("/nodes/ppLibClosed.png");

    private static final int MAX_WIDTH = 500;
    private static final int MAX_HEIGHT = 300;
    private static final int MIN_HEIGHT = 45;

    private final JavaDocManager myManager;
    private SmartPsiElementPointer myElement;

    private Stack<Context> myBackStack = new Stack<Context>();
    private Stack<Context> myForwardStack = new Stack<Context>();
    private ActionToolbar myToolBar;
    private boolean myIsEmpty;
    private boolean myIsShown;
    private JLabel myElementLabel;

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

    private JScrollPane myScrollPane;
    private JEditorPane myEditorPane;
    private String myText; // myEditorPane.getText() surprisingly crashes.., let's cache the text
    private JPanel myControlPanel;
    private boolean myControlPanelVisible;
    private ExternalDocAction myExternalDocAction;

    private JBPopup myHint;

    private HashMap<KeyStroke,ActionListener> myKeyboardActions = new HashMap<KeyStroke, ActionListener>(); // KeyStroke --> ActionListener

    public boolean requestFocusInWindow() {
        return myScrollPane.requestFocusInWindow();
    }


  public void requestFocus() {
    myScrollPane.requestFocus();
  }

  public JavaDocInfoComponent(final JavaDocManager manager) {
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
              rootView.setSize(MAX_WIDTH, MAX_HEIGHT);  // Necessary! Without this line, size will not increase then you go from small page to bigger one
              int prefHeight = (int) rootView.getPreferredSpan(View.Y_AXIS);
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
                  listener.actionPerformed(new ActionEvent(JavaDocInfoComponent.this, 0, ""));
                  e.consume();
                  return;
              }
              super.processKeyEvent(e);
          }
      };
      myText = "";
      myEditorPane.setEditable(false);
      myEditorPane.setBackground(HintUtil.INFORMATION_COLOR);

      myScrollPane = new JScrollPane(myEditorPane);
      myScrollPane.setBorder(null);

      myEditorPane.addMouseListener(new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
              myManager.requestFocus();
          }
      });

      myEditorPane.addFocusListener(new FocusAdapter() {
          public void focusLost(FocusEvent e) {
              Component previouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(manager.getProject());

              if (!(previouslyFocused == myEditorPane)) {
                  myHint.cancel();
              }
          }
      });

      setLayout(new BorderLayout());
      add(myScrollPane, BorderLayout.CENTER);
      myScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 2));

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new BackAction());
      group.add(new ForwardAction());
      group.add(myExternalDocAction = new ExternalDocAction());
      myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.JAVADOC_TOOLBAR, group, true);

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

      myEditorPane.addHyperlinkListener(new HyperlinkListener() {
          public void hyperlinkUpdate(HyperlinkEvent e) {
              HyperlinkEvent.EventType type = e.getEventType();
              if (type == HyperlinkEvent.EventType.ACTIVATED) {
                  manager.navigateByLink(JavaDocInfoComponent.this, e.getDescription());
              } else if (type == HyperlinkEvent.EventType.ENTERED) {
                  myEditorPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              } else if (type == HyperlinkEvent.EventType.EXITED) {
                  myEditorPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
              }
          }
      });

      registerActions();

      updateControlState();
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
        } else {
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

    public PsiElement getElement() {
        return myElement != null ? myElement.getElement() : null;
    }

    public void setText(String text) {
        setText(text, false);
    }

    public void setText(String text, boolean clean) {
        updateControlState();
        setDataInternal(myElement, text, new Rectangle(0, 0), true);
        if (clean) {
            myIsEmpty = false;
        }
    }

    public void setData(PsiElement _element, String text) {
        if (myElement != null) {
            myBackStack.push(saveContext());
            myForwardStack.clear();
        }

        final SmartPsiElementPointer element = _element != null ?
          SmartPointerManager.getInstance(_element.getProject()).createSmartPsiElementPointer(_element):
          null;

        if (element != null) {
            myElement = element;
        }

        myIsEmpty = false;
        updateControlState();
        setDataInternal(element, text, new Rectangle(0, 0));
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

  //TODO: Move to a more proper place
    public static void customizeElementLabel(final PsiElement element, final JLabel label) {
        if (element != null) {
          PsiFile file = element.getContainingFile();
          VirtualFile vfile = file == null ? null : file.getVirtualFile();

            if (vfile == null) {
                label.setText("");
                label.setIcon(null);

                return;
            }

            final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
            final Module module = fileIndex.getModuleForFile(vfile);

            if (module != null) {
                label.setText(module.getName());
                label.setIcon(module.getModuleType().getNodeIcon(false));
            } else {
                final List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(vfile);

                OrderEntry entry = null;

              for (OrderEntry order : entries) {
                if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
                  entry = order;
                  break;
                }
              }

                if (entry != null) {
                    label.setText(entry.getPresentableName());
                    label.setIcon(LIB_ICON_CLOSED);
                }
            }
        }
    }

    private void updateControlState() {
        customizeElementLabel(myElement != null ? myElement.getElement():null, myElementLabel);
        myToolBar.updateActionsImmediately(); // update faster
        setControlPanelVisible(true);//(!myBackStack.isEmpty() || !myForwardStack.isEmpty());
    }

    private class BackAction extends AnAction implements HintManager.ActionToIgnore {
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

    private class ForwardAction extends AnAction implements HintManager.ActionToIgnore {
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

    private class ExternalDocAction extends AnAction implements HintManager.ActionToIgnore {
        public ExternalDocAction() {
            super(CodeInsightBundle.message("javadoc.action.view.external"), null, IconLoader.getIcon("/actions/browser-externalJavaDoc.png"));
            registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet(), null);
        }

        public void actionPerformed(AnActionEvent e) {
            if (myElement != null) {
                myManager.openJavaDoc(myElement.getElement());
            }
        }

        public void update(AnActionEvent e) {
            Presentation presentation = e.getPresentation();
            presentation.setEnabled(myElement != null);
            if (getElement() instanceof PsiVariable && !(getElement() instanceof PsiField)) {
                presentation.setEnabled(false);
            }
        }
    }

    private void registerActions() {
        myExternalDocAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EXTERNAL_JAVADOC).getShortcutSet(),
                                                      myEditorPane);

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
                                      int value = scrollBar.getValue() - scrollBar.getUnitIncrement(-1);
                                      value = Math.max(value, 0);
                                      scrollBar.setValue(value);
                                  }
                              });

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
                                      int value = scrollBar.getValue() + scrollBar.getUnitIncrement(+1);
                                      value = Math.min(value, scrollBar.getMaximum());
                                      scrollBar.setValue(value);
                                  }
                              });

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
                                      int value = scrollBar.getValue() - scrollBar.getUnitIncrement(-1);
                                      value = Math.max(value, 0);
                                      scrollBar.setValue(value);
                                  }
                              });

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
                                      int value = scrollBar.getValue() + scrollBar.getUnitIncrement(+1);
                                      value = Math.min(value, scrollBar.getMaximum());
                                      scrollBar.setValue(value);
                                  }
                              });

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
                                      int value = scrollBar.getValue() - scrollBar.getBlockIncrement(-1);
                                      value = Math.max(value, 0);
                                      scrollBar.setValue(value);
                                  }
                              });

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
                                      int value = scrollBar.getValue() + scrollBar.getBlockIncrement(+1);
                                      value = Math.min(value, scrollBar.getMaximum());
                                      scrollBar.setValue(value);
                                  }
                              });

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
                                      scrollBar.setValue(0);
                                  }
                              });

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
                                      scrollBar.setValue(scrollBar.getMaximum());
                                  }
                              });

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, KeyEvent.CTRL_MASK),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
                                      scrollBar.setValue(0);
                                  }
                              });

        myKeyboardActions.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, KeyEvent.CTRL_MASK),
                              new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                      JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
                                      scrollBar.setValue(scrollBar.getMaximum());
                                  }
                              });
    }

    public String getText() {
        return myText;
    }
}
