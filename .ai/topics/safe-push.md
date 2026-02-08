# Safe Push Guide for AI Agents

This guide covers the Safe Push process for pushing changes to the IntelliJ repository.

## Overview

Safe Push is a system that ensures code changes pass all required tests before being merged to protected branches (like `master`). It's powered by [Patronus](https://patronus.labs.jb.gg/).

## Safe Push CLI

The `safePush.cmd` script in the repository root provides command-line access to Safe Push without needing the IDE.

### Basic Usage

```bash
# Push current HEAD to master (most common)
./safePush.cmd HEAD:master

# Push specific commit to master
./safePush.cmd <commit-hash>:master

# Push from feature branch to master
./safePush.cmd HEAD:master  # while on feature branch

# Dry run (test without pushing)
./safePush.cmd -dry-run HEAD:master
```

### Options

```
-autosquash    Automatically squash fixup! commits (default true)
-dry-run       Run tests without push
-emergency     Skip tests and push directly (USE ONLY IN EMERGENCIES)
-verbose       Verbose output
-help          Print help
```

### What Happens During Safe Push

1. Your changes are pushed to a temporary branch
2. Patronus triggers required CI checks (tests, compilation)
3. Tests may retry up to 4 times for flaky failures
4. If all checks pass, changes are merged to the target branch
5. You receive Slack/Space/Email notification of results

### Typical Duration

- Successful Safe Push: ~3-5 hours (95th percentile ~4.7 hours)
- Failed Safe Push: ~4-5 hours

### Monitoring Progress

After starting a Safe Push, you'll receive a Patronus URL like:
```
https://patronus.labs.jb.gg/robot/<uuid>
```

Visit this URL to:
- Monitor test progress
- View failure details
- Cancel the Safe Push if needed
- Restore branch for debugging failed tests

## Emergency Push

Use `./safePush.cmd -emergency` **ONLY** for:
- Fixing broken compilation
- Reverting commits that break installers
- Fixing .patronus/config.yaml misconfigurations

**DO NOT use Emergency Push for:**
- Javadoc typo fixes
- When Safe Push fails due to "unrelated" tests (they're likely related)
- Skipping tests because you're in a hurry
- Pushing large changes that conflict with concurrent pushes

## Bazel Tests and Environment Variables

When writing tests that spawn external processes (like `bazel.cmd`), ensure required environment variables are passed:

```java
// In BUILD.bazel for java_test targets:
env_inherit = [
    "LOCALAPPDATA",      // Windows
    "PROCESSOR_ARCHITECTURE",  // Windows
    "USERPROFILE",       // Windows
    "HOME",              // Unix/macOS - required for bazel cache directory
],
```

The Bazel sandbox does NOT inherit environment variables by default. Use `env_inherit` to explicitly pass variables from the host environment to tests.

## Troubleshooting

### Safe Push stuck or slow
- Check `#ij-builds` Slack channel for infrastructure issues
- Safe Push duration varies based on infrastructure load

### Tests failing that seem unrelated
- Patronus retries tests up to 4 times
- If tests fail all 4 attempts and pass in master, your changes likely caused the failure
- Report suspected infrastructure issues to `#ij-qa-watch`

### Need to debug a failed Safe Push
1. Go to your Safe Push on Patronus
2. Click "commits" to see changes
3. Click "Restore Branch" to recover your changes locally

## References

- [Safe Push Documentation](../docs/IntelliJ-Platform/2_Running-and-Testing/Safe-Push.md)
- [Patronus Manual](https://youtrack.jetbrains.com/articles/PAT-A-6)
- Slack: `#patronus-support`, `#ij-builds`, `#ij-qa-watch`
