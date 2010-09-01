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
package com.intellij.ide.actions;

import com.intellij.Patches;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.LicenseeInfoProvider;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class AboutAction extends AnAction implements DumbAware {
  @NonNls private static final String COMPANY_URL = "http://www.jetbrains.com/";      // TODO move to ApplicationInfo.xml

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu);
    e.getPresentation().setDescription("Show information about " + ApplicationNamesInfo.getInstance().getFullProductName());
  }

  public void actionPerformed(AnActionEvent e) {
    Window window = WindowManager.getInstance().suggestParentWindow(e.getData(PlatformDataKeys.PROJECT));

    showAboutDialog(window);
  }

  public static void showAbout() {
    Window window = WindowManager.getInstance().suggestParentWindow(
      PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext()));

    showAboutDialog(window);
  }

  private static void showAboutDialog(Window window) {
    ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
    JPanel mainPanel = new JPanel(new BorderLayout());
    final JComponent closeListenerOwner;
    if (appInfo.showLicenseeInfo()) {
      final Image image = ImageLoader.loadFromResource(appInfo.getAboutLogoUrl());
      final InfoSurface infoSurface = new InfoSurface(image);
      infoSurface.setPreferredSize(new Dimension(image.getWidth(null), image.getHeight(null)));
      mainPanel.add(infoSurface, BorderLayout.NORTH);

      closeListenerOwner = infoSurface;
    }
    else {
      mainPanel.add(new JLabel(IconLoader.getIcon(appInfo.getAboutLogoUrl())), BorderLayout.NORTH);
      closeListenerOwner = mainPanel;
    }

    final JDialog dialog;
    if (window instanceof Dialog) {
      dialog = new JDialog((Dialog)window);
    }
    else {
      dialog = new JDialog((Frame)window);
    }
    dialog.setUndecorated(true);
    dialog.setContentPane(mainPanel);
    dialog.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0) {
          dialog.dispose();
        }
      }
    });

    final long showTime = System.currentTimeMillis();
    final long delta = Patches.APPLE_BUG_ID_3716865 ? 100 : 0;

    dialog.addWindowFocusListener(new WindowFocusListener() {
      public void windowGainedFocus(WindowEvent e) {
      }

      public void windowLostFocus(WindowEvent e) {
        long eventTime = System.currentTimeMillis();
        if (eventTime - showTime > delta && e.getOppositeWindow() != e.getWindow()) {
          dialog.dispose();
        }
      }
    });

    closeListenerOwner.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!e.isConsumed()) {
          dialog.dispose();
          e.consume();
        }
      }
    });

    dialog.pack();

    dialog.setLocationRelativeTo(window);
    dialog.setVisible(true);
  }

  private static class AboutBoxLine {
    private final String myText;
    private final boolean myBold;
    private final boolean myLink;

    public AboutBoxLine(final String text, final boolean bold, final boolean link) {
      myLink = link;
      myText = text;
      myBold = bold;
    }

    public AboutBoxLine(final String text) {
      myText = text;
      myBold = false;
      myLink = false;
    }


    public String getText() {
      return myText;
    }

    public boolean isBold() {
      return myBold;
    }

    public boolean isLink() {
      return myLink;
    }
  }

  private static class InfoSurface extends JPanel {
    final Color col;
    final Color linkCol;
    private final Image myImage;
    private Font myFont;
    private Font myBoldFont;
    private final List<AboutBoxLine> myLines = new ArrayList<AboutBoxLine>();
    private int linkX;
    private int linkY;
    private int linkWidth;
    private boolean inLink = false;

    public InfoSurface(Image image) {
      myImage = image;


      setOpaque(false);
      //col = new Color(0xfa, 0xfa, 0xfa, 200);
      col = Color.white;
      linkCol = Color.blue;
      setBackground(col);
      ApplicationInfoEx ideInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
      Calendar cal = ideInfo.getBuildDate();
      myLines.add(new AboutBoxLine(ideInfo.getFullApplicationName(), true, false));
      myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.build.number", ideInfo.getBuild().asString())));
      String buildDate = "";
      if (ideInfo.getBuild().isSnapshot()) {
        buildDate = new SimpleDateFormat("HH:mm, ").format(cal.getTime());
      }
      buildDate += DateFormat.getDateInstance(DateFormat.LONG).format(cal.getTime());
      myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.build.date", buildDate)));
      myLines.add(new AboutBoxLine(""));
      LicenseeInfoProvider provider = LicenseeInfoProvider.getInstance();
      if (provider != null) {
        myLines.add(new AboutBoxLine(provider.getLicensedToMessage(), true, false));
        myLines.add(new AboutBoxLine(provider.getLicenseRestrictionsMessage()));
        final Date mdd = provider.getMaintenanceDueDate();
        if (mdd != null) {
          myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.maintenance.due", mdd)));
        }
      }
      myLines.add(new AboutBoxLine(""));

      {
        final Properties properties = System.getProperties();
        myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.jdk", properties.getProperty("java.version", "unknown")), true, false));
        myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.vm", properties.getProperty("java.vm.name", "unknown"))));
        myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.vendor", properties.getProperty("java.vendor", "unknown"))));

      }
      myLines.add(new AboutBoxLine(""));
      myLines.add(new AboutBoxLine("JetBrains s.r.o.", true, false));
      myLines.add(new AboutBoxLine(COMPANY_URL, true, true));
      addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent event) {
          if (inLink) {
            event.consume();
            BrowserUtil.launchBrowser(COMPANY_URL);
          }
        }
      });
      addMouseMotionListener(new MouseMotionAdapter() {
        public void mouseMoved(MouseEvent event) {
          if (
            event.getPoint().x > linkX && event.getPoint().y >= linkY &&
            event.getPoint().x < linkX + linkWidth && event.getPoint().y < linkY + 10
            ) {
            if (!inLink) {
              setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              inLink = true;
            }
          }
          else {
            if (inLink) {
              setCursor(Cursor.getDefaultCursor());
              inLink = false;
            }
          }
        }
      });
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);
      Graphics2D g2 = (Graphics2D)g;

      Font labelFont = UIUtil.getLabelFont();
      for (int labelSize = 10; labelSize != 6; labelSize -= 1) {
        g2.setPaint(col);
        g2.drawImage(myImage, 0, 0, this);

        g2.setColor(col);
        TextRenderer renderer = new TextRenderer(0, 145, 398, 120, g2);
        g2.setComposite(AlphaComposite.Src);
        myFont = labelFont.deriveFont(Font.PLAIN, labelSize);
        myBoldFont = labelFont.deriveFont(Font.BOLD, labelSize + 1);
        try {
          renderer.render(75, 0, myLines);
          break;
        }
        catch (TextRenderer.OverflowException _) {
          // ignore
        }
      }
    }

    public class TextRenderer {
      private final int xBase;
      private final int yBase;
      private final int w;
      private final int h;
      private final Graphics2D g2;

      private int x = 0;
      private int y = 0;
      private FontMetrics fontmetrics;
      private int fontAscent;
      private int fontHeight;
      private Font font;

      public class OverflowException extends Exception {
      }

      public TextRenderer(final int xBase, final int yBase, final int w, final int h, final Graphics2D g2) {
        this.xBase = xBase;
        this.yBase = yBase;
        this.w = w;
        this.h = h;
        this.g2 = g2;

        if (SystemInfo.isWindows) {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
      }


      public void render(int indentX, int indentY, List<AboutBoxLine> lines) throws OverflowException {
        x = indentX;
        y = indentY;
        g2.setColor(Color.black);
        for (int i = 0; i < lines.size(); i++) {
          AboutBoxLine line = lines.get(i);
          final String s = line.getText();
          setFont(line.isBold() ? myBoldFont : myFont);
          if (line.isLink()) {
            g2.setColor(linkCol);
            linkX = x;
            linkY = yBase + y - fontAscent;
            FontMetrics metrics = g2.getFontMetrics(font);
            linkWidth = metrics.stringWidth(s);
          }
          renderString(s, indentX);
          if (i == lines.size() - 2) {
            x += 50;
          }
          else if (i < lines.size() - 1) {
            lineFeed(indentX, s);
          }
        }
      }

      private void renderString(final String s, final int indentX) throws OverflowException {
        final List<String> words = StringUtil.split(s, " ");
        for (String word : words) {
          int wordWidth = fontmetrics.stringWidth(word);
          if (x + wordWidth >= w) {
            lineFeed(indentX, word);
          }
          else {
            char c = ' ';
            final int cW = fontmetrics.charWidth(c);
            if (x + cW < w) {
              g2.drawChars(new char[]{c}, 0, 1, xBase + x, yBase + y);
              x += cW;
            }
          }
          renderWord(word, indentX);
        }
      }

      private void renderWord(final String s, final int indentX) throws OverflowException {
        for (int j = 0; j != s.length(); ++j) {
          final char c = s.charAt(j);
          final int cW = fontmetrics.charWidth(c);
          if (x + cW >= w) {
            lineFeed(indentX, s);
          }
          g2.drawChars(new char[]{c}, 0, 1, xBase + x, yBase + y);
          x += cW;
        }
      }

      private void lineFeed(int indent, final String s) throws OverflowException {
        x = indent;
        if (s.length() == 0) {
          y += fontHeight / 3;
        }
        else {
          y += fontHeight;
        }
        if (y >= h) {
          throw new OverflowException();
        }
      }

      private void setFont(Font font) {
        this.font = font;
        fontmetrics = g2.getFontMetrics(font);
        g2.setFont(font);
        fontAscent = fontmetrics.getAscent();
        fontHeight = fontmetrics.getHeight();
      }
    }
  }
}
