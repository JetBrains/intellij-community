// "Make 'b()' return 'java.lang.Integer'" "true"

class MyClass {
    interface BaseInterface {

        Object b();
    }

    class BooleanImpl implements BaseInterface {

        @Override
        public Boolean b() {
            return true;
        }
    }


    class IntegerImpl implements BaseInterface {

        @Override
        public String b() {
            return <caret>1;
        }
    }
}