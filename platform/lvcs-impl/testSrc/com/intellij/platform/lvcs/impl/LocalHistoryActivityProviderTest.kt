// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.ActivityId
import com.intellij.history.LocalHistory
import com.intellij.history.integration.IntegrationTestCase
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.HeavyPlatformTestCase
import junit.framework.TestCase

class LocalHistoryActivityProviderTest : IntegrationTestCase() {
  override fun setUp() {
    super.setUp()

    Registry.get("lvcs.show.system.labels.in.activity.view").setValue(false, testRootDisposable)
  }

  fun `test single file`() {
    val file = createFile("file.txt")
    val otherFile = createFile("other.txt")

    val keyword = "239"
    val contents = listOf("initial", "content1$keyword", "content2", "content3", "current")

    for (c in contents) {
      setContent(file, c)
      setContent(otherFile, c)
    }

    val provider = LocalHistoryActivityProvider(project, gateway)
    val scope = ActivityScope.fromFile(file)

    val fileActivity = provider.loadActivityList(scope, null)
    TestCase.assertEquals(/* file created + content changes */contents.size + 1, fileActivity.items.size)

    val activityContents = getContentFor(file, fileActivity.getChangeSets())
    TestCase.assertEquals(/* content before change happened */listOf("") + contents.dropLast(1), activityContents.reversed())

    val filteredActivity = provider.filterActivityList(scope, fileActivity, keyword)
    TestCase.assertEquals(fileActivity.items[2], filteredActivity?.single())
  }

  fun `test multiple labels`() {
    val file = createFile("file.txt")

    setContent(file, "initial")
    val systemLabel = "system label"
    vcs.putSystemLabel(systemLabel, project.locationHash, -1)

    setContent(file, "content1")
    val userLabel = "user label"
    vcs.putUserLabel(userLabel, project.locationHash)

    setContent(file, "content2")
    val visibleUserLabel = "visible user label"
    vcs.putUserLabel(visibleUserLabel, project.locationHash)
    val hiddenUserLabel1 = "hidden user label 1"
    vcs.putUserLabel(hiddenUserLabel1, project.locationHash)
    val hiddenUserLabel2 = "hidden user label 2"
    vcs.putUserLabel(hiddenUserLabel2, project.locationHash)

    val provider = LocalHistoryActivityProvider(project, gateway)
    val scope = ActivityScope.fromFile(file)

    val activityList = provider.loadActivityList(scope, null)
    val labelNames = activityList.getLabelNameSet()

    TestCase.assertTrue(labelNames.containsAll(listOf(userLabel, visibleUserLabel)))
    TestCase.assertTrue(labelNames.intersect(listOf(systemLabel, hiddenUserLabel1, hiddenUserLabel2)).isEmpty())
  }

  fun `test multiple event and user labels`() {
    val file = createFile("file.txt")

    val activityId = ActivityId("dummyProvider", "dummyActivity")
    val localHistory = LocalHistory.getInstance()

    setContent(file, "initial")
    val visibleEventLabel1 = "visible event label 1"
    localHistory.putEventLabel(project, visibleEventLabel1, activityId)
    val userLabel1 = "user label 1"
    localHistory.putUserLabel(project, userLabel1)
    localHistory.putEventLabel(project, "event label 1", activityId)
    val userLabel2 = "user label 2"
    localHistory.putUserLabel(project, userLabel2)

    setContent(file, "content1")
    val visibleEventLabel2 = "visible event label 2"
    localHistory.putEventLabel(project, visibleEventLabel2, activityId)
    localHistory.putEventLabel(project, "hidden event label 1", activityId)

    setContent(file, "content2")
    val userLabel3 = "user label 3"
    localHistory.putUserLabel(project, userLabel3)
    localHistory.putEventLabel(project, "hidden event label 2", activityId)
    localHistory.putEventLabel(project, "hidden event label 3", activityId)

    val provider = LocalHistoryActivityProvider(project, gateway)
    val scope = ActivityScope.fromFile(file)

    val activityList = provider.loadActivityList(scope, null)
    val labelNames = activityList.getLabelNameSet()

    TestCase.assertEquals(listOf(userLabel1, userLabel2, userLabel3, visibleEventLabel1, visibleEventLabel2), labelNames.toList().sorted())
  }

