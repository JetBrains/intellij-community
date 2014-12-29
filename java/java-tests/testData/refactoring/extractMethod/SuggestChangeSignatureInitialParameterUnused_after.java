public class Test {
    {
        int x = 0;

        newMethod(x, "foo".substring(x));

        newMethod(x, "bar".substring(x));
    }

    private void newMethod(int x, String substring) {
        System.out.println(substring);
    }
}