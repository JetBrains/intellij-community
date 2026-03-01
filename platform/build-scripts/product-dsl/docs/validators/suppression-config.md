# Suppression Config Validation

Entry point: `SuppressionConfigValidator` (`NodeIds.SUPPRESSION_CONFIG_VALIDATION`).

## Overview

Validates that all keys in `suppressions.json` reference existing content modules or plugins. This prevents stale or misspelled entries from silently disabling intended suppressions.

## Inputs

- `suppressionConfig` (parsed `suppressions.json`).
- `updateSuppressions` flag.
- Graph: content module and plugin nodes.

## Rules

- If `updateSuppressions` is enabled, skip validation (invalid keys will be removed during generation).
- For content module keys, verify a content module node exists in the graph.
- For plugin keys, verify a plugin node exists in the graph.
- Report invalid keys as a single error.

## Suppression and allowlists

- None. This validator is about the suppression config itself.

## Output

- `InvalidSuppressionConfigKeyError` with invalid module and plugin keys.

## Auto-fix

- None.

## Non-goals

- Validating the correctness of suppression values beyond key existence.

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [dependency_generation.md](../dependency_generation.md)
