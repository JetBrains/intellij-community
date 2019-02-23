// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import {InputData} from "./core"
import {TimelineChartManager} from "./TimeLineChartManager"
import {ComponentsChartManager, TopHitProviderChart} from "./ComponentsChartManager"

const data: InputData = {
  "version": "1",
  "items": [
    {
      "name": "app initialization preparation",
      "duration": 3240,
      "start": 0,
      "end": 3240
    },
    {
      "name": "app initialization",
      "duration": 286,
      "start": 3240,
      "end": 3527
    },
    {
      "name": "plugins initialization",
      "description": "plugin count: 189",
      "duration": 363,
      "start": 3530,
      "end": 3894
    },
    {
      "name": "plugin descriptors loading",
      "duration": 280,
      "start": 3531,
      "end": 3811
    },
    {
      "name": "app components initialization",
      "description": "component count: 101",
      "duration": 3287,
      "start": 3898,
      "end": 7185
    },
    {
      "name": "app components registration",
      "duration": 338,
      "start": 3898,
      "end": 4236
    },
    {
      "name": "app components registered callback",
      "duration": 111,
      "start": 4236,
      "end": 4348
    },
    {
      "name": "app components creation",
      "duration": 2837,
      "start": 4348,
      "end": 7185
    },
    {
      "name": "app initialized callback",
      "duration": 223,
      "start": 7185,
      "end": 7409
    },
    {
      "name": "project components initialization",
      "description": "component count: 210",
      "duration": 1752,
      "start": 8507,
      "end": 10259
    },
    {
      "name": "project components registration",
      "duration": 567,
      "start": 8507,
      "end": 9074
    },
    {
      "name": "project components creation",
      "duration": 1184,
      "start": 9074,
      "end": 10259
    },
    {
      "name": "module loading",
      "description": "module count: 1",
      "duration": 142,
      "start": 10260,
      "end": 10402
    },
    {
      "name": "project pre-startup",
      "duration": 28,
      "start": 12006,
      "end": 12034
    },
    {
      "name": "project startup",
      "duration": 0,
      "start": 12034,
      "end": 12034
    },
    {
      "name": "default project components initialization",
      "description": "component count: 24",
      "duration": 14,
      "start": 12247,
      "end": 12261
    },
    {
      "name": "default project components registration",
      "duration": 0,
      "start": 12247,
      "end": 12248
    },
    {
      "name": "default project components creation",
      "duration": 13,
      "start": 12248,
      "end": 12261
    },
    {
      "name": "unknown",
      "duration": 1524,
      "start": 12261,
      "end": 13786
    }
  ],
  "appComponents": [
    {
      "name": "com.intellij.openapi.components.impl.ServiceManagerImpl",
      "duration": 35,
      "start": 4236,
      "end": 4271
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryState",
      "duration": 100,
      "start": 4351,
      "end": 4452
    },
    {
      "name": "com.intellij.internal.statistic.updater.StatisticsJobsScheduler",
      "duration": 16,
      "start": 4452,
      "end": 4469
    },
    {
      "name": "com.intellij.configurationStore.StoreAwareProjectManager",
      "duration": 120,
      "start": 4469,
      "end": 4590
    },
    {
      "name": "com.intellij.openapi.vfs.PlatformVirtualFileManager",
      "duration": 117,
      "start": 4469,
      "end": 4586
    },
    {
      "name": "com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl",
      "duration": 97,
      "start": 4472,
      "end": 4569
    },
    {
      "name": "com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl",
      "duration": 90,
      "start": 4472,
      "end": 4563
    },
    {
      "name": "com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl",
      "duration": 521,
      "start": 4590,
      "end": 5111
    },
    {
      "name": "com.intellij.openapi.editor.impl.EditorFactoryImpl",
      "duration": 325,
      "start": 5117,
      "end": 5443
    },
    {
      "name": "com.intellij.openapi.actionSystem.impl.ActionManagerImpl",
      "duration": 312,
      "start": 5119,
      "end": 5431
    },
    {
      "name": "com.intellij.openapi.keymap.impl.KeymapManagerImpl",
      "duration": 17,
      "start": 5119,
      "end": 5136
    },
    {
      "name": "com.intellij.history.integration.LocalHistoryImpl",
      "duration": 15,
      "start": 5443,
      "end": 5459
    },
    {
      "name": "com.intellij.ide.ui.laf.LafManagerImpl",
      "duration": 223,
      "start": 5471,
      "end": 5695
    },
    {
      "name": "com.intellij.util.net.ssl.CertificateManager",
      "duration": 68,
      "start": 5720,
      "end": 5789
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryExtensionCollector",
      "duration": 13,
      "start": 5795,
      "end": 5808
    },
    {
      "name": "com.intellij.openapi.wm.impl.FocusManagerImpl",
      "duration": 25,
      "start": 5808,
      "end": 5833
    },
    {
      "name": "com.intellij.openapi.wm.impl.WindowManagerImpl",
      "duration": 19,
      "start": 5810,
      "end": 5830
    },
    {
      "name": "com.intellij.ide.IdeTooltipManager",
      "duration": 16,
      "start": 5833,
      "end": 5850
    },
    {
      "name": "com.intellij.ide.MacOSApplicationProvider",
      "duration": 22,
      "start": 5863,
      "end": 5886
    },
    {
      "name": "com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent",
      "duration": 57,
      "start": 5886,
      "end": 5943
    },
    {
      "name": "com.intellij.util.indexing.FileBasedIndexImpl",
      "duration": 621,
      "start": 5943,
      "end": 6564
    },
    {
      "name": "com.intellij.psi.stubs.SerializationManagerImpl",
      "duration": 11,
      "start": 6564,
      "end": 6576
    },
    {
      "name": "com.intellij.psi.stubs.StubIndexImpl",
      "duration": 117,
      "start": 6566,
      "end": 6683
    },
    {
      "name": "com.intellij.openapi.actionSystem.ex.QuickListsManager",
      "duration": 17,
      "start": 6688,
      "end": 6706
    },
    {
      "name": "com.intellij.execution.ExecutorRegistryImpl",
      "duration": 23,
      "start": 6716,
      "end": 6739
    },
    {
      "name": "com.intellij.util.xml.impl.JavaDomApplicationComponent",
      "duration": 12,
      "start": 6741,
      "end": 6754
    },
    {
      "name": "com.intellij.openapi.projectRoots.impl.DefaultJdkConfigurator",
      "duration": 96,
      "start": 6755,
      "end": 6852
    },
    {
      "name": "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl",
      "duration": 12,
      "start": 6862,
      "end": 6875
    },
    {
      "name": "com.intellij.stats.completion.CompletionTrackerInitializer",
      "duration": 11,
      "start": 6883,
      "end": 6894
    },
    {
      "name": "com.intellij.stats.personalization.impl.ApplicationUserFactorStorage",
      "duration": 15,
      "start": 6894,
      "end": 6910
    },
    {
      "name": "com.intellij.completion.FeatureManagerImpl",
      "duration": 17,
      "start": 6910,
      "end": 6927
    },
    {
      "name": "com.jetbrains.cidr.lang.dfa.contextSensitive.OCSourceGliderComponent",
      "duration": 171,
      "start": 6928,
      "end": 7100
    },
    {
      "name": "org.jetbrains.android.AndroidPlugin",
      "duration": 20,
      "start": 7114,
      "end": 7134
    },
    {
      "name": "org.jetbrains.plugins.ruby.gem.GemManager",
      "duration": 12,
      "start": 7169,
      "end": 7181
    }
  ],
  "appOptionsTopHitProviders": [
    {
      "name": "com.intellij.codeInsight.intention.impl.config.IntentionsOptionsTopHitProvider",
      "duration": 1320,
      "start": 7297,
      "end": 8618
    },
    {
      "name": "com.intellij.codeInsight.template.impl.LiveTemplatesOptionsTopHitProvider",
      "duration": 309,
      "start": 8618,
      "end": 8927
    }
  ],
  "preloadActivities": [
    {
      "name": "com.intellij.ide.ui.OptionsTopHitProvider$Activity",
      "duration": 1656,
      "start": 7271,
      "end": 8928
    }
  ],
  "projectComponents": [
    {
      "name": "com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl",
      "duration": 18,
      "start": 9091,
      "end": 9110
    },
    {
      "name": "com.intellij.openapi.wm.impl.ToolWindowManagerImpl",
      "duration": 18,
      "start": 9110,
      "end": 9129
    },
    {
      "name": "com.intellij.openapi.roots.impl.ProjectRootManagerComponent",
      "duration": 12,
      "start": 9131,
      "end": 9143
    },
    {
      "name": "com.intellij.psi.impl.PsiManagerImpl",
      "duration": 13,
      "start": 9143,
      "end": 9156
    },
    {
      "name": "com.intellij.openapi.module.impl.ModuleManagerComponent",
      "duration": 34,
      "start": 9164,
      "end": 9199
    },
    {
      "name": "com.intellij.openapi.fileEditor.impl.PsiAwareFileEditorManagerImpl",
      "duration": 20,
      "start": 9199,
      "end": 9219
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl",
      "duration": 15,
      "start": 9227,
      "end": 9242
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonListeners",
      "duration": 208,
      "start": 9242,
      "end": 9450
    },
    {
      "name": "com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl",
      "duration": 21,
      "start": 9293,
      "end": 9314
    },
    {
      "name": "com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl",
      "duration": 115,
      "start": 9314,
      "end": 9430
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangeListManagerImpl",
      "duration": 113,
      "start": 9314,
      "end": 9428
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangesViewManager",
      "duration": 66,
      "start": 9320,
      "end": 9386
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.GeneralHighlightingPassFactory",
      "duration": 37,
      "start": 9450,
      "end": 9488
    },
    {
      "name": "com.intellij.codeInsight.navigation.CtrlMouseHandler",
      "duration": 14,
      "start": 9493,
      "end": 9507
    },
    {
      "name": "com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl",
      "duration": 13,
      "start": 9507,
      "end": 9521
    },
    {
      "name": "com.intellij.compiler.CompilerConfigurationImpl",
      "duration": 10,
      "start": 9549,
      "end": 9560
    },
    {
      "name": "com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager",
      "duration": 28,
      "start": 9576,
      "end": 9605
    },
    {
      "name": "com.intellij.xdebugger.impl.XDebuggerManagerImpl",
      "duration": 92,
      "start": 9618,
      "end": 9710
    },
    {
      "name": "com.intellij.execution.scratch.JavaScratchCompilationSupport",
      "duration": 36,
      "start": 9737,
      "end": 9773
    },
    {
      "name": "com.intellij.stats.personalization.impl.UserFactorsManagerImpl",
      "duration": 19,
      "start": 9792,
      "end": 9811
    },
    {
      "name": "com.intellij.tasks.impl.TaskManagerImpl",
      "duration": 42,
      "start": 9811,
      "end": 9854
    },
    {
      "name": "com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager",
      "duration": 178,
      "start": 9854,
      "end": 10033
    },
    {
      "name": "com.intellij.ide.palette.impl.PaletteToolWindowManager",
      "duration": 13,
      "start": 10033,
      "end": 10046
    },
    {
      "name": "com.jetbrains.cidr.lang.hmap.OCHeaderMapManager",
      "duration": 41,
      "start": 10079,
      "end": 10121
    },
    {
      "name": "com.intellij.jpa.JpaProjectComponent",
      "duration": 30,
      "start": 10130,
      "end": 10161
    },
    {
      "name": "com.android.tools.idea.gradle.project.AndroidGradleProjectComponent",
      "duration": 29,
      "start": 10181,
      "end": 10211
    },
    {
      "name": "com.android.tools.idea.res.PsiProjectListener",
      "duration": 11,
      "start": 10211,
      "end": 10222
    },
    {
      "name": "com.intellij.openapi.roots.impl.ModuleRootManagerComponent",
      "duration": 77,
      "start": 10302,
      "end": 10379
    }
  ],
  "projectOptionsTopHitProviders": [
    {
      "name": "com.intellij.application.options.editor.EditorOptionsTopHitProvider",
      "duration": 94,
      "start": 12405,
      "end": 12499
    },
    {
      "name": "com.intellij.application.options.editor.EditorSmartKeysOptionsTopHitProvider",
      "duration": 241,
      "start": 12501,
      "end": 12743
    },
    {
      "name": "com.intellij.application.options.editor.EditorTabsOptionsTopHitProvider",
      "duration": 36,
      "start": 12752,
      "end": 12788
    },
    {
      "name": "com.intellij.application.options.editor.CodeFoldingOptionsTopHitProvider",
      "duration": 82,
      "start": 12789,
      "end": 12872
    },
    {
      "name": "com.intellij.application.options.editor.AutoImportOptionsTopHitProvider",
      "duration": 113,
      "start": 12874,
      "end": 12988
    },
    {
      "name": "org.intellij.images.options.impl.ImagesOptionsTopHitProvider",
      "duration": 100,
      "start": 12993,
      "end": 13093
    },
    {
      "name": "com.intellij.uiDesigner.GuiDesignerOptionsTopHitProvider",
      "duration": 24,
      "start": 13102,
      "end": 13127
    }
  ],
  "totalDurationComputed": 10862,
  "totalDurationActual": 13786
}

function main() {
  ComponentsChartManager
  TimelineChartManager
  TopHitProviderChart

  const container = document.getElementById("visualization")!!
  // const chartManager = new ComponentsChartManager(container)
  // const chartManager = new TimelineChartManager(container)
  const chartManager = new TopHitProviderChart(container)
  chartManager.render(data)

  const global = window as any
  global.lastData = data
  global.chartManager = chartManager
}

main()