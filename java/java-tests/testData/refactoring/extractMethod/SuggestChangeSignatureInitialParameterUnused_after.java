public class Test {
    {
        int x = 0;

        newMethod("foo".substring(x));

        newMethod("bar".substring(x));
    }

    private void newMethod(String substring) {
        System.out.println(substring);
    }
}