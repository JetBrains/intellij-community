// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import {InputData, Item, XYChartManager} from "./core"

export class ComponentsChartManager extends XYChartManager {
  // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
  constructor(container: HTMLElement) {
    super(container)

    this.configureNameAxis()
    this.configureDurationAxis()
    this.configureSeries()
  }

  private configureNameAxis(): void {
    const nameAxis = this.chart.xAxes.push(new am4charts.CategoryAxis())
    nameAxis.dataFields.category = "shortName"
    // allow to copy text
    const nameAxisLabel = nameAxis.renderer.labels.template
    nameAxisLabel.selectable = true
    nameAxisLabel.fontSize = 12

    // https://github.com/amcharts/amcharts4/issues/997
    nameAxisLabel.rotation = -45
    nameAxisLabel.verticalCenter = "middle"
    nameAxisLabel.horizontalCenter = "right"
    nameAxis.renderer.minGridDistance = 1
    nameAxis.renderer.grid.template.location = 0
    nameAxis.renderer.grid.template.disabled = true
  }

  private configureDurationAxis(): void {
    const durationAxis = this.chart.yAxes.push(new am4charts.DurationAxis())
    durationAxis.title.text = "Duration"
    // base unit the values are in (https://www.amcharts.com/docs/v4/reference/durationformatter/)
    durationAxis.durationFormatter.baseUnit = "millisecond"
    durationAxis.durationFormatter.durationFormat = "S"
  }

  private configureSeries(): void {
    const series = this.chart.series.push(new am4charts.ColumnSeries())
    series.dataFields.dateX = "start"
    series.dataFields.categoryX = "shortName"
    series.dataFields.valueY = "duration"
    series.columns.template.tooltipText = "{name}: {duration} ms"
  }

  render(data: InputData) {
    const components = data.components
    if (components == null || components.length === 0) {
      this.chart.data = []
      return
    }

    for (const component of components) {
      const componentItem = component as ComponentItem
      const lastDotIndex = component.name.lastIndexOf(".")
      componentItem.shortName = lastDotIndex < 0 ? component.name : component.name.substring(lastDotIndex + 1)
    }
    this.chart.data = components
  }
}

interface ComponentItem extends Item {
  shortName: string
}