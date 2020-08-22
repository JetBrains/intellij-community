// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.AboutPopupDescriptionProvider;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.MathUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.notNull;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * @author Konstantin Bulenkov
 */
public final class AboutPopup {
  private static JBPopup ourPopup;
  private static final Logger LOG = Logger.getInstance(AboutPopup.class);
  /**
   * File with third party libraries HTML content,
   * see the same constant at org.jetbrains.intellij.build.impl.DistributionJARsBuilder#THIRD_PARTY_LIBRARIES_FILE_PATH
   */
  private static final String THIRD_PARTY_LIBRARIES_FILE_PATH = "license/third-party-libraries.html";

  public static void show(@Nullable Window window, boolean showDebugInfo) {
    ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();

    final PopupPanel panel = new PopupPanel(new BorderLayout());
    Icon image = IconLoader.getIcon(appInfo.getAboutImageUrl());
    if (appInfo.showLicenseeInfo()) {
      final InfoSurface infoSurface = new InfoSurface(image, showDebugInfo);
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
      @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
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

  private static final class InfoSurface extends JPanel {
    private static final ExtensionPointName<AboutPopupDescriptionProvider> EP_NAME = new ExtensionPointName<>("com.intellij.aboutPopupDescriptionProvider");

    private final Color myColor;
    private final Color myLinkColor;
    private final Icon myImage;
    private final boolean myShowDebugInfo;
    private Font myFont;
    private Font myBoldFont;
    private final List<AboutBoxLine> myLines = new ArrayList<>();
    private final StringBuilder myInfo = new StringBuilder();
    private final List<Link> myLinks = new ArrayList<>();
    private Link myActiveLink;
    private boolean myShowCopy = false;
    private float myShowCopyAlpha;
    private final Alarm myAlarm = new Alarm();

    InfoSurface(Icon image, final boolean showDebugInfo) {
      ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

      myImage = image;
      //noinspection UseJBColor
      myColor = Color.white;
      long aboutLinkColor = appInfo.getAboutLinkColor();
      //noinspection UseJBColor
      myLinkColor = aboutLinkColor == -1 ? JBUI.CurrentTheme.Link.linkColor() : new Color((int)aboutLinkColor, aboutLinkColor > 0xffffff);
      myShowDebugInfo = showDebugInfo;

      setOpaque(false);
      setBackground(myColor);
      setFocusable(true);

      String appName = appInfo.getFullApplicationName();
      String edition = ApplicationNamesInfo.getInstance().getEditionName();
      if (edition != null) appName += " (" + edition + ")";
      myLines.add(new AboutBoxLine(appName, true));
      appendLast();

      String buildInfo = IdeBundle.message("about.box.build.number", appInfo.getBuild().asString());
      Calendar cal = appInfo.getBuildDate();
      String buildDate = "";
      if (appInfo.getBuild().isSnapshot()) {
        buildDate = new SimpleDateFormat("HH:mm, ").format(cal.getTime());
      }
      buildDate += DateFormatUtil.formatAboutDialogDate(cal.getTime());
      buildInfo += IdeBundle.message("about.box.build.date", buildDate);
      myLines.add(new AboutBoxLine(buildInfo));
      appendLast();

      myLines.add(new AboutBoxLine(""));

      LicensingFacade la = LicensingFacade.getInstance();
      if (la != null) {
        final String licensedTo = la.getLicensedToMessage();
        if (licensedTo != null) {
          myLines.add(new AboutBoxLine(licensedTo, true));
          appendLast();
        }
        for (String message : la.getLicenseRestrictionsMessages()) {
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

      for (AboutPopupDescriptionProvider aboutInfoProvider : EP_NAME.getExtensions()) {
        String description = aboutInfoProvider.getDescription();
        if (description == null) continue;

        String[] lines = description.split("[\n]+");

        if (lines.length == 0) continue;

        myLines.add(new AboutBoxLine(""));

        for (String line : lines) {
          myLines.add(new AboutBoxLine(line));
          appendLast();
        }
      }

      myLines.add(new AboutBoxLine(""));
      myLines.add(new AboutBoxLine(""));
      myLines.add(new AboutBoxLine(IdeBundle.message("about.box.powered.by") + " ").keepWithNext());

      final String thirdPartyLibraries = loadThirdPartyLibraries();
      if (thirdPartyLibraries != null) {
        myLines.add(new AboutBoxLine(IdeBundle.message("about.box.open.source.software"),
                                     () -> showOpenSoftwareSources(thirdPartyLibraries)));
      }
      else {
        // When compiled from sources, third-party-libraries.html file isn't generated, so window can't be shown
        myLines.add(new AboutBoxLine(IdeBundle.message("about.box.open.source.software")));
      }

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (myActiveLink != null) {
            event.consume();
            myActiveLink.actionPerformed(new ActionEvent(event.getSource(), event.getID(), event.paramString()));
          }

          if (getCopyIconArea().contains(event.getPoint())) {
              copyInfoToClipboard(getText());
              if (ourPopup != null) {
                ourPopup.cancel();
              }
          }
        }

        static final double maxAlpha = 1.0;
        static final double fadeStep = 0.05;
        static final int animationDelay = 15;

        @Override
        public void mouseEntered(MouseEvent e) {
          if (!myShowCopy) {
            myShowCopy = true;
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(new Runnable() {
              @Override
              public void run() {
                if (myShowCopyAlpha < maxAlpha) {
                  myShowCopyAlpha = (float)Math.min(myShowCopyAlpha + fadeStep, maxAlpha);
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
          boolean hadLink = (myActiveLink != null) || getCursor() == Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
          myActiveLink = null;
          Point point = event.getPoint();
          for (Link link : myLinks) {
            if (link.myRectangle.contains(point)) {
              myActiveLink = link;
              if (!hadLink) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              }
              return;
            }
          }

          if (getCopyIconArea().contains(point)) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return;
          }

          if (hadLink && myActiveLink == null) {
            setCursor(Cursor.getDefaultCursor());
          }
        }
      });
    }

    private static @Nullable String loadThirdPartyLibraries() {
      final File thirdPartyLibrariesFile = new File(PathManager.getHomePath(), THIRD_PARTY_LIBRARIES_FILE_PATH);
      if (thirdPartyLibrariesFile.isFile()) {
        try {
          return FileUtil.loadFile(thirdPartyLibrariesFile);
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
      return null;
    }

    private static Rectangle getCopyIconArea() {
      return new Rectangle(getCopyIconCoord(), JBUI.size(16));
    }

    private void appendLast() {
      myInfo.append(myLines.get(myLines.size() - 1).getText()).append("\n");
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);

      Graphics2D g2 = (Graphics2D)g;
      GraphicsConfig config = new GraphicsConfig(g);
      UISettings.setupAntialiasing(g);

      Font labelFont = JBFont.label();
      if (SystemInfo.isWindows) {
        labelFont = JBUI.Fonts.create(SystemInfo.isWinVistaOrNewer ? "Segoe UI" : "Tahoma", 14);
      }

      int startFontSize = 14;
      for (int labelSize = JBUIScale.scale(startFontSize); labelSize != JBUIScale.scale(6); labelSize -= 1) {
        myLinks.clear();
        g2.setPaint(myColor);
        myImage.paintIcon(this, g2, 0, 0);

        g2.setColor(myColor);
        TextRenderer renderer = createTextRenderer(g2);
        UIUtil.setupComposite(g2);
        myFont = labelFont.deriveFont(Font.PLAIN, labelSize);
        myBoldFont = labelFont.deriveFont(Font.BOLD, labelSize + 1);
        try {
          renderer.render(0, 0, myLines);
          break;
        }
        catch (TextRenderer.OverflowException ignore) { }
      }

      ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
      int[] aboutLogoRect = appInfo.getAboutLogoRect();
      if (aboutLogoRect != null) {
        myLinks.add(new Link(new JBRectangle(aboutLogoRect[0], aboutLogoRect[1], aboutLogoRect[2], aboutLogoRect[3]), appInfo.getCompanyURL()));
      }

      long copyrightForeground = appInfo.getCopyrightForeground();
      Color color;
      if (copyrightForeground < 0) {
        color = JBColor.BLACK;
      }
      else {
        //noinspection UseJBColor
        color = new Color((int)copyrightForeground, copyrightForeground > 0xffffff);
      }
      g2.setColor(color);
      if (SystemInfo.isMac) {
        g2.setFont(JBUI.Fonts.miniFont());
      }
      else {
        g2.setFont(JBUI.Fonts.create(SystemInfo.isWinVistaOrNewer ? "Segoe UI" : "Tahoma", 12));
      }

      g2.setColor(createColor(appInfo.getAboutForeground()));

      JBPoint copyrightCoord = getCopyrightCoord();
      g2.drawString(getCopyrightText(), copyrightCoord.x, copyrightCoord.y);
      if (myShowDebugInfo) {
        g2.setColor(createColor(appInfo.getAboutForeground()));
        for (Link link : myLinks) {
          g2.drawRect(link.myRectangle.x, link.myRectangle.y, link.myRectangle.width, link.myRectangle.height);
        }
      }

      config.restore();
      if (myShowCopy) {
        JBPoint coord = getCopyIconCoord();
        float alpha = myShowCopyAlpha;
        config = new GraphicsConfig(g).paintWithAlpha(MathUtil.clamp(alpha, 0f, 1f));
        AllIcons.General.CopyHovered.paintIcon(this, g, coord.x, coord.y);
        config.restore();
      }
    }

    private static @NotNull String getCopyrightText() {
      ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
      return "Copyright © " + appInfo.getCopyrightStart() + '–' + Calendar.getInstance(Locale.US).get(Calendar.YEAR) + ' ' + appInfo.getCompanyName();
    }

    private @NotNull TextRenderer createTextRenderer(Graphics2D g) {
      Rectangle r = getTextRendererRect();
      return new TextRenderer(r.x, r.y, r.width, r.height, g);
    }

    private static JBRectangle getTextRendererRect() {
      return new JBRectangle(115, 156, 500, 220);
    }

    private static JBPoint getCopyrightCoord() {
      return new JBPoint(115, 395);
    }

    private static JBPoint getCopyIconCoord() {
      return new JBPoint(66, 156);
    }

    public String getText() {
      return myInfo + getExtraInfo();
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

      TextRenderer(final int xBase, final int yBase, final int w, final int h, final Graphics2D g2) {
        this.xBase = xBase;
        this.yBase = yBase;
        this.w = w;
        this.h = h;
        this.g2 = g2;

        if (SystemInfo.isWindows) {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
      }

      public void render(int indentX, int indentY, List<? extends AboutBoxLine> lines) throws OverflowException {
        x = indentX;
        y = indentY;
        ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
        for (AboutBoxLine line : lines) {
          final String s = line.getText();
          setFont(line.isBold() ? myBoldFont : myFont);
          if (line.isRunnable()) {
            g2.setColor(myLinkColor);
            FontMetrics metrics = g2.getFontMetrics(font);
            final Rectangle myRectangle = new Rectangle(xBase + x, yBase + y - fontAscent, metrics.stringWidth(s + " "), fontHeight);
            myLinks.add(new Link(myRectangle, line));
          }
          else {
            g2.setColor(createColor(appInfo.getAboutForeground()));
          }
          renderString(s, indentX);
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
          renderWord(word, indentX);
        }
      }

      private void renderWord(final String s, final int indentX) throws OverflowException {
        FontMetrics fm = null;
        Font f = null;
        try {
          for (int j = 0; j != s.length(); ++j) {
            final char c = s.charAt(j);
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
          }
          x += fontmetrics.charWidth(' ');
        }
        finally {
          if (f != null) {
            g2.setFont(f);
            fontmetrics = fm;
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

    private static class AboutBoxLine implements ActionListener {
      private final String myText;
      private final boolean myBold;
      private boolean myKeepWithNext;
      private final Runnable myRunnable;

      AboutBoxLine(final String text, final boolean bold) {
        myText = text;
        myBold = bold;
        myRunnable = null;
      }

      AboutBoxLine(final String text) {
        this(text, false);
      }

      AboutBoxLine(final String text, @NotNull Runnable runnable) {
        myText = text;
        myBold = false;
        myRunnable = runnable;
      }

      public String getText() {
        return myText;
      }

      public boolean isBold() {
        return myBold;
      }

      public boolean isRunnable() {
        return myRunnable != null;
      }

      public boolean isKeepWithNext() {
        return myKeepWithNext;
      }

      public AboutBoxLine keepWithNext() {
        myKeepWithNext = true;
        return this;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        if (myRunnable != null) myRunnable.run();
      }
    }

    private static final class Link implements ActionListener {
      private final Rectangle myRectangle;
      private final ActionListener myAction;

      private Link(Rectangle rectangle, ActionListener action) {
        myRectangle = rectangle;
        myAction = action;
      }

      private Link(Rectangle rectangle, String url) {
        myRectangle = rectangle;
        myAction = e -> BrowserUtil.browse(url);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        myAction.actionPerformed(e);
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

  @SuppressWarnings("UseJBColor")
  private static Color createColor(long rgba) {
    return rgba == -1 ? Color.BLACK : new Color((int)rgba, rgba > 0xffffff);
  }

  private static @NotNull String getExtraInfo() {
    String extraInfo = SystemInfo.getOsNameAndVersion() + "\n";

    extraInfo += "GC: " + ManagementFactory.getGarbageCollectorMXBeans().stream()
             .map(GarbageCollectorMXBean::getName).collect(StringUtil.joining()) + "\n";

    extraInfo += "Memory: " + (Runtime.getRuntime().maxMemory() / FileUtilRt.MEGABYTE) + "M" + "\n";

    extraInfo += "Cores: " + Runtime.getRuntime().availableProcessors() + "\n";

    String registryKeys = Registry.getAll().stream().filter(RegistryValue::isChangedFromDefault)
      .map(v -> v.getKey() + "=" + v.asString()).collect(StringUtil.joining());
    if (!StringUtil.isEmpty(registryKeys)) {
      extraInfo += "Registry: " + registryKeys + "\n";
    }

    String nonBundledPlugins = Arrays.stream(PluginManagerCore.getPlugins()).filter(p -> !p.isBundled() && p.isEnabled())
      .map(p -> p.getPluginId().getIdString()).collect(StringUtil.joining());
    if (!StringUtil.isEmpty(nonBundledPlugins)) {
      extraInfo += "Non-Bundled Plugins: " + nonBundledPlugins;
    }

    if (SystemInfo.isUnix && !SystemInfo.isMac) {
      extraInfo += "\nCurrent Desktop: " + notNull(System.getenv("XDG_CURRENT_DESKTOP"), "Undefined");
    }
    return extraInfo;
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
      DumbAwareAction.create(e -> copyInfoToClipboard(myInfoSurface.getText()))
        .registerCustomShortcutSet(CustomShortcutSet.fromString("meta C", "control C"), this);
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

  public static void showOpenSoftwareSources(@NotNull String htmlText) {
    DialogWrapper dialog = new DialogWrapper(true) {
      {
        init();
        setAutoAdjustable(false);
        setOKButtonText(CommonBundle.message("close.action.name"));
      }

      @Override
      protected JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(JBUIScale.scale(5), JBUIScale.scale(5)));

        JEditorPane viewer = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
        viewer.setFocusable(true);
        viewer.addHyperlinkListener(new BrowserHyperlinkListener());

        String resultHtmlText = getScaledHtmlText();
        if (StartupUiUtil.isUnderDarcula()) {
          resultHtmlText = resultHtmlText.replaceAll("779dbd", "5676a0");
        }
        viewer.setText(resultHtmlText);

        StyleSheet styleSheet = ((HTMLDocument)viewer.getDocument()).getStyleSheet();
        styleSheet.addRule("body {font-family: \"Segoe UI\", Tahoma, sans-serif;}");
        styleSheet.addRule("body {margin-top:0;padding-top:0;}");
        styleSheet.addRule("body {font-size:" + JBUIScale.scaleFontSize((float)14) + "pt;}");

        viewer.setCaretPosition(0);
        viewer.setBorder(JBUI.Borders.empty(0, 5, 5, 5));

        JBScrollPane scrollPane = new JBScrollPane(viewer, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);

        centerPanel.add(scrollPane, BorderLayout.CENTER);
        return centerPanel;
      }

      @Override
      protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
      }

      private @NotNull String getScaledHtmlText() {
        final Pattern pattern = Pattern.compile("(\\d+)px");
        final Matcher matcher = pattern.matcher(htmlText);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
          matcher.appendReplacement(sb, JBUIScale.scale(Integer.parseInt(matcher.group(1))) + "px");
        }
        matcher.appendTail(sb);

        return sb.toString();
      }
    };

    ourPopup.cancel();
    dialog.setTitle(String.format("Third-Party Software Used by %s %s",
                                  ApplicationNamesInfo.getInstance().getFullProductName(),
                                  ApplicationInfo.getInstance().getFullVersion()));
    dialog.setSize(JBUIScale.scale(750), JBUIScale.scale(650));
    dialog.show();
  }
}
