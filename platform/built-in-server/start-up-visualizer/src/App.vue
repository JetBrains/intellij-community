<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <router-view/>
</template>

<script lang="ts">
  import {Component, Vue} from "vue-property-decorator"
  import Main from "@/views/Main.vue"

  @Component({components: {Main}})
  export default class App extends Vue {
  }
</script>
