<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div style="height: 500px" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component} from "vue-property-decorator"
  import {BaseChartComponent} from "@/charts/BaseChartComponent"
  import {StatsChartManager} from "@/charts/StatsChartManager"

  @Component
  export default class StatsChart extends BaseChartComponent<StatsChartManager> {
    async createChartManager() {
      return new StatsChartManager(this.$refs.chartContainer as HTMLElement)
    }
  }
</script>
