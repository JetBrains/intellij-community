// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"

export interface ChartConfigurator {
  configureXAxis(chart: am4charts.XYChart): am4charts.Axis

  configureSeries(series: am4charts.LineSeries): void
}

export class SortedByCategory implements ChartConfigurator {
  configureXAxis(chart: am4charts.XYChart): am4charts.Axis {
    const axis = new am4charts.CategoryAxis()
    axis.dataFields.category = "build"

    chart.xAxes.push(axis)

    // https://www.amcharts.com/docs/v4/tutorials/handling-repeating-categories-on-category-axis/
    axis.renderer.labels.template.adapter.add("textOutput", (text) => {
      return text == null ? text : stripSuffix(text)
    })

    axis.adapter.add("getTooltipText", (text) => {
      return text == null ? text : stripSuffix(text)
    })
    return axis
  }

  configureSeries(series: am4charts.LineSeries) {
    series.dataFields.categoryX = "build"
  }
}

export class SortedByDate implements ChartConfigurator {
  configureXAxis(chart: am4charts.XYChart): am4charts.Axis {
    const axis = new am4charts.DateAxis()
    axis.groupData = true
    axis.dataFields.date = "t"
    chart.xAxes.push(axis)
    return axis
  }

  configureSeries(series: am4charts.LineSeries) {
    series.dataFields.dateX = "t"
  }
}

function stripSuffix(text: string): string {
  const suffixIndex = text.indexOf(" (")
  return suffixIndex === -1 ? text : text.substring(0, suffixIndex)
}
