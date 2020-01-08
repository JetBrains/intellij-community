<!-- Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div v-loading="isLoading" class="aggregatedChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {
    DataQuery,
    DataQueryFilter,
    DataRequest,
    encodeQuery,
    expandMachineAsFilterValue,
    GroupedMetricResponse
  } from "@/aggregatedStats/model"
  import {ClusteredChartManager} from "@/aggregatedStats/ClusteredChartManager"
  import {parseDuration, toClickhouseSql} from "@/aggregatedStats/parseDuration"
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
    timeRange!: string

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
      let timeRange = this.timeRange
      if (timeRange == null || timeRange == "all") {
        timeRange = "1y"
      }
      else if (timeRange === "lastMonth") {
        timeRange = "1M"
      }
      const duration = parseDuration(timeRange)

      const filters: Array<DataQueryFilter> = [
        {field: "product", value: request.product},
        {field: "project", value: request.project},
        {
          field: "machine",
          value: expandMachineAsFilterValue(request)
        },
        {field: "generated_time", sql: `> ${toClickhouseSql(duration)}`}
      ]

      const dataQuery: DataQuery = {
        fields: this.metrics,
        aggregator,
        dimensions: [
          {name: "t", sql: `toStartOfInterval(generated_time, interval 1 ${(duration.years == undefined) ? "week" : "month"}, '${Intl.DateTimeFormat().resolvedOptions().timeZone}')`}
        ],
        // do not use "Jan 06" because not clear - 06 here it is month or year
        timeDimensionFormat: duration.years == undefined ? "02 Jan" : "Jan",
        filters,
        order: ["t"]
      }

      this.loadData(`${chartSettings.serverUrl}/api/v1/groupedMetrics/${encodeQuery(dataQuery)}`, (data: GroupedMetricResponse, chartManager: ClusteredChartManager) => {
        // we want to show only last 4 intervals, but due to rounding, db can return to us 5 (since we round to start of)
        if (data.groupNames.length > 4) {
          data.groupNames = data.groupNames.slice(data.groupNames.length - 4)
        }

        if (duration.months == 1 || duration.weeks == 4) {
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