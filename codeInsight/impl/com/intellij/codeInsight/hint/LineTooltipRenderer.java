package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URL;

/**
 * @author cdr
 */
public class LineTooltipRenderer implements TooltipRenderer {
  @NonNls private String myText;

  private boolean myActiveLink = false;

  public LineTooltipRenderer(String text) {
    myText = text;
  }

  public LineTooltipRenderer(HighlightInfo highlightInfo) {
    this(highlightInfo.toolTip);
  }

  public LightweightHint show(final Editor editor, Point p, boolean alignToRight, TooltipGroup group) {
    if (myText == null) return null;

    myText = myText.replaceAll(String.valueOf(UIUtil.MNEMONIC), "");

    final HintManager hintManager = HintManager.getInstance();

    final JComponent editorComponent = editor.getComponent();
    final JEditorPane pane = initPane(myText);
    pane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        myActiveLink = true;
        if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
          myActiveLink = false;
          return;
        } 
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          showDescription(e.getDescription(), editor, pane);
        }
      }
    });
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

    int width = pane.getPreferredSize().width;

    final JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    int widthLimit = layeredPane.getWidth() - 10;
    int heightLimit = layeredPane.getHeight() - 5;

    String text = myText;

    if (alignToRight) {
      p.x -= pane.getPreferredSize().width;
    }

    // try to make cursor outside tooltip. SCR 15038
    p.x += 3;
    p.y += 3;
    width = pane.getPreferredSize().width;
    if (p.x + width >= widthLimit) {
      p.x = widthLimit - width;
    }
    if (p.x < 3) {
      p.x = 3;
    }

    final int height = pane.getPreferredSize().height;
    if (p.y + height > heightLimit) {
      p.y = heightLimit - height;
    }
    final LightweightHint hint = new LightweightHint(pane);
    hintManager.showEditorHint(hint, editor, p,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT |
                               HintManager.HIDE_BY_SCROLLING, 0, false);
    return hint;
  }

  private static JEditorPane initPane(@NonNls String text) {
    final Font font = UIUtil.getLabelFont();
    text = "<html><head><style> body, div, td { font-family: " + font.getFamily() + "; font-size: " + font.getSize() + "; } </style></head><body>" + getHtmlBody(text) + "</body></html>";
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

  private static void showDescription(final String shortName, final Editor editor, final JEditorPane tooltip) {
    final InspectionProfileEntry tool =
      ((InspectionProfile)InspectionProfileManager.getInstance().getRootProfile()).getInspectionTool(shortName);
    if (tool == null) return;
    final URL descriptionUrl = InspectionToolRegistrar.getDescriptionUrl(tool);
    String description;
    try {
      description = ResourceUtil.loadText(descriptionUrl);
    }
    catch (IOException e) {
      description = InspectionsBundle.message("inspection.tool.description.under.construction.text");
    }
    final JEditorPane pane = initPane(description);
    pane.select(0, 0);
    pane.setPreferredSize(new Dimension(3 * tooltip.getPreferredSize().width /2, 200));
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(pane);
    scrollPane.setBorder(null);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, scrollPane).createPopup();
    pane.addMouseListener(new MouseAdapter(){
      public void mousePressed(final MouseEvent e) {
        final Component contentComponent = editor.getContentComponent();
        MouseEvent newMouseEvent = SwingUtilities.convertMouseEvent(e.getComponent(), e, contentComponent);
        popup.cancel();
        contentComponent.dispatchEvent(newMouseEvent);
      }
    });
    popup.showUnderneathOf(tooltip);
  }

  public void addBelow(String text) {
    @NonNls String newBody;
    if (myText ==null) {
      newBody = getHtmlBody(text);
    }
    else {
      String html1 = getHtmlBody(myText);
      String html2 = getHtmlBody(text);
      newBody = html1 + "<hr size=1 noshade>" + html2;
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
