// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {ChartManager, InputData} from "./core"

export class ComponentsChart implements ChartManager {
  private readonly chart: am4charts.XYChart

  // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
  constructor(container: HTMLElement, isUseYForName = false) {
    const chart = am4core.create(container, am4charts.XYChart)
    this.chart = chart
    this.configureChart(isUseYForName)

    // https://www.amcharts.com/docs/v4/tutorials/auto-adjusting-chart-height-based-on-a-number-of-data-items/
    // noinspection SpellCheckingInspection
    // chart.events.on("datavalidated", (event: any) => {
    //   const chart = event.target
    //   const categoryAxis = chart.yAxes.getIndex(0)
    //   const adjustHeight = chart.data.length * (isUseYForName ? 20 : 10) - categoryAxis.pixelHeight
    //
    //   // get current chart height
    //   let targetHeight = chart.pixelHeight + adjustHeight
    //
    //   // Set it on chart's container
    //   chart.svgContainer.htmlElement.style.height = targetHeight + "px"
    // })
    chart.scrollbarX = new am4core.Scrollbar()
  }

  render(data: InputData) {
    const components = data.components
    if (components == null || components.length === 0) {
      this.chart.data = []
      return
    }

    // let startOffset = components[0].start
    for (const component of components) {
      const lastDotIndex = component.name.lastIndexOf(".")
      component.shortName = lastDotIndex < 0 ? component.name : component.name.substring(lastDotIndex + 1)
      // component.relativeStart = component.start - startOffset
    }
    this.chart.data = components
  }

  private configureChart(isUseYForName: boolean) {
    const chart = this.chart
    configureCommonChartSettings(chart)

    const nameAxis = (isUseYForName ? chart.yAxes : chart.xAxes).push(new am4charts.CategoryAxis())
    nameAxis.renderer.labels
    nameAxis.dataFields.category = "shortName"
    // allow to copy text
    const nameAxisLabel = nameAxis.renderer.labels.template
    nameAxisLabel.selectable = true
    nameAxisLabel.fontSize = 12

    if (!isUseYForName) {
      nameAxisLabel.rotation = -45
      nameAxisLabel.location = 0.4
      nameAxisLabel.verticalCenter = "middle"
      nameAxisLabel.horizontalCenter = "right"
      nameAxis.renderer.minGridDistance = 0.1

      // https://www.amcharts.com/docs/v4/concepts/axes/#Grid_labels_and_ticks
      nameAxis.renderer.grid.template.location = 0
      nameAxis.renderer.grid.template.disabled = true
    }

    const durationAxis = (isUseYForName ? chart.xAxes : chart.yAxes).push(new am4charts.DurationAxis())
    durationAxis.title.text = "Duration"
    // https://www.amcharts.com/docs/v4/reference/durationformatter/
    // base unit the values are in
    durationAxis.durationFormatter.baseUnit = "millisecond"
    durationAxis.durationFormatter.durationFormat = "S"

    const series = chart.series.push(new am4charts.ColumnSeries())
    series.columns.template.tooltipText = "{name}: {duration} ms"

    series.dataFields.dateX = "start"

    series.dataFields.categoryX = "shortName"
    series.dataFields.categoryY = "shortName"
    series.dataFields.valueY = "duration"
    series.dataFields.valueX = "duration"
  }
}

function configureCommonChartSettings(chart: am4charts.XYChart) {
  chart.exporting.menu = new am4core.ExportMenu()
  chart.mouseWheelBehavior = "zoomX"
  // chart.cursor = new am4charts.XYCursor()
}