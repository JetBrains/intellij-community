package com.intellij.util.net.ssl;

import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
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
public class CertificateInfoPanel extends JPanel {
  private static DateFormat DATE_FORMAT = DateFormat.getDateInstance(DateFormat.SHORT);

  private final CertificateWrapper myCertificateWrapper;

  public CertificateInfoPanel(@NotNull X509Certificate certificate) {
    myCertificateWrapper = new CertificateWrapper(certificate);
    setLayout(new BorderLayout());

    FormBuilder builder = FormBuilder.createFormBuilder();

    // I'm not using separate panels and form builders to preserve alignment of labels
    updateBuilderWithTitle(builder, "Issued To");
    updateBuilderWithPrincipalData(builder, myCertificateWrapper.getSubjectFields());
    updateBuilderWithTitle(builder, "Issued By");
    updateBuilderWithPrincipalData(builder, myCertificateWrapper.getIssuerFields());
    updateBuilderWithTitle(builder, "Validity Period");
    String notBefore = DATE_FORMAT.format(myCertificateWrapper.getNotBefore());
    String notAfter = DATE_FORMAT.format(myCertificateWrapper.getNotAfter());
    builder = builder
      .setIndent(IdeBorderFactory.TITLED_BORDER_INDENT)
      .addLabeledComponent("Valid from:", createColoredComponent(notBefore, "not yet valid", myCertificateWrapper.isNotYetValid()))
      .addLabeledComponent("Valid until:", createColoredComponent(notAfter, "expired", myCertificateWrapper.isExpired()));
    builder.setIndent(0);
    updateBuilderWithTitle(builder, "Fingerprints");
    builder.setIndent(IdeBorderFactory.TITLED_BORDER_INDENT);
    builder.addLabeledComponent("SHA-256:", getTextPane(formatHex(myCertificateWrapper.getSha256Fingerprint())));
    builder.addLabeledComponent("SHA-1:", getTextPane(formatHex(myCertificateWrapper.getSha1Fingerprint())));
    add(builder.getPanel(), BorderLayout.NORTH);
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

  public X509Certificate getCertificate() {
    return myCertificateWrapper.getCertificate();
  }

  private static void updateBuilderWithPrincipalData(FormBuilder builder, Map<String, String> fields) {
    builder = builder.setIndent(IdeBorderFactory.TITLED_BORDER_INDENT);
    for (CommonField field : CommonField.values()) {
      String value = fields.get(field.getShortName());
      if (value == null) {
        continue;
      }
      String label = String.format("<html>%s (<b>%s</b>)</html>", field.getShortName(), field.getLongName());
      builder = builder.addLabeledComponent(label, new JBLabel(value));
    }
    builder.setIndent(0);
  }

  private static void updateBuilderWithTitle(FormBuilder builder, String title) {
    builder.addComponent(new TitledSeparator(title), IdeBorderFactory.TITLED_BORDER_TOP_INSET);
  }

  private static JComponent getTextPane(String text) {
    JTextPane pane = new JTextPane();
    pane.setEditorKit(UIUtil.getHTMLEditorKit());
    pane.setOpaque(false);
    pane.setEditable(false);
    pane.setContentType("text/plain");
    pane.setText(text);
    //Messages.installHyperlinkSupport(pane);
    return pane;
  }

  private static JComponent createColoredComponent(String mainText, String errorText, boolean hasError) {
    SimpleColoredComponent component = new SimpleColoredComponent();
    if (hasError) {
      component.append(mainText + " (" + errorText + ")", new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED));
    } else {
      component.append(mainText);
    }
    return component;
  }
}
