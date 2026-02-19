// "Remove switch branch '"bar"'" "true-preview"
class Main {
    public void test() {
        switch ("foo") {
            case "baz":
                System.out.println("foo");
                break;
            //oops
        }
    }
}
