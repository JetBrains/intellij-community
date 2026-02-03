// "Add constructor parameter" "true"
public class ConstructorParams {
    private final String myText;
    private final static Object ourO;

    public ConstructorParams(String myText) {
        this.myText = myText;<caret>
    }
}
