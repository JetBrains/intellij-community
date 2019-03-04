<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <ItemChart type="topHitProviders"/>
</template>

<script lang="ts">
  import {Component, Vue} from "vue-property-decorator"
  import ItemChart from "./ItemChart.vue"

  @Component({components: {ItemChart}})
  export default class TopHitOptionPreloaderChart extends Vue {
  }
</script>
