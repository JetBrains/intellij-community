<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div class="activityChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {chartDescriptors} from "@/charts/ActivityChartDescriptor"
  import {BaseChartComponent} from "@/charts/BaseChartComponent"
  import {ChartManager} from "@/charts/ChartManager"
  import {Notification} from "element-ui"

  @Component
  export default class ActivityChart extends BaseChartComponent<ChartManager> {
    @Prop(String)
    type!: string

    @Watch("type")
    typeChanged(_type: string, _oldType: string): void {
      const oldChartManager = this.chartManager
      if (oldChartManager != null) {
        oldChartManager.dispose()
        this.chartManager = null
      }

      this.renderDataIfAvailable()
    }

    /** @override */
    protected async createChartManager(): Promise<ChartManager> {
      const chartContainer = this.$refs.chartContainer as HTMLElement
      const type = this.type
      const descriptor = chartDescriptors.find(it => it.id === type)
      if (descriptor == null) {
        const message = `Unknown chart type: ${type}`
        Notification.error(message)
        throw new Error(message)
      }

      const sourceNames = descriptor.sourceNames
      if (descriptor.chartManagerProducer != null) {
        // noinspection ES6RedundantAwait
        return await descriptor.chartManagerProducer(chartContainer, sourceNames!!, descriptor)
      }
      else {
        // noinspection ES6RedundantAwait
        return new (await import(/* webpackMode: "eager" */ "@/charts/ActivityChartManager")).ActivityChartManager(chartContainer, sourceNames == null ? [type] : sourceNames, descriptor)
      }
    }
  }
</script>
