// "Invert 'if' condition" "true"
class A {
    void foo() {
        String[] entries = null;
        for (String entry : entries) {
            <caret>if (entry == null)
                continue;
            System.out.println("not null");
        }
    }
}