// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {addExportMenu} from "@/charts/ChartManager"
import {AggregatedDataManager, MetricDescriptor} from "@/aggregatedStats/AggregatedDataManager"

export class AggregatedStatsChartManager {
  private readonly chart: am4charts.XYChart

  constructor(container: HTMLElement, private readonly isInstantEvents: boolean) {
    this.chart = am4core.create(container, am4charts.XYChart)

    const chart = this.chart
    chart.legend = new am4charts.Legend()
    addExportMenu(chart)

    // @ts-ignore
    const dateAxis = chart.xAxes.push(new am4charts.DateAxis())
    // @ts-ignore
    // DurationAxis doesn't work due to some unclear bug
    const valueAxis = chart.yAxes.push(new am4charts.ValueAxis())
    // const durationAxis = chart.yAxes.push(new am4charts.DurationAxis())
    valueAxis.logarithmic = true
    //
    // valueAxis.baseUnit = "millisecond"
    valueAxis.durationFormatter.baseUnit = "millisecond"
    valueAxis.durationFormatter.durationFormat = "S"

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
    scrollbarX.parent = chart.bottomAxesContainer
  }

  render(dataManager: AggregatedDataManager): void {
    const chart = this.chart

    const scrollbarX = chart.scrollbarX as am4charts.XYChartScrollbar

    const oldSeries = new Map<string, am4charts.LineSeries>()

    for (const series of chart.series) {
      oldSeries.set(series.name, series as am4charts.LineSeries)
    }

    for (const metric of (this.isInstantEvents ? dataManager.instantMetricsNames : dataManager.durationMetricsNames)) {
      let series = oldSeries.get(metric.key)
      if (series == null) {
        series = new am4charts.LineSeries()
        this.configureLineSeries(metric, series)
        chart.series.push(series)
      }
      else {
        oldSeries.delete(metric.key)
      }

      scrollbarX.series.push(series)
    }

    oldSeries.forEach(value => {
      chart.series.removeIndex(chart.series.indexOf(value))
      scrollbarX.series.removeIndex(scrollbarX.series.indexOf(value))
      value.dispose()
    })

    chart.data = dataManager.metrics
  }

  private configureLineSeries(metric: MetricDescriptor, series: am4charts.LineSeries) {
    series.name = metric.name
    // timestamp
    series.dataFields.dateX = "t"
    // duration
    series.dataFields.valueY = metric.key
    series.tooltipText = `${metric.name}: {${metric.name}} ms`
    if (metric.hiddenByDefault) {
      series.hidden = true
    }
  }

  dispose(): void {
    this.chart.dispose()
  }
}