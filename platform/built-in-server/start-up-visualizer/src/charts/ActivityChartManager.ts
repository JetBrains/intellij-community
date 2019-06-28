// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {XYChartManager} from "@/charts/ChartManager"
import {DataManager} from "@/state/DataManager"
import {Item} from "@/state/data"
import {ActivityChartDescriptor} from "@/charts/ActivityChartDescriptor"

export class ActivityChartManager extends XYChartManager {
  private legendHitHandler: ((item: LegendItem, isActive: boolean) => void) | null = null

  // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
  constructor(container: HTMLElement, private readonly sourceNames: Array<string>, private readonly descriptor: ActivityChartDescriptor) {
    super(container, module.hot)

    this.configureNameAxis()
    this.configureDurationAxis()
    this.configureSeries()

    if (sourceNames.length > 1) {
      this.createLegend()
    }
  }

  private createLegend() {
    this.chart.legend = new am4charts.Legend()

    // make colors more contrast because we have more than one series
    this.chart.colors.step = 2

    this.chart.legend.itemContainers.template.events.on("hit", event => {
      const target = event!!.target!!
      const legendItem = target!!.dataItem!!.dataContext as LegendItem
      const legendHitHandler = this.legendHitHandler
      if (legendHitHandler != null) {
        legendHitHandler(legendItem, target.isActive)
      }
    })
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
    nameAxisLabel.tooltipText = this.getTooltipText()

    // https://github.com/amcharts/amcharts4/issues/997
    if (this.descriptor.rotatedLabels !== false) {
      nameAxisLabel.rotation = -45
      nameAxisLabel.verticalCenter = "middle"
      nameAxisLabel.horizontalCenter = "right"
    }
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
    series.columns.template.tooltipText = this.getTooltipText()
  }

  protected getTooltipText() {
    let result = "{name}: {duration} ms\nrange: {start}-{end}\nthread: {thread}"
    if (this.descriptor.sourceHasPluginInformation !== false) {
      result += "\nplugin: {plugin}"
    }
    return result
  }

  // https://www.amcharts.com/docs/v4/concepts/series/#Note_about_Series_data_and_Category_axis
  render(data: DataManager): void {
    const concatenatedData: Array<ClassItem> = []
    let colorIndex = 0
    const legendData: Array<LegendItem> = []
    const applicableSources = new Set<string>()

    let sourceNames = this.sourceNames

    let getItemListBySourceName: (name: string) => Array<Item> | null | undefined = name => {
      // @ts-ignore
      return data.data[name]
    }

    let sourceNameToLegendName: (sourceName: string, itemCount: number) => string = this.sourceNameToLegendName.bind(this)

    if (this.descriptor.groupByThread === true) {
      if (sourceNames.length !== 1) {
        throw Error("groupByThread is supported only for single source")
      }

      // @ts-ignore
      const items = data.data[this.sourceNames[0]] as Array<Item>
      if (items == null || items.length === 0) {
        sourceNames = []
      }
      else {
        const threadToItems = new Map<string, Array<Item>>()
        for (const item of items) {
          const thread = item.thread
          let list = threadToItems.get(thread)
          if (list == null) {
            list = []
            threadToItems.set(thread, list)
          }
          list.push(item)
        }
        sourceNames = Array.from(threadToItems.keys())
        sourceNames.sort()
        getItemListBySourceName = name => threadToItems.get(name)
        sourceNameToLegendName = (sourceName, _itemCount) => sourceName
        if (sourceNames.length > 1 && this.chart.legend == null) {
          this.createLegend()
        }
      }
    }

    for (const sourceName of sourceNames) {
      // see XYChart.handleSeriesAdded method - fill and stroke are required to be set, and stroke is set to fill if not set otherwise (in our case fill and stroke are equals)
      // do not use XYChart.handleSeriesAdded because on add series it is already called, so, we need to reuse already generated color for first index
      const color = this.chart.colors.getIndex(colorIndex++)
      const chartConfig: ClassItemChartConfig = {
        fill: color,
        stroke: color,
      }

      // generate color before - even if no data for this type of items, still color should be the same regardless of current data set
      // so, if currently no data for project, but there is data for modules, color for modules should use index 3 and not 2
      const items = getItemListBySourceName(sourceName)
      if (items == null || items.length === 0) {
        continue
      }

      const legendItem: LegendItem = {
        name: sourceNameToLegendName(sourceName, items.length),
        fill: color,
        sourceName,
      }
      legendData.push(legendItem)
      applicableSources.add(sourceName)

      for (const item of items) {
        concatenatedData.push(this.transformDataItem(item, chartConfig, sourceName, items))
      }
    }

    if (this.chart.legend != null) {
      this.chart.legend.data = legendData
      this.legendHitHandler = (legendItem, isActive) => {
        if (isActive) {
          applicableSources.add(legendItem.sourceName)
        }
        else {
          applicableSources.delete(legendItem.sourceName)
        }
        // maybe there is a more effective way to hide data, but this one is reliable and simple
        this.chart.data = concatenatedData.filter(it => applicableSources.has(it.sourceName))
      }
    }

    concatenatedData.sort((a, b) => a.start - b.start)
    this.computeRangeMarkers(data, concatenatedData)
    this.chart.data = concatenatedData
  }

  protected transformDataItem(item: Item, chartConfig: ClassItemChartConfig, sourceName: string, _items: Array<Item>): ClassItem {
    const nameTransformer = this.descriptor.shortNameProducer
    return {
      ...item,
      shortName: nameTransformer == null ? item.name : nameTransformer(item),
      chartConfig,
      sourceName,
    }
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
      // do not add range marker if equals to first item - it means that all items beyond of phase (e.g. project post-startup activities)
      if (guideLineDescriptor.item !== items[0]) {
        const range = nameAxis.axisRanges.create()
        this.configureRangeMarker(range, guideLineDescriptor.label)
        range.category = (guideLineDescriptor.item as ClassItem).shortName
        range.label.rotation = 0
      }
    }
  }
}

interface LegendItem {
  readonly name: string
  readonly sourceName: string
  readonly fill: am4core.Color
}

export interface ClassItem extends Item {
  readonly shortName: string
  readonly chartConfig: ClassItemChartConfig

  readonly sourceName: string
}

export interface ClassItemChartConfig {
  readonly fill: am4core.Color
  readonly stroke: am4core.Color
}