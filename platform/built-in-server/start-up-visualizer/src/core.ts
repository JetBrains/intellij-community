// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"

export interface Item {
  name: string
  description?: string

  start: number
  end: number

  duration: number
}

export interface InputData {
  version: string

  items: Array<Item>

  appComponents?: Array<Item>
  projectComponents?: Array<Item>

  preloadActivities?: Array<Item>
  appOptionsTopHitProviders?: Array<Item>
  projectOptionsTopHitProviders?: Array<Item>

  totalDurationComputed: number
  totalDurationActual: number
}

export interface ChartManager {
  render(data: InputData): void
}

function configureCommonChartSettings(chart: am4charts.XYChart) {
  chart.exporting.menu = new am4core.ExportMenu()
  chart.mouseWheelBehavior = "zoomX"
  chart.scrollbarX = new am4core.Scrollbar()
  // chart.cursor = new am4charts.XYCursor()
}

export abstract class XYChartManager implements ChartManager {
  protected readonly chart: am4charts.XYChart

  protected constructor(container: HTMLElement) {
    this.chart = am4core.create(container, am4charts.XYChart)
    configureCommonChartSettings(this.chart)

    if (module != null && module.hot != null) {
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
  protected addDisposeHandler(hot: __WebpackModuleApi.Hot | null | undefined) {
    if (hot == null) {
      return
    }

    hot.dispose(_data => {
      this.chart.dispose()
    })
  }

  abstract render(data: InputData): void

  // abstract render(data: InputData): void
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