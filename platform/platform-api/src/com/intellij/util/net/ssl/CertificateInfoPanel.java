// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net.ssl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Map;

import static com.intellij.util.net.ssl.CertificateWrapper.CommonField;
import static com.intellij.util.net.ssl.CertificateWrapper.NOT_AVAILABLE;

/**
 * @author Mikhail Golubev
 */
public class CertificateInfoPanel extends JPanel {
  private static final DateFormat DATE_FORMAT = DateFormat.getDateInstance(DateFormat.SHORT);

  private final CertificateWrapper myCertificateWrapper;

  public CertificateInfoPanel(@NotNull X509Certificate certificate) {
    myCertificateWrapper = new CertificateWrapper(certificate);
    setLayout(new BorderLayout());

    FormBuilder builder = FormBuilder.createFormBuilder();

    // I'm not using separate panels and form builders to preserve alignment of labels
    updateBuilderWithTitle(builder, IdeBundle.message("section.title.issued.to"));
    updateBuilderWithPrincipalData(builder, myCertificateWrapper.getSubjectFields());
    updateBuilderWithTitle(builder, IdeBundle.message("section.title.issued.by"));
    updateBuilderWithPrincipalData(builder, myCertificateWrapper.getIssuerFields());
    updateBuilderWithTitle(builder, IdeBundle.message("section.title.validity.period"));
    @SuppressWarnings("HardCodedStringLiteral") String notBefore = DATE_FORMAT.format(myCertificateWrapper.getNotBefore());
    @SuppressWarnings("HardCodedStringLiteral") String notAfter = DATE_FORMAT.format(myCertificateWrapper.getNotAfter());
    builder = builder
      .setFormLeftIndent(IdeBorderFactory.TITLED_BORDER_INDENT)
      .addLabeledComponent(IdeBundle.message("label.valid.from"), createColoredComponent(notBefore, IdeBundle.message("label.certificate.not.yet.valid"), myCertificateWrapper.isNotYetValid()))
      .addLabeledComponent(IdeBundle.message("label.valid.until"), createColoredComponent(notAfter, IdeBundle.message("label.certificate.expired"), myCertificateWrapper.isExpired()));
    builder.setFormLeftIndent(0);
    updateBuilderWithTitle(builder, IdeBundle.message("section.title.fingerprints"));
    builder.setFormLeftIndent(IdeBorderFactory.TITLED_BORDER_INDENT);
    //noinspection HardCodedStringLiteral
    builder.addLabeledComponent("SHA-256:", getTextPane(formatHex(myCertificateWrapper.getSha256Fingerprint(), true)));
    //noinspection HardCodedStringLiteral
    builder.addLabeledComponent("SHA-1:", getTextPane(formatHex(myCertificateWrapper.getSha1Fingerprint(), true)));
    add(builder.getPanel(), BorderLayout.NORTH);
  }

  @NotNull
  @NlsSafe
  public static String formatHex(@NotNull String hex, boolean split) {
    if (NOT_AVAILABLE.equals(hex)) return hex;

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < hex.length(); i += 2) {
      // split at 16th byte
      if (split && i == 32) {
        builder.append('\n');
      }
      builder.append(hex, i, i + 2);
      builder.append(' ');
    }
    if (hex.length() > 0) {
      builder.deleteCharAt(builder.length() - 1);
    }
    return StringUtil.toUpperCase(builder.toString());
  }

  public X509Certificate getCertificate() {
    return myCertificateWrapper.getCertificate();
  }

  private static void updateBuilderWithPrincipalData(FormBuilder builder, Map<String, @NlsSafe String> fields) {
    builder = builder.setFormLeftIndent(IdeBorderFactory.TITLED_BORDER_INDENT);
    for (CommonField field : CommonField.values()) {
      String value = fields.get(field.getShortName());
      if (value == null) {
        continue;
      }
      String label = new HtmlBuilder()
        .append(field.getShortName())
        .append(" (")
        .append(HtmlChunk.text(field.getLongName()).bold())
        .append(")")
        .wrapWith("html")
        .toString();
      builder = builder.addLabeledComponent(label, new JBLabel(value));
    }
    builder.setFormLeftIndent(0);
  }

  private static void updateBuilderWithTitle(FormBuilder builder, @Nls String title) {
    builder.addComponent(new TitledSeparator(title), IdeBorderFactory.TITLED_BORDER_TOP_INSET);
  }

  private static JComponent getTextPane(@Nls String text) {
    JTextPane pane = new JTextPane();
    pane.setOpaque(false);
    pane.setEditable(false);
    pane.setContentType("text/plain");
    pane.setText(text);
    //Messages.installHyperlinkSupport(pane);
    return pane;
  }

  private static JComponent createColoredComponent(@NlsContexts.Label String mainText, @Nls String errorText, boolean hasError) {
    SimpleColoredComponent component = new SimpleColoredComponent();
    if (hasError) {
      component.append(mainText + " (" + errorText + ")", new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED));
    } else {
      component.append(mainText);
    }
    return component;
  }
}
