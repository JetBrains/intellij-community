// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import Vue from "vue"
import Router, {RouteConfig} from "vue-router"
import {Notification} from "element-ui"
import Main from "@/views/Main.vue"
import ItemChart from "@/charts/ItemChart.vue"
import {chartDescriptors} from "@/charts/ItemChartDescriptor"

Vue.use(Router)

// to simplify development
const chartComponentRoutes: Array<RouteConfig> = chartDescriptors.map(it => {
  return {
    path: `/${it.name}`,
    name: it.label,
    component: ItemChart,
    props: {type: it.name},
  }
})

const routes: Array<RouteConfig> = [
  {
    path: "/",
    component: Main,
  },
  {
    path: "*",
    component: () => {
      Notification.error("Path not found")
      return Promise.reject(new Error("Path not found"))
    },
  },
]
routes.push(...chartComponentRoutes)

export default new Router({
  routes,
})
