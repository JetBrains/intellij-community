module fleet.multiplatform.shims {
  requires kotlin.stdlib;
  requires kotlinx.coroutines.core;
  requires static fleet.util.multiplatform;

  exports fleet.multiplatform.shims;
}