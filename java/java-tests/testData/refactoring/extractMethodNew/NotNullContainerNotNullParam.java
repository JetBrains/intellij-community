import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
class C {
    void test(String s) {
        <selection>
        System.out.println(s);
        </selection>
    }
}
