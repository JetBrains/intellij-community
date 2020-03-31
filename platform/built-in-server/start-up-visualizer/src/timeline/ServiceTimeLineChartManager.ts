// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4core from "@amcharts/amcharts4/core"
import {DataManager} from "@/state/DataManager"
import {ClassItem} from "@/charts/ActivityChartManager"
import {transformTraceEventToClassItem} from "@/charts/ServiceChartManager"
import {BaseTimeLineChartManager} from "@/timeline/BaseTimeLineChartManager"
import {CompleteTraceEvent} from "@/state/data"
import * as am4charts from "@amcharts/amcharts4/charts"
import {TimeLineGuide} from "@/timeline/timeLineChartHelper"
import {LegendItem} from "@/charts/ChartManager"

export class ServiceTimeLineChartManager extends BaseTimeLineChartManager {
  private readonly rangeLegend: am4charts.Legend

  constructor(container: HTMLElement) {
    super(container)

    this.configureDurationAxis()
    this.configureSeries("{shortName}")

    const rangeLegend = new am4charts.Legend()
    this.rangeLegend = rangeLegend
    rangeLegend.parent = this.chart.chartContainer
    rangeLegend.tooltipText = "Range Legend"
    rangeLegend.interactionsEnabled = false
    // make clear that it is not item, but range legend
    rangeLegend.markers.template.width = 4
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

  protected collectGuides(dataManager: DataManager, guides: Array<TimeLineGuide>) {
    const colorSet = new am4core.ColorSet()
    colorSet.step = 2
    for (const item of dataManager.data.prepareAppInitActivities) {
      if (item.name.endsWith(" async preloading") || item.name.endsWith(" sync preloading")) {
        const color = colorSet.next()
        guides.push({label: item.name, value: item.start, endValue: item.end, color: color})
      }
    }

    this.rangeLegend.data = this.guides.map(it => {
      const result: LegendItem = {name: it.label, fill: it.color}
      return result
    })
  }

  protected configureRangeMarker(range: am4charts.DurationAxisDataItem, _label: string, color: am4core.Color, _yOffset = 0): void {
    const axisFill = range.axisFill
    axisFill.stroke = color
    axisFill.strokeDasharray = "2,2"
    axisFill.strokeOpacity = 1

    // HTML WhiteSmoke
    axisFill.fill = am4core.color("#F5F5F5")
    axisFill.fillOpacity = 0.3
  }
}