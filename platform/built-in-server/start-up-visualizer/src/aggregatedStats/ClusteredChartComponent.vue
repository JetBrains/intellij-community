<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div v-loading="isLoading" class="aggregatedChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {DataQuery, DataRequest, encodeQuery, expandMachineAsFilterValue, GroupedMetricResponse} from "@/aggregatedStats/model"
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
          {field: "project", value: request.project},
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

      this.loadData(`${chartSettings.serverUrl}/api/v1/groupedMetrics/${encodeQuery(dataQuery)}`, (data: GroupedMetricResponse, chartManager: ClusteredChartManager) => {
        // we want to show only last 4 weeks, but due to rounding, db can return to us 5 (since we round to start of week)
        if (this.timeRange === "lastMonth" && data.groupNames.length > 4) {
          data.groupNames = data.groupNames.slice(data.groupNames.length - 4)
        }

        if (this.timeRange === "lastMonth") {
          chartManager.setChartTitle("Last 4 weeks")
        }
        chartManager.render(data)
      })
    }

    protected createChartManager() {
      return new ClusteredChartManager(this.$refs.chartContainer as HTMLElement)
    }
  }
</script>