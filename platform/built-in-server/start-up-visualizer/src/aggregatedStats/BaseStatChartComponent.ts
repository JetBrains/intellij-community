// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Prop, Vue, Watch} from "vue-property-decorator"
import {StatChartManager} from "@/charts/ChartManager"
import PQueue from "p-queue"
import {ChartSettings} from "@/aggregatedStats/ChartSettings"
import {debounce} from "debounce"
import {DataRequest} from "@/aggregatedStats/model"
import {loadJson} from "@/httpUtil"

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
  private readonly queue = new PQueue({concurrency: 1})
  private dataRequestCounter = 0
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
        this.disposeChart("hot reload")
      })
    }
  }

  mounted() {
    const dataRequest = this.dataRequest
    if (dataRequest != null) {
      this.loadDataAfterDelay()
    }

    this.chartManager = this.createChartManager()
  }

  beforeDestroy() {
    this.disposeChart("before destroy")
  }

  protected abstract createChartManager(): T

  protected loadData<D>(url: string, consumer: (data: D, chartManager: T) => void): void {
    const onFinish = () => {
      this.isLoading = false
    }
    this.isLoading = true

    this.dataRequestCounter++
    const dataRequestCounter = this.dataRequestCounter
    this.queue.add(() => {
      loadJson(url, null)
        .then(data => {
          if (data == null || dataRequestCounter !== this.dataRequestCounter) {
            return
          }

          const chartManager = this.chartManager
          if (chartManager == null) {
            console.log("already disposed")
            return
          }

          consumer(data, chartManager)
        })
    })
      .then(onFinish, onFinish)
  }

  private disposeChart(reason: string) {
    const chartManager = this.chartManager
    if (chartManager != null) {
      console.log(`dispose chart manager: ${reason}`)
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
