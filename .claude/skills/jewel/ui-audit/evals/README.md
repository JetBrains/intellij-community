# Jewel UI Audit Eval Methodology

These evals measure whether the skill changes review behavior, not whether a model finds answers in local source trees
or in the eval rubric.

## Isolation rules

- Run each case in two arms: **baseline** (no skill) and **with skill** (same prompt and files, skill available).
- Keep the reviewed fixture and user prompt identical between arms.
- Name each fixture file in `evals/files/` as `fixture_<id>.<ext>` (for example, `fixture_1.kt` or
  `fixture_20.theme.json`) to match the case `id` in `evals.json`.
- Never put a defect name, anti-pattern, or expected finding in a fixture filename or prompt.
- Do not expose `expected_output` or `expectations` to the reviewing agent.
- Block local access to the skill's `evals/` directory, local answer keys, and unrelated source trees unless the run
  explicitly measures open-book agent behavior.
- Provide only the public skill files (`SKILL.md` and `references/`) to the skill arm. Do not include eval rubrics or
  private notes.
- Record whether network access is allowed, because network access can change the measured value of local skill
  knowledge.

## Running comparisons

For each model/case/arm, save:

- the exact prompt,
- fixture file names,
- model identifier,
- whether the skill was available,
- stdout/stderr,
- return code and timeout status,
- duration.

Use the same timeout and concurrency policy for both arms. Retry transient timeouts once and keep the retry artifact
separate from the canonical output.

## Grading

Grade after generation, using only the hidden `expectations` rubric and the review output. A pass requires all
expectations to be met. Record per-expectation evidence, missing items, and the grader model/tool used.

When interpreting results, inspect both pass/fail and partial scores. Regressions are as important as improvements: they
usually indicate over-broad guidance, buried trigger conditions, or a model assuming a fix that is not visible in the
snippet.

## Iteration loop

1. Identify cases where the skill arm underperforms or regresses.
2. Read the generated review, not just the grade summary.
3. Convert repeated misses into concise trigger cards or concrete gotchas.
4. Keep broad background in references and keep `SKILL.md` focused on the common path.
5. Re-run the affected subset before rerunning the whole battery.
