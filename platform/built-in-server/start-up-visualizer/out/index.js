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
    class ItemChartManager extends core_1.XYChartManager {
        // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
        constructor(container, sourceNames) {
            super(container);
            this.sourceNames = sourceNames;
            this.configureNameAxis();
            this.configureDurationAxis();
            this.configureSeries();
            this.chart.legend = new am4charts.Legend();
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
define("dev", ["require", "exports", "TimeLineChartManager", "ComponentsChartManager"], function (require, exports, TimeLineChartManager_1, ComponentsChartManager_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const data = {
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
    };
    function main() {
        ComponentsChartManager_1.ComponentsChartManager;
        TimeLineChartManager_1.TimelineChartManager;
        ComponentsChartManager_1.TopHitProviderChart;
        const container = document.getElementById("visualization");
        // const chartManager = new ComponentsChartManager(container)
        // const chartManager = new TimelineChartManager(container)
        const chartManager = new ComponentsChartManager_1.TopHitProviderChart(container);
        chartManager.render(data);
        const global = window;
        global.lastData = data;
        global.chartManager = chartManager;
    }
    main();
});
define("main", ["require", "exports", "@amcharts/amcharts4/core", "@amcharts/amcharts4/themes/animated", "ComponentsChartManager", "TimeLineChartManager", "core"], function (require, exports, am4core, animated_1, ComponentsChartManager_2, TimeLineChartManager_2, core_3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const storageKeyPort = "ijPort";
    const storageKeyData = "inputIjFormat";
    function main() {
        am4core.useTheme(animated_1.default);
        const chartManagers = [
            new TimeLineChartManager_2.TimelineChartManager(document.getElementById("visualization")),
            new ComponentsChartManager_2.ComponentsChartManager(document.getElementById("componentChart")),
            new ComponentsChartManager_2.TopHitProviderChart(document.getElementById("optionsTopHitProviderChart")),
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
