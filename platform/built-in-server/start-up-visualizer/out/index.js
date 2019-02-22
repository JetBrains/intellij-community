define("core", ["require", "exports", "@amcharts/amcharts4/charts", "@amcharts/amcharts4/core"], function (require, exports, am4charts, am4core) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    function configureCommonChartSettings(chart) {
        chart.exporting.menu = new am4core.ExportMenu();
        chart.mouseWheelBehavior = "zoomX";
        chart.scrollbarX = new am4core.Scrollbar();
        // chart.cursor = new am4charts.XYCursor()
    }
    class XYChartManager {
        constructor(container) {
            this.chart = am4core.create(container, am4charts.XYChart);
            configureCommonChartSettings(this.chart);
            if (module != null && module.hot != null) {
                let devState = null;
                const handler = () => {
                    const axis = this.chart.xAxes.getIndex(0);
                    if (devState == null) {
                        devState = {
                            start: axis.start,
                            end: axis.end,
                        };
                    }
                    else {
                        devState.start = axis.start;
                        devState.end = axis.end;
                    }
                    sessionStorage.setItem("devState", JSON.stringify(devState));
                };
                setTimeout(() => {
                    // noinspection SpellCheckingInspection
                    this.chart.xAxes.getIndex(0).events.on("startchanged", handler);
                    // noinspection SpellCheckingInspection
                    this.chart.xAxes.getIndex(0).events.on("endchanged", handler);
                }, 1000);
                // strange, but doesn't work
                // module.hot.dispose(() => {
                //   if (devState == null) {
                //     sessionStorage.removeItem("devState")
                //   }
                //   else {
                //     sessionStorage.setItem("devState", JSON.stringify(devState))
                //   }
                // })
                const devStateRaw = sessionStorage.getItem("devState");
                if (devStateRaw != null) {
                    this.chart.events.on("ready", () => {
                        const devState = JSON.parse(devStateRaw);
                        const axis = this.chart.xAxes.getIndex(0);
                        axis.start = devState.start;
                        axis.end = devState.end;
                    });
                }
            }
        }
    }
    exports.XYChartManager = XYChartManager;
    function getInputElement(id) {
        return document.getElementById(id);
    }
    exports.getInputElement = getInputElement;
    function getButtonElement(id) {
        return document.getElementById(id);
    }
    exports.getButtonElement = getButtonElement;
});
define("ComponentsChartManager", ["require", "exports", "@amcharts/amcharts4/charts", "core"], function (require, exports, am4charts, core_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    class ComponentsChartManager extends core_1.XYChartManager {
        // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
        constructor(container) {
            super(container);
            this.configureNameAxis();
            this.configureDurationAxis();
            this.configureSeries();
        }
        configureNameAxis() {
            const nameAxis = this.chart.xAxes.push(new am4charts.CategoryAxis());
            nameAxis.dataFields.category = "shortName";
            // allow to copy text
            const nameAxisLabel = nameAxis.renderer.labels.template;
            nameAxisLabel.selectable = true;
            nameAxisLabel.fontSize = 12;
            // https://github.com/amcharts/amcharts4/issues/997
            nameAxisLabel.rotation = -45;
            nameAxisLabel.verticalCenter = "middle";
            nameAxisLabel.horizontalCenter = "right";
            nameAxis.renderer.minGridDistance = 1;
            nameAxis.renderer.grid.template.location = 0;
            nameAxis.renderer.grid.template.disabled = true;
        }
        configureDurationAxis() {
            const durationAxis = this.chart.yAxes.push(new am4charts.DurationAxis());
            durationAxis.title.text = "Duration";
            // base unit the values are in (https://www.amcharts.com/docs/v4/reference/durationformatter/)
            durationAxis.durationFormatter.baseUnit = "millisecond";
            durationAxis.durationFormatter.durationFormat = "S";
        }
        configureSeries() {
            const series = this.chart.series.push(new am4charts.ColumnSeries());
            series.dataFields.dateX = "start";
            series.dataFields.categoryX = "shortName";
            series.dataFields.valueY = "duration";
            series.columns.template.tooltipText = "{name}: {duration} ms";
        }
        render(data) {
            const components = data.components;
            if (components == null || components.length === 0) {
                this.chart.data = [];
                return;
            }
            for (const component of components) {
                const componentItem = component;
                const lastDotIndex = component.name.lastIndexOf(".");
                componentItem.shortName = lastDotIndex < 0 ? component.name : component.name.substring(lastDotIndex + 1);
            }
            this.chart.data = components;
        }
    }
    exports.ComponentsChartManager = ComponentsChartManager;
});
define("timeLineChartHelper", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    function computeLevels(items) {
        let lastAllocatedColorIndex = 0;
        for (let i = 0; i < items.length; i++) {
            const item = items[i];
            let level = 0;
            for (let j = i - 1; j >= 0; j--) {
                const prevItem = items[j];
                if (prevItem.end >= item.end) {
                    level = prevItem.level + 1;
                    item.colorIndex = prevItem.colorIndex;
                    break;
                }
            }
            if (item.colorIndex === undefined) {
                item.colorIndex = lastAllocatedColorIndex++;
            }
            item.level = level;
        }
    }
    exports.computeLevels = computeLevels;
});
define("TimeLineChartManager", ["require", "exports", "core", "@amcharts/amcharts4/charts", "@amcharts/amcharts4/core", "timeLineChartHelper"], function (require, exports, core_2, am4charts, am4core, timeLineChartHelper_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    // https://github.com/almende/vis/blob/master/examples/timeline/dataHandling/dataSerialization.html
    // do not group because it makes hard to understand results
    // (executed sequentially, so, we need to see it sequentially from left to right)
    const isCreateGroups = false;
    const groups = isCreateGroups ? [
        { id: "application components" },
        { id: "project components" },
    ] : null;
    class TimelineChartManager extends core_2.XYChartManager {
        constructor(container) {
            super(container);
            const chart = this.chart;
            this.configureDurationAxis();
            const levelAxis = this.configureLevelAxis();
            this.configureSeries();
            this.addHeightAdjuster(levelAxis);
        }
        configureLevelAxis() {
            const levelAxis = this.chart.yAxes.push(new am4charts.CategoryAxis());
            levelAxis.dataFields.category = "rowIndex";
            levelAxis.renderer.grid.template.location = 0;
            levelAxis.renderer.minGridDistance = 1;
            levelAxis.renderer.labels.template.disabled = true;
            // levelAxis.renderer.grid.template.disabled = true
            return levelAxis;
        }
        configureDurationAxis() {
            const durationAxis = this.chart.xAxes.push(new am4charts.DurationAxis());
            durationAxis.durationFormatter.baseUnit = "millisecond";
            durationAxis.durationFormatter.durationFormat = "S";
            durationAxis.min = 0;
            durationAxis.strictMinMax = true;
            // durationAxis.renderer.grid.template.disabled = true
        }
        configureSeries() {
            const series = this.chart.series.push(new am4charts.ColumnSeries());
            // series.columns.template.width = am4core.percent(80)
            series.columns.template.tooltipText = "{name}: {duration}\nlevel: {level}";
            series.dataFields.openDateX = "start";
            series.dataFields.openValueX = "start";
            series.dataFields.dateX = "end";
            series.dataFields.valueX = "end";
            series.dataFields.categoryY = "rowIndex";
            series.columns.template.propertyFields.fill = "color";
            series.columns.template.propertyFields.stroke = "color";
            // series.columns.template.strokeOpacity = 1
            const valueLabel = series.bullets.push(new am4charts.LabelBullet());
            valueLabel.label.text = "{name}";
            valueLabel.label.truncate = false;
            valueLabel.label.hideOversized = false;
            valueLabel.label.horizontalCenter = "left";
            // valueLabel.label.fill = am4core.color("#fff")
            valueLabel.locationX = 1;
            // https://github.com/amcharts/amcharts4/issues/668#issuecomment-446655416
            valueLabel.interactionsEnabled = false;
            // valueLabel.label.fontSize = 12
        }
        addHeightAdjuster(levelAxis) {
            // https://www.amcharts.com/docs/v4/tutorials/auto-adjusting-chart-height-based-on-a-number-of-data-items/
            // noinspection SpellCheckingInspection
            this.chart.events.on("datavalidated", () => {
                const grid = this.chart.yAxes.getIndex(0).renderer.grid;
                // hide all grid lines except first and last
                for (let i = 1; i < (grid.length - 1); i++) {
                    grid.getIndex(i).disabled = true;
                }
                const chart = this.chart;
                const adjustHeight = chart.data.reduce((max, item) => Math.max(item.rowIndex, max), 0) * 35 - levelAxis.pixelHeight;
                // get current chart height
                let targetHeight = chart.pixelHeight + adjustHeight;
                // Set it on chart's container
                chart.svgContainer.htmlElement.style.height = targetHeight + "px";
            });
        }
        render(ijData) {
            const items = ijData.items;
            const firstStart = new Date(items[0].start);
            const timeOffset = 0;
            const data = transformIjData(ijData, timeOffset);
            this.chart.data = data;
            const originalItems = items;
            const durationAxis = this.chart.xAxes.getIndex(0);
            durationAxis.max = originalItems[originalItems.length - 1].end - timeOffset;
        }
    }
    exports.TimelineChartManager = TimelineChartManager;
    function transformIjData(input, timeOffset) {
        const colorSet = new am4core.ColorSet();
        const transformedItems = new Array(input.items.length);
        timeLineChartHelper_1.computeLevels(input.items);
        // we cannot use actual level as row index because in this case labels will be overlapped, so,
        // row index simply incremented till empirical limit (6).
        let rowIndex = 0;
        for (let i = 0; i < input.items.length; i++) {
            const item = input.items[i];
            if (rowIndex > 5 && item.level === 0) {
                rowIndex = 0;
            }
            const result = {
                name: item.name,
                start: item.start - timeOffset,
                end: item.end - timeOffset,
                duration: item.duration,
                rowIndex: rowIndex++,
                color: colorSet.getIndex(item.colorIndex),
                level: item.level,
            };
            transformedItems[i] = result;
        }
        transformedItems.sort((a, b) => a.rowIndex - b.rowIndex);
        return transformedItems;
    }
});
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
define("dev", ["require", "exports", "TimeLineChartManager"], function (require, exports, TimeLineChartManager_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const data = {
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
    };
    function main() {
        const container = document.getElementById("visualization");
        // const chartManager = new ComponentsChartManager(container)
        const chartManager = new TimeLineChartManager_1.TimelineChartManager(container);
        chartManager.render(data);
        const global = window;
        global.lastData = data;
        global.chartManager = chartManager;
    }
    main();
});
define("main", ["require", "exports", "@amcharts/amcharts4/core", "@amcharts/amcharts4/themes/animated", "ComponentsChartManager", "TimeLineChartManager", "core"], function (require, exports, am4core, animated_1, ComponentsChartManager_1, TimeLineChartManager_2, core_3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const storageKeyPort = "ijPort";
    const storageKeyData = "inputIjFormat";
    function main() {
        am4core.useTheme(animated_1.default);
        const chartManagers = [
            new TimeLineChartManager_2.TimelineChartManager(document.getElementById("visualization")),
            new ComponentsChartManager_1.ComponentsChartManager(document.getElementById("componentsVisualization")),
        ];
        // debug
        const global = window;
        global.timelineChart = chartManagers[0];
        global.componentsChart = chartManagers[1];
        configureInput(data => {
            global.lastData = data;
            for (const chartManager of chartManagers) {
                chartManager.render(data);
            }
        });
    }
    exports.main = main;
    function configureInput(dataListener) {
        const inputElement = core_3.getInputElement("ijInput");
        function callListener(rawData) {
            dataListener(JSON.parse(rawData));
        }
        function setInput(rawData) {
            if (rawData != null && rawData.length !== 0) {
                inputElement.value = rawData;
                callListener(rawData);
            }
        }
        document.addEventListener("DOMContentLoaded", () => {
            getPortInputElement().value = localStorage.getItem(storageKeyPort) || "63342";
            setInput(localStorage.getItem(storageKeyData));
        });
        function grabFromRunningInstance(port) {
            fetch(`http://localhost:${port}/api/about/?startUpMeasurement`, { credentials: "omit" })
                .then(it => it.json())
                .then(json => {
                const data = json.startUpMeasurement;
                if (data == null) {
                    const message = "IntelliJ Platform IDE didn't report startup measurement result";
                    console.error(message, json);
                    alert(message);
                    return;
                }
                const rawData = JSON.stringify(data, null, 2);
                localStorage.setItem(storageKeyData, rawData);
                setInput(rawData);
            });
        }
        core_3.getButtonElement("grabButton").addEventListener("click", () => {
            // use parseInt to validate input
            let port = getPortInputElement().value;
            if (port.length === 0) {
                port = "63342";
            }
            else if (!/^\d+$/.test(port)) {
                throw new Error("Port number value is not numeric");
            }
            localStorage.setItem(storageKeyPort, port);
            grabFromRunningInstance(port);
        });
        core_3.getButtonElement("grabDevButton").addEventListener("click", () => {
            grabFromRunningInstance("63343");
        });
        inputElement.addEventListener("input", () => {
            const rawData = inputElement.value.trim();
            localStorage.setItem(storageKeyData, rawData);
            callListener(rawData);
        });
    }
    function getPortInputElement() {
        return core_3.getInputElement("ijPort");
    }
});
define("timeLineChartHelper.test", ["require", "exports", "timeLineChartHelper"], function (require, exports, timeLineChartHelper_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    test("adds 1 + 2 to equal 3", () => {
        const items = [
            {
                "name": "default project components creation",
                "duration": 13,
                "start": 1550854187320,
                "end": 1550854187333
            },
            {
                "name": "default project components registration",
                "isSubItem": true,
                "duration": 0,
                "start": 1550854187320,
                "end": 1550854187320
            },
            {
                "name": "default project components initialization",
                "description": "component count: 24",
                "isSubItem": true,
                "duration": 13,
                "start": 1550854187320,
                "end": 1550854187333
            },
        ];
        timeLineChartHelper_2.computeLevels(items);
        expect(items.map(it => {
            return { name: it.name, level: it.level };
        })).toMatchObject([
            {
                "name": "default project components creation",
                "level": 0,
            },
            {
                "name": "default project components registration",
                "level": 1,
            },
            {
                "name": "default project components initialization",
                "level": 1,
            },
        ]);
    });
});
