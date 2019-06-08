<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
<!--  <el-row>-->
<!--    <el-col>-->
<!--      <small>-->
<!--        Component-->
<!--      </small>-->
<!--    </el-col>-->
<!--  </el-row>-->
  <div class="activityChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component} from "vue-property-decorator"
  import {BaseChartComponent} from "@/charts/BaseChartComponent"
  import {TreeMapChartManager} from "@/charts/TreeMapChartManager"

  @Component
  export default class TreeMapChart extends BaseChartComponent<TreeMapChartManager> {
    createChartManager(): TreeMapChartManager {
      return new TreeMapChartManager(this.$refs.chartContainer as HTMLElement)
    }
  }
</script>
