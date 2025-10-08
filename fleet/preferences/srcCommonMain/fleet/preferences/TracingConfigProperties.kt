package fleet.preferences

object TracingConfigProperties {
  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
  val otelGrpcEndpoint: String? by lazy { fleetProperty("fleet.diagnostic.otel.grpc.endpoint", null) }
  val otelHttpEndpoint: String? by lazy { fleetProperty("fleet.diagnostic.otel.http.endpoint", null) }
  val otelTracesDir: String? by lazy { fleetProperty("fleet.diagnostic.otel.traces.dir", null) }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
  val metricsFile: String? by lazy { fleetProperty("fleet.diagnostic.metrics.file", null) }

  val fahrplanStartupFile: String? by lazy { fleetProperty("fahrplan.startup", null) }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
  val fahrplanEnabled: Boolean by lazy {
    fahrplanStartupFile != null ||
    (fleetProperty("fleet.diagnostic.fahrplan.enabled", null)?.toBoolean() ?: isFleetInternalDefaultValue)
  }
  val openTelemetryEnabled: Boolean by lazy {
    isFleetInternalDefaultValue ||
    otelGrpcEndpoint != null ||
    otelHttpEndpoint != null ||
    otelTracesDir != null ||
    metricsFile != null ||
    ijPerfFile != null
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
  val ijPerfFile: String? by lazy { fleetProperty("fleet.diagnostic.ijperf.file", null) }

  val fusSchemaDir: String? by lazy { fleetProperty("fleet.diagnostic.fus.schema.dir", null) }
  val schemaGenerationMode: Boolean by lazy { fusSchemaDir != null } //todo [MM] maybe rewrite to fleet.dock.impl.RunMode
  val ijPerfSpanPrefix: String? by lazy { fleetProperty("fleet.diagnostic.ijperf.span.prefix") }

  val performanceTestMode: Boolean by lazy {
    (ijPerfFile != null || fahrplanStartupFile != null) &&
    !fleetProperty("fleet.diagnostic.suppress.quit", "false").toBoolean()
  }

  //does a number of things to simplify FUS testing
  // 1. does not update metadata (allows to work with modified metadata)
  // 2. disables bucket check (sends always)
  // 3. does not remove fus files (allows reading what is actually sent)
  // 4. shows a directory with fus files on startup
  val fusTestingMode: Boolean by lazy { fleetProperty("fleet.diagnostic.fus.testing.mode", "false").toBoolean() }
}