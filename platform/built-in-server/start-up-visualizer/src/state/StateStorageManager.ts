// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {DataManager} from "@/state/DataManager"
import {InputData} from "@/state/data"
import {ChartSettings} from "@/aggregatedStats/ChartSettings"

export interface AppState {
  data: DataManager | null
  recentlyUsedIdePort: number

  chartSettings: ChartSettings
}

export const mainModuleName = "app"
export const markerNames = ["app initialized callback", "module loading"]
const mainModuleStorageKey = "startup-visualizer-v2"
export const defaultIdePort = 63342

export class StateStorageManager {
  private readonly storage = window.localStorage

  createDataManager(data: InputData | null): DataManager | null {
    if (data == null) {
      return null
    }

    Object.seal(data)
    const dataManager = new DataManager(data)
    Object.seal(dataManager)
    return dataManager
  }

  // moduleNameToState here it is object where key it is our vuex module name and value the module's state
  // just use module name as storage item key - if later module will be renamed, it can be handled here
  saveState(moduleNameToState: any) {
    const state: AppState | null = moduleNameToState[mainModuleName]
    if (state == null) {
      this.storage.removeItem(mainModuleStorageKey)
    }
    else {
      this.storage.setItem(mainModuleStorageKey, JSON.stringify(state))
    }
  }

  restoreState() {
    const rawState = this.storage.getItem(mainModuleStorageKey)
    const state: AppState = {
      data: null,
      recentlyUsedIdePort: defaultIdePort,
      chartSettings: new ChartSettings(),
    }
    if (rawState == null) {
      return {
        [mainModuleName]: state
      }
    }

    const jsonData = JSON.parse(rawState)
    Object.assign(state, jsonData)
    const data = jsonData.data
    if (data != null) {
      state.data = this.createDataManager(data)
    }
    return {[mainModuleName]: state}
  }
}