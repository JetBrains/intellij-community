public class Parameters {
        public void subject(final int anObject) {
                System.out.println(anObject);
                System.out.println(anObject);
        }

        public void context() {
                subject(1 +1);
                subject(2 +1);
        }
}