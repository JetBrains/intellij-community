// "Add exception to method signature" "true"

class C {
 interface ExceptionThrower2 {

        <TException extends Exception>
        TException
        throwChainedException(Class<TException> exceptionClass, Throwable cause, String format, Object... argArr)  throws TException;
    }

    public interface IExample {
         void foo() throws Exception;
    }

    public static final class Example implements IExample {

        private final ExceptionThrower2 et;

        public Example(ExceptionThrower2 et) {
            this.et = et;
        }

        @Override
        public void foo() throws Exception {
            try {
                _foo();
            } catch (Exception e) {
                throw et.throwChainedException(Exception.class, e, "Error: [%s]", e.getMessage());
            }
        }

        private void _foo()
                throws Exception {
            throw new Exception();
        }
    }
}
