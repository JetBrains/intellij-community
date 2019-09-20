// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {XYChartManager} from "@/charts/ChartManager"
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {TimeLineItem, transformToTimeLineItems} from "@/timeline/timeLineChartHelper"
import {Item} from "@/state/data"
import {SERVICE_WAITING} from "@/state/DataManager"

export const LABEL_DURATION_THRESHOLD = 20

// natural sort of alphanumerical strings
const collator = new Intl.Collator(undefined, {numeric: true, sensitivity: "base"})

export abstract class BaseTimeLineChartManager extends XYChartManager {
  protected maxRowIndex = 0
  protected readonly threadFirstRowIndexSet = new Set<number>()

  constructor(container: HTMLElement) {
    super(container)

    const levelAxis = this.configureLevelAxis()
    this.addHeightAdjuster(levelAxis)
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

  protected abstract configureLevelAxis(): am4charts.CategoryAxis

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
        this.threadFirstRowIndexSet.add(rowIndex)
      }
      else {
        if (items[i - 1].thread !== item.thread) {
          lastAllocatedColorIndex++
          rowIndex++

          this.threadFirstRowIndexSet.add(rowIndex)
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
}
