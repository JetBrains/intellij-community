<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <el-tabs v-model="activeName" @tab-click="navigate">
    <!--  use v-once because `charts` is not going to be changed  -->
    <el-tab-pane v-once v-for="item in charts" :key="item.name" :label="item.label" :name="item.id" lazy>
      <keep-alive>
        <ActivityChart :type="item.id"/>
      </keep-alive>
    </el-tab-pane>
  </el-tabs>
</template>

<script lang="ts">
  import {Component, Vue, Watch} from "vue-property-decorator"
  import ActivityChart from "@/views/ActivityChart.vue"
  import {Location} from "vue-router"
  import {chartDescriptors} from "@/charts/ActivityChartDescriptor"

  @Component({components: {ActivityChart}})
  export default class TabbedCharts extends Vue {
    charts = chartDescriptors.filter(it => it.isInfoChart !== true)

    activeName: string = chartDescriptors[0].id

    created() {
      this.updateLocation(this.$route)
    }

    @Watch("$route")
    onRouteChanged(location: Location, _oldLocation: Location): void {
      this.updateLocation(location)
    }

    private updateLocation(location: Location): void {
      const tab = location.query == null ? null : location.query.tab
      // do not check `location.path === "/"` because if component displayed, so, active
      if (tab == null) {
        this.activeName = chartDescriptors[0].id
      }
      else {
        const descriptor = chartDescriptors.find(it => it.id === tab)
        this.activeName = descriptor == null ? chartDescriptors[0].id : descriptor.id
      }
    }

    navigate(): void {
      this.$router.push({
        query: {
          ...this.$route.query,
          tab: this.activeName,
        },
      })
    }
  }
</script>
