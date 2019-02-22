// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Item} from "./core"

export interface TimeLineItem extends Item {
  level: number
  rowIndex: number

  colorIndex: number
}

export function computeLevels(items: Array<Item>) {
  let lastAllocatedColorIndex = 0
  for (let i = 0; i < items.length; i++) {
    const item = items[i] as TimeLineItem
    let level = 0
    for (let j = i - 1; j >= 0; j--) {
      const prevItem = items[j] as TimeLineItem
      if (prevItem.end >= item.end) {
        level = prevItem.level + 1
        item.colorIndex = prevItem.colorIndex
        break
      }
    }

    if (item.colorIndex === undefined) {
      item.colorIndex = lastAllocatedColorIndex++
    }
    item.level = level
  }
}