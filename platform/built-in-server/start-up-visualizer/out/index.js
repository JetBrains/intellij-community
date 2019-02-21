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
            nameAxisLabel.rotation = -45;
            nameAxisLabel.location = 0.4;
            nameAxisLabel.verticalCenter = "middle";
            nameAxisLabel.horizontalCenter = "right";
            nameAxis.renderer.minGridDistance = 1;
            nameAxis.renderer.grid.template.location = 0;
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
define("TimeLineChartManager", ["require", "exports", "core", "@amcharts/amcharts4/charts", "@amcharts/amcharts4/core"], function (require, exports, core_2, am4charts, am4core) {
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
        // private readonly dateAxis: am4charts.DateAxis
        constructor(container) {
            super(container);
            this.lastData = null;
            const chart = this.chart;
            const durationAxis = chart.xAxes.push(new am4charts.DurationAxis());
            durationAxis.durationFormatter.baseUnit = "millisecond";
            durationAxis.durationFormatter.durationFormat = "S";
            durationAxis.min = 0;
            durationAxis.strictMinMax = true;
            // durationAxis.renderer.grid.template.disabled = true
            const levelAxis = chart.yAxes.push(new am4charts.CategoryAxis());
            levelAxis.dataFields.category = "level";
            levelAxis.renderer.grid.template.location = 0;
            levelAxis.renderer.minGridDistance = 1;
            levelAxis.renderer.labels.template.disabled = true;
            const series = chart.series.push(new am4charts.ColumnSeries());
            // series.columns.template.width = am4core.percent(80)
            series.columns.template.tooltipText = "{name}: {duration}";
            series.dataFields.openDateX = "start";
            series.dataFields.openValueX = "start";
            series.dataFields.dateX = "end";
            series.dataFields.valueX = "end";
            series.dataFields.categoryY = "level";
            // series.columns.template.propertyFields.fill = "color"
            // series.columns.template.propertyFields.stroke = "color";
            // series.columns.template.adapter.add("fill", (fill, target) => {
            //   return target.dataItem ? chart.colors.getIndex(target.dataItem.index) : fill
            // })
            series.columns.template.propertyFields.fill = "color";
            series.columns.template.propertyFields.stroke = "color";
            series.columns.template.strokeOpacity = 1;
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
            const isUseRealTimeElement = document.getElementById("isUseRealTime");
            if (isUseRealTimeElement != null) {
                isUseRealTimeElement.addEventListener("click", () => {
                    if (this.lastData != null) {
                        this.render(this.lastData);
                    }
                });
            }
            this.addHeightAdjuster(levelAxis);
        }
        addHeightAdjuster(levelAxis) {
            // https://www.amcharts.com/docs/v4/tutorials/auto-adjusting-chart-height-based-on-a-number-of-data-items/
            // noinspection SpellCheckingInspection
            this.chart.events.on("datavalidated", () => {
                const chart = this.chart;
                const adjustHeight = chart.data.reduce((maxLevel, item) => Math.max(item.level, maxLevel), 0) * 35 - levelAxis.pixelHeight;
                // get current chart height
                let targetHeight = chart.pixelHeight + adjustHeight;
                // Set it on chart's container
                chart.svgContainer.htmlElement.style.height = targetHeight + "px";
            });
        }
        render(ijData) {
            this.lastData = ijData;
            // const isUseRealTime = (document.getElementById("isUseRealTime") as HTMLInputElement).checked
            // noinspection ES6ModulesDependencies
            const firstStart = new Date(ijData.items[0].start);
            // hack to force timeline to start from 0
            // const timeOffset = isUseRealTime ? 0 : (firstStart.getSeconds() * 1000) + firstStart.getMilliseconds()
            const timeOffset = ijData.items[0].start;
            const data = transformIjData(ijData, timeOffset);
            this.chart.data = data;
            const originalItems = ijData.items;
            const durationAxis = this.chart.xAxes.getIndex(0);
            // https://www.amcharts.com/docs/v4/concepts/axes/date-axis/
            // this.dateAxis.dateFormats.setKey("second", isUseRealTime ? "HH:mm:ss" : "s")
            // this.dateAxis.min = originalItems[0].start - timeOffset
            // this.dateAxis.max = originalItems[originalItems.length - 1].end - timeOffset
            durationAxis.max = originalItems[originalItems.length - 1].end - timeOffset;
        }
    }
    exports.TimelineChartManager = TimelineChartManager;
    function computeTitle(item, _index) {
        let result = item.name + (item.description == null ? "" : `<br/>${item.description}`) + `<br/>${item.duration} ms`;
        // debug
        // result += `<br/>${index}`
        return result;
    }
    function computeLevels(input) {
        let prevItem = null;
        let level = 0;
        for (const item of input.items) {
            if (prevItem != null) {
                if (prevItem.end >= item.end) {
                    level++;
                }
            }
            item.level = level;
            prevItem = item;
        }
    }
    function transformIjData(input, timeOffset) {
        const colorSet = new am4core.ColorSet();
        const transformedItems = new Array(input.items.length);
        computeLevels(input);
        // we cannot use actual level as row index because in this case labels will be overlapped, so,
        // row index simply incremented till empirical limit (6).
        let rowIndex = 0;
        for (let i = 0; i < input.items.length; i++) {
            const item = input.items[i];
            const result = {
                name: item.name,
                start: item.start - timeOffset,
                end: item.end - timeOffset,
                duration: item.duration,
                // level: item.isSubItem ? 2 : 1
                // level: "l" + getLevel(i, input.items, transformedItems).toString(),
                // level: getLevel(i, input.items, transformedItems),
                // level: item.level,
                level: rowIndex++,
                color: colorSet.getIndex(item.level /* level from original item is correct */)
            };
            if (rowIndex > 6) {
                rowIndex = 0;
            }
            transformedItems[i] = result;
        }
        transformedItems.sort((a, b) => a.level - b.level);
        return transformedItems;
    }
});
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
define("dev", ["require", "exports", "ComponentsChartManager"], function (require, exports, ComponentsChartManager_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const data = {
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
    };
    function main() {
        const chartManager = new ComponentsChartManager_1.ComponentsChartManager(document.getElementById("componentsVisualization"));
        chartManager.render(data);
        window.chartManager = chartManager;
    }
    main();
});
define("main", ["require", "exports", "@amcharts/amcharts4/core", "@amcharts/amcharts4/themes/animated", "ComponentsChartManager", "TimeLineChartManager", "core"], function (require, exports, am4core, animated_1, ComponentsChartManager_2, TimeLineChartManager_1, core_3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const storageKeyPort = "ijPort";
    const storageKeyData = "inputIjFormat";
    function main() {
        am4core.useTheme(animated_1.default);
        const chartManagers = [
            new TimeLineChartManager_1.TimelineChartManager(document.getElementById("visualization")),
            new ComponentsChartManager_2.ComponentsChartManager(document.getElementById("componentsVisualization")),
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
