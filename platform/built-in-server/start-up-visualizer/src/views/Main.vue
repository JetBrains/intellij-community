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

    <TimelineChart/>

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
  import InputForm from "@/views/InputForm.vue"
  import TabbedCharts from "@/views/TabbedCharts.vue"
  import TimelineChart from "@/views/TimelineChart.vue"

  @Component({components: {InputForm, TimelineChart, TabbedCharts}})
  export default class Main extends Vue {
  }
</script>
<style>
.activityChart {
  width: 100%;
  height: 380px;
}
</style>
