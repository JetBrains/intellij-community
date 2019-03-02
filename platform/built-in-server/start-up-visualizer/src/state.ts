// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import Vue from "vue"
import Vuex from "vuex"
import {Module, Mutation, VuexModule} from "vuex-module-decorators"
import VuexPersistence from "vuex-persist"
import {InputData, Item} from "@/data"

const markerNames = ["app initialized callback", "module loading"]
const markerNameToRangeTitle = new Map<string, string>([["app initialized callback", "app initialized"], ["module loading", "project initialized"]])

export const UPDATE_DATE_MUTATION_NAME = "updateData"

export class DataManager {
  get data(): InputData {
    return this.dataModule.data!!
  }

  get markerItems(): Array<Item | null> {
    return this.dataModule.markerItems
  }

  constructor(private readonly dataModule: DataModule) {
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

export interface GuideLineDescriptor {
  readonly item: Item
  readonly label: string
}

// name here is important because otherwise getModule from vuex-module-decorators will not work
@Module({name: "data"})
export class DataModule extends VuexModule {
  data: InputData | null = null
  recentlyUsedIdePort: number = 63342

  get markerItems(): Array<Item | null> {
    const items = this.data == null ? null : this.data!!.items
    if (items == null || items.length === 0) {
      return []
    }

    const markerItems = new Array(markerNames.length)
    // JS array is sparse and setting length doesn't pre-fill array
    markerItems.fill(null)
    itemLoop:  for (const item of items) {
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

    return markerItems
  }

  // don't forget to rename UPDATE_DATE_MUTATION_NAME if method will be renamed
  @Mutation
  updateData(data: InputData) {
    this.data = data
  }

  @Mutation
  updateRecentlyUsedIdePort(value: number) {
    this.recentlyUsedIdePort = value
  }
}

Vue.use(Vuex)

const vuexLocal = new VuexPersistence({
  storage: window.localStorage,
  key: "startup-visualizer"
})

export const store = new Vuex.Store({
  // strict: process.env.NODE_ENV !== "production",
  plugins: [vuexLocal.plugin],
  mutations: {
    RESTORE_MUTATION: vuexLocal.RESTORE_MUTATION,
  },
  modules: {
    data: DataModule,
  }
})
