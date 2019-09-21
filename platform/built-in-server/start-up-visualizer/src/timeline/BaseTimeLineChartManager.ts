// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {XYChartManager} from "@/charts/ChartManager"
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {TimeLineGuide, TimeLineItem, transformToTimeLineItems} from "@/timeline/timeLineChartHelper"
import {Item} from "@/state/data"
import {DataManager, SERVICE_WAITING} from "@/state/DataManager"

export const LABEL_DURATION_THRESHOLD = 20

// natural sort of alphanumerical strings
const collator = new Intl.Collator(undefined, {numeric: true, sensitivity: "base"})

export abstract class BaseTimeLineChartManager extends XYChartManager {
  protected maxRowIndex = 0
  protected threadRowFirstIndex = 0
  private readonly threadFirstRowIndexSet = new Map<number, am4core.Color>()

  protected readonly guides: Array<TimeLineGuide> = []

  protected constructor(container: HTMLElement) {
    super(container)

    const levelAxis = this.configureLevelAxis()
    this.addHeightAdjuster(levelAxis)
  }

  private addHeightAdjuster(levelAxis: am4charts.Axis) {
    // https://www.amcharts.com/docs/v4/tutorials/auto-adjusting-chart-height-based-on-a-number-of-data-items/
    // noinspection SpellCheckingInspection
    this.chart.events.on("datavalidated", () => {
      const chart = this.chart
      const targetHeight = chart.pixelHeight + ((this.maxRowIndex + 1) * 20 - levelAxis.pixelHeight)
      chart.svgContainer!!.htmlElement.style.height = targetHeight + "px"
    })
  }

  protected configureLevelAxis(): am4charts.CategoryAxis {
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

    levelAxis.renderer.labels.template.selectable = true
    levelAxis.renderer.labels.template.fill = am4core.color("#ff0000")
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
    levelAxis.renderer.labels.template.adapter.add("fill", (value, target, _key) => {
      const dataItem = target.dataItem
      const index = dataItem == null ? -1 : dataItem.index
      return index >= this.threadRowFirstIndex ? this.threadFirstRowIndexSet.get(index) : value
    })
    // level is an internal property - not interested for user
    levelAxis.cursorTooltipEnabled = false
    return levelAxis
  }

  protected configureSeries(valueLabelText: string) {
    const series = this.chart.series.push(new am4charts.ColumnSeries())
    // series.columns.template.width = am4core.percent(80)
    // https://github.com/amcharts/amcharts4/issues/989#issuecomment-467862120
    series.columns.template.tooltipText = this.getToolTipText()
    // series.columns.template.tooltipText += "\n" + "rowIndex: {rowIndex}"
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
    valueLabel.label.text = valueLabelText
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

  protected getToolTipText() {
    return "{name}: {duration}\nlevel: {level}\nrange: {start}-{end}\nthread: {thread}"
  }

  private getItemByColumn(column: am4core.Sprite): TimeLineItem | null {
    const dataItem = column.dataItem
    const index = dataItem == null ? -1 : dataItem.index
    const data = this.chart.data
    return index >= 0 && index < data.length ? data[index] as TimeLineItem : null
  }

  protected configureDurationAxis(): am4charts.DurationAxis {
    const durationAxis = this.chart.xAxes.push(new am4charts.DurationAxis())
    durationAxis.durationFormatter.baseUnit = "millisecond"
    durationAxis.durationFormatter.durationFormat = "S"

    // cursor tooltip is distracting
    durationAxis.cursorTooltipEnabled = false
    return durationAxis
  }

  protected transformParallelToTimeLineItems(originalItems: Array<Item>, colorSet: am4core.ColorSet, threadRowFirstIndex: number): Array<TimeLineItem> {
    // events must be already sorted by time
    const items = transformToTimeLineItems(originalItems)
    items.sort((a, b) => collator.compare(a.thread, b.thread))

    let rowIndex = threadRowFirstIndex
    this.threadFirstRowIndexSet.clear()

    let lastAllocatedColorIndex = 0

    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      if (i === 0) {
        this.threadFirstRowIndexSet.set(rowIndex, colorSet.getIndex(lastAllocatedColorIndex))
      }
      else {
        if (items[i - 1].thread !== item.thread) {
          lastAllocatedColorIndex++
          rowIndex++

          this.threadFirstRowIndexSet.set(rowIndex, colorSet.getIndex(lastAllocatedColorIndex))
        }
        else {
          // for parallel activities ladder is used only to avoid text overlapping,
          // and two adjacent items are rendered in the same row if time gap between is greater than 100ms
          if (item.duration >= LABEL_DURATION_THRESHOLD && (item.start - items[i - 1].end) < 100) {
            rowIndex++
          }
        }
      }

      if ((item as any).cat === SERVICE_WAITING) {
        // highlight as error
        item.color = am4core.color("#ff0000")
      }
      else {
        item.color = colorSet.getIndex(lastAllocatedColorIndex)
      }
      item.rowIndex = rowIndex

      if (rowIndex > this.maxRowIndex) {
        this.maxRowIndex = rowIndex
      }
    }
    return items
  }

  protected computeRangeMarkers(dataManager: DataManager) {
    this.collectGuides(dataManager, this.guides)
    this.applyGuides()
  }

  // final
  protected applyGuides() {
    const nameAxis = this.chart.xAxes.getIndex(0) as am4charts.DurationAxis
    nameAxis.axisRanges.clear()

    const guides = this.guides
    if (guides.length === 0) {
      return
    }

    for (const guide of guides) {
      const range = nameAxis.axisRanges.create()
      this.configureRangeMarker(range, guide.label, guide.color, 10 /* empirical value, not clear for now how to compute programmatically */)
      range.value = guide.value
      if (guide.endValue != null) {
        range.endValue = guide.endValue
      }
    }
  }

  protected collectGuides(dataManager: DataManager, guides: Array<TimeLineGuide>) {
    const isInstantEventProvided = dataManager.isInstantEventProvided
    if (isInstantEventProvided) {
      for (const item of dataManager.data.traceEvents) {
        // reduce unneeded guides - do not report "app component registered / loaded" (it is clear)
        if (item.ph === "i" && !item.name.startsWith("app component ") && !item.name.endsWith(" initialized")) {
          guides.push({label: item.name, value: Math.round(item.ts / 1000), color: this.blackColor})
        }
      }
    }

    for (const item of dataManager.data.prepareAppInitActivities) {
      if (!isInstantEventProvided && item.name === "splash initialization") {
        guides.push({label: "splash", value: item.start, color: this.blackColor})
      }
    }
  }
}
