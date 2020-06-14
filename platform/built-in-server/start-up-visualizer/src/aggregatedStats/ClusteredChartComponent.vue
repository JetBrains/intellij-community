<!-- Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div v-loading="isLoading" class="aggregatedChart" ref="chartContainer"></div>
</template>

<script lang="ts">
import {Component, Prop, Watch} from "vue-property-decorator"
import {DataQuery, DataRequest, encodeQuery, getFilters, GroupedMetricResponse} from "@/aggregatedStats/model"
import {ClusteredChartManager} from "@/aggregatedStats/ClusteredChartManager"
import {DurationParseResult, parseTimeRange, toClickhouseSql} from "@/aggregatedStats/parseDuration"
import {BaseStatChartComponent} from "@/aggregatedStats/BaseStatChartComponent"
import {DEFAULT_AGGREGATION_OPERATOR} from "@/aggregatedStats/ChartSettings"

const rison = require("rison-node")

@Component
export default class ClusteredChartComponent extends BaseStatChartComponent<ClusteredChartManager> {
  @Prop({type: String})
  timeRange!: string

  @Watch("chartSettings.aggregationOperator")
  aggregationOperatorChanged() {
    this.loadDataAfterDelay()
  }

  @Watch("chartSettings.quantile")
  quantileChanged() {
    this.loadDataAfterDelay()
  }

  @Watch("timeRange")
  timeRangeChanged(value: string, oldValue: string) {
    console.info(`timeRange changed (${oldValue} => ${value})`)
    this.loadDataAfterDelay()
  }

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
    const timeRange = parseTimeRange(this.timeRange)

    const filters = getFilters(request).concat([
      {field: "generated_time", sql: `> ${toClickhouseSql(timeRange)}`}
    ])

    const interval = getClickHouseIntervalByDuration(timeRange)
    const dataQuery: DataQuery = {
      db: request.db,
      fields: this.metrics,
      aggregator,
      dimensions: [
        {
          name: "t",
          sql: `toStartOfInterval(generated_time, interval 1 ${interval}, '${Intl.DateTimeFormat().resolvedOptions().timeZone}')`
        }
      ],
      // do not use "Jan 06" because not clear - 06 here it is month or year
      timeDimensionFormat: interval === "week" ? "02 Jan" : "Jan",
      filters,
      order: ["t"]
    }

    this.loadData(`${chartSettings.serverUrl}/api/v1/groupedMetrics/${encodeQuery(dataQuery)}`, (data: GroupedMetricResponse, chartManager: ClusteredChartManager) => {
      let intervalCount = 0
      if (timeRange.years != null) {
        intervalCount = timeRange.years
      }
      else if (timeRange.months != null) {
        intervalCount = timeRange.months
      }
      else if (timeRange.weeks != null) {
        intervalCount = timeRange.weeks
      }

      // 1 year or 1 month - show as 4 quarters / 4 weeks
      if (intervalCount == 1) {
        intervalCount = 4
      }

      // we want to show only last N intervals, but due to rounding, db can return to us N +1 (since we round to start of)
      if (data.groupNames.length > intervalCount) {
        data.groupNames = data.groupNames.slice(data.groupNames.length - intervalCount)
      }

      // if (duration.months == 1 || duration.weeks == 4) {
      //   chartManager.setChartTitle("Last 4 weeks")
      // }
      chartManager.render(data)
    })
  }

  protected createChartManager() {
    const chartManager = new ClusteredChartManager(this.$refs.chartContainer as HTMLElement)
    chartManager.chart.exporting.menu!!.items[0]!!.menu!!.push({
      label: "Open",
      type: "custom",
      options: {
        callback: () => {
          const configuration = rison.encode({
            chartSettings: this.chartSettings,
            metrics: this.metrics,
            timeRange: this.timeRange,
            dataRequest: this.dataRequest,
          })
          window.open("/#/clustered-chart/" + configuration, "_blank")
        }
      }
    })
    return chartManager
  }
}

function getClickHouseIntervalByDuration(duration: DurationParseResult) {
  if (duration.years == null && (duration.months == null || duration.months <= 1)) {
    return "week"
  }
  else {
    return "month"
  }
}
</script>