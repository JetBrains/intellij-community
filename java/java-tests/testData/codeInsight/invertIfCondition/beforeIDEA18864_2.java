// "Invert If Condition" "true"
class A {
    public static void foo() {
        <caret>if (1 + 2 == 3) {
            System.out.println("say");
        }
        System.out.println("something");
    }
}
