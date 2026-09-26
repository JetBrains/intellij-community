interface SAM {
}

class Test {

  {
    SAM sam = new SAM() {
        public void foo(int i) {
        }
    };
  }

}