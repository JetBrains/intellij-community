# yt.py command reference

Full command surface of [`../scripts/yt.py`](../scripts/yt.py). Read this before composing any
invocation not already shown in [`../SKILL.md`](../SKILL.md).

All examples assume `YT` points at the CLI and a token source is set. Use the **absolute** path to
this skill's `scripts/yt.py` so it works from any working directory — replace `<SKILL-DIR>` with
this skill's own directory (or a harness-provided variable such as `$CLAUDE_SKILL_DIR` under Claude
Code):

```bash
YT="<SKILL-DIR>/scripts/yt.py"
export YOUTRACK_TOKEN_OP_PATH="op://VAULT/ITEM/FIELD"
```

## Contents

- [Global flags](#global-flags)
- [Environment variables](#environment-variables)
- [auth](#auth)
- [issue](#issue)
- [command](#command)
- [comment](#comment)
- [tag](#tag)
<!-- "link" below is the subcommand name, not vague link text -->
<!-- rumdl-disable-next-line MD059 -->
- [link](#link)
- [work](#work)
- [attach](#attach)
- [user, project, saved-queries](#user-project-saved-queries)
- [Field selection](#field-selection)

## Global flags

Available on every subcommand:

| Flag | Effect |
|---|---|
| `--token-op-path op://V/I/F` | Read the token from 1Password. Beats both env vars. |
| `--format json\|table\|ids` | Output format. Default `json`. |
| `--verbose` | Log method, URL, and request body to stderr. Never logs headers. |

These work either side of the subcommand: `yt.py --verbose issue get X` and
`yt.py issue get X --verbose` are equivalent.

`--dry-run` exists on **every** mutating command, destructive ones included — use it to see the
exact endpoint and payload without sending anything. `--yes` is additionally required to actually
perform a destructive action (`comment delete`, `tag remove`, `attach delete`).

Only `GET` requests are retried on 429/5xx. A `POST` or `DELETE` that fails is reported rather
than replayed, since YouTrack may already have applied it.

## Environment variables

The CLI reads exactly two, both for authentication. There are no others — everything else is a
flag.

| Variable | Value | Notes |
|---|---|---|
| `YOUTRACK_TOKEN` | The permanent token itself | Works without the 1Password CLI |
| `YOUTRACK_TOKEN_OP_PATH` | An `op://VAULT/ITEM/FIELD` secret reference | Needs the 1Password CLI (`op`) |

Precedence, most explicit first — the first one set wins:

1. `--token-op-path` (flag)
2. `$YOUTRACK_TOKEN`
3. `$YOUTRACK_TOKEN_OP_PATH`

A blank or whitespace-only `$YOUTRACK_TOKEN` is ignored rather than treated as set, so it falls
through to the next source. If none resolve, the CLI exits 3 and names all three options.

See [Provide the token](../SKILL.md#provide-the-token) for choosing between them, and note that
`op://` paths are user-specific: never hardcode a real one into a file or commit.

## auth

```bash
python3 $YT auth check
# {"login": "sebp", "url": "https://youtrack.jetbrains.com", "authenticated": true}
python3 $YT auth check --format table
```

Reports the resolved login only — never the token. Honours `--format` like every other command.
Exit 3 if no token resolves or the token is rejected.

## issue

```bash
# Fetch
python3 $YT issue get JEWEL-1367
python3 $YT issue get JEWEL-1367 --fields idReadable,summary,description --format table

# Search
python3 $YT issue search 'project: JEWEL #Unresolved' --top 20
python3 $YT issue search 'project: JEWEL assignee: me' --format ids
python3 $YT issue search 'project: JEWEL' --top 100 --skip 100      # paginate

# Create — always preview with --dry-run and get user confirmation first
python3 $YT issue create --project JEWEL --summary 'Title' \
  --description-file /tmp/body.md --field Type=Task --field State=Open --dry-run
python3 $YT issue create --project JEWEL --summary 'Title' \
  --description-file /tmp/body.md --field Type=Task --field State=Open

# Update
python3 $YT issue update JEWEL-1367 --summary 'New title'
python3 $YT issue update JEWEL-1367 --description-file /tmp/body.md

# Custom fields
python3 $YT issue field list JEWEL-1367 --format table
python3 $YT issue field set JEWEL-1367 State 'In Progress'
python3 $YT issue field set JEWEL-1367 Assignee sebp
```

`--field Name=Value` is repeatable. The `$type` is inferred from the field name — `State` →
`StateIssueCustomField`, `Assignee` → `SingleUserIssueCustomField` (keyed on `login`), everything
else → `SingleEnumIssueCustomField` (keyed on `name`). Override with `--type` on `field set`; the
key inside `value` follows the type you give, so `--type SingleUserIssueCustomField` sends
`{"login": …}` rather than `{"name": …}`.

`--raw-payload FILE` on `issue create` sends a JSON file verbatim, bypassing all of the above.
Use it only when the flags cannot express what you need.

## command

Applies YouTrack command syntax — the same language as the UI command bar.

```bash
# Validate without applying (routes to /api/commands/assist)
python3 $YT command apply 'State In Review' --issue JEWEL-1367 --dry-run

# Apply
python3 $YT command apply 'State In Review' --issue JEWEL-1367

# One command, several issues — impossible with the old per-issue endpoint
python3 $YT command apply 'add Board Sprint 3' --issue JEWEL-1367 --issue JEWEL-525
```

Dry-run output has a `commands` array; check `error: false` and read `description` to confirm
YouTrack understood the command before applying it for real.

## comment

```bash
python3 $YT comment list JEWEL-1367 --top 50 --format table
python3 $YT comment add JEWEL-1367 --text 'Short note.'
python3 $YT comment add JEWEL-1367 --text-file /tmp/comment.md      # preferred for long text
python3 $YT comment update JEWEL-1367 <COMMENT-ID> --text-file /tmp/comment.md
python3 $YT comment delete JEWEL-1367 <COMMENT-ID> --yes
```

## tag

```bash
python3 $YT tag list --top 100                 # every tag on the instance
python3 $YT tag list --issue JEWEL-1367        # tags on one issue
python3 $YT tag add JEWEL-1367 'needs-triage'  # name or internal id
python3 $YT tag remove JEWEL-1367 <TAG-ID> --yes
```

`tag add` accepts a tag name and resolves it to an internal id for you.

## link

```bash
python3 $YT link list JEWEL-1367                 # only populated link types
python3 $YT link types --top 50                  # what link types exist
python3 $YT link add JEWEL-1367 --type 'relates to' --target JEWEL-525
python3 $YT link add JEWEL-1367 --type 'depends on' --target IJPL-250885 --dry-run
```

`link add` is built on `command apply`. `--type` is the phrase YouTrack uses:
`relates to`, `depends on`, `is required for`, `duplicates`, `is duplicated by`,
`parent for`, `subtask of`. Run `link types` if unsure.

`link list` filters out the empty link types the API returns for every issue.

## work

```bash
python3 $YT work list JEWEL-1367 --format table
python3 $YT work log JEWEL-1367 --duration '2h 30m' --text 'Reviewed PR feedback.'
python3 $YT work log JEWEL-1367 --duration '45m' --date 2026-07-20
```

`--duration` takes YouTrack's presentation format (`2h`, `90m`, `1d 4h`). `--date` is
`YYYY-MM-DD`, interpreted as that calendar day in your local timezone, and defaults to today.

## attach

```bash
python3 $YT attach list JEWEL-525 --format table
python3 $YT attach upload JEWEL-1367 screenshot.png diagram.svg
python3 $YT attach download JEWEL-525 --attachment <ATTACHMENT-ID> --out /tmp/shot.png
python3 $YT attach download JEWEL-525 --all --out /tmp/attachments/
python3 $YT attach delete JEWEL-1367 <ATTACHMENT-ID> --yes
```

With `--all`, `--out` is a directory and files keep their original names. Attachment URLs from
the API are relative and carry a `sign` capability token; `attach download` resolves and fetches
them for you, so you never need to handle those URLs directly — and should not print them.

## user, project, saved-queries

```bash
python3 $YT user me
python3 $YT user search jane --top 10 --format table

python3 $YT project get <PROJECT>                    # -> {"shortName":"...","id":"<INTERNAL-ID>",...}
python3 $YT project fields <PROJECT> --format table   # required flags and allowed types

python3 $YT saved-queries --top 50
```

`project get` queries `/api/admin/projects`. Never derive a project id by fetching
`<PROJECT>-1` — issue number 1 is not guaranteed to exist (it has been deleted in JEWEL, where
`JEWEL-1` returns 404), so the trick is unreliable.

## Field selection

YouTrack returns only the fields you ask for. Each command sends a sensible default; override
with `--fields` on `issue get` and `issue search`:

```bash
python3 $YT issue get JEWEL-1367 --fields 'idReadable,summary,customFields(name,value(name))'
```

Nesting uses parentheses. Useful pieces: `idReadable`, `summary`, `description`, `created`,
`updated`, `project(shortName)`, `reporter(login)`, `customFields(name,value(name,login))`,
`comments(id,text,author(login))`, `tags(id,name)`, `attachments(id,name,size)`.
