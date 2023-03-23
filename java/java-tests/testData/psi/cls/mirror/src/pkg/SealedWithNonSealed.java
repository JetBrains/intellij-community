package pkg;

sealed interface SealedWithNonSealed {
  non-sealed class ANonSealed implements SealedWithNonSealed {}
  final class finalClass implements SealedWithNonSealed {}
}
