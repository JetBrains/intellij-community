"""The build date that every action of a dev distribution pins."""

# Pinned so the components of one distribution agree and an assembly does not carry the wall clock into its outputs. It
# dates archive entries and the `.SNAPSHOT` plugin version suffix, and both would otherwise differ between components
# built minutes apart.
#
# Deliberately *not* the product build date. A dev distribution stamps none, so the IDE resolves its build time at
# startup and no EAP expiration period can run out on a cached distribution. See `computeAppInfoXml`. A far-future date
# chosen to outrun that period makes every dev IDE start expired, because a build date over a day ahead of the wall
# clock is expired too.
#
# The product files action and `dev_dist_plugin_descriptor` pass the same date to their tools. An EAP product without a
# `majorReleaseDate` takes it as its release date.
DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS = "1767225600"  # 2026-01-01T00:00:00Z
