// "Remove switch label '"bar"'" "true"
class Main {
    public void test() {
        switch ("foo") {
            case "baz":
                int i = 1;
                class Foo {}
                System.out.println(i);
                break;
            case "qux":
                // comment
                i = 2;
                System.out.println("hello"+new Foo()+i);
                //oops
            default:
                System.out.println("oops");
        }
    }
}
