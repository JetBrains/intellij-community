import java.util.List;

public class Test {

    public Test(Object o1, Object o2) {
    }

    private Object test(List<String> list) {
        <selection>for (String some : list) {
            String x = "x";
            String y = "y";
            if (some.isEmpty()) return new Test(x, y);
        }</selection>
        return null;
    }

}