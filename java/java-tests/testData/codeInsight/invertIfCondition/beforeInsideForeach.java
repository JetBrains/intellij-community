// "Invert If Condition" "true"
class Foo {
    void foo(String[] args) {
        for (String s : args) {
            <caret>if ("6".equals(s)) {
                System.out.println(s);
            }
        }
    }
}