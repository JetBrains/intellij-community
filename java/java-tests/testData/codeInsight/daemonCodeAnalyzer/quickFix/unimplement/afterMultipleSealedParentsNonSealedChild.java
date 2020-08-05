// "Unimplement Class" "true"
public sealed interface A permits B {}

final class C {}

non-sealed class B implements A {}