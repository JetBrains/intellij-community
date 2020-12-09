// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.application.subscribe
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkUpdateNotification.InstallUpdateNotification
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkUpdateNotification.RejectUpdateNotification
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ui.UIUtil
import org.junit.Assert
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class JdkUpdateTest : LightPlatformTestCase() {
  private val myNotifications = Collections.synchronizedList(ArrayList<Notification>())

  override fun setUp() {
    super.setUp()
    service<JdkInstallerStore>().loadState(JdkInstallerState())

    val key = "jdk.downloader.home"
    val oldHome = System.getProperty(key)
    System.setProperty(key, createTempDir("jdk-install-home").toString())
    disposeOnTearDown(Disposable {
      if (oldHome != null) {
        System.setProperty(key, oldHome)
      } else {
        System.clearProperty(key)
      }
    })

    Notifications.TOPIC.subscribe(testRootDisposable, object: Notifications {
      override fun notify(notification: Notification) {
        myNotifications += notification
      }
    })

    listOurNotifications().forEach { it.expire() }
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun listOurNotifications() = myNotifications
    .filter { !it.isExpired }
    .filter { it.groupId == "JDK Update" || it.groupId == "JDK Update Error" }

  private fun listOurExpiredNotifications() = myNotifications
    .filter { it.isExpired }
    .filter { it.groupId == "JDK Update" || it.groupId == "JDK Update Error" }

  private fun expectSingleSuggestUpdateNotification() : Notification {
    return listOurNotifications().single { it.groupId == "JDK Update" }
  }

  private fun newNotification(sdkName: String, oldVersion: JdkItem = mockZipOld, newVersion: JdkItem = mockZipNew): JdkUpdateNotification {
    val oldSdk = ProjectJdkTable.getInstance().createSdk(sdkName, JavaSdk.getInstance())
    oldSdk.sdkModificator.apply {
      homePath = createTempDir("mock-old-home").toString()
      versionString = oldVersion.versionString
    }.commitChanges()

    if (oldSdk is Disposable) {
      Disposer.register(testRootDisposable, oldSdk)
    }

    return JdkUpdateNotification(oldSdk, oldVersion, newVersion) {}
  }

  fun `test the same popup is not shown twice`() {
    val notification = newNotification("old-sdk")

    notification.showNotificationIfAbsent()
    notification.showNotificationIfAbsent()
    notification.showNotificationIfAbsent()

    val notifications = listOurNotifications()
    val expired = listOurExpiredNotifications()
    Assert.assertEquals("$notifications", 1, notifications.size)
    Assert.assertEquals("$expired", 0, expired.size)
  }

  fun `test jdk update`() {
    val update = newNotification("old-sdk2")
    update.showNotificationIfAbsent()

    val n = expectSingleSuggestUpdateNotification()
    n.fireAction<InstallUpdateNotification>()
    UIUtil.dispatchAllInvocationEvents()

    val ourNotifications = listOurNotifications()
    Assert.assertTrue("$ourNotifications", ourNotifications.isEmpty())
    Assert.assertEquals(update.newItem.versionString, update.jdk.versionString)
    Assert.assertTrue(update.isTerminated())

    // we do not have marker files in the test (as we use fake JdkItem's here
    //Assert.assertEquals(JdkInstaller.getInstance().findJdkItemForInstalledJdk(update.jdk.homePath), update.newItem)
    Assert.assertEquals(service<JdkInstallerStore>().findInstallations(update.newItem), listOf(Paths.get(update.jdk.homePath!!)))
    Assert.assertEquals(service<JdkInstallerStore>().findInstallations(update.oldItem), listOf<Path>())
  }

  fun `test jdk update failed`() {
    val update = newNotification("old-sdk2", newVersion = mockZipNewBroken)
    update.showNotificationIfAbsent()

    val n = expectSingleSuggestUpdateNotification()
    n.fireAction<InstallUpdateNotification>()
    UIUtil.dispatchAllInvocationEvents()

    val ourNotifications = listOurNotifications()
    Assert.assertEquals("$ourNotifications", 1, ourNotifications.size)
    Assert.assertEquals("$ourNotifications", 1, ourNotifications.filter { it.type == NotificationType.ERROR }.size)
    Assert.assertEquals(update.oldItem.versionString, update.jdk.versionString)
    Assert.assertTrue(!update.isTerminated())

    update.showNotificationIfAbsent()
    Assert.assertEquals("$ourNotifications", ourNotifications, listOurNotifications())
  }

  fun `test reject is not lost`() {
    val update = newNotification("old-sdk2")
    update.showNotificationIfAbsent()

    val n = expectSingleSuggestUpdateNotification()
    n.fireAction<RejectUpdateNotification>()
    UIUtil.dispatchAllInvocationEvents()

    val ourNotifications = listOurNotifications()
    Assert.assertTrue("$ourNotifications", ourNotifications.isEmpty())
    Assert.assertEquals(update.oldItem.versionString, update.jdk.versionString)
    Assert.assertTrue(update.isTerminated())
    Assert.assertFalse(service<JdkUpdaterState>().isAllowed(update.jdk, update.newItem))
  }

  private inline fun <reified T: NotificationAction> Notification.fireAction() {
    val action = actions.filterIsInstance<T>().single()
    Notification.fire(this, action)
  }


  fun `test merge notifications correctly`() {
    val old1 = newNotification("old-1")
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    val notifications = listOurNotifications()
    val expired = listOurExpiredNotifications()
    Assert.assertEquals("$notifications", 1, notifications.size)
    Assert.assertEquals("$expired", 0, expired.size)
  }

  fun `test merge notifications correctly 2`() {
    val old1 = newNotification("old-1")
    val old2 = newNotification("old-2")
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)
    service<JdkUpdaterNotifications>().showNotification(old2.jdk, old2.oldItem, old2.newItem)
    service<JdkUpdaterNotifications>().showNotification(old2.jdk, old2.oldItem, old2.newItem)
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    val notifications = listOurNotifications()
    val expired = listOurExpiredNotifications()
    Assert.assertEquals("$notifications", 2, notifications.size)
    Assert.assertEquals("$expired", 0, expired.size)
  }

  fun `test replace notifications correctly 2`() {
    val old1 = newNotification("old-1")
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem.copy(jdkVersion = "17.0.777"))

    val notifications = listOurNotifications()
    val expired = listOurExpiredNotifications()
    Assert.assertEquals("$notifications", 1, notifications.size)
    Assert.assertTrue("$notifications", notifications.single().content.contains("17.0.777"))
    Assert.assertEquals("$expired", 1, expired.size)
    Assert.assertFalse("$notifications", expired.single().content.contains("17.0.777"))
  }

  fun `test replace notifications correctly 3`() {
    val old1 = newNotification("old-1")
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    Assert.assertEquals(1, listOurNotifications().size)
    Assert.assertEquals(0, listOurExpiredNotifications().size)

    runWriteAction {
      old1.jdk.sdkModificator.also { it.versionString = "new JDK version" }.commitChanges()
    }
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    //notification must expire and update
    Assert.assertEquals(1, listOurNotifications().size)
    Assert.assertEquals(1, listOurExpiredNotifications().size)
  }

  fun `test replace notifications correctly 4`() {
    val old1 = newNotification("old-1")
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    Assert.assertEquals(1, listOurNotifications().size)
    Assert.assertEquals(0, listOurExpiredNotifications().size)

    runWriteAction {
      old1.jdk.sdkModificator.also { it.homePath = it.homePath + "-123" }.commitChanges()
    }
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    //notification must expire and update
    Assert.assertEquals(1, listOurNotifications().size)
    Assert.assertEquals(1, listOurExpiredNotifications().size)
  }
}

