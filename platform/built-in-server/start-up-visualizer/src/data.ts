// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
export interface Item {
  name: string
  description?: string

  start: number
  end: number

  duration: number
}

export interface InputData {
  version: string

  items: Array<Item>

  appComponents?: Array<Item>
  projectComponents?: Array<Item>

  appServices?: Array<Item>
  projectServices?: Array<Item>

  preloadActivities?: Array<Item>
  appOptionsTopHitProviders?: Array<Item>
  projectOptionsTopHitProviders?: Array<Item>

  totalDurationComputed: number
  totalDurationActual: number
}

const markerNames = ["app initialized callback", "module loading"]
const markerNameToRangeTitle = new Map<string, string>([["app initialized callback", "app initialized"], ["module loading", "project initialized"]])

interface GuideLineDescriptor {
  readonly item: Item
  readonly label: string
}

export class DataManager {
  readonly markerItems: Array<Item | null>

  constructor(readonly data: InputData) {
    const markerItems = new Array(markerNames.length)
    // bloody JS array is sparse and setting length doesn't pre-fill array
    markerItems.fill(null)
    itemLoop:  for (const item of data.items) {
      for (let i = 0; i < markerNames.length; i++) {
        if (markerItems[i] == null && item.name === markerNames[i]) {
          markerItems[i] = item

          // stop if all items are found
          if (markerItems.every(it => it != null)) {
            break itemLoop
          }
        }
      }
    }

    for (let i = 0; i < markerNames.length; i++) {
      if (markerItems[i] == null) {
        console.error(`Cannot find item for phase "${markerNames[i]}"`)
      }
    }

    this.markerItems = markerItems
  }

  computeGuides(items: Array<Item>): Array<GuideLineDescriptor> {
    const rangeItems: Array<Item | null> = new Array(markerNames.length)
    rangeItems.fill(null)

    let outOfReady: Item | null = null
    for (const item of items) {
      for (let i = 0; i < this.markerItems.length; i++) {
        if (rangeItems[i] == null) {
          const markerItem = this.markerItems[i]
          if (markerItem != null && item.start >= markerItem.end) {
            rangeItems[i] = item
          }
        }
      }

      if (outOfReady == null && item.start >= this.data.totalDurationActual) {
        outOfReady = item
      }

      if (outOfReady != null && rangeItems.every(it => it != null)) {
        break
      }
    }

    const result: Array<GuideLineDescriptor> = []
    for (let i = 0; i < markerNames.length; i++) {
      const rangeItem = rangeItems[i]
      if (rangeItem != null) {
        let rangeLabel = markerNames[i]
        const customLabel = markerNameToRangeTitle.get(rangeLabel)
        if (customLabel != null) {
          rangeLabel = customLabel
        }
        result.push({item: rangeItem, label: rangeLabel})
      }
    }

    if (outOfReady != null) {
      result.push({item: outOfReady, label: "ready"})
    }
    return result
  }
}
