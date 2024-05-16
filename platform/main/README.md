These modules are used as aggregators of dependencies for running from sources and in tests.
They are included in respective product `*.main` modules.

# Extracting and splitting the platform

- If a module is extracted from the big dump (ide/lang/etc) into a pure v2 module,
  it should be registered as a dependency in `intellij.platform.main`.
- If a module is then split between frontend/backend/monolith,
  the parts should be registered as dependencies in the corresponding `.main` modules.  
