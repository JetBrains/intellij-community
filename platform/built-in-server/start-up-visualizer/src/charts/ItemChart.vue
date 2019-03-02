<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div class="activityChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Vue} from "vue-property-decorator"
  import {ComponentChartManager, ItemChartType, ServiceChartManager, TopHitProviderChart} from "./ItemChartManager"
  import {DataManager, DataModule, UPDATE_DATE_MUTATION_NAME} from "@/state"
  import {getModule} from "vuex-module-decorators"
  import {ChartManager} from "@/core"

  @Component
  export default class ItemChart extends Vue {
    @Prop(String)
    type!: ItemChartType

    private readonly dataModule = getModule(DataModule, this.$store)

    mounted() {
      const chartManager = this.createChartManager()
      this.$store.subscribe(mutation => {
        if (mutation.type === UPDATE_DATE_MUTATION_NAME) {
          chartManager.render(new DataManager(this.dataModule))
        }
      })

      const data = this.dataModule.data
      if (data != null) {
        chartManager.render(new DataManager(this.dataModule))
      }
    }

    private createChartManager(): ChartManager {
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
