// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {addExportMenu} from "@/charts/ChartManager"

export class AggregatedStatsChartManager {
  private readonly chart: am4charts.XYChart

  constructor(container: HTMLElement) {
    this.chart = am4core.create(container, am4charts.XYChart)

    const chart = this.chart
    chart.legend = new am4charts.Legend()
    addExportMenu(chart)

    // @ts-ignore
    const dateAxis = chart.xAxes.push(new am4charts.DateAxis())
    // @ts-ignore
    const valueAxis = chart.yAxes.push(new am4charts.ValueAxis())
    valueAxis.logarithmic = true

    const cursor = new am4charts.XYCursor()
    cursor.behavior = "zoomXY"
    // cursor.xAxis = dateAxis
    // cursor.snapToSeries = series
    chart.cursor = cursor

    // create vertical scrollbar and place it before the value axis
    chart.scrollbarY = new am4core.Scrollbar()
    chart.scrollbarY.parent = chart.leftAxesContainer
    chart.scrollbarY.toBack()

    // create a horizontal scrollbar with preview and place it underneath the date axis
    const scrollbarX = new am4charts.XYChartScrollbar()
    chart.scrollbarX = scrollbarX
    // scrollbarX.series.push(series)
    scrollbarX.parent = chart.bottomAxesContainer
  }

  render(data: Array<MachineMetrics>): void {
    const chart = this.chart

    const metrics = data[0].metrics

    // do not sort, use as is
    const metricNames = Object.keys(metrics[0])
    // metricNames.sort()

    chart.series.clear()
    for (const metricName of metricNames) {
      if (metricName == "splash" || metricName == "t") {
        continue
      }

      const series = chart.series.push(new am4charts.LineSeries())
      series.name = metricName

      // timestamp
      series.dataFields.dateX = "t"
      // duration
      series.dataFields.valueY = metricName
      series.tooltipText = `${metricName}: {${metricName}}`
    }

    this.chart.data = metrics
  }

  dispose(): void {
    this.chart.dispose()
  }
}