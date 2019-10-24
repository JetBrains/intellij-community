// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// https://github.com/vuejs/vue-class-component/issues/253#issuecomment-394401746
// initial value must be undefined, otherwise it will be reactive
// so, let's extract this code from bloody Vue component
import {ClusteredChartManager} from "@/aggregatedStats/ClusteredChartManager"
import {InfoResponse} from "@/aggregatedStats/model"
import {loadJson} from "@/httpUtil"
import {ElNotification} from "element-ui/types/notification"
import {ChartSettings, DEFAULT_AGGREGATION_OPERATOR} from "@/aggregatedStats/ChartSettings"

export interface DataRequest {
  product: string
  machine: Array<string>
  chartSettings: ChartSettings
  infoResponse: InfoResponse
}

export class AggregatedStatComponent {
  private readonly clusteredChartManagers: Array<ClusteredChartManager> = []

  lastInfoResponse: InfoResponse | null = null

  constructor() {
    Object.seal(this)
  }

  loadClusteredChartsData(product: string, machine: Array<string>, chartSettings: ChartSettings, refs: { [key: string]: HTMLElement }, notify: ElNotification): void {
    const chartManagers = this.clusteredChartManagers
    if (chartManagers.length === 0) {
      chartManagers.push(new ClusteredChartManager(refs.clusteredDurationChartContainer))
      chartManagers.push(new ClusteredChartManager(refs.clusteredInstantChartContainer))
    }

    chartManagers[0].setData(loadJson(this.createGroupedMetricUrl(product, machine, chartSettings, false), null, notify))
    chartManagers[1].setData(loadJson(this.createGroupedMetricUrl(product, machine, chartSettings, true), null, notify))
  }

  public static expandMachine(request: DataRequest): string {
    if (request.machine.length > 1) {
      return request.machine.join(",")
    }

    const groupName = request.machine[0]
    const infoResponse = request.infoResponse
    for (const machineGroup of infoResponse.productToMachine[request.product]) {
      if (machineGroup.name === groupName) {
        return machineGroup.children.map(it => it.name).join(",")
      }
    }
    return groupName
  }

  private createGroupedMetricUrl(product: string, machine: Array<string>, chartSettings: ChartSettings, isInstant: boolean): string {
    let operator = chartSettings.aggregationOperator || DEFAULT_AGGREGATION_OPERATOR
    let operatorArg = 0
    if (operator === "median") {
      operator = "quantile"
      operatorArg = 50
    }
    else if (operator === "quantile") {
      operatorArg = chartSettings.quantile
    }

    let result = `${chartSettings.serverUrl}/api/v1/groupedMetrics/` +
      `product=${encodeURIComponent(product)}` +
      `&machine=${encodeURIComponent(AggregatedStatComponent.expandMachine({product, machine, infoResponse: this.lastInfoResponse!!, chartSettings}))}` +
      `&operator=${operator}`
    if (operatorArg !== 0) {
      result += `&operatorArg=${operatorArg}`
    }
    result += `&eventType=${isInstant ? "i" : "d"}`
    return result
  }

  dispose() {
    this.lastInfoResponse = null

    for (const chartManager of this.clusteredChartManagers) {
      chartManager.dispose()
    }
    this.clusteredChartManagers.length = 0
  }
}