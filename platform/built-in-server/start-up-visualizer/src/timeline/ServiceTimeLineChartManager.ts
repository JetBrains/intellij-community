// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {TimeLineGuide} from "./timeLineChartHelper"
import {DataManager} from "@/state/DataManager"
import {ClassItem} from "@/charts/ActivityChartManager"
import {transformTraceEventToClassItem} from "@/charts/ServiceChartManager"
import {BaseTimeLineChartManager} from "@/timeline/BaseTimeLineChartManager"
import {CompleteTraceEvent} from "@/state/data"

export class ServiceTimeLineChartManager extends BaseTimeLineChartManager {
  private threadRowFirstIndex = 0

  constructor(container: HTMLElement) {
    super(container)

    this.configureDurationAxis()
    this.configureSeries("{shortName}")
  }


  protected getToolTipText(): string {
    let result = "{name}: {ownDuration} ms\nrange: {start}-{end}\nthread: {thread}" + "\nplugin: {plugin}" + "\ntotal duration: {totalDuration} ms"
    return result
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

  render(dataManager: DataManager) {
    this.chart.data = this.transformIjData(dataManager)
    this.computeRangeMarkers(dataManager)
  }

  private computeRangeMarkers(dataManager: DataManager) {
    const nameAxis = this.chart.xAxes.getIndex(0) as am4charts.DurationAxis
    nameAxis.axisRanges.clear()

    const guides: Array<TimeLineGuide> = []

    if (dataManager.isInstantEventProvided) {
      for (const item of dataManager.data.traceEvents) {
        // reduce unneeded guides - do not report "app component registered / loaded" (it is clear)
        if (item.ph === "i" && !item.name.startsWith("app component ") && !item.name.endsWith(" initialized")) {
          guides.push({label: item.name, value: Math.round(item.ts / 1000)})
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

  private transformIjData(dataManager: DataManager): Array<any> {
    const colorSet = new am4core.ColorSet()

    let items: Array<ClassItem & CompleteTraceEvent> = []
    transformTraceEventToClassItem(dataManager.serviceEvents, null, items, false)
    this.maxRowIndex = 0

    return this.transformParallelToTimeLineItems(items, colorSet, 0)
  }
}