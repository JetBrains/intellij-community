// "Bind constructor parameters to fields" "false"

class Bar {

    private int myi1;
    private int myi2;

    Bar(int <caret>i1, int i2, String i3) {
      this();
    }
    Bar(){
      myi1 = 1;
      myi2 = 1;
    }
}