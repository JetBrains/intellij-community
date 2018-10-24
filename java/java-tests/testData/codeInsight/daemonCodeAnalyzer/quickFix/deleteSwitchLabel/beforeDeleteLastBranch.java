// "Remove switch branch '"bar"'" "true"
class Main {
    public void test() {
        switch ("foo") {
            case "baz":
                System.out.println("foo");
                break;
            case "<caret>bar":
                int i = 2;
                class Foo {}
                System.out.println("hello"+new Foo()+i);
                //oops
        }
    }
}
