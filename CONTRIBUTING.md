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
   <YouTrack ticket ID>( <YouTrack ticket ID>)* (<subsystem>: )? <subject>
        
   <detailed description>?
   ```

E.g.:

   ```
   IDEA-125730 Groovy: declare explicit type 

   Broken template should revert all its changes and move the caret back to the original position
   ```

Avoid including links to any discussions in commit messages (Slack, https://platform.jetbrains.com/, etc.). Instead, summarize the
discussion right in the commit message, or create a YouTrack ticket and summarize it there.

## Building the IDE

Please read the [README.md](README.md) in order to understand how to build or
run the IDE on your machine.

