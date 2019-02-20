// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import {ComponentsChartManager} from "./ComponentsChartManager"
import {InputData} from "./core"

const data: InputData = {
  "items": [
    {
      "name": "app initialization",
      "duration": 314,
      "start": 1550639300591,
      "end": 1550639300905
    },
    {
      "name": "app components initialization",
      "description": "component count: 101",
      "duration": 3955,
      "start": 1550639300913,
      "end": 1550639304868
    },
    {
      "name": "plugins initialization",
      "description": "plugin count: 189",
      "isSubItem": true,
      "duration": 367,
      "start": 1550639300914,
      "end": 1550639301281
    },
    {
      "name": "plugin descriptors loading",
      "isSubItem": true,
      "duration": 280,
      "start": 1550639300914,
      "end": 1550639301194
    },
    {
      "name": "app components registration",
      "isSubItem": true,
      "duration": 436,
      "start": 1550639301283,
      "end": 1550639301719
    },
    {
      "name": "app components registered callback",
      "isSubItem": true,
      "duration": 117,
      "start": 1550639301719,
      "end": 1550639301836
    },
    {
      "name": "app components creation",
      "isSubItem": true,
      "duration": 3032,
      "start": 1550639301836,
      "end": 1550639304868
    },
    {
      "name": "project components initialization",
      "description": "component count: 211",
      "duration": 1943,
      "start": 1550639305620,
      "end": 1550639307563
    },
    {
      "name": "project components registration",
      "isSubItem": true,
      "duration": 718,
      "start": 1550639305621,
      "end": 1550639306339
    },
    {
      "name": "project components creation",
      "isSubItem": true,
      "duration": 1224,
      "start": 1550639306339,
      "end": 1550639307563
    },
    {
      "name": "project pre-startup",
      "duration": 20,
      "start": 1550639309626,
      "end": 1550639309646
    },
    {
      "name": "project startup",
      "duration": 5,
      "start": 1550639309646,
      "end": 1550639309651
    },
    {
      "name": "default project components creation",
      "duration": 12,
      "start": 1550639309823,
      "end": 1550639309835
    },
    {
      "name": "default project components initialization",
      "description": "component count: 24",
      "isSubItem": true,
      "duration": 12,
      "start": 1550639309823,
      "end": 1550639309835
    },
    {
      "name": "default project components registration",
      "isSubItem": true,
      "duration": 0,
      "start": 1550639309823,
      "end": 1550639309823
    },
    {
      "name": "unknown",
      "duration": 1101,
      "start": 1550639309823,
      "end": 1550639310924
    }
  ],
  "components": [
    {
      "name": "com.intellij.openapi.components.impl.ServiceManagerImpl",
      "duration": 42,
      "start": 1550639301719,
      "end": 1550639301761
    },
    {
      "name": "com.intellij.openapi.util.registry.RegistryState",
      "duration": 119,
      "start": 1550639301839,
      "end": 1550639301958
    },
    {
      "name": "com.intellij.internal.statistic.updater.StatisticsJobsScheduler",
      "duration": 19,
      "start": 1550639301958,
      "end": 1550639301977
    },
    {
      "name": "com.intellij.configurationStore.StoreAwareProjectManager",
      "duration": 127,
      "start": 1550639301978,
      "end": 1550639302105
    },
    {
      "name": "com.intellij.openapi.vfs.PlatformVirtualFileManager",
      "duration": 123,
      "start": 1550639301978,
      "end": 1550639302101
    },
    {
      "name": "com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl",
      "duration": 103,
      "start": 1550639301980,
      "end": 1550639302083
    },
    {
      "name": "com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl",
      "duration": 98,
      "start": 1550639301980,
      "end": 1550639302078
    },
    {
      "name": "com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl",
      "duration": 534,
      "start": 1550639302105,
      "end": 1550639302639
    },
    {
      "name": "com.intellij.openapi.editor.impl.EditorFactoryImpl",
      "duration": 350,
      "start": 1550639302644,
      "end": 1550639302994
    },
    {
      "name": "com.intellij.openapi.actionSystem.impl.ActionManagerImpl",
      "duration": 336,
      "start": 1550639302646,
      "end": 1550639302982
    },
    {
      "name": "com.intellij.openapi.keymap.impl.KeymapManagerImpl",
      "duration": 17,
      "start": 1550639302646,
      "end": 1550639302663
    },
    {
      "name": "com.intellij.history.integration.LocalHistoryImpl",
      "duration": 16,
      "start": 1550639302994,
      "end": 1550639303010
    },
    {
      "name": "com.intellij.ide.ui.laf.LafManagerImpl",
      "duration": 201,
      "start": 1550639303023,
      "end": 1550639303224
    },
    {
      "name": "com.intellij.util.net.ssl.CertificateManager",
      "duration": 70,
      "start": 1550639303249,
      "end": 1550639303319
    },
    {
      "name": "com.intellij.openapi.wm.impl.FocusManagerImpl",
      "duration": 22,
      "start": 1550639303329,
      "end": 1550639303351
    },
    {
      "name": "com.intellij.openapi.wm.impl.WindowManagerImpl",
      "duration": 16,
      "start": 1550639303332,
      "end": 1550639303348
    },
    {
      "name": "com.intellij.ide.IdeTooltipManager",
      "duration": 16,
      "start": 1550639303351,
      "end": 1550639303367
    },
    {
      "name": "com.intellij.ide.MacOSApplicationProvider",
      "duration": 48,
      "start": 1550639303378,
      "end": 1550639303426
    },
    {
      "name": "com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent",
      "duration": 51,
      "start": 1550639303426,
      "end": 1550639303477
    },
    {
      "name": "com.intellij.util.indexing.FileBasedIndexImpl",
      "duration": 564,
      "start": 1550639303477,
      "end": 1550639304041
    },
    {
      "name": "com.intellij.psi.stubs.SerializationManagerImpl",
      "duration": 11,
      "start": 1550639304041,
      "end": 1550639304052
    },
    {
      "name": "com.intellij.psi.stubs.StubIndexImpl",
      "duration": 109,
      "start": 1550639304043,
      "end": 1550639304152
    },
    {
      "name": "com.intellij.openapi.actionSystem.ex.QuickListsManager",
      "duration": 19,
      "start": 1550639304157,
      "end": 1550639304176
    },
    {
      "name": "com.intellij.execution.ExecutorRegistryImpl",
      "duration": 26,
      "start": 1550639304188,
      "end": 1550639304214
    },
    {
      "name": "com.intellij.util.xml.impl.JavaDomApplicationComponent",
      "duration": 15,
      "start": 1550639304216,
      "end": 1550639304231
    },
    {
      "name": "com.intellij.openapi.projectRoots.impl.DefaultJdkConfigurator",
      "duration": 87,
      "start": 1550639304232,
      "end": 1550639304319
    },
    {
      "name": "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl",
      "duration": 13,
      "start": 1550639304330,
      "end": 1550639304343
    },
    {
      "name": "com.intellij.stats.completion.CompletionTrackerInitializer",
      "duration": 11,
      "start": 1550639304349,
      "end": 1550639304360
    },
    {
      "name": "com.intellij.stats.personalization.impl.ApplicationUserFactorStorage",
      "duration": 15,
      "start": 1550639304360,
      "end": 1550639304375
    },
    {
      "name": "com.intellij.completion.FeatureManagerImpl",
      "duration": 14,
      "start": 1550639304375,
      "end": 1550639304389
    },
    {
      "name": "com.jetbrains.cidr.lang.dfa.contextSensitive.OCSourceGliderComponent",
      "duration": 146,
      "start": 1550639304401,
      "end": 1550639304547
    },
    {
      "name": "org.jetbrains.android.AndroidPlugin",
      "duration": 22,
      "start": 1550639304547,
      "end": 1550639304569
    },
    {
      "name": "org.jetbrains.plugins.ruby.gem.GemManager",
      "duration": 12,
      "start": 1550639304602,
      "end": 1550639304614
    },
    {
      "name": "com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl",
      "duration": 23,
      "start": 1550639306358,
      "end": 1550639306381
    },
    {
      "name": "com.intellij.openapi.wm.impl.ToolWindowManagerImpl",
      "duration": 21,
      "start": 1550639306382,
      "end": 1550639306403
    },
    {
      "name": "com.intellij.openapi.roots.impl.ProjectRootManagerComponent",
      "duration": 14,
      "start": 1550639306404,
      "end": 1550639306418
    },
    {
      "name": "com.intellij.psi.impl.PsiManagerImpl",
      "duration": 13,
      "start": 1550639306418,
      "end": 1550639306431
    },
    {
      "name": "com.intellij.openapi.module.impl.ModuleManagerComponent",
      "duration": 33,
      "start": 1550639306439,
      "end": 1550639306472
    },
    {
      "name": "com.intellij.openapi.fileEditor.impl.PsiAwareFileEditorManagerImpl",
      "duration": 21,
      "start": 1550639306473,
      "end": 1550639306494
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl",
      "duration": 15,
      "start": 1550639306501,
      "end": 1550639306516
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.DaemonListeners",
      "duration": 205,
      "start": 1550639306516,
      "end": 1550639306721
    },
    {
      "name": "com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl",
      "duration": 20,
      "start": 1550639306567,
      "end": 1550639306587
    },
    {
      "name": "com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl",
      "duration": 115,
      "start": 1550639306587,
      "end": 1550639306702
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangeListManagerImpl",
      "duration": 114,
      "start": 1550639306587,
      "end": 1550639306701
    },
    {
      "name": "com.intellij.openapi.vcs.changes.ChangesViewManager",
      "duration": 68,
      "start": 1550639306593,
      "end": 1550639306661
    },
    {
      "name": "com.intellij.codeInsight.daemon.impl.GeneralHighlightingPassFactory",
      "duration": 35,
      "start": 1550639306721,
      "end": 1550639306756
    },
    {
      "name": "com.intellij.codeInsight.navigation.CtrlMouseHandler",
      "duration": 13,
      "start": 1550639306761,
      "end": 1550639306774
    },
    {
      "name": "com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl",
      "duration": 15,
      "start": 1550639306774,
      "end": 1550639306789
    },
    {
      "name": "com.intellij.packaging.impl.artifacts.ArtifactManagerImpl",
      "duration": 12,
      "start": 1550639306806,
      "end": 1550639306818
    },
    {
      "name": "com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager",
      "duration": 28,
      "start": 1550639306847,
      "end": 1550639306875
    },
    {
      "name": "com.intellij.xdebugger.impl.XDebuggerManagerImpl",
      "duration": 91,
      "start": 1550639306887,
      "end": 1550639306978
    },
    {
      "name": "com.intellij.execution.scratch.JavaScratchCompilationSupport",
      "duration": 42,
      "start": 1550639307007,
      "end": 1550639307049
    },
    {
      "name": "com.intellij.stats.personalization.impl.UserFactorsManagerImpl",
      "duration": 15,
      "start": 1550639307071,
      "end": 1550639307086
    },
    {
      "name": "com.intellij.tasks.impl.TaskManagerImpl",
      "duration": 41,
      "start": 1550639307086,
      "end": 1550639307127
    },
    {
      "name": "com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager",
      "duration": 199,
      "start": 1550639307127,
      "end": 1550639307326
    },
    {
      "name": "com.intellij.ide.palette.impl.PaletteToolWindowManager",
      "duration": 13,
      "start": 1550639307326,
      "end": 1550639307339
    },
    {
      "name": "com.intellij.jpa.JpaProjectComponent",
      "duration": 29,
      "start": 1550639307340,
      "end": 1550639307369
    },
    {
      "name": "com.intellij.j2ee.web.WebProjectComponent",
      "duration": 15,
      "start": 1550639307370,
      "end": 1550639307385
    },
    {
      "name": "com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache",
      "duration": 11,
      "start": 1550639307420,
      "end": 1550639307431
    },
    {
      "name": "com.jetbrains.cidr.lang.hmap.OCHeaderMapManager",
      "duration": 43,
      "start": 1550639307432,
      "end": 1550639307475
    },
    {
      "name": "com.android.tools.idea.gradle.project.AndroidGradleProjectComponent",
      "duration": 29,
      "start": 1550639307485,
      "end": 1550639307514
    },
    {
      "name": "com.android.tools.idea.res.PsiProjectListener",
      "duration": 12,
      "start": 1550639307514,
      "end": 1550639307526
    },
    {
      "name": "com.intellij.openapi.roots.impl.ModuleRootManagerComponent",
      "duration": 81,
      "start": 1550639307592,
      "end": 1550639307673
    }
  ],
  "preloadActivities": [
    {
      "name": "com.intellij.ide.ui.OptionsTopHitProvider$Activity",
      "duration": 1637,
      "start": 1550639304712,
      "end": 1550639306349
    }
  ],
  "totalDurationComputed": 7350,
  "totalDurationActual": 10333
}

function main() {
  const chartManager = new ComponentsChartManager(document.getElementById("componentsVisualization")!!)
  chartManager.render(data);
  (window as any).chartManager = chartManager
}

main()