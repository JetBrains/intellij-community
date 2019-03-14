<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div class="activityChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {ActivityChartManager, ComponentChartManager} from "./ActivityChartManager"
  import {ActivityChartType} from "@/charts/ActivityChartDescriptor"
  import {BaseChartComponent} from "@/charts/BaseChartComponent"

  @Component
  export default class ActivityChart extends BaseChartComponent<ActivityChartManager> {
    @Prop(String)
    type!: ActivityChartType

    @Watch("type")
    typeChanged(_type: any, _oldType: any): void {
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
      if (type === "components" || type == null) {
        return new ComponentChartManager(chartContainer)
      }
      else if (type === "services") {
        return new ActivityChartManager(chartContainer, ["appServices", "projectServices", "moduleServices"])
      }
      else if (type === "extensions") {
        return new ActivityChartManager(chartContainer, ["appExtensions", "projectExtensions", "moduleExtensions"])
      }
      else if (type === "topHitProviders") {
        return new ActivityChartManager(chartContainer, ["appOptionsTopHitProviders", "projectOptionsTopHitProviders"])
      }
      else if (type === "prepareAppInitActivity") {
        return new ActivityChartManager(chartContainer, ["prepareAppInitActivities"])
      }
      else {
        throw new Error(`Unknown chart type: ${type}`)
      }
    }
  }
</script>
