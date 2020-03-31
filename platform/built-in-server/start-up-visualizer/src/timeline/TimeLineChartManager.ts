// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {TimeLineItem, transformToTimeLineItems} from "./timeLineChartHelper"
import {DataManager} from "@/state/DataManager"
import {InputData} from "@/state/data"
import {BaseTimeLineChartManager, LABEL_DURATION_THRESHOLD} from "@/timeline/BaseTimeLineChartManager"

export class TimelineChartManager extends BaseTimeLineChartManager {
  private readonly statsLabel: am4core.Label

  constructor(container: HTMLElement) {
    super(container)

    this.configureDurationAxis()
    this.configureSeries("{name}")

    this.statsLabel = this.chart.createChild(am4core.Label)
    this.statsLabel.selectable = true
  }

  protected configureDurationAxis(): am4charts.DurationAxis {
    const durationAxis = super.configureDurationAxis()
    durationAxis.min = 0
    // no need to add extra space on the sides
    durationAxis.strictMinMax = true
    return durationAxis
  }

  render(data: DataManager) {
    this.guides.length = 0

    this.setStatsLabel(data)
    this.chart.data = this.transformIjData(data.data)

    const durationAxis = this.chart.xAxes.getIndex(0) as am4charts.DurationAxis
    durationAxis.max = Math.max(data.data.totalDurationComputed, data.data.totalDurationActual)

    this.computeRangeMarkers(data)
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

    const parallelItems = this.transformParallelToTimeLineItems(data.prepareAppInitActivities, colorSet, this.threadRowFirstIndex)
    this.addGuides(parallelItems)
    return items.concat(parallelItems)
  }

  private addGuides(parallelItems: Array<TimeLineItem>) {
    const guides = this.guides
    for (const item of parallelItems) {
      const isAsyncServicePreloading = item.name.endsWith(" async preloading")
      if (isAsyncServicePreloading || item.name.endsWith(" sync preloading")) {
        guides.push({label: "", value: item.end, color: item.color})
      }

      if (isAsyncServicePreloading) {
        guides.push({label: "", value: item.start, color: item.color})
      }
    }
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