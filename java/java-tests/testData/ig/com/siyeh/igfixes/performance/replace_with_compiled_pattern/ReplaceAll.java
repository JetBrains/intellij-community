class Literal {

    void f(String text, String replacement) {
        text.<caret>replaceAll("http://.+", replacement);
        text.replaceAll("http://.+", replacement);
        text.replaceAll("https://.+", replacement);
        text.replaceAll("https://.+", replacement);
    }
}