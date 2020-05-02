// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import Vue from "vue"
import ElementUI from "element-ui"
import router from "./router"
import {store} from "./state/state"
import locale from "element-ui/lib/locale/lang/en"

import "element-ui/lib/theme-chalk/reset.css"
import "element-ui/lib/theme-chalk/index.css"
import AppContainer from "@/AppContainer.vue"

Vue.use(ElementUI, {locale})

Vue.config.productionTip = false

new Vue({
  render: h => h(AppContainer),
  store,
  router,
}).$mount("#app")
