import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class B {
    public final A myDelegate = new A();

    @Nullable
    public Object methodFromA(@NotNull String s) {
        return myDelegate.methodFromA(s);
    }
}