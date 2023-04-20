// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.setesting

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.util.ui.AbstractLayoutManager
import com.intellij.util.ui.GridBag
import java.awt.*
import javax.swing.*

internal class SETestingPanel(contributors: List<SearchEverywhereContributor<*>>) : JBSplitter(false), Disposable {

  @Suppress("HardCodedStringLiteral")
  private val searchTextField = JTextField("test")
  private val elementsLimitField = JSpinner(SpinnerNumberModel(50, 1, 1000, 1))
  private val contributorsList = createContributorsList(contributors)
  private val searchButton = createSearchButton()
  private val groupingIntervalSpinner = createGroupingIntervalSpinner()
  private val resultsPanel = JPanel(MyLayoutManager())
  private val graphs = mutableListOf<ResultsGraph>()

  private var currentIndicator: ProgressIndicator? = null

  init {
    firstComponent = createLeftPanel()
    secondComponent = createRightPanel()
    proportion = 0.3f
  }

  override fun dispose() {
    currentIndicator?.let { if (!it.isCanceled) it.cancel() }
  }

  private fun createLeftPanel(): JPanel {
    val res = JPanel(GridBagLayout())
    val gbc = GridBag().nextLine()

    res.add(JLabel(IdeBundle.message("searcheverywhere.test.dialog.search.field")), gbc.next())
    res.add(searchTextField, gbc.next().weightx(1.0).fillCell())
    res.add(JLabel(IdeBundle.message("searcheverywhere.test.dialog.elements.limit")), gbc.next())
    res.add(elementsLimitField, gbc.next().weightx(0.0))

    res.add(JScrollPane(contributorsList), gbc.nextLine().weighty(1.0).coverLine().fillCell().anchor(GridBagConstraints.CENTER))

    res.add(searchButton, gbc.nextLine().coverLine().fillCellNone().anchor(GridBagConstraints.CENTER))

    return res
  }

  private fun createRightPanel(): JPanel {
    val res = JPanel(BorderLayout())
    val northPanel = JPanel(FlowLayout(FlowLayout.LEFT))

    northPanel.add(JLabel(IdeBundle.message("searcheverywhere.test.dialog.grouping.interval")))
    northPanel.add(groupingIntervalSpinner)
    res.add(northPanel, BorderLayout.NORTH)
    res.add(JScrollPane(resultsPanel), BorderLayout.CENTER)

    return res
  }

  private fun createGroupingIntervalSpinner(): JSpinner {
    val res = JSpinner(SpinnerNumberModel(100, 10, 1000, 10))
    res.addChangeListener { graphs.forEach { it.setGroupingInterval(res.value as Int) } }
    return res
  }

  private fun createSearchButton(): JButton {
    val button = JButton(IdeBundle.message("searcheverywhere.test.dialog.search.button"))
    button.addActionListener { runTestTask() }
    return button
  }

  private fun runTestTask() {
    searchButton.icon = AnimatedIcon.Default.INSTANCE

    val tester = SETester(contributorsList.selectedValuesList, searchTextField.text, elementsLimitField.value as Int)
    val indicator = ProgressIndicatorBase()
    indicator.start()

    currentIndicator?.let { if (!it.isCanceled) it.cancel() }
    currentIndicator = indicator

    val task = Runnable {
      val results = tester.test(indicator)
      SwingUtilities.invokeLater {
        fillResults(results)
        searchButton.icon = null
        indicator.stop()
      }
    }
    ApplicationManager.getApplication().executeOnPooledThread(task)
  }

  private fun fillResults(results: Map<SearchEverywhereContributor<*>, List<Long>>) {
    val maxTime = results.values.flatten().maxOrNull() ?: return
    graphs.clear()
    graphs.addAll(results.map { (c, t) ->
      ResultsGraph(c.groupName, t, maxTime).apply { setGroupingInterval(groupingIntervalSpinner.value as Int) }
    })
    resultsPanel.removeAll()
    graphs.forEach { resultsPanel.add(it) }
  }

  private fun createContributorsList(contributors: List<SearchEverywhereContributor<*>>): JList<SearchEverywhereContributor<*>> {
    val list = JBList(MyListModel(contributors))
    list.setCellRenderer { _, value, _, _, _ -> JLabel(value.groupName) }
    return list
  }
}

class MyLayoutManager: AbstractLayoutManager() {

  override fun preferredLayoutSize(parent: Container): Dimension {
    val size = parent.componentCount
    if (size <= 0) return Dimension(0, 0)

    return parent.components[0].preferredSize

    //val columns = when (size) {
    //  in 1..3 -> 1
    //  in 4..8 -> 2
    //  else -> 3
    //}
    //var rows = size / columns
    //if (size % columns > 0) rows++
    //
    //val tileSize = parent.components[0].preferredSize
    //return Dimension(tileSize.width * columns, tileSize.height * rows)
  }

  override fun layoutContainer(parent: Container) {
    val size = parent.componentCount
    if (size <= 0) return

    val tilePrefSize = parent.components[0].preferredSize
    val columnsCount = parent.width / tilePrefSize.width
    var rowsCount = size / columnsCount
    if (size % columnsCount > 0) rowsCount++

    val tileSize = Dimension(parent.width / columnsCount, parent.height / rowsCount)
    val currentStartPoint = Point(0, 0)
    var currentColumn = 0
    for (comp in parent.components) {
      comp.bounds = Rectangle(currentStartPoint, tileSize)
      if (currentColumn >= columnsCount - 1) {
        currentStartPoint.x = 0
        currentStartPoint.y += tileSize.height
        currentColumn = 0
      }
      else {
        currentStartPoint.x += tileSize.width
        currentColumn++
      }
    }
  }
}

private class MyListModel(val contributors: List<SearchEverywhereContributor<*>>): AbstractListModel<SearchEverywhereContributor<*>>() {
  override fun getSize() = contributors.size

  override fun getElementAt(index: Int) = contributors[index]
}