// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
module.exports = {
  configureWebpack: {
    devtool: "source-map",
  },
  integrity: true,
  chainWebpack: config => {
    config.externals({
      "@amcharts/amcharts4/core": "am4core",
      "@amcharts/amcharts4/charts": "am4charts",
      "@amcharts/amcharts4/themes/animated": "am4themes_animated",
    })
  },
}
