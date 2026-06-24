# Jewel UI Audit Eval Methodology

These evals are intended to measure whether the skill changes review behavior, not whether a model can find answers in
local source trees or in the eval rubric.

## Isolation rules

- Run each case in two arms: **baseline** (no skill) and **with skill** (same prompt and files, skill available).
- Keep the reviewed fixture and user prompt identical between arms.
- Do not expose `expected_output` or `expectations` to the reviewing agent.
- Prevent local access to the skill's `evals/` directory, local answer keys, and unrelated checked-out source trees
  unless the run is explicitly measuring open-book agent behavior.
- If the skill arm needs reference docs, provide only the public skill files intended for normal skill use; do not
  include eval rubrics or private notes.
- Network access may be allowed when the goal is to measure realistic agent behavior, but record that choice because it
  can reduce the measured value of local skill knowledge.

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
