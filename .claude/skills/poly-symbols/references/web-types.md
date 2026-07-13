# Web Types

Part of [poly-symbols](../SKILL.md). Web Types is a JSON metadata format for contributing
**statically defined** `PolySymbol`s — no Kotlin/Java code required. It's open source and
IDE-agnostic (schema published on GitHub / JSON Schema Store); it originated for the Vue framework,
which is why a few legacy/deprecated properties still exist.

## When to use Web Types vs. a hand-written `PolySymbolScope`

Use Web Types for **static, enumerable vocabularies** — a fixed list of components/attributes/
properties/events, or a microsyntax pattern that doesn't depend on runtime source analysis. Use a
hand-written `PolySymbolScope`/`PolySymbolQueryScopeContributor` (see
[query-model.md](query-model.md)) when symbols must be derived from the user's own source code
(Vue component props from `defineProps`, Angular directive inputs from `@Input` decorators, GDScript
SDK classes parsed from doctool XML). Most real integrations use **both**, merging static
library/framework vocabulary with dynamically-extracted user symbols (see
[case-studies.md](case-studies.md) — Vue's `VueWebTypesMergedSymbol` is the concrete merge point).

Important, evidence-backed nuance from this repo: **standard HTML5 tags/attributes and standard CSS
properties/pseudo-classes/functions are NOT shipped as Web Types.** They still come from the
bundled RelaxNG HTML5 schema and webref-derived CSS XML respectively (pre-PolySymbols machinery —
see [case-studies.md](case-studies.md#js-ts-html-css)). Web Types in this repo is used for
**framework/library augmentation** (Vue, Angular, htmx, npm packages) and small enumerable
vocabularies (CSS units) — not core spec knowledge.

## File shape

```json
{
  "$schema": "https://json.schemastore.org/web-types.json",
  "name": "my-element",
  "version": "1.0.0",
  "description-markup": "markdown",
  "contributions": {
    "html": {
      "elements": [
        { "name": "my-element", "attributes": [{ "name": "foo" }] }
      ]
    }
  }
}
```

Required top-level: `name`, `version`, `contributions`. `contributions` is organized by
**namespace** — currently `html`, `css`, or `js` — each holding **symbol kind** names as
properties (some directly supported by the IDE, listed below). Each kind maps to an array of
contributions; each contribution needs at minimum a `name`, and can carry sub-contributions
(nested arrays/objects) and custom properties. Sub-contributions default to the parent's namespace
unless nested explicitly under a different namespace key (`js`/`css`/`html`).

**Every contribution corresponds 1:1 to a `PsiSourcedPolySymbol`**, with custom JSON properties
exposed via its `properties` field.

### Patterns in JSON

Mirrors the Kotlin pattern DSL from [patterns.md](patterns.md) — `template`/`items`/`delegate`/
`repeat`/`required`/`priority`/`or`. See that file for the full worked Vue directive example and the
Angular custom-event-modifier example.

## Discovery / loading

Three mechanisms:
- **npm**: auto-discovered via the `"web-types"` field in a package's `package.json` (string or
  array of paths) — this is how `PackageJsonPolySymbolsRegistryManager` picks up third-party
  library Web Types (see [case-studies.md](case-studies.md#js-ts-html-css)).
- **Local project**: the same `"web-types"` field, usable directly in the project's own
  `package.json`.
- **IDE plugin**: register at EP `com.intellij.polySymbols.webTypes`
  (`source` = resource path, `enableByDefault` = whether it's active without a matching dependency).
  Real examples: `plugins/css/backend/resources/web-types/css.web-types.json`,
  `contrib/vuejs/vuejs-backend/resources/web-types/vue@3.5.0.web-types.json`,
  `contrib/Angular/.../web-types/angular-base@0.0.0.web-types.json`.

Loading pipeline: `WebTypesDefinitionsEP`
(`community/platform/polySymbols/src-web/com/intellij/polySymbols/webTypes/impl/WebTypesDefinitionsEP.kt`)
deserializes the JSON (validating `name` + a parseable `SemVer` version) into a `WebTypes`/
`Contributions` model. `WebTypesScopeBase`
(`community/platform/polySymbols/src-web/.../webTypes/WebTypesScopeBase.kt`, extending
`StaticPolySymbolScopeBase`) builds an in-memory contribution index and exposes it through the
normal `getMatchingSymbols`/`getSymbols`/`getCodeCompletions` `PolySymbolScope` contract.

## Special properties

- `inject-language` — injects a language into element text or attribute values (`html/elements`,
  `html/attributes` only).
- `doc-hide-pattern` — hides the "pattern" section in the documentation popup.
- `hide-from-completion` — excludes the symbol from code completion (it still resolves).

## Directly-supported symbol kinds

IDEs natively understand: `html/elements`, `html/attributes`, `css/properties`,
`css/pseudo-classes`, `css/pseudo-elements`, `css/functions`, `css/classes`, `css/parts` (added
2023.2; before 2023.1 the JS plugin was required). Anything else is understood only by whichever
framework's PolySymbols integration defines behavior for that kind.

### Framework-specific kinds

- **Angular** — set `"framework": "angular"`. `js/ng-custom-events` contributes pattern-based
  custom event symbols with modifiers (event-binding syntax like `(click.prevent.stop)`) — see the
  worked example and the important caveat about which modifiers are Angular's own vs. test-fixture
  demonstration in [patterns.md](patterns.md).
- **Vue** — set `"framework": "vue"`. `html/vue-components`, `html/vue-directives` (nested
  `html/argument`, `html/modifiers`), `html/vue-file-top-elements`; nested under components:
  `html/props`, `html/slots` (+ `vue-properties` for scoped slots), `js/events`, `html/vue-model`.
- **Web Components / Lit** — installable as `@web-types/lit`; recommended shape: `/html/attributes`
  for HTML-facing attributes, `/js/properties` for DOM class properties, `/js/events` for events.

## Related

- [Patterns](patterns.md) — the pattern DSL these JSON `pattern` blocks compile down to.
- [poly-context](../../poly-context/SKILL.md) — `contexts-config` inside a Web Types file gates when its symbols are active.
- Official docs: [Web Types](https://plugins.jetbrains.com/docs/intellij/polysymbols-web-types.html).
