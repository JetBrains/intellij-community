// "Remove switch label '"qux"'" "true-preview"
class Main {
    public void test() {
        switch ("foo") {
            case "baz":
                int i = 1;
                class Foo {}
                System.out.println(i);
                break;
            case "<caret>qux" /*comment*/:
            case "bar":
                i = 2;
                System.out.println("hello"+new Foo()+i);
                //oops
            default:
                System.out.println("oops");
        }
    }
}
