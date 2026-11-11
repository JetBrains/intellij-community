# Grid core

`intellij.grid.core.impl` holds the data side of the grid: models, hookup contracts and base classes, loaders, and pending edits.
The [grid implementation](../impl/README.md) builds the Swing grid on top of it.

- **Product mode:** no frontend or backend marker dependency, so it loads wherever its dependencies load.
- **Dependencies:** `intellij.grid`, `intellij.grid.types`, `intellij.grid.csv.core.impl` and the platform; never `intellij.grid.impl`.
- **Language server:** ships its classes but not its descriptor. The server declares its three extension points again and registers none of its services.

| Class                                                          | Role                                                                             |
|----------------------------------------------------------------|----------------------------------------------------------------------------------|
| [GridDataHookUp](src/datagrid/GridDataHookUp.java)             | Models, loading, paging, sorting and an optional mutator of one result.          |
| [GridHookUpRegistry](src/datagrid/GridHookUpRegistry.kt)       | Registers hookups of a project; `GridDataHookUpManager` implements it.           |
| [MutationsStorageImpl](src/datagrid/MutationsStorageImpl.java) | Pending cell edits; `TypesMutationsStorageImpl` adds the pending types of cells. |
