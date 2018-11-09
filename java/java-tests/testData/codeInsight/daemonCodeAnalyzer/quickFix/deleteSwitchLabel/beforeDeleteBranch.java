// "Remove switch branch '"baz"'" "true"
class Main {
    public void test() {
        switch ("foo") {
            case "<caret>baz":
                int i = 1;
                class Foo {}
                System.out.println(i);
                break;
            case "qux":
            case "bar":
                i = 2;
                System.out.println("hello"+new Foo()+i);
                //oops
            default:
                System.out.println("oops");
        }
    }
}
