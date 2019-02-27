// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import {InputData, Item, XYChartManager} from "./core"
import * as am4core from "@amcharts/amcharts4/core"

export type ComponentProviderSourceNames = "appComponents" | "projectComponents"
export type ServiceProviderSourceNames = "appServices" | "projectServices"
export type TopHitProviderSourceNames = "appOptionsTopHitProviders" | "projectOptionsTopHitProviders"

export abstract class ItemChartManager extends XYChartManager {
  // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
  protected constructor(container: HTMLElement, private readonly sourceNames: Array<ComponentProviderSourceNames> | Array<TopHitProviderSourceNames> | Array<ServiceProviderSourceNames>) {
    super(container, module.hot)

    this.configureNameAxis()
    this.configureDurationAxis()
    this.configureSeries()

    this.chart.legend = new am4charts.Legend()
    // make colors more contrast because we have more than one series
    this.chart.colors.step = 2
  }

  private get nameAxis(): am4charts.CategoryAxis {
    return this.chart.xAxes.getIndex(0) as am4charts.CategoryAxis
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

    // cursor tooltip is distracting
    nameAxis.cursorTooltipEnabled = false
  }

  private configureDurationAxis(): void {
    const durationAxis = this.chart.yAxes.push(new am4charts.DurationAxis())
    durationAxis.title.text = "Duration"
    // base unit the values are in (https://www.amcharts.com/docs/v4/reference/durationformatter/)
    durationAxis.durationFormatter.baseUnit = "millisecond"
    durationAxis.durationFormatter.durationFormat = "S"
    durationAxis.strictMinMax = true

    // cursor tooltip is distracting
    durationAxis.cursorTooltipEnabled = false
  }

  protected configureSeries(): void {
    const a = this.addSeries()
    const p = this.addSeries()

    a.name = "Application-level"
    p.name = "Project-level"
  }

  protected addSeries(): am4charts.ColumnSeries {
    const series = this.chart.series.push(new am4charts.ColumnSeries())
    series.dataFields.dateX = "start"
    series.dataFields.categoryX = "shortName"
    series.dataFields.valueY = "duration"
    series.columns.template.tooltipText = "{name}: {duration} ms\nrange: {start}-{end}"
    series.clustered = false

    // noinspection SpellCheckingInspection
    series.events.on("visibilitychanged", event => {
      const nameAxis = this.nameAxis

      const seriesList = this.chart.series
      let offset = 0
      let length = 0
      for (let i = 0; i < seriesList.length; i++) {
        const otherSeries = seriesList.getIndex(i)!!
        if (otherSeries === series) {
          length = series.data.length
          break
        }

        if (!otherSeries.visible) {
          // do not take in account because if not visible, data is already removed from axis data
          continue
        }

        offset += otherSeries.data.length
      }

      if (event.visible) {
        nameAxis.data.splice(offset, 0, ...series.data)
      }
      else {
        nameAxis.data.splice(offset, length)
      }
      // trigger update
      nameAxis.data = nameAxis.data

      // without this call items is not rendered correctly (overlapped)
      this.chart.invalidateData()
    })
    return series
  }

  render(data: InputData) {
    const sources: Array<Array<Item>> = []
    const series = this.chart.series
    let seriesIndex = 0
    const axisData = []
    for (const sourceName of this.sourceNames) {
      const items = data[sourceName] || []
      sources.push(items)
      ItemChartManager.assignShortName(items)
      series.getIndex(seriesIndex++)!!.data = items

      axisData.push(...items)
    }

    // https://www.amcharts.com/docs/v4/concepts/series/#Note_about_Series_data_and_Category_axis
    const nameAxis = this.nameAxis
    nameAxis.data = axisData

    this.computeRangeMarkers(data)

    // since we don't set chart data, but set data to series and axis explicitly, amcharts doesn't re-zoom on new data, so, needed to be called explicitly
    this.chart.invalidateData()
  }

  protected computeRangeMarkers(data: InputData) {
    const nameAxis = this.nameAxis
    const appInitialized = data.items.find(it => it.name === "app initialized callback")

    let outOfInitialized: ClassItem | null = null
    let outOfReady: ClassItem | null = null
    for (const item of nameAxis.data) {
      if (outOfInitialized == null && appInitialized != null && item.start >= appInitialized.end) {
        outOfInitialized = item as ClassItem
      }
      if (outOfReady == null && item.start >= data.totalDurationActual) {
        outOfReady = item as ClassItem
      }

      if (outOfInitialized != null && outOfReady != null) {
        break
      }
    }

    nameAxis.axisRanges.clear()
    this.createRangeMarker(nameAxis, outOfInitialized, "app initialized")
    this.createRangeMarker(nameAxis, outOfReady, "ready")
  }

  private createRangeMarker(axis: am4charts.CategoryAxis, item: ClassItem | null, label: string) {
    if (item == null) {
      return
    }

    const range = axis.axisRanges.create()
    range.category = item.shortName
    range.label.tooltipText = label
    // range.label.text = label
    // range.label.inside = true
    // range.label.rotation = 0
    // range.label.verticalCenter = "middle"
    // range.label.horizontalCenter = "right"
    range.grid.stroke = am4core.color("#396478")
    range.grid.strokeWidth = 2
    range.grid.strokeOpacity = 1
  }

  private static assignShortName(items: Array<Item>) {
    for (const component of items) {
      const componentItem = component as ClassItem
      const lastDotIndex = component.name.lastIndexOf(".")
      componentItem.shortName = lastDotIndex < 0 ? component.name : component.name.substring(lastDotIndex + 1)
    }
  }
}

export class ComponentChartManager extends ItemChartManager {
  constructor(container: HTMLElement) {
    super(container, ["appComponents", "projectComponents"])
  }

  // doesn't make sense for components - cannot be outside of ready, and app initialized is clear
  // because color for app/project bars is different
  protected computeRangeMarkers(_data: InputData) {
  }
}

export class ServiceChartManager extends ItemChartManager {
  constructor(container: HTMLElement) {
    super(container, ["appServices", "projectServices"])
  }
}

export class TopHitProviderChart extends ItemChartManager {
  constructor(container: HTMLElement) {
    super(container, ["appOptionsTopHitProviders", "projectOptionsTopHitProviders"])
  }
}

interface ClassItem extends Item {
  shortName: string
}