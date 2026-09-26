// "Move initializer to constructor" "false"
public record X(int a, int b) {
    int <caret>i=7;

    X() {
    }
}
