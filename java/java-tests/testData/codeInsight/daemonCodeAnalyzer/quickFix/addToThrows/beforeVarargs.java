// "Add exception to method signature" "true-preview"

class C {
 interface ExceptionThrower2 {

        <TException extends Exception>
        TException
        throwChainedException(Class<TException> exceptionClass, Throwable cause, String format, Object... argArr)  throws TException;
    }

    public interface IExample {
         void foo();
    }

    public static final class Example implements IExample {

        private final ExceptionThrower2 et;

        public Example(ExceptionThrower2 et) {
            this.et = et;
        }

        @Override
        public void foo() {
            try {
                _foo();
            } catch (Exception e) {
                throw et.throw<caret>ChainedException(Exception.class, e, "Error: [%s]", e.getMessage());
            }
        }

        private void _foo()
                throws Exception {
            throw new Exception();
        }
    }
}
