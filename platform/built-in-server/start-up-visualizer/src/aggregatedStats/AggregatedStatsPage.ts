// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Component, Vue, Watch} from "vue-property-decorator"
import {AppStateModule} from "@/state/state"
import {getModule} from "vuex-module-decorators"
import {loadJson} from "@/httpUtil"
import {DataRequest, InfoResponse, MachineGroup} from "@/aggregatedStats/model"
import {debounce} from "debounce"
import LineChartComponent from "@/aggregatedStats/LineChartComponent.vue"
import ClusteredChartComponent from "@/aggregatedStats/ClusteredChartComponent.vue"

export const projectNameToTitle = new Map<string, string>()

projectNameToTitle.set("/q9N7EHxr8F1NHjbNQnpqb0Q0fs", "joda-time")
projectNameToTitle.set("73YWaW9bytiPDGuKvwNIYMK5CKI", "simple for IJ")
projectNameToTitle.set("1PbxeQ044EEghMOG9hNEFee05kM", "light edit (IJ)")

projectNameToTitle.set("j1a8nhKJexyL/zyuOXJ5CFOHYzU", "simple for PS")
projectNameToTitle.set("JeNLJFVa04IA+Wasc+Hjj3z64R0", "simple for WS")
projectNameToTitle.set("nC4MRRFMVYUSQLNIvPgDt+B3JqA", "Idea")
Object.seal(projectNameToTitle)

@Component({
  components: {LineChartComponent, ClusteredChartComponent}
})
export default class AggregatedStatsPage extends Vue {
  private readonly dataModule = getModule(AppStateModule, this.$store)

  private lastInfoResponse: InfoResponse | null = null

  chartSettings = this.dataModule.chartSettings

  products: Array<string> = []
  projects: Array<string> = []
  machines: Array<MachineGroup> = []

  isFetching: boolean = false

  projectNameToTitle = projectNameToTitle

  private loadDataAfterDelay = debounce(() => {
    this.loadData()
  }, 1000)

  dataRequest: DataRequest | null = null

  loadData() {
    this.isFetching = true
    loadJson(`${this.chartSettings.serverUrl}/api/v1/info`, null)
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

  @Watch("chartSettings.selectedProduct")
  selectedProductChanged(product: string | null, _oldV: string): void {
    console.log("product changed", product, _oldV)

    const infoResponse = this.lastInfoResponse
    if (infoResponse != null) {
      this.applyChangedProduct(product, infoResponse)
    }

    this.dataModule.updateChartSettings(this.chartSettings)
  }

  private applyChangedProduct(product: string | null, info: InfoResponse) {
    if (product != null && product.length > 0) {
      // later maybe will be more info for machine, so, do not use string instead of Machine
      this.machines = info.productToMachine[product] || []
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
      if (product != null && selectedMachine != null && selectedMachine.length > 0 && this.chartSettings.selectedProject !== selectedProject) {
        this.requestDataReloading(product, selectedMachine, selectedProject)
      }
    }
    else {
      this.chartSettings.selectedMachine = selectedMachine
    }
  }

  private requestDataReloading(product: string, machine: Array<string>, project: string) {
    this.dataRequest = Object.seal({product, machine, project, infoResponse: this.lastInfoResponse!!})
  }

  @Watch("chartSettings.selectedMachine")
  selectedMachineChanged(machine: Array<string> | string, _oldV: Array<string>): void {
    if (typeof machine === "string") {
      machine = [machine]
      this.chartSettings.selectedMachine = machine
    }

    console.log("machine changed", machine, _oldV)
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
  selectedProjectChanged(project: string, _oldV: Array<string>): void {
    console.log("project changed", project, _oldV)
    if (project == null) {
      return
    }

    const product = this.chartSettings.selectedProduct
    const machine = this.chartSettings.selectedMachine
    if (product == null || product.length === 0 || machine == null || machine.length === 0) {
      return
    }

    this.requestDataReloading(product, machine, this.chartSettings.selectedProject)
  }

  @Watch("chartSettings.serverUrl")
  serverUrlChanged(newV: string | null, _oldV: string) {
    if (!isEmpty(newV)) {
      this.loadDataAfterDelay()
    }
  }

  @Watch("chartSettings", {deep: true})
  chartSettingsChanged(_newV: string | null, _oldV: string) {
    this.dataModule.updateChartSettings(this.chartSettings)
  }

  mounted() {
    const serverUrl = this.chartSettings.serverUrl
    if (!isEmpty(serverUrl)) {
      this.loadData()
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