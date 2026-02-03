# Grid: Table Component

This component is responsible for rendering tables in various contexts in intellij

## Module names

Module naming conventions inside this directory are following:
- All module names start with `intellij.grid`
- Module-specific word follows `intellij.grid`, i.e. `images` means that this
  module is about image support inside tables
- Module-specific word, if any, is followed by `core` if this module only brings
  core, not a full support
- Implementation modules end with `impl`, API modules don't have this suffix