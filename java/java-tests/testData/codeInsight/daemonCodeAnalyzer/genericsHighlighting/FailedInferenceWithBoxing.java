class Test {
        public <T> T doStuff() {
                return null;
        }
        public boolean test() {
                return <error descr="Incompatible types. Found: 'java.lang.Object', required: 'boolean'">doStuff</error>();
        }
        
        public Boolean test1() {
                return doStuff();
        }
}