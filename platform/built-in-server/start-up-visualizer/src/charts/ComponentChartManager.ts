// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {DataManager} from "@/state/DataManager"
import {ActivityChartManager, ClassItem, ClassItemChartConfig} from "@/charts/ActivityChartManager"
import {Item} from "@/state/data"
import {ActivityChartDescriptor} from "@/charts/ActivityChartDescriptor"

export class ComponentChartManager extends ActivityChartManager {
  constructor(container: HTMLElement, sourceNames: Array<string>, descriptor: ActivityChartDescriptor) {
    super(container, sourceNames, descriptor)
  }

  // doesn't make sense for components - cannot be outside of ready, and app initialized is clear
  // because color for app/project bars is different
  protected computeRangeMarkers(_data: DataManager) {
  }

  protected transformDataItem(item: Item, chartConfig: ClassItemChartConfig, sourceName: string, items: Array<Item>): ClassItem {
    const result = super.transformDataItem(item, chartConfig, sourceName, items);
    (result as ComponentItem).totalDuration = item.end - item.start
    return result
  }

  protected getTooltipText() {
    return super.getTooltipText() + "\ntotal duration: {totalDuration} ms"
  }
}

interface ComponentItem extends ClassItem {
  totalDuration: number
}