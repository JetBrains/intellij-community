// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {addExportMenu} from "@/charts/ChartManager"
import {GroupedMetricResponse} from "@/aggregatedStats/model"

// todo https://www.amcharts.com/demos/variance-indicators/
export class ClusteredChartManager {
  private readonly chart: am4charts.XYChart

  constructor(container: HTMLElement) {
    this.chart = am4core.create(container, am4charts.XYChart)

    const chart = this.chart
    chart.legend = new am4charts.Legend()
    addExportMenu(chart)

    this.configureCategoryAxis()

    const valueAxis = chart.yAxes.push(new am4charts.DurationAxis())
    valueAxis.durationFormatter.baseUnit = "millisecond"
    valueAxis.durationFormatter.durationFormat = "S"

    const cursor = new am4charts.XYCursor()
    cursor.behavior = "zoomXY"
    cursor.lineX.disabled = true
    cursor.lineY.disabled = true
    // cursor.xAxis = dateAxis
    // cursor.snapToSeries = series
    chart.cursor = cursor
  }

  private configureCategoryAxis() {
    const chart = this.chart
    const categoryAxis = chart.xAxes.push(new am4charts.CategoryAxis())
    categoryAxis.dataFields.category = "metric"
    categoryAxis.renderer.grid.template.location = 0
    categoryAxis.renderer.minGridDistance = 20
    categoryAxis.renderer.cellStartLocation = 0.1
    categoryAxis.renderer.cellEndLocation = 0.9
    const label = categoryAxis.renderer.labels.template
    label.truncate = true
    label.maxWidth = 120
    label.tooltipText = "{category}"
  }

  private render(data: GroupedMetricResponse): void {
    const chart = this.chart

    const oldSeries = new Map<string, am4charts.ColumnSeries>()

    for (const series of chart.series) {
      oldSeries.set(series.name, series as am4charts.ColumnSeries)
    }

    for (const groupName of data.groupNames) {
      let series = oldSeries.get(groupName)
      if (series == null) {
        series = new am4charts.ColumnSeries()
        this.configureSeries(groupName, series)
        chart.series.push(series)
      }
      else {
        oldSeries.delete(groupName)
      }
    }

    oldSeries.forEach(value => {
      console.log("dispose series", value.name)
      chart.series.removeIndex(chart.series.indexOf(value))
      value.dispose()
    })

    chart.data = data.data
  }

  private configureSeries(groupName: string, series: am4charts.ColumnSeries) {
    series.name = groupName
    series.dataFields.valueY = groupName
    series.dataFields.categoryX = "metric"
    series.columns.template.width = am4core.percent(95)
    // series.columns.template.tooltipText = `${groupName}: {valueY} ms`

    let valueLabel = series.bullets.push(new am4charts.LabelBullet())
    valueLabel.label.text = "{valueY.formatDuration('S')}"
    valueLabel.label.verticalCenter = "bottom"
    // valueLabel.label.dx = 10
    valueLabel.label.hideOversized = false
    valueLabel.label.truncate = false
  }

  dispose(): void {
    this.chart.dispose()
  }

  setData(data: Promise<GroupedMetricResponse>) {
    data
      .then(data => {
        if (data != null) {
          this.render(data)
        }
      })
  }
}