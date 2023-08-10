interface ILambdaTest {
  int getResult();
}

class LambdaTest implements ILambdaTest {
  ILambdaTest lambda = () -> {
    if (true) {
      return 42;
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