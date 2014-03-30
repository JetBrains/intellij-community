class SomeClass {
    public static final Object OBJECT_OVERRIDDEN = new Object() {
        public String toString() {
            return "";
        }
    };

    public String get() {
        return OBJECT_OVERRIDDEN.toSt<ref>ring();
    }
}