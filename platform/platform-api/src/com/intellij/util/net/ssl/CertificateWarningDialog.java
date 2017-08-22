package com.intellij.util.net.ssl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.security.cert.X509Certificate;

/**
 * @author Mikhail Golubev
 */
public class CertificateWarningDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(CertificateWarningDialog.class);

  public static CertificateWarningDialog createUntrustedCertificateWarning(@NotNull X509Certificate certificate) {
    return new CertificateWarningDialog(certificate,
                                        "Untrusted Server's Certificate",
                                        "Server's certificate is not trusted");
  }

  public static CertificateWarningDialog createExpiredCertificateWarning(@NotNull X509Certificate certificate) {
    throw new UnsupportedOperationException("Not supported");
  }

  private JPanel myRootPanel;
  private JLabel myWarningSign;
  private JPanel myCertificateInfoPanel;
  private JTextPane myNoticePane;
  private JTextPane myMessagePane;
  private final X509Certificate myCertificate;

  public CertificateWarningDialog(@NotNull X509Certificate certificate, @NotNull String title, @NotNull String message) {
    super((Project)null, false);

    myRootPanel.setPreferredSize(new JBDimension(550, 650));
    myNoticePane.setEditorKit(UIUtil.getHTMLEditorKit());
    myMessagePane.setEditorKit(UIUtil.getHTMLEditorKit());

    myCertificate = certificate;

    CertificateManager manager = CertificateManager.getInstance();
    setTitle(title);
    myMessagePane.setText(String.format("<html><body><p>%s</p></body></html>", message));
    myMessagePane.setBackground(UIUtil.getPanelBackground());
    setOKButtonText("Accept");
    setCancelButtonText("Reject");
    myWarningSign.setIcon(AllIcons.General.WarningDialog);

    Messages.installHyperlinkSupport(myNoticePane);
    //    myNoticePane.setFont(myNoticePane.getFont().deriveFont((float)FontSize.SMALL.getSize()));

    String path = FileUtil.toCanonicalPath(manager.getCacertsPath());
    String password = manager.getPassword();

    myNoticePane.setText(
      String.format("<html><p>" +
                    "Accepted certificate will be saved in truststore <code>%s</code> with default password <code>%s</code>" +
                    "</p><html>",
                    path, password
      )
    );
    myCertificateInfoPanel.add(new CertificateInfoPanel(certificate), BorderLayout.CENTER);
    setResizable(false);
    init();
    LOG.debug("Preferred size: " + getPreferredSize());
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }
}
