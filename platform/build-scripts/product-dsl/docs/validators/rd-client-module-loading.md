# RD Client Module Loading

Entry point: `RdClientModuleLoadingValidator` (`rdClientModuleLoadingValidation`).

## Inputs

- Products discovered from the product registry.
- Product content, nested module sets, and bundled production plugins from the plugin graph.
- All bundled plugins from the product properties, including modular loader declarations.
- Compatible plugins from the product configuration.
- Expanded CLion test plugin specifications and their additional bundled plugins.
- Loading overrides and capability aliases from the product specification.
- Effective content, production plugin, and test plugin dependency plans.
- Module visibility and aliases from the descriptor cache.

## Rules

The rule checks production configurations and the CLion test configurations. Bundling an on-demand module does not activate it.

- Resolve required dependencies within each product.
- Exclude candidates with unresolved dependencies and propagate exclusions to their dependents.
- Exclude a plugin if its required or embedded content cannot load.
- Start activation from eligible plugins and content with embedded, required, or optional loading.
- Activate on-demand modules through dependency chains from those roots.
- Repeat exclusions and activation until the result stops changing. An isolated on-demand cycle cannot activate itself.

`Rider`, CLion Nova, IDEA with the C++ plugin, and products whose names end with `JetBrainsClient` must activate these modules:

- `intellij.rd.client`
- `intellij.rd.client.base`

Other RD client modules can remain inactive or be absent in these products.

Every other product, including CLion Classic, must leave all modules with the `intellij.rd.client` prefix inactive.
The prefix check also covers future modules.

CLion scenarios disable the other engine plugin by its plugin ID.
The test scenarios add the CLion dev-build test plugin and apply the same engine rules.
The positive requirement excludes `intellij.rd.client.testFramework`. An active test consumer can load that framework on demand.

For products that prohibit RD client activation, the rule also checks all configured compatible plugins together.
It retains baseline violations and their paths when additional plugins exclude a baseline consumer.
The compatible-plugin check excludes the C++ plugin. A separate IDEA scenario requires RD client activation with that plugin.

Synthetic products and unselected test plugins do not participate in this check.

## Output

The rule emits one `RdClientModuleLoadingError` per scenario with violations.
Unexpected modules include their activation paths. Missing required modules include their exclusion reasons.
For unexpected activation, the error suggests a missing dependency on `intellij.platform.frontend.split` in a `.frontend.split` module.
Without this dependency, the module can load in a monolith IDE.
For test configurations, the error also suggests on-demand loading for unused RD test frameworks.

The rule does not change files or accept error suppressions.
Dependencies omitted from generated descriptors do not create activation paths.

## Limits

The analysis uses the product configuration. It does not start IDE processes or inspect JVM classes.
Plugins outside the configured compatible set and custom runtime settings are outside this check.
