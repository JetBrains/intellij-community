public class TestCase {

    Object ooo() {}

    {
        if (!(ooo() instanceof String)) {
        } else {
          String s = oo<caret>
        }
    }
}