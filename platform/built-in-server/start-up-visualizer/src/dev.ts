// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import {InputData} from "./core"
import {TimelineChartManager} from "./TimeLineChartManager"

const data: InputData = {
  "items": [
    {
      "name": "app initialization preparation",
      "duration": 2744,
      "start": 0,
      "end": 2744
    },
    {
      "name": "app initialization",
      "duration": 302,
      "start": 2744,
      "end": 3046
    },
    {
      "name": "plugins initialization",
      "description": "plugin count: 189",
      "duration": 369,
      "start": 3053,
      "end": 3423
    },
    {
      "name": "plugin descriptors loading",
      "duration": 281,
      "start": 3054,
      "end": 3335
    },
    {
      "name": "app components initialization",
      "description": "component count: 101",
      "duration": 3029,
      "start": 3424,
      "end": 6453
    },
    {
      "name": "app components registration",
      "duration": 323,
      "start": 3424,
      "end": 3747
    },
    {
      "name": "app components registered callback",
      "duration": 111,
      "start": 3747,
      "end": 3859
    },
    {
      "name": "app components creation",
      "duration": 2594,
      "start": 3859,
      "end": 6453
    },
    {
      "name": "app initialized callback",
      "duration": 229,
      "start": 6453,
      "end": 6683
    },
    {
      "name": "project components initialization",
      "description": "component count: 210",
      "duration": 2008,
      "start": 7490,
      "end": 9498
    },
    {
      "name": "project components registration",
      "duration": 760,
      "start": 7490,
      "end": 8250
    },
    {
      "name": "project components creation",
      "duration": 1248,
      "start": 8250,
      "end": 9498
    },
    {
      "name": "project pre-startup",
      "duration": 29,
      "start": 11277,
      "end": 11307
    },
    {
      "name": "project startup",
      "duration": 0,
      "start": 11307,
      "end": 11307
    },
    {
      "name": "default project components initialization",
      "description": "component count: 24",
      "duration": 11,
      "start": 11510,
      "end": 11522
    },
    {
      "name": "default project components registration",
      "duration": 0,
      "start": 11510,
      "end": 11511
    },
    {
      "name": "default project components creation",
      "duration": 11,
      "start": 11511,
      "end": 11522
    },
    {
      "name": "unknown",
      "duration": 1157,
      "start": 11522,
      "end": 12679
    }
  ],
  "components": [
    {
      "name": "com.intellij.openapi.components.impl.ServiceManagerImpl",
      "duration": 37,
      "start": 3747,
      "end": 3785
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryState",
      "duration": 99,
      "start": 3862,
      "end": 3962
    },
    {
      "name": "com.intellij.internal.statistic.updater.StatisticsJobsScheduler",
      "duration": 16,
      "start": 3962,
      "end": 3978
    },
    {
      "name": "com.intellij.configurationStore.StoreAwareProjectManager",
      "duration": 122,
      "start": 3978,
      "end": 4101
    },
    {
      "name": "com.intellij.openapi.vfs.PlatformVirtualFileManager",
      "duration": 118,
      "start": 3978,
      "end": 4097
    },
    {
      "name": "com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl",
      "duration": 98,
      "start": 3981,
      "end": 4080
    },
    {
      "name": "com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl",
      "duration": 93,
      "start": 3981,
      "end": 4074
    },
    {
      "name": "com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl",
      "duration": 448,
      "start": 4101,
      "end": 4549
    },
    {
      "name": "com.intellij.openapi.editor.impl.EditorFactoryImpl",
      "duration": 306,
      "start": 4554,
      "end": 4861
    },
    {
      "name": "com.intellij.openapi.actionSystem.impl.ActionManagerImpl",
      "duration": 293,
      "start": 4556,
      "end": 4849
    },
    {
      "name": "com.intellij.openapi.keymap.impl.KeymapManagerImpl",
      "duration": 15,
      "start": 4556,
      "end": 4571
    },
    {
      "name": "com.intellij.history.integration.LocalHistoryImpl",
      "duration": 13,
      "start": 4861,
      "end": 4874
    },
    {
      "name": "com.intellij.ide.ui.laf.LafManagerImpl",
      "duration": 220,
      "start": 4885,
      "end": 5105
    },
    {
      "name": "com.intellij.util.net.ssl.CertificateManager",
      "duration": 72,
      "start": 5131,
      "end": 5204
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryExtensionCollector",
      "duration": 13,
      "start": 5210,
      "end": 5223
    },
    {
      "name": "com.intellij.openapi.wm.impl.FocusManagerImpl",
      "duration": 20,
      "start": 5223,
      "end": 5243
    },
    {
      "name": "com.intellij.openapi.wm.impl.WindowManagerImpl",
      "duration": 15,
      "start": 5225,
      "end": 5241
    },
    {
      "name": "com.intellij.ide.IdeTooltipManager",
      "duration": 16,
      "start": 5243,
      "end": 5260
    },
    {
      "name": "com.intellij.ide.MacOSApplicationProvider",
      "duration": 21,
      "start": 5271,
      "end": 5292
    },
    {
      "name": "com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent",
      "duration": 50,
      "start": 5292,
      "end": 5343
    },
    {
      "name": "com.intellij.util.indexing.FileBasedIndexImpl",
      "duration": 527,
      "start": 5343,
      "end": 5871
    },
    {
      "name": "com.intellij.psi.stubs.SerializationManagerImpl",
      "duration": 11,
      "start": 5871,
      "end": 5883
    },
    {
      "name": "com.intellij.psi.stubs.StubIndexImpl",
      "duration": 100,
      "start": 5872,
      "end": 5973
    },
    {
      "name": "com.intellij.openapi.actionSystem.ex.QuickListsManager",
      "duration": 18,
      "start": 5978,
      "end": 5996
    },
    {
      "name": "com.intellij.execution.ExecutorRegistryImpl",
      "duration": 22,
      "start": 6006,
      "end": 6029
    },
    {
      "name": "com.intellij.util.xml.impl.JavaDomApplicationComponent",
      "duration": 11,
      "start": 6031,
      "end": 6042
    },
    {
      "name": "com.intellij.openapi.projectRoots.impl.DefaultJdkConfigurator",
      "duration": 84,
      "start": 6044,
      "end": 6129
    },
    {
      "name": "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl",
      "duration": 10,
      "start": 6138,
      "end": 6149
    },
    {
      "name": "com.intellij.stats.personalization.impl.ApplicationUserFactorStorage",
      "duration": 14,
      "start": 6165,
      "end": 6180
    },
    {
      "name": "com.intellij.completion.FeatureManagerImpl",
      "duration": 13,
      "start": 6180,
      "end": 6193
    },
    {
      "name": "com.jetbrains.cidr.lang.dfa.contextSensitive.OCSourceGliderComponent",
      "duration": 160,
      "start": 6194,
      "end": 6354
    },
    {
      "name": "com.intellij.j2ee.appServerIntegrations.impl.ApplicationServersManagerImpl",
      "duration": 14,
      "start": 6354,
      "end": 6369
    },
    {
      "name": "org.jetbrains.android.AndroidPlugin",
      "duration": 23,
      "start": 6376,
      "end": 6400
    },
    {
      "name": "com.jetbrains.python.testing.VFSTestFrameworkListener",
      "duration": 15,
      "start": 6425,
      "end": 6440
    },
    {
      "name": "com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl",
      "duration": 20,
      "start": 8266,
      "end": 8286
    },
    {
      "name": "com.intellij.openapi.wm.impl.ToolWindowManagerImpl",
      "duration": 20,
      "start": 8287,
      "end": 8307
    },
    {
      "name": "com.intellij.openapi.roots.impl.ProjectRootManagerComponent",
      "duration": 13,
      "start": 8308,
      "end": 8321
    },
    {
      "name": "com.intellij.psi.impl.PsiManagerImpl",
      "duration": 13,
      "start": 8321,
      "end": 8335
    },
    {
      "name": "com.intellij.psi.impl.PsiDocumentManagerImpl",
      "duration": 16,
      "start": 8336,
      "end": 8353
    },
    {
      "name": "com.intellij.openapi.module.impl.ModuleManagerComponent",
      "duration": 35,
      "start": 8353,
      "end": 8388
    },
    {
      "name": "com.intellij.openapi.fileEditor.impl.PsiAwareFileEditorManagerImpl",
      "duration": 20,
      "start": 8389,
      "end": 8410
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl",
      "duration": 16,
      "start": 8418,
      "end": 8434
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonListeners",
      "duration": 202,
      "start": 8434,
      "end": 8637
    },
    {
      "name": "com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl",
      "duration": 21,
      "start": 8487,
      "end": 8509
    },
    {
      "name": "com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl",
      "duration": 109,
      "start": 8509,
      "end": 8618
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangeListManagerImpl",
      "duration": 107,
      "start": 8509,
      "end": 8617
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangesViewManager",
      "duration": 62,
      "start": 8515,
      "end": 8577
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.GeneralHighlightingPassFactory",
      "duration": 32,
      "start": 8637,
      "end": 8669
    },
    {
      "name": "com.intellij.codeInsight.navigation.CtrlMouseHandler",
      "duration": 13,
      "start": 8673,
      "end": 8687
    },
    {
      "name": "com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl",
      "duration": 13,
      "start": 8687,
      "end": 8701
    },
    {
      "name": "com.intellij.packaging.impl.artifacts.ArtifactManagerImpl",
      "duration": 10,
      "start": 8717,
      "end": 8727
    },
    {
      "name": "com.intellij.compiler.CompilerConfigurationImpl",
      "duration": 10,
      "start": 8728,
      "end": 8738
    },
    {
      "name": "com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager",
      "duration": 27,
      "start": 8754,
      "end": 8781
    },
    {
      "name": "com.intellij.xdebugger.impl.XDebuggerManagerImpl",
      "duration": 88,
      "start": 8794,
      "end": 8882
    },
    {
      "name": "com.intellij.execution.scratch.JavaScratchCompilationSupport",
      "duration": 41,
      "start": 8911,
      "end": 8953
    },
    {
      "name": "com.intellij.stats.personalization.impl.UserFactorsManagerImpl",
      "duration": 19,
      "start": 8973,
      "end": 8992
    },
    {
      "name": "com.intellij.tasks.impl.TaskManagerImpl",
      "duration": 50,
      "start": 8992,
      "end": 9043
    },
    {
      "name": "com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager",
      "duration": 212,
      "start": 9043,
      "end": 9256
    },
    {
      "name": "com.intellij.ide.palette.impl.PaletteToolWindowManager",
      "duration": 14,
      "start": 9256,
      "end": 9270
    },
    {
      "name": "com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache",
      "duration": 10,
      "start": 9298,
      "end": 9308
    },
    {
      "name": "com.jetbrains.cidr.lang.hmap.OCHeaderMapManager",
      "duration": 45,
      "start": 9309,
      "end": 9354
    },
    {
      "name": "com.intellij.jpa.JpaProjectComponent",
      "duration": 31,
      "start": 9362,
      "end": 9394
    },
    {
      "name": "com.android.tools.idea.gradle.project.AndroidGradleProjectComponent",
      "duration": 33,
      "start": 9414,
      "end": 9447
    },
    {
      "name": "com.android.tools.idea.res.PsiProjectListener",
      "duration": 12,
      "start": 9447,
      "end": 9459
    },
    {
      "name": "com.intellij.openapi.roots.impl.ModuleRootManagerComponent",
      "duration": 75,
      "start": 9530,
      "end": 9605
    },
    {
      "name": "com.intellij.facet.FacetManagerImpl",
      "duration": 18,
      "start": 9605,
      "end": 9624
    }
  ],
  "preloadActivities": [
    {
      "name": "com.intellij.ide.ui.OptionsTopHitProvider$Activity",
      "duration": 1639,
      "start": 6530,
      "end": 8169
    }
  ],
  "totalDurationComputed": 9883,
  "totalDurationActual": 12679
}

function main() {
  const container = document.getElementById("visualization")!!
  // const chartManager = new ComponentsChartManager(container)
  const chartManager = new TimelineChartManager(container)
  chartManager.render(data)

  const global = window as any
  global.lastData = data
  global.chartManager = chartManager
}

main()