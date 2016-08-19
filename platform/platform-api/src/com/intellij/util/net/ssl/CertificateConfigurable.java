/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.net.ssl;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import static com.intellij.util.net.ssl.CertificateUtil.getCommonName;
import static com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager;

/**
 * @author Mikhail Golubev
 */
public class CertificateConfigurable implements SearchableConfigurable, Configurable.NoScroll, CertificateListener {
  private static final FileTypeDescriptor CERTIFICATE_DESCRIPTOR = new FileTypeDescriptor("Choose Certificate", ".crt", ".cer", ".pem");
  @NonNls public static final String EMPTY_PANEL = "empty.panel";

  private JPanel myRootPanel;

  private JBCheckBox myAcceptAutomatically;
  private JBCheckBox myCheckHostname;
  private JBCheckBox myCheckValidityPeriod;

  private JPanel myCertificatesListPanel;
  private JPanel myDetailsPanel;
  private JPanel myEmptyPanel;
  private MutableTrustManager myTrustManager;

  private Tree myTree;
  private CertificateTreeBuilder myTreeBuilder;
  private Set<X509Certificate> myCertificates = new HashSet<>();

  private void initializeUI() {
    myTree = new Tree();
    myTreeBuilder = new CertificateTreeBuilder(myTree);

    // are not fully functional by now
    myCheckHostname.setVisible(false);
    myCheckValidityPeriod.setVisible(false);

    myTrustManager = CertificateManager.getInstance().getCustomTrustManager();
    // show newly added certificates
    myTrustManager.addListener(this);

    myTree.getEmptyText().setText("No certificates");
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setRootVisible(false);
    //myTree.setShowsRootHandles(false);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree).disableUpDownActions();
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // show choose file dialog, add certificate
        FileChooser.chooseFile(CERTIFICATE_DESCRIPTOR, null, null, file -> {
          String path = file.getPath();
          X509Certificate certificate = CertificateUtil.loadX509Certificate(path);
          if (certificate == null) {
            Messages.showErrorDialog(myRootPanel, "Malformed X509 server certificate", "Not Imported");
          }
          else if (myCertificates.contains(certificate)) {
            Messages.showWarningDialog(myRootPanel, "Certificate already exists", "Not Imported");
          }
          else {
            myCertificates.add(certificate);
            myTreeBuilder.addCertificate(certificate);
            addCertificatePanel(certificate);
            myTreeBuilder.selectCertificate(certificate);
          }
        });
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        // allow to delete several certificates at once
        for (X509Certificate certificate : myTreeBuilder.getSelectedCertificates(true)) {
          myCertificates.remove(certificate);
          myTreeBuilder.removeCertificate(certificate);
        }
        if (myCertificates.isEmpty()) {
          showCard(EMPTY_PANEL);
        }
        else {
          myTreeBuilder.selectFirstCertificate();
        }
      }
    });

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        X509Certificate certificate = myTreeBuilder.getFirstSelectedCertificate(true);
        if (certificate != null) {
          showCard(getCardName(certificate));
        }
      }
    });
    myCertificatesListPanel.add(decorator.createPanel(), BorderLayout.CENTER);
  }

  private void showCard(@NotNull String cardName) {
    ((CardLayout)myDetailsPanel.getLayout()).show(myDetailsPanel, cardName);
  }

  private void addCertificatePanel(@NotNull X509Certificate certificate) {
    String uniqueName = getCardName(certificate);
    JPanel infoPanel = new CertificateInfoPanel(certificate);
    UIUtil.addInsets(infoPanel, UIUtil.PANEL_REGULAR_INSETS);
    JBScrollPane scrollPane = new JBScrollPane(infoPanel);
    //scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myDetailsPanel.add(scrollPane, uniqueName);
  }

  private static String getCardName(@NotNull X509Certificate certificate) {
    return certificate.getSubjectX500Principal().getName();
  }

  @NotNull
  @Override
  public String getId() {
    return "http.certificates";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Server Certificates";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.idesettings.server.certificates";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    // lazily initialized to ensure that disposeUIResources() will be called, if
    // tree builder was created
    initializeUI();
    return myRootPanel;
  }

  @Override
  public boolean isModified() {
    CertificateManager.Config state = CertificateManager.getInstance().getState();
    return myAcceptAutomatically.isSelected() != state.ACCEPT_AUTOMATICALLY ||
           myCheckHostname.isSelected() != state.CHECK_HOSTNAME ||
           myCheckValidityPeriod.isSelected() != state.CHECK_VALIDITY ||
           !myCertificates.equals(new HashSet<>(myTrustManager.getCertificates()));
  }

  @Override
  public void apply() throws ConfigurationException {
    List<X509Certificate> existing = myTrustManager.getCertificates();

    Set<X509Certificate> added = new HashSet<>(myCertificates);
    added.removeAll(existing);

    Set<X509Certificate> removed = new HashSet<>(existing);
    removed.removeAll(myCertificates);

    for (X509Certificate certificate : added) {
      if (!myTrustManager.addCertificate(certificate)) {
        throw new ConfigurationException("Cannot add certificate for " + getCommonName(certificate), "Cannot Add Certificate");
      }
    }

    for (X509Certificate certificate : removed) {
      if (!myTrustManager.removeCertificate(certificate)) {
        throw new ConfigurationException("Cannot remove certificate for " + getCommonName(certificate), "Cannot Remove Certificate");
      }
    }
    CertificateManager.Config state = CertificateManager.getInstance().getState();

    state.ACCEPT_AUTOMATICALLY = myAcceptAutomatically.isSelected();
    state.CHECK_HOSTNAME = myCheckHostname.isSelected();
    state.CHECK_VALIDITY = myCheckValidityPeriod.isSelected();
  }

  @Override
  public void reset() {
    List<X509Certificate> original = myTrustManager.getCertificates();
    myTreeBuilder.reset(original);

    myCertificates.clear();
    myCertificates.addAll(original);

    myDetailsPanel.removeAll();
    myDetailsPanel.add(myEmptyPanel, EMPTY_PANEL);

    // fill lower panel with cards
    for (X509Certificate certificate : original) {
      addCertificatePanel(certificate);
    }

    if (!myCertificates.isEmpty()) {
      myTreeBuilder.selectFirstCertificate();
    }

    CertificateManager.Config state = CertificateManager.getInstance().getState();
    myAcceptAutomatically.setSelected(state.ACCEPT_AUTOMATICALLY);
    myCheckHostname.setSelected(state.CHECK_HOSTNAME);
    myCheckValidityPeriod.setSelected(state.CHECK_VALIDITY);
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myTreeBuilder);
    myTrustManager.removeListener(this);
  }

  @Override
  public void certificateAdded(final X509Certificate certificate) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myTreeBuilder != null && !myCertificates.contains(certificate)) {
        myCertificates.add(certificate);
        myTreeBuilder.addCertificate(certificate);
        addCertificatePanel(certificate);
      }
    });
  }

  @Override
  public void certificateRemoved(final X509Certificate certificate) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myTreeBuilder != null && myCertificates.contains(certificate)) {
        myCertificates.remove(certificate);
        myTreeBuilder.removeCertificate(certificate);
      }
    });
  }
}
