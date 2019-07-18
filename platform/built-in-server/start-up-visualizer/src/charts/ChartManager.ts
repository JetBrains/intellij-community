// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {DataManager} from "@/state/DataManager"

export interface ChartManager {
  render(data: DataManager): void

  dispose(): void
}

export function configureCommonChartSettings(chart: am4charts.XYChart) {
  chart.mouseWheelBehavior = "zoomX"
  chart.scrollbarX = new am4core.Scrollbar()

  configureCursor(chart)
}

export function configureCursor(chart: am4charts.XYChart) {
  const cursor = new am4charts.XYCursor()
  cursor.lineY.disabled = true
  cursor.lineX.disabled = true
  cursor.behavior = "zoomXY"
  chart.cursor = cursor
}

export abstract class BaseChartManager<T extends am4charts.Chart> implements ChartManager {
  protected constructor(protected readonly chart: T) {
    const exportMenu = new am4core.ExportMenu()
    const topItems = exportMenu.items[0].menu!!
    for (let i = topItems.length - 1; i >= 0; i--) {
      const chartElement = topItems[i]
      if (chartElement.label == "Data") {
        topItems.splice(i, 1)
      }
      else if (chartElement.label == "Image") {
        // remove PDF
        const length = chartElement.menu!!.length
        if (chartElement.menu!![length - 1].label == "PDF")
        chartElement.menu!!.length = length - 1
      }
    }
    chart.exporting.menu = exportMenu
  }

  abstract render(data: DataManager): void

  /** @override */
  dispose(): void {
    this.chart.dispose()
  }
}

export abstract class XYChartManager extends BaseChartManager<am4charts.XYChart> {
  protected constructor(container: HTMLElement, _childHot: __WebpackModuleApi.Hot | null | undefined) {
    super(am4core.create(container, am4charts.XYChart))

    configureCommonChartSettings(this.chart)
  }

  abstract render(data: DataManager): void

  protected configureRangeMarker(range: am4charts.AxisDataItem, label: string, yOffset = 0): void {
    range.label.inside = true
    range.label.horizontalCenter = "middle"
    range.label.fontSize = 12
    range.label.valign = "bottom"
    range.label.text = label
    range.grid.stroke = am4core.color("#000000")
    range.grid.strokeDasharray = "2,2"
    range.grid.strokeOpacity = 1

    range.label.adapter.add("dy", (_y, _target) => {
      return -this.chart.yAxes.getIndex(0)!!.pixelHeight + yOffset
    })
    range.label.adapter.add("x", (x, _target) => {
      const rangePoint = range.point
      return rangePoint == null ? x : rangePoint.x
    })
  }
}
