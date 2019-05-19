// "Remove switch branch '"baz"'" "true"
class Main {
    public void test() {
        switch ("foo") {
            case "qux":
            case "bar":
                class Foo {}
                int i;
                i = 2;
                System.out.println("hello"+new Foo()+i);
                //oops
            default:
                System.out.println("oops");
        }
    }
}
