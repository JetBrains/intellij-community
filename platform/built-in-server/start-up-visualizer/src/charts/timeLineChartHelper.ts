// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4charts from "@amcharts/amcharts4/charts"
import * as am4core from "@amcharts/amcharts4/core"
import {Item} from "@/state/data"

export interface TimeLineItem extends Item {
  level: number
  rowIndex: number

  colorIndex: number

  color: am4core.Color
}

export function transformToTimeLineItems(items: Array<Item>): Array<TimeLineItem> {
  const result = new Array<TimeLineItem>(items.length)
  let lastAllocatedColorIndex = 0
  for (let i = 0; i < items.length; i++) {
    const item: TimeLineItem = {
      ...items[i],
      level: 0,
      colorIndex: -1,
      rowIndex: -1,
      color: null as any,
    }
    result[i] = item

    for (let j = i - 1; j >= 0; j--) {
      const prevItem = result[j]
      if (prevItem.end >= item.end) {
        item.level = prevItem.level + 1
        item.colorIndex = prevItem.colorIndex
        break
      }
    }

    if (item.colorIndex === -1) {
      item.colorIndex = lastAllocatedColorIndex++
    }
  }
  return result
}

export function disableGridButKeepBorderLines(axis: am4charts.Axis) {
  axis.renderer.grid.template.adapter.add("disabled", (_, target) => {
    if (target.dataItem == null) {
      return false
    }

    const index = target.dataItem.index
    return !(index === 0 || index === -1)
  })
}
