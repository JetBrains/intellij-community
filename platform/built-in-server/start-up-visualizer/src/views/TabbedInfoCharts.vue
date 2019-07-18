<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <el-tabs v-model="activeName" @tab-click="navigate">
    <el-tab-pane label="Timeline" name="timeline" lazy>
      <keep-alive>
        <TimelineChart/>
      </keep-alive>
    </el-tab-pane>
    <!--  use v-once because `charts` is not going to be changed  -->
    <el-tab-pane v-once v-for="item in charts" :key="item.name" :label="item.label" :name="item.id" lazy>
      <keep-alive>
        <ActivityChart :type="item.id"/>
      </keep-alive>
    </el-tab-pane>
    <el-tab-pane label="Stats" name="stats" lazy>
      <keep-alive>
        <StatsChart/>
      </keep-alive>
    </el-tab-pane>
  </el-tabs>
</template>

<script lang="ts">
  import {Component, Vue, Watch} from "vue-property-decorator"
  import {Location} from "vue-router"
  import TimelineChart from "@/timeline/TimelineChart.vue"
  import StatsChart from "@/views/StatsChart.vue"
  import ActivityChart from "@/views/ActivityChart.vue"
  import {chartDescriptors} from "@/charts/ActivityChartDescriptor"

  const DEFAULT_ACTIVE_TAB = "timeline"

  @Component({components: {TimelineChart, ActivityChart, StatsChart}})
  export default class TabbedInfoCharts extends Vue {
    activeName: string = DEFAULT_ACTIVE_TAB

    charts = chartDescriptors.filter(it => it.isInfoChart === true)

    created() {
      this.updateLocation(this.$route)
    }

    @Watch("$route")
    onRouteChanged(location: Location, _oldLocation: Location): void {
      this.updateLocation(location)
    }

    private updateLocation(location: Location): void {
      const tab = location.query == null ? null : location.query.infoTab
      // do not check `location.path === "/"` because if component displayed, so, active
      this.activeName = tab == null ? DEFAULT_ACTIVE_TAB : tab as string
    }

    navigate(): void {
      this.$router.push({
        query: {
          ...this.$route.query,
          infoTab: this.activeName,
        },
      })
    }
  }
</script>
