abstract class IdeaBugTest<<warning descr="Type parameter 'M' is never used">M</warning> extends IdeaBugTest.Mapping>
{
        static class Mapping {}
}

class BugTestSub extends IdeaBugTest<<warning descr="Mapping is not accessible in current context">BugTestSub.SubMapping</warning>>
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
interface SomeInterface<<warning descr="Type parameter 'T' is never used">T</warning>> {}
class Settings extends AbstractSettings implements SomeInterface<Settings.MyState> {
    static class MyState implements State {}
}

//--------------------------------------------
class Records {
  interface RecordCategory {
  }

  static abstract class Record<<warning descr="Type parameter 'CATEGORY_TYPE' is never used">CATEGORY_TYPE</warning> extends RecordCategory> extends Records {}

  static class ResultRecord extends Record<ResultRecord.Category> {
    public enum Category implements RecordCategory {
      kind();
    }
  }
}
//---------------------------------------------
class Parent<<warning descr="Type parameter 'T' is never used">T</warning> extends Parent.NestedParent>
{
  protected static interface NestedParent
  {
  }
}

class Test
{
  public final static class Child extends Parent<<warning descr="NestedParent is not accessible in current context">Child.NestedChild</warning>>
  {
    private static interface NestedChild extends NestedParent
    {
    }
  }
}