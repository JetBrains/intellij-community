// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net.ssl;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.ui.HTMLEditorKitBuilder;
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
                                        IdeBundle.message("dialog.title.untrusted.server.s.certificate"),
                                        IdeBundle.message("text.server.s.certificate.trusted"));
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

  public CertificateWarningDialog(@NotNull X509Certificate certificate,
                                  @NotNull @NlsContexts.DialogTitle String title,
                                  @NotNull @NlsContexts.DetailedDescription String message) {
    super((Project)null, false);

    myRootPanel.setPreferredSize(new JBDimension(550, 650));
    myNoticePane.setEditorKit(HTMLEditorKitBuilder.simple());
    myMessagePane.setEditorKit(HTMLEditorKitBuilder.simple());

    myCertificate = certificate;

    CertificateManager manager = CertificateManager.getInstance();
    setTitle(title);
    myMessagePane.setText(new HtmlBuilder()
                            .append(HtmlChunk.raw(message).wrapWith("p"))
                            .wrapWithHtmlBody()
                            .toString());
    myMessagePane.setBackground(UIUtil.getPanelBackground());
    setOKButtonText(CommonBundle.message("button.accept"));
    myOKAction.putValue(DEFAULT_ACTION, null);
    setCancelButtonText(IdeBundle.message("button.reject"));
    myCancelAction.putValue(DEFAULT_ACTION, true);
    myWarningSign.setIcon(AllIcons.General.WarningDialog);

    Messages.installHyperlinkSupport(myNoticePane);
    //    myNoticePane.setFont(myNoticePane.getFont().deriveFont((float)FontSize.SMALL.getSize()));

    String path = FileUtil.toSystemDependentName(FileUtil.toCanonicalPath(manager.getCacertsPath()));
    @NlsSafe String password = manager.getPassword();

    myNoticePane.setText(new HtmlBuilder()
                           .appendRaw(IdeBundle.message("label.certificate.will.be.saved",
                                                        HtmlChunk.text(path).wrapWith("code"),
                                                        HtmlChunk.text(password).wrapWith("code")))
                           .wrapWithHtmlBody()
                           .toString());
    myCertificateInfoPanel.add(new CertificateInfoPanel(certificate), BorderLayout.CENTER);
    setResizable(true);
    init();
    LOG.debug("Preferred size: " + getPreferredSize());
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }
}
