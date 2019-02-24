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
define("ItemChartManager", ["require", "exports", "@amcharts/amcharts4/charts", "core"], function (require, exports, am4charts, core_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    class ItemChartManager extends core_1.XYChartManager {
        // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
        constructor(container, sourceNames) {
            super(container);
            this.sourceNames = sourceNames;
            this.configureNameAxis();
            this.configureDurationAxis();
            this.configureSeries();
            this.chart.legend = new am4charts.Legend();
            // make colors more contrast because we have more than one series
            this.chart.colors.step = 2;
        }
        get nameAxis() {
            return this.chart.xAxes.getIndex(0);
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
            durationAxis.strictMinMax = true;
        }
        configureSeries() {
            const a = this.addSeries();
            const p = this.addSeries();
            a.name = "Application-level";
            p.name = "Project-level";
        }
        addSeries() {
            const series = this.chart.series.push(new am4charts.ColumnSeries());
            series.dataFields.dateX = "start";
            series.dataFields.categoryX = "shortName";
            series.dataFields.valueY = "duration";
            series.columns.template.tooltipText = "{name}: {duration} ms";
            series.clustered = false;
            // noinspection SpellCheckingInspection
            series.events.on("visibilitychanged", event => {
                const nameAxis = this.nameAxis;
                const seriesList = this.chart.series;
                let offset = 0;
                let length = 0;
                for (let i = 0; i < seriesList.length; i++) {
                    const otherSeries = seriesList.getIndex(i);
                    if (otherSeries === series) {
                        length = series.data.length;
                        break;
                    }
                    if (!otherSeries.visible) {
                        // do not take in account because if not visible, data is already removed from axis data
                        continue;
                    }
                    offset += otherSeries.data.length;
                }
                if (event.visible) {
                    nameAxis.data.splice(offset, 0, ...series.data);
                }
                else {
                    nameAxis.data.splice(offset, length);
                }
                // trigger update
                nameAxis.data = nameAxis.data;
                // const seriesList = this.chart.series
                // const axisData = []
                // for (let i = 0; i < seriesList.length; i++) {
                //   const s = seriesList.getIndex(i)!!
                //   if (s.visible) {
                //     axisData.push(...s.data)
                //   }
                // }
                // nameAxis.data = axisData
                // wothout this call items is not rendered correctly (overlapped)
                this.chart.invalidateData();
            });
            return series;
        }
        render(data) {
            const sources = [];
            const series = this.chart.series;
            let seriesIndex = 0;
            const axisData = [];
            for (const sourceName of this.sourceNames) {
                const items = data[sourceName] || [];
                sources.push(items);
                ItemChartManager.assignShortName(items);
                series.getIndex(seriesIndex++).data = items;
                axisData.push(...items);
            }
            // https://www.amcharts.com/docs/v4/concepts/series/#Note_about_Series_data_and_Category_axis
            this.nameAxis.data = axisData;
        }
        static assignShortName(items) {
            for (const component of items) {
                const componentItem = component;
                const lastDotIndex = component.name.lastIndexOf(".");
                componentItem.shortName = lastDotIndex < 0 ? component.name : component.name.substring(lastDotIndex + 1);
            }
        }
    }
    exports.ItemChartManager = ItemChartManager;
    class ComponentsChartManager extends ItemChartManager {
        constructor(container) {
            super(container, ["appComponents", "projectComponents"]);
        }
    }
    exports.ComponentsChartManager = ComponentsChartManager;
    class TopHitProviderChart extends ItemChartManager {
        constructor(container) {
            super(container, ["appOptionsTopHitProviders", "projectOptionsTopHitProviders"]);
        }
    }
    exports.TopHitProviderChart = TopHitProviderChart;
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
    class TimelineChartManager extends core_2.XYChartManager {
        constructor(container) {
            super(container);
            this.maxRowIndex = 0;
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
define("dev", ["require", "exports", "TimeLineChartManager", "ItemChartManager"], function (require, exports, TimeLineChartManager_1, ItemChartManager_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const data = {
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
    };
    function main() {
        ItemChartManager_1.ComponentsChartManager;
        TimeLineChartManager_1.TimelineChartManager;
        ItemChartManager_1.TopHitProviderChart;
        const container = document.getElementById("visualization");
        // const chartManager = new ComponentsChartManager(container)
        // const chartManager = new TimelineChartManager(container)
        const chartManager = new ItemChartManager_1.TopHitProviderChart(container);
        chartManager.render(data);
        const global = window;
        global.lastData = data;
        global.chartManager = chartManager;
    }
    main();
});
define("main", ["require", "exports", "@amcharts/amcharts4/core", "@amcharts/amcharts4/themes/animated", "ItemChartManager", "TimeLineChartManager", "core"], function (require, exports, am4core, animated_1, ItemChartManager_2, TimeLineChartManager_2, core_3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const storageKeyPort = "ijPort";
    const storageKeyData = "inputIjFormat";
    function main() {
        am4core.useTheme(animated_1.default);
        const chartManagers = [
            new TimeLineChartManager_2.TimelineChartManager(document.getElementById("visualization")),
            new ItemChartManager_2.ComponentsChartManager(document.getElementById("componentChart")),
            new ItemChartManager_2.TopHitProviderChart(document.getElementById("optionsTopHitProviderChart")),
        ];
        const global = window;
        global.timelineChart = chartManagers[0];
        global.componentsChart = chartManagers[1];
        new InputFormManager(data => {
            for (const chartManager of chartManagers) {
                chartManager.render(data);
            }
        });
    }
    exports.main = main;
    class InputFormManager {
        constructor(dataListener) {
            this.dataListener = dataListener;
            if (document.readyState === "loading") {
                document.addEventListener("DOMContentLoaded", () => {
                    this.configureElements();
                });
            }
            else {
                this.configureElements();
            }
        }
        callListener(rawData) {
            this.dataListener(JSON.parse(rawData));
        }
        setInput(rawData) {
            if (rawData != null && rawData.length !== 0) {
                core_3.getInputElement("ijInput").value = rawData;
                this.callListener(rawData);
            }
        }
        configureElements() {
            const inputElement = core_3.getInputElement("ijInput");
            getPortInputElement().value = localStorage.getItem(storageKeyPort) || "63342";
            this.setInput(localStorage.getItem(storageKeyData));
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
                this.grabFromRunningInstance(port);
            });
            core_3.getButtonElement("grabDevButton").addEventListener("click", () => {
                this.grabFromRunningInstance("63343");
            });
            inputElement.addEventListener("input", () => {
                const rawData = inputElement.value.trim();
                localStorage.setItem(storageKeyData, rawData);
                this.callListener(rawData);
            });
        }
        grabFromRunningInstance(port) {
            const host = `localhost:${port}`;
            function showError(reason) {
                alert(`Cannot load data from "${host}": ${reason}`);
            }
            const controller = new AbortController();
            const signal = controller.signal;
            const timeoutId = setTimeout(() => {
                controller.abort();
                showError("8 seconds timeout");
            }, 8000);
            fetch(`http://${host}/api/about/?startUpMeasurement`, { credentials: "omit", signal })
                .then(it => it.json())
                .then(json => {
                clearTimeout(timeoutId);
                const data = json.startUpMeasurement;
                if (data == null) {
                    const message = "IntelliJ Platform IDE didn't report startup measurement result";
                    console.error(message, json);
                    alert(message);
                    return;
                }
                const rawData = JSON.stringify(data, null, 2);
                localStorage.setItem(storageKeyData, rawData);
                this.setInput(rawData);
            })
                .catch(e => {
                clearTimeout(timeoutId);
                console.error(e);
                if (!(e instanceof window.AbortError)) {
                    showError(e);
                }
            });
        }
    }
    function getPortInputElement() {
        return core_3.getInputElement("ijPort");
    }
    main();
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
