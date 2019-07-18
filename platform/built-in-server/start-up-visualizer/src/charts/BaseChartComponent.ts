// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Vue, Watch} from "vue-property-decorator"
import {AppState, mainModuleName} from "@/state/StateStorageManager"
import {DataManager} from "@/state/DataManager"
import {ChartManager} from "@/charts/ChartManager"
import {Notification} from "element-ui"

// @ts-ignore
@Component
export abstract class BaseChartComponent<T extends ChartManager> extends Vue {
  protected chartManager: T | null = null

  /** @final */
  get measurementData(): DataManager | null {
    return (this.$store.state[mainModuleName] as AppState).data
  }

  mounted() {
    this.renderDataIfAvailable()
  }

  protected abstract createChartManager(): Promise<T>

  @Watch("measurementData")
  /** @final */
  protected renderDataIfAvailable(): void {
    const data = this.measurementData
    if (data == null) {
      // yes, do not re-render as empty - null value not expected to be set in valid cases
      return
    }

    let chartManager = this.chartManager
    if (chartManager == null) {
      this.createChartManager()
        .then(chartManager => {
          this.chartManager = chartManager
          chartManager.render(data)
        })
        .catch(e => {
          console.log(e)
          Notification.error(e)
        })
    }
    else {
      chartManager.render(data)
    }
  }

  beforeDestroy() {
    const chartManager = this.chartManager
    if (chartManager != null) {
      this.chartManager = null
      chartManager.dispose()
    }
  }
}
