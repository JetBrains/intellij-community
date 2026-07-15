# PolySymbols query model

Part of [poly-symbols](../SKILL.md). This is the "how it actually resolves" reference: the query
executor, scopes, contributors/configurators, declarations, references, completion, and how a
resolved `PolySymbol` lights up search/rename/navigation/documentation.

## PolySymbolQueryExecutor

Central entry point (`community/platform/polySymbols/src/com/intellij/polySymbols/query/PolySymbolQueryExecutor.kt`).
Built via `PolySymbolQueryExecutorFactory.create(location: PsiElement)`, which configures the
executor from every registered `PolySymbolQueryScopeContributor` and `PolySymbolQueryConfigurator`
based on the PSI location — contributors supply initial scopes, configurators supply `PolyContext`
and name-conversion rules. Three query kinds:

```kotlin
executor.nameMatchQuery(kind, name).run(): List<PolySymbol>          // resolve a specific name
executor.listSymbolsQuery(kind, expandPatterns).run(): List<PolySymbol>  // enumerate everything of a kind
executor.codeCompletionQuery(kind, name, position).run(): List<PolySymbolCodeCompletionItem>
```

All three accept `additionalScope(...)` to push extra scopes onto the query on top of whatever the
contributors registered — this is how a reference provider narrows/extends resolution for one
specific query (e.g. GDScript's qualified-reference scopes, described in
[case-studies.md](case-studies.md)).

## PolySymbolScope

```kotlin
interface PolySymbolScope {
  fun createPointer(): Pointer<out PolySymbolScope>
  fun getMatchingSymbols(qualifiedName, params, stack): List<PolySymbol>   // default: getSymbols().flatMap { it.match(...) }
  fun getSymbols(kind, params, stack): List<PolySymbol> = emptyList()
  fun getCodeCompletions(qualifiedName, params, stack): List<PolySymbolCodeCompletionItem>
  fun isExclusiveFor(kind): Boolean = false
}
```

A scope is itself often a `PolySymbol` that contains other symbols (an HTML element scoping its
attributes, a JS class scoping its members) — `PolySymbol.queryScope` defaults to `listOf(this)` if
the symbol implements `PolySymbolScope`.

**For any scope backed by more than a handful of symbols, or whose computation should be cached**,
use the `polySymbolScopeCached(...)` DSL
(`community/platform/polySymbols/src/com/intellij/polySymbols/query/PolySymbolScopeDsl.kt`) rather
than hand-extending `PolySymbolScopeWithCache` — it's the same `PolySymbolScopeWithCache`/
`CachedValuesManager` engine underneath, just without the manual `createPointer()`/`equals`/
`hashCode` boilerplate:

```kotlin
fun <T : PsiElement, K> polySymbolScopeCached(element: T, key: K, configure: PsiPolySymbolScopeCachedBuilder<T, K>.() -> Unit): PolySymbolScope
fun <T : PsiElement> polySymbolScopeCached(element: T, configure: PsiPolySymbolScopeCachedBuilder<T, Unit>.() -> Unit): PolySymbolScope
fun polySymbolScopeCached(project: Project, configure: ProjectPolySymbolScopeCachedBuilder<Unit>.() -> Unit): PolySymbolScope
fun <T : UserDataHolder, K> polySymbolScopeCached(project: Project, dataHolder: T, key: K, configure: PolySymbolScopeCachedBuilder<T, K>.() -> Unit): PolySymbolScope
```

Four overloads keyed by holder shape — a `PsiElement` (cache keyed on that element, invalidated via
whatever you pass to `cacheDependencies(...)`), a `Project` (`Unit` key, hard-pointer), or an
arbitrary `UserDataHolder` + explicit key (requires an explicit `pointer { }` in the config lambda —
omitting it throws). The builder exposes `provides(...)`/`exclusiveFor(...)`/`requiresResolve(...)`
as one-liners, plus `filterCodeCompletions { }`/`filterNameMatches { }` to post-process the cached
base result without subclassing. The `initialize { }` block must call `cacheDependencies(...)` with
a non-empty set (throws otherwise) and add symbols via `add`/`addAll`/`+`/`addSymbol`. Real examples:
`contrib/Astro/src/org/jetbrains/astro/polySymbols/scope/AstroAvailableComponentsScope.kt` (Project,
Unit key), `AstroNamespacedComponentsScope.kt` (PsiElement, Unit key, with `filterCodeCompletions`).

