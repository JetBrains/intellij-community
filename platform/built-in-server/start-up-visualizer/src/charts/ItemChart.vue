<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div class="activityChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Watch} from "vue-property-decorator"
  import {ComponentChartManager, ItemChartManager, ServiceChartManager, TopHitProviderChart} from "./ItemChartManager"
  import {ItemChartType} from "@/charts/ItemChartDescriptor"
  import {BaseChartComponent} from "@/charts/BaseChartComponent"

  @Component
  export default class ItemChart extends BaseChartComponent<ItemChartManager> {
    @Prop(String)
    type!: ItemChartType

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
    protected createChartManager(): ItemChartManager {
      const chartContainer = this.$refs.chartContainer as HTMLElement
      const type = this.type
      if (type === "components" || type == null) {
        return new ComponentChartManager(chartContainer)
      }
      else if (type === "services") {
        return new ServiceChartManager(chartContainer)
      }
      else if (type === "topHitProviders") {
        return new TopHitProviderChart(chartContainer)
      }
      else {
        throw new Error(`Unknown chart type: ${type}`)
      }
    }
  }
</script>
