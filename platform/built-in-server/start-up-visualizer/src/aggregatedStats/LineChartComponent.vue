<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div v-loading="isLoading" class="aggregatedChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Vue, Watch} from "vue-property-decorator"
  import {LineChartManager} from "@/aggregatedStats/LineChartManager"
  import {ChartSettings} from "@/aggregatedStats/ChartSettings"
  import {AppState, mainModuleName} from "@/state/StateStorageManager"
  import {loadJson} from "@/httpUtil"
  import {AggregatedStatComponent, DataRequest} from "@/aggregatedStats/AggregatedStatComponent"
  import {SortedByCategory, SortedByDate} from "@/aggregatedStats/ChartConfigurator"
  import PQueue from "p-queue"

  @Component
  export default class LineChartComponent extends Vue {
    @Prop(String)
    type!: "duration" | "instant"

    @Prop(String)
    order!: "date" | "buildNumber"

    @Prop(Object)
    dataRequest!: DataRequest | null

    private chartManager: LineChartManager | null = null

    // ensure that if several tasks were added, the last one will set the chart data
    private readonly queue = new PQueue({concurrency: 1})

    private dataRequestCounter = 0

    isLoading: boolean = false

    get chartSettings(): ChartSettings | null {
      return (this.$store.state[mainModuleName] as AppState).chartSettings
    }

    created() {
      Object.seal(this.queue)
    }

    mounted() {
      const configurator = this.order == "date" ? new SortedByDate() : new SortedByCategory()
      this.chartManager = new LineChartManager(this.$refs.chartContainer as HTMLElement, this.chartSettings || new ChartSettings(), this.type === "instant", configurator)
      if (this.dataRequest != null) {
        this.reloadData(this.dataRequest)
      }
    }

    @Watch("chartSettings")
    chartSettingsChanged(value: ChartSettings): void {
      this.chartManager!!.scrollbarXPreviewOptionChanged(value)
    }

    @Watch("dataRequest")
    dataRequestChanged(request: DataRequest | null): void {
      console.log(`dataRequestChanged: LineChartComponent(type=${this.type}, order=${this.order})`, request)
      if (request != null) {
        this.reloadData(request)
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
      if (this.chartManager == null) {
        console.log("skip data reloading: chartManager is null", this)
        return
      }

      const productAndMachineParams = `product=${encodeURIComponent(request.product)}&machine=${encodeURIComponent(AggregatedStatComponent.expandMachine(request))}`
      const url = `${request.chartSettings.serverUrl}/api/v1/metrics/` + productAndMachineParams
      const reportUrlPrefix = `${request.chartSettings.serverUrl}/api/v1/report/` + productAndMachineParams

      const onFinish = () => {
        this.isLoading = false
      }
      this.isLoading = true

      this.dataRequestCounter++
      const dataRequestCounter = this.dataRequestCounter
      this.queue.add(() => {
        loadJson(`${url}&eventType=${this.type[0]}&order=${this.order[0]}`, null, this.$notify)
          .then(data => {
            if (data == null || dataRequestCounter !== this.dataRequestCounter) {
              return
            }

            const chartManager = this.chartManager
            if (chartManager == null) {
              return
            }

            chartManager.setData(data, request.infoResponse, reportUrlPrefix)
          })
      })
      .then(onFinish, onFinish)
    }
  }
</script>