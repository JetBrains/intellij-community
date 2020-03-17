// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Axis, CategoryAxis, DateAxis, LineSeries, XYChart} from "@amcharts/amcharts4/charts"
import {Circle, DataItem, MouseCursorStyle} from "@amcharts/amcharts4/core"

export interface ChartConfigurator {
  configureXAxis(chart: XYChart): Axis

  configureSeries(series: LineSeries): void
}

export class SortedByCategory implements ChartConfigurator {
  configureXAxis(chart: XYChart): Axis {
    const axis = new CategoryAxis()
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

  configureSeries(series: LineSeries) {
    series.dataFields.categoryX = "build"
  }
}

export class SortedByDate implements ChartConfigurator {
  constructor(private readonly showTooltip: (data: any | null) => void) {
  }

  configureXAxis(chart: XYChart): Axis {
    const axis = new DateAxis()
    axis.groupData = true
    // on granularity change, keep currently selected range
    axis.keepSelection = true
    axis.dataFields.date = "t"
    chart.xAxes.push(axis)

    chart.chartContainer.events.on("hit", _event => {
      const dataItem = axis.getSeriesDataItem(chart.series.getIndex(0)!!, axis.toAxisPosition(chart.cursor.xPosition), true)
      if (dataItem != null) {
        this.showToolTipFromDataItem(dataItem)
      }
    });
    return axis
  }

  configureSeries(series: LineSeries) {
    series.dataFields.dateX = "t"

    series.minBulletDistance = 15

    const bullet = series.bullets.push(new Circle())
    bullet.radius = 3
    bullet.cursorOverStyle = MouseCursorStyle.pointer

    const hoverState = bullet.states.create("hover")
    hoverState.properties.scale = 1.3

    bullet.events.on("hit", event => {
      const dataItem = event.target.dataItem
      if (dataItem != null) {
        this.showToolTipFromDataItem(dataItem)
      }
    })
  }

  private showToolTipFromDataItem(dataItem: DataItem) {
    const dataContext = dataItem.dataContext
    if (dataContext == null) {
      this.showTooltip((dataItem as any).groupDataItems[0].dataContext)
    }
    else {
      this.showTooltip(dataContext)
    }
  }
}

function stripSuffix(text: string): string {
  const suffixIndex = text.indexOf(" (")
  return suffixIndex === -1 ? text : text.substring(0, suffixIndex)
}
