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
        }
    }
    exports.XYChartManager = XYChartManager;
});
define("ComponentsChartManager", ["require", "exports", "@amcharts/amcharts4/charts", "core"], function (require, exports, am4charts, core_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    class ComponentsChartManager extends core_1.XYChartManager {
        // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
        constructor(container, isUseYForName = false) {
            super(container);
            const chart = this.chart;
            const nameAxis = (isUseYForName ? chart.yAxes : chart.xAxes).push(new am4charts.CategoryAxis());
            nameAxis.renderer.labels;
            nameAxis.dataFields.category = "shortName";
            // allow to copy text
            const nameAxisLabel = nameAxis.renderer.labels.template;
            nameAxisLabel.selectable = true;
            nameAxisLabel.fontSize = 12;
            if (!isUseYForName) {
                nameAxisLabel.rotation = -45;
                nameAxisLabel.location = 0.4;
                nameAxisLabel.verticalCenter = "middle";
                nameAxisLabel.horizontalCenter = "right";
                nameAxis.renderer.minGridDistance = 0.1;
                // https://www.amcharts.com/docs/v4/concepts/axes/#Grid_labels_and_ticks
                nameAxis.renderer.grid.template.location = 0;
                nameAxis.renderer.grid.template.disabled = true;
            }
            const durationAxis = (isUseYForName ? chart.xAxes : chart.yAxes).push(new am4charts.DurationAxis());
            durationAxis.title.text = "Duration";
            // https://www.amcharts.com/docs/v4/reference/durationformatter/
            // base unit the values are in
            durationAxis.durationFormatter.baseUnit = "millisecond";
            durationAxis.durationFormatter.durationFormat = "S";
            const series = chart.series.push(new am4charts.ColumnSeries());
            series.columns.template.tooltipText = "{name}: {duration} ms";
            series.dataFields.dateX = "start";
            series.dataFields.categoryX = "shortName";
            series.dataFields.categoryY = "shortName";
            series.dataFields.valueY = "duration";
            series.dataFields.valueX = "duration";
        }
        render(data) {
            const components = data.components;
            if (components == null || components.length === 0) {
                this.chart.data = [];
                return;
            }
            // let startOffset = components[0].start
            for (const component of components) {
                const lastDotIndex = component.name.lastIndexOf(".");
                component.shortName = lastDotIndex < 0 ? component.name : component.name.substring(lastDotIndex + 1);
                // component.relativeStart = component.start - startOffset
            }
            this.chart.data = components;
        }
    }
    exports.ComponentsChartManager = ComponentsChartManager;
});
define("timeline", ["require", "exports", "core", "@amcharts/amcharts4/charts", "@amcharts/amcharts4/core"], function (require, exports, core_2, am4charts, am4core) {
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
    const timelineOptions = {
        // http://visjs.org/examples/timeline/items/itemOrdering.html
        // strange, but order allows to have consistent ordering but still some items not stacked correctly (e.g. "project components initialization between registration and creation even if indices are correct)
        order: (o1, o2) => {
            // if (o1.startRaw <= o2.startRaw && o1.endRaw >= o2.endRaw) {
            //   return -1
            // }
            return o1.rawIndex - o2.rawIndex;
        },
    };
    class TimelineChartManager extends core_2.XYChartManager {
        constructor(container) {
            super(container);
            this.lastData = null;
            const chart = this.chart;
            const dateAxis = chart.xAxes.push(new am4charts.DateAxis());
            this.dateAxis = dateAxis;
            // https://www.amcharts.com/docs/v4/concepts/formatters/formatting-date-time/
            // Milliseconds since 1970-01-01 / Unix epoch
            dateAxis.dateFormatter.inputDateFormat = "x";
            dateAxis.renderer.minGridDistance = 1;
            dateAxis.renderer.labels.template.hideOversized = false;
            // dateAxis.baseInterval = {count: 100, timeUnit: "millisecond"}
            dateAxis.baseInterval = { count: 1, timeUnit: "millisecond" };
            // const durationAxis = chart.xAxes.push(new am4charts.DurationAxis())
            // durationAxis.durationFormatter.baseUnit = "millisecond"
            // durationAxis.durationFormatter.durationFormat = "S"
            // durationAxis.renderer.minGridDistance = 0.1
            const levelAxis = chart.yAxes.push(new am4charts.CategoryAxis());
            levelAxis.dataFields.category = "level";
            levelAxis.renderer.grid.template.location = 0;
            levelAxis.renderer.minGridDistance = 0.1;
            levelAxis.renderer.labels.template.disabled = true;
            const series = chart.series.push(new am4charts.ColumnSeries());
            // series.columns.template.width = am4core.percent(80)
            series.columns.template.tooltipText = "{name}: {duration}";
            series.dataFields.openDateX = "start";
            series.dataFields.dateX = "end";
            series.dataFields.categoryY = "level";
            series.dataFields.valueX = "duration";
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
            // const series2 = chart.series.push(new am4charts.LineSeries())
            // series2.xAxis = durationAxis
            // series2.dataFields.valueX = "duration"
            // series2.dataFields.dateY = "start"
            document.getElementById("isUseRealTime").addEventListener("click", () => {
                if (this.lastData != null) {
                    this.render(this.lastData);
                }
            });
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
            const isUseRealTime = document.getElementById("isUseRealTime").checked;
            // noinspection ES6ModulesDependencies
            const firstStart = new Date(ijData.items[0].start);
            // hack to force timeline to start from 0
            const timeOffset = isUseRealTime ? 0 : (firstStart.getSeconds() * 1000) + firstStart.getMilliseconds();
            const data = transformIjData(ijData, timeOffset);
            this.chart.data = data;
            const originalItems = ijData.items;
            // https://www.amcharts.com/docs/v4/concepts/axes/date-axis/
            this.dateAxis.dateFormats.setKey("second", isUseRealTime ? "HH:mm:ss" : "s");
            this.dateAxis.min = originalItems[0].start - timeOffset;
            this.dateAxis.max = originalItems[originalItems.length - 1].end - timeOffset;
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
// function getLevel(itemIndex: number, items: Array<Item>, transformedItems: Array<any>): number {
//   let index = itemIndex
//   const currentItem = items[itemIndex]
//   while (true) {
//     --index
//     if (index < 0) {
//       return 1
//     }
//
//     let prevItem = items[index]
//     // items are sorted, no need to check start or next items
//     const diff = prevItem.end - currentItem.end
//     if (diff >= 0) {
//       return transformedItems[index].level + ((diff == 0 && currentItem.start > prevItem.start) ? 0 : 1)
//     }
//   }
// }
define("main", ["require", "exports", "@amcharts/amcharts4/core", "@amcharts/amcharts4/themes/animated", "ComponentsChartManager", "timeline"], function (require, exports, am4core, animated_1, ComponentsChartManager_1, timeline_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const storageKeyData = "inputIjFormat";
    const storageKeyPort = "ijPort";
    function main() {
        am4core.useTheme(animated_1.default);
        const chartManagers = [
            new timeline_1.TimelineChartManager(document.getElementById("visualization")),
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
        const inputElement = getInputElement("ijInput");
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
        getButtonElement("grabButton").addEventListener("click", () => {
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
        getButtonElement("grabDevButton").addEventListener("click", () => {
            grabFromRunningInstance("63343");
        });
        inputElement.addEventListener("input", () => {
            const rawData = inputElement.value.trim();
            localStorage.setItem(storageKeyData, rawData);
            callListener(rawData);
        });
    }
    function getPortInputElement() {
        return getInputElement("ijPort");
    }
    function getInputElement(id) {
        return document.getElementById(id);
    }
    function getButtonElement(id) {
        return document.getElementById(id);
    }
});
