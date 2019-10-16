// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Vue, Watch} from "vue-property-decorator"
import {AppStateModule} from "@/state/state"
import {getModule} from "vuex-module-decorators"
import {loadJson} from "@/httpUtil"
import {InfoResponse, Machine} from "@/aggregatedStats/model"
import {debounce} from "debounce"
import {AggregatedStatComponent} from "@/aggregatedStats/AggregatedStatComponent"

@Component
export default class AggregatedStatsPage extends Vue {
  private readonly dataModule = getModule(AppStateModule, this.$store)

  private readonly helper!: AggregatedStatComponent

  created() {
    // @ts-ignore
    // noinspection JSConstantReassignment
    this.helper = new AggregatedStatComponent()

    if (module.hot != null) {
      module.hot.dispose(() => {
        console.log("dispose charts on hot reload")
        this.helper.dispose()
      })
    }
  }

  chartSettings = this.dataModule.chartSettings

  products: Array<string> = []
  machines: Array<Machine> = []

  aggregationOperators: Array<string> = ["median", "min", "max", "quantile"]

  isFetching: boolean = false

  private loadDataAfterDelay = debounce(() => {
    this.loadData()
  }, 1000)

  isShowScrollbarXPreviewChanged(_value: boolean) {
    this.helper.showScrollbarXPreviewChanged()
    this.dataModule.updateChartSettings(this.chartSettings)
  }

  loadData() {
    this.isFetching = true
    loadJson(`${this.chartSettings.serverUrl}/api/v1/info`, null, this.$notify)
      .then((data: InfoResponse | null) => {
        if (data == null) {
          return
        }

        this.helper.lastInfoResponse = data
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
        console.log("update product on info response", selectedProduct)
        const oldSelectedMachine = this.chartSettings.selectedMachine
        this.applyChangedProduct(selectedProduct, data)
        const newSelectedMachine = this.chartSettings.selectedMachine
        if (oldSelectedMachine !== newSelectedMachine) {
          this.selectedMachineChanged(newSelectedMachine, oldSelectedMachine)
        }

        this.isFetching = false
      })
      .catch(e => {
        this.isFetching = false
        console.error(e)
      })
  }

  @Watch("chartSettings.selectedProduct")
  selectedProductChanged(product: string | null, _oldV: string): void {
    console.log("product changed", product, _oldV)

    const infoResponse = this.helper.lastInfoResponse
    if (infoResponse != null) {
      this.applyChangedProduct(product, infoResponse)
    }

    this.dataModule.updateChartSettings(this.chartSettings)
  }

  private applyChangedProduct(product: string | null, infoResponse: InfoResponse) {
    if (product != null && product.length > 0) {
      // later maybe will be more info for machine, so, do not use string instead of Machine
      this.machines = infoResponse.productToMachine[product].map(name => {
        return {id: name, name}
      }) || []
    }
    else {
      this.machines = []
    }

    let selectedMachine = this.chartSettings.selectedMachine
    const machines = this.machines
    if (machines.length === 0) {
      selectedMachine = ""
      this.chartSettings.selectedMachine = selectedMachine
    }
    else if (selectedMachine == null || !machines.find(it => it.id === selectedMachine)) {
      selectedMachine = machines[0].id
    }

    if (this.chartSettings.selectedMachine === selectedMachine) {
      // data will be reloaded on machine change, but if product changed but machine remain the same, data reloading must be triggered here
      if (product != null && selectedMachine != null) {
        this.loadClusteredChartsData(product)
        this.helper.loadLineChartData(product, selectedMachine, this.chartSettings, this.$refs as any, this.$notify)
      }
    }
    else {
      this.chartSettings.selectedMachine = selectedMachine
    }
  }

  @Watch("chartSettings.selectedMachine")
  selectedMachineChanged(machine: string | null | undefined, _oldV: string): void {
    this.dataModule.updateChartSettings(this.chartSettings)

    console.log("machine changed", machine, _oldV)
    if (machine == null) {
      return
    }

    const product = this.chartSettings.selectedProduct
    if (product == null || product.length === 0) {
      return
    }

    this.loadClusteredChartsData(product)
    this.helper.loadLineChartData(product, machine, this.chartSettings, this.$refs as any, this.$notify)
  }

  private loadClusteredChartsData(product: string): void {
    const machine = this.chartSettings.selectedMachine
    if (machine != null) {
      this.helper.loadClusteredChartsData(product, machine, this.chartSettings, this.$refs as any, this.$notify)
    }
  }

  @Watch("chartSettings.serverUrl")
  serverUrlChanged(newV: string | null, _oldV: string) {
    if (!isEmpty(newV)) {
      this.loadDataAfterDelay()
    }

    this.dataModule.updateChartSettings(this.chartSettings)
  }

  @Watch("chartSettings.aggregationOperator")
  aggregationOperatorChanged(newV: string | null, _oldV: string) {
    this.dataModule.updateChartSettings(this.chartSettings)

    if (isEmpty(newV)) {
      return
    }

    this.reloadClusteredDataIfPossible()
  }

  private reloadClusteredDataIfPossible() {
    const product = this.chartSettings.selectedProduct
    if (product == null || product.length === 0) {
      return
    }
    this.loadClusteredChartsData(product)
  }

  private reloadClusteredDataIfPossibleAfterDelay = debounce(() => {
    this.reloadClusteredDataIfPossible()
  }, 300)

  @Watch("chartSettings.quantile")
  quantileChanged(_newV: number, _oldV: number) {
    console.log("quantile changed", _newV)
    this.reloadClusteredDataIfPossibleAfterDelay()

    this.dataModule.updateChartSettings(this.chartSettings)
  }

  mounted() {
    const serverUrl = this.chartSettings.serverUrl
    if (!isEmpty(serverUrl)) {
      this.loadData()
    }
  }

  beforeDestroy() {
    const helper = this.helper
    helper.dispose()
  }
}

function isEmpty(v: string | null): boolean {
  return v == null || v.length === 0
}