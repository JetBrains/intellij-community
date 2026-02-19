import org.jetbrains.annotations.NotNull;

// "Change 'implements B' to 'extends B'" "true-preview"
class A extends B<@NotNull C<String>, @NotNull Integer> {
}

class B<@org.jetbrains.annotations.Nullable T, @org.jetbrains.annotations.NotNull K>{}


class C<@org.jetbrains.annotations.Nullable T> {
}
