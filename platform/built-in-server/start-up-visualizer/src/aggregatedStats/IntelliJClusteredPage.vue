<!-- Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div>
    <el-form :inline="true" size="small">
      <el-form-item label="Period">
        <el-select v-model="timeRange" data-lpignore="true" filterable>
          <el-option v-for='item in timeRanges' :key="item.k" :label="item.l" :value="item.k"/>
        </el-select>
      </el-form-item>
      <el-form-item label="Operator">
        <el-select v-model="chartSettings.aggregationOperator" data-lpignore="true" filterable>
          <el-option v-for='name in ["median", "min", "max", "quantile"]' :key="name" :label="name" :value="name"/>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input-number v-if="chartSettings.aggregationOperator === 'quantile'"
                         :min="0" :max="100" :step="10"
                         v-model="chartSettings.quantile"/>
      </el-form-item>
    </el-form>

    <el-row :gutter="5">
      <!-- more space for duration events (because number of duration metrics more than instant) -->
      <el-col :span="16">
        <el-card shadow="never" :body-style="{ padding: '0px' }">
          <ClusteredChartComponent :dataRequest="dataRequest" :chartSettings="chartSettings" :timeRange="timeRange"
                                   :metrics='["bootstrap_d", "appInitPreparation_d", "appInit_d", "pluginDescriptorLoading_d", "appComponentCreation_d", "projectComponentCreation_d"]'/>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" :body-style="{ padding: '0px' }">
          <ClusteredChartComponent :dataRequest="dataRequest" :chartSettings="chartSettings" :timeRange="timeRange"
                                   :metrics='["splash_i", "startUpCompleted_i"]'/>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="5" style="margin-top: 5px;">
      <el-col :span="12">
        <el-card shadow="never" :body-style="{ padding: '0px' }">
          <ClusteredChartComponent :dataRequest="dataRequest" :chartSettings="chartSettings" :timeRange="timeRange"
                                   :metrics='["appStarter_d", "serviceSyncPreloading_d", "serviceAsyncPreloading_d","moduleLoading_d"]'/>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" :body-style="{ padding: '0px' }">
          <ClusteredChartComponent :dataRequest="dataRequest" :chartSettings="chartSettings" :timeRange="timeRange"
                                   :metrics='["projectServiceSyncPreloading_d", "projectServiceAsyncPreloading_d", "projectDumbAware_d", "editorRestoring_d", "editorRestoringTillPaint_d"]'/>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts">
import {Component, Prop, Vue} from "vue-property-decorator"
import LineChartComponent from "./LineChartComponent.vue"
import ClusteredChartComponent from "./ClusteredChartComponent.vue"
import {getModule} from "vuex-module-decorators"
import {AppStateModule} from "@/state/state"
import {DataRequest} from "@/aggregatedStats/model"
import {timeRanges} from "@/aggregatedStats/parseDuration"

@Component({
  components: {LineChartComponent, ClusteredChartComponent}
})
export default class IntelliJClusteredPage extends Vue {
  private readonly dataModule = getModule(AppStateModule, this.$store)

  readonly timeRanges = timeRanges

  chartSettings = this.dataModule.chartSettings

  @Prop()
  dataRequest!: DataRequest

  timeRange: String = "3M"
}
</script>
