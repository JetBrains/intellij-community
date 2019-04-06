<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div class="timeLineChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component} from "vue-property-decorator"
  import {TimelineChartManager} from "@/charts/TimeLineChartManager"
  import {BaseChartComponent} from "@/charts/BaseChartComponent"

  @Component
  export default class TimelineChart extends BaseChartComponent<TimelineChartManager> {
    createChartManager(): TimelineChartManager {
      return new TimelineChartManager(this.$refs.chartContainer as HTMLElement)
    }
  }
</script>
