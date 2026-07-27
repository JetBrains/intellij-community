# PolySymbols case studies

Part of [poly-symbols](../SKILL.md). Four real integrations in this repo, each covering: where it
lives, which extension points it registers, its scope hierarchy, one or two classes worth reading,
and — the point of this document — an explicit **PolySymbols-vs-legacy verdict** with file:line
evidence. Read the relevant section before designing a new integration; the failure mode this
document exists to prevent is assuming PolySymbols is the sole/authoritative resolution path when
in every studied case it demonstrably is not (see the [main skill](../SKILL.md)'s "additive, not
authoritative" rule).

## GDScript

**Where**: `dotnet/Plugins/godot-support/gdscript/src/main/kotlin/gdscript/polySymbols/` (`psi/`,
`sdk/`, `scope/`, `reference/`, `completion/`, `index/`, `config/`). **Read the module's own
`polySymbols/README.md` first** — it documents its partial-adoption state directly.

**EPs registered** (`.../gdscript/src/main/resources/META-INF/plugin.xml`): `enableInLanguage`
for `GdScript`; a single `queryScopeContributor` (`GdPolySymbolQueryScopeContributor`); five
`psiReferenceProvider`s, one per leaf ref PSI type (`GdTypeHintRef`, `GdInheritanceIdRef`,
`GdRefIdRef`, `GdGetMethodIdRef`, `GdSetMethodIdRef`); `psiLinkedSymbol` for `GdNamedIdElement` and
`GdFile`; one `declarationProvider` (`GdSdkPolySymbolDeclarationProvider`, for synthetic SDK files
only). No `queryConfigurator`. Completion is **not** a PolySymbols EP — five plain
`completion.contributor`s each extend `PolySymbolsCompletionProviderBase` by hand.

**Two symbol hierarchies**, both `abstract class GdPolySymbol : PolySymbol`:
- `GdPsiPolySymbol : PsiLinkedPolySymbol` — real PSI-backed (classes, methods, properties, signals,
  enums, autoloads, loaded-class aliases). It uses the `PsiLinkedPolySymbol` *bridge* (see
  [query-model.md](query-model.md#declarations)), not the plain `PolySymbolDeclaredInPsi` path,
  precisely because it has to: the legacy `psi.referenceContributor`/`PsiReferenceBase` reference
  providers registered on the same host elements (below) still resolve directly to the underlying
  `PsiElement`, and `PsiLinkedPolySymbol` is what keeps that legacy resolve target recognized as a
  usage/rename target of the new `PolySymbol` too — the registered `psiLinkedSymbol host="..."`
  entries for `GdNamedIdElement`/`GdFile` exist for exactly this reason. Several subtypes
  (`GdPsiLocalVariableSymbol`, `GdPsiParameterSymbol`, `GdPsiBindingPatternSymbol`,
  `GdPsiForVariableSymbol`) exist but are **unreachable** — no scope ever produces them.
- `GdSdkPolySymbol` — synthetic, XML-doc-backed (Godot engine classes/methods/signals/annotations/
  operators). No real `PsiElement`; navigation lazily generates a synthetic GDScript file
  (`GdSdkSyntheticPsiCache`) and navigates there. `renameTarget = null` (SDK code isn't renamable).

**Scope hierarchy**: `GdPolySymbolQueryScopeContributor` maps 5 PSI locations (get/set-method-id
refs, inheritance refs, type-hint refs, ref-id refs, annotation types) to scope lists — SDK scopes
(`GdSdkClassesPolySymbolScope`, `GdSdkGlobalPolySymbolScope`, `GdSdkAnnotationsPolySymbolScope`) are
parsed on demand from Godot doctool XML via `GdSdkXmlParser`, cached through
`PolySymbolScopeWithCache`. Qualified references (`a.b`) delegate to `PolySymbolCompoundScope`
subclasses (`GdQualifiedRefIdResolveScope`, `GdQualifiedTypeHintResolveScope`) that resolve the
qualifier to one symbol and re-expose *its* `queryScope` — the `queryScope`-chaining pattern from
[query-model.md](query-model.md).

**Verdict — dual-track, and it's known/acknowledged**: `plugin.xml` registers **both** a legacy
`psi.referenceContributor` (`GdInheritanceReferenceContributor`, `GdTypeHintReferenceContributor`,
`GdSetGetMethodIdReferenceContributor`, `GdRefIdReferenceContributor`, lines 128-134) **and** the
new `polySymbols.psiReferenceProvider` (lines 322-336) for the **same host element classes**,
unconditionally, with no gate between them. The module's README states plainly that this causes
duplicate Find Usages results until the legacy path is deleted. Concretely:
- The legacy `GdClassMemberReference.resolveId()` resolves to for-loop variables, parameters,
  binding patterns, and dict keys in addition to classes/methods/properties; the PolySymbols
  `QUALIFIABLE_SYMBOLS` pattern group covers only `CLASS, METHOD, PROPERTY, CONSTANT, ENUM, SIGNAL,
  LOADED_CLASS_ALIAS, AUTOLOAD` — no locals at all.
- Nested-class qualifiers in `extends Outer.Inner` (`GdInheritanceSubIdRef`) and all resource-path
  references (`GdResourceReferenceContributor`) have **zero** PolySymbols coverage.
- Completion is not a PolySymbols EP integration point — five plain `completion.contributor`s each
  call `PolySymbolsCompletionProviderBase`'s query helpers by hand, then rely on the platform default
  `getCodeCompletions()` (derived from `getSymbols()`) plus a `GdPolySymbolCodeCompletionItemCustomizer`
  (EP `com.intellij.polySymbols.codeCompletionItemCustomizer`, see [query-model.md](query-model.md))
  to populate `icon`/`priority`/`tailText`/`typeText` from `GdPolySymbol`. This covers SDK and
  user-defined/PSI symbols uniformly — the once-present SDK-only completion filter is gone.
- The TSCN language (`.tscn`/resource references) has no PolySymbols involvement whatsoever.

**Worth reading**: `test/.../polySymbols/model/GdCoreSdkPolySymbolModelTest.kt` (SDK query executor
construction, inheritance/member scope queries); `GdInheritancePolySymbolReferenceTest.kt` (clean
resolve-test pattern); `GdPolySymbolsCompletionTest.kt` (its one test is
`@Ignore("Completion not implemented yet")` — itself evidence); `GdSdkPolySymbolRefactoringTest.kt`
(synthetic-file Find Usages end to end).

## JS/TS, HTML, CSS

**Where**: JS-specific glue in `plugins/JavaScriptLanguage/web-platform/src/com/intellij/polySymbols/js/`;
generic `html`-namespace core support in `community/platform/polySymbols/src-web/.../html/`; the
legacy-integration bridges live in `community/xml/xml-psi-impl/.../polySymbols/html/` and
`plugins/css/backend/src/com/intellij/polySymbols/css/`.

**How standard HTML actually resolves**: `XmlTagDelegate.computeElementDescriptor()`
(`community/xml/xml-psi-impl/.../XmlTagDelegate.java:514-548`) iterates all
`XmlElementDescriptorProvider`s, falling back to the RelaxNG-schema-backed `HtmlNSDescriptorImpl`.
PolySymbols' own provider, `HtmlElementSymbolDescriptorsProvider`
(`.../polySymbols/html/elements/HtmlElementSymbolDescriptorsProvider.kt:16-36`), **returns `null`
when the query result `hasOnlyStandardHtmlSymbols()`** — i.e. it deliberately steps aside for plain
`<div>` and lets the legacy RelaxNG-HTML5-schema path (`community/xml/relaxng/resources/.../html5-schema/`)
answer. Same shape on attributes (`HtmlAttributeSymbolDescriptorsProvider.kt:43-67`).

**CSS mirrors this exactly, more explicitly**: `intellij.css.backend.xml` registers
`CssElementSymbolDescriptorProvider` at the *legacy* EP `css.elementDescriptorProvider`, alongside
the pre-existing `CssElementDescriptorProviderImpl` registered with **`order="last"`** as the
guaranteed fallback (line 216-217). Standard property/pseudo-class/function knowledge comes from
`CssElementDescriptorFactory2`, itself sourced from webref-derived XML schema files
(`syntax-data/webref/*.xml`), entirely independent of Web Types. The bundled
`plugins/css/backend/resources/web-types/css.web-types.json` is only ~330 lines (units + a
catch-all class pattern) — it does **not** enumerate the standard CSS property list.

**Plain JS/TS resolution is still the classic engine**: `TypeScriptReferenceExpressionResolver.resolve()`
(`plugins/JavaScriptLanguage/js-analysis-impl/.../TypeScriptReferenceExpressionResolver.kt:53-102`)
consults PolySymbols in exactly one narrow branch — unqualified references inside injected/embedded
expression hosts (`JSEmbeddedContent`, i.e. framework template expressions), lines 65-69. Everything
else — qualified access, calls, ordinary property lookup — goes through
`doResolveReference`/`WalkUpResolveProcessor`/the TS type engine, predating and bypassing
PolySymbols entirely. `JSReferenceExpressionSymbolReferenceProvider`'s own doc comment says
resolving via PolySymbols alone "is not enough, because we need proper type evaluation... by JS
support" — it only *continues* a chain whose qualifier already resolved to a PolySymbol, never
originates one.

**What DOES route through PolySymbols for JS**: `js/properties` (object-literal "shape" symbols,
indexed access) via `JSLiteralExpressionSymbolReferenceProvider`; `js/symbols` modifier merging via
`JSPolySymbolMatchCustomizer`; JSDoc tags via `JSDocSymbolQueryScopeContributor`.

**Web Types as the static source**: loaded by `WebTypesDefinitionsEP`
(`community/platform/polySymbols/src-web/.../webTypes/impl/WebTypesDefinitionsEP.kt`) into
`WebTypesScopeBase`/`StaticPolySymbolScope`. `PackageJsonPolySymbolsRegistryManager`
(`plugins/JavaScriptLanguage/web-platform/.../nodejs/PackageJsonPolySymbolsRegistryManager.kt`)
watches `package.json`/`node_modules` and loads each dependency's bundled/npm-shipped
`web-types.json` or `customElements.json` — this is also what feeds `node-packages`
[PolyContext](../../poly-context/SKILL.md) rules.

**Verdict**: for all three languages, PolySymbols is layered *onto* pre-existing extension points
(`xml.elementDescriptorProvider`, `css.elementDescriptorProvider`, `JSReferenceExpression.resolve()`)
as an additional, conditionally-activating provider — never a replacement — and each PolySymbols
provider contains explicit "defer to legacy for anything standard" logic. Bundled Web Types never
encode core spec data (no full HTML5 tag list, no full CSS property list); they exist for
framework/library augmentation (Vue/Angular/htmx/npm packages) and small enumerable vocabularies
(CSS units). A plugin author adding a new "named string" or "object shape" convention uses
PolySymbols; anyone touching how `foo.bar` resolves for real JS/TS code touches the classic engine.

**Worth reading**: `JSPolySymbolsObjectLiteralFeatureTest.kt` (clearest end-to-end custom
integration — registers its own scope contributor); `PolySymbolsHtmlResolveTest.kt` (custom tag →
`.ts` source module via Web Types); `PolySymbolsCssHighlightingTest.kt`/`PolySymbolsCssCodeCompletionTest.kt`.

## Vue

**Where**: `contrib/vuejs/vuejs-backend/src/org/jetbrains/vuejs/web/` (`scopes/`, `symbols/`), plus
the much older, larger **Vue Model** domain layer (`model/`, `model/source/`, `model/typed/`) that
was retrofitted to implement `PolySymbol`/`PolySymbolScope` directly rather than being wrapped by
new classes.

**EPs** (`contrib/vuejs/vuejs-backend/resources/intellij.vuejs.backend.xml`): `framework id="vue"`;
`context kind="framework" name="vue"` → `VueFileContextProvider`; `queryScopeContributor` ×2
(`VueSymbolQueryScopeContributor`, `VueI18NSymbolQueryScopeContributor`); `queryConfigurator`
(`VueSymbolQueryConfigurator`); `queryResultsCustomizerFactory`; `declarationProvider`
(`VueSymbolDeclarationProvider`); one `psiReferenceProvider` (deprecated `slot="x"` attribute,
kept PolySymbols-aware for back-compat); 17+ `webTypes` registrations (one per Vue 1.x–3.6 minor,
plus `vue-i18n`, `vue-contexts`, `nuxt`).

**How components/props/slots/events become symbols** — both static and dynamic, reconciled:
`VueContainer`/`VueComponent`/`VueInputProperty`/`VueSlot` (the pre-existing Vue Model domain
objects) directly implement `PolySymbol`/`PolySymbolScope` via a `VueSymbol` mixin. Source-derived
data comes from four `vuejs.containerInfoProvider` implementations — Options API `props:`/`emits:`
objects, `defineComponent`, class-API `@Component` decorator, and `<script setup>`
`defineProps`/`defineEmits`/`defineModel`/`defineSlots` macros (`VueScriptSetupInfoProvider.kt:181-232`).
Static library components come from Web Types. `VueWebTypesMergedSymbol` (a `CompositePolySymbol`)
merges same-named source + static symbols so documentation/icon/apiStatus coalesce. Proximity
(local/app/library/global) is attached via `VueComponentWithProximity`-style wrappers and mapped to
`PolySymbol.Priority`.

**Directive microsyntax**: declared in Web Types JSON, not the Kotlin DSL — see the full
`v-on:click.once.alt` worked example in [patterns.md](patterns.md).

**Verdict**: the *template/markup* surface (directives, components, props, slots, events,
modifiers) is essentially 100% PolySymbols-driven and pattern-matched declaratively — matching the
docs' "heaviest adopter" framing. But real, acknowledged gaps remain outside that surface:
- `VueAttributeValueCompletionProvider.kt:20` has a literal `// TODO move to web-types` comment —
  `lang="..."` and slot-name completion are still hand-rolled `CompletionProvider`s, not PolySymbols.
- `VueReferenceContributor` (plain `psi.referenceContributor`) still handles `<style src>`/
  `<template src>` file references and `ref="x"` → script-side declaration — no PolySymbols
  equivalent exists for "file path" or "PSI variable" symbol kinds.
- Vuex (`mapState`/`mapActions`/store-module keys) is a fully self-contained legacy subsystem,
  never integrated with PolySymbols.
- CSS `v-bind()`/CSS-modules class references (`VueCssReferencesContributor`) are plain PSI
  references.

**Worth reading**: `VueCompletionTest.kt` (`testEventModifiers`, `testEventsAfterVOn` — the
directive-microsyntax completion end to end; `setUp()` explicitly calls
`enableIdempotenceChecksOnEveryCache()` from `com.intellij.polySymbols.testFramework` to catch
registry regressions); `VueComponentTest.kt` (props/emits/slots resolution across Options/Composition/
`<script setup>` APIs, golden-file diffing).

## Angular

**Where**: `contrib/Angular/angular-backend/src/org/angular2/web/` (`scopes/` — 21 files,
`findUsages/`, `references/`, `declarations/`), plus `library/forms/` for Reactive Forms (the only
`library/` feature module — no dedicated CDK/Material/Router module; those go through generic
selector resolution + Web Types instead).

**EPs** (`contrib/Angular/angular-plugin/resources/META-INF/plugin.xml`): `framework id="angular"`;
`context kind="framework" name="angular"` → `AngularCliContextProvider`; `queryScopeContributor`
(`Angular2SymbolQueryScopeContributor`); `queryConfigurator`; `queryResultsCustomizerFactory` ×2
(base + Forms); 6 `psiReferenceProvider`s (selectors, directive-property literals, block/block-param
refs, template-binding keys); 5 `declarationProvider`s; `psiLinkedSymbolProvider`
(`Angular2PsiLinkedPolySymbolProvider`); `highlightingCustomizer`; 18+ `webTypes` (one per Angular
version, plus `angular-base`, `angular-hacks`, `hammerjs`, `ionic-angular`).

**How components/directives/pipes become symbols**: pre-modeled as a full domain graph in
`entities/` (`Angular2ClassBasedComponent`/`Directive`/`Pipe`, `Angular2DirectiveProperty`,
`Angular2DirectiveSelector`, with three parallel backends — source/ivy/metadata — selected via the
`entitiesSource` EP), computed from `@Component`/`@Directive`/`@Pipe`/`@Input`/`@Output` decorator
metadata. These entities *implement* `PolySymbol`/`Angular2Symbol` directly; scope classes just
query `Angular2EntitiesProvider` and surface them, filtered by `Angular2SymbolQueryResultsCustomizer`
for module/standalone-imports scope (in-scope / importable / unreachable → warnings + import
quick-fixes).

**`PsiLinkedPolySymbolProvider` — why Angular specifically needs it**: `Angular2PsiLinkedPolySymbolProvider`
recovers the `Angular2DirectiveProperty` for a raw `TypeScriptField` (e.g. right-clicking
`@Input() foo` in a `.ts` file) so `PsiLinkedPolySymbolReferenceSearcher` can expand **every name
variant** (`@Input('alias')`, kebab-case attribute form, banana-in-a-box binding form) via
`PolySymbolNamesProvider.getNames(...)` and search for each — without it, Find Usages started from
the class field would silently degrade to a literal-name text search and miss aliased template
usages. See [query-model.md](query-model.md) for the general mechanism.

**Custom-event pattern**: Angular's *own* `js/ng-custom-events` usage in `angular-base@0.0.0.web-types.json`
covers extended key-event modifiers (`(keydown.control.shift.enter)`), not `.prevent`/`.stop` — that
exact modifier pair appears only in a **test fixture**
(`contrib/Angular/angular-tests/testData/highlighting/customUserEvents/custom-user-events.web-types.json`)
demonstrating that any third-party library can add its own event-modifier microsyntax purely via a
Web Types contribution, no Kotlin required. See [patterns.md](patterns.md) for the JSON.

**Angular Forms** (`library/forms/`): `Angular2FormsPolySymbolQueryResultsCustomizer` wraps matched
`formControlName`/`formGroupName`/`formArrayName` attribute symbols so their *value* resolves as a
symbol reference (`PolySymbolHtmlAttributeValue.create(PLAIN, Type.SYMBOL, required = true)`);
`Angular2FormsSymbolQueryScopeContributor` supplies the actual `FormGroup`/`FormBuilder.group()`
-derived prop symbols (via `Angular2FormSymbolsBuilder`) plus a `ReferencingPolySymbol`-style mapping
scope. Net effect: full go-to-declaration/rename/find-usages/completion/diagnostics for
`formControlName="usern|ame"`, built entirely on generic PolySymbols machinery — the deepest,
most complete framework-feature integration studied.

**Verdict**: the markup/selector surface (directives, components, pipes, selectors, Forms) is
~100% PolySymbols-driven. But `Angular2TSReferencesContributor` (plain `PsiReferenceContributor`)
still handles `templateUrl`/`styleUrl(s)` file refs and `@Pipe({name: ...})` literal refs outside
PolySymbols (appropriately — these are file/generic-PSI references, not symbol kinds); more
significantly, the **Angular2 expression language** used inside `{{ }}` and bindings is implemented
as a genuine `JSLanguageDialect`, so its reference/completion infrastructure is native JS/TS PSI
machinery that PolySymbols *supplements* (via `PolySymbolsCompletionProviderBase`, a couple of
`psiReferenceProvider`s for block params/template-binding keys) rather than replaces.

**Worth reading**: `Angular2HighlightingTest.testCustomUserEvents` (the `.prevent`/`.stop` pattern
end to end, with diagnostics); `Angular2RenameTest.testDirectiveInputMappedObject`/
`testHostDirectiveInputForwarded` (the `PsiLinkedPolySymbolProvider` name-variant rename story);
`Angular2FormsCodeCompletionTest.kt`/`Angular2FormsRenameRefactoringTest.kt` (Forms end to end).
