<!-- Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div>
    <ClusteredChartComponent :dataRequest="dataRequest" :metrics="metrics" :chartSettings="chartSettings" :timeRange="timeRange"/>
  </div>
</template>

<script lang="ts">
import {Component, Vue} from "vue-property-decorator"
import LineChartComponent from "./LineChartComponent.vue"
import ClusteredChartComponent from "./ClusteredChartComponent.vue"
import {DataRequest} from "@/aggregatedStats/model"
import {ChartSettings} from "@/aggregatedStats/ChartSettings"

const rison: { decode: (o: any) => string } = require("rison-node")
const pathPrefix = "/clustered-chart/"

@Component({
  components: {LineChartComponent, ClusteredChartComponent}
})
export default class SingleClusteredChartPage extends Vue {
  dataRequest: DataRequest | null = null

  metrics: Array<string> = []

  timeRange: string = ""
  chartSettings!: ChartSettings

  beforeMount() {
    this.applyConfiguration(this.$route.path)
  }

  private applyConfiguration(path: string) {
    const configuration: any = rison.decode(path.substring(path.indexOf(pathPrefix) + pathPrefix.length))
    console.log(configuration)
    this.chartSettings = configuration.chartSettings
    this.metrics = configuration.metrics
    this.timeRange = configuration.timeRange
    this.dataRequest = Object.seal(configuration.dataRequest)
  }
}
</script>

<style>
.aggregatedChart {
  width: 100%;
  height: 300px;
}
</style>
