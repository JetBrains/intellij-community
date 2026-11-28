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

def host_select(value_of):
    """A `select` over the `host_*` config settings: `value_of(platform)` for each host platform.

    A host that is not one of [HOST_PLATFORMS] matches no key, so the select fails with its name.
    """
    return select({"@community//build:host_" + platform: value_of(platform) for platform in HOST_PLATFORMS})

# The platforms of a Linux guest. A Docker worker shares the kernel of a Linux VM on the host, so it runs the CPU of
# the host (`GuestArch::of` of the VM controller, `community/tools/vm/crates/avl-base/src/config.rs`).
LINUX_GUEST_PLATFORMS = [
    "linux_aarch64",
    "linux_x64",
]

def linux_guest_platform(host):
    """The platform of the Linux guest of the host platform `host`."""
    return "linux_" + platform_parts(host).arch

def linux_guest_select(by_guest):
    """A `select` over the host platforms: the entry of `by_guest` for the Linux guest platform of each host.

    `by_guest` is a dictionary from each of [LINUX_GUEST_PLATFORMS] to a value, so a BUILD file can call it.
    """
    return host_select(lambda host: by_guest[linux_guest_platform(host)])
