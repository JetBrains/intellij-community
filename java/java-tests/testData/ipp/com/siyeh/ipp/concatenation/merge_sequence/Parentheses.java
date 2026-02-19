
class Parentheses {

    public static void main(String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append("a");
        (sb).<caret>append("B");
        sb.append('c');
        sb.toString();
    }
}