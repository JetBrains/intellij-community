// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Prop, Vue, Watch} from "vue-property-decorator"
import {StatChartManager} from "@/charts/ChartManager"
import PQueue from "p-queue"
import {ChartSettings} from "@/aggregatedStats/ChartSettings"
import {debounce} from "debounce"
import {DataRequest} from "@/aggregatedStats/model"

// @ts-ignore
@Component
export abstract class BaseStatChartComponent<T extends StatChartManager> extends Vue {
  @Prop({type: Array, required: true})
  metrics!: Array<string>

  @Prop(Object)
  dataRequest!: DataRequest | null

  @Prop({type: Object, required: true})
  chartSettings!: ChartSettings

  isLoading: boolean = false

  protected chartManager!: T | null

  // ensure that if several tasks were added, the last one will set the chart data
  protected readonly queue = new PQueue({concurrency: 1})
  protected dataRequestCounter = 0
  private pendingDataRequest: DataRequest | null = null

  // for some reason doesn't work (this is not correct) if doReload inlined
  private readonly loadDataAfterDelayFunction = debounce(() => this.doReload(), 100)

  protected loadDataAfterDelay() {
    this.pendingDataRequest = this.dataRequest
    this.loadDataAfterDelayFunction()
  }

  created() {
    Object.seal(this.queue)
    Object.seal(this.loadDataAfterDelayFunction)

    if (module.hot != null) {
      module.hot.dispose(() => {
        const chartManager = this.chartManager
        if (chartManager != null) {
          console.log("dispose chart on hot reload")
          chartManager.dispose()
        }
      })
    }
  }

  mounted() {
    const dataRequest = this.dataRequest
    if (dataRequest != null) {
      this.loadDataAfterDelay()
    }
  }

  beforeDestroy() {
    const chartManager = this.chartManager
    if (chartManager != null) {
      console.log("unset chart manager")
      this.chartManager = null
      chartManager.dispose()
    }
  }

  @Watch("dataRequest")
  dataRequestChanged(): void {
    this.loadDataAfterDelay()
  }

  protected abstract reloadData(request: DataRequest): void

  private doReload() {
    const lastDataRequest = this.pendingDataRequest
    if (lastDataRequest != null) {
      this.pendingDataRequest = null
      this.reloadData(lastDataRequest)
    }
  }
}
