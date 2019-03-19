<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div class="activityChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {ActivityChartManager, ComponentChartManager} from "./ActivityChartManager"
  import {ActivityChartType, chartDescriptors} from "@/charts/ActivityChartDescriptor"
  import {BaseChartComponent} from "@/charts/BaseChartComponent"

  @Component
  export default class ActivityChart extends BaseChartComponent<ActivityChartManager> {
    @Prop(String)
    type!: ActivityChartType

    @Watch("type")
    typeChanged(_type: ActivityChartType, _oldType: ActivityChartType): void {
      const oldChartManager = this.chartManager
      if (oldChartManager != null) {
        oldChartManager.dispose()
        this.chartManager = null
      }

      this.chartManager = this.createChartManager()
      this.renderDataIfAvailable()
    }

    /** @override */
    protected createChartManager(): ActivityChartManager {
      const chartContainer = this.$refs.chartContainer as HTMLElement
      const type = this.type
      const descriptor = chartDescriptors.find(it => it.id === type)
      if (descriptor == null) {
        throw new Error(`Unknown chart type: ${type}`)
      }

      const sourceNames = descriptor.sourceNames
      if (type === "components") {
        return new ComponentChartManager(chartContainer, sourceNames!!)
      }
      else {
        return new ActivityChartManager(chartContainer, sourceNames == null ? [type] : sourceNames)
      }
    }
  }
</script>
