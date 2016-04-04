
public class Foo {
    public static void foo() {
        bar(
                bar(1, 2, 3),
                bar(3, 4, 5),
                bar(6, 7, 8)
        );
    }
}