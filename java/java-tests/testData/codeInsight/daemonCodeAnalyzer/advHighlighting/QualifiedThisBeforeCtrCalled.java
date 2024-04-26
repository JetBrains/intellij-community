class FirstLevel {
  FirstLevel(Object i) {
  }

  class SecondLevel {

    class ThirdLevel extends FirstLevel {

      ThirdLevel(int i) {
        super(new ThirdLevel(1) {
          public void a() {
            <error descr="Cannot reference 'ThirdLevel.this' before supertype constructor has been called">ThirdLevel.this</error>.hashCode();
          }
        });
      }
    }
  }
}