**Caveat**: the DSL does not expose `PolySymbolScopeWithCache.partialMatchingSupport` — if a scope
answers `getMatchingSymbols` via a direct name-indexed lookup instead of building the full symbol map
first (e.g. GDScript's `GdPsiClassesPolySymbolScope`/`GdPsiResourceClassesPolySymbolScope`, see
[case-studies.md](case-studies.md#gdscript)), keep it hand-written — converting would force every
lookup through the full-cache path, a real perf regression for a project-wide index. Reach for
hand-written `PolySymbolScopeWithCache` only for that case, or when you need custom `createPointer()`
chaining through an owning symbol (rare).

`isExclusiveFor(kind)`: when a scope is exclusive for a kind, pattern-matching stops walking further
down the scope stack for that kind once this scope has been consulted — use it when a scope is
known to be a complete, self-contained answer (no outer scope could contribute more).

## PolySymbolQueryScopeContributor — the registrar DSL

```kotlin
interface PolySymbolQueryScopeContributor {
  fun registerProviders(registrar: PolySymbolQueryScopeProviderRegistrar)
}
```

Register at EP `com.intellij.polySymbols.queryScopeContributor`. The registrar is a **builder**:
each call returns a new stage, so you can freely reuse partial chains. Order of calls does not
affect performance — the framework reorders conditions to match contributors against locations
efficiently, conceptually like how completion contributors are matched.

```kotlin
registrar
  .inFile(AstroFileImpl::class.java)
  .inContext { it.framework == AstroFramework.ID }        // gate by PolyContext — see poly-context skill
  .apply {
    forAnyPsiLocationInFile()
      .contributeScopeProvider { location -> listOf(AstroFrontmatterScope(location.containingFile as AstroFileImpl)) }

    forPsiLocation(CssElement::class.java)
      .contributeScopeProvider { location ->
        location.parentOfType<XmlTag>()
          ?.takeIf { StringUtil.equalsIgnoreCase(it.name, HtmlUtil.STYLE_TAG_NAME) }
          ?.let { listOf(AstroStyleDefineVarsScope(it)) }
        ?: emptyList()
      }
  }
```

Chain shape: `inFile(s)`/no-file-filter → `inContext { }` (optional) → `withResolveRequired()`
(optional — restricts to locations where resolve, not just listing, is actually needed) →
`forPsiLocation(s)`/`forAnyPsiLocationInFile()`/`forAnywhere()` → `contributeScopeProvider { }`.
`forAnywhere()` (no file filter at all) is used for scopes that should apply project-wide
regardless of file type — e.g. the Node/package.json Web Types scope in the JS platform.

### PolySymbolQueryStack and `queryScope` chaining

Scopes contributed for a location seed the initial `PolySymbolQueryStack`. During pattern matching,
**every matched symbol's `queryScope` is pushed onto the stack**, extending what the *next* segment
of a microsyntax pattern (or the next qualifier in a dotted reference) can resolve against. This is
how e.g. `a.b.c` chains: resolving `a` pushes `a`'s member scope, so `b` resolves against it, and so
on — see the `PolySymbolCompoundScope` delegation pattern in
[case-studies.md](case-studies.md#gdscript) for a concrete qualifier-resolution implementation.

## PolySymbolQueryConfigurator

```kotlin
interface PolySymbolQueryConfigurator {
  fun getContextRulesProviders(project, dir): List<PolyContextRulesProvider> = emptyList()
  fun getNameConversionRulesProviders(project, element, context): List<PolySymbolNameConversionRulesProvider> = emptyList()
  fun beforeQueryExecutorCreation(project) {}
}
```

Register at EP `com.intellij.polySymbols.queryConfigurator`. Two jobs: dynamic `PolyContext` rules
(see [poly-context](../../poly-context/SKILL.md)), and **name-conversion rules** — mapping between
a symbol's canonical name and the various surface spellings it can appear as (kebab-case HTML
attribute ↔ camelCase JS property, or Vue's `v-` directive internal-name stripping). Conversion
rules apply uniformly to `canonicalNames`, `renames`, and `completionVariants` so that resolve,
rename, and completion never disagree about which spellings are valid.

## Declarations

Three paths — pick based on whether the symbol has a backing `PsiElement` at all, and if so,
whether that `PsiElement` also needs to keep working as a target for **legacy, non-PolySymbols**
find-usages/rename:

- **`PolySymbolDeclaredInPsi`** (`community/platform/polySymbols/src/com/intellij/polySymbols/utils/PolySymbolDeclaredInPsi.kt`)
  — **the default for a PSI-backed symbol that lives purely in the PolySymbols model.** Override
  `sourceElement`/`textRangeInSourceElement`, and register a `PolySymbolDeclarationProvider`
  (EP `com.intellij.polySymbols.declarationProvider`) that instantiates/finds the symbol for a given
  `PsiElement` and returns its `declaration`. You get navigation, find-usages, and rename for the
  *symbol* for free. It does **not** link the symbol to the `PsiElement` for reverse lookups — a
  usage/rename search started directly on `sourceElement` (bypassing the symbol) will not find or
  rename it, and this includes any *other*, unrelated classic `PsiReferenceBase` elsewhere in the
  project (even in another language/file type) that resolves straight to `sourceElement` — that
  classic reference will not be recognized as a usage of the symbol. There is no supported way to
  get that back other than `PsiLinkedPolySymbol` below (which requires the *declaring* symbol to
  implement it, not something the referencing side can opt into) — if you hit this, it's a real
  platform limitation, not a bug in your provider; stop and ask before reaching for a platform patch
  (see [SKILL.md](../SKILL.md#do-not-modify-polysymbols-platform-source)).
  **The provider must be pure dispatch — decide *which* symbol(s) a `PsiElement` backs, then return
  `symbol.declaration` (the interface's own default, built from `sourceElement`/
  `textRangeInSourceElement`).** Do not hand-roll a `PolySymbolDeclaration` implementation inside the
  provider, and do not thread a range through provider-side helper functions. If a symbol's
  declaration range differs from the default (`TextRange(0, sourceElement.textLength)`) — e.g. a
  symbol anchored to a whole file with no real name-identifier token to point at — override
  `textRangeInSourceElement` **on that symbol class**, not in the provider: the symbol represents
  itself, the provider only knows which symbol to build. See GDScript's
  `GdPsiPolySymbolDeclarationProvider`/`GdPsiResourceClassSymbol` for the worked example (the latter
  overrides `textRangeInSourceElement` to `TextRange.EMPTY_RANGE` since a whole-file declaration has
  no caret-anchorable position). Reach for `PolySymbolDeclaredInPsi` first; it doesn't require a
  legacy PSI-reference mechanism to exist.
- **`PsiLinkedPolySymbol`** (`community/platform/polySymbols/src/com/intellij/polySymbols/search/PsiLinkedPolySymbol.kt`)
  + registering `polySymbols.psiLinkedSymbol host="..."` — **a bridge, not a general shortcut.**
  Its own doc comment says exactly this: *"serves as a bridge between Symbol-based functionality
  and the old PSI-based functionality."* Implementing it makes usage-search/rename **bidirectional**
  with the raw `linkedElement`: a reference resolving to the `PsiLinkedPolySymbol` is treated as a
  usage of `linkedElement` and vice versa. Reach for this specifically when a language still runs a
  legacy `PsiReferenceContributor`/PSI-based resolve mechanism side by side with a PolySymbols
  integration and hasn't (or can't yet) migrate it away — i.e. exactly the "additive, not
  authoritative" situation described in the [main skill](../SKILL.md), and exactly why GDScript's
  `GdPsiPolySymbol` implements it (see [case-studies.md](case-studies.md#gdscript)). If you're
  building a symbol kind with no legacy PSI-resolve counterpart to bridge to, use
  `PolySymbolDeclaredInPsi` instead. **Do not** override `renameTarget`/`searchTarget` on a
  `PsiLinkedPolySymbol` — the framework handles those from the linked element. Pair it with a
  `PsiLinkedPolySymbolProvider` for the reverse element→symbol lookup (see below).
- **Hand-written `PolySymbolDeclarationProvider`**, with no `PolySymbolDeclaredInPsi`/
  `PsiLinkedPolySymbol` involved — for symbols with no real backing `PsiElement` at all, e.g. a
  synthetic SDK/engine symbol whose only PSI representation is a lazily-generated file created on
  demand for navigation (GDScript's `GdSdkPolySymbolDeclarationProvider` is the concrete example —
  it only fires inside that synthetic file, closing the "element → symbol" loop the platform can't
  discover on its own because the file didn't exist until `getNavigationTargets` created it).

## References — `PsiPolySymbolReferenceProvider`

```kotlin
interface PsiPolySymbolReferenceProvider<T : PsiExternalReferenceHost> {
  fun getReferencedSymbol(psiElement: T): PolySymbol? = null
  fun getReferencedSymbolNameOffset(psiElement: T): Int = 0
  fun getOffsetsToReferencedSymbols(psiElement: T): Map<Int, PolySymbol> = ...   // multiple refs in one element
  fun canReference(target: Symbol): Boolean = true   // optimize usage search: bail out before an expensive resolve
  fun shouldShowProblems(element: T): Boolean = true
}
```

Register per host-element type at EP `com.intellij.polySymbols.psiReferenceProvider`
(`hostElementClass`/`hostLanguage`/`implementationClass`). Implement `getReferencedSymbol` for the
single-reference case; `getReferencedSymbolNameOffset` when the reference sits at an offset inside
the element (e.g. inside a string literal); `getOffsetsToReferencedSymbols` for multiple
non-pattern-expressible references in one element. Internally these almost always call
`PolySymbolQueryExecutorFactory.create(psiElement).nameMatchQuery(kind, text).run().firstOrNull()`
— qualifier/dotted-path resolution is typically delegated to the *scope* layer (via `queryScope`
chaining above), not handled inside the reference provider itself.

`canReference(target)` is a usage-search optimization: return `false` early if this provider could
never possibly reference the given symbol, before the platform builds expensive cache keys or
attempts a resolve. Use with care — it's often hard to prove a symbol with a pattern *can't* match.

## References — own references (`PolySymbolOwnReferences`)

The alternative to `PsiPolySymbolReferenceProvider`, not a feature of it. Own references are "known
by the element, part of the language" (per `PsiExternalReferenceHost`'s own javadoc) — a language's
own canonical resolve, meant to fully replace a legacy `PsiReferenceContributor`-style mechanism.
External references (`PsiPolySymbolReferenceProvider`, above) are for a *different* plugin/language
layering a reference onto a host that doesn't know about it — e.g. a filename string literal
referenced by unrelated framework support.

```kotlin
interface PolySymbolOwnReferencesBuilder {
  fun reference(symbol: PolySymbol, offset: Int = 0, showProblems: Boolean = true)
  fun references(offsetsToSymbols: Map<Int, PolySymbol>, showProblems: Boolean = true)
}

fun polySymbolOwnReferences(element: PsiElement, configure: PolySymbolOwnReferencesBuilder.() -> Unit): PolySymbolOwnReferences
```

(`community/platform/polySymbols/backend/src/com/intellij/polySymbols/references/PolySymbolOwnReferences.kt`)
Implement `PsiElement.getOwnReferences(): Collection<PsiSymbolReference>` directly on your PSI class
and return `polySymbolOwnReferences(this) { ... }.references` — the builder reuses the same
name-segment expansion (`MatchProblem`/deprecation reporting included) that
`PsiPolySymbolReferenceProvider` uses internally, just without the EP/`getOffsetsToReferencedSymbols`
indirection.

**Do not implement both mechanisms for the same element.** Per
`PsiSymbolReferenceServiceImpl.getReferences()`, own references — once non-empty — are used *instead
of* external ones for resolve/search/rename, so a `PsiPolySymbolReferenceProvider` registered for a
host that also overrides `getOwnReferences()` is dead code, not a supplement.

**Preferred way to wire this up: `PolySymbolOwnReferencesHost`.** Implementing the raw builder above by
hand still leaves you to write the `getOwnReferences()` override and your own caching. Instead, have
your PSI class implement `PolySymbolOwnReferencesHost` and override its one abstract
method:
```kotlin
interface PolySymbolOwnReferencesHost : PsiElement {
  fun buildOwnReferences(builder: PolySymbolOwnReferencesBuilder)   // the only method you implement
  fun getPolySymbolOwnReferences(): PolySymbolOwnReferences = ...   // cached per element, default-implemented
  override fun getOwnReferences(): Collection<PsiSymbolReference> = getPolySymbolOwnReferences().references   // default-implemented
}
```
It deliberately does **not** extend `PsiExternalReferenceHost` — an own-references host may not want
external-reference support at all. `getPolySymbolOwnReferences()` is cached (`CachedValuesManager`,
invalidated on PSI modification) and shared by both `getOwnReferences()` and
`PolySymbolHighlightingAnnotator`'s symbol-kind highlighting (via `PolySymbolOwnReferencesHost`'s
`referencedSymbols`).

**This does not mean "full automatic highlighting with no extra work," though — two corrections to a
common misreading:** (1) `PolySymbolHighlightingAnnotator` picks up highlighting from **either**
own-references **or** EP-provided (`PsiExternalReferenceHost`) references — own-references is not
the exclusive source, it's one of two equally-supported paths. (2) Neither path produces *any* actual
color by itself. Confirmed by reading the annotator's attribute-lookup chain directly
(`PolySymbolHighlightingAnnotator.highlightSymbols()`): for every resolved symbol it tries, in order,
`PolySymbolHighlightingCustomizer.getSymbolTextAttributes(host, symbol, level)`, then
`symbol[TextAttributesKeyProperty]`, then `PolySymbolHighlightingCustomizer.getTextAttributesFor(kind)`
— all three return `null` unless something registers a customizer or sets a per-symbol property. With
nothing registered, a fully-working own/external reference resolves and is find-usages/rename-able,
but renders with zero color. Implementing `PolySymbolOwnReferencesHost` gets you the *mechanism*
(a hook the annotator will call into); a [`PolySymbolHighlightingCustomizer`](#polysymbolhighlightingcustomizer)
is what actually supplies the colors.

## PolySymbolHighlightingCustomizer

```kotlin
interface PolySymbolHighlightingCustomizer {
  fun getSymbolKindTextAttributes(kind: PolySymbolKind): TextAttributesKey? = null
  fun getDefaultHostClassTextAttributes(): Map<Class<out PsiElement>, TextAttributesKey> = emptyMap()
  fun getSymbolTextAttributes(host: PsiElement, symbol: PolySymbol, level: Int): TextAttributesKey? = null
}
```

Register at EP `com.intellij.polySymbols.highlightingCustomizer`
(`community/platform/polySymbols/backend/src/com/intellij/polySymbols/highlighting/PolySymbolHighlightingCustomizer.kt`).
All three methods default to "no opinion" (`null`/empty map) — a customizer only needs to implement
the one(s) it actually has an answer for. Multiple registered customizers compose **first-non-null-wins**,
in EP registration order, so `order="before ..."` matters when more than one customizer could answer
for the same kind/symbol — Angular registers `order="before js, before html"` so its own coloring for
Angular-specific symbol kinds takes precedence over the generic JS/HTML customizers that would
otherwise also match.

Two real shapes:
- **Kind-only**: CSS's customizer just maps `PolySymbolKind` → `TextAttributesKey` via
  `getSymbolKindTextAttributes` — no need to inspect the concrete symbol.
- **Symbol-aware**: override `getSymbolTextAttributes(host, symbol, level)` when the color depends on
  something the kind alone can't express — a modifier (static vs. instance), which class declared it
  (user code vs. SDK/builtin), or the host's own syntax (is this identifier followed by a call).
  JS's and GDScript's customizers (`GdPolySymbolHighlightingCustomizer`) are both this shape.

**The `symbol` passed to `getSymbolTextAttributes` is whatever the query/reference machinery
produced — usually the raw, unwrapped `nameMatchQuery` result, not necessarily the real underlying
symbol.** Confirmed by reading `PolySymbolHighlightingAnnotator.highlightSymbols()`: it passes
`nameSegment.symbols` entries straight through. For a language whose own-references go through
`fromNameMatchQuery`-style filtering, that's the same `PolySymbolMatchBase` wrapper whose own `.kind`
reports the *query's* composite kind, not the real symbol's — unwrap first
(`symbol.unwrapMatchedSymbols().firstOrNull()`) before reading `.kind`/`.modifiers`/anything else
kind-specific, or the customizer will silently return `null`/a wrong answer for almost every symbol
once it's wired in as the real coloring path (not a niche fallback). GDScript's
`GdPolySymbolHighlightingCustomizer` hit exactly this while migrating off a legacy annotator: a
mechanical transplant of the old (correct, because it ran *after* a classic-PSI-resolve success)
`when (symbol.kind)` coloring logic silently rendered almost everything as a generic fallback color
once it became the primary path, because the raw query result it now received had a useless `.kind`.

**No platform hook exists for unresolved-reference diagnostics or resolution-dependent checks** —
worth knowing explicitly rather than assuming a customizer/EP covers it. The only built-in mechanism
is mechanical: a `PsiPolySymbolReferenceProvider`/own-reference can return
`PsiPolySymbolReferenceProvider.unresolvedSymbol(kind, name)` instead of `null`, which the annotator
turns into a hardcoded `WARNING`-severity "unrecognized identifier" annotation — optionally remapped
to a real, user-configurable inspection via EP `com.intellij.polySymbols.inspectionToolMapping`
(`PolySymbolInspectionToolMappingEP`; Vue's `VueUnrecognizedSlotInspection` is the worked example).
There is **no** hook for: domain-specific tolerance logic that should suppress "unresolved" for
certain shapes (e.g. GDScript tolerates unresolved members on a `Variant`/`Node`-typed qualifier), or
a diagnostic that depends on resolution *succeeding* to a particular kind (GDScript's "SDK builtin
type with a constructor can't be assigned to a variable" check — that only makes sense once you know
what the reference resolved *to*). Both stay hand-written `Annotator`s that resolve via the
PolySymbols APIs (`element.getOwnReferences()`/a `resolveSymbolReference()`-style helper) and run
their own checks on the result — see GDScript's `GdRefIdAnnotator` for the worked example of an
annotator that does exactly this and nothing else (coloring is fully delegated to the customizer
above).

## Completion — `PolySymbolsCompletionProviderBase`

```kotlin
abstract class PolySymbolsCompletionProviderBase<T : PsiElement> : CompletionProvider<CompletionParameters>() {
  abstract fun getContext(position: PsiElement): T?
  abstract fun addCompletions(parameters, result, position, name, queryExecutor, context: T)
}
```

Register an ordinary `completion.contributor` whose provider extends this base class. It handles
offset/prefix bookkeeping (including injected-document offset translation) and calls your
`addCompletions`, which typically calls the companion helper
`processCompletionQueryResults(queryExecutor, result, kind, name, position, location, ...)` — which
itself runs `queryExecutor.codeCompletionQuery(kind, name, position).run()` and converts results
into `LookupElement`s, deduplicating and restarting completion on prefix changes for
`completeAfterInsert` items (multistaged completion, see [patterns.md](patterns.md)).

## Lighting up search / rename / navigation / documentation

`PolySymbol` already wires these through overridable properties/methods with sensible defaults —
you do **not** need to hand-roll `SearchTarget`/`RenameTarget` unless you need behavior the default
doesn't provide:

```kotlin
val searchTarget: PolySymbolSearchTarget? get() = null       // usually PolySymbolSearchTarget.create(...)
val renameTarget: PolySymbolRenameTarget? get() = null       // usually PolySymbolRenameTarget.create(...)
fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? = null   // usually PolySymbolDocumentationTarget.create(...)
override fun getNavigationTargets(project: Project): Collection<NavigationTarget> = emptyList()
```

**`PsiLinkedPolySymbolProvider`** (EP `com.intellij.polySymbols.psiLinkedSymbolProvider`,
`getSymbols(element: PsiElement): List<PsiLinkedPolySymbol>`) is the reverse lookup: element → the
symbol(s) it backs. You need this specifically when Find Usages/Rename is invoked **from the raw
PSI declaration** rather than from an already-resolved symbol reference — e.g. right-clicking an
`@Input() foo` field in a `.ts` file. Generic `ReferencesSearch` only has a bare `PsiElement`; without
a `PsiLinkedPolySymbolProvider` to recover the `PolySymbol`, the search falls back to a literal-name
text search and misses every usage written under an aliased/converted name (Angular's
`Angular2PsiLinkedPolySymbolProvider` is the worked example — see [case-studies.md](case-studies.md#angular)).

## PolySymbolQueryResultsCustomizer

```kotlin
interface PolySymbolQueryResultsCustomizer {
  fun apply(matches: List<PolySymbol>, strict: Boolean, qualifiedName: PolySymbolQualifiedName): List<PolySymbol>
  fun apply(item: PolySymbolCodeCompletionItem, kind: PolySymbolKind): PolySymbolCodeCompletionItem?
}
```

Register a `PolySymbolQueryResultsCustomizerFactory` at EP
`com.intellij.polySymbols.queryResultsCustomizerFactory`. This is the general "post-filter or remap
whatever the query returned" hook — e.g. Angular Forms uses it to wrap resolved
`formControlName`/`formGroupName`/`formArrayName` attribute symbols so their *value* is forced to
resolve as a `FormGroup`-key symbol reference instead of a free string (see
[case-studies.md](case-studies.md#angular)). One instance is created per query-executor build, via
`PolySymbolQueryResultsCustomizerFactory.create(location, context)` — it's context/location-scoped
and can change *which* symbols resolve or complete, not just how they render.

## PolySymbolCodeCompletionItemCustomizer

```kotlin
interface PolySymbolCodeCompletionItemCustomizer {
  fun customize(item: PolySymbolCodeCompletionItem, context: PolyContext, kind: PolySymbolKind, location: PsiElement): PolySymbolCodeCompletionItem?
}
```

Register at EP `com.intellij.polySymbols.codeCompletionItemCustomizer`
(`community/platform/polySymbols/backend/src/com/intellij/polySymbols/completion/PolySymbolCodeCompletionItemCustomizer.kt`).
**Global, unscoped hook** — every registered customizer runs, in registration order, for every
completion item from every language/framework
(`PolySymbolsCompletionProviderBase.processPolySymbolCodeCompletionItems`, right after a scope's
`getCodeCompletions()` — custom or the `getSymbols()`-derived platform default — has already
produced the item). Each implementation must self-gate, typically on
`item.symbol?.namespace`/`item.symbol is SomeBaseSymbolClass` (CSS's
`CssImplCodeCompletionItemCustomizer`, GDScript's `GdPolySymbolCodeCompletionItemCustomizer`) or on
`context.framework` (Angular's `Angular2CodeCompletionItemCustomizer`) — returning the item
unchanged, or `null` to drop it, when it doesn't apply.

**Not the same thing as [`PolySymbolQueryResultsCustomizer`](#polysymbolqueryresultscustomizer)
above**: that's the heavier, per-query-executor-build mechanism used for *semantic remapping* of
both resolve matches and completion items. `PolySymbolCodeCompletionItemCustomizer` is purely
presentational — it only ever adjusts a completion item's rendering
(`icon`/`priority`/`tailText`/`typeText`/etc.) after the symbol set is already final; it cannot
change *which* symbols are offered. Prefer this one whenever the goal is "make completion items for
symbols of kind X look right," not "change what X resolves to."
