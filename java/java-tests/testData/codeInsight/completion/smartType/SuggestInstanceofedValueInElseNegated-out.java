public class TestCase {

    Object ooo() {}

    {
        if (!(ooo() instanceof String)) {
        } else {
          String s = (String) ooo();<caret>
        }
    }
}