  fun `test directory`() {
    val directory = createDirectory("directory")
    val file = createChildData(directory, "file.txt")
    val otherFile = createFile("other.txt")

    setContent(file, "initial")
    val label = "label"
    vcs.putUserLabel(label, project.locationHash)

    setContent(otherFile, "initial")
    vcs.putUserLabel("hidden label", project.locationHash)

    val provider = LocalHistoryActivityProvider(project, gateway)
    val scope = ActivityScope.fromFile(directory)

    val directoryActivity = provider.loadActivityList(scope, null)
    TestCase.assertEquals(4, directoryActivity.items.size)
    TestCase.assertEquals(listOf("label",
                                 "Modify ${file.name}",
                                 "Create ${file.name}",
                                 "Create ${directory.name}"), directoryActivity.getNamesList())
  }

  fun `test recent activity`() {
    val file = createFile("file.txt")
    val otherFile = createFile("other.txt")

    for (c in listOf("initial", "current")) {
      setContent(file, c)
      setContent(otherFile, c)
    }

    val label1 = "label1"
    val label2 = "label2"
    val systemLabel = "system label"
    val otherProjectLabel = "other project label"

    vcs.putUserLabel(label1, project.locationHash)
    vcs.putUserLabel(label2, project.locationHash)
    vcs.putSystemLabel(systemLabel, project.locationHash, -1)
    vcs.putUserLabel(otherProjectLabel, "other-project")

    val provider = LocalHistoryActivityProvider(project, gateway)
    val scope = ActivityScope.Recent

    val activityList = provider.loadActivityList(scope, null)
    val labelNames = activityList.getLabelNameSet()

    TestCase.assertTrue(labelNames.containsAll(listOf(label1, label2)))
    TestCase.assertTrue(labelNames.intersect(listOf(systemLabel, otherProjectLabel)).isEmpty())

    for (f in listOf(file, otherFile)) {
      val fileActivity = provider.loadActivityList(scope, file.name)
      TestCase.assertEquals(3, fileActivity.items.size)

      val activityContents = getContentFor(file, fileActivity.getChangeSets())
      TestCase.assertEquals(listOf("initial", ""), activityContents)
    }
  }

  fun `test multiple files history`() {
    val file = createFile("file.txt")
    val otherFile = createFile("other.txt")
    val excludedFile = createFile("excluded.txt")

    for (c in listOf("initial", "current")) {
      setContent(file, c)
      setContent(otherFile, c)
      setContent(excludedFile, c)
    }

    val visibleLabel = "visible label"
    val hiddenLabel = "invisible label"
    vcs.putUserLabel(visibleLabel, project.locationHash)
    vcs.putUserLabel(hiddenLabel, project.locationHash)

    val provider = LocalHistoryActivityProvider(project, gateway)
    val scope = ActivityScope.fromFiles(listOf(file, otherFile))

    val activityList = provider.loadActivityList(scope, null)

    TestCase.assertEquals(listOf(visibleLabel,
                                 "Modify ${otherFile.name}",
                                 "Modify ${file.name}",
                                 "Modify ${otherFile.name}",
                                 "Modify ${file.name}",
                                 "Create ${otherFile.name}",
                                 "Create ${file.name}"), activityList.getNamesList())
  }

  fun `test parent directory and child file history`() {
    val file = createFile("file.txt")
    val directory = createDirectory("directory")

    setContent(file, "initial")
    setContent(file, "content")

    // Starting an action to set a normal name instead of WriteCommandAction.getDefaultCommandName
    val moveActionName = "Move ${file.name}"
    val action = LocalHistory.getInstance().startAction(moveActionName)
    HeavyPlatformTestCase.move(file, directory)
    action.finish()

    setContent(file, "current")

    val provider = LocalHistoryActivityProvider(project, gateway)
    val scope = ActivityScope.fromFiles(listOf(file, directory))

    val activityList = provider.loadActivityList(scope, null)

    TestCase.assertEquals(listOf("Modify ${file.name}",
                                 moveActionName,
                                 "Modify ${file.name}",
                                 "Modify ${file.name}",
                                 "Create ${directory.name}",
                                 "Create ${file.name}"), activityList.getNamesList())
  }

