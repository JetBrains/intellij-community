// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.DefaultCaret;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class ConsentSettingsUi extends JPanel implements ConfigurableUi<List<Consent>> {
  final Collection<Pair<JCheckBox, Consent>> consentMapping = new ArrayList<>();
  private final boolean myPreferencesMode;

  public ConsentSettingsUi(boolean preferencesMode) {
    myPreferencesMode = preferencesMode;
    setLayout(new GridLayout(1, 1));
  }

  @Override
  public void reset(@NotNull List<Consent> consents) {
    consentMapping.clear();
    if (consents.isEmpty()) {
      JLabel label = new JLabel("There are no data-sharing options available", SwingConstants.CENTER);
      label.setVerticalAlignment(SwingConstants.CENTER);
      label.setOpaque(true);
      label.setBackground(JBColor.background());
      removeAll();
      add(label);
      return;
    }
    final JPanel body = new JPanel(new GridBagLayout());

    boolean addCheckBox = myPreferencesMode || consents.size() > 1;
    for (Iterator<Consent> it = consents.iterator(); it.hasNext(); ) {
      final Consent consent = it.next();
      final JComponent comp = createConsentElement(consent, addCheckBox);
      boolean lastConsent = !it.hasNext();
      if (lastConsent) {
        body.setBackground(comp.getBackground());
      } else {
        comp.setBorder(JBUI.Borders.emptyBottom(15));
      }
      body.add(comp, new GridBagConstraints(
        0, GridBagConstraints.RELATIVE, 1, 1, 1.0, lastConsent && myPreferencesMode ? 1.0 : 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, JBUI.insets(10, 0, 0, 0), 0, 0)
      );
    }
    if (!myPreferencesMode) {
      JLabel hintLabel = new JBLabel("You can always change this behavior in " + ShowSettingsUtil.getSettingsMenuName() + " | Appearance & Behavior | System Settings | Analytics.");
      hintLabel.setEnabled(false);
      hintLabel.setVerticalAlignment(SwingConstants.TOP);
      hintLabel.setFont(JBUI.Fonts.smallFont());
      //noinspection UseDPIAwareInsets
      body.add(hintLabel, new GridBagConstraints(
        0, GridBagConstraints.RELATIVE, 1, 1, 1.0,  1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
        new Insets(JBUI.scale(16), addCheckBox ? UIUtil.getCheckBoxTextHorizontalOffset(new JCheckBox()) : JBUI.scale(10), JBUI.scale(10), 0), 0, 0)
      );
    }
    body.setBorder(JBUI.Borders.empty(10));
    removeAll();
    add(new JBScrollPane(body, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED));
  }

  @NotNull
  private JComponent createConsentElement(Consent consent, boolean addCheckBox) {
    //TODO: refactor DocumentationComponent to use external link marker here, there and everywhere
    final JEditorPane viewer = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
    viewer.setFocusable(false);
    viewer.setCaret(new DefaultCaret(){
      @Override
      protected void adjustVisibility(Rectangle nloc) {
        //do nothing to avoid autoscroll
      }
    });
    viewer.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final URL url = e.getURL();
        if (url != null) {
          BrowserUtil.browse(url);
        }
      }
    });
    viewer.setText("<html>" + StringUtil.replace(consent.getText(), "\n", "<br>") + "</html>");
    StyleSheet styleSheet = ((HTMLDocument)viewer.getDocument()).getStyleSheet();
    //styleSheet.addRule("body {font-family: \"Segoe UI\", Tahoma, sans-serif;}");
    styleSheet.addRule("body {margin-top:0;padding-top:0;}");
    //styleSheet.addRule("body {font-size:" + JBUI.scaleFontSize(13) + "pt;}");
    styleSheet.addRule("h2, em {margin-top:" + JBUI.scaleFontSize(20) + "pt;}");
    styleSheet.addRule("h1, h2, h3, p, h4, em {margin-bottom:0;padding-bottom:0;}");
    styleSheet.addRule("p, h1 {margin-top:0;padding-top:"+JBUI.scaleFontSize(6)+"pt;}");
    styleSheet.addRule("li {margin-bottom:" + JBUI.scaleFontSize(6) + "pt;}");
    styleSheet.addRule("h2 {margin-top:0;padding-top:"+JBUI.scaleFontSize(13)+"pt;}");
    viewer.setCaretPosition(0);

    final JPanel pane = new JPanel(new BorderLayout());
    pane.setBackground(viewer.getBackground());
    int leftInset = JBUI.scale(10);
    if (addCheckBox) {
      final JCheckBox cb = new JBCheckBox(consent.getName(), consent.isAccepted());
      cb.setBackground(viewer.getBackground());
      cb.setFont(cb.getFont().deriveFont(Font.BOLD));
      leftInset = UIUtil.getCheckBoxTextHorizontalOffset(cb);
      consentMapping.add(Pair.create(cb, consent));
      pane.add(cb, BorderLayout.NORTH);
    } else {
      consentMapping.add(Pair.create(null, consent));
    }
    //noinspection UseDPIAwareBorders
    viewer.setBorder(new EmptyBorder(addCheckBox ? 0 : JBUI.scale(5), leftInset, 0, 0));
    pane.add(viewer, BorderLayout.CENTER);
    return pane;
  }

  @NotNull
  private List<Consent> getState() {
    final List<Consent> result = new ArrayList<>();
    for (Pair<JCheckBox, Consent> pair : consentMapping) {
      JCheckBox checkBox = pair.first;
      result.add(pair.second.derive(checkBox == null || checkBox.isSelected()));
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
    if (myPreferencesMode) {
      ConsentOptions.getInstance().setConsents(consents);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }
}
