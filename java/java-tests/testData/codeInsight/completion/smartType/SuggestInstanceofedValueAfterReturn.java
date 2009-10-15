public class TestCase extends Zzz {

    Object ooo() {}

    {
        if (!(ooo() instanceof String)) {
          return;
        }
        String s = o<caret>
    }
}