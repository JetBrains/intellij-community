# Old MCP Operation Map

Use this map when a prompt or older instruction mentions the retired `PluginModelAnalyzer` MCP operation names.

| Old operation | JSON request |
| --- | --- |
| `analyze_module_structure` | `{"filter":"summary"}` |
| module `info` | `{"filter":"moduleInfo","module":"<module>"}` |
| module `dependencies` | `{"filter":"moduleDependencies","module":"<module>"}` |
| module `paths` | `{"filter":"modulePaths","module":"<module>"}` |
| module `reachability` | `{"filter":"moduleReachability","module":"<module>","moduleSet":"<set>"}` |
| module `find_by_loading` | `{"filter":"moduleLoading","loading":"EMBEDDED","details":true}` |
| module `dependency_path` | `{"filter":"dependencyPath","fromModule":"<from>","toModule":"<to>","includeScopes":true}` |
| module-set `info` | `{"filter":"moduleSetQuery","moduleSet":"<set>"}` |
| module-set `search` | `{"filter":"moduleSetQuery","name":"<name>","location":"COMMUNITY","limit":20}` |
| module-set `hierarchy` | `{"filter":"moduleSetQuery","moduleSet":"<set>","details":true}` |
| module-set `overlap` | `{"filter":"moduleSetOverlap","minOverlapPercent":70,"limit":20}` |
| module-set `graph` | No ASCII graph output; use `moduleSetQuery` plus `productTracePath` or `productUsage` for structured edges. |
| module-set `suggest_for_modules` | `{"filter":"suggestModuleSetsForModules","modules":["<module>"]}` |
| product `info` | `{"filter":"productQuery","product":"<product>"}` |
| product `search` | `{"filter":"productQuery","name":"<name>","limit":20}` |
| product `compare` | `{"filter":"productCompare","product":"<product1>","product2":"<product2>"}` |
| product `similar` | `{"filter":"productSimilarity","threshold":0.8,"limit":20}` |
| product `composition` | `{"filter":"composition"}` for all products; use `productQuery` for one product. |
| product `trace_path` | `{"filter":"productTracePath","product":"<product>","moduleSet":"<set>"}` |
| product `deprecated_includes` | `{"filter":"deprecatedIncludes","product":"<product>"}` |
| product `full_analysis` | Avoid unless explicitly needed; omit the filter only when the full raw model is required. |
| validation `community_products` | `{"filter":"validation","check":"community_products"}` |
| validation `module_set_locations` | `{"filter":"validation","check":"module_set_locations"}` |
| validation `loading_inconsistencies` | `{"filter":"validation","check":"loading_inconsistencies"}` |
