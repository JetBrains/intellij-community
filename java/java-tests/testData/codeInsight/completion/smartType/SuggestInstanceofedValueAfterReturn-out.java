public class TestCase extends Zzz {

    Object ooo() {}

    {
        if (!(ooo() instanceof String)) {
          return;
        }
        String s = (String) ooo();<caret>
    }
}