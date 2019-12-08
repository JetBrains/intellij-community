// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import * as am4core from "@amcharts/amcharts4/core"
import {Item} from "@/state/data"

export interface TimeLineItem extends Item {
  // helper property, not required to visualize chart (used only to compute rowIndex for non-parallel activities)
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

    let colorIndex = -1

    for (let j = i - 1; j >= 0; j--) {
      const prevItem = result[j]
      if (prevItem.end >= item.end) {
        item.level = prevItem.level + 1
        colorIndex = prevItem.colorIndex
        item.colorIndex = colorIndex
        break
      }
    }

    if (colorIndex === -1) {
      colorIndex = lastAllocatedColorIndex++
      item.colorIndex = colorIndex
    }
  }
  return result
}

export interface TimeLineGuide {
  readonly value: number
  readonly endValue?: number

  readonly label: string

  readonly color: am4core.Color
}