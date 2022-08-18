// "Unimplement" "true-preview"
public sealed interface A permits B {}

class C {}

non-sealed class B implements A {}