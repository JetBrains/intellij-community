class Test_3 {

    public static void main(String[] aArgs) {
        Info info = null;
        update(info)
                .withSuper()
                .withChild();
    }

    public interface Info<_Info> { }

    public static <Type extends Info<?>> C<?> update(Type aType) {
        return null;
    }

    public static class C<_Builder extends C< _Builder>> extends A<_Builder> {
        public _Builder withChild() {
            return null;
        }
    }

    public static class A<_B extends A<_B>> {
        public _B withSuper() {
            return null;
        }
    }
}