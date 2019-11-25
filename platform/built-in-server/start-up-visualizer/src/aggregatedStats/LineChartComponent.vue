<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <el-popover
    placement="top"
    trigger="manual"
    v-model="infoIsVisible">
    <div>
      <div>
        <!-- cell text has 10px padding - so, add link maring to align text  -->
        <el-link style="margin-left: 10px" :href="reportLink" target="_blank" type="text">{{reportName}}</el-link>
        <el-link type="default"
                 style="float: right"
                 :underline="false"
                 icon="el-icon-close"
                 @click='infoIsVisible = false'/>
      </div>
      <el-table :data="reportTableData" :show-header="false">
        <el-table-column property="name" class-name="infoMetricName" min-width="180"/>
        <el-table-column property="value" align="right" class-name="infoMetricValue"/>
      </el-table>
    </div>
    <div slot="reference" v-loading="isLoading" class="aggregatedChart" ref="chartContainer"></div>
  </el-popover>
</template>

<style>
  .el-table .cell {
    word-break: normal;
  }

  .infoMetricName {
    white-space: nowrap;
  }
  .infoMetricValue {
    font-family: monospace;
  }

  /*table.chartTooltip th {*/
  /*  text-align: left;*/
  /*  font-weight: normal;*/
  /*}*/
</style>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {LineChartManager} from "@/aggregatedStats/LineChartManager"
  import {ChartSettings} from "@/aggregatedStats/ChartSettings"
  import {SortedByCategory, SortedByDate} from "@/aggregatedStats/ChartConfigurator"
  import {
    DataQuery,
    DataQueryDimension,
    DataRequest,
    encodeQuery,
    expandMachineAsFilterValue,
    MetricDescriptor,
    Metrics
  } from "@/aggregatedStats/model"
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

    infoIsVisible: boolean = false
    reportTableData: Array<any> = []
    reportName: string = ""

    reportLink: string | null = null

    // openReport() {
    //   window.open(this.reportLink!!, "_blank")
    // }

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

        const fields: Array<string | DataQueryDimension> = [{name: "t", sql: `toUnixTimestamp(anyHeavy(generated_time)) * 1000`}]
        fields.push(...this.metrics)
        dataQuery.fields = fields
        dataQuery.order = dataQuery.dimensions.map(it => it.name)
      }
      else {
        if (granularity !== "as is") {
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
        }
        else {
          const fields: Array<string | DataQueryDimension> = [{name: "t", sql: `toUnixTimestamp(generated_time) * 1000`}, "build_c1", "build_c2", "build_c3"]
          fields.push(...this.metrics)
          dataQuery.fields = fields
        }
        dataQuery.order = ["t"]
      }

      if (granularity !== "as is") {
        dataQuery.aggregator = "medianTDigest"
      }

      this.loadData(`${chartSettings.serverUrl}/api/v1/metrics/${encodeQuery(dataQuery)}`, (data: Array<Metrics>, chartManager: LineChartManager) => {
        chartManager.render(data)
      })
    }

    protected createChartManager() {
      const metricDescriptors: Array<MetricDescriptor> = this.metrics.map(key => {
        return {
          key,
          name: (key.endsWith("_d") || key.endsWith("_i")) ? key.substring(0, key.length - 2) : key,
          hiddenByDefault: false,
        }
      })

      const configurator = this.order === "date" ? new SortedByDate(data => {
        if (data == null) {
          this.infoIsVisible = false
          return
        }

        const tableData = []
        for (const metricDescriptor of metricDescriptors) {
          tableData.push({
            name: metricDescriptor.name,
            value: data[metricDescriptor.key],
          })

          // const isDiffAbnormal = prevItem != null && (value - prevItem[metric.key]) >= 100
          // if (isDiffAbnormal) {
          //   html += "<strong>"
          // }
          // html += shortEnglishHumanizer(value)
          // if (isDiffAbnormal) {
          //   html += "</strong>"
          // }
          // html += `</td></tr>`
        }

        const request = this.dataRequest!!
        const reportQuery: DataQuery = {
          filters: [
            {field: "product", value: request.product},
            {field: "machine", value: expandMachineAsFilterValue(request)},
            {field: "generated_time", value: data.t / 1000},
          ],
        }
        const reportUrl = `/api/v1/report/${encodeQuery(reportQuery)}`
        this.reportLink = `/#/report?reportUrl=${encodeURIComponent(this.chartSettings.serverUrl)}${reportUrl}`

        this.reportTableData = tableData
        this.infoIsVisible = true
        this.reportName = this.chartManager!!.dateFormatter.format(data.t, "EEE, dd MMM yyyy HH:mm:ss zzz")
      }) : new SortedByCategory()
      return new LineChartManager(this.$refs.chartContainer as HTMLElement, this.chartSettings || new ChartSettings(), this.type === "instant", metricDescriptors, configurator)
    }
  }
</script>