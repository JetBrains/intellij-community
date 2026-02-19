// "Replace 'if else' with '?:'" "true"

class Test {
    private Double max(final Double a, final Double b) {
        return a != null && b != null ? Double.valueOf(Math.max(a, b)) : a != null ? a : b;
    }
}