// "Invert If Condition" "true"
public class C {
    public static int main(String[] args) {
        if (!a) {
            foo();
            return 1;
        }
        else {
            return 2;
        }
    }

    private static void foo() {
    }
}
