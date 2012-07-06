package pck;
class BaseMatcher<T> {
}

class MyMatcher extends BaseMatcher<String> {
}

class Expectations {
    public <T> T with(T t) {
        System.out.println("T");
        return null;
    }

    public <T> T with(BaseMatcher<T> m) {
        return null;
    }

    public static void main(String[] args) {
        MyMatcher t = new MyMatcher();
        String w = new Expectations().with( t);
    }
}

