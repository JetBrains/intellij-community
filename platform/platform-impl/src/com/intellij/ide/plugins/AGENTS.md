# Plugin Manager Specifications

Plugin manager behavior specifications start at the [specification index](../../../../../spec/plugin-manager/README.md).

Before you change a file, compare it with each specification `targets` field. Read each matching specification.

Update the owning specification in the same changelist when behavior changes.

Behavior includes UX, navigation, source routing, filtering, persistence, and plugin operations.

An implementation-only refactor does not need a specification change. State this in the final report.

If you find existing drift, report the requirement and evidence.
Continue when the change remains safe. Repair the drift in a separate commit.

Keep observable behavior in specifications. Keep implementation mechanics in KDoc.

A file without a matching target has no specification duty.
