// "Invert If Condition" "true"
public class C {
    public static int main(String[] args) {
        <caret>if (a) return 2;
        foo();
        return 1;
    }

    private static void foo() {
    }
}
