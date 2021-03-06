// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.certificates

import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileTypeDescriptor
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.*
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.net.ssl.*
import com.intellij.util.net.ssl.ConfirmingTrustManager.MutableTrustManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.CardLayout
import java.security.cert.X509Certificate
import javax.swing.JPanel
import javax.swing.tree.TreeSelectionModel

class PluginCertificateManager :
  BoundConfigurable(
    IdeBundle.message("plugin.manager.custom.certificates"),
    "plugin.certificates"
  ), Configurable.NoScroll, CertificateListener {

  private val myTree: Tree = Tree()

  private val myCertificatesListPanel: JPanel = panel {
    row {
      val decorator = ToolbarDecorator.createDecorator(myTree)
        .disableUpDownActions()
        .setAddAction { chooseFileAndAdd() }
        .setRemoveAction { removeSelectedCertificates() }
        .createPanel()
      decorator(growX)
    }
  }
  private val myDetailsPanel: JPanel = JPanel(CardLayout())

  val myRootPanel = panel {
    row {
      myCertificatesListPanel()
    }
    row {
      myDetailsPanel(growX)
    }
  }

  val myEmptyPanel = panel {
    row {
      label(IdeBundle.message("settings.certificate.no.certificate.selected"))
    }
  }

  private val CERTIFICATE_DESCRIPTOR = FileTypeDescriptor(
    IdeBundle.message("settings.certificate.choose.certificate"),
    ".crt", ".CRT",
    ".cer", ".CER",
    ".pem", ".PEM",
    ".der", ".DER"
  )
  private val EMPTY_PANEL = "empty.panel"
  private val myTrustManager: MutableTrustManager = PluginCertificateStore.getInstance().customTrustManager
  private val myTreeBuilder: CertificateTreeBuilder = CertificateTreeBuilder(myTree)
  private val myCertificates = mutableSetOf<X509Certificate>()


  override fun createPanel(): DialogPanel {
    init()
    return myRootPanel
  }

  override fun certificateAdded(certificate: X509Certificate) {
    UIUtil.invokeLaterIfNeeded {
      if (!myCertificates.contains(certificate)) {
        myCertificates.add(certificate)
        myTreeBuilder.addCertificate(certificate)
        addCertificatePanel(certificate)
      }
    }
  }

  override fun certificateRemoved(certificate: X509Certificate) {
    UIUtil.invokeLaterIfNeeded {
      if (myCertificates.contains(certificate)) {
        myCertificates.remove(certificate)
        myTreeBuilder.removeCertificate(certificate)
      }
    }
  }

  override fun isModified(): Boolean {
    return myCertificates != HashSet(myTrustManager.certificates)
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    val existing = myTrustManager.certificates
    val added = myCertificates - existing
    val removed = existing - myCertificates
    for (certificate in added) {
      if (!myTrustManager.addCertificate(certificate)) {
        throw ConfigurationException(
          IdeBundle.message(
            "settings.certificate.cannot.add.certificate.for", CertificateUtil.getCommonName(certificate)
          ),
          IdeBundle.message("settings.certificate.cannot.add.certificate"))
      }
    }
    for (certificate in removed) {
      if (!myTrustManager.removeCertificate(certificate)) {
        throw ConfigurationException(
          IdeBundle.message(
            "settings.certificate.cannot.remove.certificate.for",
            CertificateUtil.getCommonName(certificate)
          ),
          IdeBundle.message("settings.certificate.cannot.remove.certificate"))
      }
    }
  }

  override fun reset() {
    val original = myTrustManager.certificates
    myTreeBuilder.reset(original)
    myCertificates.clear()
    myCertificates.addAll(original)
    myDetailsPanel.removeAll()
    myDetailsPanel.add(myEmptyPanel, CertificateConfigurable.EMPTY_PANEL)

    // fill lower panel with cards
    for (certificate in original) {
      addCertificatePanel(certificate!!)
    }
    if (myCertificates.isNotEmpty()) {
      myTreeBuilder.selectFirstCertificate()
    }
  }

  override fun disposeUIResources() {
    Disposer.dispose(myTreeBuilder)
    myTrustManager.removeListener(this)
  }

  private fun init() {
    // show newly added certificates
    myTrustManager.addListener(this)
    myTree.emptyText.text = IdeBundle.message("settings.certificate.no.certificates")
    myTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    myTree.isRootVisible = false
    myTree.addTreeSelectionListener {
      val certificate = myTreeBuilder.getFirstSelectedCertificate(true)
      if (certificate != null) {
        showCard(getCardName(certificate))
      }
    }

    myCertificatesListPanel.border = IdeBorderFactory.createTitledBorder(
      IdeBundle.message("settings.trusted.certificates"), false, JBUI.insetsTop(8)
    ).setShowLine(false)
  }

  private fun chooseFileAndAdd() {
    FileChooser.chooseFile(CERTIFICATE_DESCRIPTOR, null, null) { file: VirtualFile ->
      val path = file.path
      val certificate = CertificateUtil.loadX509Certificate(path)
      when {
        certificate == null -> {
          Messages.showErrorDialog(
            myRootPanel,
            IdeBundle.message("settings.certificate.malformed.x509.server.certificate"),
            IdeBundle.message("settings.certificate.not.imported")
          )
        }
        myCertificates.contains(certificate) -> {
          Messages.showWarningDialog(
            myRootPanel,
            IdeBundle.message("settings.certificate.certificate.already.exists"),
            IdeBundle.message("settings.certificate.not.imported")
          )
        }
        else -> addCertificate(certificate)
      }
    }
  }

  private fun addCertificate(certificate: X509Certificate) {
    myCertificates.add(certificate)
    myTreeBuilder.addCertificate(certificate)
    addCertificatePanel(certificate)
    myTreeBuilder.selectCertificate(certificate)
  }

  private fun removeSelectedCertificates() {
    for (certificate in myTreeBuilder.getSelectedCertificates(true)) {
      myCertificates.remove(certificate)
      myTreeBuilder.removeCertificate(certificate)
    }
    if (myCertificates.isEmpty()) {
      showCard(EMPTY_PANEL)
    }
    else {
      myTreeBuilder.selectFirstCertificate()
    }
  }

  private fun showCard(cardName: String) {
    (myDetailsPanel.layout as CardLayout).show(myDetailsPanel, cardName)
  }

  private fun getCardName(certificate: X509Certificate): String = certificate.subjectX500Principal.name

  private fun addCertificatePanel(certificate: X509Certificate) {
    val uniqueName = getCardName(certificate)
    val infoPanel: JPanel = CertificateInfoPanel(certificate)
    UIUtil.addInsets(infoPanel, UIUtil.PANEL_REGULAR_INSETS)
    val scrollPane = JBScrollPane(infoPanel)
    myDetailsPanel.add(scrollPane, uniqueName)
  }

}