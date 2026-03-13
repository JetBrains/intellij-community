// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.gdpr.localConsents.LocalConsentOptions;
import com.intellij.ide.gdpr.ui.consents.AiDataCollectionConsentUi;
import com.intellij.ide.gdpr.ui.consents.ConsentUi;
import com.intellij.ide.gdpr.ui.consents.DefaultConsentUi;
import com.intellij.ide.gdpr.ui.consents.ErrorsAutoReportConsentUi;
import com.intellij.ide.gdpr.ui.consents.TraceDataCollectionConsentUI;
import com.intellij.ide.gdpr.ui.consents.UsageStatisticsConsentUi;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.GridLayout;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class ConsentSettingsUi extends JPanel implements ConfigurableUi<List<Consent>> {
  private final Collection<ConsentStateSupplier> consentMapping = new ArrayList<>();
  private final boolean myPreferencesMode;
  private final boolean myIsJetBrainsVendor;

  public ConsentSettingsUi(boolean preferencesMode) {
    this(preferencesMode, ApplicationInfoImpl.getShadowInstance().isVendorJetBrains());
  }

  @ApiStatus.Internal
  public ConsentSettingsUi(boolean preferencesMode, boolean isJetBrainsVendor) {
    myPreferencesMode = preferencesMode;
    myIsJetBrainsVendor = isJetBrainsVendor;
    setLayout(new GridLayout(1, 1));
  }

  @Override
  public void reset(@NotNull List<Consent> consents) {
    consentMapping.clear();
    removeAll();

    if (consents.isEmpty()) {
      add(ConsentSettingsBodyKt.createNoOptionsConsentSettings(myPreferencesMode));
      return;
    }

    JBScrollPane scrollPane =
      new JBScrollPane(ConsentSettingsBodyKt.createConsentSettings(consentMapping, myPreferencesMode, myIsJetBrainsVendor, consents),
                       VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setBorder(JBUI.Borders.empty());
    add(scrollPane);
  }

  static int getParagraphSpace() {
    return JBUIScale.scale(10);
  }

  private static @NlsSafe String getParagraphTag() {
    return "<p style=\"margin-bottom:" + getParagraphSpace() + "px;\">";
  }

  static @NotNull JEditorPane createSingleConsent(Consent consent) {
    //TODO: refactor DocumentationComponent to use external link marker here, there and everywhere
    JEditorPane viewer = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
    viewer.setOpaque(false);
    viewer.setFocusable(false);
    UIUtil.doNotScrollToCaret(viewer);
    viewer.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        final URL url = e.getURL();
        if (url != null) {
          BrowserUtil.browse(url);
        }
      }
    });
    viewer.setText("<html><body>" + replaceParagraphs(consent.getText()) + "</body></html>");
    StyleSheet styleSheet = ((HTMLDocument)viewer.getDocument()).getStyleSheet();
    //styleSheet.addRule("body {font-family: \"Segoe UI\", Tahoma, sans-serif;}");
    styleSheet.addRule("body {margin-top:0;padding-top:0;}");
    //styleSheet.addRule("body {font-size:" + JBUI.scaleFontSize(13) + "pt;}");
    styleSheet.addRule("h2, em {margin-top:" + JBUIScale.scaleFontSize((float)20) + "pt;}");
    styleSheet.addRule("h1, h2, h3, p, h4, em {margin-bottom:0;padding-bottom:0;}");
    styleSheet.addRule("p, h1 {margin-top:0;padding-top:" + JBUIScale.scaleFontSize((float)6) + "pt;}");
    styleSheet.addRule("li {margin-bottom:" + JBUIScale.scaleFontSize((float)6) + "pt;}");
    styleSheet.addRule("h2 {margin-top:0;padding-top:" + JBUIScale.scaleFontSize((float)13) + "pt;}");
    styleSheet.addRule("a, a:link {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED) + ";}");
    styleSheet.addRule("a:hover {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.HOVERED) + ";}");
    styleSheet.addRule("a:active {color:#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.PRESSED) + ";}");
    viewer.setCaretPosition(0);

    return viewer;
  }

  @ApiStatus.Internal
  public static @NotNull ConsentUi getConsentUi(Consent consent) {
    if (ConsentOptions.condUsageStatsConsent().test(consent)) {
      return new UsageStatisticsConsentUi(consent);
    }
    if (ConsentOptions.condAiDataCollectionConsent().test(consent)) {
      return new AiDataCollectionConsentUi(consent);
    }
    if (LocalConsentOptions.condTraceDataCollectionComLocalConsent().test(consent) ||
        LocalConsentOptions.condTraceDataCollectionNonComLocalConsent().test(consent)) {
      return new TraceDataCollectionConsentUI(consent);
    }
    if (ConsentOptions.condEAAutoReportConsent().test(consent)) {
      return new ErrorsAutoReportConsentUi(consent);
    }
    return new DefaultConsentUi(consent);
  }

  @Contract(pure = true)
  private static String replaceParagraphs(String text) {
    return getParagraphTag() + StringUtil.replace(text, "\n", "</p>" + getParagraphTag()) + "</p>";
  }

  private @NotNull List<Consent> getState() {
    final List<Consent> result = new ArrayList<>();
    for (ConsentStateSupplier supplier : consentMapping) {
      result.add(supplier.consent.derive(supplier.getState()));
    }
    result.sort(Comparator.comparing(Consent::getId));
    return result;
  }


  @Override
  public boolean isModified(@NotNull List<Consent> consents) {
    List<Consent> state = getState();
    if (consents.size() != state.size()) return true;
    for (int i = 0; i < state.size(); i++) {
      Consent consent1 = state.get(i);
      Consent consent2 = consents.get(i);
      if (!consent1.equals(consent2)) return true;
      if (consent1.isAccepted() != consent2.isAccepted()) return true;
    }

    return false;
  }

  @Override
  public void apply(@NotNull List<Consent> consents) {
    consents.clear();
    consents.addAll(getState());
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  record ConsentStateSupplier(Consent consent, Supplier<Boolean> getter) {

    boolean getState() {
      return getter.get();
    }
  }
}
