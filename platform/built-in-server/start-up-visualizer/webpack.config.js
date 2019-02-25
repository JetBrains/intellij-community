// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
const path = require("path")
const webpack = require("webpack")
const CleanWebpackPlugin = require("clean-webpack-plugin")
const HtmlWebpackPlugin = require("html-webpack-plugin")
const SriPlugin = require("webpack-subresource-integrity")
const CopyPlugin = require("copy-webpack-plugin")

const ForkTsCheckerWebpackPlugin = require("fork-ts-checker-webpack-plugin")
const ForkTsCheckerNotifierWebpackPlugin = require("fork-ts-checker-notifier-webpack-plugin")

function configurePlugins(isProduction) {
  const plugins = [
    new CleanWebpackPlugin(["dist/*.js", "dist/*.js.map"]),
    new HtmlWebpackPlugin({
      template: path.join(__dirname, "index.template.html"),
    }),
    new SriPlugin({
      hashFuncNames: ["sha384"],
      enabled: isProduction,
    }),
    new CopyPlugin([
      {from: path.join(__dirname, "main.css")},
    ]),
  ]

  if (!isProduction) {
    plugins.push(new ForkTsCheckerWebpackPlugin({
      useTypescriptIncrementalApi: true,
    }),)

    plugins.push(new ForkTsCheckerNotifierWebpackPlugin({
      title: "TypeScript",
      excludeWarnings: false,
      skipFirstNotification: true,
    }))
  }
  return plugins
}

module.exports = module.exports = function (env, argv) {
  const isProduction = argv.mode === "production"

  const isMinimizeDisabledExplicitly = argv["optimize-minimize"] === false

  const configuration = {
    entry: path.join(__dirname, "src/main.ts"),
    devtool: "source-map",
    module: {
      rules: [
        {
          test: /\.tsx?$/,
          use: [
            {loader: "ts-loader", options: {transpileOnly: !isProduction}}
          ],
          exclude: /node_modules/
        }
      ],
    },
    resolve: {
      extensions: [".tsx", ".ts", ".js"],
    },
    output: {
      filename: isProduction && !isMinimizeDisabledExplicitly ? "[name].[contenthash].js" : "[name].js",
      path: path.join(__dirname, "dist"),
      crossOriginLoading: "anonymous",
    },
    externals: {
      "@amcharts/amcharts4/core": "am4core",
      "@amcharts/amcharts4/charts": "am4charts",
      "@amcharts/amcharts4/themes/animated": "am4themes_animated",
    },
    devServer: {
      hot: true,
    },
    plugins: configurePlugins(isProduction),
  }

  // --optimize-minimize=false doesn't work otherwise
  if (isMinimizeDisabledExplicitly) {
    configuration.optimization = {
      minimize: false
    }
  }

  return configuration
}