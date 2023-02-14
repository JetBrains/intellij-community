# Targets API

Targets API is a set of application programming interfaces that allows to request the execution of a process on a generalized machine called
target. The target can be a local machine, a remote SSH server, a Docker engine, etc. The purpose of the API is to be able to write client
code that works for all targets currently implemented, as well as targets that will be added in the future.

The core goals of the API:

- Provide first-class support for remote and cloud development in IntelliJ IDEA and other JetBrains IDEs.
- Enable the user to configure an execution environment, specified either entirely through a GUI or using infrastructure as code.
- If relevant for the target language, enable environment introspection. This is the process that allows us to obtain information we need
  about the environment in order to provide accurate code completion suggestions.
- Ensure that any time we run user code, we can do so in the environment specified by the user. This includes building, running, debugging,
  and testing.

Discover [the core concepts of the API](targets-api-concepts.md) and the approach to [using the API](using-targets-api.md).