// "Change 'implements B' to 'extends B'" "true-preview"
class A implements
        <caret>B<@org.jetbrains.annotations.NotNull C<java.lang.String>,
          @org.jetbrains.annotations.NotNull Integer>{
}

class B<@org.jetbrains.annotations.Nullable T, @org.jetbrains.annotations.NotNull K>{}


class C<@org.jetbrains.annotations.Nullable T> {
}
