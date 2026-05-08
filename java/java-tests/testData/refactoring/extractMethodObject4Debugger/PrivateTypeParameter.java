package privatebound;

public class PrivateTypeParameter {
    public static void main(String[] args) {
        new PrivateTypeParameter().foo(new PrivateValue());
    }

    private <T extends PrivateValue> void foo(T value) {
        <caret>int x = 0;
    }

    private static class PrivateValue {
    }
}
