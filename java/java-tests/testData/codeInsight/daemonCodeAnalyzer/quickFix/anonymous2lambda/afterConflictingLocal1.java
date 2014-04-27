// "Replace with lambda" "true"
class X1 {

  public void testLambdaConversionBug() {
    Object data = new Object();
    new Thread(() -> {
      System.out.println(data.getClass());
      {
        Integer data1 =1;
        System.out.println(data1.longValue());

      }
    });

  }
}