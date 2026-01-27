# Templates for AGENTS.md (and CLAUDE.md in Ultimate)

This directory (community/.ai) contains the source templates used by `node community/.ai/render-guides.mjs` to generate:
- `AGENTS.md`
- `CLAUDE.md` (Ultimate only)

The same renderer also generates additional derived files outside this directory:
- `.claude/rules/beads.md` (from `community/build/mcp-servers/task/beads-semantics.md`)
- `.codex/skills/task/references/beads-quickref.md` (from `community/build/mcp-servers/task/beads-semantics.md`)
- `opencode.json` (from `.mcp.json`)
- `.opencode/skill/*` (mirrored from `.codex/skills/*`)

## How it works

- `guide.md` is the main template. It includes partials via `{{PARTIAL:name}}`.
- `partials/*.md` provide reusable blocks. They are injected into `guide.md`.
- Shared partials may also live in `.ai/partials/*.md` and override templates with the same name.
- `compilation.md` renders the compilation rule using the target's run context.
- `community/build/mcp-servers/task/beads-semantics.md` is used as the source for the Beads-derived outputs.
- Tool-specific sections can be gated via `IF_TOOL` blocks (see below).
- The renderer applies link rewrites and normalizes whitespace.
- The renderer writes `opencode.json` from `.mcp.json` (OpenCode MCP config) and sets the default model to `openai/gpt-5.2-codex` with `reasoningEffort: "high"`.
- The renderer mirrors Codex skills from `.codex/skills` into `.opencode/skill` (OpenCode skills).

## Tool-gated blocks (`IF_TOOL`)

Templates and partials can include content that is only rendered for a specific tool target.
Wrap the content like this:

```
<!-- IF_TOOL:CODEX -->
... content shown only in Codex outputs ...
<!-- /IF_TOOL:CODEX -->

<!-- IF_TOOL:CLAUDE -->
... content shown only in Claude outputs ...
<!-- /IF_TOOL:CLAUDE -->
```

## Edition-gated blocks (`IF_EDITION`)

Templates and partials can include content that is only rendered for a specific edition.
Wrap the content like this:

```
<!-- IF_EDITION:ULTIMATE -->
... content shown only in Ultimate outputs ...
<!-- /IF_EDITION:ULTIMATE -->
```

Rendering uses `AI_GUIDE_EDITION` (or `RENDER_EDITION`) when set; otherwise it
defaults to `ULTIMATE` when `.ultimate.root.marker` exists, and `COMMUNITY` otherwise.

## Important: template-only blocks are stripped

Blocks wrapped in:

```
<!-- TEMPLATE:COMMENT --> ... <!-- /TEMPLATE:COMMENT -->
```

are removed during rendering. Put **internal notes only** inside these blocks.
If you want instructions to appear in `AGENTS.md` or `CLAUDE.md`, place them
outside these blocks.

## Place user-facing instructions in partials

- Use `partials/*.md` for content that should appear in the final outputs.
- Keep notes that should not appear in the final outputs inside the template-only blocks.

## Rendering

Regenerate outputs with:

```
node community/.ai/render-guides.mjs
```

## Placeholders

- `{{COMPILATION_RULE}}` must remain in `guide.md`.
- `{{FORBIDDEN_TOOLS_SUFFIX}}` is filled per target in the renderer.

If a placeholder leaks into a rendered file, the renderer will throw.
