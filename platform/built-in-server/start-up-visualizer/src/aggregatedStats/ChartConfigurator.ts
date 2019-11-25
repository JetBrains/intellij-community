// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"

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
    axis.renderer.labels.template.adapter.add("textOutput", text => {
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
  constructor(private readonly showTooltip: (data: any | null) => void) {
  }

  configureXAxis(chart: am4charts.XYChart): am4charts.Axis {
    const axis = new am4charts.DateAxis()
    axis.groupData = true
    // on granularity change, keep currently selected range
    axis.keepSelection = true
    axis.dataFields.date = "t"
    chart.xAxes.push(axis)
    return axis
  }

  configureSeries(series: am4charts.LineSeries) {
    series.dataFields.dateX = "t"

    series.minBulletDistance = 15

    const bullet = series.bullets.push(new am4charts.CircleBullet())
    bullet.circle.strokeWidth = 2
    bullet.circle.radius = 4
    bullet.circle.fill = am4core.color("#fff")

    bullet.circle.events.on("hit", event => {
      const dataItem = event.target.dataItem
      if (dataItem == null) {
        return
      }

      const dataContext = dataItem.dataContext
      if (dataContext == null) {
        this.showTooltip((dataItem as any).groupDataItems[0].dataContext)
      }
      else {
        this.showTooltip(dataContext)
      }
      // if clicking on a segment, we need to get the tooltip dataItem
      // if (event.target.className === "LineSeriesSegment") {
      //   dataItem = dataItem.component.tooltipDataItem;
      // }
    })
  }
}

function stripSuffix(text: string): string {
  const suffixIndex = text.indexOf(" (")
  return suffixIndex === -1 ? text : text.substring(0, suffixIndex)
}
