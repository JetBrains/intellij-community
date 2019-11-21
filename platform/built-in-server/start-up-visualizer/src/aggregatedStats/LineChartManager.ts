// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {ChartSettings} from "@/aggregatedStats/ChartSettings"
import {addExportMenu, StatChartManager} from "@/charts/ChartManager"
import HumanizeDuration from "humanize-duration"
import {MetricDescriptor, Metrics} from "@/aggregatedStats/model"
import {ChartConfigurator} from "@/aggregatedStats/ChartConfigurator"
import * as am4plugins_annotation from "@amcharts/amcharts4/plugins/annotation"

export class LineChartManager implements StatChartManager {
  private readonly chart: am4charts.XYChart

  reportUrlPrefix: string | null = null
  metricDescriptors: Array<MetricDescriptor> = []

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

    for (const metric of this.metricDescriptors) {
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
    html += `</table><input type="button" value="Analyze Report" style="width: 100%" onclick='window.open("/#/report?reportUrl=${encodeURIComponent(this.reportUrlPrefix + "&generatedTime=" + generatedTime)}", "_blank")' />`
    return html
  }

  constructor(container: HTMLElement,
              private chartSettings: ChartSettings,
              private readonly isInstantEvents: boolean,
              private readonly configurator: ChartConfigurator) {
    this.chart = am4core.create(container, am4charts.XYChart)

    const chart = this.chart

    chart.legend = new am4charts.Legend()
    chart.legend.itemContainers.template.events.on("over", event => {
      const dataItem = event.target.dataItem
      if (dataItem == null) {
        return
      }

      for (const series of this.chart.series) {
        if (dataItem.dataContext === series) {
          continue
        }

        for (const segment of (series as am4charts.LineSeries).segments) {
          segment.setState("inactive")
        }
      }
    })
    chart.legend.itemContainers.template.events.on("out", event => {
      const dataItem: any = event.target.dataItem
      if (dataItem == null) {
        return
      }

      for (const series of this.chart.series) {
        if (dataItem.dataContext === series) {
          continue
        }

        for (const segment of (series as am4charts.LineSeries).segments) {
          segment.setState("default")
        }
      }
    })

    chart.colors.step = 4
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
    valueAxis.durationFormatter.baseUnit = "millisecond"
    valueAxis.durationFormatter.durationFormat = "S"

    const cursor = new am4charts.XYCursor()
    cursor.behavior = "zoomX"
    chart.cursor = cursor

    if (this.chartSettings.showScrollbarXPreview) {
      this.configureScrollbarXWithPreview()
    }

    chart.plugins.push(new am4plugins_annotation.Annotation())

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
    if ((chart.scrollbarX != null) === chartSettings.showScrollbarXPreview) {
      return
    }

    if (chartSettings.showScrollbarXPreview) {
      const scrollbarX = this.configureScrollbarXWithPreview()
      chart.series.each(it => {
        scrollbarX.series.push(it)
      })
    }
    else {
      chart.scrollbarX = null as any
    }
  }

  render(data: Array<Metrics>): void {
    const chart = this.chart

    if (chart.series.length === 0) {
      const scrollbarX = chart.scrollbarX as am4charts.XYChartScrollbar
      for (const metric of this.metricDescriptors) {
        const series = new am4charts.LineSeries()
        this.configureLineSeries(metric, series)
        chart.series.push(series)
        if (this.chartSettings.showScrollbarXPreview) {
          scrollbarX.series.push(series)
        }
      }

      const firstSeries = chart.series.getIndex(0)!!
      if (!firstSeries.adapter.isEnabled("tooltipHTML")) {
        firstSeries.adapter.add("tooltipHTML", this.toolTipAdapter as any)
        const tooltip = firstSeries.tooltip!!
        tooltip.pointerOrientation = "down"
        tooltip.background.fillOpacity = 0.4
        tooltip.adapter.add("y", (_x, _target) => {
          return (this.chart.yAxesAndPlotContainer.y as number) + 40
        })
        tooltip.label.interactionsEnabled = true
      }
    }

    chart.data = data
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

    series.strokeWidth = 2
    const segmentState = series.segments.template.states.create("inactive")
    segmentState.properties.strokeOpacity = 0.4
  }

  dispose(): void {
    this.chart.dispose()
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