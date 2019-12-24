// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
module.exports = {
  integrity: true,
  chainWebpack: config => {
    config.devtool("source-map")
    config.optimization.splitChunks({
      cacheGroups: {
        amchartsCore: {
          name: "amcharts-core",
          test: /[\\/]node_modules[\\/]@amcharts[\\/]amcharts4[\\/]/,
          priority: -5,
          chunks: "initial",
          enforce: true,
        },
        amchartsCharts: {
          name: "amcharts-charts",
          test: /[\\/]node_modules[\\/]@amcharts[\\/]amcharts4[\\/].internal[\\/]charts/,
          priority: -2,
          chunks: "initial",
          enforce: true,
        },
        elementUi: {
          name: "element-ui",
          test: /[\\/]node_modules[\\/]element-ui/,
          priority: -5,
          chunks: "initial",
          enforce: true,
        },
      },
    })

    // noinspection SpellCheckingInspection
    config.externals(function (context, request, callback) {
      if (/xlsx|pdfmake|canvg/.test(request)) {
        return callback(null, "commonjs " + request)
      }
      callback()
    })
  }
}
