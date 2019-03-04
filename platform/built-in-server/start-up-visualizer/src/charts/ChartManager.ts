// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {DataManager} from "@/state/DataManager"

export interface ChartManager {
  render(data: DataManager): void
}

function configureCommonChartSettings(chart: am4charts.XYChart) {
  chart.exporting.menu = new am4core.ExportMenu()
  chart.mouseWheelBehavior = "zoomX"
  chart.scrollbarX = new am4core.Scrollbar()

  const cursor = new am4charts.XYCursor()
  cursor.lineY.disabled = true
  cursor.lineX.disabled = true
  // todo y axis for ItemChart doesn't work as expected (not scaled according to current data) because of 2 series for axis (and so, no chart data is set)
  cursor.behavior = "zoomXY"
  chart.cursor = cursor
}

export abstract class XYChartManager implements ChartManager {
  protected readonly chart: am4charts.XYChart

  protected constructor(container: HTMLElement, _childHot: __WebpackModuleApi.Hot | null | undefined) {
    this.chart = am4core.create(container, am4charts.XYChart)
    configureCommonChartSettings(this.chart)

    // this.addDisposeHandler(childHot)
  }

  dispose(): void {
    this.chart.dispose()
  }

  // module.hot must be passed here explicitly, because module in this context related only to this module
  // private addDisposeHandler(hot: __WebpackModuleApi.Hot | null | undefined) {
  //   if (hot == null) {
  //     return
  //   }
  //
  //   hot.dispose(_data => {
  //     const chart = this.chart
  //     if (chart == null) {
  //       return
  //     }
  //
  //     (this as any).chart = null
  //     chart.dispose()
  //     // const exportingMenu = chart.exporting.menu
  //     // if (exportingMenu != null) {
  //     //   exportingMenu.dispose()
  //     // }
  //   })
  // }

  abstract render(data: DataManager): void
}
