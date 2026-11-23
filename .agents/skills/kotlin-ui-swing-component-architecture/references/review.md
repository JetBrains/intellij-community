# Architecture reviews

## Classify architecture findings

Treat these as severity defaults and adjust for actual impact.

- **Critical:** Blocking the EDT; accessing Swing from background threads; network, filesystem, database, or domain mutations in the View; domain layers depending on Swing.
- **Major:** Exposed internal widgets or unnecessary Swing types in public APIs; avoidable Swing inheritance; unnecessary custom widgets or painting; a reusable custom widget without a proper `ComponentUI`; application state inferred from widgets; Controller-built layouts; uncancelled or stale async results; missing lifecycle cleanup; a god panel mixing UI, services, and workflows.
- **Minor:** Large anonymous listeners; unnecessary widget getters or interfaces; mixed construction and behavior that remains understandable; small duplication in imperative rendering.

A panel over roughly 500 lines is a review signal, not an automatic defect. Judge responsibility count, coupling, asynchronous behavior, and change risk.

## Produce architecture reviews

When reviewing code, use these exact headings. Cite concrete symbols or file locations, distinguish observed facts from recommendations, and write `None` when a section has no findings.

### Architecture Assessment

State the feature boundary, current complexity, selected architecture level, and whether a refactor is justified.

### Current Responsibilities

Map rendering, intents, state, application logic, asynchronous work, navigation, and lifecycle to their current owners.

### Architecture Problems

List Critical, Major, and Minor findings with evidence and impact.

### Critical Issues

Call out EDT violations, blocking, domain/Swing dependency inversion, unsafe late results, or other correctness risks that require priority.

### Suggested Component Structure

Show the smallest useful target structure and its public boundary.

### Minimal Refactoring Plan

Give ordered, behavior-preserving steps. Separate necessary work from optional cleanup.

### Example Code

Provide a focused example or patch shape for the highest-value change; do not rewrite the whole feature unless requested.

### Trade-offs

Explain added types or state, rejected heavier patterns, testing impact, migration risk, and any manual verification still needed.
