// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {TimeLineGuide, TimeLineItem, transformToTimeLineItems} from "./timeLineChartHelper"
import {DataManager} from "@/state/DataManager"
import {InputData} from "@/state/data"
import {BaseTimeLineChartManager, LABEL_DURATION_THRESHOLD} from "@/timeline/BaseTimeLineChartManager"

export class TimelineChartManager extends BaseTimeLineChartManager {
  private threadRowFirstIndex = 0

  private readonly statsLabel: am4core.Label

  constructor(container: HTMLElement) {
    super(container)

    this.configureDurationAxis()
    this.configureSeries("{name}")

    this.statsLabel = this.chart.createChild(am4core.Label)
    this.statsLabel.selectable = true
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

  protected configureDurationAxis(): am4charts.DurationAxis {
    const durationAxis = super.configureDurationAxis()
    durationAxis.min = 0
    // no need to add extra space on the sides
    durationAxis.strictMinMax = true
    return durationAxis
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
      "Component count", stats.component.app + stats.component.project + stats.component.module, `(${itemStats.reportedComponentCount} created)`,
      "Service count", stats.service.app + stats.service.project + stats.service.module, `(${itemStats.reportedServiceCount} created)`,
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
    this.computeRowIndexForMainActivities(items, colorSet)

    items.sort((a, b) => a.rowIndex - b.rowIndex)

    this.threadRowFirstIndex = this.maxRowIndex + 1

    return items.concat(this.transformParallelToTimeLineItems(data.prepareAppInitActivities, colorSet, this.threadRowFirstIndex))
  }

  private computeRowIndexForMainActivities(items: Array<TimeLineItem>, colorSet: am4core.ColorSet) {
    // we cannot use actual level as row index because in this case labels will be overlapped, so,
    // row index simply incremented till empirical limit
    const offset = this.maxRowIndex
    let rowIndex = offset
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      if (rowIndex > 5 && item.level === 0) {
        rowIndex = offset
      }
      else {
        // do not append label-less and insignificant activities to significant ones to avoid confusion
        // (all child activities have the same color)
        if (i !== 0 && (item.duration >= LABEL_DURATION_THRESHOLD || (items[i - 1].duration >= LABEL_DURATION_THRESHOLD))) {
          rowIndex++
        }
      }

      item.rowIndex = rowIndex
      item.color = colorSet.getIndex(item.colorIndex)

      if (rowIndex > this.maxRowIndex) {
        this.maxRowIndex = rowIndex
      }
    }
  }
}