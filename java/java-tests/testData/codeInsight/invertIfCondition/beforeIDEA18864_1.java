// "Invert If Condition" "true"
class A {
    public static void foo() {
        <caret>if (1 != 2) {
            // very important comment here
            System.out.println("something");
        }
    }
}
