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

interface Fun<A, B> {
  B m(A a);
}

class TestAnonymousWithRefToTheTopLevelUninitializedField {
  private final int myId;

  private Runnable r = new Runnable() {
    final int localId;
    {
      localId = 0;
    }

    Fun<Integer, Integer> ff = (a) -> <error descr="Variable 'myId' might not have been initialized">myId</error>;
    Fun<Integer, Integer> ffLocal = (a) -> localId;
    public void run() {
    }
  };

  public TestAnonymousWithRefToTheTopLevelUninitializedField(int id) {
    myId = id;
  }

}

class TestThisQualified {
  final String s;

  final Runnable r = () -> System.out.println(<error descr="Variable 'this.s' might not have been initialized">this.s</error>.length());
  final Runnable r2 = () -> System.out.println(this.r2);
  final Runnable r3;
  {
    r3 = () -> System.out.println(<error descr="Variable 'this.r3' might not have been initialized">this.r3</error>);
  }

  public TestThisQualified() {
    s = "";
  }
}