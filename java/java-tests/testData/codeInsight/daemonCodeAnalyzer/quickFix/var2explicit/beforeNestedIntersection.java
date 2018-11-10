// "Replace 'var' with explicit type" "false"
class Main {
    {
        <caret>var b = java.util.Arrays.asList("", 2);
        final int i = b.get(0).compareTo("");
        java.io.Serializable s = b.get(0);
    }
}