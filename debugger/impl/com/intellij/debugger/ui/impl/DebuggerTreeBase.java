/*
 * Class DebuggerTreeBase
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.text.StringTokenizer;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;


public class DebuggerTreeBase extends DnDAwareTree {
  private Project myProject;
  private DebuggerTreeNodeImpl myCurrentTooltipNode;
  private JComponent myCurrentTooltip;
  protected final TipManager myTipManager;

  public DebuggerTreeBase(TreeModel model, Project project) {
    super(model);
    myProject = project;
    com.intellij.util.ui.UIUtil.setLineStyleAngled(this);
    setRootVisible(false);
    setShowsRootHandles(true);
    setCellRenderer(new DebuggerTreeRenderer());
    updateUI();
    myTipManager = new TipManager(this, new TipManager.TipFactory() {
          public JComponent createToolTip(MouseEvent e) {
            return DebuggerTreeBase.this.createToolTip(e);
          }
        });
    TreeUtil.installActions(this);
  }

  private int getMaximumChars(final String s, final FontMetrics metrics, final int maxWidth) {
    int minChar = 0;
    int maxChar = s.length();
    int chars;
    while(minChar < maxChar) {
      chars = (minChar + maxChar + 1) / 2;
      final int width = metrics.stringWidth(s.substring(0,  chars));
      if(width <= maxWidth) {
        minChar = chars;
      }
      else {
        maxChar = chars - 1;
      }
    }
    return minChar;
  }

  private JComponent createTipContent(String tipText) {
    final JToolTip tooltip = new JToolTip();

    if(tipText == null) {
      tooltip.setTipText(tipText);
    }
    else {
      Dimension rootSize = getVisibleRect().getSize();
      Insets borderInsets = tooltip.getBorder().getBorderInsets(tooltip);
      rootSize.width -= (borderInsets.left + borderInsets.right) * 2;
      rootSize.height -= (borderInsets.top + borderInsets.bottom) * 2;

      //noinspection HardCodedStringLiteral
      final Element html = new Element("html");

      final StringBuilder tipBuilder = StringBuilderSpinAllocator.alloc();
      try {
        final StringTokenizer tokenizer = new StringTokenizer(tipText, "\n");
        final FontMetrics metrics = tooltip.getFontMetrics(tooltip.getFont());
        while(tokenizer.hasMoreElements()) {
          String line = tokenizer.nextToken();
          while (line.length() > 0) {
            if(getMaximumChars(line, metrics, rootSize.width) == line.length()) {
              tipBuilder.append(line).append('\n');
              break;
            }
            else { // maxChars < line.length()
              final String delimiterString = "\\\n";
              final int chars = getMaximumChars(line, metrics, rootSize.width - metrics.stringWidth(delimiterString));
              tipBuilder.append(line.substring(0, chars));
              tipBuilder.append(delimiterString);
              line = line.substring(chars);
            }
          }
        }
        //noinspection HardCodedStringLiteral
        Element p = new Element("pre");
        html.addContent(p);
        p.setText(JDOMUtil.legalizeText(tipBuilder.toString()));
      }
      finally {
        StringBuilderSpinAllocator.dispose(tipBuilder);
      }

      XMLOutputter outputter = JDOMUtil.createOutputter("\n");
      Format format = outputter.getFormat().setTextMode(Format.TextMode.PRESERVE);
      outputter.setFormat(format);
      tooltip.setTipText(outputter.outputString(html));
    }

    tooltip.setBorder(null);

    return tooltip;
  }

  public JComponent createToolTip(MouseEvent e) {
    final DebuggerTreeNodeImpl node = getNodeToShowTip(e);
    if (node == null) {
      return null;
    }

    if(myCurrentTooltip != null && myCurrentTooltip.isShowing() && myCurrentTooltipNode == node) {
      return myCurrentTooltip;
    }

    myCurrentTooltipNode = node;

    final String toolTipText = getTipText(node);
    if(toolTipText == null) {
      return null;
    }

    final JComponent tipContent = createTipContent(toolTipText);
    final JScrollPane scrollPane = new JScrollPane(tipContent);
    scrollPane.setBorder(null);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

    final JToolTip toolTip = new JToolTip();
    toolTip.setLayout(new BorderLayout());
    toolTip.add(scrollPane, BorderLayout.CENTER);

    final Point point = e.getPoint();
    Rectangle tipRectangle = getTipBounds(point, tipContent.getPreferredSize());
    final Border tooltipBorder = toolTip.getBorder();
    if(tooltipBorder != null) {
      final Insets borderInsets = tooltipBorder.getBorderInsets(this);
      tipRectangle.setSize(tipRectangle.width  + borderInsets.left + borderInsets.right, tipRectangle.height + borderInsets.top  + borderInsets.bottom);
    }
    final Dimension tipSize = new Dimension(tipRectangle.getSize());

    if(tipRectangle.getWidth() < tipContent.getPreferredSize().getWidth()) {
      tipSize.height += scrollPane.getHorizontalScrollBar().getPreferredSize().height;
    }

    if(tipRectangle.getHeight() < tipContent.getPreferredSize().getHeight()) {
      tipSize.width += scrollPane.getVerticalScrollBar().getPreferredSize().width;
    }

    if(!tipSize.equals(tipRectangle.getSize())) {
      tipRectangle = getTipBounds(point, tipSize);
    }

    toolTip.setPreferredSize(tipRectangle.getSize());

    myCurrentTooltip = toolTip;

    return toolTip;
  }

  private String getTipText(DebuggerTreeNodeImpl node) {
    NodeDescriptorImpl descriptor = node.getDescriptor();
    if (descriptor instanceof ValueDescriptorImpl) {
      String text = ((ValueDescriptorImpl)descriptor).getValueLabel();
      if (text != null) {
        if(StringUtil.startsWithChar(text, '{') && text.indexOf('}') > 0) {
          int idx = text.indexOf('}');
          if(idx != text.length() - 1) {
            text = text.substring(idx + 1);
          }
        }

        if(StringUtil.startsWithChar(text, '\"') && StringUtil.endsWithChar(text, '\"')) {
          text = text.substring(1, text.length() - 1);
        }

        final String tipText = prepareToolTipText(text);
        if (tipText.length() > 0 && (tipText.indexOf('\n') >= 0 || !getVisibleRect().contains(getRowBounds(getRowForPath(new TreePath(node.getPath())))))) {
          return tipText;
        }
      }
    }
    return null;
  }

  private DebuggerTreeNodeImpl getNodeToShowTip(MouseEvent event) {
    TreePath path = getPathForLocation(event.getX(), event.getY());
    if (path != null) {
      Object last = path.getLastPathComponent();
      if (last instanceof DebuggerTreeNodeImpl) {
        return (DebuggerTreeNodeImpl)last;
      }
    }

    return null;
  }

  private Rectangle getTipBounds(final Point point, Dimension tipContentSize) {
    Rectangle nodeBounds = new Rectangle(point);
    TreePath pathForLocation = getPathForLocation(point.x, point.y);
    if(pathForLocation != null) {
      nodeBounds = getPathBounds(pathForLocation);
    }

    Rectangle contentRect = getVisibleRect();
    int x, y;

    int vgap = nodeBounds.height;
    int height;
    int width = Math.min(tipContentSize.width, contentRect.width);
    if(point.y > contentRect.y + contentRect.height / 2) {
      y = Math.max(contentRect.y, nodeBounds.y - tipContentSize.height - vgap);
      height = Math.min(tipContentSize.height, nodeBounds.y - contentRect.y - vgap);
    }
    else {
      y = nodeBounds.y + nodeBounds.height + vgap;
      height = Math.min(tipContentSize.height, contentRect.height - y);
    }

    final Dimension tipSize = new Dimension(width, height);

    x = point.x - width / 2;
    if(x < contentRect.x) {
      x = contentRect.x;
    }
    if(x + width > contentRect.x + contentRect.width) {
      x = contentRect.x + contentRect.width - width;
    }

    return new Rectangle(new Point(x, y), tipSize);
  }

  private String prepareToolTipText(String text) {
    int tabSize = CodeStyleSettingsManager.getSettings(myProject).getTabSize(StdFileTypes.JAVA);
    if (tabSize < 0) {
      tabSize = 0;
    }
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      boolean special = false;
      for(int idx = 0; idx < text.length(); idx++) {
        char c = text.charAt(idx);
        if(special) {
          if (c == 't') { // convert tabs to spaces
            for (int i = 0; i < tabSize; i++) {
              buf.append(' ');
            }
          }
          else if (c == 'r') { // remove occurances of '\r'
          }
          else if (c == 'n') {
            buf.append('\n');
          }
          else {
            buf.append('\\');
            buf.append(c);
          }
          special = false;
        }
        else {
          if(c == '\\') {
            special = true;
          }
          else {
            buf.append(c);
          }
        }
      }

      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  public void dispose() {
    myTipManager.dispose();
  }

}