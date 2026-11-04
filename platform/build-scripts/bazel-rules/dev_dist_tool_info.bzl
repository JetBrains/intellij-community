"""The identity of a dev-dist tool, which every action of the tool reads. This file loads nothing."""

DevDistToolInfo = provider(
    doc = "The identity of a `dev_dist_tool`.",
    fields = {
        "identity": "File: the text that states the crates, the sources, the Rust toolchain and the rules_rs sources of the tool. It is equal on every host.",
    },
)

def dev_dist_tool_inputs(tool):
    """Returns the inputs that every action of a dev-dist tool declares besides its own: the identity of the tool.

    The remote cache key of the action omits the binary and the runfiles of the tool, see
    `community/build/bazel_scrubbing.cfg`. The identity takes their place. A tool that is no `dev_dist_tool`, such as
    a test fake, adds nothing.

    Args:
      tool: the target of the tool attribute, with `cfg = "exec"`.
    """
    if DevDistToolInfo in tool:
        return [tool[DevDistToolInfo].identity]
    return []
