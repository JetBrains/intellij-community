// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {CompleteTraceEvent, InputData, InputDataV11AndLess, InputDataV20, ItemV0, ItemV20} from "./data"
import {markerNames} from "./StateStorageManager"
import compareVersions from "compare-versions"
import {serviceSourceNames} from "@/charts/ActivityChartDescriptor"

const markerNameToRangeTitle = new Map<string, string>([["app initialized callback", "app initialized"], ["module loading", "project initialized"]])

export interface ItemStats {
  readonly reportedComponentCount: number
  readonly reportedServiceCount: number
}

const statSupportMinVersion = "3"
const instantEventSupportMinVersion = "11"

export const SERVICE_WAITING = "serviceWaiting"
const serviceEventCategorySet = new Set(serviceSourceNames.concat(SERVICE_WAITING))

export class DataManager {
  private readonly version: string | null

  constructor(readonly data: InputData) {
    this.version = data.version

    const version = this.version
    if (version != null && compareVersions.compare(version, "20", ">=")) {
      convertItemV20ToV0(data.prepareAppInitActivities as any)

      convertItemV20ToV0(data.appExtensions as any)
      convertItemV20ToV0(data.projectExtensions as any)
      convertItemV20ToV0(data.moduleExtensions as any)

      convertItemV20ToV0(data.appOptionsTopHitProviders as any)
      convertItemV20ToV0(data.projectOptionsTopHitProviders as any)

      convertItemV20ToV0(data.projectPostStartupActivities as any)
    }
  }

  private _markerItems: Array<ItemV0 | null> | null = null
  private _serviceEvents: Array<CompleteTraceEvent> | null = null

  get isStatSupported(): boolean {
    const version = this.version
    return version != null && compareVersions.compare(version, statSupportMinVersion, ">=")
  }

  get isInstantEventProvided(): boolean {
    const version = this.version
    return version != null && compareVersions.compare(version, instantEventSupportMinVersion, ">=")
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

    const version = this.version
    const isNewCompactFormat = version != null && compareVersions.compare(version, "20", ">=")
    if (isNewCompactFormat) {
      const list: Array<CompleteTraceEvent> = []
      const data = this.data as InputDataV20

      convertV20ToTraceEvent(data.appComponents, "appComponents", list)
      convertV20ToTraceEvent(data.projectComponents, "projectComponents", list)
      convertV20ToTraceEvent(data.moduleComponents, "moduleComponents", list)

      convertV20ToTraceEvent(data.appServices, "appServices", list)
      convertV20ToTraceEvent(data.projectServices, "projectServices", list)
      convertV20ToTraceEvent(data.moduleServices, "moduleServices", list)

      convertV20ToTraceEvent(data.serviceWaiting, "serviceWaiting", list)

      this._serviceEvents = list
      return list
    }
    else if (version != null && compareVersions.compare(version, "12", ">=")) {
      this._serviceEvents = this.data.traceEvents.filter(value => value.cat != null && serviceEventCategorySet.has(value.cat)) as Array<CompleteTraceEvent>
      return this._serviceEvents
    }
    else {
      const list: Array<CompleteTraceEvent> = []
      const data = this.data as InputDataV11AndLess

      convertV11ToTraceEvent(data.appComponents, "appComponents", list)
      convertV11ToTraceEvent(data.projectComponents, "projectComponents", list)
      convertV11ToTraceEvent(data.moduleComponents, "moduleComponents", list)

      convertV11ToTraceEvent(data.appServices, "appServices", list)
      convertV11ToTraceEvent(data.projectServices, "projectServices", list)
      convertV11ToTraceEvent(data.moduleServices, "moduleServices", list)

      this._serviceEvents = list
      return list
    }
  }

  get markerItems(): Array<ItemV0 | null> {
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
    itemLoop: for (const item of items) {
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
        console.warn(`Cannot find item for phase "${markerNames[i]}"`)
      }
    }

    this._markerItems = result
    return result
  }

  computeGuides(items: Array<ItemV0>): Array<GuideLineDescriptor> {
    const rangeItems: Array<ItemV0 | null> = new Array(markerNames.length)
    rangeItems.fill(null)

    let outOfReady: ItemV0 | null = null
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
  readonly item: ItemV0
  readonly label: string
}

function convertV11ToTraceEvent(odlList: Array<ItemV0> | null | undefined, category: string, list: Array<CompleteTraceEvent>) {
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

function convertV20ToTraceEvent(odlList: Array<ItemV20> | null | undefined, category: string, list: Array<CompleteTraceEvent>) {
  if (odlList == null) {
    return
  }

  for (const item of odlList) {
    list.push({
      ...item,
      name: item.n,
      ph: "X",
      ts: item.s,
      dur: item.d,
      args: {
        ownDur: item.od === undefined ? item.d : item.od,
      },
      tid: item.t,
      cat: category,
    })
  }
}

function convertItemV20ToV0(odlList: Array<ItemV0 & ItemV20> | null | undefined): void {
  if (odlList == null) {
    return
  }

  for (const item of odlList) {
    // @ts-ignore
    // noinspection JSConstantReassignment
    item.name = item.n
    // @ts-ignore
    // noinspection JSConstantReassignment
    item.start = item.s
    // @ts-ignore
    // noinspection JSConstantReassignment
    item.end = item.s + item.d
    // @ts-ignore
    // noinspection JSConstantReassignment
    item.duration = item.od === undefined ? item.d : item.od
    // @ts-ignore
    // noinspection JSConstantReassignment
    item.thread = item.t
  }
}