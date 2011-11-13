// "Add constructor parameter" "true"
public class ConstructorParams {
    private final String myText;
    private final Object ourO = null;

    public ConstructorParams(String myText) {
        this.myText = myText;<caret>
    }
}
