// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import Vue from "vue"
import Router from "vue-router"
import {Notification} from "element-ui"
import Main from "@/views/Main.vue"

Vue.use(Router)

export default new Router({
  routes: [
    {
      path: "/",
      component: Main,
    },
    // {
    //   path: "/charts/components",
    //   name: "Components",
    //   component: ComponentChart,
    // },
    // {
    //   path: "/charts/services",
    //   name: "Services",
    //   component: ServiceChart,
    // },
    // {
    //   path: "/charts/topHitProviders",
    //   name: "Options Top Hit Providers",
    //   component: TopHitOptionPreloaderChart,
    // },
    {
      path: "*",
      component: () => {
        Notification.error("Path not found")
        return Promise.reject(new Error("Path not found"))
      },
    },
  ],
})
