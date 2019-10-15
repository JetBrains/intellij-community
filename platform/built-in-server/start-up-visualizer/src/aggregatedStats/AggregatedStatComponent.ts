// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// https://github.com/vuejs/vue-class-component/issues/253#issuecomment-394401746
// initial value must be undefined, otherwise it will be reactive
// so, let's extract this code from bloody Vue component
import {LineChartManager} from "@/aggregatedStats/LineChartManager"
import {ClusteredChartManager} from "@/aggregatedStats/ClusteredChartManager"
import {InfoResponse} from "@/aggregatedStats/model"
import {loadJson} from "@/httpUtil"
import {ElNotification} from "element-ui/types/notification"
import {ChartSettings, DEFAULT_AGGREGATION_OPERATOR} from "@/aggregatedStats/ChartSettings"

export class AggregatedStatComponent {
  private readonly lineChartManagers: Array<LineChartManager> = []
  private readonly clusteredChartManagers: Array<ClusteredChartManager> = []

  lastInfoResponse: InfoResponse | null = null

  constructor() {
    Object.seal(this)
  }

  showScrollbarXPreviewChanged() {
    this.lineChartManagers.forEach(it => it.scrollbarXPreviewOptionChanged())
  }

  loadClusteredChartsData(product: string, machine: string, chartSettings: ChartSettings, refs: { [key: string]: HTMLElement }, notify: ElNotification): void {
    const chartManagers = this.clusteredChartManagers
    if (chartManagers.length === 0) {
      chartManagers.push(new ClusteredChartManager(refs.clusteredDurationChartContainer))
      chartManagers.push(new ClusteredChartManager(refs.clusteredInstantChartContainer))
    }

    chartManagers[0].setData(loadJson(this.createGroupedMetricUrl(product, machine, chartSettings, false), null, notify))
    chartManagers[1].setData(loadJson(this.createGroupedMetricUrl(product, machine, chartSettings, true), null, notify))
  }

  private createGroupedMetricUrl(product: string, machineId: string, chartSettings: ChartSettings, isInstant: boolean): string {
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
      `&machine=${machineId}` +
      `&operator=${operator}`
    if (operatorArg !== 0) {
      result += `&operatorArg=${operatorArg}`
    }
    result += `&eventType=${isInstant ? "i" : "d"}`
    return result
  }

  loadLineChartData(product: string, machine: string, chartSettings: ChartSettings, refs: { [key: string]: HTMLElement }, notify: ElNotification): void {
    const chartManagers = this.lineChartManagers
    if (chartManagers.length === 0) {
      chartManagers.push(new LineChartManager(refs.lineDurationChartContainer, chartSettings, false))
      chartManagers.push(new LineChartManager(refs.lineInstantChartContainer, chartSettings, true))
    }

    const productAndMachineParams = `product=${encodeURIComponent(product)}&machine=${encodeURIComponent(machine)}`
    const url = `${chartSettings.serverUrl}/api/v1/metrics/` + productAndMachineParams
    const infoResponse = this.lastInfoResponse!!

    const reportUrlPrefix = `${chartSettings.serverUrl}/api/v1/report/` + productAndMachineParams

    chartManagers[0].setData(loadJson(`${url}&eventType=d`, null, notify), infoResponse, reportUrlPrefix)
    chartManagers[1].setData(loadJson(`${url}&eventType=i`, null, notify), infoResponse, reportUrlPrefix)
  }

  dispose() {
    this.lastInfoResponse = null

    for (const chartManager of this.clusteredChartManagers) {
      chartManager.dispose()
    }
    this.clusteredChartManagers.length = 0

    for (const chartManager of this.lineChartManagers) {
      chartManager.dispose()
    }
    this.lineChartManagers.length = 0
  }
}