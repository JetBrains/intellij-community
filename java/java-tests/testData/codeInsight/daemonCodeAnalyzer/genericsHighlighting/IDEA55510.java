abstract class IdeaBugTest<M extends IdeaBugTest.Mapping>
{
        static class Mapping {}
}

class BugTestSub extends IdeaBugTest<<error descr="SubMapping is not accessible in current context">BugTestSub.SubMapping</error>>
{
        public abstract static class SubMapping extends Mapping {}
}

class BugTestSub1 extends IdeaBugTest<BugTestSub1.SubMapping>
{
        public abstract static class SubMapping extends IdeaBugTest.Mapping {} //fqn here
}

class AbstractSettings {
    interface State {}
}
interface SomeInterface<T> {}
class Settings extends AbstractSettings implements SomeInterface<Settings.MyState> {
    static class MyState implements State {}
}

//--------------------------------------------
class Records {
  interface RecordCategory {
  }

  static abstract class Record<CATEGORY_TYPE extends RecordCategory> extends Records {}

  static class ResultRecord extends Record<ResultRecord.Category> {
    public enum Category implements RecordCategory {
      kind();
    }
  }
}
//---------------------------------------------
class Parent<T extends Parent.NestedParent>
{
  protected static interface NestedParent
  {
  }
}

class Test
{
  public final static class Child extends Parent<<error descr="NestedChild is not accessible in current context">Child.NestedChild</error>>
  {
    private static interface NestedChild extends NestedParent
    {
    }
  }
}