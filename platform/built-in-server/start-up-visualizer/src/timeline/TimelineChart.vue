<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div class="timeLineChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component} from "vue-property-decorator"
  import {TimelineChartManager} from "./TimeLineChartManager"
  import {BaseChartComponent} from "@/charts/BaseChartComponent"
  import {ChartManager} from "@/charts/ChartManager"

  @Component
  export default class TimelineChart extends BaseChartComponent<ChartManager> {
    async createChartManager() {
      return new TimelineChartManager(this.$refs.chartContainer as HTMLElement)
    }
  }
</script>
