// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
module.exports = {
  configureWebpack: {
    devtool: "source-map",
  },
  integrity: true,
  configureWebpack: config => {
    if (process.env.NODE_ENV === "production") {
      config.optimization.splitChunks.cacheGroups.amcharts = {
        name: "amcharts",
        test: /[\\/]node_modules[\\/]@amcharts[\\/]/,
        priority: -5,
        chunks: "initial"
      }
    }
  },
  chainWebpack: config => {
    // noinspection SpellCheckingInspection
    return config
      .externals({
          // doesn't work for pdfmake, because chunk name and module name differs (well, it is ok, prefetch works)
         "pdfmake": "pdfmake",
         "xlsx": "xlsx",
       })
      .plugin("prefetch")
      .tap(args => {
        return [
          {
            rel: "prefetch",
            include: "asyncChunks",
            fileBlacklist: [
              /\.map$/,
              /pdfmake\.[^.]+\.js$/,
              /xlsx\.[^.]+\.js$/,
            ]
          }
        ]
      })
  }
}
