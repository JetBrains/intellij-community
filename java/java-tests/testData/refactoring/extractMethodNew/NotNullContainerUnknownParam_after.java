import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnknownNullability;

@NotNullByDefault
class C {
    public void test(StringSupplier ss) {
        String s = ss.get();

        newMethod(s);

    }

    private void newMethod(@UnknownNullability String s) {
        System.out.println(s);
    }
}

interface StringSupplier {
    public String get();
}
