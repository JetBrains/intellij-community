// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {DataManager} from "@/state/DataManager"

export interface ChartManager {
  render(data: DataManager): void

  dispose(): void
}

function configureCommonChartSettings(chart: am4charts.XYChart) {
  chart.mouseWheelBehavior = "zoomX"
  chart.scrollbarX = new am4core.Scrollbar()

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
}
