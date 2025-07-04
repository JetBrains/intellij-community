module fleet.lsp.protocol {
  requires fleet.util.core;
  requires io.ktor.network.tls;
  requires kotlin.stdlib;
  requires kotlinx.coroutines.core;
  requires kotlinx.io.core;
  requires kotlinx.serialization.core;
  requires kotlinx.serialization.json;
  requires org.jetbrains.annotations;

  exports com.jetbrains.lsp.protocol;
  exports com.jetbrains.lsp.implementation;
}