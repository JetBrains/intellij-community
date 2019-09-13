// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {disableGridButKeepBorderLines, TimeLineItem, transformToTimeLineItems} from "./timeLineChartHelper"
import {XYChartManager} from "@/charts/ChartManager"
import {DataManager} from "@/state/DataManager"
import {InputData, Item} from "@/state/data"

const LABEL_DURATION_THRESHOLD = 20

// natural sort of alphanumerical strings
const collator = new Intl.Collator(undefined, {numeric: true, sensitivity: "base"})

export class TimelineChartManager extends XYChartManager {
  private maxRowIndex = 0
  private threadRowFirstIndex = 0

  private readonly statsLabel: am4core.Label
  private readonly threadFirstRowIndexSet = new Set<number>()

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

    levelAxis.renderer.grid.template.adapter.add("disabled", (_, target, _key) => {
      if (target.dataItem == null) {
        return false
      }

      const index = target.dataItem.index
      if (index === 0 || index === -1) {
        return false
      }
      if (index < this.threadRowFirstIndex) {
        return true
      }
      return !this.threadFirstRowIndexSet.has(index)
    })

    disableGridButKeepBorderLines
    // disableGridButKeepBorderLines(levelAxis)
    levelAxis.renderer.labels.template.selectable = true
    levelAxis.renderer.labels.template.adapter.add("text", (_value, target, _key) => {
      const dataItem = target.dataItem
      const index = dataItem == null ? -1 : dataItem.index
      if (index >= this.threadRowFirstIndex && this.threadFirstRowIndexSet.has(index)) {
        return "{thread}"
      }
      else {
        return ""
      }
    })
    // level is an internal property - not interested for user
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
    // series.columns.template.tooltipText += "\nrowIndex: {rowIndex}"
    series.columns.template.adapter.add("tooltipText", (value, target, _key) => {
      const item = this.getItemByColumn(target)
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
    valueLabel.label.adapter.add("text", (value, target, _key) => {
      const item = this.getItemByColumn(target)
      if (item != null && item.duration < LABEL_DURATION_THRESHOLD) {
        return ""
      }
      else {
        return value
      }
    })
    valueLabel.label.truncate = false
    valueLabel.label.hideOversized = false
    valueLabel.label.horizontalCenter = "left"
    valueLabel.locationX = 1
    // https://github.com/amcharts/amcharts4/issues/668#issuecomment-446655416
    valueLabel.interactionsEnabled = false
  }

  private getItemByColumn(column: am4core.Sprite): TimeLineItem | null {
    const dataItem = column.dataItem
    const index = dataItem == null ? -1 : dataItem.index
    const data = this.chart.data
    return index >= 0 && index < data.length ? data[index] as TimeLineItem : null
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
    this.setStatsLabel(data)
    this.chart.data = this.transformIjData(data.data)

    const durationAxis = this.chart.xAxes.getIndex(0) as am4charts.DurationAxis
    durationAxis.max = Math.max(data.data.totalDurationComputed, data.data.totalDurationActual)

    this.computeRangeMarkers(data)
  }

  private computeRangeMarkers(data: DataManager) {
    const nameAxis = this.chart.xAxes.getIndex(0) as am4charts.DurationAxis
    nameAxis.axisRanges.clear()

    const guides: Array<TimeLineGuide> = []

    if (data.isInstantEventProvided) {
      for (const item of data.data.traceEvents) {
        // reduce unneeded guides - do not report "app component registered / loaded" (it is clear)
        if (item.ph === "i" && !item.name.startsWith("app component ") && !item.name.endsWith(" initialized")) {
          guides.push({label: item.name, value: Math.round(item.ts / 1000)})
        }
      }
    }
    else {
      for (const item of data.data.prepareAppInitActivities) {
        if (item.name === "splash initialization") {
          guides.push({label: "splash", value: item.start})
        }
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

  private transformIjData(data: InputData): Array<any> {
    const colorSet = new am4core.ColorSet()
    const items = transformToTimeLineItems(data.items || [])
    this.maxRowIndex = 0
    this.computeRowIndex(items, colorSet)

    items.sort((a, b) => a.rowIndex - b.rowIndex)

    return items.concat(this.transformParallelToTimeLineItems(data.prepareAppInitActivities, colorSet))
  }

  private computeRowIndex(items: Array<TimeLineItem>, colorSet: am4core.ColorSet) {
    // we cannot use actual level as row index because in this case labels will be overlapped, so,
    // row index simply incremented till empirical limit
    const offset = this.maxRowIndex
    let rowIndex = offset
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      if (rowIndex > 5 && item.level === 0) {
        rowIndex = offset
      }
      else if (i !== 0 && item.duration >= LABEL_DURATION_THRESHOLD) {
        rowIndex++
      }

      item.rowIndex = rowIndex
      item.color = colorSet.getIndex(item.colorIndex)

      if (rowIndex > this.maxRowIndex) {
        this.maxRowIndex = rowIndex
      }
    }
  }

  private transformParallelToTimeLineItems(originalItems: Array<Item>, colorSet: am4core.ColorSet): Array<TimeLineItem> {
    const items = transformToTimeLineItems(originalItems)

    items.sort((a, b) => collator.compare(a.thread, b.thread))

    let lastOffset = this.maxRowIndex
    let rowIndex = this.maxRowIndex + 1
    this.threadFirstRowIndexSet.clear()
    this.threadRowFirstIndex = rowIndex

    let lastAllocatedColorIndex = 0

    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      if (i === 0) {
        this.threadFirstRowIndexSet.add(rowIndex)
      }
      else {
        if (items[i - 1].thread !== item.thread) {
          lastAllocatedColorIndex++
          rowIndex++
          lastOffset = rowIndex

          this.threadFirstRowIndexSet.add(rowIndex)
        }
        else {
          if (rowIndex > (lastOffset + 3) && item.level === 0) {
            // rowIndex = lastOffset
          }

          // for parallel activities ladder is used only to avoid text overlapping,
          // and two adjacent items are rendered in the same row if time gap between is greater than 100ms
          if (item.duration >= LABEL_DURATION_THRESHOLD && (item.start - items[i - 1].end) < 100) {
            rowIndex++
          }
        }
      }

      item.color = colorSet.getIndex(lastAllocatedColorIndex)
      item.rowIndex = rowIndex

      if (rowIndex > this.maxRowIndex) {
        this.maxRowIndex = rowIndex
      }
    }

    // console.log(this.threadRowFirstIndex)
    // console.log(Array.from(this.threadFirstRowIndexSet))

    return items
  }
}

interface TimeLineGuide {
  readonly value: number
  readonly label: string
}