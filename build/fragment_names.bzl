"""The names of the platform fragments that the distribution macros of both halves share.

Data only: this file loads nothing. The plan generator writes the same names into the generated fragment inputs.
"""

# The payload key of the platform `lib/` jars. The packers pack every one of them.
PLATFORM_LIB_FRAGMENT = "platform_lib"

# The component that places `modules/module-descriptors.{dat,jar}`.
PLATFORM_RUNTIME_MODULE_REPOSITORY_FRAGMENT = "platform_runtime_module_repository"
