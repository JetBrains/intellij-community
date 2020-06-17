<!-- Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div>
    <el-row>
      <el-col :span="16">
        <el-form :inline="true" size="small">
          <el-form-item label="Server">
            <el-input
                data-lpignore="true"
                placeholder="Enter the aggregated stats server URL..."
                v-model="chartSettings.serverUrl"/>
          </el-form-item>

          <el-form-item label="Machine">
            <el-cascader
                v-model="chartSettings.selectedMachine"
                :show-all-levels="false"
                :props='{"label": "name", value: "name", checkStrictly: true, emitPath: false}'
                :options="machines"/>
          </el-form-item>

          <el-form-item>
            <el-button title="Updated automatically, but you can force data reloading"
                       type="primary"
                       icon="el-icon-refresh"
                       :loading="isFetching"
                       @click="loadData"/>
          </el-form-item>
        </el-form>
      </el-col>
      <el-col :span="8">
        <div style="float: right">
          <el-checkbox size="small" v-model="chartSettings.showScrollbarXPreview">Show horizontal scrollbar preview</el-checkbox>
        </div>
      </el-col>
    </el-row>

    <h3>Aggregated</h3>

    <el-tabs value="date" size="small">
      <el-tab-pane v-for='item in [
          {name: "By Date", order: "date"},
          {name: "By Build", order: "buildNumber"}
          ]' :key="item.order" :label="item.name" :name="item.order" lazy>
        <keep-alive>
          <div>
            <el-form v-if="item.order === 'date'" :inline="true" size="small">
              <el-form-item label="Granularity">
                <el-select v-model="chartSettings.granularity" data-lpignore="true" filterable>
                  <el-option v-for='name in ["as is", "2 hour", "day", "week", "month"]' :key="name" :label="name" :value="name"/>
                </el-select>
              </el-form-item>
              <el-form-item label="Period">
                <el-select v-model="timeRange" data-lpignore="true" filterable>
                  <el-option v-for='item in timeRanges' :key="item.k" :label="item.l" :value="item.k"/>
                </el-select>
              </el-form-item>
            </el-form>

            <el-row :gutter="5">
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["prepareAppInitActivities.run frontend"]'
                                      :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["prepareAppInitActivities.start main frontend"]'
                                      :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
            </el-row>

            <el-row :gutter="5">
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["prepareAppInitActivities.uiRoot.s"]'
                                      :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["prepareAppInitActivities.start app.duration"]'
                                      :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
            </el-row>
          </div>
        </keep-alive>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script lang="ts">
import {Component} from "vue-property-decorator"
import LineChartComponent from "./LineChartComponent.vue"
import ClusteredChartComponent from "./ClusteredChartComponent.vue"
import {AggregatedStatsPage} from "./AggregatedStatsPage"

@Component({
  components: {LineChartComponent, ClusteredChartComponent}
})
export default class FleetAggregatedStatsPage extends AggregatedStatsPage {
  protected getDbName(): string {
    return "fleet"
  }
}
</script>

<!--suppress CssUnusedSymbol -->
<style>
.aggregatedChart {
  width: 100%;
  height: 300px;
}

.dividerAfterForm {
  margin-top: 0 !important;
}
</style>
