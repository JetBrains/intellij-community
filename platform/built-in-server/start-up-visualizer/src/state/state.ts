// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import Vue from "vue"
import Vuex from "vuex"
import {Module, Mutation, VuexModule} from "vuex-module-decorators"
import VuexPersistence from "vuex-persist"
import {InputData} from "./data"
import {AppState, defaultIdePort, mainModuleName, StateStorageManager} from "@/state/StateStorageManager"
import {DataManager} from "@/state/DataManager"
import {ChartSettings} from "@/aggregatedStats/ChartSettings"

const stateStorageManager = new StateStorageManager()

// name here is important because otherwise getModule from vuex-module-decorators will not work
@Module({name: mainModuleName})
export class AppStateModule extends VuexModule implements AppState {
  data: DataManager | null = null
  recentlyUsedIdePort: number = defaultIdePort

  chartSettings = new ChartSettings()

  @Mutation
  updateData(data: InputData | null) {
    this.data = stateStorageManager.createDataManager(data)
  }

  @Mutation
  updateRecentlyUsedIdePort(value: number) {
    this.recentlyUsedIdePort = value
  }

  @Mutation
  updateChartSettings(chartSettings: ChartSettings) {
    this.chartSettings = chartSettings
  }
}

Vue.use(Vuex)

const vuexLocal = new VuexPersistence({
  storage: window.localStorage,
  saveState: function (_key: string, moduleNameToState: any, _storage) {
    stateStorageManager.saveState(moduleNameToState)
  },
  restoreState: function (_key: string, _storage) {
    return stateStorageManager.restoreState()
  },
})

export const store = new Vuex.Store({
  plugins: [vuexLocal.plugin],
  mutations: {
    RESTORE_MUTATION: vuexLocal.RESTORE_MUTATION,
  },
  modules: {
    [mainModuleName]: AppStateModule,
  }
})
