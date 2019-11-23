<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div v-loading="isLoading" class="aggregatedChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {loadJson} from "@/httpUtil"
  import {DataQuery, DataRequest, expandMachineAsFilterValue, GroupedMetricResponse} from "@/aggregatedStats/model"
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

      // server will cache request, so, `now` here can lead to cached response, but it is ok because data collected each several hours, so, cache cleared in any case
      const dataQuery: DataQuery = {
        fields: this.metrics,
        aggregator,
        dimensions: [
          {name: "t", sql: `toStartOfInterval(generated_time, interval 1 ${this.timeRange === "lastMonth" ? "week" : "month"}, '${Intl.DateTimeFormat().resolvedOptions().timeZone}')`}
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
        dataQuery.filters!!.push({field: "generated_time", sql: "> subtractMonths(now(), 1)"})
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
          .then((data: GroupedMetricResponse | null) => {
            if (data == null || dataRequestCounter !== this.dataRequestCounter) {
              return
            }

            // we want to show only last 4 weeks, but due to rounding, db can return to us 5 (since we round to start of week)
            if (this.timeRange === "lastMonth" && data.groupNames.length > 4) {
              data.groupNames = data.groupNames.slice(data.groupNames.length - 4)
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