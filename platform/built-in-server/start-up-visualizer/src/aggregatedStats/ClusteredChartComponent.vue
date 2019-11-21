<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div v-loading="isLoading" class="aggregatedChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {loadJson} from "@/httpUtil"
  import {DataQuery, DataRequest, expandMachineAsFilterValue} from "@/aggregatedStats/model"
  import {ClusteredChartManager} from "@/aggregatedStats/ClusteredChartManager"
  import {BaseStatChartComponent} from "@/aggregatedStats/BaseStatChartComponent"
  import {DEFAULT_AGGREGATION_OPERATOR} from "@/aggregatedStats/ChartSettings"

  @Component
  export default class ClusteredChartComponent extends BaseStatChartComponent<ClusteredChartManager> {
    @Watch("chartSettings.aggregationOperator")
    aggregationOperatorChanged() {
      this.loadDataAfterDelay()
    }

    @Watch("chartSettings.quantile")
    quantileChanged() {
      this.loadDataAfterDelay()
    }

    @Prop({type: String})
    timeRange!: "all" | "lastMonth"

    protected reloadData(request: DataRequest) {
      const chartSettings = this.chartSettings
      let aggregator: string = chartSettings.aggregationOperator || DEFAULT_AGGREGATION_OPERATOR
      if (aggregator === "median") {
        aggregator = "quantileTDigest(0.5)"
      }
      else if (aggregator === "quantile") {
        aggregator = `quantileTDigest(${chartSettings.quantile / 100})`
      }

      const dataQuery: DataQuery = {
        fields: this.metrics,
        aggregator,
        dimensions: [
          {name: "t", sql: this.timeRange === "lastMonth" ? "toStartOfWeek(generated_time, 1)" : "toStartOfMonth(generated_time)"}
        ],
        // do not use "Jan 06" because not clear - 06 here it is month or year
        timeDimensionFormat: this.timeRange === "lastMonth" ? "02 Jan" : "Jan",
        filters: [
          {field: "product", value: request.product},
          {
            field: "machine",
            value: expandMachineAsFilterValue(request)
          },
        ],
        order: ["t"]
      }

      if (this.timeRange === "lastMonth") {
        const startDate = new Date()
        startDate.setMonth(startDate.getMonth() - 1)
        dataQuery.filters!!.push({field: "generated_time", value: Math.round(startDate.getTime() / 1000), operator: ">"})
      }

      const url = `${chartSettings.serverUrl}/api/v1/groupedMetrics/` + encodeURIComponent(JSON.stringify(dataQuery))

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

      chartManager = new ClusteredChartManager(this.$refs.chartContainer as HTMLElement)
      this.chartManager = chartManager
      return chartManager
    }
  }
</script>