// "Unimplement" "true-preview"
public sealed interface A permits B {}

sealed class C permits B {}

non-sealed class B extends C<caret> implements A {}