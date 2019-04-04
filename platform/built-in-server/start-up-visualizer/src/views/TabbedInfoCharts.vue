<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <el-tabs v-model="activeName" @tab-click="navigate">
    <el-tab-pane label="Timeline" name="timeline" lazy>
      <keep-alive>
        <TimelineChart/>
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
  import TimelineChart from "@/views/TimelineChart.vue"
  import StatsChart from "@/views/StatsChart.vue"

  const DEFAULT_ACTIVE_TAB = "timeline"

  @Component({components: {TimelineChart, StatsChart}})
  export default class TabbedInfoCharts extends Vue {
    activeName: string = DEFAULT_ACTIVE_TAB

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
