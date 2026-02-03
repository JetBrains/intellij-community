package p;
import java.util.stream.Stream;
import java.util.List;

public abstract class A implements List<Stream<String>> {
  public Stream<String> foo() {}
  
  public void bar() {}
  
  public Stream<String> myField;
  
  public A a() {}
}