class MyClass {
  public void foo() {
    MyDD<String> d = new MyDD<String>() {
        @Override
        public int hashCode() {
            <selection>return super.hashCode();    //To change body of overridden methods use File | Settings | File Templates.</selection>
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        public String toString() {
            return super.toString();    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();    //To change body of overridden methods use File | Settings | File Templates.
        }
    };
  }
}

abstract class MyDD<T> {
}