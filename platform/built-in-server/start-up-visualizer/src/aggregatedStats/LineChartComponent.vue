<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div v-loading="isLoading" class="aggregatedChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {LineChartManager} from "@/aggregatedStats/LineChartManager"
  import {ChartSettings} from "@/aggregatedStats/ChartSettings"
  import {loadJson} from "@/httpUtil"
  import {SortedByCategory, SortedByDate} from "@/aggregatedStats/ChartConfigurator"
  import {DataQuery, DataRequest, expandMachine, expandMachineAsFilterValue} from "@/aggregatedStats/model"
  import {BaseStatChartComponent} from "@/aggregatedStats/BaseStatChartComponent"

  @Component
  export default class LineChartComponent extends BaseStatChartComponent<LineChartManager> {
    @Prop({type: String, required: true})
    type!: "duration" | "instant"

    @Prop(String)
    order!: "date" | "buildNumber"

    @Watch("chartSettings.showScrollbarXPreview")
    showScrollbarXPreviewChanged(): void {
      const chartManager = this.chartManager
      if (chartManager != null) {
        chartManager.scrollbarXPreviewOptionChanged(this.chartSettings)
      }
    }

    @Watch("chartSettings.granularity")
    granularityChanged() {
      this.loadDataAfterDelay()
    }

    protected reloadData(request: DataRequest) {
      const dataQuery: DataQuery = {
        fields: this.metrics,
        filters: [
          {field: "product", value: request.product},
          {field: "machine", value: expandMachineAsFilterValue(request)},
        ],
      }

      const chartSettings = this.chartSettings
      let granularity = chartSettings.granularity
      if (granularity == null || granularity == null) {
        granularity = "hour"
      }

      if (this.order === "buildNumber") {
        dataQuery.dimensions = [
          {name: "build_c1"},
          {name: "build_c2"},
          {name: "build_c3"},
        ]

        dataQuery.fields = [{name: "t", sql: `toUnixTimestamp(anyHeavy(generated_time)) * 1000`}]
        dataQuery.fields = dataQuery.fields.concat(this.metrics)
        dataQuery.order = dataQuery.dimensions.map(it => it.name)
      }
      else if (granularity !== "as is") {
        let sql: string
        if (granularity == null || granularity === "hour") {
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
          // seconds to milliseconds
          {name: "t", sql: `toUnixTimestamp(${sql}) * 1000`}
        ]

        dataQuery.fields = ["build_c1", "build_c2", "build_c3"].map(it => {
          return {name: it, sql: `anyHeavy(${it})`}
        })
        dataQuery.fields = dataQuery.fields.concat(this.metrics)
        dataQuery.order = ["t"]
      }

      dataQuery.aggregator = "medianTDigest"

      const url = `${chartSettings.serverUrl}/api/v1/metrics/` + encodeURIComponent(JSON.stringify(dataQuery))
      const reportUrlPrefix = `${chartSettings.serverUrl}/api/v1/report/` + `product=${encodeURIComponent(request.product)}&machine=${encodeURIComponent(expandMachine(request))}`

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