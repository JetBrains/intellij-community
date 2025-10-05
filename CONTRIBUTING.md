# Contributing to JetBrains IDEs Open Source

Thanks for your interest in contributing to the JetBrains IDEs open-source repository!

## What kinds of contributions we welcome

- **Bug fixes (preferred).** Most contributions we accept are fixes for reproducible issues. Tests should supply those fixes if possible.
- **Features (by prior agreement only).** If you want to add a feature, please discuss it with us first. We accept features only when they
  align with our roadmap for the relevant subsystem.
- **“Patch welcome” issues.** We maintain a list of feature requests and improvements that we warmly welcome from the community:
  https://youtrack.jetbrains.com/issues?q=%23patch_welcome

Before you start, make sure you:

- Have an existing YouTrack ticket for the issue you plan to work on (or create one if needed): https://youtrack.jetbrains.com/
- Have read:
  - [IntelliJ Coding Guidelines](https://plugins.jetbrains.com/docs/intellij/intellij-coding-guidelines.html)
  - [Contribute Code](https://www.jetbrains.com/opensource/idea/)
  - Follow the recommended [commit message format](#commit-message-format)

## Commit message format

We strongly recommend following this commit message format:

   ```
      [<subsystem>] <YouTrack ticket ID/category keyword for non-production changes (see allowed list below)> short description

      detailed description
   ```

E.g.:

   ```
      [groovy] IDEA-125730 Declare explicit type 

      Broken template should revert all its changes and move the caret back to the original position
   ```

Avoid including links to any discussions in commit messages (Slack, https://platform.jetbrains.com/, etc.). Instead, summarize the
discussion right in the commit message,
or create a YouTrack issue and summarize it there.

1. If the commit changes a product's distribution, always include a link to the corresponding YouTrack ticket.
2. If the commit does not change a product's distribution (e.g., tests, documentation, formatting, etc.), it should contain category keyword
   instead of ticket id.
   Possible keywords:
   - `tests`, `test`
   - `cleanup`, `typo`
   - `refactor`, `refactoring` for small refactorings (e.g., renaming a local variable, extracting private method, changes which don't
   affect public API signatures)
   - `docs`, `doc`
   - `format`, `style`

   Example: ```[ui] typo “accross” → “across”```

## Building the IDE

Please read the [README.md](README.md) in order to understand how to build or
run the IDE on your machine.

