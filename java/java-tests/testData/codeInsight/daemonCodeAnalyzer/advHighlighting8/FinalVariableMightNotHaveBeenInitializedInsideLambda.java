class Test {
  final Runnable runnable;

  {
    runnable = () -> System.out.println(<error descr="Variable 'runnable' might not have been initialized">runnable</error>);
  }

  final Runnable runnable1;
  {
    runnable1 = new Runnable() {
      @Override
      public void run() {
        System.out.println(runnable1);
      }
    };
  }

}

abstract class TestInnerAnonymous {


  void foo() {
    new Object() {
      final Runnable runnable;

      {
        runnable = () -> System.out.println(<error descr="Variable 'runnable' might not have been initialized">runnable</error>);
      }

      final Runnable runnable1;

      {
        runnable1 = new Runnable() {
          @Override
          public void run() {
            System.out.println(runnable1);
          }
        };
      }

    };
  }


  private static class MyObject {
    final Runnable runnable;

    {
      runnable = () -> System.out.println(<error descr="Variable 'runnable' might not have been initialized">runnable</error>);
    }

    final Runnable runnable1;

    {
      runnable1 = new Runnable() {
        @Override
        public void run() {
          System.out.println(runnable1);
        }
      };
    }

  }
}

