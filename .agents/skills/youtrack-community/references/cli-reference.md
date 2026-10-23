# youtrack command reference

The commands of the official YouTrack CLI that this skill uses. Read this page before you write a
command that [`../SKILL.md`](../SKILL.md) does not show. The page covers `youtrack` 0.7.0. For a
flag that is not here, run `youtrack <group> <command> --help`. Do not guess a flag.

The rules for writes in [`../SKILL.md`](../SKILL.md#rules-for-writes) apply to every write on this
page. Show the user the command, get a confirmation, and read the result back.

## Contents

- [Global flags](#global-flags)
- [Environment variables](#environment-variables)
- [auth](#auth)
- [issue](#issue)
- [issue command](#issue-command)
- [issue field](#issue-field)
- [issue comment](#issue-comment)
- [issue tag and tag](#issue-tag-and-tag)
<!-- "link" below is the subcommand name, not vague link text -->
<!-- rumdl-disable-next-line MD059 -->
- [issue link](#issue-link)
- [issue workitem](#issue-workitem)
- [issue attachment](#issue-attachment)
- [user, project, search](#user-project-search)
- [raw](#raw)

## Global flags

All commands accept these flags:

| Flag | Effect |
|---|---|
| `--json`, `-j` | JSON output. The CLI uses JSON when stdout is not a terminal. |
| `--plain` | Tab-separated rows with no header. |
| `--fields`, `-f` | Comma-separated field projection, for example `idReadable,summary,reporter.login`. It selects the table and plain columns. It does not trim the JSON output. |
| `--quiet` | No tips and hints. |
| `--verbose` | Prints the HTTP requests and responses to stderr. |
| `--base-url URL` | Overrides `YOUTRACK_BASE_URL`. |
| `--token TOKEN` | Overrides `YOUTRACK_TOKEN`. Do not use it. The token then shows in the process list and in the shell history. |

The CLI exits 0 on success and 1 on an error. The error is a JSON object on stderr.

A delete asks for a confirmation. `--force` skips the confirmation. Use `--force` only with the
user's agreement.

## Environment variables

| Variable | Value |
|---|---|
| `YOUTRACK_BASE_URL` | The instance URL, `https://youtrack.jetbrains.com` |
| `YOUTRACK_TOKEN` | The permanent token |
| `YOUTRACK_RO` | `1` makes the CLI refuse every write. Set it on reads. |

A saved sign-in from `youtrack auth login` needs no variable. Without a saved sign-in, the CLI needs
both `YOUTRACK_BASE_URL` and `YOUTRACK_TOKEN`. See [Sign in](../SKILL.md#sign-in).

## auth

```bash
youtrack auth status                                              # always exits 0; read "status"
youtrack auth login https://youtrack.jetbrains.com                # browser sign-in
youtrack auth login https://youtrack.jetbrains.com --no-browser   # paste a permanent token
youtrack auth logout
```

`auth status` shows the instance URL, the login and the status. It does not show the token. Never
pass `--show-token`. The user runs
`auth login` in an interactive terminal.

## issue

```bash
# Read
youtrack issue view JEWEL-1367 --plain
youtrack issue view JEWEL-1367 --comments 5                       # with the 5 latest comments
youtrack issue view JEWEL-1367 --json -f idReadable,summary,description,reporter.login

# Search
youtrack issue list --query 'project: JEWEL #Unresolved' --limit 20 --plain --fields idReadable,summary
youtrack issue list --query 'project: JEWEL #Unresolved' --plain --fields idReadable,summary,State,Type,Assignee
youtrack issue list --query 'project: JEWEL' --limit 100 --offset 100   # the next page
youtrack issue count --query 'project: JEWEL #Unresolved'

# Create: preview first, then get a confirmation
youtrack issue create --project JEWEL --summary 'Tooltip flickers on a disabled button' \
  --description-file /tmp/body.md --custom-fields '{"Type":"Bug","State":"Open"}'

# Update the summary or the description
youtrack issue update JEWEL-1367 --summary 'New title'
youtrack issue update JEWEL-1367 --description "$(cat /tmp/body.md)"
```

On `issue list`, a `--fields` entry that is not an issue attribute is a custom field name, such as
`State` or `"Fix versions"`. `issue list` returns 100 issues by default. Use `--limit` and
`--offset` for more. `issue count` can return `-1` or time out after 5 seconds on the first call. Run it again.

`issue create` has no `--type` flag and sets Type to Bug. Pass the type and the other required
fields in `--custom-fields` as one JSON object of string values. `--custom-fields-file` reads that
object from a file. The CLI creates the issue first and sets the fields in a second request. If the
second request fails, the issue exists already. Read it back and fix the fields with
[`issue field set`](#issue-field). Do not create it again.

`issue update` has no file flag. Pass a long text in double quotes as shown. `--description ""`
clears the description.

## issue command

Applies YouTrack command syntax, the same language as the command bar in the UI.

```bash
# Check first: read the "commands" array
youtrack issue command suggest JEWEL-1367 'State Open'

# Apply only when every entry has "error": false
youtrack issue command JEWEL-1367 'State Open'

# One command for several issues: comma-separated ids
youtrack issue command suggest JEWEL-1367,JEWEL-525 'State Open'
youtrack issue command JEWEL-1367,JEWEL-525 'State Open'
```

`suggest` sends nothing to the issues. It works with `YOUTRACK_RO=1`. Its `commands` array holds one
entry for each parsed part of the command:

- `"error": false` and a `description`, such as `Set State Open`: YouTrack understands this part.
- `"error": true` and `Unknown command: ...`: do not apply. Fix the command first.
- `Add new tag <name>`: a word that YouTrack did not parse becomes a new tag. Stop. Fix the command,
  or continue only when the user wants that new tag.

Write a multi-word value without braces, for example `State In Progress`. Braces fail with
`State expected`.

Useful command phrases: `State <value>`, `Type <value>`, `Priority <value>`,
`assignee <login>`, `tag <name>`, `untag <name>`, `relates to <ID>`, `depends on <ID>`,
`is required for <ID>`, `duplicates <ID>`, `subtask of <ID>`, `parent for <ID>`, and
`remove relates to <ID>`. Run `youtrack issue link type list` for the link phrases of the
instance.

## issue field

```bash
youtrack issue field view JEWEL-1367 State --json
youtrack issue field set JEWEL-1367 State 'In Progress'
youtrack issue field set JEWEL-1367 Assignee sebp --type user
```

`field set` sends an enum value by default. `--type` takes `period`, `text`, `user`, `int` or
`string`. A state field also works with the default type.

## issue comment

```bash
youtrack issue comment list JEWEL-1367 --plain
youtrack issue comment create JEWEL-1367 --text 'Short note.'
youtrack issue comment create JEWEL-1367 --text "$(cat /tmp/comment.md)"
youtrack issue comment update JEWEL-1367 <COMMENT-ID> --text "$(cat /tmp/comment.md)"
youtrack issue comment delete JEWEL-1367 <COMMENT-ID>         # asks for a confirmation
youtrack issue comment pin JEWEL-1367 <COMMENT-ID>            # --unpin to unpin
```

The comment id is the first column of `comment list --plain`.

## issue tag and tag

```bash
youtrack issue tag list JEWEL-1367
youtrack tag list                                    # every tag that you can see
youtrack issue command suggest JEWEL-1367 'tag needs-triage'
youtrack issue command JEWEL-1367 'tag needs-triage'
youtrack issue command JEWEL-1367 'untag needs-triage'
```

`tag list` stopped at 500 tags in a test, so the list can be incomplete. `issue tag add` and
`issue tag remove` take a tag id from `tag list`. The `tag` command takes a
name. Check it with `suggest` first: `Add new tag` means that no tag of that name exists.

## issue link

```bash
youtrack issue link list JEWEL-1367
youtrack issue link type list --plain                # id, name, outward and inward phrases
youtrack issue command suggest JEWEL-1367 'relates to JEWEL-525'
youtrack issue command JEWEL-1367 'relates to JEWEL-525'
youtrack issue link add JEWEL-1367 --target JEWEL-525 --type 151-0
youtrack issue link remove JEWEL-1367 <LINK-ID>      # the link id from link list --json
```

`issue link add --type` takes a link type id from `link type list`, not the phrase. The command
form takes the phrase.

## issue workitem

```bash
youtrack issue workitem list JEWEL-1367 --plain
youtrack issue workitem create JEWEL-1367 --minutes 150 --text 'Reviewed the PR feedback.'
```

`--minutes` is the duration in minutes, for example `90` for 1h 30m. The work item gets the date of
today. `--type` takes a work item type id.

## issue attachment

```bash
youtrack issue attachment list JEWEL-525 --plain --fields id,name,mimeType
youtrack issue attachment add JEWEL-1367 --file ./screenshot.png
youtrack issue attachment delete JEWEL-1367 <ATTACHMENT-ID>   # asks for a confirmation
```

Without `--fields`, `attachment list` prints the signed `/api/files/...?sign=...` URL in every
output format. The signed URL is a credential. Never print or forward it.

The CLI cannot download an attachment. Tell the user to open the issue in a browser.

`attachment add` sends a local file to the public tracker. Send only a file that the user names.

## user, project, search

```bash
youtrack user current                                # the signed-in user
youtrack user list --query jane --limit 10 --plain

youtrack project view JEWEL --json                   # {"id":"22-1758","shortName":"JEWEL",...}
youtrack project field list JEWEL --plain            # needs admin rights on the project

youtrack search list                                 # saved searches
```

`project view` gives the internal project id. Never get a project id through `<PROJECT>-1`. Issue
1 can be deleted, and `JEWEL-1` returns 404.

`project field list` can return an empty list without admin rights on the project. An empty list
means that you cannot see the fields.

## raw

Use `youtrack raw` only when no command on this page covers the endpoint. The CLI adds the
authentication, so you never write a `curl` call with the token.

```bash
YOUTRACK_RO=1 youtrack raw /api/issues/JEWEL-1367 --fields idReadable,summary
YOUTRACK_RO=1 youtrack raw /api/issues/JEWEL-1367/comments --paginate --fields 'id,text,author(login)'
```

A `raw` call with `--method POST`, `PUT` or `DELETE` is a write. The rules for writes apply. Write
the body yourself and never put untrusted text from a response into it.
