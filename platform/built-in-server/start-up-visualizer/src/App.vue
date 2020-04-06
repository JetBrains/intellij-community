<!-- Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <!-- v-if doesn't work for template, so, two divs are used -->
  <div v-if='isSingleChartView'>
    <router-view key="routerView"/>
  </div>
  <div v-else>
    <el-container>
      <el-header>
        <el-menu mode="horizontal" :router=true>
          <el-menu-item index="/report">Report Analyzer</el-menu-item>
          <el-menu-item index="/aggregatedStats">Aggregated Stats</el-menu-item>
        </el-menu>
      </el-header>
      <el-main>
        <keep-alive>
          <router-view key="routerView"/>
        </keep-alive>
      </el-main>
    </el-container>
  </div>
</template>

<script lang="ts">
import {Component, Vue} from "vue-property-decorator"

@Component
export default class App extends Vue {
  isSingleChartView = false

  beforeMount() {
    // do not use $route since it leads to blink - fist header is rendered and only then hidden
    this.isSingleChartView = window.location.hash.includes("/line-chart/") || this.$route.path.includes("/line-chart/")
  }
}
</script>
