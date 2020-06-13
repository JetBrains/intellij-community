// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Vue, Watch} from "vue-property-decorator"
import {AppStateModule} from "@/state/state"
import {getModule} from "vuex-module-decorators"
import {loadJson} from "@/httpUtil"
import {DataRequest, expandMachineAsFilterValue, InfoResponse, MachineGroup} from "@/aggregatedStats/model"
import {debounce} from "debounce"
import LineChartComponent from "@/aggregatedStats/LineChartComponent.vue"
import ClusteredChartComponent from "@/aggregatedStats/ClusteredChartComponent.vue"
import {timeRanges} from "./parseDuration"
import {Location} from "vue-router"
import {Notification} from "element-ui"
import ClusteredPage from "@/aggregatedStats/ClusteredPage.vue"

export const projectNameToTitle = new Map<string, string>()

projectNameToTitle.set("/q9N7EHxr8F1NHjbNQnpqb0Q0fs", "joda-time")
projectNameToTitle.set("73YWaW9bytiPDGuKvwNIYMK5CKI", "simple for IJ")
projectNameToTitle.set("1PbxeQ044EEghMOG9hNEFee05kM", "light edit (IJ)")

projectNameToTitle.set("j1a8nhKJexyL/zyuOXJ5CFOHYzU", "simple for PS")
projectNameToTitle.set("JeNLJFVa04IA+Wasc+Hjj3z64R0", "simple for WS")
projectNameToTitle.set("nC4MRRFMVYUSQLNIvPgDt+B3JqA", "Idea")
Object.seal(projectNameToTitle)

@Component({
  components: {LineChartComponent, ClusteredChartComponent, ClusteredPage}
})
export default class AggregatedStatsPage extends Vue {
  private readonly dataModule = getModule(AppStateModule, this.$store)
  readonly timeRanges = timeRanges

  private lastInfoResponse: InfoResponse | null = null

  chartSettings = this.dataModule.chartSettings

  products: Array<string> = []
  projects: Array<string> = []
  machines: Array<MachineGroup> = []

  isFetching: boolean = false

  projectNameToTitle = projectNameToTitle

  timeRange: String = "3M"

  private loadDataAfterDelay = debounce(() => {
    this.loadData()
  }, 1000)

  dataRequest: DataRequest | null = null

  loadData() {
    this.isFetching = true
    loadJson(`${this.chartSettings.serverUrl}/api/v1/info?db=ij`, null)
      .then((data: InfoResponse | null) => {
        if (data == null) {
          this.isFetching = false
          return
        }

        this.lastInfoResponse = Object.seal(data)
        this.products = data.productNames

        let selectedProduct = this.chartSettings.selectedProduct
        if (this.products.length === 0) {
          selectedProduct = ""
        }
        else if (selectedProduct == null || selectedProduct.length === 0 || !this.products.includes(selectedProduct)) {
          selectedProduct = this.products[0]
        }
        this.chartSettings.selectedProduct = selectedProduct

        // not called by Vue for some reasons
        console.log("update product on info response", selectedProduct)
        const oldSelectedMachine = this.chartSettings.selectedMachine
        this.applyChangedProduct(selectedProduct, data)
        const newSelectedMachine = this.chartSettings.selectedMachine
        if (!isArrayContentTheSame(oldSelectedMachine, newSelectedMachine)) {
          this.selectedMachineChanged(newSelectedMachine, oldSelectedMachine)
        }

        this.isFetching = false
      })
      .catch(e => {
        this.isFetching = false
        console.error(e)
      })
  }

  @Watch("$route")
  onRouteChanged(location: Location, _oldLocation: Location): void {
    this.setProductFromQuery(location.query?.product)
    this.setProjectFromQuery(location.query?.project)
  }

  @Watch("chartSettings.selectedProduct")
  selectedProductChanged(product: string | null, oldProduct: string | undefined): void {
    console.info(`product changed (${oldProduct} => ${product})`)

    const infoResponse = this.lastInfoResponse
    if (infoResponse != null) {
      this.applyChangedProduct(product, infoResponse)
    }

    const currentQuery = this.$route.query
    if (currentQuery.product !== product && product != null && product.length > 0) {
      // noinspection JSIgnoredPromiseFromCall
      this.$router.push({
        query: {
          ...currentQuery,
          product,
        },
      })
    }
  }

