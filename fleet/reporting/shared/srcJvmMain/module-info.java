module fleet.reporting.shared {
  requires fleet.reporting.api;
  requires kotlin.stdlib;
  requires kotlinx.coroutines.core;
  requires kotlinx.serialization.json;
  requires kotlinx.serialization.core;
  requires fleet.multiplatform.shims;

  exports fleet.reporting.shared.fahrplan;
  exports fleet.reporting.shared.tracing;
  exports fleet.reporting.shared.runtime;
}