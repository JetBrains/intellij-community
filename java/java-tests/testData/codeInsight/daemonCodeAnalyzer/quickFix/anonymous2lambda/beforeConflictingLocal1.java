// "Replace with lambda" "true"
class X1 {

  public void testLambdaConversionBug() {
    Object data = new Object();
    new Thread(new Runn<caret>able() {
      @Override
      public void run() {
        System.out.println(data.getClass());
        {
          Integer data=1;
          System.out.println(data.longValue());

        }
      }
    });

  }
}