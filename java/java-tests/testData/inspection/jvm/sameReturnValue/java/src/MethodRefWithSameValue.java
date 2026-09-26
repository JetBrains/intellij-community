interface ILambdaTest {
  int getResult();
}

class LambdaTest implements ILambdaTest {
  ILambdaTest lambda = this::getResult;

  @Override
  public int getResult() {
    return 42;
  }
}