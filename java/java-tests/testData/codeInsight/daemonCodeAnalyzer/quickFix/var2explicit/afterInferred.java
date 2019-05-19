// "Replace 'var' with explicit type" "true"
class Main {
    {
        String b = java.util.Arrays.asList("", "").get(0);
        final int i = b.compareTo("");
    }
}