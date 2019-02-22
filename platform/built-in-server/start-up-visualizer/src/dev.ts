// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import {InputData} from "./core"
import {TimelineChartManager} from "./TimeLineChartManager"

const data: InputData = {
  "items": [
    {
      "name": "app initialization",
      "duration": 296,
      "start": 1550832115178,
      "end": 1550832115474
    },
    {
      "name": "app components initialization",
      "description": "component count: 101",
      "duration": 3680,
      "start": 1550832115480,
      "end": 1550832119160
    },
    {
      "name": "plugins initialization",
      "description": "plugin count: 189",
      "isSubItem": true,
      "duration": 375,
      "start": 1550832115480,
      "end": 1550832115855
    },
    {
      "name": "plugin descriptors loading",
      "isSubItem": true,
      "duration": 285,
      "start": 1550832115481,
      "end": 1550832115766
    },
    {
      "name": "app components registration",
      "isSubItem": true,
      "duration": 379,
      "start": 1550832115857,
      "end": 1550832116236
    },
    {
      "name": "app components registered callback",
      "isSubItem": true,
      "duration": 112,
      "start": 1550832116236,
      "end": 1550832116348
    },
    {
      "name": "app components creation",
      "isSubItem": true,
      "duration": 2812,
      "start": 1550832116348,
      "end": 1550832119160
    },
    {
      "name": "app initialized callback",
      "duration": 250,
      "start": 1550832119160,
      "end": 1550832119410
    },
    {
      "name": "project components initialization",
      "description": "component count: 211",
      "duration": 2490,
      "start": 1550832120239,
      "end": 1550832122729
    },
    {
      "name": "project components registration",
      "isSubItem": true,
      "duration": 829,
      "start": 1550832120239,
      "end": 1550832121068
    },
    {
      "name": "project components creation",
      "isSubItem": true,
      "duration": 1661,
      "start": 1550832121068,
      "end": 1550832122729
    },
    {
      "name": "project pre-startup",
      "duration": 22,
      "start": 1550832124542,
      "end": 1550832124564
    },
    {
      "name": "project startup",
      "isSubItem": true,
      "duration": 0,
      "start": 1550832124564,
      "end": 1550832124564
    },
    {
      "name": "default project components creation",
      "duration": 10,
      "start": 1550832124762,
      "end": 1550832124772
    },
    {
      "name": "default project components initialization",
      "description": "component count: 24",
      "isSubItem": true,
      "duration": 10,
      "start": 1550832124762,
      "end": 1550832124772
    },
    {
      "name": "default project components registration",
      "isSubItem": true,
      "duration": 0,
      "start": 1550832124762,
      "end": 1550832124762
    },
    {
      "name": "unknown",
      "duration": 1046,
      "start": 1550832124762,
      "end": 1550832125808
    }
  ],
  "components": [
    {
      "name": "com.intellij.openapi.components.impl.ServiceManagerImpl",
      "duration": 36,
      "start": 1550832116236,
      "end": 1550832116272
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryState",
      "duration": 112,
      "start": 1550832116352,
      "end": 1550832116464
    },
    {
      "name": "com.intellij.internal.statistic.updater.StatisticsJobsScheduler",
      "duration": 18,
      "start": 1550832116464,
      "end": 1550832116482
    },
    {
      "name": "com.intellij.configurationStore.StoreAwareProjectManager",
      "duration": 118,
      "start": 1550832116483,
      "end": 1550832116601
    },
    {
      "name": "com.intellij.openapi.vfs.PlatformVirtualFileManager",
      "duration": 114,
      "start": 1550832116483,
      "end": 1550832116597
    },
    {
      "name": "com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl",
      "duration": 95,
      "start": 1550832116486,
      "end": 1550832116581
    },
    {
      "name": "com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl",
      "duration": 88,
      "start": 1550832116486,
      "end": 1550832116574
    },
    {
      "name": "com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl",
      "duration": 521,
      "start": 1550832116601,
      "end": 1550832117122
    },
    {
      "name": "com.intellij.openapi.editor.impl.EditorFactoryImpl",
      "duration": 343,
      "start": 1550832117127,
      "end": 1550832117470
    },
    {
      "name": "com.intellij.openapi.actionSystem.impl.ActionManagerImpl",
      "duration": 329,
      "start": 1550832117128,
      "end": 1550832117457
    },
    {
      "name": "com.intellij.openapi.keymap.impl.KeymapManagerImpl",
      "duration": 16,
      "start": 1550832117129,
      "end": 1550832117145
    },
    {
      "name": "com.intellij.history.integration.LocalHistoryImpl",
      "duration": 16,
      "start": 1550832117470,
      "end": 1550832117486
    },
    {
      "name": "com.intellij.ide.ui.laf.LafManagerImpl",
      "duration": 232,
      "start": 1550832117498,
      "end": 1550832117730
    },
    {
      "name": "com.intellij.util.net.ssl.CertificateManager",
      "duration": 71,
      "start": 1550832117755,
      "end": 1550832117826
    },
    {
      "name": "com.intellij.openapi.wm.impl.FocusManagerImpl",
      "duration": 20,
      "start": 1550832117835,
      "end": 1550832117855
    },
    {
      "name": "com.intellij.openapi.wm.impl.WindowManagerImpl",
      "duration": 16,
      "start": 1550832117837,
      "end": 1550832117853
    },
    {
      "name": "com.intellij.ide.IdeTooltipManager",
      "duration": 15,
      "start": 1550832117855,
      "end": 1550832117870
    },
    {
      "name": "com.intellij.ide.MacOSApplicationProvider",
      "duration": 60,
      "start": 1550832117880,
      "end": 1550832117940
    },
    {
      "name": "com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent",
      "duration": 49,
      "start": 1550832117940,
      "end": 1550832117989
    },
    {
      "name": "com.intellij.util.indexing.FileBasedIndexImpl",
      "duration": 576,
      "start": 1550832117989,
      "end": 1550832118565
    },
    {
      "name": "com.intellij.psi.stubs.SerializationManagerImpl",
      "duration": 11,
      "start": 1550832118565,
      "end": 1550832118576
    },
    {
      "name": "com.intellij.psi.stubs.StubIndexImpl",
      "duration": 119,
      "start": 1550832118566,
      "end": 1550832118685
    },
    {
      "name": "com.intellij.openapi.actionSystem.ex.QuickListsManager",
      "duration": 19,
      "start": 1550832118690,
      "end": 1550832118709
    },
    {
      "name": "com.intellij.execution.ExecutorRegistryImpl",
      "duration": 26,
      "start": 1550832118720,
      "end": 1550832118746
    },
    {
      "name": "com.intellij.util.xml.impl.JavaDomApplicationComponent",
      "duration": 11,
      "start": 1550832118748,
      "end": 1550832118759
    },
    {
      "name": "com.intellij.openapi.projectRoots.impl.DefaultJdkConfigurator",
      "duration": 91,
      "start": 1550832118760,
      "end": 1550832118851
    },
    {
      "name": "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl",
      "duration": 12,
      "start": 1550832118861,
      "end": 1550832118873
    },
    {
      "name": "com.intellij.stats.personalization.impl.ApplicationUserFactorStorage",
      "duration": 15,
      "start": 1550832118890,
      "end": 1550832118905
    },
    {
      "name": "com.intellij.completion.FeatureManagerImpl",
      "duration": 14,
      "start": 1550832118905,
      "end": 1550832118919
    },
    {
      "name": "com.jetbrains.cidr.lang.dfa.contextSensitive.OCSourceGliderComponent",
      "duration": 152,
      "start": 1550832118930,
      "end": 1550832119082
    },
    {
      "name": "org.jetbrains.android.AndroidPlugin",
      "duration": 25,
      "start": 1550832119082,
      "end": 1550832119107
    },
    {
      "name": "org.jetbrains.plugins.textmate.TextMateApplicationComponent",
      "duration": 12,
      "start": 1550832119124,
      "end": 1550832119136
    },
    {
      "name": "org.jetbrains.plugins.ruby.gem.GemManager",
      "duration": 12,
      "start": 1550832119145,
      "end": 1550832119157
    },
    {
      "name": "com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl",
      "duration": 18,
      "start": 1550832121084,
      "end": 1550832121102
    },
    {
      "name": "com.intellij.openapi.wm.impl.ToolWindowManagerImpl",
      "duration": 19,
      "start": 1550832121102,
      "end": 1550832121121
    },
    {
      "name": "com.intellij.openapi.roots.impl.ProjectRootManagerComponent",
      "duration": 14,
      "start": 1550832121122,
      "end": 1550832121136
    },
    {
      "name": "com.intellij.psi.impl.PsiManagerImpl",
      "duration": 20,
      "start": 1550832121136,
      "end": 1550832121156
    },
    {
      "name": "com.intellij.openapi.module.impl.ModuleManagerComponent",
      "duration": 35,
      "start": 1550832121165,
      "end": 1550832121200
    },
    {
      "name": "com.intellij.openapi.fileEditor.impl.PsiAwareFileEditorManagerImpl",
      "duration": 21,
      "start": 1550832121201,
      "end": 1550832121222
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl",
      "duration": 16,
      "start": 1550832121231,
      "end": 1550832121247
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonListeners",
      "duration": 213,
      "start": 1550832121247,
      "end": 1550832121460
    },
    {
      "name": "com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl",
      "duration": 23,
      "start": 1550832121302,
      "end": 1550832121325
    },
    {
      "name": "com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl",
      "duration": 114,
      "start": 1550832121325,
      "end": 1550832121439
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangeListManagerImpl",
      "duration": 113,
      "start": 1550832121325,
      "end": 1550832121438
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangesViewManager",
      "duration": 69,
      "start": 1550832121331,
      "end": 1550832121400
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.GeneralHighlightingPassFactory",
      "duration": 38,
      "start": 1550832121460,
      "end": 1550832121498
    },
    {
      "name": "com.intellij.codeInsight.navigation.CtrlMouseHandler",
      "duration": 16,
      "start": 1550832121503,
      "end": 1550832121519
    },
    {
      "name": "com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl",
      "duration": 16,
      "start": 1550832121519,
      "end": 1550832121535
    },
    {
      "name": "com.intellij.packaging.impl.artifacts.ArtifactManagerImpl",
      "duration": 12,
      "start": 1550832121556,
      "end": 1550832121568
    },
    {
      "name": "com.intellij.compiler.CompilerConfigurationImpl",
      "duration": 390,
      "start": 1550832121569,
      "end": 1550832121959
    },
    {
      "name": "com.intellij.openapi.vcs.impl.VcsDirectoryMappingStorage",
      "duration": 23,
      "start": 1550832121961,
      "end": 1550832121984
    },
    {
      "name": "com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager",
      "duration": 27,
      "start": 1550832121995,
      "end": 1550832122022
    },
    {
      "name": "com.intellij.xdebugger.impl.XDebuggerManagerImpl",
      "duration": 95,
      "start": 1550832122033,
      "end": 1550832122128
    },
    {
      "name": "com.intellij.execution.scratch.JavaScratchCompilationSupport",
      "duration": 44,
      "start": 1550832122159,
      "end": 1550832122203
    },
    {
      "name": "com.intellij.stats.personalization.impl.UserFactorsManagerImpl",
      "duration": 17,
      "start": 1550832122227,
      "end": 1550832122244
    },
    {
      "name": "com.intellij.tasks.impl.TaskManagerImpl",
      "duration": 43,
      "start": 1550832122244,
      "end": 1550832122287
    },
    {
      "name": "com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager",
      "duration": 204,
      "start": 1550832122287,
      "end": 1550832122491
    },
    {
      "name": "com.intellij.ide.palette.impl.PaletteToolWindowManager",
      "duration": 13,
      "start": 1550832122491,
      "end": 1550832122504
    },
    {
      "name": "com.intellij.jpa.JpaProjectComponent",
      "duration": 30,
      "start": 1550832122506,
      "end": 1550832122536
    },
    {
      "name": "com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache",
      "duration": 11,
      "start": 1550832122577,
      "end": 1550832122588
    },
    {
      "name": "com.jetbrains.cidr.lang.hmap.OCHeaderMapManager",
      "duration": 47,
      "start": 1550832122589,
      "end": 1550832122636
    },
    {
      "name": "com.android.tools.idea.gradle.project.AndroidGradleProjectComponent",
      "duration": 30,
      "start": 1550832122645,
      "end": 1550832122675
    },
    {
      "name": "com.android.tools.idea.res.PsiProjectListener",
      "duration": 12,
      "start": 1550832122675,
      "end": 1550832122687
    },
    {
      "name": "com.intellij.openapi.roots.impl.ModuleRootManagerComponent",
      "duration": 87,
      "start": 1550832122763,
      "end": 1550832122850
    },
    {
      "name": "com.android.tools.idea.uibuilder.palette.NlPaletteModel",
      "duration": 21,
      "start": 1550832122855,
      "end": 1550832122876
    }
  ],
  "preloadActivities": [
    {
      "name": "com.intellij.ide.ui.OptionsTopHitProvider$Activity",
      "duration": 1790,
      "start": 1550832119252,
      "end": 1550832121042
    }
  ],
  "totalDurationComputed": 7794,
  "totalDurationActual": 10630
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