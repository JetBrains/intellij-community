// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Vue, Watch} from "vue-property-decorator"
import {LineChartManager} from "./LineChartManager"
import {LineChartDataManager} from "@/aggregatedStats/LineChartDataManager"
import {AppStateModule} from "@/state/state"
import {getModule} from "vuex-module-decorators"
import {loadJson} from "@/httpUtil"
import {GroupedMetricResponse, InfoResponse, Machine, Metrics} from "@/aggregatedStats/model"
import {ClusteredChartManager} from "@/aggregatedStats/ClusteredChartManager"

@Component
export default class AggregatedStatsPage extends Vue {
  private readonly dataModule = getModule(AppStateModule, this.$store)
  private readonly lineChartManagers: Array<LineChartManager> = []
  private readonly clusteredChartManagers: Array<ClusteredChartManager> = []

  isFetching: boolean = false

  chartSettings = this.dataModule.chartSettings

  products: Array<string> = []
  machines: Array<Machine> = []

  private lastInfoResponse: InfoResponse | null = null

  loadData() {
    this.isFetching = true
    this.doLoadData(() => {
      this.isFetching = false
    })
  }

  isShowScrollbarXPreviewChanged(_value: boolean) {
    this.lineChartManagers.forEach(it => it.scrollbarXPreviewOptionChanged())
    this.dataModule.updateChartSettings(this.chartSettings)
  }

  private doLoadData(processed: () => void) {
    loadJson(new URL(`${this.chartSettings.serverUrl}/info`), processed, this.$notify)
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
  selectedProductChanged(product: string| null, _oldV: string): void {
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
      this.chartSettings.selectedMachine = undefined
    }
    else if (selectedMachine == null || !machines.find(it => it.id === selectedMachine)) {
      this.chartSettings.selectedMachine = machines[0].id
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

    this.loadClusteredChartData(product, machine)
    this.loadLineChartData(product, machine)
  }

  loadClusteredChartData(product: string, machineId: number): void {
    const url = new URL(`${this.chartSettings.serverUrl}/groupedMetrics`)
    url.searchParams.set("product", product)
    url.searchParams.set("machine", machineId.toString())
    loadJson(url, null, this.$notify)
      .then(data => {
        if (data != null) {
          this.renderClusteredCharts(data)
        }
      })
      .catch(e => {
        console.error(e)
      })
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
      chartManagers.push(new LineChartManager(this.$refs.durationEventChartContainer as HTMLElement, this.chartSettings, false))
      chartManagers.push(new LineChartManager(this.$refs.instantEventChartContainer as HTMLElement, this.chartSettings, true))
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

  private renderClusteredCharts(data: GroupedMetricResponse): void {
    let chartManagers = this.clusteredChartManagers
    if (chartManagers.length === 0) {
      chartManagers.push(new ClusteredChartManager(this.$refs.clusteredChartContainer as HTMLElement))
    }

    for (const chartManager of chartManagers) {
      try {
        chartManager.render(data)
      }
      catch (e) {
        console.error(e)
      }
    }
  }

  mounted() {
    const chartSettings = this.chartSettings
    if (!isEmpty(chartSettings.serverUrl)) {
      this.loadData()
    }
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