public class Foo {

    void bar() {
        equals(new Object() {
            boolean bar() {
                return f<caret>
            }
        });
    }

  boolean fefefef() {}

}
