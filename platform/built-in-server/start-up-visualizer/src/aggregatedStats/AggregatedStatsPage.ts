// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Vue, Watch} from "vue-property-decorator"
import {LineChartManager} from "./LineChartManager"
import {LineChartDataManager} from "@/aggregatedStats/LineChartDataManager"
import {AppStateModule} from "@/state/state"
import {getModule} from "vuex-module-decorators"
import {loadJson} from "@/httpUtil"
import {InfoResponse, Machine, Metrics} from "@/aggregatedStats/model"
import {ClusteredChartManager} from "@/aggregatedStats/ClusteredChartManager"
import {debounce} from "debounce"

@Component
export default class AggregatedStatsPage extends Vue {
  private readonly dataModule = getModule(AppStateModule, this.$store)
  private readonly lineChartManagers: Array<LineChartManager> = []
  private readonly clusteredChartManagers: Array<ClusteredChartManager> = []

  chartSettings = this.dataModule.chartSettings

  products: Array<string> = []
  machines: Array<Machine> = []

  aggregationOperators: Array<string> = ["median", "min", "max"]

  private lastInfoResponse: InfoResponse | null = null

  private loadData = debounce(() => {
    this.doLoadData()
  }, 300)

  isShowScrollbarXPreviewChanged(_value: boolean) {
    this.lineChartManagers.forEach(it => it.scrollbarXPreviewOptionChanged())
    this.dataModule.updateChartSettings(this.chartSettings)
  }

  private doLoadData() {
    loadJson(new URL(`${this.chartSettings.serverUrl}/info`), null, this.$notify)
      .then((data: InfoResponse | null) => {
        if (data == null) {
          return
        }

        this.lastInfoResponse = data
        this.products = data.productNames
        let selectedProduct = this.chartSettings.selectedProduct
        if (this.products.length === 0) {
          selectedProduct = ""
        }
        else if (selectedProduct == null || selectedProduct.length === 0 || !this.products.includes(selectedProduct)) {
          selectedProduct = this.products[0]
        }
        this.chartSettings.selectedProduct = selectedProduct
        // not called by Vue for some reasons
        this.selectedProductChanged(selectedProduct, "")
        this.selectedMachineChanged(this.chartSettings.selectedMachine, "")
      })
      .catch(e => {
        console.error(e)
      })
  }

  @Watch("chartSettings.selectedProduct")
  selectedProductChanged(product: string | null, _oldV: string): void {
    console.log("product changed", product, _oldV)
    const infoResponse = this.lastInfoResponse
    if (infoResponse == null) {
      return
    }

    if (product != null && product.length > 0) {
      this.machines = infoResponse.productToMachine[product] || []
    }
    else {
      this.machines = []
    }

    let selectedMachine = this.chartSettings.selectedMachine
    const machines = this.machines
    if (machines.length === 0) {
      selectedMachine = null
      this.chartSettings.selectedMachine = null
    }
    else if (selectedMachine == null || !machines.find(it => it.id === selectedMachine)) {
      selectedMachine = machines[0].id
    }

    if (this.chartSettings.selectedMachine === selectedMachine) {
      // data will be reloaded on machine change, but if product changed but machine remain the same, data reloading must be triggered here
      if (product != null && selectedMachine != null) {
        this.loadClusteredChartsData(product, selectedMachine)
        this.loadLineChartData(product, selectedMachine)
      }
    }
    else {
      this.chartSettings.selectedMachine = selectedMachine
    }
  }

  @Watch("chartSettings.selectedMachine")
  selectedMachineChanged(machine: number | null | undefined, _oldV: string): void {
    console.log("machine changed", machine, _oldV)
    if (machine == null) {
      return
    }

    const product = this.chartSettings.selectedProduct
    if (product == null || product.length === 0) {
      return
    }

    this.loadClusteredChartsData(product, machine)
    this.loadLineChartData(product, machine)
  }

  private loadClusteredChartsData(product: string, machineId: number): void {
    const chartManagers = this.clusteredChartManagers
    if (chartManagers.length === 0) {
      chartManagers.push(new ClusteredChartManager(this.$refs.clusteredDurationChartContainer as HTMLElement))
      chartManagers.push(new ClusteredChartManager(this.$refs.clusteredInstantChartContainer as HTMLElement))
    }

    chartManagers[0].setData(loadJson(this.createGroupedMetricUrl(product, machineId, false), null, this.$notify))
    chartManagers[1].setData(loadJson(this.createGroupedMetricUrl(product, machineId, true), null, this.$notify))
  }

  createGroupedMetricUrl(product: string, machineId: number, isInstant: boolean): URL {
    const url = new URL(`${this.chartSettings.serverUrl}/groupedMetrics`)
    url.searchParams.set("product", product)
    url.searchParams.set("machine", machineId.toString())
    url.searchParams.set("eventType", isInstant ? "i" : "d")
    return url
  }

  // noinspection DuplicatedCode
  loadLineChartData(product: string, machineId: number): void {
    const url = new URL(`${this.chartSettings.serverUrl}/metrics`)
    url.searchParams.set("product", product)
    url.searchParams.set("machine", machineId.toString())
    const infoResponse = this.lastInfoResponse!!
    loadJson(url, null, this.$notify)
      .then(data => {
        if (data != null) {
          this.renderLineCharts(data, infoResponse)
        }
      })
      .catch(e => {
        console.error(e)
      })
  }

  private renderLineCharts(data: Array<Metrics>, infoResponse: InfoResponse): void {
    let chartManagers = this.lineChartManagers
    if (chartManagers.length === 0) {
      chartManagers.push(new LineChartManager(this.$refs.lineDurationChartContainer as HTMLElement, this.chartSettings, false))
      chartManagers.push(new LineChartManager(this.$refs.lineInstantChartContainer as HTMLElement, this.chartSettings, true))
    }

    const dataManager = new LineChartDataManager(data, infoResponse)
    for (const chartManager of chartManagers) {
      try {
        chartManager.render(dataManager)
      }
      catch (e) {
        console.error(e)
      }
    }
  }

  @Watch("chartSettings.serverUrl")
  serverUrlChanged(newV: string | null, _oldV: string) {
    if (!isEmpty(newV)) {
      this.loadData()
    }
  }

  mounted() {
    this.serverUrlChanged(this.chartSettings.serverUrl, "")
  }

  beforeDestroy() {
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

function isEmpty(v: string | null): boolean {
  return v == null || v.length === 0
}