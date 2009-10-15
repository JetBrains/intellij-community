public class TestCase extends Zzz {

    Object ooo() {}

    {
        if (!(ooo() instanceof String)) {
          return;
        }
        Object s = ooo();<caret>
    }
}