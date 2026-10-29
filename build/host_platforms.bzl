"""The platforms that a distribution is assembled for and that a build runs on, as one vocabulary.

This file loads nothing. The rules of the BUILD load graph load it, so an edit of a download declaration does not
reload their packages.
"""

# Named after the `host_*` config settings in this package, which are the select axis every consumer uses.
HOST_PLATFORMS = [
    "darwin_aarch64",
    "darwin_x64",
    "linux_aarch64",
    "linux_x64",
    "windows_aarch64",
    "windows_x64",
]

def platform_parts(platform):
    """Splits a [HOST_PLATFORMS] entry into its OS and architecture tokens."""
    if platform not in HOST_PLATFORMS:
        fail("'%s' is not one of %s" % (platform, HOST_PLATFORMS))
    os, _, arch = platform.rpartition("_")
    return struct(os = os, arch = arch)
