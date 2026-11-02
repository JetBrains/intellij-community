// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.application.subscribe
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import com.intellij.util.SystemProperties
import com.intellij.util.system.OS
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.Collections
import javax.swing.JPanel

@RunsInEdt
class JdkUpdateTest : BareTestFixtureTestCase() {
  private val myNotifications = Collections.synchronizedList(ArrayList<Notification>())

  @Rule @JvmField val tempDir = TempDirectory()
  @Rule @JvmField val runInEdt = EdtRule()

  @Before fun setUp() {
    service<JdkInstallerStore>().loadState(JdkInstallerState())
    JdkUpdaterSettings.getInstance().loadState(JdkUpdaterState())

    val key = "jdk.downloader.home"
    val oldHome = System.setProperty(key, tempDir.newDirectory("jdk-install-home").toString())
    Disposer.register(testRootDisposable, Disposable { SystemProperties.setProperty(key, oldHome) })

    Notifications.TOPIC.subscribe(testRootDisposable, object: Notifications {
      override fun notify(notification: Notification) {
        myNotifications += notification
      }
    })

    doEventsWhile(1)
    listOurNotifications().forEach { it.expire() }
    listCleanupNotifications().forEach { it.expire() }
    listOurActions().forEach { it.reachTerminalState() }
  }

  private fun listOurActions(): List<JdkUpdateNotification> {
    doEventsWhile(1)
    return service<JdkUpdaterNotifications>().getActions().map { it.jdkUpdateNotification }.filter { it.isUpdateActionVisible }
  }

  private fun listOurNotifications() = myNotifications
    .filter { !it.isExpired }
    .filter { it.groupId == "JDK Update" || it.groupId == "JDK Update Error" }

  private fun listCleanupNotifications() = myNotifications
    .filter { !it.isExpired }
    .filter { it.groupId == "JDK Update Cleanup" }

  private fun cleanupAction(notification: Notification, messageKey: String): NotificationAction =
    notification.actions.single { it.templatePresentation.text == ProjectBundle.message(messageKey) } as NotificationAction

  private fun newNotification(sdkName: String,
                              oldVersion: JdkItem = mockZipOld,
                              newVersion: JdkItem = mockZipNew,
                              oldHome: Path = tempDir.newDirectory().toPath()): JdkUpdateNotification? {
    val oldSdk = ProjectJdkTable.getInstance().findJdk(sdkName) ?: ProjectJdkTable.getInstance().createSdk(sdkName, JavaSdk.getInstance())
    val sdkModificator = oldSdk.sdkModificator
    sdkModificator.homePath = oldHome.toString()
    sdkModificator.versionString = oldVersion.versionString
    ApplicationManager.getApplication().runWriteAction { sdkModificator.commitChanges() }

    if (oldSdk is Disposable) {
      Disposer.register(testRootDisposable, oldSdk)
    }

    val notification = service<JdkUpdaterNotifications>().showNotification(oldSdk, oldVersion, newVersion)
    doEventsWhile(5)
    return notification
  }

  @Test fun `test the same popup is not shown twice`() {
    newNotification("old-sdk")

    val actions = listOurActions()
    val notifications = listOurNotifications()
    Assert.assertEquals("$notifications", 0, notifications.size)
    Assert.assertEquals("$actions", 1, actions.size)
  }

  @Test fun `test jdk update`() {
    val update = newNotification("old-sdk2")!!
    Assert.assertEquals(setOf(update), listOurActions().toSet())

    update.fireAction()
    doEventsWhile(5)

    val ourActions = listOurActions()
    val ourNotifications = listOurNotifications()
    Assert.assertTrue("$ourActions", ourActions.isEmpty())
    Assert.assertTrue("$ourNotifications", ourNotifications.isEmpty())
    Assert.assertEquals(update.newItem.versionString, update.jdk.versionString)
    Assert.assertTrue(update.isTerminated())

    // we do not have marker files in the test (as we use fake JdkItem's here
    //Assert.assertEquals(JdkInstaller.getInstance().findJdkItemForInstalledJdk(update.jdk.homePath), update.newItem)
    Assert.assertEquals(service<JdkInstallerStore>().findInstallations(update.newItem), listOf(Paths.get(update.jdk.homePath!!)))
    Assert.assertEquals(service<JdkInstallerStore>().findInstallations(update.oldItem), listOf<Path>())
  }

