// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Vue, Watch} from "vue-property-decorator"
import {AggregatedStatsChartManager} from "./AggregatedStatsChartManager"
import {AggregatedDataManager} from "@/aggregatedStats/AggregatedDataManager"
import {AppStateModule} from "@/state/state"
import {getModule} from "vuex-module-decorators"
import {InfoResponse} from "@/aggregatedStats/ChartSettings"
import {loadJson} from "@/httpUtil"
import {Metrics} from "@/aggregatedStats/model"

@Component
export default class AggregatedStatsChart extends Vue {
  private readonly dataModule = getModule(AppStateModule, this.$store)
  private readonly chartManagers: Array<AggregatedStatsChartManager> = []

  isFetching: boolean = false

  chartSettings = this.dataModule.chartSettings

  products: Array<string> = []
  machines: Array<string> = []

  private lastInfoResponse: InfoResponse | null = null

  loadData() {
    this.isFetching = true
    this.doLoadData(() => {
      this.isFetching = false
    })
  }

  isShowScrollbarXPreviewChanged(_value: boolean) {
    this.chartManagers.forEach(it => it.scrollbarXPreviewOptionChanged())
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
      this.machines = infoResponse.productToMachineNames[product] || []
    }
    else {
      this.machines = []
    }

    let selectedMachine = this.chartSettings.selectedMachine
    const machines = this.machines
    if (machines.length === 0) {
      this.chartSettings.selectedMachine = ""
    }
    else if (selectedMachine == null || selectedMachine.length === 0 || !machines.includes(selectedMachine)) {
      this.chartSettings.selectedMachine = machines[0]
    }
  }

  @Watch("chartSettings.selectedMachine")
  selectedMachineChanged(machine: string | null, _oldV: string): void {
    console.log("machine changed", machine, _oldV)
    if (machine == null || machine.length === 0) {
      return
    }

    const product = this.chartSettings.selectedProduct
    if (product == null || product.length === 0) {
      return
    }

    const url = new URL(`${this.chartSettings.serverUrl}/metrics`)
    url.searchParams.set("product", product)
    url.searchParams.set("machine", machine)
    const infoResponse = this.lastInfoResponse!!
    loadJson(url, () => {}, this.$notify)
      .then(data => {
        if (data != null) {
          this.renderData(data, infoResponse)
        }
      })
  }

  private renderData(data: Array<Metrics>, infoResponse: InfoResponse): void {
    let chartManagers = this.chartManagers
    if (chartManagers.length === 0) {
      chartManagers.push(new AggregatedStatsChartManager(this.$refs.durationEventChartContainer as HTMLElement, this.chartSettings, false))
      chartManagers.push(new AggregatedStatsChartManager(this.$refs.instantEventChartContainer as HTMLElement, this.chartSettings, true))
    }

    const dataManager = new AggregatedDataManager(data, infoResponse)
    for (const chartManager of chartManagers) {
      try {
        chartManager.render(dataManager)
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
    for (const chartManager of this.chartManagers) {
      chartManager.dispose()
    }
    this.chartManagers.length = 0
  }
}

function isEmpty(v: string | null): boolean {
  return v == null || v.length === 0
}