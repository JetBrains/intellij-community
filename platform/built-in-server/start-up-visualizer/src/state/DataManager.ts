// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {InputData, Item} from "./data"
import {markerNames} from "./StateStorageManager"
import * as semver from "semver"
import {SemVer} from "semver"

const markerNameToRangeTitle = new Map<string, string>([["app initialized callback", "app initialized"], ["module loading", "project initialized"]])

export interface ItemStats {
  readonly reportedComponentCount: number
  readonly reportedServiceCount: number
}

const statSupportMinVersion = semver.coerce("3")!!

export class DataManager {
  private readonly version: SemVer | null

  constructor(readonly data: InputData) {
    this.version = semver.coerce(data.version)
  }

  private _markerItems: Array<Item | null> | null = null

  get isStatSupported(): boolean {
    const version = this.version
    return version != null && semver.gte(version, statSupportMinVersion)
  }

  get itemStats(): ItemStats {
    const data = this.data
    return {
      reportedServiceCount: getListLength(data.appComponents) + getListLength(data.projectComponents) + getListLength(data.moduleComponents),
      reportedComponentCount: getListLength(data.appServices) + getListLength(data.projectServices) + getListLength(data.moduleServices),
    }
  }

  get markerItems(): Array<Item | null> {
    if (this._markerItems != null) {
      return this._markerItems
    }

    const items = this.data == null ? null : this.data!!.items
    if (items == null || items.length === 0) {
      return []
    }

    const result = new Array(markerNames.length)
    // JS array is sparse and setting length doesn't pre-fill array
    result.fill(null)
    itemLoop:  for (const item of items) {
      for (let i = 0; i < markerNames.length; i++) {
        if (result[i] == null && item.name === markerNames[i]) {
          result[i] = item

          // stop if all items are found
          if (result.every(it => it != null)) {
            break itemLoop
          }
        }
      }
    }

    for (let i = 0; i < markerNames.length; i++) {
      if (result[i] == null) {
        console.error(`Cannot find item for phase "${markerNames[i]}"`)
      }
    }

    this._markerItems = result
    return result
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

  toJSON(_key: string): InputData {
    return this.data
  }
}

export interface GuideLineDescriptor {
  readonly item: Item
  readonly label: string
}

function getListLength(list: Array<any> | null | undefined): number {
  return list == null ? 0 : list.length
}