// "Surround with try/catch" "false"
class Test {
    public static void main(String[] args) {
        var foo = new T<caret>est() {
            int x = 2;
        };
        System.out.println(foo.x);
    }

    Test() throws Exception {}
}