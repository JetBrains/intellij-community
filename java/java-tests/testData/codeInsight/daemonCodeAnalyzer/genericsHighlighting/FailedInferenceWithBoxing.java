class Test {
        public <T> T doStuff() {
                return null;
        }
        public boolean test() {
                <error descr="Incompatible types. Found: 'java.lang.Object', required: 'boolean'">return doStuff();</error>
        }
        
        public Boolean test1() {
                return doStuff();
        }
}