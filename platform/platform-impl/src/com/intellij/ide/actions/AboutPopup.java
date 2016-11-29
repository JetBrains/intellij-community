/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.UI;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class AboutPopup {
  private static final String COPY_URL = "copy://";
  private static JBPopup ourPopup;

  public static void show(@Nullable Window window) {
    ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();

    final PopupPanel panel = new PopupPanel(new BorderLayout());
    Icon image = IconLoader.getIcon(appInfo.getAboutImageUrl());
    if (appInfo.showLicenseeInfo()) {
      final InfoSurface infoSurface = new InfoSurface(image);
      infoSurface.setPreferredSize(new Dimension(image.getIconWidth(), image.getIconHeight()));
      panel.setInfoSurface(infoSurface);
    }
    else {
      panel.add(new JLabel(image), BorderLayout.NORTH);
    }

    RelativePoint location;
    if (window != null) {
      Rectangle r = window.getBounds();
      location = new RelativePoint(window, new Point((r.width - image.getIconWidth()) / 2, (r.height - image.getIconHeight()) / 2));
    }
    else {
      Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
      location = new RelativePoint(new Point((r.width - image.getIconWidth()) / 2, (r.height - image.getIconHeight()) / 2));
    }

    ourPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel)
      .setRequestFocus(true)
      .setFocusable(true)
      .setResizable(false)
      .setMovable(false)
      .setModalContext(false)
      .setShowShadow(true)
      .setShowBorder(false)
      .setCancelKeyEnabled(true)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .createPopup();

    Disposer.register(ourPopup, new Disposable() {
      @Override
      public void dispose() {
        ourPopup = null;
      }
    });

    ourPopup.show(location);
  }

  private static void copyInfoToClipboard(String text) {
    try {
      CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }
    catch (Exception ignore) { }
  }

  private static class InfoSurface extends JPanel {
    private final Color myColor;
    private final Color myLinkColor;
    private final Icon myImage;
    private Font myFont;
    private Font myBoldFont;
    private final List<AboutBoxLine> myLines = new ArrayList<>();
    private StringBuilder myInfo = new StringBuilder();
    private final List<Link> myLinks = new ArrayList<>();
    private Link myActiveLink;
    private boolean myShowCopy = false;
    private float myShowCopyAlpha;
    private Alarm myAlarm = new Alarm();

    public InfoSurface(Icon image) {
      ApplicationInfoImpl appInfo = (ApplicationInfoImpl)ApplicationInfoEx.getInstanceEx();

      myImage = image;
      //noinspection UseJBColor
      myColor = Color.white;
      myLinkColor = appInfo.getAboutLinkColor() != null ? appInfo.getAboutLinkColor() : UI.getColor("link.foreground");

      setOpaque(false);
      setBackground(myColor);
      setFocusable(true);
      Calendar cal = appInfo.getBuildDate();
      myLines.add(new AboutBoxLine(appInfo.getFullApplicationName(), true, null));
      appendLast();

      String buildInfo = IdeBundle.message("about.box.build.number", appInfo.getBuild().asString());
      String buildDate = "";
      if (appInfo.getBuild().isSnapshot()) {
        buildDate = new SimpleDateFormat("HH:mm, ").format(cal.getTime());
      }
      buildDate += DateFormatUtil.formatAboutDialogDate(cal.getTime());
      buildInfo += IdeBundle.message("about.box.build.date", buildDate);
      myLines.add(new AboutBoxLine(buildInfo));
      appendLast();

      myLines.add(new AboutBoxLine(""));

      LicensingFacade provider = LicensingFacade.getInstance();
      if (provider != null) {
        myLines.add(new AboutBoxLine(provider.getLicensedToMessage(), true, null));
        appendLast();
        for (String message : provider.getLicenseRestrictionsMessages()) {
          myLines.add(new AboutBoxLine(message));
          appendLast();
        }
      }

      myLines.add(new AboutBoxLine(""));

      Properties properties = System.getProperties();
      String javaVersion = properties.getProperty("java.runtime.version", properties.getProperty("java.version", "unknown"));
      String arch = properties.getProperty("os.arch", "");
      myLines.add(new AboutBoxLine(IdeBundle.message("about.box.jre", javaVersion, arch)));
      appendLast();

      String vmVersion = properties.getProperty("java.vm.name", "unknown");
      String vmVendor = properties.getProperty("java.vendor", "unknown");
      myLines.add(new AboutBoxLine(IdeBundle.message("about.box.vm", vmVersion, vmVendor)));
      appendLast();

      String thirdParty = appInfo.getThirdPartySoftwareURL();
      if (thirdParty != null) {
        myLines.add(new AboutBoxLine(""));
        myLines.add(new AboutBoxLine(""));
        myLines.add(new AboutBoxLine("Powered by ").keepWithNext());
        myLines.add(new AboutBoxLine("open-source software", false, thirdParty));
      }

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (myActiveLink != null) {
            event.consume();
            if (COPY_URL.equals(myActiveLink.myUrl)) {
              copyInfoToClipboard(myInfo.toString());
              if (ourPopup != null) {
                ourPopup.cancel();
              }
              return;
            }
            BrowserUtil.browse(myActiveLink.myUrl);
          }
        }

        final static double maxAlpha = 0.5;
        final static double fadeStep = 0.05;
        final static int animationDelay = 15;

        @Override
        public void mouseEntered(MouseEvent e) {
          if (!myShowCopy) {
            myShowCopy = true;
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(new Runnable() {
              @Override
              public void run() {
                if (myShowCopyAlpha < maxAlpha) {
                  myShowCopyAlpha += fadeStep;
                  repaint();
                  myAlarm.addRequest(this, animationDelay);
                }
              }
            }, animationDelay);
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          if (myShowCopy) {
            myShowCopy = false;
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(new Runnable() {
              @Override
              public void run() {
                if (myShowCopyAlpha > 0) {
                  myShowCopyAlpha -= fadeStep;
                  repaint();
                  myAlarm.addRequest(this, animationDelay);
                }
              }
            }, animationDelay);
          }
        }
      });

      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent event) {
          boolean hadLink = (myActiveLink != null);
          myActiveLink = null;
          for (Link link : myLinks) {
            if (link.myRectangle.contains(event.getPoint())) {
              myActiveLink = link;
              if (!hadLink) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              }
              break;
            }
          }
          if (hadLink && myActiveLink == null) {
            setCursor(Cursor.getDefaultCursor());
          }
        }
      });
    }

    private void appendLast() {
      myInfo.append(myLines.get(myLines.size() - 1).getText()).append("\n");
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);

      Graphics2D g2 = (Graphics2D)g;
      UISettings.setupAntialiasing(g);

      Font labelFont = JBUI.Fonts.label();
      if (SystemInfo.isWindows) {
        labelFont = JBUI.Fonts.create("Tahoma", 12);
      }

      int startFontSize = Registry.is("ide.new.about") ? 14 : 10;
      for (int labelSize = JBUI.scale(startFontSize); labelSize != JBUI.scale(6); labelSize -= 1) {
        myLinks.clear();
        g2.setPaint(myColor);
        myImage.paintIcon(this, g2, 0, 0);

        g2.setColor(myColor);
        TextRenderer renderer = createTextRenderer(g2);
        UIUtil.setupComposite(g2);
        myFont = labelFont.deriveFont(Font.PLAIN, labelSize);
        myBoldFont = labelFont.deriveFont(Font.BOLD, labelSize + 1);
        try {
          renderer.render(30, 0, myLines);
          break;
        }
        catch (TextRenderer.OverflowException ignore) { }
      }

      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      Rectangle aboutLogoRect = appInfo.getAboutLogoRect();
      if (aboutLogoRect != null) {
        myLinks.add(new Link(aboutLogoRect, appInfo.getCompanyURL()));
      }

      if (appInfo instanceof ApplicationInfoImpl) {
        g2.setColor(((ApplicationInfoImpl)appInfo).getCopyrightForeground());
        if (SystemInfo.isMac) {
          g2.setFont(JBUI.Fonts.miniFont());
        }
        else {
          g2.setFont(JBUI.Fonts.create("Tahoma", 10));
        }
      } else {
        g2.setColor(JBColor.BLACK);
      }

      if (Registry.is("ide.new.about")) {
        g2.setColor(Gray.x33);
        g2.setFont(JBUI.Fonts.label(12));
      }
      final int copyrightX = Registry.is("ide.new.about") ? JBUI.scale(140) : JBUI.scale(30);
      final int copyrightY = Registry.is("ide.new.about") ? JBUI.scale(390) : JBUI.scale(284);
      g2.drawString(getCopyrightText(), copyrightX, copyrightY);
    }

    @NotNull
    private String getCopyrightText() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      return "\u00A9 2000\u2013" + Calendar.getInstance(Locale.US).get(Calendar.YEAR) + " JetBrains s.r.o. All rights reserved.";
    }

    @NotNull
    private TextRenderer createTextRenderer(Graphics2D g) {
      if (Registry.is("ide.new.about")) {
        return new TextRenderer(18, 200, 500, 220, g);
      }
      return new TextRenderer(0, 165, 398, 120, g);
    }

    public String getText() {
      return myInfo.toString();
    }

    private class TextRenderer {
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
        this.xBase = JBUI.scale(xBase);
        this.yBase = JBUI.scale(yBase);
        this.w = JBUI.scale(w);
        this.h = JBUI.scale(h);
        this.g2 = g2;

        if (SystemInfo.isWindows) {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
      }

      public void render(int indentX, int indentY, List<AboutBoxLine> lines) throws OverflowException {
        x = indentX;
        y = indentY;
        ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
        boolean showCopyButton = myShowCopy || myShowCopyAlpha > 0;
        for (AboutBoxLine line : lines) {
          final String s = line.getText();
          setFont(line.isBold() ? myBoldFont : myFont);
          if (line.getUrl() != null) {
            g2.setColor(myLinkColor);
            FontMetrics metrics = g2.getFontMetrics(font);
            myLinks.add(new Link(new Rectangle(xBase + x, yBase + y - fontAscent, metrics.stringWidth(s + " "), fontHeight), line.getUrl()));
          }
          else {
            g2.setColor(Registry.is("ide.new.about") ? Gray.x33 : appInfo.getAboutForeground());
          }
          renderString(s, indentX);
          if (showCopyButton) {
            final FontMetrics metrics = g2.getFontMetrics(myFont);
            String copyString = "Copy to Clipboard";
            final int width = metrics.stringWidth(copyString);
            g2.setFont(myFont);
            g2.setColor(myLinkColor);
            final int xOffset = myImage.getIconWidth() - width - 10;
            final GraphicsConfig config = GraphicsUtil.paintWithAlpha(g2, Math.max(0, Math.min(1, myShowCopyAlpha)));
            g2.drawString(copyString, xOffset, yBase + y);
            config.restore();
            myLinks.add(new Link(new Rectangle(xOffset, yBase + y - fontAscent, width, fontHeight), COPY_URL));
            showCopyButton = false;
          }
          if (!line.isKeepWithNext() && !line.equals(lines.get(lines.size()-1))) {
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
          Font f = null;
          FontMetrics fm = null;
          try {
            if (!g2.getFont().canDisplay(c)) {
              f = g2.getFont();
              fm = fontmetrics;
              g2.setFont(new Font("Monospaced", f.getStyle(), f.getSize()));
              fontmetrics = g2.getFontMetrics();
            }
            final int cW = fontmetrics.charWidth(c);
            if (x + cW >= w) {
              lineFeed(indentX, s);
            }
            g2.drawChars(new char[]{c}, 0, 1, xBase + x, yBase + y);
            x += cW;
          } finally {
            if (f != null) {
              g2.setFont(f);
              fontmetrics = fm;
            }
          }
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

    private static class AboutBoxLine {
      private final String myText;
      private final boolean myBold;
      private final String myUrl;
      private boolean myKeepWithNext;

      public AboutBoxLine(final String text, final boolean bold, final String url) {
        myText = text;
        myBold = bold;
        myUrl = url;
      }

      public AboutBoxLine(final String text) {
        myText = text;
        myBold = false;
        myUrl = null;
      }

      public String getText() {
        return myText;
      }

      public boolean isBold() {
        return myBold;
      }

      public String getUrl() {
        return myUrl;
      }

      public boolean isKeepWithNext() {
        return myKeepWithNext;
      }

      public AboutBoxLine keepWithNext() {
        myKeepWithNext = true;
        return this;
      }
    }

    private static class Link {
      private final Rectangle myRectangle;
      private final String myUrl;

      private Link(Rectangle rectangle, String url) {
        myRectangle = rectangle;
        myUrl = url;
      }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleInfoSurface();
      }
      return accessibleContext;
    }

    protected class AccessibleInfoSurface extends AccessibleJPanel {
      @Override
      public String getAccessibleName() {
        String text = "System Information\n" + getText() + "\n" + getCopyrightText();
        return AccessibleContextUtil.replaceLineSeparatorsWithPunctuation(text);
      }
    }
  }

  public static class PopupPanel extends JPanel {

    private InfoSurface myInfoSurface;

    public PopupPanel(LayoutManager layout) {
      super(layout);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessiblePopupPanel();
      }
      return accessibleContext;
    }

    public void setInfoSurface(InfoSurface infoSurface) {
      myInfoSurface = infoSurface;
      add(infoSurface, BorderLayout.NORTH);
      new DumbAwareAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          copyInfoToClipboard(myInfoSurface.getText());
        }
      }.registerCustomShortcutSet(CustomShortcutSet.fromString("meta C", "control C"), this);
    }

    protected class AccessiblePopupPanel extends AccessibleJPanel implements AccessibleAction {
      @Override
      public String getAccessibleName() {
        ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
        return "About " + appInfo.getFullApplicationName();
      }

      @Override
      public String getAccessibleDescription() {
        if (myInfoSurface != null) {
          return "Press Copy key to copy system information to clipboard";
        }
        return null;
      }

      @Override
      public AccessibleAction getAccessibleAction() {
        return this;
      }

      @Override
      public int getAccessibleActionCount() {
        if(myInfoSurface != null)
          return 1;
        return 0;
      }

      @Override
      public String getAccessibleActionDescription(int i) {
        if (i == 0 && myInfoSurface != null)
          return "Copy system information to clipboard";
        return null;
      }

      @Override
      public boolean doAccessibleAction(int i) {
        if (i == 0 && myInfoSurface != null) {
          copyInfoToClipboard(myInfoSurface.getText());
          return true;
        }
        return false;
      }
    }
  }
}
