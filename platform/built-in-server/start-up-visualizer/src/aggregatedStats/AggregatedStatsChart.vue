<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div>
    <el-form :inline="true" size="small">
      <el-form-item  label="Server url">
        <el-input
            placeholder="Enter the aggregated stats server URL..."
            label="qweqwe"
            v-model="serverUrl">
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button @click="loadData" :loading="isFetching">Load</el-button>
      </el-form-item>
    </el-form>
    <div class="activityChart" ref="chartContainer"></div>
  </div>
</template>

<script lang="ts">
  import {Component, Vue} from "vue-property-decorator"
  import {AggregatedStatsChartManager} from "./AggregatedStatsChartManager"
  import {Notification} from "element-ui"

  // @ts-ignore
  @Component
  export default class AggregatedStatsChart extends Vue {
    protected chartManager: AggregatedStatsChartManager | null = null

    serverUrl: string = "http://127.0.0.1:9044"
    isFetching: boolean = false

    protected async createChartManager() {
      return new AggregatedStatsChartManager(this.$refs.chartContainer as HTMLElement)
    }

    loadData() {
      this.isFetching = true
      this.doLoadData(() => {
        this.isFetching = false
      })
    }

    private doLoadData(processed: () => void) {
      // localhost blocked by Firefox, but 127.0.0.1 not.
      // Google Chrome correctly resolves localhost, but Firefox doesn't.
      const host = this.serverUrl

      const showError = (reason: string) => {
        this.$notify.error({
          title: "Error",
          message: `Cannot load data from "${host}": ${reason}`,
        })
      }

      let isCancelledByTimeout = false
      const controller = new AbortController()
      const signal = controller.signal
      const timeoutId = setTimeout(() => {
        isCancelledByTimeout = true
        controller.abort()
        showError("8 seconds timeout")
      }, 8000)

      fetch(`${host}/stats`, {credentials: "omit", signal})
        .then(it => it.json())
        .then(data => {
          clearTimeout(timeoutId)

          if (data == null) {
            const message = "IntelliJ Platform IDE didn't report stats"
            console.error(message)
            alert(message)
            return
          }

          processed()

          this.renderDataIfAvailable(data)
        })
        .catch(e => {
          processed()

          clearTimeout(timeoutId)
          if (!isCancelledByTimeout) {
            showError(e.toString())
          }
          console.warn(`Cannot load data from "${host}"`, e)
        })
    }

    private renderDataIfAvailable(data: any): void {
      let chartManager = this.chartManager
      if (chartManager == null) {
        this.createChartManager()
          .then(chartManager => {
            this.chartManager = chartManager
            chartManager.render(data)
          })
          .catch(e => {
            console.log(e)
            Notification.error(e)
          })
      }
      else {
        chartManager.render(data)
      }
    }

    beforeDestroy() {
      const chartManager = this.chartManager
      if (chartManager != null) {
        this.chartManager = null
        chartManager.dispose()
      }
    }
  }
</script>
