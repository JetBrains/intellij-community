public class TestCase {

    Object ooo() {}

    {
        ((String) ooo()).toString();

        String s = oo<caret>
    }
}