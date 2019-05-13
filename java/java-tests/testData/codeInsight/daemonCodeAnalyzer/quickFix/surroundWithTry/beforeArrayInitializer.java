// "Surround with try/catch" "true"
public class ExTest {
    public static String maybeThrow(String data) throws Ex {
        throw new Ex(data);
    }

    {
       String[] a = {mayb<caret>eThrow("")};
       System.out.println(a);
    }


    private static class Ex extends Exception {
        public Ex(String s) {
        }
    }
}