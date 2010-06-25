// "Invert If Condition" "true"
class A {
    public static void foo() {
        if (1 == 2) {
            return;
        }
        // very important comment here
        System.out.println("something");
    }
}
