# Restricted Module Activation

Entry point: `RestrictedModuleActivationValidator` (`restrictedModuleActivationValidation`).

## Declarations

- A module set or a spec marks a module as restricted: `onDemandModule(name, restricted = true)`.
- A `ModuleActivation` holds the `required` modules and the `allowed` modules. The `allowed` set always includes the `required` set.
- A product spec grants an activation with `moduleActivation(activation)`.
- The generator config grants an activation to a plugin with `ModuleSetGenerationConfig.pluginModuleActivations`. The key is the plugin ID.
- A product spec declares plugins of which only one loads at a time with `exclusivePlugins(...)`.
- A DSL test plugin opts in to the check with `testPlugin(..., checkModuleActivation = true)`.

The RD client modules use these declarations:

- `CommunityModuleSets.rdCommon()` marks the `intellij.rd.client*` modules as restricted.
- `rdClientActivation` requires `intellij.rd.client` and `intellij.rd.client.base`, and allows the other RD client modules.
- Rider and the JetBrains Client products grant `rdClientActivation`.
- The generator config grants `rdClientActivation` to the Radler plugin. This covers CLion Nova and IDEA with the C++ plugin.
- CLion declares the Classic and Radler plugins as exclusive plugins.

## Inputs

- Products discovered from the product registry, with their product mode.
- Product content, nested module sets, and bundled production plugins from the plugin graph.
- All bundled plugins from the product properties, including modular loader declarations.
- Compatible plugins from the product configuration.
- Expanded DSL test plugin specifications and their additional bundled plugins.
- Loading overrides and capability aliases from the product specification.
- Effective content, production plugin, and test plugin dependency plans.
- Module visibility and aliases from the descriptor cache.

## Activation Analysis

Bundling an on-demand module does not activate it.

- Exclude the modules that the product mode excludes. `ProductModeLoadingRules.getIncompatibleRootModules` supplies them.
- Resolve required dependencies within each product.
- Treat a `required-if-available` module as required when the product mode keeps its target.
- Exclude candidates with unresolved dependencies and propagate exclusions to their dependents.
- Exclude a plugin if its required or embedded content cannot load.
- Start activation from eligible plugins and content with embedded, required, or optional loading.
- Activate on-demand modules through dependency chains from those roots.
- Repeat exclusions and activation until the result stops changing. An isolated on-demand cycle cannot activate itself.

## Configurations

- A product without exclusive plugins has one base configuration.
- A product with exclusive plugins has one base configuration per exclusive plugin. The other exclusive plugins stay disabled.
- Each base configuration runs once more with each opted-in test plugin.
- Each base configuration also runs with all compatible plugins that have no grant. The rule skips this run when the base configuration allows all restricted modules.
- Each compatible plugin with a grant gets its own configuration, together with the compatible plugins that have no grant.
  The rule skips a grant that the product activation already covers.

## Rules

The effective activation of a configuration joins the product activations with the grants of the plugins in the configuration.
A granting plugin counts when it takes part in the configuration, also when the analysis excludes it.

- An active restricted module must be in the `allowed` set of the effective activation.
- Each module in the `required` set of the effective activation must be active.

Synthetic products and test plugins without the opt-in do not participate in this check.

## Output

The rule emits one `RestrictedModuleActivationError` per configuration with violations.
Unexpected modules include their activation paths. Missing required modules include their exclusion reasons.
For unexpected activation, the error lists the modules that the product mode excludes.
A consumer that must not load in this mode needs a dependency on one of them.
For test configurations, the error also suggests on-demand loading for unused restricted test frameworks.

The rule does not change files or accept error suppressions.
Dependencies omitted from generated descriptors do not create activation paths.

## Limits

The analysis uses the product configuration. It does not start IDE processes or inspect JVM classes.
Plugins outside the configured compatible set and custom runtime settings are outside this check.

The analysis does not model these parts of the startup resolver:

- Compatibility dependencies that the product adds at startup.
- The exclusion of dependency cycles.
- Private visibility of product content. The rule follows the Product DSL convention that module sets and product content are shared.
- Product-mode rules outside `ProductModeLoadingRules`, for example the light mode.
