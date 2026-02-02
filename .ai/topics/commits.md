# Commits

Follow the commit message format:
- **Production changes**: `<YouTrack ticket ID> <subsystem>: <subject>` (e.g., `IDEA-125730 Groovy: declare explicit type`)
- **Non-production changes**: `<label> <subsystem>: <subject>` (e.g., `cleanup webstorm: remove old test code`)
  - Labels: `cleanup`, `refactor`, `docs`, `tests`, `format`, `style`, `typo`, `setup`, `misc`

Restrictions:
- Commits starting with `WIP`, `fixup!`, `squash!`, or `amend!` are not allowed
- Isolate commits: do not mix refactoring with business logic changes

Safe Push:
- Use Safe Push to push changes to protected branches
- CLI: `./safePush.cmd HEAD:master`
