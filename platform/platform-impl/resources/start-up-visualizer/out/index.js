define("timeline", ["require", "exports", "vis"], function (require, exports, vis) {
    // Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    class TimelineChart {
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
    exports.TimelineChart = TimelineChart;
    function computeTitle(item, index) {
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
    function main() {
        am4core.useTheme(animated_1.default);
        const g = window;
        const dataListeners = configureInput(document.getElementById("ijInput"));
        const timeLineChart = new timeline_1.TimelineChart(document.getElementById("visualization"));
        const componentsChart = new components_1.ComponentsChart(document.getElementById("componentsVisualization"));
        // debug
        g.componentsChart = componentsChart;
        dataListeners.push((data) => {
            g.lastData = data;
            timeLineChart.render(data);
            componentsChart.render(data);
        });
    }
    exports.main = main;
    function configureInput(inputElement) {
        const dataListeners = [];
        const storageKey = "inputIjFormat";
        function restoreOldData() {
            let oldData = localStorage.getItem(storageKey);
            if (oldData != null && oldData.length > 0) {
                inputElement.value = oldData;
                callListeners(oldData);
            }
        }
        window.addEventListener("load", event => {
            restoreOldData();
        });
        inputElement.addEventListener("input", () => {
            const rawString = inputElement.value.trim();
            localStorage.setItem(storageKey, rawString);
            callListeners(rawString);
        });
        function callListeners(rawData) {
            const data = JSON.parse(rawData);
            for (const dataListener of dataListeners) {
                dataListener(data);
            }
        }
        return dataListeners;
    }
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
            chart.mouseWheelBehavior = "zoomX";
            const nameAxis = (isUseYForName ? chart.yAxes : chart.xAxes).push(new am4charts.CategoryAxis());
            nameAxis.renderer.labels;
            nameAxis.dataFields.category = "shortName";
            // allow to copy text
            nameAxis.renderer.labels.template.selectable = true;
            if (!isUseYForName) {
                nameAxis.renderer.labels.template.rotation = -45;
                nameAxis.renderer.labels.template.location = 0.4;
                nameAxis.renderer.labels.template.verticalCenter = "middle";
                nameAxis.renderer.labels.template.horizontalCenter = "right";
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
});
