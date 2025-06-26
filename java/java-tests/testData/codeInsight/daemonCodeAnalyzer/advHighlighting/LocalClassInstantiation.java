class LocalInstantiation {

  // local class in method
  static void foo(Object there) {
    class Local {
      {
        there.hashCode();
      }
      static {
        //can only be instantiated from its own static context
          //cannot be instantiated here
          //not allowed to be instantiated heree
          //cannot be instantiated outside its static context
        <error descr="Local class 'Local' cannot be instantiated from a different static context">new Local()</error>;    // should be highlighted as an error
      }
      static Runnable r = () -> {
        <error descr="Local class 'Local' cannot be instantiated from a different static context">new Local()</error>;    // should be highlighted as an error
      };
    }
  }

  // local class in lambda
  static Runnable foo = () -> {
    Object there = "";
    class Local {
      {
        there.hashCode();
      }
      static {
        <error descr="Local class 'Local' cannot be instantiated from a different static context">new Local()</error>;    // should be highlighted as an error
      }
      static Runnable r = () -> {
        <error descr="Local class 'Local' cannot be instantiated from a different static context">new Local()</error>;    // should be highlighted as an error
      };
    }
  };

  // local class in switch
  static Object bar = switch (foo) {
    case Runnable r -> {
      Object there = "";
      class Local {
        {
          there.hashCode();
        }

        static {
          <error descr="Local class 'Local' cannot be instantiated from a different static context">new Local()</error>;    // should be highlighted as an error
        }

        static Runnable r = () -> {
          <error descr="Local class 'Local' cannot be instantiated from a different static context">new Local()</error>;    // should be highlighted as an error
        };
      }
      yield r;
    }
  };

  // local class in instance init
  {
    Object there = "";
    class Local {
      {
        there.hashCode();
      }

      static {
        <error descr="'LocalInstantiation.this' cannot be referenced from a static context">new Local()</error>;    // should be highlighted as an error
      }

      static Runnable r = () -> {
        <error descr="'LocalInstantiation.this' cannot be referenced from a static context">new Local()</error>;    // should be highlighted as an error
      };
    }
  }

  // local class in static init
  static {
    Object there = "";
    class Local {
      {
        there.hashCode();
      }

      static {
        <error descr="Local class 'Local' cannot be instantiated from a different static context">new Local()</error>;    // should be highlighted as an error
      }

      static Runnable r = () -> {
        <error descr="Local class 'Local' cannot be instantiated from a different static context">new Local()</error>;    // should be highlighted as an error
      };
    }
  }
}