// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Vue, Watch} from "vue-property-decorator"
import {AppState, mainModuleName} from "@/state/StateStorageManager"
import {DataManager} from "@/state/DataManager"
import {ChartManager} from "@/charts/ChartManager"

// @ts-ignore
@Component
export abstract class BaseChartComponent<T extends ChartManager> extends Vue {
  protected chartManager: T | null = null

  /** @final */
  get measurementData(): DataManager | null {
    return (this.$store.state[mainModuleName] as AppState).data
  }

  mounted() {
    this.chartManager = this.createChartManager()
    this.renderDataIfAvailable()
  }

  protected abstract createChartManager(): T

  @Watch("measurementData")
  // @ts-ignore
  private measurementDataChanged(): void {
    this.renderDataIfAvailable()
  }

  /** @final */
  protected renderDataIfAvailable(): void {
    const data = this.measurementData
    if (data != null) {
      const chartManager = this.chartManager
      if (chartManager != null) {
        chartManager.render(data)
      }
    }
  }
}
