abstract class Type<out T>(val default: T) {
  open val forEnumConstant: T = default
}

val nullability = object : Type<Nullability>(Nullability.Default) {
  override val forEnumConstant: Nullability
    get() = Nullability.NotNull
}

enum class Nullability {
    Nullable,
    NotNull,
    Default
}
