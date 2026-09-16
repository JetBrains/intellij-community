The shared fixture set of the two readers of `.idea/runConfigurations`: the converter's `devServerRunConfigurations`
and the plan generator's `readDevDistRunConfigurationModules`. `expected.txt` states the additional modules by
product, one `product: module,module` line each, and both readers must give it. A file that names no module for a
product still counts for that product when it names a module elsewhere.
