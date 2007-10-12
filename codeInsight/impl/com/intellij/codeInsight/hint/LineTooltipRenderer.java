package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.actions.ShowErrorDescriptionAction;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author cdr
 */
public class LineTooltipRenderer implements TooltipRenderer {
  @NonNls private String myText;

  private boolean myActiveLink = false;
  private int myCurrentWidth;
  @NonNls private static final String BORDER_LINE = "<hr size=1 noshade>";

  public LineTooltipRenderer(String text) {
    myText = text;
  }

  public LineTooltipRenderer(HighlightInfo highlightInfo) {
    this(highlightInfo.toolTip);
  }

  public LineTooltipRenderer(final HighlightInfo info, final int width) {
    this(info);
    myCurrentWidth = width;
  }

  public LineTooltipRenderer(final String text, final int width) {
    this(text);
    myCurrentWidth = width;
  }

  public LightweightHint show(final Editor editor, final Point p, final boolean alignToRight, final TooltipGroup group) {
    if (myText == null) return null;

    //setup text
    myText = myText.replaceAll(String.valueOf(UIUtil.MNEMONIC), "");
    final boolean [] expanded = new boolean[] { myCurrentWidth > 0 && dressDescription()};

    //pane
    final JEditorPane pane = initPane(myText);
    pane.setCaretPosition(0);
    final HintManager hintManager = HintManager.getInstance();
    final JComponent contentComponent = editor.getContentComponent();
    // This listeners makes hint transparent for mouse events. It means that hint is closed
    // by MousePressed and this MousePressed goes into the underlying editor component.
    pane.addMouseListener(new MouseAdapter() {
      public void mouseReleased(final MouseEvent e) {
        if (!myActiveLink) {
          MouseEvent newMouseEvent = SwingUtilities.convertMouseEvent(e.getComponent(), e, contentComponent);
          hintManager.hideAllHints();
          contentComponent.dispatchEvent(newMouseEvent);
        }
      }

      public void mouseExited(final MouseEvent e) {
        hintManager.hideAllHints();
      }
    });

    final JComponent editorComponent = editor.getComponent();
    final JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    int widthLimit = layeredPane.getWidth() - 10;
    int heightLimit = layeredPane.getHeight() - 5;

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(pane);
    scrollPane.setBorder(null);
    int width = expanded[0] ? 3 * myCurrentWidth / 2 : pane.getPreferredSize().width;
    int height = expanded[0] ? Math.max(pane.getPreferredSize().height, 150) : pane.getPreferredSize().height;

    if (alignToRight) {
      p.x -= width;
    }

    // try to make cursor outside tooltip. SCR 15038
    p.x += 3;
    p.y += 3;

    if (p.x + width >= widthLimit) {
      p.x = widthLimit - width;
      width = widthLimit;
    }

    if (p.x < 3) {
      p.x = 3;
    }

    if (p.y + height > heightLimit) {
      p.y = heightLimit - height;
      height = heightLimit;
    }

    //in order to restrict tooltip size
    pane.setSize(width, height);
    pane.setMaximumSize(new Dimension(width, height));
    pane.setMinimumSize(new Dimension(width, height));
    pane.setPreferredSize(new Dimension(width, height));

    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);


