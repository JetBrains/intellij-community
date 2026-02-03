public class Parameters {
        public void subject(int p) {
                System.out.println(<selection>p+1</selection>);
                System.out.println(p+1);
        }

        public void context() {
                subject(1);
                subject(2);
        }
}