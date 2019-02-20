// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// to be able to keep start-up-visualizer in sources as ready to use (to avoid building or hosting externally),
// TS sources compiled as AMD module and required here without any external lib.
// system.js doesn't provide a way to register global
// parcel cannot exclude modules (https://github.com/parcel-bundler/parcel/issues/144
// webpack - maybe it is a solution, but ouch, for this simple project doesn't want to setup it
// so, instead of all these JS nightmares, plain own bootstrap code is used.

const nameToDefinition = new Map()

function define(name, dependencies, definition) {
  nameToDefinition.set(name, {dependencies, definition})

  if (name === "main") {
    require(["main"], function (main) {
      main.main()
    })
  }
}

function require(dependencies, definition) {
  const exports = {}
  const resolvedDependencies = dependencies.map(name => {
    switch (name) {
      case "vis":
        return vis
      case "@amcharts/amcharts4/core":
        return am4core
      case "@amcharts/amcharts4/charts":
        return am4charts
      case "@amcharts/amcharts4/themes/animated":
        return {default: am4themes_animated}
      case "require":
        return null
      case "exports":
        return exports

      default: {
        const module = nameToDefinition.get(name)
        return require(module.dependencies, module.definition)
      }
    }
  })

  definition.apply(null, resolvedDependencies)
  return exports
}