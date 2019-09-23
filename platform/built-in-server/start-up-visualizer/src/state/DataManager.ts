// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {CompleteTraceEvent, InputData, InputDataV11AndLess, Item} from "./data"
import {markerNames} from "./StateStorageManager"
import * as semver from "semver"
import {SemVer} from "semver"
import {serviceSourceNames} from "@/charts/ActivityChartDescriptor"

const markerNameToRangeTitle = new Map<string, string>([["app initialized callback", "app initialized"], ["module loading", "project initialized"]])

export interface ItemStats {
  readonly reportedComponentCount: number
  readonly reportedServiceCount: number
}

const statSupportMinVersion = semver.coerce("3")!!
const instantEventSupportMinVersion = semver.coerce("11")!!
const isNewServiceFormat = semver.coerce("12")!!

export const SERVICE_WAITING = "serviceWaiting"
const serviceEventCategorySet = new Set(serviceSourceNames.concat(SERVICE_WAITING))

export class DataManager {
  private readonly version: SemVer | null

  constructor(readonly data: InputData) {
    this.version = semver.coerce(data.version)
  }

  private _markerItems: Array<Item | null> | null = null
  private _serviceEvents: Array<CompleteTraceEvent> | null = null

  get isStatSupported(): boolean {
    const version = this.version
    return version != null && semver.gte(version, statSupportMinVersion)
  }

  get isInstantEventProvided(): boolean {
    const version = this.version
    return version != null && semver.gte(version, instantEventSupportMinVersion)
  }

  private get isNewServiceFormat(): boolean {
    const version = this.version
    return version != null && semver.gte(version, isNewServiceFormat)
  }

  get itemStats(): ItemStats {
    const serviceEvents = this.serviceEvents
    let aC = 0
    let pC = 0
    let mC = 0
    let aS = 0
    let pS = 0
    let mS = 0
    for (const event of serviceEvents) {
      switch (event.cat) {
        case "appComponents":
          aC++;
          break;
        case "projectComponents":
          pC++;
          break;
        case "moduleComponents":
          mC++;
          break;
        case "appServices":
          aS++;
          break;
        case "projectServices":
          pS++;
          break;
        case "moduleServices":
          mS++;
          break;
      }
    }
    return {
      reportedServiceCount: aS + pS + mS,
      reportedComponentCount: aC + pC + mC,
    }
  }

  get serviceEvents(): Array<CompleteTraceEvent> {
    if (this._serviceEvents != null) {
      return this._serviceEvents
    }

    if (this.isNewServiceFormat) {
      this._serviceEvents = this.data.traceEvents.filter(value => value.cat != null && serviceEventCategorySet.has(value.cat)) as Array<CompleteTraceEvent>
      return this._serviceEvents
    }

    const list: Array<CompleteTraceEvent> = []
    const data = this.data as InputDataV11AndLess
    convertToTraceEvent(data.appComponents, "appComponents", list)
    convertToTraceEvent(data.projectComponents, "projectComponents", list)
    convertToTraceEvent(data.moduleComponents, "moduleComponents", list)
    convertToTraceEvent(data.appServices, "appServices", list)
    convertToTraceEvent(data.projectServices, "projectServices", list)
    convertToTraceEvent(data.moduleServices, "moduleServices", list)
    this._serviceEvents = list
    return list
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
          if (markerItem != null && getItemStartInMs(item) >= markerItem.end) {
            rangeItems[i] = item
          }
        }
      }

      if (outOfReady == null && getItemStartInMs(item) >= this.data.totalDurationActual) {
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

function getItemStartInMs(item: any): number {
  if (item.ts === undefined) {
    return item.start
  }
  else {
    // trace event format
    return item.ts / 1000
  }
}

export interface GuideLineDescriptor {
  readonly item: Item
  readonly label: string
}

function convertToTraceEvent(odlList: Array<Item> | null | undefined, category: string, list: Array<CompleteTraceEvent>) {
  if (odlList == null) {
    return
  }

  for (const item of odlList) {
    list.push({
      ...item,
      ph: "X",
      ts: item.start * 1000,
      dur: (item.end - item.start) * 1000,
      args: {
        ownDur: item.duration * 1000,
      },
      tid: item.thread,
      cat: category,
    })
  }
}