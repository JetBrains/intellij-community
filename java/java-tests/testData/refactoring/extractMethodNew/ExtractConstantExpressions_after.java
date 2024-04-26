import org.jetbrains.annotations.Nullable;

class Test {
    final String f1 = "field";
    String test(boolean condition){
        final String f2 = "variable";
        Integer x = newMethod(condition, f2);
        if (x == null) return f1 + f2 + "literal";
        System.out.println(x);
        return "default";
    }

    private @Nullable Integer newMethod(boolean condition, String f2) {
        int x = 42;
        if (condition) return null;
        if (!condition) return null;
        return x;
    }
}