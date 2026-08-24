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
`sdk/`, `scope/`, `reference/`, `completion/`, `index/`, `config/`), plus own-references wiring on
the ref PSI classes themselves (`gdscript/psi/impl/`) and on TSCN (`tscn/psi/impl/TscnNamedElementImpl.kt`).
The module's own `polySymbols/README.md` is a snapshot from the initial partial-adoption phase
(2026-03) and is now stale on several points superseded below (e.g. it still lists local
variables/parameters/for-loop bindings as unimplemented) — treat it as historical color, not current
truth; this section reflects the codebase as of the `RIDER-140303` own-references migration
(completed 2026-07-22).

**EPs registered** (`.../gdscript/src/main/resources/META-INF/plugin.xml`): `enableInLanguage` for
**both** `GdScript` and `Tscn`; a single `queryScopeContributor` (`GdPolySymbolQueryScopeContributor`);
**zero** `psiReferenceProvider`s and **zero** `psiLinkedSymbol` registrations (both existed during the
initial implementation and were removed once own references took over — see "Verdict" below); two
`declarationProvider`s (`GdSdkPolySymbolDeclarationProvider` for synthetic SDK files,
`GdPsiPolySymbolDeclarationProvider` for real PSI); a `highlightingCustomizer`
(`GdPolySymbolHighlightingCustomizer`); an `inspectionToolMapping` suppressing the generic
unresolved-reference warning for the `qualifiable-symbol` kind (backed by the intentionally inert
`GdUnrecognizedIdentifierInspection`, see [query-model.md](query-model.md#references--own-references-polysymbolownreferences));
two `referencesSearch` executors, `GdOwnReferencesSearcher` and `GdConstructorReferencesSearcher`
(see below). No `queryConfigurator`. Completion is still **not** a PolySymbols EP — five plain
`completion.contributor`s (`gdscript.polySymbols.completion.*`) each extend
`PolySymbolsCompletionProviderBase` by hand.

**Two symbol hierarchies**, both `abstract class GdPolySymbol : PolySymbol`
(`gdscript/polySymbols/GdPolySymbols.kt`):
- `GdPsiPolySymbol : PolySymbolDeclaredInPsi` (`gdscript/polySymbols/psi/GdPsiPolySymbol.kt`) — real
  PSI-backed (classes, methods, properties, signals, enums, autoloads, loaded-class aliases, locals,
  parameters, for-loop/binding-pattern variables). It used to implement the `PsiLinkedPolySymbol`
  *bridge* (see [query-model.md](query-model.md#declarations)) instead of plain
  `PolySymbolDeclaredInPsi`, because the legacy `psi.referenceContributor`/`PsiReferenceBase`
  providers that used to be registered on the same host elements resolved directly to the underlying
  `PsiElement`, and the bridge was what kept that legacy resolve target recognized as a usage/rename
  target of the new `PolySymbol` too. Once those legacy contributors were deleted in favor of own
  references, the bridge's *raison d'être* went with them: `PolySymbolDeclaredInPsi`'s own doc
  comment spells out the tradeoff it accepts instead — "any usage or rename searches for the
  `PsiElement` returned by `sourceElement` will not result in the symbol being recognized as a usage
  or rename target." **`GdOwnReferencesSearcher`** (`gdscript/search/GdOwnReferencesSearcher.kt`) is
  the generalized fix for exactly that gap: a `ReferencesSearch` executor that classic word-searches
  for the declaration's name, resolves each text occurrence's own-references, and matches against the
  target — recovering the "recognized as a usage" behavior `PsiLinkedPolySymbol` used to provide for
  free, without the bridge itself. `GdConstructorReferencesSearcher` is the earlier, narrower
  special-case this generalizes from: it makes Find Usages on a constructor (`_init`) also surface
  `ClassName.new(...)` call sites, which don't textually contain `_init` at all.
- `GdSdkPolySymbol` — synthetic, XML-doc-backed (Godot engine classes/methods/signals/annotations/
  operators). No real `PsiElement`; navigation lazily generates a synthetic GDScript file
  (`GdSdkSyntheticPsiCache`) and navigates there. `renameTarget = null` (SDK code isn't renamable).
  Implements neither `PolySymbolDeclaredInPsi` nor `PsiLinkedPolySymbol` — it never had a real
  declaration site to link.

**Local variables, parameters, for-loop bindings, and binding patterns are now reachable** —
`GdPsiPolySymbolDeclarationProvider` produces `GdPsiLocalVariableSymbol`/`GdPsiParameterSymbol`/
`GdPsiForVariableSymbol`/`GdPsiBindingPatternSymbol` from real PSI, and a dedicated
`GdLocalSymbolsStructuredScope` walks each file's method/lambda/loop/conditional blocks to expose
them with proper nesting, wired into the unqualified `GdRefIdRef` scope list alongside the
`QUALIFIABLE_SYMBOLS` pattern group. (This closes the gap the platform-generic
`PolySymbolDeclarationProvider`/scope story in [query-model.md](query-model.md) doesn't otherwise
cover: unqualified local resolution needs its own per-file structural scope, not just a
declaration provider.)

**Own references, not EP-registered external references, are now GDScript's canonical resolve
mechanism** for every ref PSI kind except one: `GdRefElement`
(`gdscript/psi/GdRefElement.kt`) — the shared base interface for all leaf ref types — implements
`PolySymbolOwnReferenceHost` directly (see
[query-model.md](query-model.md#references--own-references-polysymbolownreferences) for the marker
interface itself). `GdRefIdRef`, `GdTypeHintRef`, `GdInheritanceIdRef`, `GdInheritanceSubIdRef`,
`GdGetMethodIdRef`, and `GdSetMethodIdRef` all override `getOwnReferences()` on their `*Impl` class
and build the result via `polySymbolOwnReferences { ... }` — `GdRefIdRefImpl` is the query-model
doc's own worked example of the lower-level `reference(range, kind, resolver)` form (branching on
`new`/`self`/`super`/math-constant tokens before falling back to a name-match query). The one
holdout is `GdStringValRef` (resource-path string literals): it has no `getOwnReferences()` override
and resolves purely through the still-registered legacy `GdResourceReferenceContributor`
(`psi.referenceContributor` on `GdTypes.STRING_VAL_NM`) — resource paths have **zero** PolySymbols
coverage, same as before.

**Scope hierarchy**: `GdPolySymbolQueryScopeContributor` maps PSI locations (get/set-method-id refs,
inheritance refs including nested `extends Outer.Inner` sub-refs, type-hint refs, ref-id refs
[layering `GdLocalSymbolsStructuredScope` on top for locals], annotation types) to scope lists — SDK
scopes (`GdSdkClassesPolySymbolScope`, `GdSdkGlobalPolySymbolScope`, `GdSdkAnnotationsPolySymbolScope`)
are parsed on demand from Godot doctool XML via `GdSdkXmlParser`, cached through
`PolySymbolScopeWithCache`. Qualified references (`a.b`, `Outer.Inner`) delegate to
`PolySymbolCompoundScope` subclasses (`GdQualifiedRefIdResolveScope`, `GdQualifiedTypeHintResolveScope`,
`GdQualifiedInheritanceResolveScope`) that resolve the qualifier to one symbol and re-expose *its*
`queryScope` — the `queryScope`-chaining pattern from [query-model.md](query-model.md).

**TSCN now has own-references-only PolySymbols coverage** (added 2026-07-18, after this case study
was first written — TSCN previously had none at all). `TscnNamedElement` implements
`PolySymbolOwnReferenceHost`; `TscnNamedElementImpl.getOwnReferences()` resolves two specific things
into GDScript-side symbols: a `script_class="MyClass"` header value → the matching `GdClassSymbol`,
and a `.tres` data-line key → the matching `@export var` field's `GdPsiPropertySymbol` on the script
referenced by the containing `ExtResource`. Nothing else in TSCN participates — no scope contributor,
no declaration provider, no PolySymbols-driven completion; plain `res://` resource-path values on the
same host element stay on the classic `TscnResourceReferenceContributor`, deliberately left alone
(see the comment in `TscnNamedElementImpl.kt`).

**Verdict — still dual-track by design, but the shape of the split changed**: the *reference*
half of the original "both mechanisms registered on the same host, unconditionally" problem is
resolved — own references now fully replace the legacy `psi.referenceContributor`s for every ref
kind except resource paths (`GdStringValRef`/TSCN `res://`), which remain the one deliberate,
un-migrated legacy island. What's still true and load-bearing:
- Completion is still not a PolySymbols EP integration point — five plain `completion.contributor`s
  each call `PolySymbolsCompletionProviderBase`'s query helpers by hand, then rely on the platform
  default `getCodeCompletions()` (derived from `getSymbols()`) plus a
  `GdPolySymbolCodeCompletionItemCustomizer` (EP `com.intellij.polySymbols.codeCompletionItemCustomizer`,
  see [query-model.md](query-model.md)) to populate `icon`/`priority`/`tailText`/`typeText` from
  `GdPolySymbol`. This covers SDK and user-defined/PSI symbols uniformly — the once-present SDK-only
  completion filter is gone.
- Resource-path references (`GdResourceReferenceContributor`, and TSCN's mirror) remain fully
  legacy, by deliberate choice, not oversight — the current `GdStringValRef` design simply never
  routes them through PolySymbols at all.
- The move off `PsiLinkedPolySymbol` traded one problem (duplicate Find Usages results between two
  live resolution mechanisms, which the module's README used to call out) for a narrower one (needing
  a hand-written `ReferencesSearch` executor, `GdOwnReferencesSearcher`, to recover usage-search
  parity) — read as confirmation that `PolySymbolDeclaredInPsi`'s "no usage-search bridge" tradeoff
  (see [query-model.md](query-model.md#declarations)) is a real cost every adopter must pay one way
  or another, not a corner nobody hits in practice.

**Worth reading** (current file names — the ones previously listed here no longer exist, renamed/
replaced during the own-references migration): `test/.../gdscript/model/GdCoreSdkModelTest.kt` (SDK
query executor construction, inheritance/member scope queries); `test/.../gdscript/resolve/ResolveTestBase.kt`
+ `ResolveInSingleFileTest.kt`/`ResolveMultiFileTest.kt` (the Symbol-API-first resolve-dumper pattern
from [migration.md](migration.md)); `test/.../gdscript/completion/GdCompletionTest.kt` (completion is
actively tested now, not the `@Ignore`d placeholder this pointer used to cite);
`test/.../tscn/refactoring/rename/ScriptClassRenamingTest.kt` +
`ResourceFieldRenamingTest.kt` (own-references end to end — rename purely through
`CodeInsightTestFixture.renameSymbolAtCaret(...)`, no own-references-specific plumbing, see
[testing.md](testing.md)); `test/.../gdscript/highlight/GdPolySymbolHighlightingCustomizerTest.kt`
(the `GdPolySymbolHighlightingCustomizer` from above).

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
