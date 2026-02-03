// "Change 'implements B' to 'extends B'" "true-preview"

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class A extends B<@NotNull C<String>, @NotNull Integer> {
}

class B<@Nullable T, @NotNull K>{}


class C<@Nullable T> {
}

