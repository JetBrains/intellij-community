<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <el-tabs v-model="activeName" @tab-click="navigate">
    <!--  use v-once because `charts` is not going to be changed  -->
    <el-tab-pane v-once v-for="item in charts" :label="item.label" :name="item.name" lazy>
      <keep-alive>
        <ItemChart :type="item.name"/>
      </keep-alive>
    </el-tab-pane>
  </el-tabs>
</template>

<script lang="ts">
  import {Component, Vue, Watch} from "vue-property-decorator"
  import ItemChart from "@/charts/ItemChart.vue"
  import {ItemChartType} from "@/charts/ItemChartManager"
  import {Location} from "vue-router"

  interface ItemChartDescriptor {
    readonly label: string
    readonly name: ItemChartType
  }

  @Component({components: {ItemChart}})
  export default class TabbedCharts extends Vue {
    charts: Array<ItemChartDescriptor> = [
      {label: "Components", name: "components"},
      {label: "Services", name: "services"},
      {label: "Options Top Hit Providers", name: "topHitProviders"},
    ]

    activeName: ItemChartType = this.charts[0].name

    created() {
      this.updateLocation(this.$route)
    }

    @Watch("$route")
    onRouteChanged(location: Location, _oldLocation: Location) {
      this.updateLocation(location)
    }

    private updateLocation(location: Location) {
      const tab = location.query == null ? null : location.query.tab
      // do not check `location.path === "/"` because if component displayed, so, active
      this.activeName = tab == null ? this.charts[0].name : tab as ItemChartType
    }

    navigate() {
      this.$router.push({query: {tab: this.activeName}})
    }
  }
</script>
