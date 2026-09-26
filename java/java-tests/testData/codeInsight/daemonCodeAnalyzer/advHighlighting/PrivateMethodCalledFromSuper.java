class FirstLevel {
  FirstLevel(int i) {
  }

  private int hello() {
    return 1;
  }

  class SecondLevel extends FirstLevel {
    SecondLevel() {
      super(1);
    }


    class ThirdLevel extends FirstLevel {
      ThirdLevel() {
        super(hello());
      }

    }
  }
}