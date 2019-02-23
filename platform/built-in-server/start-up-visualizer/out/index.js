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
    function disableGridButKeepBorderLines(axis) {
        axis.renderer.grid.template.adapter.add("disabled", (_, target) => {
            if (target.dataItem == null) {
                return false;
            }
            const index = target.dataItem.index;
            return !(index === 0 || index === -1);
        });
    }
    exports.disableGridButKeepBorderLines = disableGridButKeepBorderLines;
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
            this.maxRowIndex = 0;
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
            timeLineChartHelper_1.disableGridButKeepBorderLines(levelAxis);
            levelAxis.renderer.labels.template.disabled = true;
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
            series.columns.template.tooltipText = "{name}: {duration}\nlevel: {level}\nrange: {start}-{end}\n{description}";
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
                const chart = this.chart;
                const targetHeight = chart.pixelHeight + ((this.maxRowIndex + 1) * 30 - levelAxis.pixelHeight);
                chart.svgContainer.htmlElement.style.height = targetHeight + "px";
            });
        }
        render(ijData) {
            const items = ijData.items;
            const firstStart = new Date(items[0].start);
            const data = this.transformIjData(ijData);
            this.chart.data = data;
            const originalItems = items;
            const durationAxis = this.chart.xAxes.getIndex(0);
            durationAxis.max = originalItems[originalItems.length - 1].end;
        }
        transformIjData(input) {
            const colorSet = new am4core.ColorSet();
            const transformedItems = new Array(input.items.length);
            timeLineChartHelper_1.computeLevels(input.items);
            // we cannot use actual level as row index because in this case labels will be overlapped, so,
            // row index simply incremented till empirical limit.
            let rowIndex = 0;
            this.maxRowIndex = 0;
            for (let i = 0; i < input.items.length; i++) {
                const item = input.items[i];
                if (rowIndex > 5 && item.level === 0) {
                    rowIndex = 0;
                }
                else if (rowIndex > this.maxRowIndex) {
                    this.maxRowIndex = rowIndex;
                }
                const result = Object.assign({}, item, { rowIndex: rowIndex++, color: colorSet.getIndex(item.colorIndex) });
                transformedItems[i] = result;
            }
            transformedItems.sort((a, b) => a.rowIndex - b.rowIndex);
            return transformedItems;
        }
    }
    exports.TimelineChartManager = TimelineChartManager;
});
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
define("dev", ["require", "exports", "TimeLineChartManager"], function (require, exports, TimeLineChartManager_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const data = {
        "version": "1",
        "items": [
            {
                "name": "app initialization preparation",
                "duration": 3148,
                "start": 0,
                "end": 3148
            },
            {
                "name": "app initialization",
                "duration": 301,
                "start": 3148,
                "end": 3450
            },
            {
                "name": "plugins initialization",
                "description": "plugin count: 189",
                "duration": 398,
                "start": 3453,
                "end": 3852
            },
            {
                "name": "plugin descriptors loading",
                "duration": 298,
                "start": 3454,
                "end": 3753
            },
            {
                "name": "app components initialization",
                "description": "component count: 101",
                "duration": 3422,
                "start": 3856,
                "end": 7279
            },
            {
                "name": "app components registration",
                "duration": 368,
                "start": 3856,
                "end": 4225
            },
            {
                "name": "app components registered callback",
                "duration": 126,
                "start": 4225,
                "end": 4351
            },
            {
                "name": "app components creation",
                "duration": 2927,
                "start": 4351,
                "end": 7279
            },
            {
                "name": "app initialized callback",
                "duration": 248,
                "start": 7279,
                "end": 7528
            },
            {
                "name": "project components initialization",
                "description": "component count: 210",
                "duration": 1992,
                "start": 8804,
                "end": 10797
            },
            {
                "name": "project components registration",
                "duration": 639,
                "start": 8804,
                "end": 9444
            },
            {
                "name": "project components creation",
                "duration": 1352,
                "start": 9444,
                "end": 10797
            },
            {
                "name": "module loading",
                "description": "module count: 1",
                "duration": 166,
                "start": 10797,
                "end": 10964
            },
            {
                "name": "project pre-startup",
                "duration": 21,
                "start": 12772,
                "end": 12793
            },
            {
                "name": "project startup",
                "duration": 3,
                "start": 12793,
                "end": 12797
            },
            {
                "name": "default project components initialization",
                "description": "component count: 24",
                "duration": 13,
                "start": 13017,
                "end": 13030
            },
            {
                "name": "default project components registration",
                "duration": 0,
                "start": 13017,
                "end": 13018
            },
            {
                "name": "default project components creation",
                "duration": 12,
                "start": 13018,
                "end": 13030
            },
            {
                "name": "unknown",
                "duration": 1232,
                "start": 13030,
                "end": 14262
            }
        ],
        "components": [
            {
                "name": "com.intellij.openapi.components.impl.ServiceManagerImpl",
                "duration": 43,
                "start": 4225,
                "end": 4268
            },
            {
                "name": "com.intellij.openapi.util.registry.RegistryState",
                "duration": 109,
                "start": 4356,
                "end": 4465
            },
            {
                "name": "com.intellij.internal.statistic.updater.StatisticsJobsScheduler",
                "duration": 17,
                "start": 4465,
                "end": 4482
            },
            {
                "name": "com.intellij.configurationStore.StoreAwareProjectManager",
                "duration": 130,
                "start": 4482,
                "end": 4613
            },
            {
                "name": "com.intellij.openapi.vfs.PlatformVirtualFileManager",
                "duration": 127,
                "start": 4482,
                "end": 4609
            },
            {
                "name": "com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl",
                "duration": 104,
                "start": 4485,
                "end": 4590
            },
            {
                "name": "com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl",
                "duration": 98,
                "start": 4485,
                "end": 4583
            },
            {
                "name": "com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl",
                "duration": 531,
                "start": 4613,
                "end": 5145
            },
            {
                "name": "com.intellij.openapi.editor.impl.EditorFactoryImpl",
                "duration": 360,
                "start": 5150,
                "end": 5511
            },
            {
                "name": "com.intellij.openapi.actionSystem.impl.ActionManagerImpl",
                "duration": 345,
                "start": 5152,
                "end": 5497
            },
            {
                "name": "com.intellij.openapi.keymap.impl.KeymapManagerImpl",
                "duration": 18,
                "start": 5152,
                "end": 5170
            },
            {
                "name": "com.intellij.history.integration.LocalHistoryImpl",
                "duration": 15,
                "start": 5511,
                "end": 5526
            },
            {
                "name": "com.intellij.ide.ui.laf.LafManagerImpl",
                "duration": 241,
                "start": 5539,
                "end": 5780
            },
            {
                "name": "com.intellij.util.net.ssl.CertificateManager",
                "duration": 77,
                "start": 5810,
                "end": 5887
            },
            {
                "name": "com.intellij.openapi.util.registry.RegistryExtensionCollector",
                "duration": 17,
                "start": 5894,
                "end": 5911
            },
            {
                "name": "com.intellij.openapi.wm.impl.FocusManagerImpl",
                "duration": 22,
                "start": 5911,
                "end": 5934
            },
            {
                "name": "com.intellij.openapi.wm.impl.WindowManagerImpl",
                "duration": 17,
                "start": 5914,
                "end": 5931
            },
            {
                "name": "com.intellij.ide.IdeTooltipManager",
                "duration": 16,
                "start": 5934,
                "end": 5950
            },
            {
                "name": "com.intellij.ide.MacOSApplicationProvider",
                "duration": 22,
                "start": 5962,
                "end": 5985
            },
            {
                "name": "com.intellij.openapi.updateSettings.impl.UpdateCheckerComponent",
                "duration": 57,
                "start": 5985,
                "end": 6042
            },
            {
                "name": "com.intellij.util.indexing.FileBasedIndexImpl",
                "duration": 611,
                "start": 6042,
                "end": 6654
            },
            {
                "name": "com.intellij.psi.stubs.SerializationManagerImpl",
                "duration": 12,
                "start": 6653,
                "end": 6666
            },
            {
                "name": "com.intellij.psi.stubs.StubIndexImpl",
                "duration": 128,
                "start": 6655,
                "end": 6784
            },
            {
                "name": "com.intellij.openapi.actionSystem.ex.QuickListsManager",
                "duration": 18,
                "start": 6789,
                "end": 6807
            },
            {
                "name": "com.intellij.execution.ExecutorRegistryImpl",
                "duration": 27,
                "start": 6819,
                "end": 6846
            },
            {
                "name": "com.intellij.util.xml.impl.JavaDomApplicationComponent",
                "duration": 13,
                "start": 6848,
                "end": 6861
            },
            {
                "name": "com.intellij.openapi.projectRoots.impl.DefaultJdkConfigurator",
                "duration": 94,
                "start": 6862,
                "end": 6956
            },
            {
                "name": "org.intellij.lang.xpath.xslt.impl.XsltConfigImpl",
                "duration": 13,
                "start": 6966,
                "end": 6979
            },
            {
                "name": "com.intellij.stats.completion.CompletionTrackerInitializer",
                "duration": 11,
                "start": 6987,
                "end": 6998
            },
            {
                "name": "com.intellij.stats.personalization.impl.ApplicationUserFactorStorage",
                "duration": 16,
                "start": 6998,
                "end": 7015
            },
            {
                "name": "com.intellij.completion.FeatureManagerImpl",
                "duration": 13,
                "start": 7015,
                "end": 7029
            },
            {
                "name": "com.jetbrains.cidr.lang.dfa.contextSensitive.OCSourceGliderComponent",
                "duration": 162,
                "start": 7030,
                "end": 7192
            },
            {
                "name": "org.jetbrains.android.AndroidPlugin",
                "duration": 23,
                "start": 7205,
                "end": 7228
            },
            {
                "name": "org.jetbrains.plugins.ruby.gem.GemManager",
                "duration": 12,
                "start": 7264,
                "end": 7276
            },
            {
                "name": "com.intellij.openapi.components.impl.ServiceManagerImpl",
                "duration": 13,
                "start": 9444,
                "end": 9457
            },
            {
                "name": "com.intellij.openapi.vcs.impl.FileStatusManagerImpl",
                "duration": 10,
                "start": 9462,
                "end": 9473
            },
            {
                "name": "com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl",
                "duration": 23,
                "start": 9474,
                "end": 9497
            },
            {
                "name": "com.intellij.openapi.wm.impl.ToolWindowManagerImpl",
                "duration": 20,
                "start": 9497,
                "end": 9518
            },
            {
                "name": "com.intellij.openapi.roots.impl.ProjectRootManagerComponent",
                "duration": 13,
                "start": 9519,
                "end": 9533
            },
            {
                "name": "com.intellij.psi.impl.PsiManagerImpl",
                "duration": 14,
                "start": 9533,
                "end": 9547
            },
            {
                "name": "com.intellij.openapi.module.impl.ModuleManagerComponent",
                "duration": 42,
                "start": 9556,
                "end": 9599
            },
            {
                "name": "com.intellij.openapi.fileEditor.impl.PsiAwareFileEditorManagerImpl",
                "duration": 24,
                "start": 9600,
                "end": 9624
            },
            {
                "name": "com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl",
                "duration": 17,
                "start": 9634,
                "end": 9651
            },
            {
                "name": "com.intellij.codeInsight.daemon.impl.DaemonListeners",
                "duration": 219,
                "start": 9651,
                "end": 9871
            },
            {
                "name": "com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl",
                "duration": 20,
                "start": 9707,
                "end": 9728
            },
            {
                "name": "com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl",
                "duration": 119,
                "start": 9728,
                "end": 9848
            },
            {
                "name": "com.intellij.openapi.vcs.changes.ChangeListManagerImpl",
                "duration": 118,
                "start": 9728,
                "end": 9846
            },
            {
                "name": "com.intellij.openapi.vcs.changes.ChangesViewManager",
                "duration": 72,
                "start": 9734,
                "end": 9806
            },
            {
                "name": "com.intellij.codeInsight.daemon.impl.GeneralHighlightingPassFactory",
                "duration": 39,
                "start": 9871,
                "end": 9910
            },
            {
                "name": "com.intellij.codeInsight.navigation.CtrlMouseHandler",
                "duration": 14,
                "start": 9914,
                "end": 9929
            },
            {
                "name": "com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl",
                "duration": 15,
                "start": 9929,
                "end": 9945
            },
            {
                "name": "com.intellij.packaging.impl.artifacts.ArtifactManagerImpl",
                "duration": 10,
                "start": 9962,
                "end": 9973
            },
            {
                "name": "com.intellij.compiler.CompilerConfigurationImpl",
                "duration": 10,
                "start": 9973,
                "end": 9984
            },
            {
                "name": "com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager",
                "duration": 30,
                "start": 10002,
                "end": 10032
            },
            {
                "name": "com.intellij.xdebugger.impl.XDebuggerManagerImpl",
                "duration": 106,
                "start": 10046,
                "end": 10152
            },
            {
                "name": "com.intellij.execution.testDiscovery.TestDiscoveryIndex",
                "duration": 10,
                "start": 10175,
                "end": 10185
            },
            {
                "name": "com.intellij.execution.scratch.JavaScratchCompilationSupport",
                "duration": 40,
                "start": 10185,
                "end": 10225
            },
            {
                "name": "com.intellij.stats.personalization.impl.UserFactorsManagerImpl",
                "duration": 18,
                "start": 10246,
                "end": 10264
            },
            {
                "name": "com.intellij.tasks.impl.TaskManagerImpl",
                "duration": 53,
                "start": 10264,
                "end": 10318
            },
            {
                "name": "com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager",
                "duration": 217,
                "start": 10318,
                "end": 10536
            },
            {
                "name": "com.intellij.ide.palette.impl.PaletteToolWindowManager",
                "duration": 14,
                "start": 10536,
                "end": 10551
            },
            {
                "name": "org.jetbrains.idea.maven.navigator.MavenProjectsNavigator",
                "duration": 10,
                "start": 10559,
                "end": 10569
            },
            {
                "name": "com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache",
                "duration": 10,
                "start": 10580,
                "end": 10591
            },
            {
                "name": "com.jetbrains.cidr.lang.hmap.OCHeaderMapManager",
                "duration": 46,
                "start": 10591,
                "end": 10638
            },
            {
                "name": "com.intellij.jpa.JpaProjectComponent",
                "duration": 33,
                "start": 10648,
                "end": 10682
            },
            {
                "name": "com.android.tools.idea.gradle.project.AndroidGradleProjectComponent",
                "duration": 33,
                "start": 10704,
                "end": 10738
            },
            {
                "name": "com.android.tools.idea.res.PsiProjectListener",
                "duration": 13,
                "start": 10738,
                "end": 10751
            },
            {
                "name": "com.intellij.openapi.roots.impl.ModuleRootManagerComponent",
                "duration": 105,
                "start": 10834,
                "end": 10939
            }
        ],
        "preloadActivities": [
            {
                "name": "com.intellij.ide.ui.OptionsTopHitProvider$Activity",
                "duration": 1936,
                "start": 7369,
                "end": 9306
            }
        ],
        "totalDurationComputed": 10950,
        "totalDurationActual": 14262
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
    test("sort", () => {
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
