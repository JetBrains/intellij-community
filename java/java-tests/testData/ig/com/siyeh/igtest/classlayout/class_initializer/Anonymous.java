package classlayout.class_initializer;

class Anonymous {

  void foo() {
    new Object() {

      int i;

      {
        i = 9;
      }
    };
  }
}