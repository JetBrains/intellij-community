package com.intellij.ide.actions;

import com.intellij.Patches;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.license.LicenseManager;
import com.intellij.ide.license.ui.LicenseUrls;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.AnimatingSurface;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.text.DateFormat;

public class AboutAction extends AnAction {
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu);
  }

  public void actionPerformed(AnActionEvent e) {
    Window window = WindowManager.getInstance().suggestParentWindow((Project)e.getDataContext().getData(DataConstants.PROJECT));

    showAboutDialog(window);
  }

  public static void showAbout() {
    Window window = WindowManager.getInstance().suggestParentWindow(
      (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT));

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
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          infoSurface.start();
        }
      });
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
      public void windowGainedFocus(WindowEvent e) {}

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
    private String myText;
    private boolean myBold;
    private boolean myLink;

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

  private static class InfoSurface extends AnimatingSurface {
    final Color col;
    final Color linkCol;
    static final int UP = 0;
    static final int DOWN = 1;
    private Image myImage;
    private float myAlpha;
    private int myAlphaDirection = UP;
    private Font myFont;
    private Font myBoldFont;
    private List<AboutBoxLine> myLines = new ArrayList<AboutBoxLine>();
    private int linkX;
    private int linkY;
    private int linkWidth;
    private boolean inLink = false;

    public InfoSurface(Image image) {
      myImage = image;


      myAlpha = 0f;
      setOpaque(true);
      //col = new Color(0xfa, 0xfa, 0xfa, 200);
      col = Color.white;
      linkCol = Color.blue;
      setBackground(col);
      ApplicationInfoEx ideInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
      Calendar cal = ideInfo.getBuildDate();
      myLines.add(new AboutBoxLine(ideInfo.getFullApplicationName(), true, false));
      myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.build.number", ideInfo.getBuildNumber())));
      myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.build.date", DateFormat.getDateInstance(DateFormat.LONG).format(cal.getTime()))));
      myLines.add(new AboutBoxLine(""));
      myLines.add(new AboutBoxLine(LicenseManager.getInstance().licensedToMessage(), true, false));
      myLines.add(new AboutBoxLine(LicenseManager.getInstance().licensedRestrictionsMessage()));
      myLines.add(new AboutBoxLine(""));

      {
        final Properties properties = System.getProperties();
        //noinspection HardCodedStringLiteral
        myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.jdk", properties.getProperty("java.version", "unknown")), true, false));
        //noinspection HardCodedStringLiteral
        myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.vm", properties.getProperty("java.vm.name", "unknown"))));
        //noinspection HardCodedStringLiteral
        myLines.add(new AboutBoxLine(IdeBundle.message("aboutbox.vendor", properties.getProperty("java.vendor", "unknown"))));
      }
      myLines.add(new AboutBoxLine(""));
      //noinspection HardCodedStringLiteral
      myLines.add(new AboutBoxLine("JetBrains s.r.o.", true, false));
      myLines.add(new AboutBoxLine(LicenseUrls.getCompanyUrl(), true, true));
      addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent event) {
          if (inLink) {
            event.consume();
            BrowserUtil.launchBrowser(LicenseUrls.getCompanyUrl());
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

    public void render(int w, int h, Graphics2D g2) {
      AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha);
      g2.setComposite(ac);

      //noinspection HardCodedStringLiteral
      Font labelFont = UIUtil.getLabelFont();
      for (int labelSize = 10; labelSize != 6; labelSize -= 1) {
        g2.setPaint(col);
        g2.drawImage(myImage, 0, 0, this);
        g2.setColor(col);
        int startX = (int)(-300 * (1.0f - myAlpha) + 1);
        TextRenderer renderer = new TextRenderer(startX, 145, 398, 135, g2);
        g2.setComposite(AlphaComposite.Src);
        myFont = labelFont.deriveFont(Font.PLAIN, labelSize);
        myBoldFont = labelFont.deriveFont(Font.BOLD, labelSize+1);
        try {
          renderer.render (75, 0, myLines);
          break;
        }
        catch (TextRenderer.OverflowException _) {
          // ignore
        }
      }
    }

    public void reset(int w, int h) { }

    public void step(int w, int h) {
      if (myAlphaDirection == UP) {
        if ((myAlpha += 0.2) > .99) {
          myAlphaDirection = DOWN;
          myAlpha = 1.0f;
        }
      }
      else if (myAlphaDirection == DOWN) {
        stop();
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

      public class OverflowException extends Exception { }

      public TextRenderer(final int xBase, final int yBase, final int w, final int h, final Graphics2D g2) {
        this.xBase = xBase;
        this.yBase = yBase;
        this.w = w;
        this.h = h;
        this.g2 = g2;
        g2.fillRect(xBase, yBase, w, h);
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
          setFont (line.isBold() ? myBoldFont : myFont);
          if (line.isLink()) {
            g2.setColor(linkCol);
            linkX = x;
            linkY = yBase + y - fontAscent;
            FontMetrics metrics = g2.getFontMetrics(font);
            linkWidth = metrics.stringWidth (s);
          }
          renderString(s, indentX);
          if (i == lines.size()-2) {
            x += 50;
          }
          else if (i < lines.size()-1) {
            lineFeed (indentX, s);
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
        for (int j = 0; j != s.length(); ++ j) {
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
          y += fontHeight/2;
        }
        else {
          y += fontHeight;
        }
        if (y >= h)
          throw new OverflowException();
      }

      private void setFont(Font font) {
        this.font = font;
        fontmetrics =  g2.getFontMetrics(font);
        g2.setFont (font);
        fontAscent = fontmetrics.getAscent();
        fontHeight = fontmetrics.getHeight();
      }
    }
  }
}
