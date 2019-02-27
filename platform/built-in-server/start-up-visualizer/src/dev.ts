// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import {InputData} from "./core"
import {TimelineChartManager} from "./TimeLineChartManager"
import {ComponentChartManager, TopHitProviderChart} from "./ItemChartManager"

const data: InputData = {
  "version": "1",
  "items": [
    {
      "name": "app initialization preparation",
      "duration": 2773,
      "start": 1,
      "end": 2774
    },
    {
      "name": "app initialization",
      "duration": 295,
      "start": 2774,
      "end": 3069
    },
    {
      "name": "plugins initialization",
      "description": "plugin count: 189",
      "duration": 327,
      "start": 3073,
      "end": 3400
    },
    {
      "name": "plugin descriptors loading",
      "duration": 241,
      "start": 3074,
      "end": 3315
    },
    {
      "name": "app components initialization",
      "description": "component count: 101",
      "duration": 3120,
      "start": 3405,
      "end": 6525
    },
    {
      "name": "app components registration",
      "duration": 381,
      "start": 3405,
      "end": 3786
    },
    {
      "name": "app components registered callback",
      "duration": 111,
      "start": 3786,
      "end": 3897
    },
    {
      "name": "app components creation",
      "duration": 2627,
      "start": 3897,
      "end": 6525
    },
    {
      "name": "app initialized callback",
      "duration": 229,
      "start": 6525,
      "end": 6754
    },
    {
      "name": "project components initialization",
      "description": "component count: 210",
      "duration": 2026,
      "start": 7547,
      "end": 9574
    },
    {
      "name": "project components registration",
      "duration": 776,
      "start": 7547,
      "end": 8324
    },
    {
      "name": "project components creation",
      "duration": 1250,
      "start": 8324,
      "end": 9574
    },
    {
      "name": "module loading",
      "description": "module count: 1",
      "duration": 143,
      "start": 9575,
      "end": 9718
    },
    {
      "name": "project pre-startup",
      "duration": 26,
      "start": 11507,
      "end": 11533
    },
    {
      "name": "project startup",
      "duration": 0,
      "start": 11533,
      "end": 11533
    },
    {
      "name": "default project components initialization",
      "description": "component count: 24",
      "duration": 11,
      "start": 11718,
      "end": 11730
    },
    {
      "name": "default project components registration",
      "duration": 0,
      "start": 11718,
      "end": 11718
    },
    {
      "name": "default project components creation",
      "duration": 11,
      "start": 11718,
      "end": 11730
    },
    {
      "name": "unknown",
      "duration": 1475,
      "start": 11730,
      "end": 13205
    }
  ],
  "appComponents": [
    {
      "name": "com.intellij.openapi.components.impl.ServiceManagerImpl",
      "duration": 37,
      "start": 3786,
      "end": 3823
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryState",
      "duration": 101,
      "start": 3901,
      "end": 4003
    },
    {
      "name": "com.intellij.internal.statistic.updater.StatisticsJobsScheduler",
      "duration": 15,
      "start": 4003,
      "end": 4019
    },
    {
      "name": "com.intellij.configurationStore.StoreAwareProjectManager",
      "duration": 124,
      "start": 4019,
      "end": 4143
    },
    {
      "name": "com.intellij.openapi.vfs.PlatformVirtualFileManager",
      "duration": 121,
      "start": 4019,
      "end": 4140
    },
    {
      "name": "com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl",
      "duration": 101,
      "start": 4022,
      "end": 4124
    },
    {
      "name": "com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl",
      "duration": 95,
      "start": 4022,
      "end": 4118
    },
    {
      "name": "com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl",
      "duration": 480,
      "start": 4143,
      "end": 4624
    },
    {
      "name": "com.intellij.openapi.editor.impl.EditorFactoryImpl",
      "duration": 314,
      "start": 4630,
      "end": 4944
    },
    {
      "name": "com.intellij.openapi.actionSystem.impl.ActionManagerImpl",
      "duration": 300,
      "start": 4631,
      "end": 4932
    },
    {
      "name": "com.intellij.openapi.keymap.impl.KeymapManagerImpl",
      "duration": 16,
      "start": 4631,
      "end": 4647
    },
    {
      "name": "com.intellij.history.integration.LocalHistoryImpl",
      "duration": 14,
      "start": 4944,
      "end": 4959
    },
    {
      "name": "com.intellij.ide.ui.laf.LafManagerImpl",
      "duration": 218,
      "start": 4973,
      "end": 5191
    },
    {
      "name": "com.intellij.util.net.ssl.CertificateManager",
      "duration": 66,
      "start": 5218,
      "end": 5285
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryExtensionCollector",
      "duration": 13,
      "start": 5291,
      "end": 5305
    },
    {
      "name": "com.intellij.openapi.wm.impl.FocusManagerImpl",
      "duration": 20,
      "start": 5305,
      "end": 5326
    },
    {
      "name": "com.intellij.openapi.wm.impl.WindowManagerImpl",
      "duration": 15,
      "start": 5307,
      "end": 5323
    },
    {
      "name": "com.intellij.ide.IdeTooltipManager",
      "duration": 16,
      "start": 5326,
      "end": 5342
    },
    {
      "name": "com.intellij.ide.MacOSApplicationProvider",
      "duration": 22,
      "start": 5354,
      "end": 5376
    },
    {
      "name": "com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent",
      "duration": 54,
      "start": 5376,
      "end": 5431
    },
    {
      "name": "com.intellij.util.indexing.FileBasedIndexImpl",
      "duration": 550,
      "start": 5431,
      "end": 5982
    },
    {
      "name": "com.intellij.psi.stubs.SerializationManagerImpl",
      "duration": 11,
      "start": 5982,
      "end": 5993
    },
    {
      "name": "com.intellij.psi.stubs.StubIndexImpl",
      "duration": 95,
      "start": 5983,
      "end": 6078
    },
    {
      "name": "com.intellij.openapi.actionSystem.ex.QuickListsManager",
      "duration": 15,
      "start": 6083,
      "end": 6098
    },
    {
      "name": "com.intellij.execution.ExecutorRegistryImpl",
      "duration": 21,
      "start": 6108,
      "end": 6130
    },
    {
      "name": "com.intellij.util.xml.impl.JavaDomApplicationComponent",
      "duration": 11,
      "start": 6131,
      "end": 6143
    },
    {
      "name": "com.intellij.openapi.projectRoots.impl.DefaultJdkConfigurator",
      "duration": 84,
      "start": 6144,
      "end": 6228
    },
    {
      "name": "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl",
      "duration": 11,
      "start": 6238,
      "end": 6249
    },
    {
      "name": "com.intellij.stats.completion.CompletionTrackerInitializer",
      "duration": 10,
      "start": 6257,
      "end": 6268
    },
    {
      "name": "com.intellij.stats.personalization.impl.ApplicationUserFactorStorage",
      "duration": 15,
      "start": 6268,
      "end": 6283
    },
    {
      "name": "com.intellij.completion.FeatureManagerImpl",
      "duration": 13,
      "start": 6283,
      "end": 6297
    },
    {
      "name": "com.jetbrains.cidr.lang.dfa.contextSensitive.OCSourceGliderComponent",
      "duration": 149,
      "start": 6298,
      "end": 6447
    },
    {
      "name": "org.jetbrains.android.AndroidPlugin",
      "duration": 18,
      "start": 6458,
      "end": 6477
    },
    {
      "name": "org.jetbrains.plugins.ruby.gem.GemManager",
      "duration": 10,
      "start": 6511,
      "end": 6522
    }
  ],
  "appOptionsTopHitProviders": [
    {
      "name": "com.intellij.codeInsight.intention.impl.config.IntentionsOptionsTopHitProvider",
      "duration": 1261,
      "start": 6633,
      "end": 7894
    },
    {
      "name": "com.intellij.codeInsight.template.impl.LiveTemplatesOptionsTopHitProvider",
      "duration": 311,
      "start": 7894,
      "end": 8206
    }
  ],
  "preloadActivities": [
    {
      "name": "com.intellij.ide.ui.OptionsTopHitProvider$Activity",
      "duration": 1605,
      "start": 6601,
      "end": 8206
    }
  ],
  "projectComponents": [
    {
      "name": "com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl",
      "duration": 22,
      "start": 8343,
      "end": 8366
    },
    {
      "name": "com.intellij.openapi.wm.impl.ToolWindowManagerImpl",
      "duration": 20,
      "start": 8367,
      "end": 8387
    },
    {
      "name": "com.intellij.openapi.roots.impl.ProjectRootManagerComponent",
      "duration": 14,
      "start": 8388,
      "end": 8402
    },
    {
      "name": "com.intellij.psi.impl.PsiManagerImpl",
      "duration": 20,
      "start": 8402,
      "end": 8423
    },
    {
      "name": "com.intellij.openapi.module.impl.ModuleManagerComponent",
      "duration": 39,
      "start": 8433,
      "end": 8473
    },
    {
      "name": "com.intellij.openapi.fileEditor.impl.PsiAwareFileEditorManagerImpl",
      "duration": 21,
      "start": 8474,
      "end": 8495
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl",
      "duration": 16,
      "start": 8502,
      "end": 8519
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonListeners",
      "duration": 200,
      "start": 8519,
      "end": 8720
    },
    {
      "name": "com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl",
      "duration": 21,
      "start": 8569,
      "end": 8591
    },
    {
      "name": "com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl",
      "duration": 109,
      "start": 8591,
      "end": 8701
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangeListManagerImpl",
      "duration": 108,
      "start": 8591,
      "end": 8700
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangesViewManager",
      "duration": 63,
      "start": 8597,
      "end": 8660
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.GeneralHighlightingPassFactory",
      "duration": 33,
      "start": 8720,
      "end": 8754
    },
    {
      "name": "com.intellij.codeInsight.navigation.CtrlMouseHandler",
      "duration": 16,
      "start": 8759,
      "end": 8775
    },
    {
      "name": "com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl",
      "duration": 14,
      "start": 8775,
      "end": 8790
    },
    {
      "name": "com.intellij.compiler.CompilerConfigurationImpl",
      "duration": 10,
      "start": 8817,
      "end": 8828
    },
    {
      "name": "com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager",
      "duration": 29,
      "start": 8848,
      "end": 8877
    },
    {
      "name": "com.intellij.xdebugger.impl.XDebuggerManagerImpl",
      "duration": 98,
      "start": 8891,
      "end": 8990
    },
    {
      "name": "com.intellij.execution.scratch.JavaScratchCompilationSupport",
      "duration": 37,
      "start": 9019,
      "end": 9056
    },
    {
      "name": "com.intellij.stats.personalization.impl.UserFactorsManagerImpl",
      "duration": 22,
      "start": 9075,
      "end": 9098
    },
    {
      "name": "com.intellij.tasks.impl.TaskManagerImpl",
      "duration": 42,
      "start": 9098,
      "end": 9141
    },
    {
      "name": "com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager",
      "duration": 191,
      "start": 9141,
      "end": 9332
    },
    {
      "name": "com.intellij.ide.palette.impl.PaletteToolWindowManager",
      "duration": 15,
      "start": 9332,
      "end": 9347
    },
    {
      "name": "com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache",
      "duration": 12,
      "start": 9373,
      "end": 9385
    },
    {
      "name": "com.jetbrains.cidr.lang.hmap.OCHeaderMapManager",
      "duration": 42,
      "start": 9386,
      "end": 9429
    },
    {
      "name": "com.intellij.jpa.JpaProjectComponent",
      "duration": 31,
      "start": 9437,
      "end": 9469
    },
    {
      "name": "com.android.tools.idea.gradle.project.AndroidGradleProjectComponent",
      "duration": 33,
      "start": 9490,
      "end": 9523
    },
    {
      "name": "com.android.tools.idea.res.PsiProjectListener",
      "duration": 12,
      "start": 9523,
      "end": 9535
    },
    {
      "name": "com.intellij.openapi.roots.impl.ModuleRootManagerComponent",
      "duration": 80,
      "start": 9606,
      "end": 9687
    }
  ],
  "projectOptionsTopHitProviders": [
    {
      "name": "com.intellij.application.options.editor.EditorOptionsTopHitProvider",
      "duration": 83,
      "start": 11861,
      "end": 11944
    },
    {
      "name": "com.intellij.application.options.editor.EditorSmartKeysOptionsTopHitProvider",
      "duration": 241,
      "start": 11946,
      "end": 12187
    },
    {
      "name": "com.intellij.application.options.editor.EditorTabsOptionsTopHitProvider",
      "duration": 29,
      "start": 12193,
      "end": 12223
    },
    {
      "name": "com.intellij.application.options.editor.CodeFoldingOptionsTopHitProvider",
      "duration": 69,
      "start": 12224,
      "end": 12293
    },
    {
      "name": "com.intellij.application.options.editor.AutoImportOptionsTopHitProvider",
      "duration": 85,
      "start": 12296,
      "end": 12381
    },
    {
      "name": "org.intellij.images.options.impl.ImagesOptionsTopHitProvider",
      "duration": 80,
      "start": 12384,
      "end": 12464
    },
    {
      "name": "com.intellij.uiDesigner.GuiDesignerOptionsTopHitProvider",
      "duration": 25,
      "start": 12472,
      "end": 12498
    }
  ],
  "totalDurationComputed": 10427,
  "totalDurationActual": 13205
}

function main() {
  ComponentChartManager
  TimelineChartManager
  TopHitProviderChart

  const container = document.getElementById("visualization")!!
  // const chartManager = new ComponentChartManager(container)
  // const chartManager = new TimelineChartManager(container)
  const chartManager = new TopHitProviderChart(container)
  chartManager.render(data)

  const global = window as any
  global.lastData = data
  global.chartManager = chartManager
}

main()