// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import Vue from "vue"
import Router, {RouteConfig} from "vue-router"
import {Notification} from "element-ui"
import {chartDescriptors} from "@/charts/ActivityChartDescriptor"

Vue.use(Router)

const routes: Array<RouteConfig> = [
  {
    path: "/",
    redirect: "/report",
    component: () => import("@/App.vue"),
    children: [
      {
        path: "/report",
        name: "Report Analyzer",
        component: () => import("@/report/Report.vue"),
      },
      {
        path: "/aggregatedStats",
        name: "Aggregated Stats",
        component: () => import("@/aggregatedStats/IntelliJAggregatedStatsPage.vue"),
      },
      {
        path: "/fleetStats",
        name: "Aggregated Fleet Stats",
        component: () => import("@/aggregatedStats/FleetAggregatedStatsPage.vue"),
      },
      {
        path: "/report/timeline",
        name: "Timeline",
        component: () => import("@/timeline/TimelineChart.vue"),
      },
      {
        path: "/report/serviceTimeline",
        name: "Service Timeline",
        component: () => import("@/timeline/ServiceTimelineChart.vue"),
      },
    ]
  },
  {
    path: "/line-chart/*",
    name: "Aggregated Stats - Line Chart",
    component: () => import("@/aggregatedStats/SingleLineChartPage.vue"),
    props: true,
  },
  {
    path: "/clustered-chart/*",
    name: "Aggregated Stats - Clustered Chart",
    component: () => import("@/aggregatedStats/SingleClusteredChartPage.vue"),
    props: true,
  },
  {
    path: "*",
    component: () => {
      Notification.error("Path not found")
      return Promise.reject(new Error("Path not found"))
    },
  },
]

// to simplify development
for (const chartDescriptor of chartDescriptors) {
  routes.push({
    path: `/report/${chartDescriptor.id}`,
    name: chartDescriptor.label,
    component: () => import("@/report/ActivityChart.vue"),
    props: {type: chartDescriptor.id},
  })
}

const router = new Router({
  routes,
})

// https://github.com/vuejs/vue-router/issues/914#issuecomment-384477609
router.afterEach((to, _from) => {
  Vue.nextTick(() => {
    document.title = to.name!!
  })
})

export default router