  fun `test diff data`() {
    val file = createFile("file.txt")
    val directory = createDirectory("directory")
    val innerDirectory = createChildDirectory(directory, "innerDirectory")

    setContent(file, "initial")
    setContent(file, "content")

    val provider = LocalHistoryActivityProvider(project, gateway)
    val scope = ActivityScope.fromFiles(listOf(myRoot))

    val activityList = provider.loadActivityList(scope, null)

    TestCase.assertEquals(listOf("MODIFIED:${file.name}"), getDiffDataForSelection(provider, scope, activityList, 0, 1))
    TestCase.assertEquals(listOf("ADDED:${innerDirectory.name}"), getDiffDataForSelection(provider, scope, activityList, 1, 2))
    TestCase.assertEquals(listOf("ADDED:${directory.name}"), getDiffDataForSelection(provider, scope, activityList, 2, 3))
    TestCase.assertEquals(listOf("ADDED:${file.name}"), getDiffDataForSelection(provider, scope, activityList, 3, 4))
    TestCase.assertEquals(listOf("ADDED:${file.name}", "ADDED:${directory.name}", "ADDED:${innerDirectory.name}").sorted(),
                          getDiffDataForSelection(provider, scope, activityList, 0, 4))

    TestCase.assertEquals(listOf("MODIFIED:${file.name}"), getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithNext, 0))
    TestCase.assertEquals(listOf("MODIFIED:${file.name}"), getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithLocal, 0))

    TestCase.assertEquals(listOf("MODIFIED:${file.name}"), getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithNext, 1))
    TestCase.assertEquals(listOf("MODIFIED:${file.name}"), getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithLocal, 1))

    TestCase.assertEquals(listOf("ADDED:${innerDirectory.name}"), getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithNext, 2))
    TestCase.assertEquals(listOf("MODIFIED:${file.name}", "ADDED:${innerDirectory.name}").sorted(),
                          getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithLocal, 2))

    TestCase.assertEquals(listOf("ADDED:${directory.name}"), getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithNext, 3))
    TestCase.assertEquals(listOf("MODIFIED:${file.name}", "ADDED:${directory.name}", "ADDED:${innerDirectory.name}").sorted(),
                          getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithLocal, 3))

    TestCase.assertEquals(listOf("ADDED:${file.name}"), getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithNext, 4))
    TestCase.assertEquals(listOf("ADDED:${file.name}", "ADDED:${directory.name}", "ADDED:${innerDirectory.name}").sorted(),
                          getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithLocal, 4))
  }

  private fun getDiffDataForSelection(provider: LocalHistoryActivityProvider, scope: ActivityScope, activityList: ActivityData,
                                      diffMode: DirectoryDiffMode, index: Int): List<String> {
    return getDiffDataForSelection(provider, scope, activityList, diffMode, index, index)
  }

  private fun getDiffDataForSelection(provider: LocalHistoryActivityProvider, scope: ActivityScope, activityList: ActivityData,
                                      from: Int, to: Int): List<String> {
    return getDiffDataForSelection(provider, scope, activityList, DirectoryDiffMode.WithLocal, from, to)
  }

  private fun getDiffDataForSelection(provider: LocalHistoryActivityProvider, scope: ActivityScope, activityList: ActivityData,
                                      diffMode: DirectoryDiffMode, from: Int, to: Int): List<String> {
    val selection = ActivitySelection(listOf(from, to).distinct().map { activityList.items[it] }, activityList)
    return provider.loadDiffData(scope, selection, diffMode)!!.presentableChanges.map { "${it.fileStatus}:${it.filePath.name}" }.sorted()
  }

  private fun ActivityData.getLabelNameSet(): Set<String> {
    return items.mapNotNull { (it as? LabelActivityItem)?.name }.toSet()
  }

  private fun ActivityData.getNamesList(): List<String> {
    return items.mapNotNull { (it as? ChangeSetActivityItem)?.name }
  }
}
