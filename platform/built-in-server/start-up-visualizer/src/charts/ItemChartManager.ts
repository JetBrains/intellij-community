// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {XYChartManager} from "@/charts/ChartManager"
import {DataManager} from "@/state/DataManager"
import {Item} from "@/state/data"

export type ComponentProviderSourceNames = "appComponents" | "projectComponents" | "moduleComponents"
export type ServiceProviderSourceNames = "appServices" | "projectServices" | "moduleServices"
export type ExtensionProviderSourceNames = "appExtensions" | "projectExtensions" | "moduleExtensions"
export type TopHitProviderSourceNames = "appOptionsTopHitProviders" | "projectOptionsTopHitProviders"

export class ItemChartManager extends XYChartManager {
  // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
  constructor(container: HTMLElement,
              private readonly sourceNames: Array<ComponentProviderSourceNames>
                | Array<TopHitProviderSourceNames>
                | Array<ServiceProviderSourceNames>
                | Array<ExtensionProviderSourceNames>) {
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
    const nameAxisLabel = nameAxis.renderer.labels.template
    // allow to copy text
    nameAxisLabel.selectable = true
    nameAxisLabel.fontSize = 12
    // quite useful to have tooltips also on axis labels (user report: they're easy to target with mouse)
    nameAxisLabel.tooltipText = "{name}: {duration} ms\nrange: {start}-{end}"

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
    durationAxis.baseUnit = "millisecond"
    // base unit the values are in (https://www.amcharts.com/docs/v4/reference/durationformatter/)
    durationAxis.durationFormatter.baseUnit = "millisecond"
    durationAxis.durationFormatter.durationFormat = "S"
    // do not set strictMinMax because otherwise zooming will not work
    // (axis will not reflect currently selected items, but always the whole set and so, will be not convenient to see items with small values)
    // durationAxis.strictMinMax = true

    // cursor tooltip is distracting
    durationAxis.cursorTooltipEnabled = false

    // https://www.amcharts.com/docs/v4/concepts/axes/positioning-axis-elements/#Setting_the_density_of_the_the_grid_labels
    // default value 40 makes distribution not good
    // but for other charts default value suits better...
    // decided to not do anything - if you don't like that extremes makes chart not readable - fix these extremes or select desired area (cursor is supported)
  }

  protected configureSeries(): void {
    const series = this.chart.series.push(new am4charts.ColumnSeries())
    series.dataFields.categoryX = "shortName"
    series.dataFields.valueY = "duration"
    series.columns.template.configField = "chartConfig"
    series.columns.template.tooltipText = "{name}: {duration} ms\nrange: {start}-{end}"
  }

  // https://www.amcharts.com/docs/v4/concepts/series/#Note_about_Series_data_and_Category_axis
  render(data: DataManager): void {
    const concatenatedData: Array<ClassItem> = []
    let colorIndex = 0
    const legendData: Array<LegendItem> = []
    const applicableSources = new Set<string>()
    for (const sourceName of this.sourceNames) {
      // see XYChart.handleSeriesAdded method - fill and stroke are required to be set, and stroke is set to fill if not set otherwise (in our case fill and stroke are equals)
      // do not use XYChart.handleSeriesAdded because on add series it is already called, so, we need to reuse already generated color for first index
      const color = this.chart.colors.getIndex(colorIndex++)
      const chartConfig: ClassItemChartConfig = {
        fill: color,
        stroke: color,
      }

      // generate color before - even if no data for this type of items, still color should be the same regardless of current data set
      // so, if currently no data for project, but there is data for modules, color for modules should use index 3 and not 2
      const items = data.data[sourceName]
      if (items == null || items.length === 0) {
        continue
      }

      const legendItem: LegendItem = {
        name: this.sourceNameToLegendName(sourceName, items.length),
        fill: color,
        sourceName,
      }
      legendData.push(legendItem)
      applicableSources.add(sourceName)

      for (const item of items) {
        concatenatedData.push({
          ...item,
          shortName: getShortName(item),
          chartConfig,
          sourceName,
        })
      }
    }

    this.chart.legend.data = legendData
    this.chart.legend.itemContainers.template.events.on("hit", event => {
      const target = event!!.target!!
      const legendItem = target!!.dataItem!!.dataContext as LegendItem
      if (target.isActive) {
        applicableSources.delete(legendItem.sourceName)
      }
      else {
        applicableSources.add(legendItem.sourceName)
      }
      // maybe there is a more effective way to hide data, but this one is reliable and simple
      this.chart.data = concatenatedData.filter(it => applicableSources.has(it.sourceName))
    })

    concatenatedData.sort((a, b) => a.start - b.start)
    this.computeRangeMarkers(data, concatenatedData)
    this.chart.data = concatenatedData
  }

  private sourceNameToLegendName(sourceName: string, itemCount: number): string {
    let prefix
    if (sourceName.startsWith("app")) {
      prefix = "Application"
    }
    else if (sourceName.startsWith("project")) {
      prefix = "Project"
    }
    else if (sourceName.startsWith("module")) {
      prefix = "Module"
    }
    return `${prefix}-level (${itemCount})`
  }

  protected computeRangeMarkers(data: DataManager, items: Array<ClassItem>): void {
    const nameAxis = this.nameAxis
    nameAxis.axisRanges.clear()
    for (const guideLineDescriptor of data.computeGuides(items)) {
      this.createRangeMarker(nameAxis, guideLineDescriptor.item as ClassItem, guideLineDescriptor.label)
    }
  }

  private createRangeMarker(axis: am4charts.CategoryAxis, item: ClassItem, label: string): void {
    const range = axis.axisRanges.create()
    range.category = item.shortName
    range.label.inside = true
    range.label.horizontalCenter = "middle"
    range.label.valign = "bottom"
    range.label.text = label
    range.label.rotation = 0
    range.grid.stroke = am4core.color("#000000")
    range.grid.strokeDasharray = "2,2"
    range.grid.strokeOpacity = 1

    range.label.adapter.add("dy", (_y, _target) => {
      return -this.chart.yAxes.getIndex(0)!!.pixelHeight
    })
    range.label.adapter.add("x", (x, _target) => {
      const rangePoint = range.point
      return rangePoint == null ? x : rangePoint.x
    })
  }
}

function getShortName(item: Item): string {
  const lastDotIndex = item.name.lastIndexOf(".")
  return lastDotIndex < 0 ? item.name : item.name.substring(lastDotIndex + 1)
}

export class ComponentChartManager extends ItemChartManager {
  constructor(container: HTMLElement) {
    super(container, ["appComponents", "projectComponents", "moduleComponents"])
  }

  // doesn't make sense for components - cannot be outside of ready, and app initialized is clear
  // because color for app/project bars is different
  protected computeRangeMarkers(_data: DataManager) {
  }
}

interface LegendItem {
  readonly name: string
  readonly sourceName: string
  readonly fill: am4core.Color
}

interface ClassItem extends Item {
  readonly shortName: string
  readonly chartConfig: ClassItemChartConfig

  readonly sourceName: string
}

interface ClassItemChartConfig {
  readonly fill: am4core.Color
  readonly stroke: am4core.Color
}