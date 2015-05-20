import static java.lang.Integer.parseInt;

// "Static import method 'java.lang.Integer.parseInt'" "true"
public class X {
    {
        <caret>parseInt("",10);
    }
}