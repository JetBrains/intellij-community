// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.integration.IntegrationTestCase
import junit.framework.TestCase

class LocalHistoryActivityProviderTest : IntegrationTestCase() {
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
                                 "Changes in file.txt",
                                 "Changes in file.txt",
                                 "Changes in directory"), directoryActivity.getNamesList())
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

  private fun ActivityData.getLabelNameSet(): Set<String> {
    return items.mapNotNull { (it as? LabelActivityItem)?.name }.toSet()
  }

  private fun ActivityData.getNamesList(): List<String> {
    return items.mapNotNull { (it as? ChangeSetActivityItem)?.name }
  }
}