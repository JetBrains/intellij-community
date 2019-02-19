define("core", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
});
define("components", ["require", "exports", "@amcharts/amcharts4/charts", "@amcharts/amcharts4/core"], function (require, exports, am4charts, am4core) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    class ComponentsChart {
        // isUseYForName - if true, names are more readable, but not possible to see all components because layout from top to bottom (so, opposite from left to right some data can be out of current screen)
        constructor(container, isUseYForName = false) {
            const chart = am4core.create(container, am4charts.XYChart);
            this.chart = chart;
            this.configureChart(isUseYForName);
            // https://www.amcharts.com/docs/v4/tutorials/auto-adjusting-chart-height-based-on-a-number-of-data-items/
            // noinspection SpellCheckingInspection
            // chart.events.on("datavalidated", (event: any) => {
            //   const chart = event.target
            //   const categoryAxis = chart.yAxes.getIndex(0)
            //   const adjustHeight = chart.data.length * (isUseYForName ? 20 : 10) - categoryAxis.pixelHeight
            //
            //   // get current chart height
            //   let targetHeight = chart.pixelHeight + adjustHeight
            //
            //   // Set it on chart's container
            //   chart.svgContainer.htmlElement.style.height = targetHeight + "px"
            // })
            chart.scrollbarX = new am4core.Scrollbar();
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
        configureChart(isUseYForName) {
            const chart = this.chart;
            configureCommonChartSettings(chart);
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
    }
    exports.ComponentsChart = ComponentsChart;
    function configureCommonChartSettings(chart) {
        chart.exporting.menu = new am4core.ExportMenu();
        chart.mouseWheelBehavior = "zoomX";
        // chart.cursor = new am4charts.XYCursor()
    }
});
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
define("timeline", ["require", "exports", "vis"], function (require, exports, vis) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    // Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    class TimelineChartManager {
        constructor(container) {
            this.dataSet = new vis.DataSet();
            this.lastData = null;
            this.timeline = new vis.Timeline(container, this.dataSet, timelineOptions);
            document.getElementById("isUseRealTime").addEventListener("click", () => {
                if (this.lastData != null) {
                    this.render(this.lastData);
                }
            });
        }
        render(ijData) {
            this.lastData = ijData;
            const data = transformIjToVisJsFormat(ijData, isCreateGroups);
            const dataSet = this.dataSet;
            dataSet.clear();
            const items = data.items;
            if (data.groups != null) {
                this.timeline.setGroups(data.groups);
            }
            dataSet.add(items);
            timelineOptions.min = items[0].start;
            timelineOptions.max = items[items.length - 1].end + 1000; /* add 1 second as padding */
            this.timeline.setOptions(timelineOptions);
            this.timeline.fit();
        }
    }
    exports.TimelineChartManager = TimelineChartManager;
    function computeTitle(item, _index) {
        let result = item.name + (item.description == null ? "" : `<br/>${item.description}`) + `<br/>${item.duration} ms`;
        // debug
        // result += `<br/>${index}`
        return result;
    }
    function transformIjToVisJsFormat(input, isCreateGroups) {
        const isUseRealTime = document.getElementById("isUseRealTime").checked;
        const moment = vis.moment;
        // noinspection ES6ModulesDependencies
        const firstStart = moment(input.items[0].start);
        // hack to force timeline to start from 0
        const timeOffset = isUseRealTime ? 0 : (firstStart.seconds() * 1000) + firstStart.milliseconds();
        const transformer = (item, index) => {
            // noinspection ES6ModulesDependencies
            const vItem = {
                id: item.name,
                title: computeTitle(item, index),
                content: item.name,
                start: moment(item.start - timeOffset),
                // startRaw: item.start,
                end: moment(item.end - timeOffset),
                // endRaw: item.end,
                type: "range",
                rawIndex: index,
            };
            if (isCreateGroups) {
                for (const group of groups) {
                    if (item.name.startsWith(group.id)) {
                        vItem.group = group.id;
                        break;
                    }
                }
            }
            return vItem;
        };
        const items = input.items.map(transformer);
        // a lot of components - makes timeline not readable
        // if (input.components != null) {
        //   items = items.concat(input.components.map(transformer))
        // }
        return {
            groups: groups,
            items,
        };
    }
});
define("main", ["require", "exports", "@amcharts/amcharts4/core", "@amcharts/amcharts4/themes/animated", "components", "timeline"], function (require, exports, am4core, animated_1, components_1, timeline_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const storageKeyData = "inputIjFormat";
    const storageKeyPort = "ijPort";
    function main() {
        am4core.useTheme(animated_1.default);
        const chartManagers = [
            new timeline_1.TimelineChartManager(document.getElementById("visualization")),
            new components_1.ComponentsChart(document.getElementById("componentsVisualization")),
        ];
        // debug
        const global = window;
        global.componentsChart = chartManagers[1];
        configureInput(document.getElementById("ijInput"), data => {
            global.lastData = data;
            for (const chartManager of chartManagers) {
                chartManager.render(data);
            }
        });
    }
    exports.main = main;
    function configureInput(inputElement, dataListener) {
        function callListener(rawData) {
            dataListener(JSON.parse(rawData));
        }
        function setInput(rawData) {
            if (rawData != null && rawData.length !== 0) {
                inputElement.value = rawData;
                callListener(rawData);
            }
        }
        const portInputElement = document.getElementById("ijPort");
        window.addEventListener("load", () => {
            portInputElement.value = localStorage.getItem(storageKeyPort) || "63342";
            setInput(localStorage.getItem(storageKeyData));
        });
        function grabFromRunningInstance(port) {
            fetch(`http://localhost:${port}/api/about/?startUpMeasurement`, { credentials: "omit" })
                .then(it => it.json())
                .then(json => setInput(JSON.stringify(json.startUpMeasurement || { items: [] }, null, 2)));
        }
        getButton("grabButton").addEventListener("click", () => {
            // use parseInt to validate input
            let port = portInputElement.value;
            if (port.length === 0) {
                port = "63342";
            }
            else if (!/^\d+$/.test(port)) {
                throw new Error("Port number value is not numeric");
            }
            localStorage.setItem(storageKeyPort, port);
            grabFromRunningInstance(port);
        });
        getButton("grabDevButton").addEventListener("click", () => {
            grabFromRunningInstance("63343");
        });
        inputElement.addEventListener("input", () => {
            const rawData = inputElement.value.trim();
            localStorage.setItem(storageKeyData, rawData);
            callListener(rawData);
        });
    }
    function getButton(id) {
        return document.getElementById(id);
    }
});