  @Test fun `test jdk update keeps custom install location`() {
    val customJdks = tempDir.newDirectory("custom-jdks").toPath()
    val oldJdkHome = customJdks.resolve("old-jdk")
    Files.createDirectories(oldJdkHome)
    Files.createFile(customJdks.resolve(".old-jdk.intellij"))
    val update = newNotification("old-sdk-custom", newVersion = mockZipNew.copy(jdkVersion = "17.0.777"), oldHome = oldJdkHome)!!

    update.fireAction()
    doEventsWhile(5)

    Assert.assertEquals(customJdks, Paths.get(update.jdk.homePath!!).parent)
    Assert.assertTrue(Files.exists(oldJdkHome))
    Assert.assertTrue(Files.exists(customJdks.resolve(".old-jdk.intellij")))
    val cleanupNotification = listCleanupNotifications().single()
    runNotificationAction(cleanupNotification, cleanupAction(cleanupNotification, "notification.action.jdk.update.cleanup.remove"))
    doEventsWhile(5)

    Assert.assertFalse(Files.exists(oldJdkHome))
    Assert.assertFalse(Files.exists(customJdks.resolve(".old-jdk.intellij")))
  }

  @Test fun `test Always action enables automatic cleanup`() {
    val customJdks = tempDir.newDirectory("custom-jdks-always-remove").toPath()
    val oldJdkHome = customJdks.resolve("old-jdk")
    Files.createDirectories(oldJdkHome)
    val markerFile = Files.createFile(customJdks.resolve(".old-jdk.intellij"))
    val update = newNotification("old-sdk-always-remove", oldHome = oldJdkHome)!!

    update.fireAction()
    doEventsWhile(5)

    val cleanupNotification = listCleanupNotifications().single()
    runNotificationAction(cleanupNotification, cleanupAction(cleanupNotification, "notification.action.jdk.update.cleanup.always"))
    doEventsWhile(5)

    Assert.assertTrue(JdkUpdaterSettings.getInstance().isRemoveOutdatedJdksAutomatically)
    Assert.assertFalse(Files.exists(oldJdkHome))
    Assert.assertFalse(Files.exists(markerFile))
  }

  @Test fun `test automatic cleanup removes outdated JDK without a notification`() {
    val customJdks = tempDir.newDirectory("custom-jdks-automatic-remove").toPath()
    val oldJdkHome = customJdks.resolve("old-jdk")
    Files.createDirectories(oldJdkHome)
    val markerFile = Files.createFile(customJdks.resolve(".old-jdk.intellij"))
    JdkUpdaterSettings.getInstance().isRemoveOutdatedJdksAutomatically = true
    val update = newNotification("old-sdk-automatic-remove", oldHome = oldJdkHome)!!

    update.fireAction()
    doEventsWhile(5)

    Assert.assertFalse(Files.exists(oldJdkHome))
    Assert.assertFalse(Files.exists(markerFile))
    Assert.assertTrue(listCleanupNotifications().isEmpty())
  }

  @Test fun `test jdk update keeps old install used by another sdk`() {
    val customJdks = tempDir.newDirectory("custom-jdks-in-use").toPath()
    val oldJdkHome = customJdks.resolve("old-jdk")
    Files.createDirectories(oldJdkHome)
    Files.createFile(customJdks.resolve(".old-jdk.intellij"))
    val update = newNotification("old-sdk-in-use", oldHome = oldJdkHome)!!

    val anotherSdk = ProjectJdkTable.getInstance().createSdk("another-old-sdk", JavaSdk.getInstance())
    val sdkModificator = anotherSdk.sdkModificator.apply {
      homePath = oldJdkHome.toString()
      versionString = mockZipOld.versionString
    }
    ApplicationManager.getApplication().runWriteAction {
      sdkModificator.commitChanges()
      ProjectJdkTable.getInstance().addJdk(anotherSdk, testRootDisposable)
    }

    update.fireAction()
    doEventsWhile(5)

    Assert.assertTrue(Files.exists(oldJdkHome))
    Assert.assertTrue(Files.exists(customJdks.resolve(".old-jdk.intellij")))
    Assert.assertTrue(listCleanupNotifications().isEmpty())
  }

