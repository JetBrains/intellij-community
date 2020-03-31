public class TestCase {

    Object ooo() {}

    {
        if (!(ooo() instanceof String)) {
          return;
        }
        Object s = ooo();<caret>
    }
}