// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {DataManager} from "./data"

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

  protected constructor(container: HTMLElement, childHot: __WebpackModuleApi.Hot | null | undefined) {
    this.chart = am4core.create(container, am4charts.XYChart)
    configureCommonChartSettings(this.chart)

    this.addDisposeHandler(childHot)

    if (module != null && module.hot != null) {
      this.addDisposeHandler(module.hot)

      let devState: DevState | null = null
      const handler = () => {
        const axis = this.chart.xAxes.getIndex(0)!!
        if (devState == null) {
          devState = {
            start: axis.start,
            end: axis.end,
          }
        }
        else {
          devState.start = axis.start
          devState.end = axis.end
        }

        sessionStorage.setItem("devState", JSON.stringify(devState))
      }
      setTimeout(() => {
        // noinspection SpellCheckingInspection
        this.chart.xAxes.getIndex(0)!!.events.on("startchanged", handler)
        // noinspection SpellCheckingInspection
        this.chart.xAxes.getIndex(0)!!.events.on("endchanged", handler)
      }, 1000)

      // module.hot.dispose(() => {
      //   if (devState == null) {
      //     sessionStorage.removeItem("devState")
      //   }
      //   else {
      //     sessionStorage.setItem("devState", JSON.stringify(devState))
      //   }
      // })

      const devStateRaw = sessionStorage.getItem("devState")
      if (devStateRaw != null) {
        this.chart.events.on("ready", () => {
          const devState = JSON.parse(devStateRaw)
          const axis = this.chart.xAxes.getIndex(0)!!
          axis.start = devState.start
          axis.end = devState.end
        })
      }
    }
  }

  // module.hot must be passed here explicitly, because module in this context related only to this module
  private addDisposeHandler(hot: __WebpackModuleApi.Hot | null | undefined) {
    if (hot == null) {
      return
    }

    hot.dispose(_data => {
      const chart = this.chart
      if (chart == null) {
        return
      }

      (this as any).chart = null
      chart.dispose()
      // const exportingMenu = chart.exporting.menu
      // if (exportingMenu != null) {
      //   exportingMenu.dispose()
      // }
    })
  }

  abstract render(data: DataManager): void
}

interface DevState {
  start: number
  end: number
}

export function getInputElement(id: string): HTMLInputElement {
  return document.getElementById(id) as HTMLInputElement
}

export function getButtonElement(id: string): HTMLButtonElement {
  return document.getElementById(id) as HTMLButtonElement
}
