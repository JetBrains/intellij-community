"""The names of the platform fragments that the distribution macros of both halves share.

Data only: this file loads nothing. The plan generator writes the same names into the generated fragment inputs.
"""

# The payload key of the platform `lib/` jars. The packers pack every one of them.
PLATFORM_LIB_FRAGMENT = "platform_lib"

# The fragment name of the reference target that packs those jars the `JarPackager` way.
PLATFORM_LIB_REFERENCE_FRAGMENT = "platform_lib_reference"

# The component that places `modules/module-descriptors.{dat,jar}`, and the payload of its reference.
PLATFORM_RUNTIME_MODULE_REPOSITORY_FRAGMENT = "platform_runtime_module_repository"
