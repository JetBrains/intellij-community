<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div>
    <el-row>
      <el-col :span="12">
        <el-form :inline="true" size="small">
          <el-form-item label="Server url">
            <el-input
                placeholder="Enter the aggregated stats server URL..."
                v-model="serverUrl">
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button @click="loadData" :loading="isFetching">Load</el-button>
          </el-form-item>
        </el-form>
      </el-col>
      <el-col :span="12">
        <div style="float: right">
          <el-checkbox
              size="small"
              v-model="chartSettings.isShowScrollbarXPreview"
              @change="isShowScrollbarXPreviewChanged"
          >Show horizontal scrollbar preview</el-checkbox>
        </div>
      </el-col>
    </el-row>

    <h2>Duration Events</h2>
    <div class="aggregatedChart" ref="durationEventChartContainer"></div>
    <h2>Instant Events</h2>
    <div class="aggregatedChart" ref="instantEventChartContainer"></div>
    <el-row>
      <el-col>
        <ul>
          <li>
            <small>Events <code>bootstrap</code> and <code>splash</code> are not available for reports <= v5 (May 2019, Idea 2019.2).</small>
          </li>
        </ul>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts">
  import {Component, Vue} from "vue-property-decorator"
  import {AggregatedStatsChartManager} from "./AggregatedStatsChartManager"
  import {AggregatedDataManager} from "@/aggregatedStats/AggregatedDataManager"
  import {AppStateModule} from "@/state/state"
  import {getModule} from "vuex-module-decorators"

  // @ts-ignore
  @Component
  export default class AggregatedStatsChart extends Vue {
    private readonly dataModule = getModule(AppStateModule, this.$store)
    private readonly chartManagers: Array<AggregatedStatsChartManager> = []

    serverUrl: string = "http://127.0.0.1:9044"
    isFetching: boolean = false

    chartSettings = this.dataModule.chartSettings

    loadData() {
      this.isFetching = true
      this.doLoadData(() => {
        this.isFetching = false
      })
    }

    isShowScrollbarXPreviewChanged(_value: boolean) {
      this.chartManagers.forEach(it => it.scrollbarXPreviewOptionChanged())
      this.dataModule.updateChartSettings(this.chartSettings)
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

          try {
            this.renderData(data)
          }
          catch (e) {
            console.error(e)
          }
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

    mounted() {
      const aggregatedData = this.dataModule.aggregatedData
      if (aggregatedData != null) {
        this.renderData(aggregatedData)
      }
    }

    private renderData(data: any): void {
      let chartManagers = this.chartManagers
      if (chartManagers.length === 0) {
        chartManagers.push(new AggregatedStatsChartManager(this.$refs.durationEventChartContainer as HTMLElement, this.chartSettings, false))
        chartManagers.push(new AggregatedStatsChartManager(this.$refs.instantEventChartContainer as HTMLElement, this.chartSettings, true))
      }

      this.dataModule.updateAggregatedData(data)

      const dataManager = new AggregatedDataManager(data)
      for (const chartManager of chartManagers) {
        try {
          chartManager.render(dataManager)
        }
        catch (e) {
          console.error(e)
        }
      }
    }

    beforeDestroy() {
      for (const chartManager of this.chartManagers) {
        chartManager.dispose()
      }
      this.chartManagers.length = 0
    }
  }
</script>
