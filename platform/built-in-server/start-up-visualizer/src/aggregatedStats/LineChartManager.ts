// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {LineChartDataManager, MetricDescriptor} from "@/aggregatedStats/LineChartDataManager"
import {ChartSettings} from "@/aggregatedStats/ChartSettings"
import {addExportMenu} from "@/charts/ChartManager"
import HumanizeDuration from "humanize-duration"
import {InfoResponse, Metrics} from "@/aggregatedStats/model"
import {ChartConfigurator} from "@/aggregatedStats/ChartConfigurator"

export class LineChartManager {
  private readonly chart: am4charts.XYChart

  private dataManager: LineChartDataManager | null = null

  // use adapter to use HumanizeDuration
  private toolTipAdapter = (_value: string | undefined, target: am4charts.LineSeries) => {
    const dataItem = target.tooltipDataItem
    const dataContext: any = dataItem == null ? null : dataItem.dataContext
    if (dataContext == null) {
      return _value
    }

    // RFC1123
    let html = `<table class="chartTooltip"><caption>${this.chart.dateFormatter.format(dataContext.t, "EEE, dd MMM yyyy HH:mm:ss zzz")}</caption>`

    const prevItem = dataItem.index == 0 ? null : this.chart.data[dataItem.index - 1]

    const dataManager = this.dataManager!!
    for (const metric of dataManager.metricDescriptors) {
      const value = dataContext[metric.key]
      html += `<tr><th>${metric.name}</th><td>`

      const isDiffAbnormal = prevItem != null && (value - prevItem[metric.key]) >= 100
      if (isDiffAbnormal) {
        html += "<strong>"
      }
      html += shortEnglishHumanizer(value)
      if (isDiffAbnormal) {
        html += "</strong>"
      }
      html += `</td></tr>`
    }

    const generatedTime = dataContext.t / 1000
    html += `</table><input type="button" value="Analyze Report" style="width: 100%" onclick='window.open("/#/report?reportUrl=${encodeURIComponent(dataManager.reportUrlPrefix + "&generatedTime=" + generatedTime)}", "_blank")' />`
    return html
  }

  constructor(container: HTMLElement,
              private chartSettings: ChartSettings,
              private readonly isInstantEvents: boolean,
              private readonly configurator: ChartConfigurator) {
    this.chart = am4core.create(container, am4charts.XYChart)

    const chart = this.chart
    chart.legend = new am4charts.Legend()
    addExportMenu(chart)

    // const dateAxis = chart.xAxes.push(new am4charts.DateAxis())
    // @ts-ignore
    const xAxis = configurator.configureXAxis(chart)
    // xAxis.groupData = true
    // DurationAxis doesn't work due to some unclear bug
    const valueAxis = chart.yAxes.push(new am4charts.ValueAxis())
    // const durationAxis = chart.yAxes.push(new am4charts.DurationAxis())

    // do not use logarithmic scale for line chart of duration events - better looking and more clear charts, if height will be a problem, then chart height can be increased
    valueAxis.logarithmic = this.isInstantEvents
    //
    // valueAxis.baseUnit = "millisecond"
    valueAxis.durationFormatter.baseUnit = "millisecond"
    valueAxis.durationFormatter.durationFormat = "S"

    const cursor = new am4charts.XYCursor()
    cursor.behavior = "zoomXY"
    // cursor.fullWidthLineX = true
    // cursor.xAxis = xAxis
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

    // prevent Vue reactivity
    Object.seal(this)
  }

  private configureScrollbarXWithPreview(): am4charts.XYChartScrollbar {
    const scrollbarX = new am4charts.XYChartScrollbar()
    const chart = this.chart
    chart.scrollbarX = scrollbarX
    scrollbarX.parent = chart.bottomAxesContainer
    return scrollbarX
  }

  scrollbarXPreviewOptionChanged(chartSettings: ChartSettings) {
    this.chartSettings = chartSettings

    // no need to dispose old scrollbar explicit - will be disposed automatically on set
    const chart = this.chart
    if ((chart.scrollbarX instanceof am4charts.XYChartScrollbar) === chartSettings.showScrollbarXPreview) {
      return
    }


    if (chartSettings.showScrollbarXPreview) {
      const scrollbarX = this.configureScrollbarXWithPreview()
      chart.series.each(it => {
        scrollbarX.series.push(it)
      })
    }
    else {
      chart.scrollbarX = new am4core.Scrollbar()
    }
  }

  render(dataManager: LineChartDataManager): void {
    const chart = this.chart
    this.dataManager = dataManager

    const scrollbarX = chart.scrollbarX as am4charts.XYChartScrollbar
    const oldSeries = new Map<string, am4charts.LineSeries>()

    for (const series of chart.series) {
      oldSeries.set(series.name, series as am4charts.LineSeries)
    }

    for (const metric of dataManager.metricDescriptors) {
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

    const firstSeries = chart.series.getIndex(0)
    if (firstSeries != null && !firstSeries.adapter.isEnabled("tooltipHTML")) {
      firstSeries.adapter.add("tooltipHTML", this.toolTipAdapter as any)
      const tooltip = firstSeries.tooltip!!
      tooltip.pointerOrientation = "down"
      tooltip.background.fillOpacity = 0.4
      tooltip.adapter.add("y", (_x, _target) => {
        return (this.chart.yAxesAndPlotContainer.y as number) + 40
      })
      tooltip.label.interactionsEnabled = true
    }

    oldSeries.forEach(value => {
      chart.series.removeIndex(chart.series.indexOf(value))
      if (this.chartSettings.showScrollbarXPreview) {
        scrollbarX.series.removeIndex(scrollbarX.series.indexOf(value))
      }
      value.dispose()
    })

    chart.data = dataManager.metrics
  }

  private configureLineSeries(metric: MetricDescriptor, series: am4charts.LineSeries) {
    series.name = metric.name
    // timestamp
    // series.dataFields.dateX = "t"
    this.configurator.configureSeries(series)
    // duration
    series.dataFields.valueY = metric.key

    if (metric.hiddenByDefault) {
      series.hidden = true
    }
  }

  dispose(): void {
    this.chart.dispose()
  }

  setData(data: Array<Metrics>, info: InfoResponse, reportUrlPrefix: string): void {
    this.render(new LineChartDataManager(data, info, this.isInstantEvents, reportUrlPrefix))
  }
}

const shortEnglishHumanizer = HumanizeDuration.humanizer({
  language: "shortEn",
  maxDecimalPoints: 2,
  // exclude "s" seconds to force using ms for consistency
  units: ["h", "m", "ms"],
  languages: {
    shortEn: {
      y: () => 'y',
      mo: () => 'mo',
      w: () => 'w',
      d: () => 'd',
      h: () => 'h',
      m: () => 'm',
      s: () => 's',
      ms: () => 'ms',
    }
  }
})