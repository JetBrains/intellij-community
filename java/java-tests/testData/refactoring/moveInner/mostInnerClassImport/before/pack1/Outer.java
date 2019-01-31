package pack1;

public class Outer {
    public static class Inner {         
        private final MostInner innerMost = new MostInner();

        public class MostInner {}
    }
}