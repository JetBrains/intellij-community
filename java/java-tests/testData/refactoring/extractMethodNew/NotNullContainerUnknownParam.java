import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
class C {
    public void test(StringSupplier ss) {
        String s = ss.get();
        <selection>
        System.out.println(s);
        </selection>
    }
}

interface StringSupplier {
    public String get();
}
