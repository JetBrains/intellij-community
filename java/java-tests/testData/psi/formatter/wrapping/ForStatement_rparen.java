
public class Foo {
    public void foo() {
        for (int i = 0; i < 10; i++) {
        }

        for (int thisShouldBeWrapped = 0;
             thisShouldBeWrapped < 10;
             thisShouldBeWrapped++
                ) {
        }
    }
}