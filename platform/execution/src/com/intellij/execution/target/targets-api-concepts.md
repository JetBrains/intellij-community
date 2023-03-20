Targets API should have several layers of abstractions which allow it to solve its own set of problems.

# Base Layer

The layer allows you to prepare the target environment for execution, build the command line and start the process.

The layer provides the following entities:

* Target configuration.
  This entity defines a specific target, for example, an SSH machine by the connection credentials or a Docker image by Docker server,
  Docker image name and specific attributes. The layer provides the mechanism to serialize data that defines the target to XML.
* [Language configuration](#Language-Configuration).
  This entity is optional for the execution of a process. It could be used for defining additional properties to perform a specific task on
  the target to support integration of the IDE with languages, frameworks and tools.
* Execution request.
  This entity takes in the list of requirements required for a proper functioning of the future process. These requirements specifically
  include port bindings and volume bindings. Requirements might contain partial information on bindings. For example, a _target port
  binding_ demands the specific port on the target, but it might not specify the associated port on the local machine, leaving the target to
  decide on that matter.
  Each request is expected to produce a single execution environment, which in its turn creates a single process. Reusing of the request is
  not allowed.
* Execution environment.
  The execution environment is produced by the execution request. During the production phase, the requirements defined in the request are
  being resolved: the contents of upload volumes are delivered to the target, the upload volumes are prepared to be available for the
  further execution of the process, the port bindings are prepared to be available for the process.
  As a result of this, the environment consolidates the reply values for the requirements specified in the request. Together with the
  initial request requirements, the reply values make up the set of path mappings and port bindings. This correspondence is likely to be
  used for composing the command line for the process.
* Command line for the process.
  The command line defines the process by defining a binary, its parameters, the working directory and a set of environment variables.

Having a project for building the process execution must not be obligatory on this level.

## Language Configuration

While the target configuration contains the base information required for building the process, in most cases the user should also be able
to specify additional information to support specific workflows. For example, building Go application on a target requires the path to Go
binary and the value of `GOPATH` environment variable to be set for the build process. These are the paths on the target, which should be
defined by the user.

The language configuration solves this problem. It declares a set of properties to be defined by the user to fulfill the specific
language/frameworks/tooling needs.

The language configuration is set up for a specific target configuration, while each target configuration could have several related
language configurations linked to it.

## Configuration Wizard

Base layer provides the methods for configuring specific target and language data using a wizard. The flow of the wizard includes three
steps:

1. Configuring a target.
2. Introspect the configured target.
   This step allows the language-specific part to retrieve the information from the target and to use this information in the step (3) for
   building initial values of corresponding properties. The language-specific implementation retrieves the information by executing a
   sequence of operations on the target using the introspection part of the API. Two types of operations are now supported: get the value of
   a specified environment variable, execute a command and retrieve its output (`stdout` and `stderr`).
   For example, for pre-configuring the properties for Go language-related tasks, Go language-specific implementation tries to locate `go`
   binary on the target by executing `which go` command on the target and retrieves the value of `GOPATH` environment variable defined in
   the target's environment.
   Though it is not implemented, it is possible to provide access to the file system of the target during the introspection phase.
3. Configuring language-specific properties.
   See [Language Configuration](#Language-Configuration) section.

# "Run Targets" Layer

The layer is built on top of the base layer.

The layer stores targets configurations data on the **project** level.

The layer provides the additional UI:

* "Run-on" component for Run Configurations.
* "Targets" page with the list of configured targets in Project Settings.

# "SDK on Targets" Layer

The layer is built on top of the base layer. It does not depend on "Run Targets" layer.

The layer stores targets configurations data within SDK on the **application** level.