import static java.lang.Integer.parseInt;

// "Import static method 'java.lang.Integer.parseInt'" "true"
public class X {
    {
        <caret>parseInt("",10);
    }
}