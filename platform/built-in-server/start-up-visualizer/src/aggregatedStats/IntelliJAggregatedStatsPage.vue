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

          <el-form-item label="Product">
            <el-select v-model="chartSettings.selectedProduct" filterable data-lpignore="true">
              <el-option v-for="productId in products" :key="productId" :label="productId" :value="productId"/>
            </el-select>
          </el-form-item>

          <el-form-item label="Project">
            <el-select v-model="chartSettings.selectedProject" filterable>
              <el-option v-for="project in projects" :key="project" :label="projectNameToTitle.get(project) || project" :value="project"/>
            </el-select>
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

    <IntelliJClusteredPage :dataRequest="dataRequest"/>

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

            <el-divider class="dividerAfterForm">Bootstrap</el-divider>
            <el-row :gutter="5">
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["bootstrap_d", "appInitPreparation_d", "appInit_d", "pluginDescriptorLoading_d", "euaShowing_d", "appStarter_d"]'
                                      :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["pluginDescriptorInitV18_d", "appComponentCreation_d", "projectComponentCreation_d"]'
                                      :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
            </el-row>

            <el-divider>Services</el-divider>
            <el-row :gutter="5" style="margin-top: 5px;">
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["appComponentCreation_d", "serviceSyncPreloading_d", "serviceAsyncPreloading_d"]'
                                      :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["projectComponentCreation_d", "projectServiceSyncPreloading_d", "projectServiceAsyncPreloading_d", "moduleLoading_d"]'
                                      :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
            </el-row>

            <el-divider>Post-opening</el-divider>
            <el-row :gutter="5" style="margin-top: 5px;">
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["projectDumbAware_d", "editorRestoring_d", "editorRestoringTillPaint_d"]' :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
              <el-col :span="12">
                <el-card shadow="never" :body-style="{ padding: '0px' }">
                  <LineChartComponent type="instant" :order="item.order" :dataRequest="dataRequest" :timeRange="timeRange"
                                      :metrics='["splash_i", "startUpCompleted_i"]' :chartSettings="chartSettings"/>
                </el-card>
              </el-col>
            </el-row>
          </div>
        </keep-alive>
      </el-tab-pane>
    </el-tabs>

    <el-row>
      <el-col>
        <ul>
          <li>
            <small>Events <code>bootstrap</code> and <code>splash</code> are not available for reports <= v5 (May 2019, Idea 2019.2).</small>
          </li>
          <li>
            <small>Events <code>editorRestoring</code> and <code>projectDumbAware_d</code> are reliably reported since 23 November 2019.</small>
          </li>
          <li>
            <small>Event <code>pluginDescriptorInit</code> is reported since March 2019.</small>
          </li>
        </ul>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts">
import {Component} from "vue-property-decorator"
import LineChartComponent from "./LineChartComponent.vue"
import ClusteredChartComponent from "./ClusteredChartComponent.vue"
import IntelliJClusteredPage from "./IntelliJClusteredPage.vue"
import {AggregatedStatsPage} from "./AggregatedStatsPage"

const projectNameToTitle = new Map<string, string>()

projectNameToTitle.set("/q9N7EHxr8F1NHjbNQnpqb0Q0fs", "joda-time")
projectNameToTitle.set("73YWaW9bytiPDGuKvwNIYMK5CKI", "simple for IJ")
projectNameToTitle.set("1PbxeQ044EEghMOG9hNEFee05kM", "light edit (IJ)")

projectNameToTitle.set("j1a8nhKJexyL/zyuOXJ5CFOHYzU", "simple for PS")
projectNameToTitle.set("JeNLJFVa04IA+Wasc+Hjj3z64R0", "simple for WS")
projectNameToTitle.set("nC4MRRFMVYUSQLNIvPgDt+B3JqA", "Idea")
Object.seal(projectNameToTitle)

@Component({
  components: {LineChartComponent, ClusteredChartComponent, IntelliJClusteredPage}
})
export default class IntelliJAggregatedStatsPage extends AggregatedStatsPage {
  projectNameToTitle = projectNameToTitle

  protected getDbName(): string {
    return "ij"
  }

  protected convertProjectNameToTitle(projectName: string): string {
    return projectNameToTitle.get(projectName) || projectName
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
