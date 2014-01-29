package com.intellij.util.net.ssl;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Map;

import static com.intellij.util.net.ssl.CertificateWrapper.CommonField;

/**
 * @author Mikhail Golubev
 */
public class CertificateInfo {
  private static DateFormat DATE_FORMAT = DateFormat.getDateInstance(DateFormat.SHORT);

  private JPanel myPanel;
  private final CertificateWrapper myCertificateWrapper;

  public CertificateInfo(@NotNull X509Certificate certificate) {
    myCertificateWrapper = new CertificateWrapper(certificate);

    FormBuilder builder = FormBuilder.createFormBuilder();

    // I'm not using separate panels and form builders to preserve alignment of labels
    builder = updateBuilderWithTitle(builder, "Issued To");
    builder = updateBuilderWithPrincipalData(builder, myCertificateWrapper.getSubjectFields());
    builder = updateBuilderWithTitle(builder, "Issued By");
    builder = updateBuilderWithPrincipalData(builder, myCertificateWrapper.getIssuerFields());
    builder = updateBuilderWithTitle(builder, "Validity Period");
    builder = builder
      .setIndent(IdeBorderFactory.TITLED_BORDER_INDENT)
      .addLabeledComponent("Valid from:", new JBLabel(DATE_FORMAT.format(myCertificateWrapper.getNotBefore())))
      .addLabeledComponent("Valid until:", new JBLabel(DATE_FORMAT.format(myCertificateWrapper.getNotAfter())));
    builder = builder.setIndent(0);
    builder = updateBuilderWithTitle(builder, "Fingerprints");
    builder = builder.setIndent(IdeBorderFactory.TITLED_BORDER_INDENT);
    builder = builder.addLabeledComponent("SHA-256:", getTextPane(formatHex(myCertificateWrapper.getSha256Fingerprint())));
    builder = builder.addLabeledComponent("SHA-1:", getTextPane(formatHex(myCertificateWrapper.getSha1Fingerprint())));
    myPanel.add(builder.getPanel(), BorderLayout.NORTH);
  }

  @NotNull
  private static String formatHex(@NotNull String hex) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < hex.length(); i += 2) {
      // split at 16th byte
      if (i == 32) {
        builder.append('\n');
      }
      builder.append(hex.substring(i, i + 2));
      builder.append(' ');
    }
    if (hex.length() > 0) {
      builder.deleteCharAt(builder.length() - 1);
    }
    return builder.toString().toUpperCase();
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public X509Certificate getCertificate() {
    return myCertificateWrapper.getCertificate();
  }

  private static FormBuilder updateBuilderWithPrincipalData(FormBuilder builder, Map<String, String> fields) {
    builder = builder.setIndent(IdeBorderFactory.TITLED_BORDER_INDENT);
    for (CommonField field : CommonField.values()) {
      String value = fields.get(field.getShortName());
      if (value == null) {
        continue;
      }
      String label = String.format("<html>%s (<b>%s</b>)</html>", field.getShortName(), field.getLongName());
      builder = builder.addLabeledComponent(label, new JBLabel(value));
    }
    return builder.setIndent(0);
  }

  private static FormBuilder updateBuilderWithTitle(FormBuilder builder, String title) {
    return builder.addComponent(new TitledSeparator(title), IdeBorderFactory.TITLED_BORDER_TOP_INSET);
  }

  private static JComponent getTextPane(String text) {
    JTextPane pane = new JTextPane();
    pane.setOpaque(false);
    pane.setEditable(false);
    pane.setContentType("text/plain");
    pane.setText(text);
    //Messages.installHyperlinkSupport(pane);
    return pane;
  }
}
