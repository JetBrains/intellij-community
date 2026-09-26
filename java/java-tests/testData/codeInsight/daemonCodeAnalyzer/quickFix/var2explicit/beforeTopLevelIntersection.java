// "Replace 'var' with explicit type" "false"
class Main {
    {
        <caret>var b = java.util.Arrays.asList("", 2).get(0);
        final int i = b.compareTo("");
    }
}