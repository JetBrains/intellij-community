# RD Client Module Loading

Entry point: `RdClientModuleLoadingValidator` (`rdClientModuleLoadingValidation`).

## Inputs

- Products discovered from the product registry.
- Product content, nested module sets, and bundled production plugins from the plugin graph.
- Plugins declared by the modular loader in `product-modules.xml` and its includes.
- Loading overrides and capability aliases from the product specification.
- Effective content and plugin dependency plans.
- Module visibility and aliases from the descriptor cache.

## Rules

The rule checks activation in the default production configuration. Bundling an on-demand module does not activate it.

- Resolve required dependencies within each product.
- Exclude candidates with unresolved dependencies and propagate exclusions to their dependents.
- Exclude a plugin if its required or embedded content cannot load.
- Start activation from eligible plugins and content with embedded, required, or optional loading.
- Activate on-demand modules through dependency chains from those roots.
- Repeat exclusions and activation until the result stops changing. An isolated on-demand cycle cannot activate itself.

Products named `Rider` or `CLion`, and names ending with `JetBrainsClient`, must activate these modules:

- `intellij.rd.client`
- `intellij.rd.client.base`

Other RD client modules can remain inactive or be absent in these products.

Every other product must leave all modules with the `intellij.rd.client` prefix inactive.
The prefix check also covers future modules.

Synthetic test products and test plugin content do not participate in this check.
The positive requirement excludes `intellij.rd.client.testFramework`.

## Output

The rule emits one `RdClientModuleLoadingError` per product with violations.
Unexpected modules include their activation paths. Missing required modules include their exclusion reasons.
For unexpected activation, the error suggests a missing dependency on `intellij.platform.frontend.split` in a `.frontend.split` module.
Without this dependency, the module can load in a monolith IDE.

The rule does not change files or accept error suppressions.
Dependencies omitted from generated descriptors do not create activation paths.

## Limits

The analysis uses the product configuration. It does not start IDE processes or inspect JVM classes.
User-installed plugins and custom runtime settings are outside this check.
