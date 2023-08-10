// "Create method 'fooBar'" "true"
interface I {
  String str(Container c);
}
class FooBar {
  {
    I i = Container::fooBar;
  }
}
class Container{
    public String fooBar() {
        return null;
    }
}