  private applyChangedProduct(product: string | null, info: InfoResponse) {
    if (product != null && product.length > 0) {
      // later maybe will be more info for machine, so, do not use string instead of Machine
      this.machines = info.productToMachine[product] || []
      if (this.machines.length === 0) {
        Notification.error(`No machines for product ${product}. Please check that product code is valid.`)
      }

      const projects = info.productToProjects[product] || []
      projects.sort((a, b) => {
        const t1 = projectNameToTitle.get(a) || a
        const t2 = projectNameToTitle.get(b) || b
        if (t1.startsWith("simple ") && !t2.startsWith("simple ")) {
          return -1
        }
        if (t2.startsWith("simple ") && !t1.startsWith("simple ")) {
          return 1
        }
        return t1.localeCompare(t2)
      })
      this.projects = projects
    }
    else {
      console.error(`set machines to empty list because no product (${product})`)
      this.machines = []
      this.projects = []
    }

    let selectedProject = this.chartSettings.selectedProject
    const projects = this.projects
    if (projects.length === 0) {
      selectedProject = ""
      this.chartSettings.selectedProject = selectedProject
    }
    else if (selectedProject == null || selectedProject.length === 0 || !projects.includes(selectedProject)) {
      selectedProject = projects[0]
    }

    let selectedMachine = this.chartSettings.selectedMachine || []
    const machines = this.machines
    if (machines.length === 0) {
      selectedMachine = []
      this.chartSettings.selectedMachine = selectedMachine
    }
    else if (selectedMachine.length === 0 || !machines.find(it => selectedMachine.includes(it.name)) || !machines.find(it => it.children.find(it => selectedMachine.includes(it.name)))) {
      selectedMachine = [machines[0].name]
    }

    if (this.chartSettings.selectedProject === selectedProject) {
      // data will be reloaded on machine change, but if product changed but machine remain the same, data reloading must be triggered here
      if (product != null && selectedProject != null && selectedProject.length > 0) {
        this.requestDataReloading(product, selectedMachine, selectedProject)
      }
    }
    else {
      this.chartSettings.selectedProject = selectedProject
    }

    if (isArrayContentTheSame(this.chartSettings.selectedMachine, selectedMachine)) {
      // data will be reloaded on machine change, but if product changed but machine remain the same, data reloading must be triggered here
      if (product != null && selectedMachine.length > 0 && this.chartSettings.selectedProject !== selectedProject) {
        this.requestDataReloading(product, selectedMachine, selectedProject)
      }
    }
    else {
      this.chartSettings.selectedMachine = selectedMachine
    }
  }

  private requestDataReloading(product: string, machine: Array<string>, project: string) {
    this.dataRequest = Object.seal({
      db: "ij",
      product,
      machine: expandMachineAsFilterValue(product, machine, this.lastInfoResponse!!),
      project
    })
  }

  @Watch("chartSettings.selectedMachine")
  selectedMachineChanged(machine: Array<string> | string, oldMachine: Array<string>): void {
    if (typeof machine === "string") {
      machine = [machine]
      this.chartSettings.selectedMachine = machine
    }

    if (oldMachine === machine) {
      return
    }

    console.log(`machine changed (${oldMachine?.join()} => ${machine?.join()})`)
    if (machine == null) {
      return
    }

    const product = this.chartSettings.selectedProduct
    const project = this.chartSettings.selectedProject
    if (product == null || product.length === 0 || project == null || project.length === 0) {
      return
    }

    this.requestDataReloading(product, machine, project)
  }

  @Watch("chartSettings.selectedProject")
  selectedProjectChanged(project: string, oldProject: Array<string>): void {
    console.log(`project changed (${oldProject} => ${project})`)
    if (project == null) {
      return
    }

    const product = this.chartSettings.selectedProduct
    const machine = this.chartSettings.selectedMachine
    if (product == null || product.length === 0 || machine == null || machine.length === 0) {
      return
    }

    this.requestDataReloading(product, machine, this.chartSettings.selectedProject)

    const currentQuery = this.$route.query
    if (currentQuery.project !== project && project != null && project.length > 0) {
      // noinspection JSIgnoredPromiseFromCall
      this.$router.push({
        query: {
          ...currentQuery,
          project,
        },
      })
    }
  }

  @Watch("chartSettings.serverUrl")
  serverUrlChanged(newV: string | null, _oldV: string) {
    if (!isEmpty(newV)) {
      this.loadDataAfterDelay()
    }
  }

  @Watch("chartSettings", {deep: true})
  chartSettingsChanged(_newV: string | null, _oldV: string) {
    console.log("chartSettings changed (deep watcher)")
    this.dataModule.updateChartSettings(this.chartSettings)
  }

  beforeMount() {
    const serverUrl = this.chartSettings.serverUrl
    if (!isEmpty(serverUrl)) {
      const query = this.$route.query
      this.setProductFromQuery(query.product)
      this.setProjectFromQuery(query.project)
      this.loadData()
    }
  }

  private setProductFromQuery(product: string | undefined | null | (string | undefined | null)[]) {
    if (product != null && product.length == 2) {
      console.log(`product specified in query: ${product}`)
      this.chartSettings.selectedProduct = (product as string).toUpperCase()
    }
  }

  private setProjectFromQuery(project: string | undefined | null | (string | undefined | null)[]) {
    if (project != null && project.length > 0) {
      console.log(`project specified in query: ${project}`)
      this.chartSettings.selectedProject = project as string
    }
  }
}

function isEmpty(v: string | null): boolean {
  return v == null || v.length === 0
}

function isArrayContentTheSame(a: Array<string>, b: Array<string>): boolean {
  if (a.length !== b.length) {
    return false
  }

  for (const item of a) {
    if (!b.includes(item)) {
      return false
    }
  }
  return true
}