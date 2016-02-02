import java.util.function.Function;

class Test {
  static class MyMap<V>
  {
    V put( char[] key, V value )
    {
      return null;
    }

    V put( String key, V value )
    {
      return null;
    }
  }

  static final MyMap<Test.F> s_funcMap = new MyMap<>();

  interface F extends Function<Thread,Number> { }

  static
  {
    // without casts
    s_funcMap.put( "ID", Thread::getId );
    s_funcMap.put( "ID", thread -> thread.getId() + 1 );
    s_funcMap.put( "ID", thread -> { return thread.getId() + 1; } );

    // with casts
    s_funcMap.put( "ID", (Test.F) Thread::getId );
    s_funcMap.put( "ID", (Test.F) thread -> thread.getId() + 1 );
    s_funcMap.put( "ID", (Test.F) thread -> { return thread.getId() + 1; } );
  }
}
