<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <el-row :gutter="16">
    <el-col :span="10">
      <el-input
        type="textarea"
        :rows="4"
        placeholder="Enter the IntelliJ Platform start-up timeline..."
        @change="dataChanged"
        @input="inputChanged"
        v-model="inputData">
      </el-input>
    </el-col>
    <el-col :span="14">
      <el-form :inline="true" size="small">
        <el-form-item>
          <el-button @click="getFromRunningInstance" :loading="isFetching">Get from running instance</el-button>
        </el-form-item>
        <el-form-item>
          <el-input-number v-model="portNumber" :min="1024" :max="65535"></el-input-number>
        </el-form-item>
      </el-form>
      <el-form :inline="true" size="small">
        <el-form-item>
          <el-button @click="getFromRunningDevInstance" :loading="isFetchingDev">Get from running instance on port 63343</el-button>
        </el-form-item>
      </el-form>
    </el-col>
  </el-row>
</template>

<script lang="ts">
  import {Component, Vue, Watch} from "vue-property-decorator"
  import {getModule} from "vuex-module-decorators"
  import {AppStateModule} from "@/state/state"
  import {loadJson} from "@/httpUtil"
  import {Location} from "vue-router"

  @Component
  export default class InputForm extends Vue {
    private readonly dataModule = getModule(AppStateModule, this.$store)

    private inputTimerId: number = -1

    inputData: string = this.dataModule.data == null ? "" : JSON.stringify(this.dataModule.data, null, 2)
    portNumber: number = this.dataModule.recentlyUsedIdePort

    // we can set this flag using reference to button, but "[Vue warn]: Avoid mutating a prop directly...",
    // so, it seems that data property it is the only recommended way
    isFetching: boolean = false
    isFetchingDev: boolean = false

    private lastReportUrl = ""

    dataChanged() {
      const text = this.inputData
      this.dataModule.updateData(text.length === 0 ? null : JSON.parse(text))
    }

    inputChanged() {
      if (this.inputTimerId !== -1) {
        clearTimeout(this.inputTimerId)
      }
      this.inputTimerId = setTimeout(() => {
        this.dataChanged()
        this.inputTimerId = -1
      }, 500)
    }

    getFromRunningInstance() {
      this.isFetching = true

      this.dataModule.updateRecentlyUsedIdePort(this.portNumber)
      this.loadReport(getIdeaReportUrl(this.portNumber))
    }

    getFromRunningDevInstance() {
      this.isFetchingDev = true
      this.loadReport(getIdeaReportUrl(63343))
    }

    @Watch("$route")
    onRouteChanged(location: Location, _oldLocation: Location): void {
      this.loadReportUrlIfSpecified(location)
    }

    created() {
      this.loadReportUrlIfSpecified(this.$route)
    }

    loadReportUrlIfSpecified(location: Location) {
      const reportUrl = location.query == null ? "" : location.query.reportUrl as string
      if (reportUrl != null && reportUrl.length > 0 && this.lastReportUrl !== reportUrl) {
        this.isFetching = true
        this.loadReport(reportUrl)
      }
    }

    private loadReport(reportUrl: string) {
      this.lastReportUrl = reportUrl
      // localhost blocked by Firefox, but 127.0.0.1 not.
      // Google Chrome correctly resolves localhost, but Firefox doesn't.
      loadJson(reportUrl, () => {
        this.isFetchingDev = false
        this.isFetching = false
      }, this.$notify)
        .then(data => {
          if (data == null) {
            return
          }

          this.dataModule.updateData(data)
          this.inputData = JSON.stringify(data, null, 2)
        })
    }
  }

  function getIdeaReportUrl(port: number) {
    return `http://127.0.0.1:${port}/api/startUpMeasurement`
  }
</script>