    final Ref<AnAction> anAction = new Ref<AnAction>();
    final LightweightHint hint = new LightweightHint(scrollPane) {
      public void hide() {
        ShowErrorDescriptionAction.rememberCurrentWidth(pane.getWidth());
        super.hide();
        final AnAction action = anAction.get();
        if (action != null) {
          action.unregisterCustomShortcutSet(contentComponent);
        }
      }
    };
    anAction.set(new AnAction() { //action to expand description when tooltip was shown after mouse move; need to unregister from editor component
      {
        registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)), contentComponent);
      }
      public void actionPerformed(final AnActionEvent e) {
        hint.hide();
        if (myCurrentWidth > 0) {
          stripDescription();
        }
        new LineTooltipRenderer(myText, myCurrentWidth > 0 ? 0 : pane.getWidth()).show(editor, new Point(p.x -3, p.y -3), alignToRight, group);
      }
    });

    pane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        myActiveLink = true;
        if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
          myActiveLink = false;
          return;
        }
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          if (!expanded[0]) { // more -> less
            for (final TooltipLinkHandlerEP handlerEP : Extensions.getExtensions(TooltipLinkHandlerEP.EP_NAME)) {
              if (handlerEP.handleLink(e.getDescription(), editor, pane)) {
                myText = myText
                  .replace(" " + DaemonBundle.message("inspection.extended.description"), "")
                  .replace("(" + KeymapUtil.getShortcutsText(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")", "");
                pane.setText(myText);
                return;
              }
            }
            if (e.getURL() != null) {
              BrowserUtil.launchBrowser(e.getURL().toString());
            }
          } else { //less -> more
            stripDescription();
            hint.hide();
            new LineTooltipRenderer(myText, 0).show(editor, p, alignToRight, group);
          }
        }
      }
    });
    hintManager.showEditorHint(hint, editor, p,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT |
                               HintManager.HIDE_BY_SCROLLING, 0, false);
    return hint;
  }

  private boolean dressDescription() {
    final String[] problems = getHtmlBody(myText).split(BORDER_LINE);
    String text = "";
    for (String problem : problems) {
      final String descriptionPrefix = getDescriptionPrefix(problem);
      if (descriptionPrefix != null) {
        for (final TooltipLinkHandlerEP handlerEP : Extensions.getExtensions(TooltipLinkHandlerEP.EP_NAME)) {
          final String description = handlerEP.getDescription(descriptionPrefix);
          if (description != null) {
            text += getHtmlBody(problem).replace(DaemonBundle.message("inspection.extended.description"),
                                                   DaemonBundle.message("inspection.collapse.description")) + BORDER_LINE + getHtmlBody(description) + BORDER_LINE;
            break;
          }
        }
      }
    }
    if (text.length() > 0) { //otherwise do not change anything
      myText = "<html><body>" +  StringUtil.trimEnd(text, BORDER_LINE) + "</body></html>";
      return true;
    }
    return false;
  }

  private void stripDescription() {
    final String[] problems = getHtmlBody(myText).split(BORDER_LINE);
    myText = "<html><body>";
    for (int i = 0; i < problems.length; i++) {
      final String problem = problems[i];
      if (i % 2 == 0) {
        myText += getHtmlBody(problem).replace(DaemonBundle.message("inspection.collapse.description"),
                                               DaemonBundle.message("inspection.extended.description")) + BORDER_LINE;
      }
    }
    myText = StringUtil.trimEnd(myText, BORDER_LINE) + "</body></html>";
  }

  @Nullable
  private static String getDescriptionPrefix(@NonNls String text) {
    final int linkIdx = text.indexOf("<a href=");
    if (linkIdx != -1) {
      final String ref = text.substring(linkIdx + 9);
      final int quatIdx = ref.indexOf("\"");
      if (quatIdx > 0) {
        return ref.substring(0, quatIdx);
      }
    }
    return null;
  }

  static JEditorPane initPane(@NonNls String text) {
    text = "<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + "</head><body>" + getHtmlBody(text) + "</body></html>";
    final JEditorPane pane = new JEditorPane(UIUtil.HTML_MIME, text);
    pane.setEditable(false);
    pane.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Color.black),
        BorderFactory.createEmptyBorder(0, 5, 0, 5)
      )
    );
    pane.setForeground(Color.black);
    pane.setBackground(HintUtil.INFORMATION_COLOR);
    pane.setOpaque(true);
    return pane;
  }

  public void addBelow(String text) {
    @NonNls String newBody;
    if (myText ==null) {
      newBody = getHtmlBody(text);
    }
    else {
      String html1 = getHtmlBody(myText);
      String html2 = getHtmlBody(text);
      newBody = html1 + BORDER_LINE + html2;
    }
    myText = "<html><body>" + newBody + "</body></html>";
  }

  private static String getHtmlBody(@NonNls String text) {
    if (!text.startsWith("<html>")) {
      return text.replaceAll("\n","<br>");
    }
    final int bodyIdx = text.indexOf("<body>");
    final int closedBodyIdx = text.indexOf("</body>");
    if (bodyIdx != -1 && closedBodyIdx != -1) {
      return text.substring(bodyIdx + "<body>".length(), closedBodyIdx);
    }
    text = StringUtil.trimStart(text, "<html>").trim();
    text = StringUtil.trimEnd(text, "</html>").trim();
    text = StringUtil.trimStart(text, "<body>").trim();
    text = StringUtil.trimEnd(text, "</body>").trim();
    return text;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LineTooltipRenderer)) return false;

    final LineTooltipRenderer lineTooltipRenderer = (LineTooltipRenderer)o;

    if (myText != null ? !myText.equals(lineTooltipRenderer.myText) : lineTooltipRenderer.myText != null) return false;

    return true;
  }

  public int hashCode() {
    return myText != null ? myText.hashCode() : 0;
  }

  public String getText() {
    return myText;
  }
}
