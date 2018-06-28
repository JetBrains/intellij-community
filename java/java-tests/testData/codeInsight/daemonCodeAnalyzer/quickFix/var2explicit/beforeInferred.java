// "Replace 'var' with explicit type" "true"
class Main {
    {
        <caret>var b = java.util.Arrays.asList("", "").get(0);
        final int i = b.compareTo("");
    }
}