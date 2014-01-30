package com.intellij.util.net.ssl;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import static com.intellij.util.net.ssl.CertificateWrapper.CommonField.COMMON_NAME;
import static com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager;

/**
 * @author Mikhail Golubev
 */
public class CertificateConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private static final FileTypeDescriptor CERTIFICATE_DESCRIPTOR = new FileTypeDescriptor("Choose Certificate", ".crt", ".cer", ".pem");

  private JPanel myRootPanel;
  private JBCheckBox myCheckHostname;
  private JBCheckBox myCheckValidityPeriod;

  private JPanel myCertificatesListPanel;
  private JPanel myDetailsPanel;
  private JBList myCertificatesList;
  private MutableTrustManager myTrustManager;

  public CertificateConfigurable() {
    myTrustManager = CertificatesManager.getInstance().getCustomTrustManager();

    myCertificatesList = new JBList();
    myCertificatesList.getEmptyText().setText("No certificates");
    myCertificatesList.setCellRenderer(new ListCellRendererWrapper<X509Certificate>() {
      @Override
      public void customize(JList list, X509Certificate certificate, int index, boolean selected, boolean hasFocus) {
        if (!new CertificateWrapper(certificate).isValid()) {
          setForeground(UIUtil.getLabelDisabledForeground());
        }
        setText(new CertificateWrapper(certificate).getSubjectField(COMMON_NAME));
      }
    });
    // fill lower panel with cards
    for (X509Certificate certificate : myTrustManager.getCertificates()) {
      addCertificatePanel(certificate);
    }

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myCertificatesList).disableUpDownActions();
    decorator.setVisibleRowCount(5);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // show choose file dialog, add certificate
        FileChooser.chooseFile(CERTIFICATE_DESCRIPTOR, null, null, new Consumer<VirtualFile>() {
          @Override
          public void consume(VirtualFile file) {
            String path = file.getPath();
            X509Certificate certificate = CertificateUtil.loadX509Certificate(path);
            if (certificate != null) {
              getListModel().add(certificate);
              addCertificatePanel(certificate);
              myCertificatesList.setSelectedValue(certificate, true);
            }
            else {
              Messages.showErrorDialog(myRootPanel, "Cannot Load Certificate", "Possibly malformed X509 server certificate " + path);
            }
          }
        });
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // allow to delete several certificates at once
        CollectionListModel<X509Certificate> model = getListModel();
        for (int i : myCertificatesList.getSelectedIndices()) {
          X509Certificate certificate = (X509Certificate)myCertificatesList.getModel().getElementAt(i);
          model.remove(certificate);
        }
        if (getListModel().getSize() > 0) {
          myCertificatesList.setSelectedIndex(0);
        } else {
          myDetailsPanel.removeAll();
          myDetailsPanel.repaint();
        }
      }
    });

    /*
    decorator.addExtraAction(new AnActionButton("View details", AllIcons.General.Information) {
      @Override
      public boolean isEnabled() {
        return getSelectedCertificate() != null;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        DialogBuilder dialog = new DialogBuilder(myRootPanel);
        dialog.setTitle("Certificate Details");
        dialog.setCenterPanel(new CertificateInfoPanel(getSelectedCertificate()).getPanel());
        // Only OK action is available
        dialog.setActionDescriptors(new DialogBuilder.ActionDescriptor[]{new DialogBuilder.OkActionDescriptor()});
        dialog.show();
      }
    });
    */
    myCertificatesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        X509Certificate certificate = getSelectedCertificate();
        if (certificate != null) {
          String uniqueName = certificate.getSubjectX500Principal().getName();
          ((CardLayout)myDetailsPanel.getLayout()).show(myDetailsPanel, uniqueName);
          //mySplitter.doLayout();
          //mySplitter.repaint();
        }
      }
    });
    myCertificatesListPanel.add(decorator.createPanel(), BorderLayout.CENTER);
  }

  private void addCertificatePanel(X509Certificate certificate) {
    String uniqueName = certificate.getSubjectX500Principal().getName();
    JPanel infoPanel = new CertificateInfoPanel(certificate);
    UIUtil.addInsets(infoPanel, UIUtil.PANEL_REGULAR_INSETS);
    JBScrollPane scrollPane = new JBScrollPane(infoPanel);
    //scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myDetailsPanel.add(scrollPane, uniqueName);
  }

  private CollectionListModel<X509Certificate> getListModel() {
    //noinspection unchecked
    return (CollectionListModel<X509Certificate>)myCertificatesList.getModel();
  }

  private List<X509Certificate> getCertificates() {
    return getListModel().getItems();
  }

  private X509Certificate getSelectedCertificate() {
    return (X509Certificate)myCertificatesList.getSelectedValue();
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
    CertificatesManager.Config state = CertificatesManager.getInstance().getState();
    return myCheckHostname.isSelected() != state.checkHostname ||
           myCheckValidityPeriod.isSelected() != state.checkValidity ||
           !getCertificates().equals(myTrustManager.getCertificates());
  }

  @Override
  public void apply() throws ConfigurationException {
    List<X509Certificate> existing = myTrustManager.getCertificates();

    Set<X509Certificate> added = new HashSet<X509Certificate>(getCertificates());
    added.removeAll(existing);

    Set<X509Certificate> removed = new HashSet<X509Certificate>(existing);
    removed.removeAll(getCertificates());

    for (X509Certificate certificate : added) {
      if (!myTrustManager.addCertificate(certificate)) {
        throw new ConfigurationException("Cannot add certificate", "Cannot Add Certificate");
      }
    }

    for (X509Certificate certificate : removed) {
      if (!myTrustManager.removeCertificate(certificate)) {
        throw new ConfigurationException("Cannot remove certificate", "Cannot Remove Certificate");
      }
    }

    //Set<X509Certificate> old = new HashSet<X509Certificate>(existing);
    //for (X509Certificate certificate : getListModel().getItems()) {
    //  if (old.contains(certificate)) {
    //    old.remove(certificate);
    //  }
    //  else if (!myTrustManager.addCertificate(certificate)) {
    //    throw new ConfigurationException("Cannot add certificate", "Cannot Add Certificate");
    //  }
    //}
    //// all that remains were removed
    //for (X509Certificate certificate : old) {
    //  if (!myTrustManager.removeCertificate(certificate)) {
    //    throw new ConfigurationException("Cannot remove certificate", "Cannot Remove Certificate");
    //  }
    //}
    CertificatesManager.Config state = CertificatesManager.getInstance().getState();
    state.checkHostname = myCheckHostname.isSelected();
    state.checkValidity = myCheckValidityPeriod.isSelected();
  }

  @Override
  public void reset() {
    //noinspection unchecked
    myCertificatesList.setModel(new CollectionListModel<X509Certificate>(myTrustManager.getCertificates()));
    CertificatesManager.Config state = CertificatesManager.getInstance().getState();
    myCheckHostname.setSelected(state.checkHostname);
    myCheckValidityPeriod.setSelected(state.checkValidity);
  }

  @Override
  public void disposeUIResources() {

  }
}
