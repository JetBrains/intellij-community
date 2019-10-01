// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {AggregatedDataManager, MetricDescriptor} from "@/aggregatedStats/AggregatedDataManager"
import {ChartSettings} from "@/aggregatedStats/ChartSettings"
import {addExportMenu} from "@/charts/ChartManager"

interface ChartConfigurator {
   configureXAxis(chart: am4charts.XYChart): void

  configureSeries(series: am4charts.LineSeries): void
}

class SortedByCategory implements ChartConfigurator {
  configureXAxis(chart: am4charts.XYChart): void {
    const axis = new am4charts.CategoryAxis()
    axis.dataFields.category = "build"

    chart.xAxes.push(axis)

    // https://www.amcharts.com/docs/v4/tutorials/handling-repeating-categories-on-category-axis/
    axis.renderer.labels.template.adapter.add("textOutput", (text) => {
      if (text == null) {
        return text
      }

      const suffixIndex = text.indexOf(" (")
      return suffixIndex === -1 ? text : text.substring(0, suffixIndex)
    })
  }

  configureSeries(series: am4charts.LineSeries) {
    series.dataFields.categoryX = "build"
  }
}

export class AggregatedStatsChartManager {
  private readonly chart: am4charts.XYChart

  constructor(container: HTMLElement, private readonly chartSettings: ChartSettings, private readonly isInstantEvents: boolean, private readonly configurator: ChartConfigurator = new SortedByCategory()) {
    this.chart = am4core.create(container, am4charts.XYChart)

    const chart = this.chart
    chart.legend = new am4charts.Legend()
    addExportMenu(chart)

    // const dateAxis = chart.xAxes.push(new am4charts.DateAxis())
    configurator.configureXAxis(chart)
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
    if (this.chartSettings.showScrollbarXPreview) {
      this.configureScrollbarXWithPreview()
    }
    else {
      chart.scrollbarX = new am4core.Scrollbar()
    }
  }

  private configureScrollbarXWithPreview(): am4charts.XYChartScrollbar {
    const scrollbarX = new am4charts.XYChartScrollbar()
    const chart = this.chart
    chart.scrollbarX = scrollbarX
    scrollbarX.parent = chart.bottomAxesContainer
    return scrollbarX
  }

  scrollbarXPreviewOptionChanged() {
    // no need to dispose old scrollbar explicit - will be disposed automatically on set
    const chart = this.chart
    console.log("scrollbarXPreviewOptionChanged: " + this.chartSettings.showScrollbarXPreview)
    if (this.chartSettings.showScrollbarXPreview) {
      const scrollbarX = this.configureScrollbarXWithPreview()
      chart.series.each(it => {
        scrollbarX.series.push(it)
      })
    }
    else {
      chart.scrollbarX = new am4core.Scrollbar()
    }
  }

  render(dataManager: AggregatedDataManager): void {
    const chart = this.chart

    const scrollbarX = chart.scrollbarX as am4charts.XYChartScrollbar
    const oldSeries = new Map<string, am4charts.LineSeries>()

    for (const series of chart.series) {
      oldSeries.set(series.name, series as am4charts.LineSeries)
    }

    for (const metric of (this.isInstantEvents ? dataManager.instantMetricDescriptors : dataManager.durationMetricDescriptors)) {
      let series = oldSeries.get(metric.key)
      if (series == null) {
        series = new am4charts.LineSeries()
        this.configureLineSeries(metric, series)
        chart.series.push(series)
      }
      else {
        oldSeries.delete(metric.key)
      }

      if (this.chartSettings.showScrollbarXPreview) {
        scrollbarX.series.push(series)
      }
    }

    oldSeries.forEach(value => {
      chart.series.removeIndex(chart.series.indexOf(value))
      if (this.chartSettings.showScrollbarXPreview) {
        scrollbarX.series.removeIndex(scrollbarX.series.indexOf(value))
      }
      value.dispose()
    })

    chart.data = dataManager.metrics
    if (this.isInstantEvents) {
      (window as any).chart = chart
    }
    console.log(dataManager.metrics)
  }

  private configureLineSeries(metric: MetricDescriptor, series: am4charts.LineSeries) {
    series.name = metric.name
    // timestamp
    // series.dataFields.dateX = "t"
    this.configurator.configureSeries(series)
    // duration
    series.dataFields.valueY = metric.key
    // RFC1123
    series.tooltipText = `${metric.name}: {${metric.name}} ms\n` + "report time: {t.formatDate('EEE, dd MMM yyyy HH:mm:ss zzz')}"
    if (metric.hiddenByDefault) {
      series.hidden = true
    }
  }

  dispose(): void {
    this.chart.dispose()
  }
}