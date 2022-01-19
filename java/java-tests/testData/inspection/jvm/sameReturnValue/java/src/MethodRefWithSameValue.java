interface ILambdaTest {
  int getResult();
}

class LambdaTest implements ILambdaTest {
  @Override
  public int getResult() {
    return 42;
  }
}

class LamdaTest2 implements ILambdaTest {

  @Override
  public int getResult() {
    return 43;
  }
}