// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import {InputData} from "./core"
import {TimelineChartManager} from "./TimeLineChartManager"

const data: InputData = {
  "items": [
    {
      "name": "app initialization",
      "duration": 307,
      "start": 1550844299966,
      "end": 1550844300273
    },
    {
      "name": "plugins initialization",
      "description": "plugin count: 189",
      "duration": 353,
      "start": 1550844300280,
      "end": 1550844300633
    },
    {
      "name": "plugin descriptors loading",
      "isSubItem": true,
      "duration": 265,
      "start": 1550844300281,
      "end": 1550844300546
    },
    {
      "name": "app components initialization",
      "description": "component count: 101",
      "duration": 3254,
      "start": 1550844300634,
      "end": 1550844303888
    },
    {
      "name": "app components registration",
      "isSubItem": true,
      "duration": 330,
      "start": 1550844300634,
      "end": 1550844300964
    },
    {
      "name": "app components registered callback",
      "isSubItem": true,
      "duration": 111,
      "start": 1550844300964,
      "end": 1550844301075
    },
    {
      "name": "app components creation",
      "isSubItem": true,
      "duration": 2813,
      "start": 1550844301075,
      "end": 1550844303888
    },
    {
      "name": "app initialized callback",
      "duration": 242,
      "start": 1550844303888,
      "end": 1550844304130
    },
    {
      "name": "project components initialization",
      "description": "component count: 210",
      "duration": 2295,
      "start": 1550844304955,
      "end": 1550844307250
    },
    {
      "name": "project components registration",
      "isSubItem": true,
      "duration": 782,
      "start": 1550844304955,
      "end": 1550844305737
    },
    {
      "name": "project components creation",
      "isSubItem": true,
      "duration": 1513,
      "start": 1550844305737,
      "end": 1550844307250
    },
    {
      "name": "project pre-startup",
      "isSubItem": true,
      "duration": 33,
      "start": 1550844310824,
      "end": 1550844310857
    },
    {
      "name": "project startup",
      "isSubItem": true,
      "duration": 1,
      "start": 1550844310857,
      "end": 1550844310858
    },
    {
      "name": "default project components creation",
      "isSubItem": true,
      "duration": 17,
      "start": 1550844311117,
      "end": 1550844311134
    },
    {
      "name": "default project components initialization",
      "description": "component count: 24",
      "isSubItem": true,
      "duration": 17,
      "start": 1550844311117,
      "end": 1550844311134
    },
    {
      "name": "default project components registration",
      "isSubItem": true,
      "duration": 0,
      "start": 1550844311117,
      "end": 1550844311117
    },
    {
      "name": "unknown",
      "duration": 1251,
      "start": 1550844311117,
      "end": 1550844312368
    }
  ],
  "components": [
    {
      "name": "com.intellij.openapi.components.impl.ServiceManagerImpl",
      "duration": 35,
      "start": 1550844300964,
      "end": 1550844300999
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryState",
      "duration": 95,
      "start": 1550844301079,
      "end": 1550844301174
    },
    {
      "name": "com.intellij.internal.statistic.updater.StatisticsJobsScheduler",
      "duration": 16,
      "start": 1550844301175,
      "end": 1550844301191
    },
    {
      "name": "com.intellij.configurationStore.StoreAwareProjectManager",
      "duration": 121,
      "start": 1550844301191,
      "end": 1550844301312
    },
    {
      "name": "com.intellij.openapi.vfs.PlatformVirtualFileManager",
      "duration": 117,
      "start": 1550844301191,
      "end": 1550844301308
    },
    {
      "name": "com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl",
      "duration": 99,
      "start": 1550844301193,
      "end": 1550844301292
    },
    {
      "name": "com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl",
      "duration": 93,
      "start": 1550844301193,
      "end": 1550844301286
    },
    {
      "name": "com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl",
      "duration": 543,
      "start": 1550844301312,
      "end": 1550844301855
    },
    {
      "name": "com.intellij.openapi.editor.impl.EditorFactoryImpl",
      "duration": 308,
      "start": 1550844301860,
      "end": 1550844302168
    },
    {
      "name": "com.intellij.openapi.actionSystem.impl.ActionManagerImpl",
      "duration": 294,
      "start": 1550844301862,
      "end": 1550844302156
    },
    {
      "name": "com.intellij.openapi.keymap.impl.KeymapManagerImpl",
      "duration": 17,
      "start": 1550844301862,
      "end": 1550844301879
    },
    {
      "name": "com.intellij.history.integration.LocalHistoryImpl",
      "duration": 15,
      "start": 1550844302168,
      "end": 1550844302183
    },
    {
      "name": "com.intellij.ide.ui.laf.LafManagerImpl",
      "duration": 222,
      "start": 1550844302196,
      "end": 1550844302418
    },
    {
      "name": "com.intellij.util.net.ssl.CertificateManager",
      "duration": 68,
      "start": 1550844302446,
      "end": 1550844302514
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryExtensionCollector",
      "duration": 13,
      "start": 1550844302523,
      "end": 1550844302536
    },
    {
      "name": "com.intellij.openapi.wm.impl.FocusManagerImpl",
      "duration": 19,
      "start": 1550844302536,
      "end": 1550844302555
    },
    {
      "name": "com.intellij.openapi.wm.impl.WindowManagerImpl",
      "duration": 16,
      "start": 1550844302537,
      "end": 1550844302553
    },
    {
      "name": "com.intellij.ide.IdeTooltipManager",
      "duration": 16,
      "start": 1550844302555,
      "end": 1550844302571
    },
    {
      "name": "com.intellij.ide.MacOSApplicationProvider",
      "duration": 21,
      "start": 1550844302583,
      "end": 1550844302604
    },
    {
      "name": "com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent",
      "duration": 52,
      "start": 1550844302604,
      "end": 1550844302656
    },
    {
      "name": "com.intellij.util.indexing.FileBasedIndexImpl",
      "duration": 617,
      "start": 1550844302656,
      "end": 1550844303273
    },
    {
      "name": "com.intellij.psi.stubs.SerializationManagerImpl",
      "duration": 15,
      "start": 1550844303272,
      "end": 1550844303287
    },
    {
      "name": "com.intellij.psi.stubs.StubIndexImpl",
      "duration": 139,
      "start": 1550844303279,
      "end": 1550844303418
    },
    {
      "name": "com.intellij.openapi.actionSystem.ex.QuickListsManager",
      "duration": 20,
      "start": 1550844303426,
      "end": 1550844303446
    },
    {
      "name": "com.intellij.execution.ExecutorRegistryImpl",
      "duration": 23,
      "start": 1550844303457,
      "end": 1550844303480
    },
    {
      "name": "com.intellij.openapi.projectRoots.impl.DefaultJdkConfigurator",
      "duration": 92,
      "start": 1550844303493,
      "end": 1550844303585
    },
    {
      "name": "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl",
      "duration": 11,
      "start": 1550844303595,
      "end": 1550844303606
    },
    {
      "name": "com.intellij.stats.completion.CompletionTrackerInitializer",
      "duration": 11,
      "start": 1550844303612,
      "end": 1550844303623
    },
    {
      "name": "com.intellij.stats.personalization.impl.ApplicationUserFactorStorage",
      "duration": 13,
      "start": 1550844303623,
      "end": 1550844303636
    },
    {
      "name": "com.intellij.completion.FeatureManagerImpl",
      "duration": 15,
      "start": 1550844303636,
      "end": 1550844303651
    },
    {
      "name": "com.jetbrains.cidr.lang.dfa.contextSensitive.OCSourceGliderComponent",
      "duration": 154,
      "start": 1550844303651,
      "end": 1550844303805
    },
    {
      "name": "org.jetbrains.android.AndroidPlugin",
      "duration": 23,
      "start": 1550844303819,
      "end": 1550844303842
    },
    {
      "name": "org.jetbrains.plugins.ruby.gem.GemManager",
      "duration": 12,
      "start": 1550844303873,
      "end": 1550844303885
    },
    {
      "name": "com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl",
      "duration": 21,
      "start": 1550844305756,
      "end": 1550844305777
    },
    {
      "name": "com.intellij.openapi.wm.impl.ToolWindowManagerImpl",
      "duration": 19,
      "start": 1550844305777,
      "end": 1550844305796
    },
    {
      "name": "com.intellij.openapi.roots.impl.ProjectRootManagerComponent",
      "duration": 13,
      "start": 1550844305797,
      "end": 1550844305810
    },
    {
      "name": "com.intellij.psi.impl.PsiManagerImpl",
      "duration": 14,
      "start": 1550844305810,
      "end": 1550844305824
    },
    {
      "name": "com.intellij.openapi.module.impl.ModuleManagerComponent",
      "duration": 42,
      "start": 1550844305832,
      "end": 1550844305874
    },
    {
      "name": "com.intellij.openapi.fileEditor.impl.PsiAwareFileEditorManagerImpl",
      "duration": 21,
      "start": 1550844305875,
      "end": 1550844305896
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl",
      "duration": 17,
      "start": 1550844305904,
      "end": 1550844305921
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonListeners",
      "duration": 211,
      "start": 1550844305921,
      "end": 1550844306132
    },
    {
      "name": "com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl",
      "duration": 21,
      "start": 1550844305975,
      "end": 1550844305996
    },
    {
      "name": "com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl",
      "duration": 115,
      "start": 1550844305996,
      "end": 1550844306111
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangeListManagerImpl",
      "duration": 113,
      "start": 1550844305997,
      "end": 1550844306110
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangesViewManager",
      "duration": 68,
      "start": 1550844306003,
      "end": 1550844306071
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.GeneralHighlightingPassFactory",
      "duration": 35,
      "start": 1550844306132,
      "end": 1550844306167
    },
    {
      "name": "com.intellij.codeInsight.navigation.CtrlMouseHandler",
      "duration": 16,
      "start": 1550844306171,
      "end": 1550844306187
    },
    {
      "name": "com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl",
      "duration": 16,
      "start": 1550844306187,
      "end": 1550844306203
    },
    {
      "name": "com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager",
      "duration": 30,
      "start": 1550844306259,
      "end": 1550844306289
    },
    {
      "name": "com.intellij.xdebugger.impl.XDebuggerManagerImpl",
      "duration": 89,
      "start": 1550844306302,
      "end": 1550844306391
    },
    {
      "name": "com.intellij.execution.scratch.JavaScratchCompilationSupport",
      "duration": 37,
      "start": 1550844306421,
      "end": 1550844306458
    },
    {
      "name": "com.intellij.stats.personalization.impl.UserFactorsManagerImpl",
      "duration": 18,
      "start": 1550844306474,
      "end": 1550844306492
    },
    {
      "name": "com.intellij.tasks.impl.TaskManagerImpl",
      "duration": 48,
      "start": 1550844306492,
      "end": 1550844306540
    },
    {
      "name": "com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager",
      "duration": 193,
      "start": 1550844306540,
      "end": 1550844306733
    },
    {
      "name": "com.intellij.ide.palette.impl.PaletteToolWindowManager",
      "duration": 20,
      "start": 1550844306733,
      "end": 1550844306753
    },
    {
      "name": "org.jetbrains.idea.maven.navigator.MavenProjectsNavigator",
      "duration": 18,
      "start": 1550844306764,
      "end": 1550844306782
    },
    {
      "name": "org.osmorc.OsmorcProjectComponent",
      "duration": 19,
      "start": 1550844306786,
      "end": 1550844306805
    },
    {
      "name": "com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache",
      "duration": 14,
      "start": 1550844306805,
      "end": 1550844306819
    },
    {
      "name": "com.jetbrains.cidr.lang.hmap.OCHeaderMapManager",
      "duration": 86,
      "start": 1550844306821,
      "end": 1550844306907
    },
    {
      "name": "com.jetbrains.cidr.cpp.compdb.CompDBWorkspace",
      "duration": 21,
      "start": 1550844306913,
      "end": 1550844306934
    },
    {
      "name": "com.intellij.jpa.JpaProjectComponent",
      "duration": 67,
      "start": 1550844306934,
      "end": 1550844307001
    },
    {
      "name": "com.intellij.psi.impl.source.jsp.JspContextManagerImpl",
      "duration": 11,
      "start": 1550844307010,
      "end": 1550844307021
    },
    {
      "name": "com.android.tools.idea.gradle.project.AndroidGradleProjectComponent",
      "duration": 75,
      "start": 1550844307031,
      "end": 1550844307106
    },
    {
      "name": "com.android.tools.idea.res.PsiProjectListener",
      "duration": 62,
      "start": 1550844307106,
      "end": 1550844307168
    },
    {
      "name": "org.jetbrains.plugins.terminal.TerminalView",
      "duration": 15,
      "start": 1550844307178,
      "end": 1550844307193
    },
    {
      "name": "com.intellij.lang.javascript.frameworks.webpack.Webpack4PluginProviderRegistrar",
      "duration": 21,
      "start": 1550844307199,
      "end": 1550844307220
    },
    {
      "name": "com.intellij.openapi.roots.impl.ModuleRootManagerComponent",
      "duration": 123,
      "start": 1550844307294,
      "end": 1550844307417
    },
    {
      "name": "com.intellij.facet.FacetManagerImpl",
      "duration": 33,
      "start": 1550844307417,
      "end": 1550844307450
    }
  ],
  "preloadActivities": [
    {
      "name": "com.intellij.ide.ui.OptionsTopHitProvider$Activity",
      "duration": 1726,
      "start": 1550844303962,
      "end": 1550844305688
    },
    {
      "name": "com.intellij.openapi.actionSystem.impl.ActionPreloader",
      "duration": 6322,
      "start": 1550844305688,
      "end": 1550844312010
    }
  ],
  "totalDurationComputed": 7702,
  "totalDurationActual": 12402
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