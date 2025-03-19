import java.util.regex.Pattern;

class Literal {

    private static final Pattern PATTERN = Pattern.compile("https://.+");
    private static final Pattern REGEX = Pattern.compile("http://.+");

    void f(String text, String replacement) {
        REGEX<caret>.matcher(text).replaceAll(replacement);
        REGEX.matcher(text).replaceAll(replacement);
        PATTERN.matcher(text).replaceAll(replacement);
        PATTERN.matcher(text).replaceAll(replacement);
    }
}