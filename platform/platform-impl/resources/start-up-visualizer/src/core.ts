// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"

export interface Item {
  name: string

  start: number
  end: number

  duration: number

  // added data
  shortName: string
  level: number

  isSubItem: boolean
}

export interface InputData {
  items: Array<Item>
  components?: Array<Item>
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
  }

  abstract render(data: InputData): void

  // abstract render(data: InputData): void
}