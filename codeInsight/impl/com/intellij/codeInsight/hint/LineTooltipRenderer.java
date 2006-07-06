package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.SplittingUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Feb 21, 2005
 * Time: 7:17:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class LineTooltipRenderer implements TooltipRenderer {
  private String myText;

  public LineTooltipRenderer(String text) {
    myText = text;
  }

  public LineTooltipRenderer(HighlightInfo highlightInfo) {
    this(highlightInfo.toolTip);
  }

  public LightweightHint show(final Editor editor, Point p, boolean alignToRight, TooltipGroup group) {

    final HintManager hintManager = HintManager.getInstance();

    final JComponent editorComponent = editor.getComponent();
    JLabel label = new JLabel();
    final JComponent contentComponent = editor.getContentComponent();
    // This listeners makes hint transparent for mouse events. It means that hint is closed
    // by MousePressed and this MousePressed goes into the underlying editor component.
    label.addMouseListener(
      new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          MouseEvent newMouseEvent = SwingUtilities.convertMouseEvent(e.getComponent(), e, contentComponent);
          hintManager.hideAllHints();
          contentComponent.dispatchEvent(newMouseEvent);
        }
      }
    );

    label.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Color.black),
        BorderFactory.createEmptyBorder(0, 5, 0, 5)
      )
    );
    label.setForeground(Color.black);
    label.setBackground(HintUtil.INFORMATION_COLOR);
    label.setOpaque(true);

    String text = myText;

    if (text == null) return null;
    label.setText(text);
    int width = label.getPreferredSize().width;

    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

    int widthLimit = layeredPane.getWidth() - 10;
    int heightLimit = layeredPane.getHeight() - 5;
    if (!isRichHtml(text) && width > widthLimit / 3) {
      label.setUI(new MultiLineLabelUI());
      text = splitText(label, text, widthLimit);
      label.setText(text);
    }

    if (alignToRight) {
      p.x -= label.getPreferredSize().width;
    }

    // try to make cursor outside tooltip. SCR 15038
    p.x += 3;
    p.y += 3;
    width = label.getPreferredSize().width;
    if (p.x + width >= widthLimit) {
      p.x = widthLimit - width;
    }
    if (p.x < 3) {
      p.x = 3;
    }

    int height = label.getPreferredSize().height;
    if (p.y + height > heightLimit) {
      p.y = heightLimit - height;
    }
    LightweightHint hint = new LightweightHint(label);
    hintManager.showEditorHint(hint, editor, p,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT |
                               HintManager.HIDE_BY_SCROLLING, 0, false);
    return hint;
  }

  private static boolean isRichHtml(@NonNls final String text) {
    if (!text.startsWith("<html>") || !text.endsWith("</html>")) return false;
    @NonNls int idx = "<html>".length();
    idx = text.indexOf("<body>", idx);
    if (idx == -1) return false;
    idx += "<body>".length();

    int endIdx = text.lastIndexOf("</body>", text.length() - "</html>".length());
    if (endIdx <= 0) return false;

    int i = text.indexOf('<', idx);
    return i != -1 && i < endIdx;
  }

  /**
   * @return text splitted with '\n'
   */
  private static String splitText(JLabel label, String text, int widthLimit) {
    FontMetrics fontMetrics = label.getFontMetrics(label.getFont());

    String[] lines = SplittingUtil.splitText(text, fontMetrics, widthLimit, ' ');

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (i > 0) {
        result.append('\n');
      }
      result.append(line);
    }
    return result.toString();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void addBelow(String text) {
    String html1 = getHtmlBody(myText);
    String html2 = getHtmlBody(text);
    myText = "<html><body>" + html1 + "<hr size=1 noshade>" + html2 + "</body></html>";
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String getHtmlBody(String text) {
    if (!text.startsWith("<html>")) {
      return text.replaceAll("\n","<br>");
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
