package com.intellij.util.net.ssl;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.security.cert.X509Certificate;

import static com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager;

/**
 * @author Mikhail Golubev
 */
public class CertificateConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myRootPanel;
  private JCheckBox myCheckHostnameCheckBox;
  private JPanel myCertificatesPanel;
  private JBLabel myCertificatesLabel;
  private JBList myCertificatesList;

  public CertificateConfigurable() {
    myCertificatesList = new JBList();
    myCertificatesList.getEmptyText().setText("No certificates");
    myCertificatesLabel.setLabelFor(myCertificatesList);
    MutableTrustManager manager = CertificatesManager.getInstance().getCustomTrustManager();
    CollectionListModel<X509Certificate> model = new CollectionListModel<X509Certificate>(manager.getCertificates());
    myCertificatesList.setModel(model);
    myCertificatesPanel.add(myCertificatesList, BorderLayout.CENTER);

    // TODO: add/remove actions, custom renderer
  }

  @NotNull
  @Override
  public String getId() {
    return "http.certificates";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Server Certificates";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myRootPanel;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {

  }

  @Override
  public void reset() {

  }

  @Override
  public void disposeUIResources() {

  }
}
