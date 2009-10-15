public class TestCase extends Zzz {

    Object ooo() {}

    {
        ((String) ooo()).toString();

        String s = o<caret>
    }
}