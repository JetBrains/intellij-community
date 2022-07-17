interface ILambdaTest {
  int getResult();
}

class LambdaTest implements ILambdaTest {
  ILambdaTest lambda = () -> {
    if (false) {
      return 41;
    }
    else {
      return 42;
    }
  }
  };
  @Override
  public int getResult() {
    return 42;
  }
}