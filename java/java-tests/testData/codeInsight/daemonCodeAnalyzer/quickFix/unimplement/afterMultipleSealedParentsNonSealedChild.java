// "Unimplement Class" "true"
public sealed interface A permits B {}

sealed class C {}

non-sealed class B implements A {}