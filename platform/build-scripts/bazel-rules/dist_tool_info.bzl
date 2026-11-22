"""The identity of a distribution tool, which every action of the tool reads. This file loads nothing."""

DistToolInfo = provider(
    doc = "The identity of a `dist_tool`.",
    fields = {
        "identity": "File: the text that states the crates, the sources, the Rust toolchain and the rules_rs sources of the tool. It is equal on every host.",
    },
)

def dist_tool_inputs(tool):
    """Returns the inputs that every action of a distribution tool declares besides its own: the identity of the tool.

    The remote cache key of the action omits the binary and the runfiles of the tool, see
    `community/build/bazel_scrubbing.cfg`. The identity takes their place. A tool that is no `dist_tool`, such as
    a test fake, adds nothing.

    Args:
      tool: the target of the tool attribute, with `cfg = "exec"`.
    """
    if DistToolInfo in tool:
        return [tool[DistToolInfo].identity]
    return []
