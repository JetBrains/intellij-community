# Module Sets

Module sets are collections of modules that can be referenced as a single entity in product configurations.
All module set files follow the naming pattern: `intellij.moduleSets.<category>.<subcategory>.xml`

## Creating a New Module Set

See `/create-module-set` slash command for detailed instructions on creating a new module set.

## IDE Module Sets

### [intellij.moduleSets.ide.common.xml](intellij.moduleSets.ide.common.xml)

A set of product modules for regular IDE. For example, for WebStorm, but not for Fleet backend.

This set includes [intellij.moduleSets.essential](#intellijmodulesetsessentialxml) and [intellij.moduleSets.vcs](#intellijmodulesetsvcsxml) sets.

### [intellij.moduleSets.essential.xml](intellij.moduleSets.essential.xml)

A set of product modules that are essential for any IDE based on IJ Platform.

Includes [intellij.moduleSets.libraries](#intellijmodulesetslibrariesxml).

Included in the [ide.common](#intellijmodulesetsidecommonxml) set.

### [intellij.moduleSets.ide.ultimate.xml](intellij.moduleSets.ide.ultimate.xml)

A set of modules common to all ultimate IDEs.

Includes observability features (coverage, profiling), IDE infrastructure (new UI onboarding, import settings, DAP, tips, registry cloud), and Language Server Protocol support.

Includes the [commercial](#intellijmodulesetscommercialxml) module set.

This set is used by WebStorm, GoLand, RustRover, RubyMine, PhpStorm, CLion, DataGrip, Rider, PyCharm Pro, Aqua, and Ultimate.

### [intellij.moduleSets.commercial.xml](intellij.moduleSets.commercial.xml)

A set of commercial platform modules required by all JetBrains commercial IDEs.

Includes the commercial platform module and licensing functionality.

This set is included in the [ide.ultimate](#intellijmodulesetsideultimatexml) set.

### [intellij.moduleSets.ide.trial.xml](intellij.moduleSets.ide.trial.xml)

A set of trial and monetization modules for commercial IDEs without a free tier.

Includes trial promotion and trace consent modules.

This set is used by WebStorm, GoLand, RustRover, RubyMine, PhpStorm, CLion, and DataGrip.

## VCS Module Sets

### [intellij.moduleSets.vcs.xml](intellij.moduleSets.vcs.xml)

A set of product modules for regular IDE with VCS support.
This is a separate set because, for instance, `intellij.platform.smRunner.vcs` should not be included in Rider,
yet we still want to avoid duplicating the list of VCS modules.

This set is included in the [ide.common](#intellijmodulesetsidecommonxml) set.

### [intellij.moduleSets.vcs.shared.xml](intellij.moduleSets.vcs.shared.xml)

A set of VCS modules shared between different product variants.

### [intellij.moduleSets.vcs.frontend.xml](intellij.moduleSets.vcs.frontend.xml)

A set of VCS modules specific to frontend/client implementations.

## Library Module Sets

### [intellij.moduleSets.libraries.xml](intellij.moduleSets.libraries.xml)

A set that aggregates all library module sets. This is the main entry point for including all platform libraries.

Includes [libraries.core](#intellijmodulesetslibrariescorexml), [libraries.ktor](#intellijmodulesetslibrariesktrxml), [libraries.misc](#intellijmodulesetslibrariesmiscxml), and [libraries.temporaryBundled](#intellijmodulesetslibrariestemporarybundledxml).

### [intellij.moduleSets.libraries.core.xml](intellij.moduleSets.libraries.core.xml)

A set of library modules that are embedded into Core and bundled to all IDEs based on IJ Platform.

All library modules in this file must have `loading="embedded"`.

### [intellij.moduleSets.libraries.misc.xml](intellij.moduleSets.libraries.misc.xml)

A set of library modules that must NOT be embedded into Core.
Plugins that require these libraries should bundle them individually.

All libs here must not be embedded. If a library should be embedded, it should be moved to [libraries.core](#intellijmodulesetslibrariescorexml).

### [intellij.moduleSets.libraries.ktor.xml](intellij.moduleSets.libraries.ktor.xml)

A set of Ktor networking library modules that are embedded with the platform.
Includes ktor-io, ktor-utils, ktor-network-tls, ktor-client, and related modules.

### [intellij.moduleSets.libraries.temporaryBundled.xml](intellij.moduleSets.libraries.temporaryBundled.xml)

A set of library modules that are temporarily bundled with the platform but should eventually be moved elsewhere.

## Other Module Sets

### [intellij.moduleSets.xml.xml](intellij.moduleSets.xml.xml)

A set of modules providing XML language support and related functionality.

### [intellij.moduleSets.rd.common.xml](intellij.moduleSets.rd.common.xml)

A set of common Remote Development modules.

### [intellij.moduleSets.grid.core.xml](intellij.moduleSets.grid.core.xml)

A set of core grid-related modules.

### [intellij.moduleSets.elevation.xml](intellij.moduleSets.elevation.xml)

A set of modules related to privilege elevation functionality.

### [intellij.moduleSets.debugger.streams.xml](intellij.moduleSets.debugger.streams.xml)

A set of debugger stream tracing modules for Ultimate Edition IDEs.

Includes core stream tracing functionality, shared utilities, and backend integration.

This set is used by Rider, Aqua, and Ultimate.

### [intellij.moduleSets.ssh.xml](intellij.moduleSets.ssh.xml)

A set of SSH-related modules for remote development and deployment features.

Currently includes SSH UI components (`intellij.platform.ssh.ui`).

This set is used by all commercial IDEs, PyCharm Pro, Gateway, JetBrains Client, and Ultimate editions.