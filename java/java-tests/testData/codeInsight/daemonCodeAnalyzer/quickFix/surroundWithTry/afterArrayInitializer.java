// "Surround with try/catch" "true"
public class ExTest {
    public static String maybeThrow(String data) throws Ex {
        throw new Ex(data);
    }

    {
        String[] a = new String[0];
        try {
            a = new String[]{maybeThrow("")};
        } catch (Ex e) {
            throw new RuntimeException(e);
        }
        System.out.println(a);
    }


    private static class Ex extends Exception {
        public Ex(String s) {
        }
    }
}