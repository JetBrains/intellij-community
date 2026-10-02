"""The platform jar order of the runtime module repository, as a rule with two lists.

The core plugin lists the jars of `first` in that order. Then it lists every other jar with a module, sorted by its
smallest member module name. Then it lists the jars of `last` in that order. The plan generator states the two lists in
`DEV_DIST_PLATFORM_JAR_ORDERS` and proves the rule on the order of `JarPackager`. This file loads nothing, so a unit
test can call it.
"""

def platform_jar_order(entries, first, last):
    """Orders the platform jars by the rule.

    The sort key of a jar is its smallest member module name, whatever the merge order of its members. A module packs
    into one jar, so two jars never share a key.

    Args:
      entries: list of struct(destination, member_modules): the packed jars. `destination` is relative to `lib/`.
      first: list of string: the destinations before the sorted range, in order.
      last: list of string: the destinations after the sorted range, in order.

    Returns:
      struct(destinations, error). `destinations` lists every entry in order. `error` is `None`, or the message of a
      name that no entry has, of a name stated twice, or of a library-only jar that `last` does not name.
    """
    known = {entry.destination: entry for entry in entries}
    named = {}
    for destination in first + last:
        if destination not in known:
            return struct(destinations = [], error = "the platform jar order names %s, but the payload does not pack it" % destination)
        if destination in named:
            return struct(destinations = [], error = "the platform jar order names %s twice" % destination)
        named[destination] = True
    middle = []
    for entry in entries:
        if entry.destination in named:
            continue
        if not entry.member_modules:
            return struct(
                destinations = [],
                error = "%s has no module and so no sort key, but the platform jar order does not name it in last" % entry.destination,
            )
        middle.append((min(entry.member_modules), entry.destination))
    return struct(destinations = first + [destination for _, destination in sorted(middle)] + last, error = None)
