import java.util.regex.Pattern;

class Literal {

    private static final Pattern ABC = Pattern.compile("abc", Pattern.LITERAL);

    void f(String text, String replacement) {
        ABC.matcher(text).replaceAll("replacement");//end line comment
    }
}