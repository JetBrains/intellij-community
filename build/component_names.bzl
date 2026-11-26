"""The names of the platform components that the distribution macros of both halves share.

Data only: this file loads nothing. The plan generator writes the same names into the generated component inputs.
"""

# The payload key of the platform `lib/` jars. The packers pack every one of them.
PLATFORM_LIB_COMPONENT = "platform_lib"

# The component that places `modules/module-descriptors.{dat,jar}`.
PLATFORM_RUNTIME_MODULE_REPOSITORY_COMPONENT = "platform_runtime_module_repository"
