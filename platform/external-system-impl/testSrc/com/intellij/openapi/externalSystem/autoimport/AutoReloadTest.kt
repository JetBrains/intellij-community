// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.*
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.AutoReloadType.*
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.FAILURE
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFilesModificationContext.Event.UPDATE
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFilesModificationContext.ReloadStatus.IN_PROGRESS
import com.intellij.openapi.externalSystem.autoimport.MockProjectAware.ReloadCollisionPassType
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.openapi.externalSystem.util.runWriteActionAndWait
import com.intellij.testFramework.refreshVfs
import com.intellij.testFramework.utils.editor.saveToDisk
import com.intellij.testFramework.utils.vfs.getDocument
import kotlin.io.path.appendText
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText

class AutoReloadTest : AutoReloadTestCase() {

  fun `test simple modification tracking`() {
    test { settingsFile ->
      settingsFile.appendString("println 'hello'")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      settingsFile.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      settingsFile.replaceString("hi", "hello")
      assertStateAndReset(numReload = 0, notified = false, event = "revert changes")

      settingsFile.appendString("\n ")
      assertStateAndReset(numReload = 0, notified = false, event = "empty modification")
      settingsFile.replaceString("println", "print ln")
      assertStateAndReset(numReload = 0, notified = true, event = "split token by space")
      settingsFile.replaceString("print ln", "println")
      assertStateAndReset(numReload = 0, notified = false, event = "revert modification")

      settingsFile.appendString(" ")
      assertStateAndReset(numReload = 0, notified = false, event = "empty modification")
      settingsFile.appendString("//It is comment")
      assertStateAndReset(numReload = 0, notified = false, event = "append comment")
      settingsFile.insertStringAfter("println", "/*It is comment*/")
      assertStateAndReset(numReload = 0, notified = false, event = "append comment")
      settingsFile.insertString(0, "//")
      assertStateAndReset(numReload = 0, notified = true, event = "comment code")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      scheduleProjectReload()
      assertStateAndReset(numReload = 0, notified = false, event = "empty project refresh")
    }
  }

