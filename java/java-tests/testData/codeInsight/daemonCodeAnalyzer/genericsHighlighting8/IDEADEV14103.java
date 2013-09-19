class TestGenerics {

    static interface EnumInterface {
        public String getSomething();
    }

    static enum Enum1 implements EnumInterface {
        A("alpha"),
        B("beta"),
        G("gamme"),
        ;
        private String text;

        Enum1(String text) {
            this.text = text;
        }

        public String getSomething() {
            return text;
        }
    }

    static class TestBase<I extends Enum<I> & EnumInterface> {

        protected final void add(Eval eval) {
            eval.hashCode();
        }

        abstract class Eval {
            private I enumI;

            public Eval(I enumI) {
                this.enumI = enumI;
            }

            public final void doSomething() {
                System.out.println(enumI.getSomething());
            }
        }

    }




    class Test1 extends TestBase<Enum1> {

        public Test1() {
            add(new Eval(Enum1.A) {});
        }
    }
}
