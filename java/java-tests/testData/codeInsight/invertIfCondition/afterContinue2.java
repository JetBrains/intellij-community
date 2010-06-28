// "Invert If Condition" "true"
class A {
    void foo() {
        String[] entries = null;
        for (String entry : entries) {
            <caret>if (entry != null) {
                System.out.println("not null");
            }
        }
    }
}