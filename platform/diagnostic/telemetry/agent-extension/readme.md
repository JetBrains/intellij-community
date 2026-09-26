# OpenTelemetry Javaagent extension

This module is required in order to extend the functionality of
the [OpenTelemetry Javaagent](https://opentelemetry.io/docs/zero-code/java/agent/).

The module is built by JPS as an ordinary IDEA plugin module.
It should not have any platform dependencies, just as any other module should not depend on it.

## How to enable it?

To enable an extension, the path to it should be passed as a JVM argument together with the Javaagent argument, like
`-javaagent:opentelemetry-javaagent-2.8.0.jar -Dotel.javaagent.extensions=extension.jar`.

From the platform side, it is possible to turn on the extension via
`com.intellij.platform.diagnostic.telemetry.impl.agent.AgentConfiguration`,
all the necessary logic already implemented in the `AgentConfiguration` class.

## Features:

### JSON exporter

Provides a way to dump collected telemetry spans into a file.

#### Configuration

- `otel.traces.exporter.json.file.enabled` - should be set to `true` to enable the file exporter;
- `otel.traces.exporter.json.file.location` - specifies the path to the target folder to which telemetry will be collected;
