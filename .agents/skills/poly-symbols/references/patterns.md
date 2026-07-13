# PolySymbols patterns (microsyntax)

Part of [poly-symbols](../SKILL.md). Patterns let a symbol match more than just its literal name —
they evaluate a "microsyntax" that a framework/library layers on top of base language syntax (HTML
attribute name syntax carrying a Vue directive, an event-binding name carrying Angular modifiers, a
message-bundle key embedded in a Java string).

## Why patterns exist

Because reference/completion providers work off *query results*, not scopes directly, a symbol with
a pattern gets an entire suite of IDE features for free once matched: code completion, reference
resolution, documentation, navigation, semantic highlighting, occurrence highlighting, find usages,
rename refactoring. Mechanically: for reference resolution the executor returns a composite symbol
(`PolySymbolMatch`) that splits into name segments, each with its own reference range; for
completion it evaluates every possible pattern-matching name to build completion items, and supports
**multistaged completion** — complete a name prefix first, then continue completing the rest of the
pattern.

## `PolySymbolWithPattern` and the match result

```kotlin
interface PolySymbolWithPattern : PolySymbol { val pattern: PolySymbolPattern }
```

Matching produces a `PolySymbolMatch` (a `CompositePolySymbol`) whose `nameSegments` is a list of
`PolySymbolNameSegment`, each describing a name range that may itself reference other symbols and
carry a `MatchProblem` (`UNKNOWN_SYMBOL`, `MISSING_REQUIRED_PART`, `DUPLICATED`). Programmatic
pattern creation is possible via the DSL below, but **Web Types JSON is the usual approach** for
static vocabularies — see [web-types.md](web-types.md) for the JSON shape, which mirrors this DSL
almost 1:1 (`template`/`items`/`delegate`/`repeat`/`required`).

## The pattern DSL

`community/platform/polySymbols/src/com/intellij/polySymbols/patterns/PolySymbolPatternDsl.kt`:

```kotlin
val pattern = polySymbolPattern {
  literal("v-")
  symbolReference()          // resolves against the enclosing symbols{} block or a custom resolver
  sequence { ... }            // ordered sub-patterns, all must match
  oneOf { branch { ... }; branch { ... } }   // alternatives
  group {                     // richest construct — see below
    symbols { from(SOME_KIND) }
    symbolReference()
  }
  optional { ... }
  repeating { unique(true); ... }
  completionPopup()           // completion-only marker; discards the typed prefix, "..." placeholder
  completionPopupWithPrefixKept()
}
```

Seven conceptual pattern kinds (per the docs, mirrored by the DSL builders above):

1. **String match** — exact, case-sensitive literal text (`literal`).
2. **Regex match** — `regex(pattern, caseSensitive)`.
3. **Symbol reference placeholder** — `symbolReference()`; resolves via the enclosing pattern's
   symbol resolver. Unmatched → `MatchProblem.UNKNOWN_SYMBOL`. The matched symbol can itself be a
   `PolySymbolMatch`, enabling nesting.
4. **Pattern sequence** — `sequence { }`; ordered, all sub-patterns required unless wrapped in
   `optional`. Missing a required part → `MatchProblem.MISSING_REQUIRED_PART`.
5. **Complex pattern / group** — `group { }` — the most capable construct: treats children as
   alternatives, can carry a `symbolsResolver`/`symbols { from(kind) { ... } }` block for nested
   `symbolReference()`s, `additionalScope(...)` to extend the resolve stack while matching its
   children, can be `optional { }` (absence isn't an error), `repeating { unique(...) }` (with
   `MatchProblem.DUPLICATED` on repeats), and can `priority(...)`/`apiStatus(...)`/
   `overrideMatchProperties { }` to override the resulting match's properties.
6. **Completion auto-popup** — `completionPopup()`/`completionPopupWithPrefixKept()` — marks a
   completion-only stopping point; selecting an item reopens completion for the next segment.
7. **Single symbol reference** (since 2023.2) — matches text against a symbol name while inserting
   a reference to another element.

Segment matching consumes text "up to the static prefixes of the following patterns" — a symbol
reference or regex must be terminated by static text or the end of the pattern; it can't be
open-ended in the middle of a sequence.

## Worked example: Vue's `v-on:click.once.alt`

The canonical example from the docs, and — in this repo — **declared entirely as Web Types JSON**,
not the Kotlin DSL (`contrib/vuejs/vuejs-backend/resources/web-types/vue@3.5.0.web-types.json`):

