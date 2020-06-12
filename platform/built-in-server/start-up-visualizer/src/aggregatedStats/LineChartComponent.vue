<!-- Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <el-popover
    placement="top"
    trigger="manual"
    v-model="infoIsVisible">
    <div>
      <div>
        <!-- cell text has 10px padding - so, add link margin to align text  -->
        <el-link v-if="reportName.length !== 0" style="margin-left: 10px" :href="reportLink" target="_blank" type="text">{{reportName}}
        </el-link>
        <small v-else style="margin-left: 10px">use <code>as is</code> granularity to see report</small>

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
</style>

<script lang="ts">
import {Component, Prop, Watch} from "vue-property-decorator"
import {LineChartManager} from "@/aggregatedStats/LineChartManager"
import {ChartSettings} from "@/aggregatedStats/ChartSettings"
import {SortedByCategory, SortedByDate} from "@/aggregatedStats/ChartConfigurator"
import {DataQuery, DataQueryDimension, DataRequest, encodeQuery, MetricDescriptor, Metrics} from "@/aggregatedStats/model"
import {BaseStatChartComponent} from "@/aggregatedStats/BaseStatChartComponent"
import {parseTimeRange, toClickhouseSql} from "@/aggregatedStats/parseDuration"

const rison = require("rison-node")

@Component
export default class LineChartComponent extends BaseStatChartComponent<LineChartManager> {
  @Prop({type: String, required: true})
  type!: "duration" | "instant"

  @Prop(String)
  order!: "date" | "buildNumber"

  @Prop({type: String})
  timeRange!: string

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

  @Watch("chartSettings.granularity")
  granularityChanged() {
    this.loadDataAfterDelay()
  }

  @Watch("timeRange")
  timeRangeChanged(value: string, oldValue: string) {
    console.info(`timeRange changed (${oldValue} => ${value})`)
    this.loadDataAfterDelay()
  }

  protected reloadData(request: DataRequest) {
    const timeRange = parseTimeRange(this.timeRange)

    const dataQuery: DataQuery = {
      db: request.db,
      fields: this.metrics,
      filters: [
        {field: "product", value: request.product},
        {field: "project", value: request.project},
        {field: "machine", value: request.machine},
        {field: "generated_time", sql: `> ${toClickhouseSql(timeRange)}`}
      ],
    }

    const chartSettings = this.chartSettings
    let granularity = chartSettings.granularity
    if (granularity == null) {
      granularity = "2 hour"
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
        let sql = "toStartOfInterval(generated_time, interval "
        // hour - backward compatibility
        if (granularity == null || granularity === "2 hour" || granularity === "hour" as any) {
          sql += "2 hour"
        }
        else if (granularity === "day") {
          sql += "1 day"
        }
        else if (granularity === "week") {
          sql += "1 week"
        }
        else {
          sql += "1 month"
        }
        sql += ")"

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
        const fields: Array<string | DataQueryDimension> = [{
          name: "t",
          sql: `toUnixTimestamp(generated_time) * 1000`
        }, "build_c1", "build_c2", "build_c3"]
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
        name: keyToName(key),
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
      }

      const request = this.dataRequest!!
      const reportQuery: DataQuery = {
        db: request.db,
        filters: [
          {field: "product", value: request.product},
          {field: "machine", value: request.machine},
          {field: "generated_time", value: data.t / 1000},
        ],
      }

      if (this.chartSettings.granularity === "as is") {
        const reportUrl = `/api/v1/report/${encodeQuery(reportQuery)}`
        this.reportLink = `/#/report?reportUrl=${encodeURIComponent(this.chartSettings.serverUrl)}${reportUrl}`
        const generatedTime = new Date(data.t)
        // 18 Oct, 13:01:49
        this.reportName = `${generatedTime.getDate()} ${generatedTime.toLocaleString("default", {month: "short"})}, ${generatedTime.toLocaleTimeString("default", {hour12: false})}`
      }
      else {
        this.reportName = ""
      }

      this.reportTableData = tableData
      this.infoIsVisible = true
    }) : new SortedByCategory()
    const chartManager = new LineChartManager(this.$refs.chartContainer as HTMLElement, this.chartSettings || new ChartSettings(), this.type === "instant", metricDescriptors, configurator)

    chartManager.chart.exporting.menu!!.items[0]!!.menu!!.push({
      label: "Open",
      type: "custom",
      options: {
        callback: () => {
          const configuration = rison.encode({
            chartSettings: this.chartSettings,
            metrics: this.metrics,
            order: this.order,
            dataRequest: this.dataRequest,
          })
          window.open("/#/line-chart/" + configuration, "_blank")
        }
      }
    })

    return chartManager
  }
}

function keyToName(key: string) {
  if (key == "pluginDescriptorInitV18_d") {
    return "pluginDescriptorInit"
  }
  else if (key == "appStarter_d") {
    return "licenseCheck"
  }
  return (key.endsWith("_d") || key.endsWith("_i")) ? key.substring(0, key.length - 2) : key
}
</script>