// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.gdpr.ui.consents.*;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class ConsentSettingsUi extends JPanel implements ConfigurableUi<List<Consent>> {
  final Collection<ConsentStateSupplier> consentMapping = new ArrayList<>();
  private final boolean myPreferencesMode;

  public ConsentSettingsUi(boolean preferencesMode) {
    myPreferencesMode = preferencesMode;
    setLayout(new GridLayout(1, 1));
  }

  @Override
  public void reset(@NotNull List<Consent> consents) {
    consentMapping.clear();
    if (consents.isEmpty()) {
      JLabel label = new JLabel(IdeBundle.message("gdpr.label.there.are.no.data.sharing.options.available"), SwingConstants.CENTER);
      label.setVerticalAlignment(SwingConstants.CENTER);
      label.setOpaque(true);
      label.setBackground(JBColor.background());
      removeAll();
      add(label);
      return;
    }
    final JPanel body = new JPanel(new GridBagLayout());
    body.setBackground(myPreferencesMode ? UIUtil.getPanelBackground() : UIUtil.getEditorPaneBackground());

    boolean addCheckBox = myPreferencesMode || consents.size() > 1;
    for (Iterator<Consent> it = consents.iterator(); it.hasNext(); ) {
      final Consent consent = it.next();
      final JComponent comp = createConsentElement(consent, addCheckBox);
      body.add(comp, new GridBagConstraints(
        0, GridBagConstraints.RELATIVE, 1, 1, 1.0, !it.hasNext() && myPreferencesMode ? 1.0 : 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
        it.hasNext() ? JBUI.insets(0, 0, 10, 0) : JBInsets.emptyInsets(), 0, 0)
      );
    }
    if (!ConsentOptions.getInstance().isEAP()) {
      addHintLabel(body, IdeBundle.message("gdpr.hint.text.apply.to.all.installed.products", ApplicationInfoImpl.getShadowInstance().getShortCompanyName()));
    }
    if (!myPreferencesMode) {
      addHintLabel(body, IdeBundle.message("gdpr.hint.text.you.can.always.change.this.behavior", ShowSettingsUtil.getSettingsMenuName()));
    }
    if (!myPreferencesMode) {
      body.setBorder(JBUI.Borders.empty(10));
    }
    removeAll();
    JBScrollPane scrollPane = new JBScrollPane(body, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setBorder(JBUI.Borders.empty());
    add(scrollPane);
  }

  private static void addHintLabel(JPanel body, @NlsContexts.HintText String text) {
    JLabel hintLabel = new JBLabel(text);
    hintLabel.setForeground(UIUtil.getContextHelpForeground());
    hintLabel.setVerticalAlignment(SwingConstants.TOP);
    hintLabel.setFont(JBUI.Fonts.smallFont());
    //noinspection UseDPIAwareInsets
    body.add(hintLabel, new GridBagConstraints(
      0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
      new Insets(JBUIScale.scale(16), 0, JBUIScale.scale(10), 0), 0, 0)
    );
  }

  private static @NlsSafe String getParagraphTag() {
    return "<p style=\"margin-bottom:" + JBUIScale.scale(10) + "px;\">";
  }

  private @NotNull JComponent createConsentElement(Consent consent, boolean addCheckBox) {
    //TODO: refactor DocumentationComponent to use external link marker here, there and everywhere
    final JPanel pane;
    final ConsentUi consentUi = getConsentUi(consent);
    final ConsentForcedState forcedState = consentUi.getForcedState();
    if (addCheckBox) {
      String checkBoxText = consentUi.getCheckBoxText();
      final JCheckBox cb = new JBCheckBox(checkBoxText, consent.isAccepted());
      ConsentStateSupplier stateSupplier;
      if (forcedState instanceof ConsentForcedState.ExternallyDisabled) {
        stateSupplier = new ConsentStateSupplier(consent, () -> consent.isAccepted());
        cb.setEnabled(false);
        cb.setSelected(false);
      }
      else if (forcedState instanceof ConsentForcedState.AlwaysEnabled) {
        stateSupplier = new ConsentStateSupplier(consent, () -> consent.isAccepted());
        cb.setEnabled(false);
        cb.setSelected(true);
      }
      else {
        stateSupplier = new ConsentStateSupplier(consent, () -> cb.isSelected());
      }
      //noinspection HardCodedStringLiteral
      pane = UI.PanelFactory.panel(cb).withComment(getParagraphTag()
                                                   + StringUtil.replace(consentUi.getCheckBoxCommentText(), "\n", "</p>" + getParagraphTag()) + "</p>").createPanel();
      cb.setOpaque(false);
      consentMapping.add(stateSupplier);
    } else {
      pane = new JPanel(new BorderLayout());
      final JEditorPane viewer = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
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
      pane.add(viewer, BorderLayout.CENTER);
      consentMapping.add(new ConsentStateSupplier(consent, () -> true));
    }
    pane.setOpaque(false);

    if (forcedState instanceof ConsentForcedState.ExternallyDisabled externallyDisabledState) {
      return wrapPanelWithWarning(pane, externallyDisabledState.getDescription());
    }
    if (forcedState instanceof ConsentForcedState.AlwaysEnabled alwaysEnabledState) {
      return wrapPanelWithWarning(pane, alwaysEnabledState.getDescription());
    }
    return pane;
  }

  private static @NotNull ConsentUi getConsentUi(Consent consent) {
    if (ConsentOptions.condUsageStatsConsent().test(consent)) {
      return new UsageStatisticsConsentUi(consent);
    }
    if (ConsentOptions.condAiDataCollectionConsent().test(consent)) {
      return new AiDataCollectionConsentUi(consent);
    }
    return new DefaultConsentUi(consent);
  }

  private static JPanel wrapPanelWithWarning(JPanel panel, @NlsContexts.DetailedDescription String warningText) {
    return UI.PanelFactory.panel(panel).withCommentIcon(AllIcons.General.Warning).withComment(warningText).createPanel();
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

  private record ConsentStateSupplier(Consent consent, Supplier<Boolean> getter) {

    boolean getState() {
      return getter.get();
    }
  }
}
