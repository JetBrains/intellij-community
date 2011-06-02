// "Add constructor parameter" "true"
public class ConstructorParams {
    private final String my<caret>Text;
    private Object myO;

    public ConstructorParams() {
    }

    public ConstructorParams(String text, int foo) {
        myText = text;
    }
}