  @Test fun `test cleanup keeps JDK that becomes used before removal`() {
    val customJdks = tempDir.newDirectory("custom-jdks-in-use-after-notification").toPath()
    val oldJdkHome = customJdks.resolve("old-jdk")
    Files.createDirectories(oldJdkHome)
    Files.createFile(customJdks.resolve(".old-jdk.intellij"))
    val update = newNotification("old-sdk-in-use-after-notification", oldHome = oldJdkHome)!!

    update.fireAction()
    doEventsWhile(5)

    val cleanupNotification = listCleanupNotifications().single()
    val anotherSdk = ProjectJdkTable.getInstance().createSdk("another-old-sdk-after-notification", JavaSdk.getInstance())
    val sdkModificator = anotherSdk.sdkModificator.apply {
      homePath = oldJdkHome.toString()
      versionString = mockZipOld.versionString
    }
    ApplicationManager.getApplication().runWriteAction {
      sdkModificator.commitChanges()
      ProjectJdkTable.getInstance().addJdk(anotherSdk, testRootDisposable)
    }

    runNotificationAction(cleanupNotification, cleanupAction(cleanupNotification, "notification.action.jdk.update.cleanup.remove"))
    doEventsWhile(5)

    Assert.assertTrue(Files.exists(oldJdkHome))
    Assert.assertTrue(Files.exists(customJdks.resolve(".old-jdk.intellij")))
  }

  @Test fun `test removal deletes marked outdated JDK`() {
    val customJdks = tempDir.newDirectory("custom-jdks-remove").toPath()
    val oldJdkHome = customJdks.resolve("old-jdk")
    Files.createDirectories(oldJdkHome)
    val markerFile = Files.createFile(customJdks.resolve(".old-jdk.intellij"))

    val installer = JdkInstaller.getInstance()
    val installDir = installer.findRemovableOutdatedJdk(oldJdkHome.toString())!!
    Assert.assertEquals(oldJdkHome, installDir)

    installer.removeOutdatedJdk(installDir)

    Assert.assertFalse(Files.exists(oldJdkHome))
    Assert.assertFalse(Files.exists(markerFile))
  }

  @Test fun `test removal skips JDK that becomes used after availability check`() {
    val customJdks = tempDir.newDirectory("custom-jdks-used-after-check").toPath()
    val oldJdkHome = customJdks.resolve("old-jdk")
    Files.createDirectories(oldJdkHome)
    Files.createFile(customJdks.resolve(".old-jdk.intellij"))

    val installer = JdkInstaller.getInstance()
    val installDir = installer.findRemovableOutdatedJdk(oldJdkHome.toString())!!
    Assert.assertEquals(oldJdkHome, installDir)

    val anotherSdk = ProjectJdkTable.getInstance().createSdk("another-old-sdk-after-check", JavaSdk.getInstance())
    val sdkModificator = anotherSdk.sdkModificator.apply {
      homePath = oldJdkHome.toString()
      versionString = mockZipOld.versionString
    }
    ApplicationManager.getApplication().runWriteAction {
      sdkModificator.commitChanges()
      ProjectJdkTable.getInstance().addJdk(anotherSdk, testRootDisposable)
    }

    installer.removeOutdatedJdk(installDir)

    Assert.assertTrue(Files.exists(oldJdkHome))
    Assert.assertTrue(Files.exists(customJdks.resolve(".old-jdk.intellij")))
  }

