<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div>
    <InputForm/>

    <el-row>
      <el-col>
        <small>
          For timeline no threshold, for other charts threshold is 10ms.
          End time equals to last dumb-aware project start-up activity (so, services and others may be out of end time).
        </small>
      </el-col>
    </el-row>

    <TabbedInfoCharts/>
    <TabbedCharts/>

    <el-row>
      <el-col>
        <ul>
          <li>
            <small>app initialized: end of phase <code>app initialized callback</code>.</small>
          </li>
          <li>
            <small>project initialized: end of phase <code>module loading</code>.</small>
          </li>
        </ul>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts">
  import {Component, Vue} from "vue-property-decorator"
  import InputForm from "@/report/InputForm.vue"
  import TabbedCharts from "@/report/TabbedCharts.vue"
  import TabbedInfoCharts from "@/report/TabbedInfoCharts.vue"

  @Component({components: {TabbedInfoCharts, InputForm, TabbedCharts}})
  export default class Main extends Vue {
  }
</script>

<style>
.activityChart {
  width: 100%;
  /*
  our data has extraordinary high values (extremes) and it makes item chart not readable (extremes are visible and others column bars are too low),
  as solution, amCharts supports breaks (https://www.amcharts.com/demos/column-chart-with-axis-break/),
  but it contradicts to our goal - to show that these items are extremes,
  so, as solution, we increase chart height to give more space to render bars.

  It is ok, as now we use UI Library (ElementUI) and can use Tabs, Collapse and any other component to group charts.
  Also, as we use Vue.js and Vue Router, it is one-line to provide dedicated view (/#/components and so on)
  */
  height: 500px;
}

.timeLineChart {
  width: 100%;
  /* in any case time line chart will adjust height according to items */
  height: 500px;
}
</style>
