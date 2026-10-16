# Audit Reporting Guidance

Use this reference when preparing the final report for a Compose UI audit, especially for no-finding audits, severity
assignment, evidence rules, or optional scoring.

## No-Finding / Low-Finding Audits

A good audit can say "this looks healthy." Positive patterns worth calling out:

- one presenter-owned screen state collected at the screen/root boundary
- local hover/focus/text-field/expanded state that is purely visual
- callbacks for persistence, file pickers, URI opening, and presenter-owned actions
- stable lazy-list keys and useful `contentType`
- `LaunchedEffect(Unit)` used only for one-shot focus/scroll setup with no changing captures

When no serious issues are found, return a short report with confidence/limits and low-priority polish only.

## Severity Guide

- **Critical:** likely broken behavior, data loss, app-state desync, runaway work, crash, or architectural boundary breach
  that can spread.
- **High:** repeated pattern that risks correctness/performance or blocks maintainability.
- **Medium:** localized smell with clear cleanup value.
- **Low:** style/API polish that should be fixed opportunistically.

## Evidence Rules

- Cite concrete file paths and line numbers where possible.
- Prefer 2-4 representative examples for systemic findings.
- Do not infer runtime problems from a grep hit alone.
- Do not double-count one root cause across categories.
- Separate Compose runtime/architecture findings from Jewel component-choice findings.
- Distinguish "found no evidence" from "proved absent".
- When recommending a fix, name the destination layer: presenter/coordinator, state holder, composition, or interop helper.

## Scoring If Explicitly Requested

| Category | Suggested weight |
|---|---:|
| Boundary and state ownership | 30% |
| Desktop lifecycle and flow collection | 20% |
| Effects and coroutine usage | 20% |
| Lazy lists and recomposition | 15% |
| Modifier/API shape and interop seams | 15% |

Label scoring as heuristic, not a measured runtime benchmark.

## Final Recommendation Style

- Say whether the surface is broadly healthy, needs targeted cleanup, or is risky.
- List the top three fixes first.
- State confidence and what you did not inspect.
- Point to the likely owner layer: presenter/coordinator, state holder, or composition.
