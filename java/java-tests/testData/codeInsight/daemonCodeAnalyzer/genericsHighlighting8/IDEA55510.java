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
