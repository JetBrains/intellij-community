// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {disableGridButKeepBorderLines, TimeLineItem, transformToTimeLineItems} from "./timeLineChartHelper"
import {XYChartManager} from "@/charts/ChartManager"
import {DataManager} from "@/state/DataManager"
import {InputData, Item} from "@/state/data"

export class TimelineChartManager extends XYChartManager {
  private maxRowIndex = 0

  private readonly statsLabel: am4core.Label

  constructor(container: HTMLElement) {
    super(container, module.hot)

    this.configureDurationAxis()
    const levelAxis = this.configureLevelAxis()
    this.configureSeries()
    this.addHeightAdjuster(levelAxis)

    this.statsLabel = this.chart.createChild(am4core.Label)
    this.statsLabel.selectable = true
    // cannot be placed on chart because overlaps data
    // this.statLabel.isMeasured = false
    // this.statLabel.x = 5
    // this.statLabel.y = 40
  }

  private configureLevelAxis() {
    const levelAxis = this.chart.yAxes.push(new am4charts.CategoryAxis())
    levelAxis.dataFields.category = "rowIndex"
    levelAxis.renderer.grid.template.location = 0
    levelAxis.renderer.minGridDistance = 1
    disableGridButKeepBorderLines(levelAxis)
    levelAxis.renderer.labels.template.disabled = true
    // level is is internal property - not interested for user
    levelAxis.cursorTooltipEnabled = false
    return levelAxis
  }

  private configureDurationAxis() {
    const durationAxis = this.chart.xAxes.push(new am4charts.DurationAxis())
    durationAxis.durationFormatter.baseUnit = "millisecond"
    durationAxis.durationFormatter.durationFormat = "S"
    durationAxis.min = 0
    durationAxis.strictMinMax = true

    // cursor tooltip is distracting
    durationAxis.cursorTooltipEnabled = false
  }

  private configureSeries() {
    const series = this.chart.series.push(new am4charts.ColumnSeries())
    // series.columns.template.width = am4core.percent(80)
    // https://github.com/amcharts/amcharts4/issues/989#issuecomment-467862120
    series.columns.template.tooltipText = "{name}: {duration}\nlevel: {level}\nrange: {start}-{end}\nthread: {thread}"
    series.columns.template.adapter.add("tooltipText", (value, target, _key) => {
      const dataItem = target.dataItem
      const index = dataItem == null ? -1 : dataItem.index
      const data = this.chart.data
      const item = index >= 0 && index < data.length ? data[index] as TimeLineItem : null
      if (item == null || item.description == null || item.description.length === 0) {
        return value
      }
      else {
        return `${value}\n{description}`
      }
    })
    series.dataFields.openDateX = "start"
    series.dataFields.openValueX = "start"
    series.dataFields.dateX = "end"
    series.dataFields.valueX = "end"
    series.dataFields.categoryY = "rowIndex"

    series.cursorHoverEnabled = false

    series.columns.template.propertyFields.fill = "color"
    series.columns.template.propertyFields.stroke = "color"

    const valueLabel = series.bullets.push(new am4charts.LabelBullet())
    valueLabel.label.text = "{name}"
    valueLabel.label.truncate = false
    valueLabel.label.hideOversized = false
    valueLabel.label.horizontalCenter = "left"
    valueLabel.locationX = 1
    // https://github.com/amcharts/amcharts4/issues/668#issuecomment-446655416
    valueLabel.interactionsEnabled = false
  }

  private addHeightAdjuster(levelAxis: am4charts.Axis) {
    // https://www.amcharts.com/docs/v4/tutorials/auto-adjusting-chart-height-based-on-a-number-of-data-items/
    // noinspection SpellCheckingInspection
    this.chart.events.on("datavalidated", () => {
      const chart = this.chart
      const targetHeight = chart.pixelHeight + ((this.maxRowIndex + 1) * 30 - levelAxis.pixelHeight)
      chart.svgContainer!!.htmlElement.style.height = targetHeight + "px"
    })
  }

  render(data: DataManager) {
    const originalItems = data.data.items || []

    this.setStatsLabel(data)
    this.chart.data = this.transformIjData(originalItems)

    const durationAxis = this.chart.xAxes.getIndex(0) as am4charts.DurationAxis
    durationAxis.max = Math.max(data.data.totalDurationComputed, data.data.totalDurationActual)

    this.computeRangeMarkers(data.data)
  }

  private computeRangeMarkers(data: InputData) {
    const nameAxis = this.chart.xAxes.getIndex(0) as am4charts.DurationAxis
    nameAxis.axisRanges.clear()

    const guides: Array<TimeLineGuide> = []
    for (const item of data.prepareAppInitActivities) {
      if (item.name === "splash initialization") {
        guides.push({label: "splash", value: item.start})
      }
    }

    if (guides.length === 0) {
      return
    }

    for (const guide of guides) {
      const range = nameAxis.axisRanges.create()
      this.configureRangeMarker(range, guide.label, 10 /* empirical value, not clear for now how to compute programmatically */)
      range.value = guide.value
    }
  }

  private setStatsLabel(data: DataManager) {
    if (!data.isStatSupported) {
      this.statsLabel.html = ""
      return
    }

    const stats = data.data.stats
    const itemStats = data.itemStats
    const statsLabelData = [
      "Plugin count", stats.plugin, "",
      "Component count", stats.component.app + stats.component.project + stats.component.module, `(${itemStats.reportedComponentCount} of them took more than 10ms)`,
      "Service count", stats.service.app + stats.service.project + stats.service.module, `(${itemStats.reportedServiceCount} created and each took more than 10ms)`,
    ]

    let result = "<table>"
    for (let i = 0; i < statsLabelData.length; i += 3) {
      result += `<tr><td>${statsLabelData[i]}:</td><td style="text-align: right">${statsLabelData[i + 1]}</td>`
      result += `<td>${statsLabelData[i + 2]}</td>`
      result += `</tr>`
    }
    result += "</table>"

    this.statsLabel.html = result
  }

  private transformIjData(originalItems: Array<Item>): Array<any> {
    const colorSet = new am4core.ColorSet()
    const items = transformToTimeLineItems(originalItems)
    // we cannot use actual level as row index because in this case labels will be overlapped, so,
    // row index simply incremented till empirical limit.
    let rowIndex = 0
    this.maxRowIndex = 0
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      if (rowIndex > 5 && item.level === 0) {
        rowIndex = 0
      }
      else if (rowIndex > this.maxRowIndex) {
        this.maxRowIndex = rowIndex
      }

      item.rowIndex = rowIndex++,
      item.color = colorSet.getIndex(item.colorIndex)
    }

    items.sort((a, b) => a.rowIndex - b.rowIndex)
    return items
  }
}

interface TimeLineGuide {
  readonly value: number
  readonly label: string
}