// "Replace 'if else' with '?:'" "true"

class Test {
    private Double foo(Double b) {
        return b != null ? 0D : null;
    }
}