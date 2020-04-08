public class TestCase {

    Object ooo() {}

    {
        if (!(ooo() instanceof String)) {
          return;
        }
        String s = (String) ooo();<caret>
    }
}