  fun `test simple modification tracking in xml`() {
    test {
      val settingsFile = createSettingsVirtualFile("settings.xml")
      assertStateAndReset(numReload = 0, notified = false, event = "empty settings files registered")
      settingsFile.replaceContent("""
        <element>
          <name description="This is a my super name">my-name</name>
        </element>
      """.trimIndent())
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "refresh project")

      settingsFile.replaceString("my-name", "my name")
      assertStateAndReset(numReload = 0, notified = true, event = "replace by space")
      settingsFile.replaceString("my name", "my-name")
      assertStateAndReset(numReload = 0, notified = false, event = "revert modification")
      settingsFile.replaceString("my-name", "my - name")
      assertStateAndReset(numReload = 0, notified = true, event = "split token by spaces")
      settingsFile.replaceString("my - name", "my-name")
      assertStateAndReset(numReload = 0, notified = false, event = "revert modification")
      settingsFile.replaceString("my-name", " my-name ")
      assertStateAndReset(numReload = 0, notified = false, event = "expand text token by spaces")
      settingsFile.insertStringAfter("</name>", " ")
      assertStateAndReset(numReload = 0, notified = false, event = "append space after tag")
      settingsFile.insertStringAfter("</name>", "\n  ")
      assertStateAndReset(numReload = 0, notified = false, event = "append empty line in file")
      settingsFile.replaceString("</name>", "</n am e>")
      assertStateAndReset(numReload = 0, notified = true, event = "split tag by spaces")
      settingsFile.replaceString("</n am e>", "</name>")
      assertStateAndReset(numReload = 0, notified = false, event = "revert modification")
      settingsFile.replaceString("</name>", "</ name >")
      assertStateAndReset(numReload = 0, notified = false, event = "expand tag brackets by spaces")
      settingsFile.replaceString("=", " = ")
      assertStateAndReset(numReload = 0, notified = false, event = "expand attribute definition")
      settingsFile.replaceString("my super name", "my  super  name")
      assertStateAndReset(numReload = 0, notified = true, event = "expand space inside attribute value")
      settingsFile.replaceString("my  super  name", "my super name")
      assertStateAndReset(numReload = 0, notified = false, event = "revert modification")
      settingsFile.insertStringAfter("my super name", " ")
      assertStateAndReset(numReload = 0, notified = true, event = "insert space in end of attribute")
      settingsFile.replaceString("my super name \"", "my super name\"")
      assertStateAndReset(numReload = 0, notified = false, event = "revert modification")
    }
  }

  fun `test unrecognized settings file`() {
    test {
      val settingsFile = createSettingsVirtualFile("settings.elvish")
      assertStateAndReset(numReload = 0, notified = false, event = "empty settings files registered")

      settingsFile.appendString("q71Gpj5 .9jR°`N.")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      settingsFile.replaceString("9jR°`N", "9`B")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      settingsFile.replaceString("9`B", "9jR°`N")
      assertStateAndReset(numReload = 0, notified = false, event = "revert changes")

      settingsFile.appendString(" ")
      assertStateAndReset(numReload = 0, notified = true, event = "unrecognized empty modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")
      settingsFile.appendString("//1G iT zt^P1Fp")
      assertStateAndReset(numReload = 0, notified = true, event = "unrecognized comment modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")
    }
  }

  fun `test deletion tracking`() {
    test { settingsFile ->
      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, notified = false, event = "settings is externally modified")

      settingsFile.delete()
      assertStateAndReset(numReload = 0, notified = true, event = "delete registered settings")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      var newSettingsFile = createFile(SETTINGS_FILE)
      newSettingsFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file content filled")
      newSettingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "modify registered settings")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      newSettingsFile.delete()
      assertStateAndReset(numReload = 0, notified = true, event = "delete registered settings")
      newSettingsFile = createFile(SETTINGS_FILE)
      newSettingsFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "create filled registered settings immediately after deleting")
      newSettingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 0, notified = false, event = "modify registered settings immediately after deleting")
    }
  }

  fun `test directory deletion tracking`() {
    test {
      val directory = findOrCreateDirectory("directory")
      val settingsFile1 = createSettingsVirtualFile("directory/settings.txt")
      assertStateAndReset(numReload = 0, notified = false, event = "empty settings file created")
      settingsFile1.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file content filled")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project reloaded")

      directory.delete()
      assertStateAndReset(numReload = 0, notified = true, event = "deleted directory with settings")
      findOrCreateDirectory("directory")
      assertStateAndReset(numReload = 0, notified = true, event = "deleted directory created without settings")
      val settingsFile2 = createSettingsVirtualFile("directory/settings.txt")
      settingsFile2.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = false, event = "reverted deleted settings")
    }
  }

  fun `test modification tracking with several settings files`() {
    test { settingsFile ->
      settingsFile.replaceContent("println 'hello'")
      assertStateAndReset(numReload = 0, notified = true, event = "settings file is modified")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project is reloaded")

      val configFile = createFile("config.groovy")
      assertStateAndReset(numReload = 0, notified = false, event = "create unregistered settings")
      configFile.replaceContent("println('hello')")
      assertStateAndReset(numReload = 0, notified = false, event = "modify unregistered settings")

      val scriptFile = createSettingsVirtualFile("script.groovy")
      assertStateAndReset(numReload = 0, notified = false, event = "empty settings files registered")
      scriptFile.replaceContent("println('hello')")
      assertStateAndReset(numReload = 0, notified = true, event = "modify settings file")
      settingsFile.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      settingsFile.replaceString("hi", "hello")
      assertStateAndReset(numReload = 0, notified = true, event = "try to revert changes if has other modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      settingsFile.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      scriptFile.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      settingsFile.replaceString("hi", "hello")
      assertStateAndReset(numReload = 0, notified = true, event = "try to revert changes if has other modification")
      scriptFile.replaceString("hi", "hello")
      assertStateAndReset(numReload = 0, notified = false, event = "revert changes")
    }
  }

  fun `test modification tracking with several sub projects`() {
    val systemId1 = ProjectSystemId("External System 1")
    val systemId2 = ProjectSystemId("External System 2")
    val projectId1 = ExternalSystemProjectId(systemId1, projectPath)
    val projectId2 = ExternalSystemProjectId(systemId2, projectPath)
    val projectAware1 = mockProjectAware(projectId1)
    val projectAware2 = mockProjectAware(projectId2)

    initialize()

    val scriptFile1 = createFile("script1.groovy")
    val scriptFile2 = createFile("script2.groovy")

    projectAware1.registerSettingsFile(scriptFile1)
    projectAware2.registerSettingsFile(scriptFile2)

    register(projectAware1)
    register(projectAware2)

    assertStateAndReset(projectAware1, numReload = 1, notified = false, event = "register project without cache")
    assertStateAndReset(projectAware2, numReload = 1, notified = false, event = "register project without cache")

    scriptFile1.appendString("println 1")
    assertStateAndReset(projectAware1, numReload = 0, notified = true, event = "modification of first settings")
    assertStateAndReset(projectAware2, numReload = 0, notified = false, event = "modification of first settings")

    scriptFile2.appendString("println 2")
    assertStateAndReset(projectAware1, numReload = 0, notified = true, event = "modification of second settings")
    assertStateAndReset(projectAware2, numReload = 0, notified = true, event = "modification of second settings")

    scriptFile1.removeContent()
    assertStateAndReset(projectAware1, numReload = 0, notified = false, event = "revert changes at second settings")
    assertStateAndReset(projectAware2, numReload = 0, notified = true, event = "revert changes at second settings")

    scheduleProjectReload()
    assertStateAndReset(projectAware1, numReload = 0, notified = false, event = "project refresh")
    assertStateAndReset(projectAware2, numReload = 1, notified = false, event = "project refresh")

    scriptFile1.replaceContent("println 'script 1'")
    scriptFile2.replaceContent("println 'script 2'")
    assertStateAndReset(projectAware1, numReload = 0, notified = true, event = "modification of both settings")
    assertStateAndReset(projectAware2, numReload = 0, notified = true, event = "modification of both settings")

    scheduleProjectReload()
    assertStateAndReset(projectAware1, numReload = 1, notified = false, event = "project refresh")
    assertStateAndReset(projectAware2, numReload = 1, notified = false, event = "project refresh")
  }

  fun `test project link-unlink`() {
    test { settingsFile ->
      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, numSubscribing = 0, numUnsubscribing = 0, notified = true, event = "modification")

      removeProjectAware()
      assertStateAndReset(numReload = 0, numSubscribing = 0, numUnsubscribing = 2, notified = false, event = "remove project")

      registerProjectAware()
      assertStateAndReset(numReload = 1, numSubscribing = 2, numUnsubscribing = 0, notified = false, event = "register project without cache")
    }
  }

  fun `test external modification tracking`() {
    test {
      var settingsFile = it

      settingsFile.replaceContentInIoFile("println 'hello'")
      assertStateAndReset(numReload = 1, notified = false, event = "untracked external modification")

      settingsFile.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "internal modification")

      settingsFile.replaceStringInIoFile("hi", "settings")
      assertStateAndReset(numReload = 0, notified = true, event = "untracked external modification during internal modification")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "refresh project")

      modification {
        assertStateAndReset(numReload = 0, notified = false, event = "start external modification")
        settingsFile.replaceStringInIoFile("settings", "modified settings")
        assertStateAndReset(numReload = 0, notified = false, event = "external modification")
      }
      assertStateAndReset(numReload = 1, notified = false, event = "complete external modification")

      modification {
        assertStateAndReset(numReload = 0, notified = false, event = "start external modification")
        settingsFile.replaceStringInIoFile("modified settings", "simple settings")
        assertStateAndReset(numReload = 0, notified = false, event = "external modification")
        settingsFile.replaceStringInIoFile("simple settings", "modified settings")
        assertStateAndReset(numReload = 0, notified = false, event = "revert external modification")
      }
      assertStateAndReset(numReload = 0, notified = false, event = "complete external modification")

      modification {
        assertStateAndReset(numReload = 0, notified = false, event = "start external modification")
        settingsFile.deleteIoFile()
        assertStateAndReset(numReload = 0, notified = false, event = "external deletion")
      }
      assertStateAndReset(numReload = 1, notified = false, event = "complete external modification")

      modification {
        assertStateAndReset(numReload = 0, notified = false, event = "start external modification")
        settingsFile = createIoFile(SETTINGS_FILE)
        assertStateAndReset(numReload = 0, notified = false, event = "external creation")
        settingsFile.replaceContentInIoFile("println 'settings'")
        assertStateAndReset(numReload = 0, notified = false, event = "external modification")
      }
      assertStateAndReset(numReload = 1, notified = false, event = "complete external modification")

      modification {
        assertStateAndReset(numReload = 0, notified = false, event = "start first external modification")
        settingsFile.replaceStringInIoFile("settings", "hello")
        assertStateAndReset(numReload = 0, notified = false, event = "first external modification")
        modification {
          assertStateAndReset(numReload = 0, notified = false, event = "start second external modification")
          settingsFile.replaceStringInIoFile("hello", "hi")
          assertStateAndReset(numReload = 0, notified = false, event = "second external modification")
        }
        assertStateAndReset(numReload = 0, notified = false, event = "complete second external modification")
      }
      assertStateAndReset(numReload = 1, notified = false, event = "complete first external modification")

      modification {
        assertStateAndReset(numReload = 0, notified = false, event = "start external modification")
        settingsFile.replaceStringInIoFile("println", "print")
        assertStateAndReset(numReload = 0, notified = false, event = "external modification")
        settingsFile.replaceString("hi", "hello")
        assertStateAndReset(numReload = 0, notified = false, event = "internal modification during external modification")
        settingsFile.replaceStringInIoFile("hello", "settings")
        assertStateAndReset(numReload = 0, notified = false, event = "external modification")
      }
      assertStateAndReset(numReload = 0, notified = true, event = "complete external modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "refresh project")
    }
  }

  fun `test tracker store and restore`() {
    val projectAware = mockProjectAware()
    val settingsFile = findOrCreateFile(SETTINGS_FILE)
    projectAware.registerSettingsFile(settingsFile)

    var state = testProjectTrackerState(projectAware) {
      assertStateAndReset(numReload = 1, notified = false, event = "register project without cache")

      settingsFile.replaceContent("println 'hello'")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")
    }

    state = testProjectTrackerState(projectAware, state) {
      assertStateAndReset(numReload = 0, notified = false, event = "register project with correct cache")

      settingsFile.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")
    }

    settingsFile.replaceStringInIoFile("hi", "hello")

    state = testProjectTrackerState(projectAware, state) {
      assertStateAndReset(numReload = 1, notified = false, event = "register project with external modifications")

      settingsFile.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")
    }

    state = testProjectTrackerState(projectAware, state) {
      assertStateAndReset(numReload = 0, notified = false, event = "register project with correct cache")

      settingsFile.replaceString("hi", "hello")
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
    }

    testProjectTrackerState(projectAware, state) {
      assertStateAndReset(numReload = 1, notified = false, event = "register project with previous modifications")
    }
  }

  fun `test move and rename settings files`() {
    test { settingsFile ->
      registerSettingsFile("script.groovy")
      registerSettingsFile("dir/script.groovy")
      registerSettingsFile("dir1/script.groovy")
      registerSettingsFile("dir/dir1/script.groovy")

      settingsFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file content filled")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      var scriptFile = settingsFile.copy("script.groovy")
      assertStateAndReset(numReload = 0, notified = true, event = "copy to registered settings")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      scriptFile.delete()
      assertStateAndReset(numReload = 0, notified = true, event = "delete file")
      scriptFile = settingsFile.copy("script.groovy")
      assertStateAndReset(numReload = 0, notified = false, event = "revert delete by copy")
      val configurationFile = settingsFile.copy("configuration.groovy")
      assertStateAndReset(numReload = 0, notified = false, event = "copy to registered settings")
      configurationFile.delete()
      assertStateAndReset(numReload = 0, notified = false, event = "delete file")

      val dir = findOrCreateDirectory("dir")
      val dir1 = findOrCreateDirectory("dir1")
      assertStateAndReset(numReload = 0, notified = false, event = "create directory")
      scriptFile.move(dir)
      assertStateAndReset(numReload = 0, notified = true, event = "move settings to directory")
      scriptFile.move(projectRoot)
      assertStateAndReset(numReload = 0, notified = false, event = "revert move settings")
      scriptFile.move(dir1)
      assertStateAndReset(numReload = 0, notified = true, event = "move settings to directory")
      dir1.move(dir)
      assertStateAndReset(numReload = 0, notified = true, event = "move directory with settings to other directory")
      scriptFile.move(projectRoot)
      assertStateAndReset(numReload = 0, notified = false, event = "revert move settings")
      scriptFile.move(dir)
      assertStateAndReset(numReload = 0, notified = true, event = "move settings to directory")
      dir.rename("dir1")
      assertStateAndReset(numReload = 0, notified = true, event = "rename directory with settings")
      scriptFile.move(projectRoot)
      assertStateAndReset(numReload = 0, notified = false, event = "revert move settings")

      settingsFile.rename("configuration.groovy")
      assertStateAndReset(numReload = 0, notified = true, event = "rename")
      settingsFile.rename(SETTINGS_FILE)
      assertStateAndReset(numReload = 0, notified = false, event = "revert rename")
    }
  }

  fun `test document changes between save`() {
    test { settingsFile ->
      val settingsDocument = runReadAction {
        settingsFile.getDocument()
      }

      settingsDocument.replaceContent("println 'hello'")
      assertStateAndReset(numReload = 0, notified = true, event = "change")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "refresh project")

      settingsDocument.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "change")
      settingsDocument.replaceString("hi", "hello")
      assertStateAndReset(numReload = 0, notified = false, event = "revert change")
      settingsDocument.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "change")
      settingsDocument.save()
      assertStateAndReset(numReload = 0, notified = true, event = "save")
      settingsDocument.replaceString("hi", "hello")
      assertStateAndReset(numReload = 0, notified = false, event = "revert change after save")
      settingsDocument.save()
      assertStateAndReset(numReload = 0, notified = false, event = "save reverted changes")
    }
  }

  fun `test processing of failure refresh`() {
    test { settingsFile ->
      settingsFile.replaceContentInIoFile("println 'hello'")
      assertStateAndReset(numReload = 1, notified = false, event = "external change")
      setReloadStatus(FAILURE)
      settingsFile.replaceStringInIoFile("hello", "hi")
      assertStateAndReset(numReload = 1, notified = true, event = "external change with failure refresh")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = true, event = "failure project refresh")
      setReloadStatus(SUCCESS)
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      settingsFile.replaceString("hi", "hello")
      assertStateAndReset(numReload = 0, notified = true, event = "modify")
      setReloadStatus(FAILURE)
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = true, event = "failure project refresh")
      settingsFile.replaceString("hello", "hi")
      assertStateAndReset(numReload = 0, notified = true, event = "try to revert changes after failure refresh")
      setReloadStatus(SUCCESS)
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")
    }
  }

  fun `test files generation during refresh`() {
    test { settingsFile ->
      settingsFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file content filled")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project is reloaded")

      onceWhenReloading {
        registerSettingsFile(settingsFile)
      }
      forceReloadProject()
      assertStateAndReset(numReload = 1, notified = false, event = "settings file is registered during reload")

      settingsFile.delete()
      assertStateAndReset(numReload = 0, notified = true, event = "settings file is deleted")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project is reloaded")

      onceWhenReloading {
        createIoFileUnsafe(SETTINGS_FILE).writeText(SAMPLE_TEXT)
      }
      forceReloadProject()
      assertStateAndReset(numReload = 1, notified = false, event = "settings file is externally created during reload")

      onceWhenReloading {
        getFile(SETTINGS_FILE).modify(INTERNAL)
      }
      forceReloadProject()
      assertStateAndReset(numReload = 1, notified = true, event = "settings file is internally modified during reload")
    }
  }

  fun `test disabling of auto-import`() {
    val projectAware = mockProjectAware()
    val settingsFile = findOrCreateFile(SETTINGS_FILE)
    projectAware.registerSettingsFile(settingsFile)

    var state = testProjectTrackerState(projectAware) {
      assertStateAndReset(numReload = 1, autoReloadType = SELECTIVE, notified = false, event = "register project without cache")
      setAutoReloadType(NONE)
      assertStateAndReset(numReload = 0, autoReloadType = NONE, notified = false, event = "disable project auto-import")
      settingsFile.replaceContentInIoFile("println 'hello'")
      assertStateAndReset(numReload = 0, autoReloadType = NONE, notified = true, event = "modification with disabled auto-import")
    }
    state = testProjectTrackerState(projectAware, state) {
      // Open modified project with disabled auto-import for external changes
      assertStateAndReset(numReload = 0, autoReloadType = NONE, notified = true, event = "register modified project")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, autoReloadType = NONE, notified = false, event = "refresh project")

      // Checkout git branch, that has additional linked project
      withLinkedProject("module", SETTINGS_FILE) { moduleSettingsFile ->
        assertStateAndReset(numReload = 0, autoReloadType = NONE, notified = true, event = "register project without cache with disabled auto-import")
        moduleSettingsFile.replaceContentInIoFile("println 'hello'")
        assertStateAndReset(numReload = 0, autoReloadType = NONE, notified = true, event = "modification with disabled auto-import")
      }
      assertStateAndReset(numReload = 0, autoReloadType = NONE, notified = false, event = "remove modified linked project")

      setAutoReloadType(SELECTIVE)
      assertStateAndReset(numReload = 0, autoReloadType = SELECTIVE, notified = false, event = "enable auto-import for project without modifications")
      setAutoReloadType(NONE)
      assertStateAndReset(numReload = 0, autoReloadType = NONE, notified = false, event = "disable project auto-import")

      settingsFile.replaceStringInIoFile("hello", "hi")
      assertStateAndReset(numReload = 0, autoReloadType = NONE, notified = true, event = "modification with disabled auto-import")
      setAutoReloadType(SELECTIVE)
      assertStateAndReset(numReload = 1, autoReloadType = SELECTIVE, notified = false, event = "enable auto-import for modified project")
    }
    testProjectTrackerState(projectAware, state) {
      assertStateAndReset(numReload = 0, autoReloadType = SELECTIVE, notified = false, event = "register project with correct cache")
    }
  }

  fun `test activation of auto-import`() {
    val systemId = ProjectSystemId("External System")
    val projectId1 = ExternalSystemProjectId(systemId, projectPath)
    val projectId2 = ExternalSystemProjectId(systemId, "$projectPath/sub-project")
    val projectAware1 = mockProjectAware(projectId1)
    val projectAware2 = mockProjectAware(projectId2)

    initialize()

    register(projectAware1, activate = false)
    assertStateAndReset(projectAware1, numReload = 0, notified = true, activated = false, event = "register inactive project")

    activate(projectId1)
    assertStateAndReset(projectAware1, numReload = 1, notified = false, activated = true, event = "activate project")

    register(projectAware2, activate = false)
    assertStateAndReset(projectAware1, numReload = 0, notified = false, activated = true, event = "register inactive project 2")
    assertStateAndReset(projectAware2, numReload = 0, notified = true, activated = false, event = "register inactive project 2")

    registerSettingsFile(projectAware1, "settings.groovy")
    registerSettingsFile(projectAware2, "sub-project/settings.groovy")
    val settingsFile1 = createIoFile("settings.groovy")
    val settingsFile2 = createIoFile("sub-project/settings.groovy")
    assertStateAndReset(projectAware1, numReload = 0, notified = false, activated = true, event = "externally created both empty settings files, but project 2 is inactive")
    assertStateAndReset(projectAware2, numReload = 0, notified = true, activated = false, event = "externally created both empty settings files, but project 2 is inactive")

    settingsFile1.replaceContentInIoFile("println 'hello'")
    settingsFile2.replaceContentInIoFile("println 'hello'")
    assertStateAndReset(projectAware1, numReload = 1, notified = false, activated = true, event = "externally modified both settings files, but project 2 is inactive")
    assertStateAndReset(projectAware2, numReload = 0, notified = true, activated = false, event = "externally modified both settings files, but project 2 is inactive")

    settingsFile1.replaceString("hello", "Hello world!")
    settingsFile2.replaceString("hello", "Hello world!")
    assertStateAndReset(projectAware1, numReload = 0, notified = true, activated = true, event = "internally modify settings")
    assertStateAndReset(projectAware2, numReload = 0, notified = true, activated = false, event = "internally modify settings")

    scheduleProjectReload()
    assertStateAndReset(projectAware1, numReload = 1, notified = false, activated = true, event = "refresh project")
    assertStateAndReset(projectAware2, numReload = 1, notified = false, activated = true, event = "refresh project")
  }

  fun `test enabling-disabling internal-external changes importing`() {
    test { settingsFile ->
      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, autoReloadType = SELECTIVE, event = "internal modification")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, autoReloadType = SELECTIVE, event = "refresh project")

      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, notified = false, autoReloadType = SELECTIVE, event = "external modification")

      setAutoReloadType(ALL)

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 1, notified = false, autoReloadType = ALL, event = "internal modification with enabled auto-reload")

      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, notified = false, autoReloadType = ALL, event = "external modification with enabled auto-reload")

      setAutoReloadType(NONE)

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, autoReloadType = NONE, event = "internal modification with disabled auto-reload")

      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 0, notified = true, autoReloadType = NONE, event = "external modification with disabled auto-reload")

      setAutoReloadType(SELECTIVE)
      assertStateAndReset(numReload = 0, notified = true, autoReloadType = SELECTIVE,
                          event = "enable auto-reload external changes with internal and external modifications")

      setAutoReloadType(ALL)
      assertStateAndReset(numReload = 1, notified = false, autoReloadType = ALL, event = "enable auto-reload of any changes")

      setAutoReloadType(NONE)

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, autoReloadType = NONE, event = "internal modification with disabled auto-reload")

      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 0, notified = true, autoReloadType = NONE, event = "external modification with disabled auto-reload")

      setAutoReloadType(ALL)
      assertStateAndReset(numReload = 1, notified = false, autoReloadType = ALL, event = "enable auto-reload of any changes")
    }
  }

  fun `test failure auto-reload with enabled auto-reload of any changes`() {
    test { settingsFile ->
      setAutoReloadType(ALL)
      setReloadStatus(FAILURE)
      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 1, notified = true, autoReloadType = ALL, event = "failure modification with enabled auto-reload")

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 1, notified = true, autoReloadType = ALL, event = "failure modification with enabled auto-reload")

      setReloadStatus(SUCCESS)
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, autoReloadType = ALL, event = "refresh project")

      setReloadStatus(FAILURE)
      onceWhenReloading {
        setReloadStatus(SUCCESS)
        settingsFile.modify(INTERNAL)
      }
      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 2, notified = false, autoReloadType = ALL, event = "success modification after failure")
    }
  }

  fun `test up-to-date promise after modifications with enabled auto-import`() {
    test { settingsFile ->
      for (collisionPassType in ReloadCollisionPassType.entries) {
        setReloadCollisionPassType(collisionPassType)

        setAutoReloadType(SELECTIVE)
        onceWhenReloading {
          settingsFile.modify(EXTERNAL)
        }
        settingsFile.modify(EXTERNAL)
        assertStateAndReset(numReload = 2, notified = false, autoReloadType = SELECTIVE, event = "auto-reload inside reload ($collisionPassType)")

        setAutoReloadType(ALL)
        onceWhenReloading {
          settingsFile.modify(INTERNAL)
        }
        settingsFile.modify(INTERNAL)
        assertStateAndReset(numReload = 2, notified = false, autoReloadType = ALL, event = "auto-reload inside reload ($collisionPassType)")
      }
    }
  }

  fun `test providing explicit reload`() {
    test { settingsFile ->
      onceWhenReloading {
        assertFalse("implicit reload after external modification", it.isExplicitReload)
      }
      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, notified = false, event = "external modification")

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "internal modification")
      onceWhenReloading {
        assertTrue("explicit reload after explicit scheduling of project reload", it.isExplicitReload)
      }
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project reload")
    }
  }

  fun `test settings files modification partition`() {
    test {
      val settingsFile1 = createSettingsVirtualFile("settings1.groovy")
      val settingsFile2 = createSettingsVirtualFile("settings2.groovy")
      val settingsFile3 = createSettingsVirtualFile("settings3.groovy")
      assertStateAndReset(numReload = 0, notified = false, event = "empty settings files registered")
      settingsFile1.appendString(SAMPLE_TEXT)
      settingsFile2.appendString(SAMPLE_TEXT)
      settingsFile3.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings files' content filled")

      onceWhenReloading {
        assertFalse(it.hasUndefinedModifications)
        assertEquals(pathsOf(), it.settingsFilesContext.updated)
        assertEquals(pathsOf(settingsFile1, settingsFile2, settingsFile3), it.settingsFilesContext.created)
        assertEquals(pathsOf(), it.settingsFilesContext.deleted)
      }
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project reload")

      settingsFile1.delete()
      settingsFile2.appendLine("println 'hello'")
      settingsFile3.appendLine("")
      assertStateAndReset(numReload = 0, notified = true, event = "settings files modification")

      onceWhenReloading {
        assertFalse(it.hasUndefinedModifications)
        assertEquals(pathsOf(settingsFile2), it.settingsFilesContext.updated)
        assertEquals(pathsOf(), it.settingsFilesContext.created)
        assertEquals(pathsOf(settingsFile1), it.settingsFilesContext.deleted)
      }
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project reload")

      settingsFile2.delete()
      settingsFile3.delete()
      markDirty()
      assertStateAndReset(numReload = 0, notified = true, event = "settings files deletion")

      onceWhenReloading {
        assertTrue(it.hasUndefinedModifications)
        assertEquals(pathsOf(), it.settingsFilesContext.updated)
        assertEquals(pathsOf(), it.settingsFilesContext.created)
        assertEquals(pathsOf(settingsFile2, settingsFile3), it.settingsFilesContext.deleted)
      }
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project reload")
    }
  }

  fun `test settings files cache`() {
    test {
      val settings1File = createSettingsVirtualFile("settings1.groovy")
      val settings2File = createSettingsVirtualFile("settings2.groovy")
      assertStateAndReset(numReload = 0, numSettingsAccess = 2, notified = false, event = "empty settings files registered")

      settings1File.appendString(SAMPLE_TEXT)
      settings2File.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, numSettingsAccess = 0, notified = true, event = "settings files' content filled")

      val configFile1 = createFile("file1.config")
      val configFile2 = createFile("file2.config")
      assertStateAndReset(numReload = 0, numSettingsAccess = 2, notified = true, event = "non settings files creation")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, numSettingsAccess = 2, notified = false, event = "project reload")

      configFile1.modify(INTERNAL)
      configFile2.modify(INTERNAL)
      configFile1.modify(EXTERNAL)
      configFile2.modify(EXTERNAL)
      assertStateAndReset(numReload = 0, numSettingsAccess = 0, notified = false, event = "non settings files modification")

      settings1File.modify(INTERNAL)
      settings2File.modify(INTERNAL)
      assertStateAndReset(numReload = 0, numSettingsAccess = 0, notified = true, event = "internal settings files modification")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, numSettingsAccess = 2, notified = false, event = "project reload")

      settings1File.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, numSettingsAccess = 2, notified = false, event = "external settings file modification")

      registerSettingsFile("settings3.groovy")
      val settings3File = settings2File.copy("settings3.groovy")
      assertStateAndReset(numReload = 0, numSettingsAccess = 1, notified = true, event = "copy settings file")

      settings1File.modify(INTERNAL)
      settings2File.modify(INTERNAL)
      settings3File.modify(INTERNAL)
      assertStateAndReset(numReload = 0, numSettingsAccess = 0, notified = true, event = "internal settings files modification")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, numSettingsAccess = 2, notified = false, event = "project reload")

      settings3File.modify(INTERNAL)
      assertStateAndReset(numReload = 0, numSettingsAccess = 0, notified = true, event = "internal settings file modification")

      settings3File.revert()
      assertStateAndReset(numReload = 0, numSettingsAccess = 0, notified = false, event = "revert modification in settings file")

      registerSettingsFile("settings4.groovy")
      configFile1.rename("settings4.groovy")
      assertStateAndReset(numReload = 0, numSettingsAccess = 1, notified = true, event = "rename config file into settings file")

      configFile1.modify(INTERNAL)
      assertStateAndReset(numReload = 0, numSettingsAccess = 0, notified = true, event = "modify settings file")

      configFile1.rename("file1.config")
      assertStateAndReset(numReload = 0, numSettingsAccess = 1, notified = false, event = "revert config file rename")

      registerSettingsFile("my-dir/file1.config")
      val myDir = findOrCreateDirectory("my-dir")
      // Implementation detail, settings file cache resets on any file creation
      assertStateAndReset(numReload = 0, numSettingsAccess = 1, notified = false, event = "created directory")
      configFile1.move(myDir)
      assertStateAndReset(numReload = 0, numSettingsAccess = 1, notified = true, event = "move config file")

      configFile1.modify(INTERNAL)
      assertStateAndReset(numReload = 0, numSettingsAccess = 0, notified = true, event = "modify config file")

      configFile1.move(projectRoot)
      assertStateAndReset(numReload = 0, numSettingsAccess = 1, notified = false, event = "revert config file move")
    }
  }

  fun `test configuration for unknown file type`() {
    test("unknown") { settingsFile ->
      settingsFile.replaceContent(byteArrayOf(1, 2, 3))
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload")

      settingsFile.replaceContent(byteArrayOf(1, 2, 3))
      assertStateAndReset(numReload = 0, notified = false, event = "empty modification")
      settingsFile.replaceContent(byteArrayOf(3, 2, 1))
      assertStateAndReset(numReload = 0, notified = true, event = "modification")
      settingsFile.replaceContent(byteArrayOf(1, 2, 3))
      assertStateAndReset(numReload = 0, notified = false, event = "revert modification")
    }
  }

  fun `test settings file modification by java nio before sync`() {
    test { settingsFile ->
      val settingsPath = settingsFile.toNioPath()

      settingsPath.appendText(SAMPLE_TEXT + "\n")
      onceWhenReloading { settingsPath.refreshVfs() }
      forceReloadProject()
      assertStateAndReset(numReload = 1, notified = false, event = "settings file modified by java nio before sync")

      settingsPath.deleteExisting()
      onceWhenReloading { settingsPath.refreshVfs() }
      forceReloadProject()
      assertStateAndReset(numReload = 1, notified = false, event = "settings file deleted by java nio before sync")

      settingsPath.createFile()
      onceWhenReloading { settingsPath.refreshVfs() }
      forceReloadProject()
      assertStateAndReset(numReload = 1, notified = false, event = "settings file created by java nio before sync")
    }
  }

  fun `test settings file modification by document before sync`() {
    test { settingsFile ->
      val settingsDocument = runReadAction {
        settingsFile.getDocument()
      }

      settingsDocument.appendString(SAMPLE_TEXT + "\n")
      onceWhenReloading {
        runWriteActionAndWait {
          settingsDocument.saveToDisk()
        }
      }
      forceReloadProject()
      assertStateAndReset(numReload = 1, notified = false, event = "settings file modified by document before sync")
    }
  }

  fun `test settings file modification during sync`() {
    test { settingsFile ->
      whenReloading(1) {
        settingsFile.modify(EXTERNAL)
      }
      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 2, notified = false, event = "settings file modified during sync")
    }
  }

  fun `test force sync action during sync`() {
    test {
      whenReloadStarted(1) {
        forceReloadProject()
      }
      forceReloadProject()
      assertStateAndReset(numReload = 2, notified = false, event = "test force sync action during sync")
    }
  }

  fun `test generation during reload`() {
    test {
      onceWhenReloading {
        createSettingsVirtualFile("settings1.cfg")
      }
      forceReloadProject()
      assertStateAndReset(numReload = 1, notified = false, event = "create file during reload")

      onceWhenReloading {
        createSettingsVirtualFile("settings2.cfg")
          .replaceContent("{ name: project }")
      }
      forceReloadProject()
      assertStateAndReset(numReload = 1, notified = false, event = "create file and modify it during reload")

      findOrCreateFile("settings2.cfg")
        .appendLine("{ type: Java }")
      assertStateAndReset(numReload = 0, notified = true, event = "internal modification")
    }
  }

  fun `test handle explicit settings files list change event`() {
    initialize()
    setAutoReloadType(ALL)

    val settingsFile1 = createFile("script1.groovy")
    settingsFile1.appendString(SAMPLE_TEXT)
    val settingsFile2 = createFile("script2.groovy")
    settingsFile2.appendString(SAMPLE_TEXT)

    val projectAware = mockProjectAware()
    projectAware.registerSettingsFile(settingsFile1)
    register(projectAware)
    assertStateAndReset(projectAware, numReload = 1, notified = false, event = "register project")

    projectAware.fireSettingsFilesListChanged()
    assertStateAndReset(projectAware, numReload = 0, notified = false, event = "handle settings files list change event when nothing actually changed")

    projectAware.registerSettingsFile(settingsFile2)
    projectAware.fireSettingsFilesListChanged()
    assertStateAndReset(projectAware, numReload = 1, notified = false, event = "handle settings files list change event when file added")
  }

  fun `test partial ignoring settings files modification events`() {
    test {
      ignoreSettingsFileWhen("ignored.groovy") { it.event == UPDATE }
      val ignoredSettingsFile = createSettingsVirtualFile("ignored.groovy")
      assertStateAndReset(numReload = 0, notified = false, event = "empty settings files registered")
      ignoredSettingsFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file content filled")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload")
      ignoredSettingsFile.modify()
      assertStateAndReset(numReload = 0, notified = false, event = "settings file ignored modification")
      markDirty()
      ignoredSettingsFile.modify()
      assertStateAndReset(numReload = 0, notified = true, event = "settings file ignored modification with dirty AI state")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload")
      ignoredSettingsFile.delete()
      assertStateAndReset(numReload = 0, notified = true, event = "settings files deletion")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload")

      ignoreSettingsFileWhen("build.lock") { it.reloadStatus == IN_PROGRESS && it.modificationType == EXTERNAL }
      val propertiesFile = createSettingsVirtualFile("build.lock")
      assertStateAndReset(numReload = 0, notified = false, event = "empty settings files registered")
      propertiesFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file content filled")
      onceWhenReloading {
        propertiesFile.modify(EXTERNAL)
      }
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "ignored settings file creation during reload")
      propertiesFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, notified = false, event = "settings file modification")
      onceWhenReloading {
        propertiesFile.modify(INTERNAL)
      }
      markDirty()
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = true, event = "settings file modification during reload")
    }
  }

  fun `test adjust modification type to hidden with any changes`() {
    test { settingsFile ->
      setAutoReloadType(ALL)
      setModificationTypeAdjustingRule { path, type ->
        if (type == INTERNAL && path.endsWith(".hidden")) HIDDEN else type
      }

      val hiddenSettingsFile = createSettingsVirtualFile("settings.hidden")
      assertStateAndReset(numReload = 0, notified = false, event = "empty settings files registered", autoReloadType = ALL)
      hiddenSettingsFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file content filled", autoReloadType = ALL)

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload", autoReloadType = ALL)

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 1, notified = false, event = "settings file modification", autoReloadType = ALL)

      hiddenSettingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file modification", autoReloadType = ALL)

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload", autoReloadType = ALL)

      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, notified = false, event = "settings file modification", autoReloadType = ALL)

      hiddenSettingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, notified = false, event = "settings file modification", autoReloadType = ALL)

      hiddenSettingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file modification", autoReloadType = ALL)

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 1, notified = false, event = "settings file modification", autoReloadType = ALL)
    }
  }

  // TODO this test doesn't really test adjusting modification type when the reload type is SELECTIVE -
  //  the test passes with and without type adjustment
  fun `test adjust modification type to hidden with external changes`() {
    test { settingsFile ->
      setModificationTypeAdjustingRule { path, type ->
        if (type == INTERNAL && path.endsWith(".hidden")) HIDDEN else type
      }

      val hiddenSettingsFile = createSettingsVirtualFile("settings.hidden")
      assertStateAndReset(numReload = 0, notified = false, event = "empty settings files registered")
      hiddenSettingsFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file content filled")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload")

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file modification")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload")

      hiddenSettingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file modification")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload")

      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, notified = false, event = "settings file modification")
      hiddenSettingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 1, notified = false, event = "settings file modification")

      hiddenSettingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file modification")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload")

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file modification")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "reload")
    }
  }

  fun`test with custom CRC calculator`() {
    test { settingsFile ->
      val forbiddenText = "forbidden_"
      createCustomCrcCalculator { tokenText ->
        forbiddenText.contentEquals(tokenText)
      }

      settingsFile.appendString(forbiddenText)
      assertStateAndReset(numReload = 0, notified = false, event = "non-CRC-changing modification")

      settingsFile.replaceContent("something")
      assertStateAndReset(numReload = 0, notified = true, event = "CRC-changing modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      settingsFile.replaceContent("$forbiddenText  ")
      assertStateAndReset(numReload = 0, notified = true, event = "revert from CRC != 0 to CRC == 0")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      settingsFile.appendString("// some  text")
      settingsFile.appendString("  ")
      assertStateAndReset(numReload = 0, notified = false, event = "non-CRC-changing modifications")

      settingsFile.delete()
      assertStateAndReset(numReload = 0, notified = false, event = "settings file with CRC == 0 deletion")

      val newSettingsFile = createFile(SETTINGS_FILE)
      assertStateAndReset(numReload = 0, notified = false, event = "create empty registered settings")

      newSettingsFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "CRC-changing modification")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      newSettingsFile.delete()
      assertStateAndReset(numReload = 0, notified = true, event = "settings file with CRC != 0 deletion")
    }
  }

  fun `test settings file deletion by java nio`() {
    test { settingsFile ->
      settingsFile.appendString(SAMPLE_TEXT)
      assertStateAndReset(numReload = 0, notified = true, event = "settings file content filled")
      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "project refresh")

      settingsFile.toNioPath().deleteExisting()
      createSettingsVirtualFile("config.groovy") // it's just to trigger AsyncFilesChangesListener
      assertStateAndReset(numReload = 0, notified = false, event = "settings file deleted by java nio")
    }
  }

  fun `test disabled reload state customisation`() {
    test { settingsFile ->
      projectAware.setDisabledReload(true)
      assertStateAndReset(numReload = 0, notified = false, event = "disabled reload")

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = false, event = "modify internally when disabled reload")

      settingsFile.revert()
      assertStateAndReset(numReload = 0, notified = false, event = "revert all changes when disabled reload")

      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 0, notified = false, event = "modify externally when disabled reload")

      scheduleChangeProcessing()
      assertStateAndReset(numReload = 0, notified = false, event = "schedule implicit project reload with pending external changes when disabled reload")

      scheduleProjectReload()
      assertStateAndReset(numReload = 0, notified = false, event = "schedule explicit project reload with pending external changes when disabled reload")

      projectAware.setDisabledReload(false) // setDisabledReload function doesn't produce change event
      assertStateAndReset(numReload = 0, notified = false, event = "enable reload with external pending changes")

      scheduleChangeProcessing()
      assertStateAndReset(numReload = 1, notified = false, event = "schedule implicit project reload with pending external changes")
    }
  }

  fun `test disabled auto-reload state customisation`() {
    test { settingsFile ->
      projectAware.setDisabledAutoReload(true)
      assertStateAndReset(numReload = 0, notified = false, event = "disabled reload")

      settingsFile.modify(INTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "modify internally when disabled reload")

      settingsFile.revert()
      assertStateAndReset(numReload = 0, notified = false, event = "revert all changes when disabled reload")

      settingsFile.modify(EXTERNAL)
      assertStateAndReset(numReload = 0, notified = true, event = "modify externally when disabled reload")

      scheduleChangeProcessing()
      assertStateAndReset(numReload = 0, notified = true, event = "schedule implicit project reload with pending external changes when disabled reload")

      scheduleProjectReload()
      assertStateAndReset(numReload = 1, notified = false, event = "schedule explicit project reload with pending external changes when disabled reload")

      projectAware.setDisabledAutoReload(false) // setDisabledAutoReload function doesn't produce change event
      assertStateAndReset(numReload = 0, notified = false, event = "enable reload with external pending changes")

      scheduleChangeProcessing()
      assertStateAndReset(numReload = 0, notified = false, event = "schedule implicit project reload without any changes")
    }
  }

  fun `test reload is enabled during reload of other project`() {
    val systemId = ProjectSystemId("External System")
    val projectId1 = ExternalSystemProjectId(systemId, "$projectPath/project1")
    val projectId2 = ExternalSystemProjectId(systemId, "$projectPath/project2")
    val projectAware1 = mockProjectAware(projectId1)
    val projectAware2 = mockProjectAware(projectId2)

    register(projectAware1)
    assertStateAndReset(projectAware1, numReload = 1, notified = false, event = "register project 1 without caches")

    register(projectAware2)
    assertStateAndReset(projectAware2, numReload = 1, notified = false, event = "register project 2 without caches")

    projectAware1.reloadEventDispatcher.onceWhenEventHappened {
      markDirty(projectId2)
      scheduleProjectReload()
      assertStateAndReset(projectAware1, numReload = 1, notified = false, event = "reload project 2 during project 1 reload")
      assertStateAndReset(projectAware2, numReload = 1, notified = false, event = "reload project 2 during project 1 reload")
    }

    markDirty(projectId1)
    scheduleProjectReload()
    assertStateAndReset(projectAware1, numReload = 0, notified = false, event = "reload project 1 and 2 completed")
    assertStateAndReset(projectAware2, numReload = 0, notified = false, event = "reload project 1 and 2 completed")
  }
}