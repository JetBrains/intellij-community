// "Remove switch label '"qux"'" "true"
class Main {
    public void test() {
        switch ("foo") {
            case "baz":
                int i = 1;
                class Foo {}
                System.out.println(i);
                break;
            /*comment*/
            case "bar":
                i = 2;
                System.out.println("hello"+new Foo()+i);
                //oops
            default:
                System.out.println("oops");
        }
    }
}
