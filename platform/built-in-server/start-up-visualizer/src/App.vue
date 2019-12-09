<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <el-container>
    <el-header>
      <el-menu mode="horizontal" :router=true>
        <el-menu-item index="/report">Report Analyzer</el-menu-item>
        <el-menu-item index="/aggregatedStats">Aggregated Stats</el-menu-item>
      </el-menu>
    </el-header>
    <el-main>
      <keep-alive>
        <router-view/>
      </keep-alive>
    </el-main>
  </el-container>
</template>

<script lang="ts">
  import {Component, Vue} from "vue-property-decorator"

  @Component
  export default class App extends Vue {
  }
</script>