val mockZipNew
  get() = jdkItemForTest(javaVersion = "17.0.1",
                         packageType = JdkPackageType.ZIP,
                         url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.zip",
                         size = 604,
                         sha256 = "1cf15536c1525f413190fd53243f343511a17e6ce7439ccee4dc86f0d34f9e81")

private val mockZipNewBroken
  get() = jdkItemForTest(javaVersion = "17.0.99",
                         packageType = JdkPackageType.ZIP,
                         url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data-404.zip",
                         size = 604,
                         sha256 = "1cf15536c1525f413190fd53243f343511a17e6ce7439ccee4dc86f0d34f9e81-404")

private val mockZipOld
  get() = jdkItemForTest(javaVersion = "17",
                         packageType = JdkPackageType.TAR_GZ,
                         url = "https://repo.labs.intellij.net/idea-test-data/jdk-download-test-data.tar.gz",
                         size = 249,
                         sha256 = "ffc8825d96e3f89cb4a8ca64b9684c37f55d6c5bd54628ebf984f8282f8a59ff")

private fun jdkItemForTest(url: String,
                           packageType: JdkPackageType,
                           size: Long,
                           sha256: String,
                           prefix: String = "",
                           packageToHomePrefix: String = "",
                           javaVersion: String
) = JdkItem(
  JdkProduct(vendor = "Vendor", product = "mock", flavour = null),
  isDefaultItem = false,
  isVisibleOnUI = true,
  jdkMajorVersion = javaVersion.split(".").first().toInt(),
  jdkVersion = javaVersion,
  jdkVendorVersion = null,
  suggestedSdkName = "suggested",
  arch = "jetbrains-hardware",
  os = "windows",
  packageType = packageType,
  url = url,
  sha256 = sha256,
  archiveSize = size,
  unpackedSize = 10 * size,
  packageRootPrefix = prefix,
  packageToBinJavaPrefix = packageToHomePrefix,
  archiveFileName = url.split("/").last(),
  installFolderName = url.split("/").last().removeSuffix(".tar.gz").removeSuffix(".zip"),
  sharedIndexAliases = listOf(),
  saveToFile = {}
)
