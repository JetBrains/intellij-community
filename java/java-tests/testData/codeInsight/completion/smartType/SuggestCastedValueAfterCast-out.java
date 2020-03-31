public class TestCase {

    Object ooo() {}

    {
        ((String) ooo()).toString();

        String s = (String) ooo();<caret>
    }
}