public interface B {
    Inner i = new Inner();

    class Inner {
        public boolean equals(Object o) {
            return o instanceof Inner;
        }
    }
}