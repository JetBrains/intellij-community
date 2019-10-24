<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
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
            <el-select v-model="chartSettings.selectedProduct" filterable>
              <el-option
                    v-for="productId in products"
                    :key="productId"
                    :label="productId"
                    :value="productId">
                  </el-option>
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
          <el-checkbox
              size="small"
              v-model="chartSettings.showScrollbarXPreview"
              @change="isShowScrollbarXPreviewChanged"
          >Show horizontal scrollbar preview</el-checkbox>
        </div>
      </el-col>
    </el-row>

    <h3>Aggregated</h3>

    <el-form :inline="true" size="small">
      <el-form-item label="Operator">
        <el-select v-model="chartSettings.aggregationOperator" data-lpignore="true" filterable>
          <el-option
              v-for="name in aggregationOperators"
              :key="name"
              :label="name"
              :value="name">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input-number v-if="chartSettings.aggregationOperator === 'quantile'"
                         :min="0" :max="100" :step="10"
                         v-model="chartSettings.quantile"/>
      </el-form-item>
    </el-form>

    <el-row>
      <!-- more space for duration events (because number of duration metrics more than instant) -->
      <el-col :span="16">
        <div class="aggregatedChart" ref="clusteredDurationChartContainer"></div>
      </el-col>
      <el-col :span="8">
        <div class="aggregatedChart" ref="clusteredInstantChartContainer"></div>
      </el-col>
    </el-row>

    <h3>Duration Events</h3>
    <el-tabs value="date" size="small">
      <el-tab-pane v-for="item in [{name: 'By Date', order: 'date'}, {name: 'By Build Number', order: 'buildNumber'}]"
                   :key="item.order" :label="item.name" :name="item.order" lazy>
        <keep-alive>
          <LineChartComponent type="duration" :order="item.order" :dataRequest="dataRequest"/>
        </keep-alive>
      </el-tab-pane>
    </el-tabs>

    <h3>Instant Events</h3>
    <el-tabs value="date" size="small">
      <el-tab-pane v-for="item in [{name: 'By Date', order: 'date'}, {name: 'By Build Number', order: 'buildNumber'}]"
                   :key="item.order" :label="item.name" :name="item.order" lazy>
        <keep-alive>
          <LineChartComponent type="instant" :order="item.order" :dataRequest="dataRequest"/>
        </keep-alive>
      </el-tab-pane>
    </el-tabs>

    <el-row>
      <el-col>
        <ul>
          <li>
            <small>Events <code>bootstrap</code> and <code>splash</code> are not available for reports <= v5 (May 2019, Idea 2019.2).</small>
          </li>
        </ul>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts" src="./AggregatedStatsPage.ts"></script>

<style>
.aggregatedChart {
  width: 100%;
  height: 340px;
}

table.chartTooltip td {
  text-align: right;
  font-family: monospace;
}

table.chartTooltip th {
  text-align: left;
  font-weight: normal;
}
</style>
