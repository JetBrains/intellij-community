// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4core from "@amcharts/amcharts4/core"
import {DataManager} from "@/state/DataManager"
import {ClassItem} from "@/charts/ActivityChartManager"
import {transformTraceEventToClassItem} from "@/charts/ServiceChartManager"
import {BaseTimeLineChartManager} from "@/timeline/BaseTimeLineChartManager"
import {CompleteTraceEvent} from "@/state/data"

export class ServiceTimeLineChartManager extends BaseTimeLineChartManager {
  constructor(container: HTMLElement) {
    super(container)

    this.configureDurationAxis()
    this.configureSeries("{shortName}")
  }


  protected getToolTipText(): string {
    let result = "{name}: {ownDuration} ms\nrange: {start}-{end}\nthread: {thread}" + "\nplugin: {plugin}" + "\ntotal duration: {totalDuration} ms"
    return result
  }

  render(dataManager: DataManager) {
    this.guides.length = 0
    this.chart.data = this.transformIjData(dataManager)
    this.computeRangeMarkers(dataManager)
  }

  private transformIjData(dataManager: DataManager): Array<any> {
    const colorSet = new am4core.ColorSet()

    let items: Array<ClassItem & CompleteTraceEvent> = []
    transformTraceEventToClassItem(dataManager.serviceEvents, null, items, false)
    this.maxRowIndex = 0

    return this.transformParallelToTimeLineItems(items, colorSet, 0)
  }
}