```json
{
  "name": "Vue directive",
  "virtual": true,
  "exclusive-contributions": ["/html/modifiers"],
  "pattern": {
    "items": "/html/vue-directives",
    "template": [
      "v-",
      "#item:Vue directive",
      { "delegate": "argument", "required": false,
        "template": [":", "#...", "#item:argument"] },
      { "items": "/html/modifiers", "required": false, "repeat": true,
        "template": [".", "#item:modifier"] }
    ]
  }
}
```

Segment breakdown for `v-on:click.once.alt`:

| Segment | Meaning |
|---|---|
| `v-` | literal prefix of the directive pattern |
| `on` | resolves to the Vue `on` directive symbol (`/html/vue-directives`) |
| `:` | literal separator |
| `click` | resolves as the directive's `argument` — for `on`, that's `/js/events` + `/html/vue-dynamic-argument`, i.e. a DOM event name |
| `.` `once` | first modifier — general modifier list (`stop`/`prevent`/`capture`/`self`/`once`/`passive`) |
| `.` `alt` | second modifier — **conditionally legal** because `click` also matches the "Mouse button"/"System event" modifier sub-patterns nested under `js.events` in the same file, which add `left`/`right`/`middle` and `ctrl`/`alt`/`shift`/`meta`/`exact` respectively |

The key lesson: "which modifiers are legal" is itself pattern-conditional on the *already-matched*
event name, not a fixed list for the whole directive — nested `items`/`template` blocks keyed by a
sub-pattern (`{"or": ["click", "dblclick", ...]}`) let a static Web Types file express that
conditionality without any code.

`@`/`:` shorthand forms (`v-on` → `@`, `v-bind` → `:`) reuse the exact same modifiers pattern via
`"delegate": "/html/vue-directives/on"` — declare the pattern skeleton once, reuse it for every
alias.

Angular's analogous microsyntax — extended key-event modifiers (`(keydown.control.shift.enter)`) —
is also Web Types-declared, under the `js/ng-custom-events` kind
(`angular-base@0.0.0.web-types.json`). A companion test fixture
(`contrib/Angular/angular-tests/testData/highlighting/customUserEvents/`) demonstrates that *any*
library can add its own event-modifier microsyntax purely via a `js/ng-custom-events` Web Types
contribution — no Kotlin required — and get full validation ("Unrecognized modifier"/"Unrecognized
event"/duplicate-modifier detection) and completion for free from the core pattern engine.

## Hand-written DSL example

Static Web Types can't express "match literally anything" — for wildcard/catch-all symbols, use the
DSL directly. Vue's `VueAnySymbol`/`VueAnySlot`
(`contrib/vuejs/vuejs-backend/src/org/jetbrains/vuejs/web/symbols/VueAnySymbol.kt`):

```kotlin
class VueAnySymbol(...) : PolySymbolWithPattern, VueSymbol {
  override val pattern: PolySymbolPattern
    get() = polySymbolPattern { regex(".*") }
}
```

## "Symbol standing in for another kind" — `ReferencingPolySymbol`

A different but related need: making one symbol kind resolve/complete as if it were another kind,
without duplicating data. Example from the docs: message-bundle property keys referenced as Java
string literals — a scope contributor supplies both the real symbols *and* a reference symbol that
maps one qualified kind onto another.

`community/platform/polySymbols/src/com/intellij/polySymbols/utils/ReferencingPolySymbol.kt`
builds exactly this: a `PolySymbolWithPattern` whose pattern is, conceptually,
`group { symbols { kinds.forEach { from(it) } }; symbolReference(name) }` — any symbol of the listed
other kinds matches, and a reference/usage gets registered under the given name.

Angular's `PolySymbolReferencingScope`
(`contrib/Angular/angular-backend/src/org/angular2/web/scopes/PolySymbolReferencingScope.kt`) is a
thin `PolySymbolScope` wrapper around exactly this factory call — it doesn't reimplement the
mechanism, it just packages it as an injectable scope for the registrar DSL. Angular Forms inlines
the same idea directly (no wrapper class) in `Angular2FormsSymbolQueryScopeContributor`'s
`attributeValueMappingScope`, to remap `HTML_ATTRIBUTE_VALUES` onto the referenced form-control-prop
kind for `formControlName="..."` (see [case-studies.md](case-studies.md#angular)).

## Related

- [Query model](query-model.md) — how a matched pattern's segments interact with the scope stack (`queryScope` chaining).
- [Web Types](web-types.md) — the declarative JSON equivalent of this DSL, and how it's loaded.
