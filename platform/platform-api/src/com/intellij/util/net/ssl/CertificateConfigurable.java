package com.intellij.util.net.ssl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.util.net.ssl.CertificateWrapper.CommonField.COMMON_NAME;
import static com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager;

/**
 * @author Mikhail Golubev
 */
public class CertificateConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private static final FileTypeDescriptor CERTIFICATE_DESCRIPTOR = new FileTypeDescriptor("Choose Certificate", ".crt", ".cer");

  private JPanel myRootPanel;
  private JBCheckBox myCheckHostname;
  private JPanel myAccptedCertificatesPanel;
  private JBCheckBox myCheckValidityPeriod;
  private JPanel myBrokenCertificatesPanel;
  private JBList mySelfSignedList;
  private JBList myBrokenList;

  public CertificateConfigurable() {
    mySelfSignedList = new JBList();
    mySelfSignedList.getEmptyText().setText("No certificates");
    mySelfSignedList.setCellRenderer(new ListCellRendererWrapper<X509Certificate>() {
      @Override
      public void customize(JList list, X509Certificate value, int index, boolean selected, boolean hasFocus) {
        setText(new CertificateWrapper(value).getSubjectField(COMMON_NAME));
      }
    });
    final MutableTrustManager manager = CertificatesManager.getInstance().getCustomTrustManager();

    installModel(manager);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(mySelfSignedList).disableUpDownActions();
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // show choose file dialog, add certificate
        FileChooser.chooseFile(CERTIFICATE_DESCRIPTOR, null, null, new Consumer<VirtualFile>() {
          @Override
          public void consume(VirtualFile file) {
            String path = file.getPath();
            if (manager.addCertificate(path)) {
              installModel(manager);
            }
            else {
              Messages.showErrorDialog(myRootPanel, "Cannot Add Certificate", "Cannot add X509 certificate " + path);
            }
          }
        });
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // allow to delete several certificates at once
        for (int i : mySelfSignedList.getSelectedIndices()) {
          X509Certificate certificate = (X509Certificate)mySelfSignedList.getModel().getElementAt(i);
          manager.removeCertificate(certificate);
        }
        installModel(manager);
      }
    }).addExtraAction(new AnActionButton("View details", AllIcons.General.Information) {
      @Override
      public boolean isEnabled() {
        return getSelectedCertificate() != null;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        DialogBuilder dialog = new DialogBuilder(myRootPanel);
        dialog.setTitle("Certificate Details");
        dialog.setCenterPanel(new CertificateInfo(getSelectedCertificate()).getPanel());
        // Only OK action is available
        dialog.setActionDescriptors(new DialogBuilder.ActionDescriptor[]{new DialogBuilder.OkActionDescriptor()});
        dialog.show();
      }
    });
    myAccptedCertificatesPanel.add(decorator.createPanel(), BorderLayout.CENTER);

    final Set<String> brokenCertificates = CertificatesManager.getInstance().getState().brokenCertificates;
    myBrokenList = new JBList(new CollectionListModel<String>(brokenCertificates));
    decorator = ToolbarDecorator.createDecorator(myBrokenList).disableUpDownActions().disableAddAction();
    decorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        myBrokenList.remove(myBrokenList.getSelectedIndex());
      }
    });
  }

  private void installModel(MutableTrustManager manager) {
    CollectionListModel<X509Certificate> model = new CollectionListModel<X509Certificate>(manager.getCertificates());
    //noinspection unchecked
    mySelfSignedList.setModel(model);
  }

  private X509Certificate getSelectedCertificate() {
    return (X509Certificate)mySelfSignedList.getSelectedValue();
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
    CertificatesManager.Config state = CertificatesManager.getInstance().getState();
    state.checkHostname = myCheckHostname.isSelected();
    state.checkValidity = myCheckValidityPeriod.isSelected();
    //noinspection unchecked
    CollectionListModel<String> model = (CollectionListModel<String>)(myBrokenList.getModel());
    state.brokenCertificates = new LinkedHashSet<String>(model.getItems());
  }

  @Override
  public void reset() {

  }

  @Override
  public void disposeUIResources() {

  }

  private void createUIComponents() {
    // TODO: place custom component creation code here
  }
}
