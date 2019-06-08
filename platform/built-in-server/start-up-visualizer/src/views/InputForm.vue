<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <el-row :gutter="16">
    <el-col :span="10">
      <el-input
        type="textarea"
        :rows="4"
        autocomplete="off"
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
  import {Component, Vue} from "vue-property-decorator"
  import {getModule} from "vuex-module-decorators"
  import {AppStateModule} from "@/state/state"

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

      // portNumber will be here as number, thanks to Vue.js, no need to validate
      // if (!/^\d+$/.test(port)) {
      //   throw new Error("Port number value is not numeric")
      // }

      this.dataModule.updateRecentlyUsedIdePort(this.portNumber)
      this.doGetFromRunningInstance(this.portNumber, () => {
        this.isFetching = false
      })
    }

    getFromRunningDevInstance() {
      this.isFetchingDev = true
      this.doGetFromRunningInstance(63343, () => {
        this.isFetchingDev = false
      })
    }

    private doGetFromRunningInstance(port: number, processed: () => void) {
      // localhost blocked by Firefox, but 127.0.0.1 not.
      // Google Chrome correctly resolves localhost, but Firefox doesn't.
      const host = `127.0.0.1:${port}`

      const showError = (reason: string) => {
        this.$notify.error({
          title: "Error",
          message: `Cannot load data from "${host}": ${reason}`,
        })
      }

      let isCancelledByTimeout = false
      const controller = new AbortController()
      const signal = controller.signal
      const timeoutId = setTimeout(() => {
        isCancelledByTimeout = true
        controller.abort()
        showError("8 seconds timeout")
      }, 8000)

      fetch(`http://${host}/api/startUpMeasurement`, {credentials: "omit", signal})
        .then(it => it.json())
        .then(data => {
          clearTimeout(timeoutId)

          if (data == null) {
            const message = "IntelliJ Platform IDE didn't report startup measurement result"
            console.error(message)
            alert(message)
            return
          }

          processed()

          this.dataModule.updateData(data)
          this.inputData = JSON.stringify(data, null, 2)
        })
        .catch(e => {
          processed()

          clearTimeout(timeoutId)
          if (!isCancelledByTimeout) {
            showError(e.toString())
          }
          console.warn(`Cannot load data from "${host}"`, e)
        })
    }
  }
</script>
