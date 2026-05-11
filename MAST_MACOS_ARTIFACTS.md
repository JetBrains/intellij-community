# Mast macOS Artifacts

## Purpose
This fork keeps JetBrains upstream workflows intact and adds one Mast-specific workflow for Apple Silicon test builds:

- `.github/workflows/mast_macos_artifacts.yml`

The workflow is manual-only and targets macOS aarch64, which is the only platform we care about for the first Mast IntelliJ Platform validation pass.

## Running The Workflow
Open GitHub Actions for `singlr-ai/mast`, choose `Mast macOS Artifacts`, and run the workflow manually.

Default inputs:

- `runner`: `macos-15-xlarge`
- `android_ref`: `master`
- `skip_steps`: `mac_sign,mac_notarize,mac_dmg,mac_sit`
- `build_properties`: empty

The workflow checks out:

- `singlr-ai/mast`
- `JetBrains/android` at `android_ref`

It then runs:

```bash
./installers.cmd \
  -Dintellij.build.target.os=mac \
  -Dintellij.build.target.arch=aarch64 \
  -Dintellij.build.output.root="$GITHUB_WORKSPACE/out/mast-idea-ce" \
  -Dintellij.build.skip.build.steps="mac_sign,mac_notarize,mac_dmg,mac_sit"
```

Skipping signing, notarization, DMG, and SIT is intentional for the first artifact workflow. It should produce a zip artifact without requiring Apple Developer certificates or notary credentials.

## Artifacts
Successful runs upload:

- `mast-macos-aarch64`
- `mast-macos-aarch64-logs`

Expected installable output is a macOS aarch64 `.zip` under:

```text
out/mast-idea-ce/artifacts/
```

If the upstream build starts producing `.dmg`, `.sit`, or `.tar.gz` outputs with the same command, those are uploaded too.

## Local Install Notes
The first artifact is unsigned and unnotarized. macOS may quarantine it after download. For internal testing, unzip it and use Finder's contextual Open flow, or remove quarantine from the app bundle:

```bash
xattr -dr com.apple.quarantine "/path/to/IntelliJ IDEA CE.app"
```

The app name is still upstream Community Edition at this stage. Mast branding is deliberately out of scope until we prove buildability and inventory the feature set on a real MacBook Pro.

## Upstream Sync Policy
This workflow is additive. It does not replace or edit JetBrains' upstream workflows, which keeps future upstream syncs simpler.
