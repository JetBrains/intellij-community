class FirstLevel {
  FirstLevel(int i) {
  }

  int hello() {
    return 1;
  }

  class SecondLevel extends FirstLevel {
    SecondLevel() {
      super(1);
    }


    class ThirdLevel extends FirstLevel {
      ThirdLevel() {
        super(SecondLevel.h());
      }

    }

    static int h() {
      return 1;
    }
  }
}