  @Test fun `test failed removal is retried silently`() {
    Assume.assumeTrue(OS.CURRENT != OS.Windows)
    val customJdks = tempDir.newDirectory("custom-jdks-failed-removal").toPath()
    val oldJdkHome = customJdks.resolve("old-jdk")
    val lockedDir = Files.createDirectories(oldJdkHome.resolve("locked"))
    Files.createFile(lockedDir.resolve("file"))
    val markerFile = Files.createFile(customJdks.resolve(".old-jdk.intellij"))
    val store = service<JdkInstallerStore>()
    val installer = JdkInstaller.getInstance()
    val installDir = installer.findRemovableOutdatedJdk(oldJdkHome.toString())!!

    Files.setPosixFilePermissions(lockedDir, PosixFilePermissions.fromString("r-xr-xr-x"))
    try {
      installer.removeOutdatedJdk(installDir)
    }
    finally {
      Files.setPosixFilePermissions(lockedDir, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    Assert.assertTrue(Files.exists(oldJdkHome))
    Assert.assertTrue(Files.exists(markerFile))
    Assert.assertEquals(listOf(oldJdkHome.toAbsolutePath().toString()), store.listOutdated())
    Assert.assertTrue(listCleanupNotifications().isEmpty())

    installer.retryOutdatedJdkRemoval()

    Assert.assertFalse(Files.exists(oldJdkHome))
    Assert.assertFalse(Files.exists(markerFile))
    Assert.assertTrue(store.listOutdated().isEmpty())
  }

  @Test fun `test jdk update failed`() {
    val update = newNotification("old-sdk2", newVersion = mockZipNewBroken)!!
    Assert.assertEquals(setOf(update), listOurActions().toSet())

    update.fireAction()
    doEventsWhile(2)

    val ourActions = listOurActions()
    val ourNotifications = listOurNotifications()
    Assert.assertEquals(setOf(update), listOurActions().toSet())
    Assert.assertEquals("$ourNotifications", 1, ourNotifications.size)
    Assert.assertEquals("$ourNotifications", 1, ourNotifications.filter { it.type == NotificationType.ERROR }.size)
    Assert.assertEquals(update.oldItem.versionString, update.jdk.versionString)
    Assert.assertTrue(!update.isTerminated())
  }

  @Test fun `test merge notifications correctly`() {
    val old1 = newNotification("old-1")!!
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    val actions = listOurActions()
    Assert.assertEquals("$actions", 1, actions.size)
  }

  @Test fun `test merge notifications correctly 2`() {
    val old1 = newNotification("old-1")!!
    val old2 = newNotification("old-2")!!
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)
    service<JdkUpdaterNotifications>().showNotification(old2.jdk, old2.oldItem, old2.newItem)
    service<JdkUpdaterNotifications>().showNotification(old2.jdk, old2.oldItem, old2.newItem)
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    val actions = listOurActions()
    Assert.assertEquals("$actions", 2, actions.size)
  }

  @Test fun `test replace notifications correctly 2`() {
    val old1 = newNotification("old-1")!!
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem.copy(jdkVersion = "17.0.777"))

    val actions = listOurActions()
    Assert.assertEquals("$actions", 1, actions.size)
    Assert.assertTrue("$actions", actions.single().newItem.jdkVersion == "17.0.777")
  }

  @Test fun `test replace notifications correctly 3`() {
    val old1 = newNotification("old-1")!!
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    doEventsWhile(5)
    Assert.assertEquals(1, listOurActions().size)

    runWriteAction {
      old1.jdk.sdkModificator.also { it.versionString = "new JDK version" }.commitChanges()
    }
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    //notification must expire and update
    Assert.assertEquals(1, listOurActions().size)
  }

  @Test fun `test replace notifications correctly 4`() {
    val old1 = newNotification("old-1")!!
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    Assert.assertEquals(1, listOurActions().size)

    runWriteAction {
      old1.jdk.sdkModificator.also { it.homePath += "-123" }.commitChanges()
    }
    service<JdkUpdaterNotifications>().showNotification(old1.jdk, old1.oldItem, old1.newItem)

    //notification must expire and update
    Assert.assertEquals(1, listOurActions().size)
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
  isPreview = false,
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

private fun doEventsWhile(iterations: Int = Int.MAX_VALUE / 2,
                          condition: () -> Boolean = { true }) {
  repeat(iterations) {
    if (!condition()) return

    ApplicationManager.getApplication().invokeAndWait {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }
    waitCoroutinesBlocking(service<JdkUpdaterNotifications>().getNotificationScope(), 1000)

    if (!condition()) return
    Thread.sleep(30)
  }
}

private fun runAction(theAction: AnAction) {
  ApplicationManager.getApplication().invokeAndWait {
    val event = KeyEvent(JPanel(), 1, 0, 0, 0, ' ')
    ActionManager.getInstance().tryToExecute(theAction, event, null, null, true)
  }
}

private fun runNotificationAction(notification: Notification, theAction: NotificationAction) {
  ApplicationManager.getApplication().invokeAndWait {
    val event = KeyEvent(JPanel(), 1, 0, 0, 0, ' ')
    val actionEvent = AnActionEvent.createEvent(theAction, DataContext.EMPTY_CONTEXT, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, event)
    theAction.actionPerformed(actionEvent, notification)
  }
}

private fun JdkUpdateNotification.fireAction() = runAction(updateAction)
