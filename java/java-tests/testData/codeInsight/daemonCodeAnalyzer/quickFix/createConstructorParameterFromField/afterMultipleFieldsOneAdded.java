// "Add constructor parameter" "true"
public class ConstructorParams {
    private final String myText;
    private Object myO;

    public ConstructorParams(String myText) {
        this.myText = myText;<caret>
    }

    public ConstructorParams(String text, int foo) {
        myText = text;
    }
}
