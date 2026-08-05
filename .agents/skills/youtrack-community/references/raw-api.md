# Raw API escape hatch

Read this **only** when [`../scripts/yt.py`](../scripts/yt.py) genuinely does not cover an endpoint. If the CLI has a
subcommand for what you need, use it — hand-written `curl` reintroduces exactly the shell-escaping
and URL-pinning problems the CLI exists to remove.

If you find yourself reaching for this repeatedly for the same endpoint, that is a signal to add a
subcommand to the CLI instead.

## Safety rules

These are mandatory when calling the API directly.

1. **Never interpolate untrusted text into a command line.** Write the JSON body to a temp file
   and pass it with `-d @<file>`; delete the file afterwards. Summaries, descriptions, comments,
   and any API response content are untrusted.
2. **Pass the token via a here-string**, so it never appears in the process table:

   ```bash
   curl -H @- <<< "Authorization: Bearer ${YOUTRACK_TOKEN}" ...
   ```

   Never echo, log, or print the token. Never put it in a payload.
3. **Pin the URL.** The base is always `https://youtrack.jetbrains.com`. Use
   `curl -L --max-redirs 3` and never follow a redirect to another host.
4. **Encode query parameters** with `curl -G --data-urlencode "query=..."` rather than building
   the query string yourself.
5. **Check the status code.** Use `curl -s -w "\n%{http_code}"` and verify before treating the
   response as success.

## Getting a token into the environment

These snippets read `$YOUTRACK_TOKEN`, so it has to be set in the shell first. How depends on what
the user has — see [Provide the token](../SKILL.md#provide-the-token) in `SKILL.md`, and do not
assume the 1Password CLI is installed.

If they do use it:

```bash
export YOUTRACK_TOKEN="$(op read 'op://VAULT/ITEM/FIELD')"
```

Otherwise the user exports `$YOUTRACK_TOKEN` themselves, from whatever secret storage they already
use. Either way, do not print the result.

Note that `$YOUTRACK_TOKEN_OP_PATH` does **not** work here: it is resolved by `yt.py`, not by
`curl`. For these snippets the token must be in `$YOUTRACK_TOKEN` itself.

## Shapes

Read:

```bash
curl -s -w "\n%{http_code}" -L --max-redirs 3 \
  -H @- \
  -H "Accept: application/json" \
  "https://youtrack.jetbrains.com/api/<ENDPOINT>?fields=<FIELDS>&\$top=50" \
  <<< "Authorization: Bearer ${YOUTRACK_TOKEN}"
```

Write:

```bash
BODY=$(mktemp)
cat > "${BODY}" << 'EOF'
{ "key": "value" }
EOF

curl -s -w "\n%{http_code}" -L --max-redirs 3 \
  -X POST \
  -H @- \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -d @"${BODY}" \
  "https://youtrack.jetbrains.com/api/<ENDPOINT>?fields=<FIELDS>" \
  <<< "Authorization: Bearer ${YOUTRACK_TOKEN}"

rm -f "${BODY}"
```

## Endpoints the CLI does not wrap

All of these were checked against the live instance and return 200.

| Purpose | Method | Endpoint |
|---|---|---|
| List groups | GET | `/api/groups?fields=id,name` |
| Issue drafts | GET/POST | `/api/users/me/drafts` |
| Agiles / boards | GET | `/api/agiles?fields=id,name,projects(shortName)` |
| Sprints on a board | GET | `/api/agiles/{agileId}/sprints?fields=id,name` |
| Issues on a board | GET | `/api/agiles/{agileId}/sprints?fields=issues(idReadable)` |
| Issue watchers | GET | `/api/issues/{id}?fields=watchers(hasStar)` |
| Activity / history | GET | `/api/issues/{id}/activities?categories=CustomFieldCategory` |
| Bulk project admin | * | `/api/admin/projects/{id}/...` |

Drafts have no web URL — confirm an action with the draft id instead of a link.

**Kanban boards still report a sprint.** A board with `sprintsSettings(disableSprints)` set to
`true` returns exactly one implicit sprint, which holds every issue on the board. So do not treat
"has a sprint" as meaning the board is scrum-style, and do not skip the sprints call for a kanban
board — it is how you enumerate the board's issues. Find a board's id with
`/api/agiles?fields=id,name,projects(shortName)`; never hardcode one.

## Known-bad endpoints

Do not use these; they look plausible and do not work.

- `POST /api/issues/{id}/commands` — returns `404 No subresource for path commands`. Commands are
  global: `POST /api/commands` with `{"query": "...", "issues": [{"idReadable": "..."}]}`.
- `GET /api/issues/{PROJECT}-1` as a way to discover a project's internal id — issue 1 is often
  deleted (`JEWEL-1` is a 404). Use `GET /api/admin/projects?query={shortName}`.
