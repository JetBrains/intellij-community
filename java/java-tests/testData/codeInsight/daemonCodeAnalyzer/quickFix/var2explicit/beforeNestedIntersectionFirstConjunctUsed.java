// "Replace 'var' with explicit type" "false"
class Main {
    {
        <caret>var b = java.util.Arrays.asList("", 2);
        final Object o = b.get(0);
    }
}