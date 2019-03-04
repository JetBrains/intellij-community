<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div class="activityChart" ref="chartContainer"></div>
</template>

<script lang="ts">
  import {Component, Prop, Vue} from "vue-property-decorator"
  import {DataManager, DataModule, UPDATE_DATE_MUTATION_NAME} from "@/state"
  import {getModule} from "vuex-module-decorators"
  import {ChartManager} from "@/core"
  import {TimelineChartManager} from "@/charts/TimeLineChartManager"

  @Component
  export default class TimelineChart extends Vue {
    @Prop(String)
    type!: string

    private readonly dataModule = getModule(DataModule, this.$store)

    mounted() {
      const chartManager = this.createChartManager()
      this.$store.subscribe(mutation => {
        if (mutation.type === UPDATE_DATE_MUTATION_NAME) {
          chartManager.render(new DataManager(this.dataModule))
        }
      })

      const data = this.dataModule.data
      if (data != null) {
        chartManager.render(new DataManager(this.dataModule))
      }
    }

    private createChartManager(): ChartManager {
      return new TimelineChartManager(this.$refs.chartContainer as HTMLElement)
    }
  }
</script>
