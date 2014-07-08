class MyClass {
  public void foo() {
    MyDD<String> d = new MyDD<String>() {
        @Override
        public int hashCode() {
            <selection>return super.hashCode();</selection>
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        @Override
        public String toString() {
            return super.toString();
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
        }
    };
  }
}

abstract class MyDD<T> {
}