<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div v-loading="isLoading" class="aggregatedChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Vue, Watch} from "vue-property-decorator"
  import {LineChartManager} from "@/aggregatedStats/LineChartManager"
  import {ChartSettings} from "@/aggregatedStats/ChartSettings"
  import {loadJson} from "@/httpUtil"
  import {AggregatedStatComponent, DataRequest} from "@/aggregatedStats/AggregatedStatComponent"
  import {SortedByCategory, SortedByDate} from "@/aggregatedStats/ChartConfigurator"
  import PQueue from "p-queue"
  import {DataQuery} from "@/aggregatedStats/model"
  import {debounce} from "debounce"

  @Component
  export default class LineChartComponent extends Vue {
    @Prop({type: String, required: true})
    type!: "duration" | "instant"

    @Prop(String)
    order!: "date" | "buildNumber"

    @Prop({type: Array, required: true})
    metrics!: Array<string>

    @Prop(Object)
    dataRequest!: DataRequest | null

    @Prop({type: Object, required: true})
    chartSettings!: ChartSettings

    private chartManager: LineChartManager | null = null

    // ensure that if several tasks were added, the last one will set the chart data
    private readonly queue = new PQueue({concurrency: 1})

    private dataRequestCounter = 0

    isLoading: boolean = false

    private pendingDataRequest: DataRequest | null = null

    // for some reason doesn't work (this is not correct) if doReload inlined
    private loadDataAfterDelay = debounce(() => this.doReload(), 100)

    private doReload() {
      const lastDataRequest = this.pendingDataRequest
      console.log(this, "WTF", lastDataRequest)

      if (lastDataRequest != null) {
        this.pendingDataRequest = null
        this.reloadData(lastDataRequest)
      }
    }

    created() {
      Object.seal(this.queue)
    }

    mounted() {
      const dataRequest = this.dataRequest
      if (dataRequest != null) {
        this.pendingDataRequest = dataRequest
        this.loadDataAfterDelay()
      }
    }

    @Watch("chartSettings.showScrollbarXPreview")
    chartSettingsChanged(): void {
      const chartManager = this.chartManager
      if (chartManager != null) {
        chartManager.scrollbarXPreviewOptionChanged(this.chartSettings)
      }
    }

    @Watch("chartSettings.granularity")
    granularityChanged() {
      this.pendingDataRequest = this.dataRequest
      this.loadDataAfterDelay()
    }

    @Watch("dataRequest")
    dataRequestChanged(request: DataRequest | null): void {
      console.log(`dataRequestChanged: LineChartComponent(type=${this.type}, order=${this.order})`, request)
      if (request != null) {
        this.pendingDataRequest = request
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

    private reloadData(request: DataRequest) {
      const dataQuery: DataQuery = {
        fields: ["generated_time", "build_c1", "build_c2", "build_c3"].concat(this.metrics),
        filters: [
          {field: "product", value: request.product},
          {field: "machine", value: AggregatedStatComponent.expandMachineAsFilterValue(request)},
        ],
        order: this.order === "date" ? ["generated_time"] : ["build_c1", "build_c2", "build_c3", "generated_time"],
      }

      const chartSettings = request.chartSettings
      let granularity = chartSettings.granularity
      if (granularity == null || granularity == null) {
        granularity = "hour"
      }

      if (granularity !== "as is" && this.order === "date") {
        dataQuery.fields = ["build_c1", "build_c2", "build_c3"].map(it => {
          return {name: it, sql: `anyHeavy(${it})`}
        })
        dataQuery.fields = dataQuery.fields.concat(this.metrics)
        dataQuery.aggregator = "medianTDigest"

        let sql: string
        if (granularity === "hour") {
          sql = "toStartOfHour(generated_time)"
        }
        else if (granularity === "day") {
          sql = "toStartOfDay(generated_time)"
        }
        else if (granularity === "week") {
          // Monday is the first day of week
          sql = "toStartOfWeek(generated_time, 1)"
        }
        else {
          sql = "toStartOfMonth(generated_time)"
        }

        dataQuery.dimensions = [
          {name: "t", sql}
        ]
        dataQuery.order = ["t"]
      }

      const url = `${chartSettings.serverUrl}/api/v1/metrics/` + encodeURIComponent(JSON.stringify(dataQuery))
      const reportUrlPrefix = `${chartSettings.serverUrl}/api/v1/report/` + `product=${encodeURIComponent(request.product)}&machine=${encodeURIComponent(AggregatedStatComponent.expandMachine(request))}`

      const onFinish = () => {
        this.isLoading = false
      }
      this.isLoading = true

      this.dataRequestCounter++
      const dataRequestCounter = this.dataRequestCounter
      this.queue.add(() => {
        loadJson(url, null, this.$notify)
          .then(data => {
            if (data == null || dataRequestCounter !== this.dataRequestCounter) {
              return
            }

            const chartManager = this.getOrCreateChartManager()
            chartManager.reportUrlPrefix = reportUrlPrefix
            chartManager.render(data)
          })
      })
      .then(onFinish, onFinish)
    }

    private getOrCreateChartManager() {
      let chartManager = this.chartManager
      if (chartManager != null) {
        return chartManager
      }

      const configurator = this.order === "date" ? new SortedByDate() : new SortedByCategory()
      chartManager = new LineChartManager(this.$refs.chartContainer as HTMLElement, this.chartSettings || new ChartSettings(), this.type === "instant", configurator)

      for (const key of this.metrics) {
        chartManager.metricDescriptors.push({
          key,
          name: (key.endsWith("_d") || key.endsWith("_i")) ? key.substring(0, key.length - 2) : key,
          hiddenByDefault: false,
        })
      }

      this.chartManager = chartManager
      return chartManager
    }
  }
</script>